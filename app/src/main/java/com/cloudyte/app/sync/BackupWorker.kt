package com.cloudyte.app.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.cloudyte.app.CloudyteApplication
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.api.FolderRequest
import com.cloudyte.app.core.network.ConnectivityNetworkMonitor
import com.cloudyte.app.core.network.DefaultNetworkPolicy
import com.cloudyte.app.core.network.NetworkPolicySnapshot
import com.cloudyte.app.core.network.PolicyDecision
import com.cloudyte.app.core.transfer.ResumableTransfer
import com.cloudyte.app.core.transfer.ScheduleReason
import com.cloudyte.app.core.transfer.TransferState
import com.cloudyte.app.core.transfer.notification.NotificationEventType
import com.cloudyte.app.core.transfer.notification.NotificationHelper
import com.cloudyte.app.data.ExclusionCandidate
import com.cloudyte.app.data.ExclusionSkipReason
import com.cloudyte.app.data.EvaluateExclusions
import com.cloudyte.app.data.TransferRepository
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.UserPreferencesState
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.BackupSourceEntity
import com.cloudyte.app.db.BackupSourcePermissionStatus
import com.cloudyte.app.db.LocalCloudFile
import com.cloudyte.app.db.LocalExclusion
import com.cloudyte.app.db.LocalTransfer
import com.cloudyte.app.db.TransferDirection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

@VisibleForTesting
internal object BackupWorkerHooks {
    @Volatile
    var networkSnapshotProvider: ((Context, UserPreferencesState) -> NetworkPolicySnapshot)? = null

    @Volatile
    var remoteFilesProvider: (suspend (accountId: String, folderId: String?) -> List<CloudFile>)? = null

    @Volatile
    var remoteFolderCreatorProvider: (suspend (accountId: String, name: String, parentId: String?) -> CloudFile?)? = null

    @Volatile
    var scheduleKickOverride: ((Context, Boolean) -> Unit)? = null

    fun reset() {
        networkSnapshotProvider = null
        remoteFilesProvider = null
        remoteFolderCreatorProvider = null
        scheduleKickOverride = null
    }
}

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "BackupWorker"
        private const val TRANSFER_CHANNEL_ID = "cloudyte_transfers"
        private const val TRANSFER_NOTIFICATION_ID = 2401
    }

    private var totalFilesToProcess = 0
    private var processedFilesCount = 0
    private var queuedFilesCount = 0
    private var skippedUpToDateCount = 0
    private var skippedExcludedCount = 0
    private var skippedMaxSizeCount = 0
    private var failedFilesCount = 0
    private var failedSourcesCount = 0
    private var forceReupload = false
    private var globalMaxUploadBytes = Long.MAX_VALUE
    private var sawRetryableError = false
    private var sawFatalClientError = false
    private var activeTransferId: String? = null
    private lateinit var transferRepository: TransferRepository

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(applicationContext, "Preparing backup…", 0, 0)
    }

    private suspend fun trySetForeground(info: ForegroundInfo) {
        try {
            setForeground(info)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.w(TAG, "Proceeding without foreground promotion: ${error.javaClass.simpleName}")
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        SyncLogger.init(context)
        UserPreferences.migrateIfNeeded(context)
        trySetForeground(createForegroundInfo(context, "Preparing backup…", 0, 0))

        val isManual = inputData.getBoolean("is_manual", false)
        forceReupload = inputData.getBoolean("force_reupload", false)
        val prefs = UserPreferences.observe(context).first()

        if (!isManual && !prefs.autoBackupEnabled) {
            SyncLogger.log("Auto-backup skipped (feature is disabled in settings)", SyncLogLevel.INFO)
            return Result.success()
        }

        val db = AppDatabase.getDatabase(context)
        BackupSourceManager.migrateLegacyBackupSourceIfNeeded(context, db)
        globalMaxUploadBytes = prefs.backupMaxFileSizeBytes

        // Enforce schedule & network/charging/roaming/battery conditions BEFORE scanning/starting uploads.
        val snapshot = BackupWorkerHooks.networkSnapshotProvider?.invoke(context, prefs)
            ?: ConnectivityNetworkMonitor(context).snapshot(prefs)
        val probeTransfer = LocalTransfer(
            id = "backup_policy_probe",
            accountId = prefs.backupAccountId ?: "",
            fileName = "backup_probe",
            direction = TransferDirection.BACKUP.name,
            operationType = "BACKUP"
        )
        val policyDecision = DefaultNetworkPolicy().canStart(probeTransfer, snapshot)
        if (policyDecision != PolicyDecision.Allowed) {
            val reasonText = describePolicyDecision(policyDecision)
            SyncLogger.log("Backup deferred: $reasonText", SyncLogLevel.WARNING)
            SyncLogger.updateStatus(SyncState.IDLE, action = reasonText, summary = reasonText)
            UserPreferences.recordBackupRunStatus(
                context = context,
                timestamp = System.currentTimeMillis(),
                summary = reasonText,
                queuedCount = 0,
                skippedSummary = "Deferred before starting uploads",
                failedCount = 0
            )
            rearmQuickMediaIfEnabled(context, prefs)
            return Result.retry()
        }

        val sources = db.backupSourceDao().getEnabledSources()
        if (sources.isEmpty()) {
            val msg = "No enabled backup folders configured."
            SyncLogger.log("Backup stopped: $msg", SyncLogLevel.WARNING)
            SyncLogger.updateStatus(SyncState.ERROR, summary = msg)
            UserPreferences.recordBackupRunStatus(
                context = context,
                timestamp = System.currentTimeMillis(),
                summary = msg,
                queuedCount = 0,
                skippedSummary = "",
                failedCount = 1
            )
            rearmQuickMediaIfEnabled(context, prefs)
            return Result.failure()
        }

        val exclusions = db.exclusionDao().getEnabledExclusions()
        val syncModeLabel = if (forceReupload) "Force Re-upload All" else "Smart Sync (New & Modified Only)"
        SyncLogger.log(
            "Starting backup scan [$syncModeLabel] across ${sources.size} folder(s) with ${exclusions.size} active exclusion(s).",
            SyncLogLevel.INFO
        )
        SyncLogger.updateStatus(SyncState.SCANNING, action = "Scanning ${sources.size} backup folder(s)…")

        try {
            totalFilesToProcess = 0
            processedFilesCount = 0
            queuedFilesCount = 0
            skippedUpToDateCount = 0
            skippedExcludedCount = 0
            skippedMaxSizeCount = 0
            failedFilesCount = 0
            failedSourcesCount = 0
            sawRetryableError = false
            sawFatalClientError = false

            val appContainer = (context.applicationContext as? CloudyteApplication)?.container
            transferRepository = TransferRepository(db.transferDao(), appContainer?.providerRegistry)

            val recovered = db.transferQueueDao().recoverExpiredLeases(System.currentTimeMillis())
            if (recovered > 0) {
                SyncLogger.log("Recovered $recovered interrupted transfer lease(s).", SyncLogLevel.WARNING)
            }

            // Inspect all enabled sources first and count files for healthy sources.
            val healthySources = mutableListOf<Triple<BackupSourceEntity, DocumentFile, String>>()
            val sourceErrors = mutableListOf<String>()

            for (source in sources) {
                val now = System.currentTimeMillis()
                val health = BackupSourceManager.inspectSourceHealth(context, source)
                if (health.status != BackupSourcePermissionStatus.GRANTED) {
                    failedSourcesCount++
                    val errText = health.errorMessage ?: "Folder is unavailable (${health.status.name})."
                    db.backupSourceDao().updatePermissionStatus(
                        id = source.id,
                        status = health.status.name,
                        error = errText,
                        updatedAt = now
                    )
                    sourceErrors += "${source.displayName}: $errText"
                    SyncLogger.log("✗ Backup folder '${source.displayName}' unavailable: $errText", SyncLogLevel.ERROR)
                    NotificationHelper.postEventIfEnabled(
                        context = context,
                        eventType = NotificationEventType.BACKUP_FAILURE,
                        title = "Backup folder access issue",
                        message = "${source.displayName}: $errText",
                        notificationId = ("source_err:${source.id}").hashCode()
                    )
                    continue
                }

                val targetAccountId = source.targetAccountId?.takeIf { it.isNotBlank() }
                    ?: prefs.backupAccountId?.takeIf { it.isNotBlank() }
                if (targetAccountId == null) {
                    failedSourcesCount++
                    val errText = "No target Cloud Drive selected."
                    db.backupSourceDao().updatePermissionStatus(
                        id = source.id,
                        status = BackupSourcePermissionStatus.GRANTED.name,
                        error = errText,
                        updatedAt = now
                    )
                    sourceErrors += "${source.displayName}: $errText"
                    SyncLogger.log("✗ '${source.displayName}': $errText", SyncLogLevel.ERROR)
                    continue
                }

                val rootDoc = BackupSourceManager.resolveTreeDocument(context, source.treeUri)
                if (rootDoc == null || !rootDoc.exists() || !rootDoc.isDirectory) {
                    failedSourcesCount++
                    val errText = "Folder could not be opened."
                    db.backupSourceDao().updatePermissionStatus(
                        id = source.id,
                        status = BackupSourcePermissionStatus.UNREADABLE.name,
                        error = errText,
                        updatedAt = now
                    )
                    sourceErrors += "${source.displayName}: $errText"
                    SyncLogger.log("✗ '${source.displayName}': $errText", SyncLogLevel.ERROR)
                    continue
                }

                totalFilesToProcess += countCandidateFiles(rootDoc, exclusions, "")
                healthySources += Triple(source, rootDoc, targetAccountId)
            }

            if (healthySources.isNotEmpty()) {
                SyncLogger.log("Found $totalFilesToProcess candidate file(s) across ${healthySources.size} accessible folder(s).", SyncLogLevel.INFO)
                SyncLogger.updateStatus(
                    SyncState.SYNCING,
                    action = "Scanning files…",
                    uploadedCount = 0,
                    totalCount = totalFilesToProcess
                )
                trySetForeground(createForegroundInfo(context, "Scanning files…", 0, totalFilesToProcess))
            }

            for ((source, rootDoc, accountId) in healthySources) {
                val prevQueued = queuedFilesCount
                val prevSkipped = skippedUpToDateCount + skippedExcludedCount + skippedMaxSizeCount
                val prevFailed = failedFilesCount

                val baseFolderId = source.targetFolderId?.takeIf { it.isNotBlank() }
                syncDirectory(
                    context = context,
                    db = db,
                    accountId = accountId,
                    currentCloudFolderId = baseFolderId,
                    localDir = rootDoc,
                    relativeDirPath = "",
                    exclusions = exclusions,
                    encryptUploads = prefs.isUploadEncryptionEnabled
                )

                val sourceQueued = queuedFilesCount - prevQueued
                val sourceSkipped = (skippedUpToDateCount + skippedExcludedCount + skippedMaxSizeCount) - prevSkipped
                val sourceFailed = failedFilesCount - prevFailed
                val scanAt = System.currentTimeMillis()
                val sourceSummary = "Queued $sourceQueued • Skipped $sourceSkipped" +
                    if (sourceFailed > 0) " • Errors $sourceFailed" else ""

                db.backupSourceDao().updateScanResult(
                    id = source.id,
                    status = BackupSourcePermissionStatus.GRANTED.name,
                    scanAt = scanAt,
                    backupSucceeded = sourceFailed == 0,
                    summary = sourceSummary,
                    error = if (sourceFailed > 0) "$sourceFailed file(s) encountered scan errors" else null,
                    queuedCount = sourceQueued,
                    skippedCount = sourceSkipped,
                    failedCount = sourceFailed
                )
            }

            if (queuedFilesCount > 0) {
                val kickOverride = BackupWorkerHooks.scheduleKickOverride
                if (kickOverride != null) {
                    kickOverride(context, !prefs.backupWifiOnly)
                } else {
                    appContainer?.transferScheduler?.kick(
                        reason = ScheduleReason.USER_ENQUEUE,
                        allowMetered = !prefs.backupWifiOnly
                    )
                }
            }

            val totalSkipped = skippedUpToDateCount + skippedExcludedCount + skippedMaxSizeCount
            val totalFailed = failedFilesCount + failedSourcesCount
            val skippedParts = buildList {
                if (skippedUpToDateCount > 0) add("$skippedUpToDateCount unchanged")
                if (skippedExcludedCount > 0) add("$skippedExcludedCount excluded")
                if (skippedMaxSizeCount > 0) add("$skippedMaxSizeCount over size limit")
            }
            val skippedSummary = if (skippedParts.isEmpty()) "" else skippedParts.joinToString(", ")

            val summary = buildString {
                append("Queued $queuedFilesCount upload(s)")
                if (totalSkipped > 0) append(" • $totalSkipped skipped")
                if (totalFailed > 0) append(" • $totalFailed error(s)")
                if (sourceErrors.isNotEmpty()) append(" (${sourceErrors.first()})")
            }

            val finalState = if (totalFailed > 0 && queuedFilesCount == 0 && totalSkipped == 0) {
                SyncState.ERROR
            } else {
                SyncState.SUCCESS
            }
            val finalLogLevel = if (totalFailed == 0) SyncLogLevel.SUCCESS else SyncLogLevel.WARNING

            SyncLogger.log("✓ $summary", finalLogLevel)
            SyncLogger.updateStatus(
                state = finalState,
                action = "Idle",
                uploadedCount = queuedFilesCount,
                totalCount = totalFilesToProcess,
                summary = summary
            )
            UserPreferences.recordBackupRunStatus(
                context = context,
                timestamp = System.currentTimeMillis(),
                summary = summary,
                queuedCount = queuedFilesCount,
                skippedSummary = skippedSummary,
                failedCount = totalFailed
            )

            rearmQuickMediaIfEnabled(context, prefs)

            return when {
                sawRetryableError -> Result.retry()
                sawFatalClientError || (failedSourcesCount > 0 && healthySources.isEmpty()) -> Result.failure()
                else -> Result.success()
            }
        } catch (e: CancellationException) {
            activeTransferId = null
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "Backup failed with IOException", e)
            val errMsg = e.localizedMessage ?: e.message ?: "Network I/O error"
            SyncLogger.log("✗ Sync interrupted (I/O): $errMsg", SyncLogLevel.ERROR)
            SyncLogger.updateStatus(SyncState.ERROR, summary = "Interrupted: $errMsg")
            UserPreferences.recordBackupRunStatus(
                context = context,
                timestamp = System.currentTimeMillis(),
                summary = "Interrupted: $errMsg",
                queuedCount = queuedFilesCount,
                skippedSummary = "",
                failedCount = failedFilesCount + failedSourcesCount + 1
            )
            rearmQuickMediaIfEnabled(context, prefs)
            return Result.retry()
        } catch (e: HttpException) {
            val code = e.code()
            Log.e(TAG, "Backup failed with HTTP $code", e)
            val errMsg = "HTTP $code: ${e.message()}"
            SyncLogger.log("✗ Sync interrupted: $errMsg", SyncLogLevel.ERROR)
            SyncLogger.updateStatus(SyncState.ERROR, summary = "Interrupted: $errMsg")
            UserPreferences.recordBackupRunStatus(
                context = context,
                timestamp = System.currentTimeMillis(),
                summary = "Interrupted: $errMsg",
                queuedCount = queuedFilesCount,
                skippedSummary = "",
                failedCount = failedFilesCount + failedSourcesCount + 1
            )
            rearmQuickMediaIfEnabled(context, prefs)
            return if (code == 429 || code in 500..599) Result.retry() else Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed with exception", e)
            val errMsg = e.localizedMessage ?: e.message ?: "Unknown error"
            SyncLogger.log("✗ Sync interrupted: $errMsg", SyncLogLevel.ERROR)
            SyncLogger.updateStatus(SyncState.ERROR, summary = "Interrupted: $errMsg")
            UserPreferences.recordBackupRunStatus(
                context = context,
                timestamp = System.currentTimeMillis(),
                summary = "Interrupted: $errMsg",
                queuedCount = queuedFilesCount,
                skippedSummary = "",
                failedCount = failedFilesCount + failedSourcesCount + 1
            )
            rearmQuickMediaIfEnabled(context, prefs)
            return if (e.cause is IOException) Result.retry() else Result.failure()
        }
    }

    private suspend fun rearmQuickMediaIfEnabled(context: Context, prefs: UserPreferencesState) {
        if (!isStopped && prefs.autoBackupEnabled && prefs.quickMediaBackupEnabled) {
            runCatching { AutoSyncManager.armQuickMediaTrigger(context, prefs) }
        }
    }

    private fun describePolicyDecision(decision: PolicyDecision): String {
        return when (decision) {
            is PolicyDecision.WaitingForNetwork -> when (decision.reason) {
                "BACKUP_WIFI_ONLY", "METERED_NETWORK_DISABLED" -> "Waiting for unmetered Wi-Fi network"
                else -> "Waiting for network connection"
            }
            is PolicyDecision.WaitingForCharging -> "Waiting for device charging"
            is PolicyDecision.WaitingForBattery -> "Paused due to low battery or battery saver"
            is PolicyDecision.WaitingForRoaming -> "Waiting because roaming transfers are disabled"
            is PolicyDecision.WaitingForAuth -> "Authentication required"
            PolicyDecision.Allowed -> "Allowed"
        }
    }

    private fun countCandidateFiles(
        dir: DocumentFile,
        exclusions: List<LocalExclusion>,
        relativePrefix: String
    ): Int {
        var count = 0
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
        for (item in children) {
            val name = item.name ?: continue
            val relPath = if (relativePrefix.isBlank()) name else "$relativePrefix/$name"
            if (item.isDirectory) {
                val dirEval = EvaluateExclusions.evaluate(
                    candidate = ExclusionCandidate(
                        fileName = name,
                        relativePath = relPath,
                        fileUri = item.uri.toString(),
                        isDirectory = true
                    ),
                    exclusions = exclusions,
                    globalMaxFileSizeBytes = globalMaxUploadBytes
                )
                if (!dirEval.excluded) {
                    count += countCandidateFiles(item, exclusions, relPath)
                }
            } else if (item.isFile) {
                count++
            }
        }
        return count
    }

    private suspend fun syncDirectory(
        context: Context,
        db: AppDatabase,
        accountId: String,
        currentCloudFolderId: String?,
        localDir: DocumentFile,
        relativeDirPath: String,
        exclusions: List<LocalExclusion>,
        encryptUploads: Boolean
    ) {
        val safeFolderId = currentCloudFolderId ?: "root"
        val folderLabel = relativeDirPath.ifBlank { localDir.name ?: "Root" }
        val appContainer = (context.applicationContext as? CloudyteApplication)?.container

        val cloudFiles = fetchRemoteFiles(context, db, appContainer, accountId, currentCloudFolderId, safeFolderId, folderLabel)
        val localItems = try {
            localDir.listFiles()
        } catch (sec: SecurityException) {
            failedFilesCount++
            SyncLogger.log("✗ Lost read access while listing '$folderLabel': ${sec.message}", SyncLogLevel.ERROR)
            return
        } catch (err: Exception) {
            failedFilesCount++
            SyncLogger.log("✗ Error listing '$folderLabel': ${err.message}", SyncLogLevel.ERROR)
            return
        }

        for (file in localItems) {
            if (!file.isFile) continue
            val fileName = file.name ?: continue
            processedFilesCount++
            val relativeFilePath = if (relativeDirPath.isBlank()) fileName else "$relativeDirPath/$fileName"

            try {
                if (!file.canRead()) {
                    failedFilesCount++
                    SyncLogger.log("✗ [$processedFilesCount/$totalFilesToProcess] Unreadable file '$relativeFilePath'.", SyncLogLevel.ERROR)
                    continue
                }

                val fileSize = file.length()
                val lastModified = file.lastModified()
                val fileUriStr = file.uri.toString()
                val mimeType = resolveMimeType(context, file, fileName)
                val sizeFormatted = formatFileSize(fileSize)

                val eval = EvaluateExclusions.evaluate(
                    candidate = ExclusionCandidate(
                        fileName = fileName,
                        relativePath = relativeFilePath,
                        fileUri = fileUriStr,
                        mimeType = mimeType,
                        sizeBytes = fileSize,
                        isDirectory = false
                    ),
                    exclusions = exclusions,
                    globalMaxFileSizeBytes = globalMaxUploadBytes
                )
                if (eval.excluded) {
                    if (eval.reason == ExclusionSkipReason.GLOBAL_MAX_SIZE) {
                        skippedMaxSizeCount++
                    } else {
                        skippedExcludedCount++
                    }
                    SyncLogger.log(
                        "• [$processedFilesCount/$totalFilesToProcess] Skipping '$relativeFilePath': ${eval.humanReason}",
                        SyncLogLevel.INFO
                    )
                    continue
                }

                var computedHash: String? = null
                val latestByUri = if (!forceReupload) {
                    db.uploadRecordDao().findLatestSuccessByUri(fileUriStr, accountId, safeFolderId)
                } else {
                    null
                }
                val matchingLegacyRecord = if (!forceReupload && latestByUri == null) {
                    db.uploadRecordDao().findMatchingSuccessRecord(
                        fileName = fileName,
                        fileSize = fileSize,
                        lastModified = lastModified,
                        accountId = accountId
                    )
                } else {
                    null
                }

                val quickUnchangedMatch = latestByUri != null &&
                    latestByUri.fileSize == fileSize &&
                    lastModified > 0L &&
                    latestByUri.lastModified == lastModified

                var isDuplicate = quickUnchangedMatch || matchingLegacyRecord != null
                if (!forceReupload && !isDuplicate) {
                    computedHash = computeSha256(context, file.uri)
                    if (computedHash != null) {
                        if (latestByUri != null &&
                            latestByUri.fileSize == fileSize &&
                            latestByUri.contentHash == computedHash
                        ) {
                            isDuplicate = true
                        } else if (db.uploadRecordDao().findLatestSuccessByHash(
                                contentHash = computedHash,
                                fileSize = fileSize,
                                accountId = accountId,
                                folderId = safeFolderId
                            ) != null
                        ) {
                            isDuplicate = true
                        }
                    }
                }

                // Only consult remote file list when there is no local upload history for this URI yet;
                // if latestByUri != null and size/mtime/hash changed, this is a modified version that MUST upload.
                if (!forceReupload && !isDuplicate && latestByUri == null && matchingLegacyRecord == null) {
                    isDuplicate = cloudFiles.any {
                        !it.isFolderItem &&
                            it.effectiveName == fileName &&
                            (it.sizeBytes <= 0L || it.sizeBytes == fileSize) &&
                            (it.updatedAt == null || lastModified <= 0L || it.updatedAt >= lastModified)
                    }
                }

                if (isDuplicate) {
                    skippedUpToDateCount++
                    SyncLogger.log("• [$processedFilesCount/$totalFilesToProcess] '$relativeFilePath' is already backed up.", SyncLogLevel.INFO)
                    SyncLogger.updateStatus(
                        SyncState.SYNCING,
                        action = "Skipping unchanged files…",
                        file = fileName,
                        uploadedCount = queuedFilesCount,
                        totalCount = totalFilesToProcess
                    )
                    continue
                }

                if (computedHash == null) {
                    computedHash = computeSha256(context, file.uri)
                }
                val hashSuffix = computedHash?.take(12)?.let { ":$it" } ?: ""
                val forceToken = if (forceReupload) ":force:${System.currentTimeMillis()}" else ""
                val transferId = "backup:$accountId:$safeFolderId:$fileUriStr:$lastModified:$fileSize$hashSuffix$forceToken"
                activeTransferId = transferId

                val existingTransfer = db.transferDao().getById(transferId)
                if (existingTransfer?.state == TransferState.PAUSED.name ||
                    existingTransfer?.state == TransferState.CANCELLED.name ||
                    existingTransfer?.state == TransferState.COMPLETED.name
                ) {
                    activeTransferId = null
                    continue
                }
                if (existingTransfer?.state == TransferState.RUNNING.name) {
                    queuedFilesCount++
                    activeTransferId = null
                    continue
                }

                val accountType = db.accountDao().getAccountById(accountId)?.accountType
                if (existingTransfer == null) {
                    db.transferDao().insertIfAbsent(
                        LocalTransfer(
                            id = transferId,
                            accountId = accountId,
                            fileName = fileName,
                            localPath = fileUriStr,
                            localUri = fileUriStr,
                            remotePath = currentCloudFolderId,
                            parentFolderId = currentCloudFolderId,
                            providerType = accountType,
                            direction = TransferDirection.BACKUP.name,
                            operationType = "BACKUP",
                            totalBytes = fileSize,
                            checksum = computedHash,
                            isEncrypted = encryptUploads,
                            encryptionFormatVersion = if (encryptUploads) 1 else null,
                            plaintextSizeBytes = if (encryptUploads) fileSize else null
                        )
                    )
                }
                val idempotencyKey = ResumableTransfer.newIdempotencyKey("$transferId:$lastModified:$fileSize")
                if (existingTransfer?.idempotencyKey != idempotencyKey || existingTransfer?.checksum != computedHash) {
                    transferRepository.saveResumeMetadata(
                        id = transferId,
                        sessionId = existingTransfer?.providerSessionId,
                        chunkSize = existingTransfer?.chunkSize?.takeIf { it > 0L } ?: ResumableTransfer.DEFAULT_CHUNK_SIZE,
                        nextChunkIndex = existingTransfer?.nextChunkIndex ?: 0,
                        checksum = computedHash,
                        idempotencyKey = idempotencyKey
                    )
                }
                val queued = when (existingTransfer?.state) {
                    TransferState.FAILED_RETRYABLE.name,
                    TransferState.WAITING_FOR_NETWORK.name,
                    TransferState.WAITING_FOR_AUTH.name -> true
                    else -> transferRepository.queueTransfer(
                        id = transferId,
                        direction = TransferDirection.BACKUP,
                        localUri = fileUriStr,
                        parentFolderId = currentCloudFolderId,
                        providerType = accountType,
                        totalBytes = fileSize
                    )
                }
                activeTransferId = null
                if (queued) {
                    queuedFilesCount++
                    SyncLogger.log("↗ [$processedFilesCount/$totalFilesToProcess] Queued '$relativeFilePath' ($sizeFormatted).", SyncLogLevel.INFO)
                    trySetForeground(createForegroundInfo(context, "Queued $fileName", processedFilesCount, totalFilesToProcess))
                } else {
                    failedFilesCount++
                    SyncLogger.log("✗ Could not enqueue '$relativeFilePath' because its transfer is terminal.", SyncLogLevel.ERROR)
                }
                SyncLogger.updateStatus(
                    SyncState.SYNCING,
                    action = "Queueing files…",
                    file = fileName,
                    uploadedCount = queuedFilesCount,
                    totalCount = totalFilesToProcess
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                activeTransferId?.let { transferRepository.markFailed(it, e.message ?: "I/O error") }
                activeTransferId = null
                failedFilesCount++
                sawRetryableError = true
                SyncLogger.log("✗ I/O error processing '$relativeFilePath': ${e.message}", SyncLogLevel.ERROR)
            } catch (e: HttpException) {
                activeTransferId?.let { transferRepository.markFailed(it, "HTTP ${e.code()}") }
                activeTransferId = null
                failedFilesCount++
                val code = e.code()
                if (code == 429 || code in 500..599) sawRetryableError = true else if (code in 400..499) sawFatalClientError = true
                SyncLogger.log("✗ HTTP $code processing '$relativeFilePath': ${e.message()}", SyncLogLevel.ERROR)
            } catch (e: Exception) {
                activeTransferId?.let { transferRepository.markFailed(it, e.message ?: "Scan error") }
                activeTransferId = null
                failedFilesCount++
                if (e.cause is IOException) sawRetryableError = true
                SyncLogger.log("✗ Error processing '$relativeFilePath': ${e.message}", SyncLogLevel.ERROR)
            }
        }

        // Process subdirectories recursively AFTER evaluating folder exclusion rules.
        for (dir in localItems) {
            if (!dir.isDirectory) continue
            val subFolderName = dir.name ?: continue
            val subRelPath = if (relativeDirPath.isBlank()) subFolderName else "$relativeDirPath/$subFolderName"
            val dirEval = EvaluateExclusions.evaluate(
                candidate = ExclusionCandidate(
                    fileName = subFolderName,
                    relativePath = subRelPath,
                    fileUri = dir.uri.toString(),
                    isDirectory = true
                ),
                exclusions = exclusions,
                globalMaxFileSizeBytes = globalMaxUploadBytes
            )
            if (dirEval.excluded) {
                skippedExcludedCount++
                SyncLogger.log("• Excluding folder '$subRelPath': ${dirEval.humanReason}", SyncLogLevel.INFO)
                continue
            }

            var cloudFolder = cloudFiles.find { it.isFolderItem && it.effectiveName == subFolderName }
            if (cloudFolder == null) {
                cloudFolder = createOrFindRemoteFolder(
                    db = db,
                    accountId = accountId,
                    parentFolderId = currentCloudFolderId,
                    safeFolderId = safeFolderId,
                    folderName = subFolderName
                )
            }

            if (cloudFolder != null) {
                syncDirectory(
                    context = context,
                    db = db,
                    accountId = accountId,
                    currentCloudFolderId = cloudFolder.effectiveId,
                    localDir = dir,
                    relativeDirPath = subRelPath,
                    exclusions = exclusions,
                    encryptUploads = encryptUploads
                )
            }
        }
    }

    private suspend fun fetchRemoteFiles(
        context: Context,
        db: AppDatabase,
        appContainer: com.cloudyte.app.AppContainer?,
        accountId: String,
        currentCloudFolderId: String?,
        safeFolderId: String,
        pathLabel: String
    ): List<CloudFile> {
        val testProvider = BackupWorkerHooks.remoteFilesProvider
        if (testProvider != null) {
            return testProvider(accountId, currentCloudFolderId)
        }
        return try {
            val response = ApiClient.apiService.getFiles(accountId, currentCloudFolderId, null)
            if (response.isSuccessful) {
                val fetched = response.body().orEmpty()
                val localMapped = fetched.map { remote ->
                    appContainer?.cloudFileRepository?.resolveRemoteCloudFileToLocal(remote, accountId, safeFolderId)
                        ?: LocalCloudFile.fromCloudFile(remote, accountId, safeFolderId)
                }
                db.cloudFileDao().insertFiles(localMapped)
                localMapped.map { it.toCloudFile() }
            } else {
                val code = response.code()
                if (code == 429 || code in 500..599) sawRetryableError = true else if (code in 400..499) sawFatalClientError = true
                db.cloudFileDao().getFilesForFolder(accountId, safeFolderId).map { it.toCloudFile() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            sawRetryableError = true
            SyncLogger.log("Using cached files for '$pathLabel': ${e.message}", SyncLogLevel.WARNING)
            db.cloudFileDao().getFilesForFolder(accountId, safeFolderId).map { it.toCloudFile() }
        } catch (e: HttpException) {
            if (e.code() == 429 || e.code() in 500..599) sawRetryableError = true else if (e.code() in 400..499) sawFatalClientError = true
            SyncLogger.log("Using cached files for '$pathLabel': ${e.message}", SyncLogLevel.WARNING)
            db.cloudFileDao().getFilesForFolder(accountId, safeFolderId).map { it.toCloudFile() }
        } catch (e: Exception) {
            SyncLogger.log("Using cached files for '$pathLabel': ${e.message}", SyncLogLevel.WARNING)
            db.cloudFileDao().getFilesForFolder(accountId, safeFolderId).map { it.toCloudFile() }
        }
    }

    private suspend fun createOrFindRemoteFolder(
        db: AppDatabase,
        accountId: String,
        parentFolderId: String?,
        safeFolderId: String,
        folderName: String
    ): CloudFile? {
        val creatorOverride = BackupWorkerHooks.remoteFolderCreatorProvider
        if (creatorOverride != null) {
            return creatorOverride(accountId, folderName, parentFolderId)
        }
        SyncLogger.log("Creating cloud folder: '$folderName'…", SyncLogLevel.INFO)
        return try {
            val createResp = ApiClient.apiService.createFolder(accountId, FolderRequest(folderName, parentFolderId))
            if (!createResp.isSuccessful) {
                val code = createResp.code()
                if (code == 429 || code in 500..599) sawRetryableError = true else if (code in 400..499) sawFatalClientError = true
            }
            val response = ApiClient.apiService.getFiles(accountId, parentFolderId, null)
            val updatedFiles = response.body().orEmpty()
            val cloudFolder = updatedFiles.find { it.isFolderItem && it.effectiveName == folderName }
            if (cloudFolder != null) {
                db.cloudFileDao().insertFiles(listOf(LocalCloudFile.fromCloudFile(cloudFolder, accountId, safeFolderId)))
            }
            cloudFolder
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            sawRetryableError = true
            SyncLogger.log("Failed to create folder '$folderName': ${e.message}", SyncLogLevel.ERROR)
            null
        } catch (e: HttpException) {
            if (e.code() == 429 || e.code() in 500..599) sawRetryableError = true else if (e.code() in 400..499) sawFatalClientError = true
            SyncLogger.log("Failed to create folder '$folderName': ${e.message}", SyncLogLevel.ERROR)
            null
        } catch (e: Exception) {
            SyncLogger.log("Failed to create folder '$folderName': ${e.message}", SyncLogLevel.ERROR)
            null
        }
    }

    private fun resolveMimeType(context: Context, doc: DocumentFile, fileName: String): String? {
        val docMime = runCatching { doc.type }.getOrNull()?.takeIf { it.isNotBlank() }
        if (docMime != null) return docMime
        val crMime = runCatching { context.contentResolver.getType(doc.uri) }.getOrNull()?.takeIf { it.isNotBlank() }
        if (crMime != null) return crMime
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return if (ext.isNotBlank()) MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) else null
    }

    private fun computeSha256(context: Context, uri: Uri): String? {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(16 * 1024)
            context.contentResolver.openInputStream(uri)?.use { input ->
                var read = input.read(buffer)
                while (read >= 0) {
                    if (read > 0) digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            } ?: return null
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    private fun createForegroundInfo(context: Context, message: String, completed: Int, total: Int): ForegroundInfo {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    TRANSFER_CHANNEL_ID,
                    "Cloudyte transfers",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Backup and transfer progress" }
            )
        }
        val notification = NotificationCompat.Builder(context, TRANSFER_CHANNEL_ID)
            .setSmallIcon(com.cloudyte.app.R.drawable.ic_launcher_foreground)
            .setContentTitle("Cloudyte backup")
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .apply {
                if (total > 0) setProgress(total, completed.coerceIn(0, total), false)
            }
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                TRANSFER_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(TRANSFER_NOTIFICATION_ID, notification)
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0L) return "0 B"
        return when {
            size >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", size.toDouble() / (1024L * 1024 * 1024))
            size >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", size.toDouble() / (1024L * 1024))
            size >= 1024 -> String.format(Locale.US, "%.1f KB", size.toDouble() / 1024)
            else -> "$size B"
        }
    }
}

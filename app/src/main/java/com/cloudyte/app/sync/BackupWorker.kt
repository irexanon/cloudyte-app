package com.cloudyte.app.sync

import android.content.Context
import android.net.Uri
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.core.transfer.ResumableTransfer
import com.cloudyte.app.api.FolderRequest
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.LocalCloudFile
import com.cloudyte.app.db.LocalUploadRecord
import com.cloudyte.app.db.LocalTransfer
import com.cloudyte.app.db.TransferDirection
import com.cloudyte.app.data.TransferRepository
import com.cloudyte.app.ui.screens.UploadHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TRANSFER_CHANNEL_ID = "cloudyte_transfers"
        private const val TRANSFER_NOTIFICATION_ID = 2401
    }

    private var totalFilesToProcess = 0
    private var processedFilesCount = 0
    private var uploadedFilesCount = 0
    private var skippedFilesCount = 0
    private var failedFilesCount = 0
    private var forceReupload = false
    private var maxUploadBytes = 500L * 1024L * 1024L
    private var sawRetryableError = false
    private var sawFatalClientError = false
    private var activeTransferId: String? = null
    private lateinit var transferRepository: TransferRepository

    override suspend fun doWork(): Result {
        val context = applicationContext
        SyncLogger.init(context)
        setForeground(createForegroundInfo(context, "Preparing backup…", 0, 0))

        val isManual = inputData.getBoolean("is_manual", false)
        forceReupload = inputData.getBoolean("force_reupload", false)
        val isEnabled = SyncPreferences.getAutoBackupEnabled(context).first()

        if (!isManual && !isEnabled) {
            SyncLogger.log("Auto-backup skipped (feature is disabled in settings)", SyncLogLevel.INFO)
            return Result.success()
        }

        val prefs = UserPreferences.observe(context).first()
        maxUploadBytes = prefs.backupMaxFileSizeMb.toLong().coerceAtLeast(1L) * 1024L * 1024L

        val uriString = SyncPreferences.getBackupUri(context).first()
        if (uriString.isNullOrBlank()) {
            SyncLogger.log("Backup failed: No local folder selected for backup.", SyncLogLevel.ERROR)
            SyncLogger.updateStatus(SyncState.ERROR, summary = "Failed: No local folder selected")
            return Result.failure()
        }

        val accountId = SyncPreferences.getBackupAccountId(context).first()
        if (accountId.isNullOrBlank()) {
            SyncLogger.log("Backup failed: No target Cloud Drive selected.", SyncLogLevel.ERROR)
            SyncLogger.updateStatus(SyncState.ERROR, summary = "Failed: No Cloud Drive selected")
            return Result.failure()
        }

        val rootUri = Uri.parse(uriString)
        val rootDoc = try {
            DocumentFile.fromTreeUri(context, rootUri)
        } catch (e: Exception) {
            null
        }

        if (rootDoc == null || !rootDoc.exists() || !rootDoc.isDirectory) {
            SyncLogger.log("Backup failed: Cannot access local folder. Permission may have expired.", SyncLogLevel.ERROR)
            SyncLogger.updateStatus(SyncState.ERROR, summary = "Failed: Local folder inaccessible")
            return Result.failure()
        }

        val syncModeLabel = if (forceReupload) "Force Re-upload All" else "Smart Sync (New & Modified Only)"
        SyncLogger.log("Starting Backup [$syncModeLabel]: Folder '${rootDoc.name ?: "Root"}'", SyncLogLevel.INFO)
        SyncLogger.updateStatus(SyncState.SCANNING, action = "Scanning local files...")

        try {
            totalFilesToProcess = countFiles(rootDoc)
            processedFilesCount = 0
            uploadedFilesCount = 0
            skippedFilesCount = 0
            failedFilesCount = 0
            sawRetryableError = false
            sawFatalClientError = false

            SyncLogger.log("Found $totalFilesToProcess total file(s) to verify.", SyncLogLevel.INFO)
            SyncLogger.updateStatus(
                SyncState.SYNCING,
                action = "Starting sync...",
                uploadedCount = 0,
                totalCount = totalFilesToProcess
            )
            setForeground(createForegroundInfo(context, "Syncing files…", 0, totalFilesToProcess))

            val db = AppDatabase.getDatabase(context)
            transferRepository = TransferRepository(db.transferDao())
            val recovered = transferRepository.recoverStaleTransfers()
            if (recovered > 0) {
                SyncLogger.log("Recovered $recovered interrupted transfer(s).", SyncLogLevel.WARNING)
            }

            syncDirectory(context, db, accountId, null, rootDoc, "")

            val summary = if (failedFilesCount == 0) {
                "Completed: $uploadedFilesCount uploaded, $skippedFilesCount up-to-date/skipped"
            } else {
                "Finished with $failedFilesCount error(s). $uploadedFilesCount uploaded, $skippedFilesCount skipped"
            }

            val finalState = if (failedFilesCount > 0 && uploadedFilesCount == 0 && skippedFilesCount == 0) {
                SyncState.ERROR
            } else {
                SyncState.SUCCESS
            }
            val finalLogLevel = if (failedFilesCount == 0) SyncLogLevel.SUCCESS else SyncLogLevel.WARNING

            SyncLogger.log("✓ $summary", finalLogLevel)
            SyncLogger.updateStatus(
                state = finalState,
                action = "Idle",
                uploadedCount = uploadedFilesCount,
                totalCount = totalFilesToProcess,
                summary = summary
            )

            return when {
                sawRetryableError -> Result.retry()
                sawFatalClientError -> Result.failure()
                else -> Result.success()
            }
        } catch (e: CancellationException) {
            activeTransferId?.let { id ->
                withContext(NonCancellable) {
                    if (::transferRepository.isInitialized) transferRepository.markCancelled(id)
                }
            }
            activeTransferId = null
            throw e
        } catch (e: IOException) {
            Log.e("BackupWorker", "Backup failed with IOException", e)
            val errMsg = e.localizedMessage ?: e.message ?: "Network I/O error"
            SyncLogger.log("✗ Sync interrupted (I/O): $errMsg", SyncLogLevel.ERROR)
            SyncLogger.updateStatus(SyncState.ERROR, summary = "Interrupted: $errMsg")
            return Result.retry()
        } catch (e: HttpException) {
            val code = e.code()
            Log.e("BackupWorker", "Backup failed with HTTP $code", e)
            val errMsg = "HTTP $code: ${e.message()}"
            SyncLogger.log("✗ Sync interrupted: $errMsg", SyncLogLevel.ERROR)
            SyncLogger.updateStatus(SyncState.ERROR, summary = "Interrupted: $errMsg")
            return if (code == 429 || code in 500..599) {
                Result.retry()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e("BackupWorker", "Backup failed with exception", e)
            val errMsg = e.localizedMessage ?: e.message ?: "Unknown error"
            SyncLogger.log("✗ Sync interrupted: $errMsg", SyncLogLevel.ERROR)
            SyncLogger.updateStatus(SyncState.ERROR, summary = "Interrupted: $errMsg")
            return if (e.cause is IOException) Result.retry() else Result.failure()
        }
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

    private fun countFiles(dir: DocumentFile): Int {
        var count = 0
        dir.listFiles().forEach { file ->
            if (file.isDirectory) {
                count += countFiles(file)
            } else if (file.isFile) {
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
        currentPath: String
    ) {
        val folderName = localDir.name ?: "Root"
        val pathLabel = if (currentPath.isBlank()) folderName else "$currentPath/$folderName"
        val safeFolderId = currentCloudFolderId ?: "root"

        // 1. Fetch remote files to check current drive state
        val cloudFiles = try {
            val response = ApiClient.apiService.getFiles(accountId, currentCloudFolderId, null)
            if (response.isSuccessful) {
                val fetched = response.body().orEmpty()
                db.cloudFileDao().insertFiles(fetched.map { LocalCloudFile.fromCloudFile(it, accountId, safeFolderId) })
                fetched
            } else {
                val code = response.code()
                if (code == 429 || code in 500..599) {
                    sawRetryableError = true
                } else if (code in 400..499) {
                    sawFatalClientError = true
                }
                db.cloudFileDao().getFilesForFolder(accountId, safeFolderId).map { it.toCloudFile() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            sawRetryableError = true
            SyncLogger.log("Using cached files for '$pathLabel': ${e.message}", SyncLogLevel.WARNING)
            db.cloudFileDao().getFilesForFolder(accountId, safeFolderId).map { it.toCloudFile() }
        } catch (e: HttpException) {
            if (e.code() == 429 || e.code() in 500..599) {
                sawRetryableError = true
            } else if (e.code() in 400..499) {
                sawFatalClientError = true
            }
            SyncLogger.log("Using cached files for '$pathLabel': ${e.message}", SyncLogLevel.WARNING)
            db.cloudFileDao().getFilesForFolder(accountId, safeFolderId).map { it.toCloudFile() }
        } catch (e: Exception) {
            SyncLogger.log("Using cached files for '$pathLabel': ${e.message}", SyncLogLevel.WARNING)
            db.cloudFileDao().getFilesForFolder(accountId, safeFolderId).map { it.toCloudFile() }
        }

        val localItems = localDir.listFiles()

        // 2. Process and synchronize files — wrap each file upload in its own try/catch so one failing file does not stop the batch
        for (file in localItems) {
            if (file.isFile) {
                val fileName = file.name ?: continue
                processedFilesCount++

                try {
                    val fileSize = file.length()
                    val lastModified = file.lastModified()
                    val sizeFormatted = formatFileSize(fileSize)

                    // Skip files over maxUploadSizeMb
                    if (fileSize > maxUploadBytes) {
                        skippedFilesCount++
                        val maxMb = maxUploadBytes / (1024L * 1024L)
                        SyncLogger.log(
                            "• [$processedFilesCount/$totalFilesToProcess] Skipping '$fileName' ($sizeFormatted exceeds max ${maxMb} MB).",
                            SyncLogLevel.WARNING
                        )
                        continue
                    }

                    // Check if unchanged in LocalUploadRecord (matching fileName, fileSize, and lastModified)
                    val matchingRecord = if (!forceReupload) {
                        db.uploadRecordDao().findMatchingSuccessRecord(
                            fileName = fileName,
                            fileSize = fileSize,
                            lastModified = lastModified,
                            accountId = accountId
                        )
                    } else {
                        null
                    }

                    val previousRecordForFile = if (!forceReupload) {
                        db.uploadRecordDao().getRecord(fileName, accountId, safeFolderId)
                    } else {
                        null
                    }

                    val existsUnchangedOnCloud = !forceReupload &&
                        previousRecordForFile == null &&
                        cloudFiles.any {
                            !it.isFolderItem &&
                                it.effectiveName == fileName &&
                                (it.sizeBytes <= 0L || it.sizeBytes == fileSize) &&
                                (it.updatedAt == null || lastModified <= 0L || it.updatedAt >= lastModified)
                        }

                    val isAlreadyUpToDate = !forceReupload && (matchingRecord != null || existsUnchangedOnCloud)

                    if (isAlreadyUpToDate) {
                        skippedFilesCount++
                        SyncLogger.log("• [$processedFilesCount/$totalFilesToProcess] '$fileName' is already up to date.", SyncLogLevel.INFO)
                        SyncLogger.updateStatus(
                            SyncState.SYNCING,
                            action = "Skipping up-to-date files...",
                            file = fileName,
                            uploadedCount = uploadedFilesCount,
                            totalCount = totalFilesToProcess
                        )
                    } else {
                        val transferId = "backup:${accountId}:${safeFolderId}:${file.uri}"
                        activeTransferId = transferId
                        val existingTransfer = db.transferDao().getById(transferId)
                        if (existingTransfer?.status == com.cloudyte.app.db.TransferStatus.PAUSED.name ||
                            existingTransfer?.status == com.cloudyte.app.db.TransferStatus.CANCELLED.name
                        ) {
                            activeTransferId = null
                            continue
                        }
                        if (existingTransfer == null) {
                            db.transferDao().insert(
                                LocalTransfer(
                                    id = transferId,
                                    accountId = accountId,
                                    fileName = fileName,
                                    localPath = file.uri.toString(),
                                    remotePath = currentCloudFolderId,
                                    direction = TransferDirection.BACKUP.name,
                                    totalBytes = fileSize
                                )
                            )
                        }
                        // Keep the durable idempotency/session record when WorkManager
                        // re-runs a failed file. The server uses this key to recover the
                        // existing provider upload session and return its current offset.
                        // Resetting it here would turn a resumable retry into a restart.
                        if (existingTransfer == null || existingTransfer.idempotencyKey.isNullOrBlank()) {
                            transferRepository.saveResumeMetadata(
                                id = transferId,
                                sessionId = existingTransfer?.providerSessionId,
                                chunkSize = existingTransfer?.chunkSize?.takeIf { it > 0L }
                                    ?: ResumableTransfer.DEFAULT_CHUNK_SIZE,
                                nextChunkIndex = existingTransfer?.nextChunkIndex ?: 0,
                                checksum = existingTransfer?.checksum,
                                idempotencyKey = ResumableTransfer.newIdempotencyKey(transferId)
                            )
                        }
                        transferRepository.markRunning(transferId, fileSize)
                        SyncLogger.log("↑ [$processedFilesCount/$totalFilesToProcess] Uploading '$fileName' ($sizeFormatted)...", SyncLogLevel.INFO)
                        SyncLogger.updateStatus(
                            SyncState.SYNCING,
                            action = "Uploading: $fileName ($sizeFormatted)",
                            file = fileName,
                            uploadedCount = uploadedFilesCount,
                            totalCount = totalFilesToProcess
                        )

                        val uploadResult = if (fileSize >= ResumableTransfer.DEFAULT_CHUNK_SIZE) {
                            UploadHelper.uploadFileResumable(
                                context = context,
                                accountId = accountId,
                                folderId = currentCloudFolderId,
                                uri = file.uri,
                                explicitFileName = fileName,
                                explicitMimeType = file.type,
                                explicitFileSize = fileSize,
                                idempotencyKey = ResumableTransfer.newIdempotencyKey(transferId)
                            )
                        } else {
                            UploadHelper.uploadFile(
                                context = context,
                                accountId = accountId,
                                folderId = currentCloudFolderId,
                                uri = file.uri,
                                explicitFileName = fileName,
                                explicitMimeType = file.type,
                                explicitFileSize = fileSize,
                                idempotencyKey = ResumableTransfer.newIdempotencyKey(transferId)
                            )
                        }

                        if (uploadResult.isSuccess) {
                            transferRepository.updateProgress(transferId, fileSize, fileSize)
                            transferRepository.updateResumableProgress(
                                transferId,
                                fileSize,
                                ((fileSize + ResumableTransfer.DEFAULT_CHUNK_SIZE - 1) / ResumableTransfer.DEFAULT_CHUNK_SIZE).toInt()
                            )
                            transferRepository.markCompleted(transferId)
                            activeTransferId = null
                            uploadedFilesCount++
                            setForeground(createForegroundInfo(context, "Uploaded $fileName", processedFilesCount, totalFilesToProcess))
                            SyncLogger.log("✓ Uploaded '$fileName' successfully", SyncLogLevel.SUCCESS)

                            db.uploadRecordDao().insertRecord(
                                LocalUploadRecord(
                                    localUri = file.uri.toString(),
                                    fileName = fileName,
                                    fileSize = fileSize,
                                    lastModified = lastModified,
                                    targetAccountId = accountId,
                                    targetFolderId = safeFolderId,
                                    uploadedAt = System.currentTimeMillis(),
                                    status = "SUCCESS"
                                )
                            )
                        } else {
                            transferRepository.markFailed(transferId, uploadResult.errorMessage ?: "Upload failed")
                            activeTransferId = null
                            failedFilesCount++
                            if (uploadResult.isRetryable) {
                                sawRetryableError = true
                            } else if (uploadResult.isFatalClientError) {
                                sawFatalClientError = true
                            }
                            SyncLogger.log("✗ Failed '$fileName': ${uploadResult.errorMessage ?: "Upload failed"}", SyncLogLevel.ERROR)
                            db.uploadRecordDao().insertRecord(
                                LocalUploadRecord(
                                    localUri = file.uri.toString(),
                                    fileName = fileName,
                                    fileSize = fileSize,
                                    lastModified = lastModified,
                                    targetAccountId = accountId,
                                    targetFolderId = safeFolderId,
                                    uploadedAt = System.currentTimeMillis(),
                                    status = "FAILED"
                                )
                            )
                        }

                        SyncLogger.updateStatus(
                            SyncState.SYNCING,
                            action = "Syncing files...",
                            file = fileName,
                            uploadedCount = uploadedFilesCount,
                            totalCount = totalFilesToProcess
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    activeTransferId?.let { transferRepository.markFailed(it, e.message ?: "I/O error") }
                    activeTransferId = null
                    failedFilesCount++
                    sawRetryableError = true
                    SyncLogger.log("✗ I/O error uploading '$fileName': ${e.message}", SyncLogLevel.ERROR)
                } catch (e: HttpException) {
                    activeTransferId?.let { transferRepository.markFailed(it, "HTTP ${e.code()}") }
                    activeTransferId = null
                    failedFilesCount++
                    val code = e.code()
                    if (code == 429 || code in 500..599) {
                        sawRetryableError = true
                    } else if (code in 400..499) {
                        sawFatalClientError = true
                    }
                    SyncLogger.log("✗ HTTP $code uploading '$fileName': ${e.message()}", SyncLogLevel.ERROR)
                } catch (e: Exception) {
                    activeTransferId?.let { transferRepository.markFailed(it, e.message ?: "Upload failed") }
                    activeTransferId = null
                    failedFilesCount++
                    if (e.cause is IOException) {
                        sawRetryableError = true
                    }
                    SyncLogger.log("✗ Error uploading '$fileName': ${e.message}", SyncLogLevel.ERROR)
                }
            }
        }

        // 3. Process subdirectories recursively
        for (file in localItems) {
            if (file.isDirectory) {
                val subFolderName = file.name ?: continue
                var cloudFolder = cloudFiles.find { it.isFolderItem && it.effectiveName == subFolderName }
                if (cloudFolder == null) {
                    SyncLogger.log("Creating cloud folder: '$subFolderName'...", SyncLogLevel.INFO)
                    try {
                        val createResp = ApiClient.apiService.createFolder(accountId, FolderRequest(subFolderName, currentCloudFolderId))
                        if (!createResp.isSuccessful) {
                            val code = createResp.code()
                            if (code == 429 || code in 500..599) {
                                sawRetryableError = true
                            } else if (code in 400..499) {
                                sawFatalClientError = true
                            }
                        }
                        val response = ApiClient.apiService.getFiles(accountId, currentCloudFolderId, null)
                        val updatedFiles = response.body().orEmpty()
                        cloudFolder = updatedFiles.find { it.isFolderItem && it.effectiveName == subFolderName }
                        if (cloudFolder != null) {
                            db.cloudFileDao().insertFiles(listOf(LocalCloudFile.fromCloudFile(cloudFolder, accountId, safeFolderId)))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        sawRetryableError = true
                        SyncLogger.log("Failed to create folder '$subFolderName': ${e.message}", SyncLogLevel.ERROR)
                    } catch (e: HttpException) {
                        if (e.code() == 429 || e.code() in 500..599) {
                            sawRetryableError = true
                        } else if (e.code() in 400..499) {
                            sawFatalClientError = true
                        }
                        SyncLogger.log("Failed to create folder '$subFolderName': ${e.message}", SyncLogLevel.ERROR)
                    } catch (e: Exception) {
                        SyncLogger.log("Failed to create folder '$subFolderName': ${e.message}", SyncLogLevel.ERROR)
                    }
                }

                if (cloudFolder != null) {
                    syncDirectory(context, db, accountId, cloudFolder.effectiveId, file, pathLabel)
                }
            }
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

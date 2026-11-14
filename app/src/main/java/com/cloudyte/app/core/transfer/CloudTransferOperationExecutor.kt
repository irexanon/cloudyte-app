package com.cloudyte.app.core.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.EncryptedMetadataEnvelope
import com.cloudyte.app.core.security.ContentKeyManager
import com.cloudyte.app.core.security.ContentKeyMaterial
import com.cloudyte.app.core.security.ContentKeyProvider
import com.cloudyte.app.core.security.CorruptedCiphertextException
import com.cloudyte.app.core.security.CryptoConstants
import com.cloudyte.app.core.security.CryptoException
import com.cloudyte.app.core.security.EncryptedMetadataCodec
import com.cloudyte.app.core.security.FrameAuthenticationException
import com.cloudyte.app.core.security.InvalidEncryptedHeaderException
import com.cloudyte.app.core.security.KeyUnavailableException
import com.cloudyte.app.core.security.KeyUnwrapException
import com.cloudyte.app.core.security.MetadataDecryptionException
import com.cloudyte.app.core.security.PlaintextFileMetadata
import com.cloudyte.app.core.security.SecureFileCleanup
import com.cloudyte.app.core.security.StreamFileCrypto
import com.cloudyte.app.core.security.UnsupportedEncryptionVersionException
import com.cloudyte.app.core.security.WrongKeyIdException
import com.cloudyte.app.data.TransferRepository
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.providers.ProviderRegistry
import com.cloudyte.app.data.providers.UnsupportedProviderOperation
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.LocalTransfer
import com.cloudyte.app.db.TransferDirection
import com.cloudyte.app.ui.screens.UploadHelper
import com.cloudyte.app.ui.screens.UploadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

/** Executes persisted transfer rows through the account-selected provider/API path. */
class CloudTransferOperationExecutor @JvmOverloads constructor(
    private val context: Context,
    private val database: AppDatabase,
    private val transfers: TransferRepository,
    private val providers: ProviderRegistry,
    private val contentKeyProvider: ContentKeyProvider = ContentKeyManager(context),
    private val streamFileCrypto: StreamFileCrypto = StreamFileCrypto(),
    private val metadataCodec: EncryptedMetadataCodec = EncryptedMetadataCodec(),
    private val shouldEncryptNewUploads: suspend () -> Boolean = {
        runCatching { UserPreferences.observe(context).first().isUploadEncryptionEnabled }.getOrDefault(false)
    },
    private val shouldEncryptOfflineFiles: suspend () -> Boolean = {
        runCatching { UserPreferences.observe(context).first().isOfflineEncryptionEnabled }.getOrDefault(false)
    },
    private val shouldDeleteAfterBackupUpload: suspend () -> Boolean = {
        runCatching { UserPreferences.observe(context).first().deleteAfterUpload }.getOrDefault(false)
    },
    private val onLocalDeletionFailed: (suspend (LocalTransfer, String) -> Unit)? = null
) : TransferOperationExecutor {

    override suspend fun onCompleted(transfer: LocalTransfer) {
        SecureFileCleanup.cleanupTransferStaging(
            context = context,
            transferId = transfer.id,
            customStagePath = transfer.encryptedStagePath
        )
        SecureFileCleanup.cleanupDownloadTemp(
            context = context,
            transferId = transfer.id,
            removePartFile = true
        )
        val rawUri = transfer.localUri ?: transfer.localPath ?: return
        val uri = Uri.parse(rawUri)
        if (uri.scheme == "file") {
            val filePath = uri.path
            if (!filePath.isNullOrBlank()) {
                val file = File(filePath)
                val queueDirectory = File(context.filesDir, "queued-text").canonicalFile
                val canonical = file.canonicalFile
                if (canonical.name.startsWith("cloudyte-queued-text-") && canonical.parentFile == queueDirectory) {
                    canonical.delete()
                    return
                }
            }
        }

        if (transfer.direction != TransferDirection.DOWNLOAD.name) {
            val plaintextSize = transfer.plaintextSizeBytes ?: transfer.totalBytes
            val lastModified = resolveSourceLastModified(uri, transfer.createdAt)
            val contentHash = if (!transfer.isEncrypted && !transfer.checksum.isNullOrBlank()) {
                transfer.checksum
            } else {
                computeSha256Safely(uri)
            }
            runCatching {
                database.uploadRecordDao().insertRecord(
                    com.cloudyte.app.db.LocalUploadRecord(
                        localUri = rawUri,
                        fileName = transfer.fileName,
                        fileSize = plaintextSize,
                        lastModified = lastModified,
                        targetAccountId = transfer.accountId,
                        targetFolderId = transfer.parentFolderId ?: transfer.remotePath ?: "root",
                        cloudFileId = transfer.remoteId ?: transfer.fileId,
                        uploadedAt = System.currentTimeMillis(),
                        status = "SUCCESS",
                        contentHash = contentHash
                    )
                )
            }
        }

        if (transfer.direction == TransferDirection.BACKUP.name && runCatching { shouldDeleteAfterBackupUpload() }.getOrDefault(false)) {
            deleteEligibleLocalBackupSource(transfer, uri)
        }
    }

    private suspend fun deleteEligibleLocalBackupSource(transfer: LocalTransfer, uri: Uri) {
        val scheme = uri.scheme
        if (scheme == "file" || scheme.isNullOrBlank()) {
            val path = if (scheme == "file") uri.path.orEmpty() else uri.toString()
            if (path.isBlank()) return
            val file = File(path)
            val canonical = runCatching { file.canonicalFile }.getOrDefault(file)
            val appFiles = runCatching { context.filesDir.canonicalFile }.getOrNull()
            val appCache = runCatching { context.cacheDir.canonicalFile }.getOrNull()
            if ((appFiles != null && canonical.path.startsWith(appFiles.path)) ||
                (appCache != null && canonical.path.startsWith(appCache.path))
            ) {
                return
            }
            if (!file.exists() || !file.isFile) return
            val deleted = runCatching { file.delete() }.getOrDefault(false)
            if (!deleted) {
                val reason = "Could not delete local file '${transfer.fileName}' after verified upload."
                com.cloudyte.app.sync.SyncLogger.log(reason, com.cloudyte.app.sync.SyncLogLevel.WARNING)
                runCatching { onLocalDeletionFailed?.invoke(transfer, reason) }
            } else {
                com.cloudyte.app.sync.SyncLogger.log("Deleted local copy after verified upload: '${transfer.fileName}'.", com.cloudyte.app.sync.SyncLogLevel.INFO)
            }
            return
        }

        if (scheme == "content") {
            val result = runCatching {
                val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                    ?: throw IOException("Local document URI is unavailable")
                if (!doc.exists()) return
                if (!doc.delete()) throw IOException("Provider refused local file deletion")
                true
            }
            result.onSuccess {
                com.cloudyte.app.sync.SyncLogger.log("Deleted local copy after verified upload: '${transfer.fileName}'.", com.cloudyte.app.sync.SyncLogLevel.INFO)
            }.onFailure { err ->
                val reason = "Uploaded '${transfer.fileName}', but failed to delete local copy: ${err.message ?: "permission or provider error"}"
                com.cloudyte.app.sync.SyncLogger.log(reason, com.cloudyte.app.sync.SyncLogLevel.WARNING)
                runCatching { onLocalDeletionFailed?.invoke(transfer, reason) }
            }
        }
    }

    private fun computeSha256Safely(uri: Uri): String? {
        return runCatching {
            val stream = if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
                val path = if (uri.scheme == "file") uri.path.orEmpty() else uri.toString()
                File(path).takeIf { it.isFile }?.inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            } ?: return null
            stream.use { input ->
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            }
        }.getOrNull()
    }

    override suspend fun onCancelled(transfer: LocalTransfer) {
        val latest = database.transferDao().getById(transfer.id) ?: transfer
        SecureFileCleanup.cleanupTransferStaging(
            context = context,
            transferId = transfer.id,
            customStagePath = latest.encryptedStagePath ?: transfer.encryptedStagePath
        )
        SecureFileCleanup.cleanupDownloadTemp(
            context = context,
            transferId = transfer.id,
            removePartFile = true
        )
        if (latest.direction != TransferDirection.DOWNLOAD.name) {
            val sessionId = latest.providerSessionId ?: transfer.providerSessionId
            if (!sessionId.isNullOrBlank()) {
                val accountType = latest.providerType
                    ?: database.accountDao().getAccountById(latest.accountId)?.accountType
                    ?: "cloudyte"
                runCatching {
                    providers.forAccountType(accountType).cancelUploadSession(sessionId)
                }
            }
        } else {
            val dest = latest.localUri ?: latest.localPath
            if (!dest.isNullOrBlank()) {
                resolvePartFileForDestination(latest.id, dest, latest.isEncrypted)?.let { part ->
                    if (part.exists()) part.delete()
                }
            }
        }
    }

    override suspend fun execute(
        transfer: LocalTransfer,
        leaseOwner: String,
        onProgress: suspend (bytes: Long, total: Long, speed: Long, etaSeconds: Long?) -> Unit
    ): TransferOutcome = try {
        checkInterruptedOutcome(transfer.id)?.let { return it }
        when (TransferDirection.valueOf(transfer.direction)) {
            TransferDirection.UPLOAD, TransferDirection.BACKUP -> upload(transfer, leaseOwner, onProgress)
            TransferDirection.DOWNLOAD -> download(transfer, leaseOwner, onProgress)
        }
    } catch (interrupted: TransferInterruptedException) {
        if (interrupted.outcome == TransferOutcome.Cancelled) {
            runCatching { onCancelled(transfer) }
        }
        interrupted.outcome
    } catch (cancelled: CancellationException) {
        val interrupted = checkInterruptedOutcome(transfer.id)
        if (interrupted != null) {
            if (interrupted == TransferOutcome.Cancelled) {
                runCatching { onCancelled(transfer) }
            }
            interrupted
        } else {
            throw cancelled
        }
    } catch (error: CryptoException) {
        mapCryptoOutcome(transfer, error)
    } catch (error: HttpException) {
        mapHttpOutcome(error.code(), error.message())
    } catch (error: FileNotFoundException) {
        TransferOutcome.Permanent("SOURCE_MISSING", error.message ?: "Local source file is missing")
    } catch (error: SecurityException) {
        TransferOutcome.Permanent("PERMISSION_DENIED", error.message ?: "Local permission denied")
    } catch (error: IllegalArgumentException) {
        TransferOutcome.Permanent("INVALID_PARAMETERS", error.message ?: "Invalid transfer parameters")
    } catch (error: UnsupportedProviderOperation) {
        TransferOutcome.Permanent("PROVIDER_UNSUPPORTED_OPERATION", error.message ?: "Provider does not support this transfer operation")
    } catch (error: IOException) {
        if (isExplicitlyUnrecoverableIo(error)) {
            TransferOutcome.Permanent("UNRECOVERABLE_IO", error.message ?: "Unrecoverable I/O error")
        } else {
            TransferOutcome.Retryable("IO_FAILURE", error.message ?: "I/O failure")
        }
    } catch (error: Exception) {
        if (isExplicitlyUnrecoverableException(error)) {
            TransferOutcome.Permanent("TRANSFER_PERMANENT_ERROR", error.message ?: "Unrecoverable transfer error")
        } else {
            TransferOutcome.Retryable("TRANSFER_EXECUTION_ERROR", error.message ?: "Transient transfer execution error")
        }
    }

    private suspend fun upload(
        transfer: LocalTransfer,
        leaseOwner: String,
        onProgress: suspend (Long, Long, Long, Long?) -> Unit
    ): TransferOutcome {
        val source = transfer.localUri ?: transfer.localPath
            ?: return TransferOutcome.Permanent("SOURCE_MISSING", "No local source URI was saved")
        val sourceUri = Uri.parse(source)
        if (sourceUri.scheme == "file" || sourceUri.scheme.isNullOrBlank()) {
            val localFile = File(if (sourceUri.scheme == "file") sourceUri.path.orEmpty() else source)
            if (!localFile.exists() || !localFile.isFile) {
                return TransferOutcome.Permanent("SOURCE_MISSING", "Local source file does not exist")
            }
        }

        val encryptThisUpload = transfer.isEncrypted || shouldEncryptNewUploads()
        if (encryptThisUpload) {
            return uploadEncrypted(transfer, sourceUri, leaseOwner, onProgress)
        }

        val size = resolveSize(sourceUri, transfer.totalBytes)
        if (size < 0L) return TransferOutcome.Permanent("SOURCE_SIZE_UNKNOWN", "Could not determine source size")
        val accountType = transfer.providerType
            ?: database.accountDao().getAccountById(transfer.accountId)?.accountType
            ?: "cloudyte"
        val provider = providers.forAccountType(accountType)
        val progressCalculator = TransferProgressCalculator()
        val key = transfer.idempotencyKey ?: ResumableTransfer.newIdempotencyKey(transfer.id)
        var activeSessionId: String? = transfer.providerSessionId

        val result = UploadHelper.uploadFileResumable(
            context = context,
            accountId = transfer.accountId,
            folderId = transfer.parentFolderId ?: transfer.remotePath,
            uri = sourceUri,
            explicitFileName = transfer.fileName,
            explicitMimeType = resolveSourceMimeType(sourceUri, transfer.fileName),
            explicitFileSize = size,
            idempotencyKey = key,
            provider = provider,
            knownChecksum = transfer.checksum,
            isEncrypted = false,
            onSessionReady = { sessionId, chunkSize, checksum, bytes, chunkIndex ->
                activeSessionId = sessionId
                checkInterruptedOutcome(transfer.id)?.let { outcome ->
                    if (outcome == TransferOutcome.Cancelled) {
                        runCatching { provider.cancelUploadSession(sessionId) }
                    }
                    throw TransferInterruptedException(outcome)
                }
                if (!transfers.saveResumeMetadataOwned(transfer.id, leaseOwner, sessionId, chunkSize, chunkIndex, checksum, key) ||
                    !transfers.updateResumableProgressOwned(transfer.id, leaseOwner, bytes, chunkIndex)
                ) {
                    val interrupted = checkInterruptedOutcome(transfer.id)
                    if (interrupted != null) {
                        if (interrupted == TransferOutcome.Cancelled) {
                            runCatching { provider.cancelUploadSession(sessionId) }
                        }
                        throw TransferInterruptedException(interrupted)
                    }
                    throw CancellationException("Transfer lease lost before upload session checkpoint")
                }
            },
            onProgress = { bytes, chunkIndex ->
                currentCoroutineContext().ensureActive()
                checkInterruptedOutcome(transfer.id)?.let { outcome ->
                    if (outcome == TransferOutcome.Cancelled && !activeSessionId.isNullOrBlank()) {
                        runCatching { provider.cancelUploadSession(activeSessionId!!) }
                    }
                    throw TransferInterruptedException(outcome)
                }
                val metrics = progressCalculator.update(bytes, size)
                onProgress(bytes, size, metrics.bytesPerSecond, metrics.etaSeconds)
                if (!transfers.updateResumableProgressOwned(transfer.id, leaseOwner, bytes, chunkIndex)) {
                    val interrupted = checkInterruptedOutcome(transfer.id)
                    if (interrupted != null) {
                        if (interrupted == TransferOutcome.Cancelled && !activeSessionId.isNullOrBlank()) {
                            runCatching { provider.cancelUploadSession(activeSessionId!!) }
                        }
                        throw TransferInterruptedException(interrupted)
                    }
                    throw CancellationException("Transfer lease lost before upload progress checkpoint")
                }
            }
        )

        checkInterruptedOutcome(transfer.id)?.let { outcome ->
            if (outcome == TransferOutcome.Cancelled && !activeSessionId.isNullOrBlank()) {
                runCatching { provider.cancelUploadSession(activeSessionId!!) }
            }
            return outcome
        }

        return mapUploadResultOutcome(result)
    }

    private suspend fun uploadEncrypted(
        transfer: LocalTransfer,
        sourceUri: Uri,
        leaseOwner: String,
        onProgress: suspend (Long, Long, Long, Long?) -> Unit
    ): TransferOutcome {
        val keyMaterial = contentKeyProvider.getOrCreateContentKey()
        try {
            val prepared = prepareOrReuseEncryptedStaging(
                transfer = transfer,
                sourceUri = sourceUri,
                keyMaterial = keyMaterial
            )

            val accountType = transfer.providerType
                ?: database.accountDao().getAccountById(transfer.accountId)?.accountType
                ?: "cloudyte"
            val provider = providers.forAccountType(accountType)
            val progressCalculator = TransferProgressCalculator()
            val idempotencyKey = transfer.idempotencyKey ?: ResumableTransfer.newIdempotencyKey(transfer.id)
            var activeSessionId: String? = transfer.providerSessionId
            val encryptedTotalBytes = prepared.encryptedSizeBytes

            val result = UploadHelper.uploadFileResumable(
                context = context,
                accountId = transfer.accountId,
                folderId = transfer.parentFolderId ?: transfer.remotePath,
                uri = Uri.fromFile(prepared.stagingFile),
                explicitFileName = prepared.opaqueRemoteName,
                explicitMimeType = CryptoConstants.ENCRYPTED_MIME_TYPE,
                explicitFileSize = encryptedTotalBytes,
                idempotencyKey = idempotencyKey,
                provider = provider,
                knownChecksum = prepared.encryptedChecksum,
                isEncrypted = true,
                encryptionFormatVersion = CryptoConstants.FORMAT_VERSION_V1,
                encryptionKeyId = keyMaterial.keyId,
                encryptedSizeBytes = encryptedTotalBytes,
                encryptedMetadata = prepared.envelope,
                onSessionReady = { sessionId, chunkSize, checksum, bytes, chunkIndex ->
                    activeSessionId = sessionId
                    checkInterruptedOutcome(transfer.id)?.let { outcome ->
                        if (outcome == TransferOutcome.Cancelled) {
                            runCatching { provider.cancelUploadSession(sessionId) }
                            SecureFileCleanup.cleanupTransferStaging(context, transfer.id, prepared.stagingFile.absolutePath)
                        }
                        throw TransferInterruptedException(outcome)
                    }
                    if (!transfers.saveResumeMetadataOwned(transfer.id, leaseOwner, sessionId, chunkSize, chunkIndex, checksum, idempotencyKey) ||
                        !transfers.updateResumableProgressOwned(transfer.id, leaseOwner, bytes, chunkIndex)
                    ) {
                        val interrupted = checkInterruptedOutcome(transfer.id)
                        if (interrupted != null) {
                            if (interrupted == TransferOutcome.Cancelled) {
                                runCatching { provider.cancelUploadSession(sessionId) }
                                SecureFileCleanup.cleanupTransferStaging(context, transfer.id, prepared.stagingFile.absolutePath)
                            }
                            throw TransferInterruptedException(interrupted)
                        }
                        throw CancellationException("Transfer lease lost before encrypted upload session checkpoint")
                    }
                },
                onProgress = { bytes, chunkIndex ->
                    currentCoroutineContext().ensureActive()
                    checkInterruptedOutcome(transfer.id)?.let { outcome ->
                        if (outcome == TransferOutcome.Cancelled && !activeSessionId.isNullOrBlank()) {
                            runCatching { provider.cancelUploadSession(activeSessionId!!) }
                            SecureFileCleanup.cleanupTransferStaging(context, transfer.id, prepared.stagingFile.absolutePath)
                        }
                        throw TransferInterruptedException(outcome)
                    }
                    val metrics = progressCalculator.update(bytes, encryptedTotalBytes)
                    onProgress(bytes, encryptedTotalBytes, metrics.bytesPerSecond, metrics.etaSeconds)
                    if (!transfers.updateResumableProgressOwned(transfer.id, leaseOwner, bytes, chunkIndex)) {
                        val interrupted = checkInterruptedOutcome(transfer.id)
                        if (interrupted != null) {
                            if (interrupted == TransferOutcome.Cancelled && !activeSessionId.isNullOrBlank()) {
                                runCatching { provider.cancelUploadSession(activeSessionId!!) }
                                SecureFileCleanup.cleanupTransferStaging(context, transfer.id, prepared.stagingFile.absolutePath)
                            }
                            throw TransferInterruptedException(interrupted)
                        }
                        throw CancellationException("Transfer lease lost before encrypted upload progress checkpoint")
                    }
                }
            )

            checkInterruptedOutcome(transfer.id)?.let { outcome ->
                if (outcome == TransferOutcome.Cancelled) {
                    if (!activeSessionId.isNullOrBlank()) {
                        runCatching { provider.cancelUploadSession(activeSessionId!!) }
                    }
                    SecureFileCleanup.cleanupTransferStaging(context, transfer.id, prepared.stagingFile.absolutePath)
                }
                return outcome
            }

            val outcome = mapUploadResultOutcome(result)
            if (outcome is TransferOutcome.Completed || outcome is TransferOutcome.Permanent || outcome is TransferOutcome.Cancelled) {
                SecureFileCleanup.cleanupTransferStaging(context, transfer.id, prepared.stagingFile.absolutePath)
            }
            return outcome
        } finally {
            keyMaterial.destroy()
        }
    }

    private data class PreparedEncryptedUpload(
        val stagingFile: File,
        val opaqueRemoteName: String,
        val encryptedSizeBytes: Long,
        val encryptedChecksum: String,
        val plaintextSizeBytes: Long,
        val envelope: EncryptedMetadataEnvelope,
        val envelopeJson: String
    )

    private suspend fun prepareOrReuseEncryptedStaging(
        transfer: LocalTransfer,
        sourceUri: Uri,
        keyMaterial: ContentKeyMaterial
    ): PreparedEncryptedUpload {
        val existingStageFile = transfer.encryptedStagePath?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?: SecureFileCleanup.getStagingFileForTransfer(context, transfer.id)

        if (existingStageFile.isFile && existingStageFile.length() > 0L && !transfer.encryptedMetadata.isNullOrBlank()) {
            try {
                val header = streamFileCrypto.validateEncryptedFile(existingStageFile, expectedKeyId = keyMaterial.keyId)
                val actualLength = existingStageFile.length()
                val expectedLength = transfer.encryptedSizeBytes ?: actualLength
                val actualSha256 = streamFileCrypto.calculateFileSha256(existingStageFile)
                val expectedSha256 = transfer.encryptedChecksum ?: actualSha256
                val envelope = metadataCodec.deserializeEnvelopeFromJson(transfer.encryptedMetadata)

                if (actualLength == expectedLength &&
                    actualSha256.equals(expectedSha256, ignoreCase = true) &&
                    envelope.keyId == keyMaterial.keyId
                ) {
                    val opaqueName = transfer.opaqueRemoteName?.takeIf {
                        it.startsWith(CryptoConstants.OPAQUE_FILE_PREFIX) && it.endsWith(CryptoConstants.OPAQUE_FILE_SUFFIX)
                    } ?: streamFileCrypto.generateOpaqueRemoteFileName()

                    return PreparedEncryptedUpload(
                        stagingFile = existingStageFile,
                        opaqueRemoteName = opaqueName,
                        encryptedSizeBytes = actualLength,
                        encryptedChecksum = actualSha256,
                        plaintextSizeBytes = header.plaintextSize,
                        envelope = envelope,
                        envelopeJson = transfer.encryptedMetadata
                    )
                }
            } catch (_: Exception) {
                // Staging file is invalid or incomplete; delete and re-stage cleanly
            }
        }

        // Remove any partial or invalid staging file before re-creating
        SecureFileCleanup.cleanupTransferStaging(context, transfer.id, transfer.encryptedStagePath)

        val stagingResult = streamFileCrypto.encryptUriToStagingFile(
            context = context,
            sourceUri = sourceUri,
            transferId = transfer.id,
            keyMaterial = keyMaterial,
            existingOpaqueRemoteName = transfer.opaqueRemoteName,
            checkCancelled = {
                if (ActiveTransferSignalRegistry.currentSignal(transfer.id) == ActiveTransferSignal.CANCEL) {
                    throw TransferInterruptedException(TransferOutcome.Cancelled)
                }
                if (ActiveTransferSignalRegistry.currentSignal(transfer.id) == ActiveTransferSignal.PAUSE) {
                    throw TransferInterruptedException(TransferOutcome.Paused)
                }
            }
        )

        val originalName = transfer.fileName.ifBlank { "file" }
        val originalExtension = originalName.substringAfterLast('.', "").lowercase(Locale.US)
        val originalMimeType = resolveSourceMimeType(sourceUri, originalName)
        val now = System.currentTimeMillis()
        val plaintextMetadata = PlaintextFileMetadata(
            version = CryptoConstants.METADATA_VERSION_V1,
            originalName = originalName,
            originalMimeType = originalMimeType,
            originalExtension = originalExtension,
            folderId = transfer.parentFolderId ?: transfer.remotePath,
            relativePath = null,
            plaintextSizeBytes = stagingResult.plaintextSizeBytes,
            plaintextChecksum = stagingResult.plaintextSha256,
            createdAt = transfer.createdAt.takeIf { it > 0L } ?: now,
            modifiedAt = resolveSourceLastModified(sourceUri, now)
        )

        val envelope = metadataCodec.encryptMetadata(plaintextMetadata, keyMaterial)
        val envelopeJson = metadataCodec.serializeEnvelopeToJson(envelope)

        transfers.saveEncryptionMetadata(
            id = transfer.id,
            isEncrypted = true,
            encryptionFormatVersion = CryptoConstants.FORMAT_VERSION_V1,
            encryptionKeyId = keyMaterial.keyId,
            encryptedMetadata = envelopeJson,
            encryptedSizeBytes = stagingResult.encryptedSizeBytes,
            encryptedChecksum = stagingResult.encryptedSha256,
            opaqueRemoteName = stagingResult.opaqueRemoteName,
            encryptedStagePath = stagingResult.stagingFile.absolutePath,
            plaintextSizeBytes = stagingResult.plaintextSizeBytes,
            originalMetadataAvailable = true
        )

        return PreparedEncryptedUpload(
            stagingFile = stagingResult.stagingFile,
            opaqueRemoteName = stagingResult.opaqueRemoteName,
            encryptedSizeBytes = stagingResult.encryptedSizeBytes,
            encryptedChecksum = stagingResult.encryptedSha256,
            plaintextSizeBytes = stagingResult.plaintextSizeBytes,
            envelope = envelope,
            envelopeJson = envelopeJson
        )
    }

    private suspend fun download(
        transfer: LocalTransfer,
        leaseOwner: String,
        onProgress: suspend (Long, Long, Long, Long?) -> Unit
    ): TransferOutcome {
        val remoteId = transfer.remoteId ?: transfer.fileId
            ?: return TransferOutcome.Permanent("REMOTE_ID_MISSING", "No remote file ID was saved")
        val destination = transfer.localUri ?: transfer.localPath
            ?: return TransferOutcome.Permanent("DESTINATION_MISSING", "No local destination URI was saved")

        val cachedCloudFile = database.cloudFileDao().getFileById(remoteId)
        val isKnownEncrypted = transfer.isEncrypted || cachedCloudFile?.isEncrypted == true
        val expectedKeyId = transfer.encryptionKeyId ?: cachedCloudFile?.encryptionKeyId

        val accountType = transfer.providerType
            ?: database.accountDao().getAccountById(transfer.accountId)?.accountType
            ?: "cloudyte"
        val provider = providers.forAccountType(accountType)

        val uri = Uri.parse(destination)
        val isContentUri = uri.scheme == "content"
        val targetFile: File? = if (isContentUri) {
            null
        } else {
            val path = if (uri.scheme == "file") uri.path else destination
            if (path.isNullOrBlank()) {
                return TransferOutcome.Permanent("DESTINATION_UNAVAILABLE", "Invalid destination path")
            }
            File(path).also { it.parentFile?.mkdirs() }
        }

        val partFile = resolvePartFileForDestination(transfer.id, destination, isKnownEncrypted)
            ?: return TransferOutcome.Permanent("DESTINATION_UNAVAILABLE", "Could not allocate temporary download file")
        partFile.parentFile?.mkdirs()

        val supportsResume = provider.capabilities.supportsResumableDownload
        if (!supportsResume && partFile.exists()) {
            partFile.delete()
        }

        var existingOffset = if (supportsResume && partFile.isFile) partFile.length().coerceAtLeast(0L) else 0L
        var rangeHeader = if (existingOffset > 0L) "bytes=$existingOffset-" else null

        var response: Response<ResponseBody> = provider.downloadFile(
            accountId = transfer.accountId,
            fileId = remoteId,
            range = rangeHeader
        )

        val expectedEncryptedOrRawTotal = transfer.encryptedSizeBytes
            ?: cachedCloudFile?.encryptedSizeBytes
            ?: transfer.totalBytes

        if (response.code() == 416 && existingOffset > 0L) {
            if (expectedEncryptedOrRawTotal > 0L && existingOffset == expectedEncryptedOrRawTotal) {
                finalizeDownloadedPartFile(
                    transfer = transfer,
                    partFile = partFile,
                    targetFile = targetFile,
                    uri = uri,
                    isContentUri = isContentUri,
                    isKnownEncrypted = isKnownEncrypted,
                    expectedKeyId = expectedKeyId
                )
                return TransferOutcome.Completed
            }
            partFile.delete()
            existingOffset = 0L
            rangeHeader = null
            transfers.updateResumableProgressOwned(transfer.id, leaseOwner, 0L, 0)
            response = provider.downloadFile(
                accountId = transfer.accountId,
                fileId = remoteId,
                range = null
            )
        }

        if (!response.isSuccessful) {
            val rawError = response.errorBody()?.string()
            val message = ApiClient.parseError(rawError, response.code())
            return mapHttpOutcome(response.code(), message)
        }

        val body = response.body()
            ?: return TransferOutcome.Retryable("EMPTY_DOWNLOAD_BODY", "Server returned empty download stream")

        val isPartialContent = response.code() == 206 && existingOffset > 0L
        val startOffset = if (isPartialContent) existingOffset else 0L
        if (!isPartialContent && partFile.exists() && existingOffset > 0L) {
            partFile.delete()
        }

        val contentRangeTotal = parseContentRangeTotal(response.headers()["Content-Range"])
        val contentLength = body.contentLength()
        val expectedTotal = when {
            contentRangeTotal != null && contentRangeTotal > 0L -> contentRangeTotal
            contentLength >= 0L -> startOffset + contentLength
            expectedEncryptedOrRawTotal > 0L -> expectedEncryptedOrRawTotal
            else -> 0L
        }

        val chunkSize = (transfer.chunkSize.takeIf { it > 0L } ?: (256L * 1024L)).coerceAtLeast(64L * 1024L)
        var lastCheckpointedIndex = if (chunkSize > 0L) (startOffset / chunkSize).toInt() else 0
        if (startOffset > 0L) {
            transfers.updateResumableProgressOwned(transfer.id, leaseOwner, startOffset, lastCheckpointedIndex)
        }

        body.use { responseBody ->
            FileOutputStream(partFile, isPartialContent).use { out ->
                val buffer = ByteArray(64 * 1024)
                var copied = startOffset
                val progressCalculator = TransferProgressCalculator()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    checkInterruptedOutcome(transfer.id)?.let { outcome ->
                        out.flush()
                        if (outcome == TransferOutcome.Cancelled) {
                            partFile.delete()
                        }
                        throw TransferInterruptedException(outcome)
                    }
                    val count = responseBody.byteStream().read(buffer)
                    if (count < 0) break
                    out.write(buffer, 0, count)
                    copied += count
                    val currentChunkIndex = ((copied + chunkSize - 1L) / chunkSize).toInt()
                    val metrics = progressCalculator.update(copied, expectedTotal)
                    onProgress(copied, expectedTotal, metrics.bytesPerSecond, metrics.etaSeconds)
                    if (currentChunkIndex != lastCheckpointedIndex || (expectedTotal > 0L && copied >= expectedTotal)) {
                        lastCheckpointedIndex = currentChunkIndex
                        if (!transfers.updateResumableProgressOwned(transfer.id, leaseOwner, copied, currentChunkIndex)) {
                            checkInterruptedOutcome(transfer.id)?.let { throw TransferInterruptedException(it) }
                            throw CancellationException("Transfer lease lost before download progress checkpoint")
                        }
                    }
                }
                out.flush()
                runCatching { out.fd.sync() }
                if (expectedTotal > 0L && copied < expectedTotal) {
                    throw IOException("Download ended before the expected byte count ($copied / $expectedTotal)")
                }
            }
        }

        finalizeDownloadedPartFile(
            transfer = transfer,
            partFile = partFile,
            targetFile = targetFile,
            uri = uri,
            isContentUri = isContentUri,
            isKnownEncrypted = isKnownEncrypted,
            expectedKeyId = expectedKeyId
        )
        return TransferOutcome.Completed
    }

    private suspend fun finalizeDownloadedPartFile(
        transfer: LocalTransfer,
        partFile: File,
        targetFile: File?,
        uri: Uri,
        isContentUri: Boolean,
        isKnownEncrypted: Boolean,
        expectedKeyId: String?
    ) {
        val hasCyteHeader = streamFileCrypto.hasCyteMagic(partFile)
        val shouldDecrypt = isKnownEncrypted || hasCyteHeader

        if (!shouldDecrypt) {
            commitPartFile(partFile, targetFile, uri, isContentUri)
            return
        }

        val plaintextTempFile = SecureFileCleanup.getPlaintextDownloadTempFile(context, transfer.id)
        var keyMaterial: ContentKeyMaterial? = null
        try {
            val header = streamFileCrypto.validateEncryptedFile(partFile, expectedKeyId = expectedKeyId)
            keyMaterial = contentKeyProvider.getContentKeyForDecryption(expectedKeyId ?: header.keyId)

            streamFileCrypto.decryptEncryptedFileToPlaintext(
                encryptedFile = partFile,
                destinationTempFile = plaintextTempFile,
                keyMaterial = keyMaterial,
                checkCancelled = {
                    if (ActiveTransferSignalRegistry.currentSignal(transfer.id) == ActiveTransferSignal.CANCEL) {
                        throw TransferInterruptedException(TransferOutcome.Cancelled)
                    }
                    if (ActiveTransferSignalRegistry.currentSignal(transfer.id) == ActiveTransferSignal.PAUSE) {
                        throw TransferInterruptedException(TransferOutcome.Paused)
                    }
                }
            )

            val isOfflineVaultDest = !isContentUri && targetFile != null &&
                targetFile.absolutePath.contains("offline_vault")

            if (isOfflineVaultDest && shouldEncryptOfflineFiles()) {
                // Keep offline vault file encrypted at rest (CYTE1 format) after verifying all GCM tags
                SecureFileCleanup.deleteFileSafely(plaintextTempFile)
                commitPartFile(partFile, targetFile, uri, isContentUri = false)
            } else {
                // Commit verified plaintext to destination and delete both encrypted part and temp plaintext
                commitPartFile(plaintextTempFile, targetFile, uri, isContentUri)
                SecureFileCleanup.deleteFileSafely(partFile)
                SecureFileCleanup.deleteFileSafely(plaintextTempFile)
            }
        } catch (t: Throwable) {
            SecureFileCleanup.deleteFileSafely(plaintextTempFile)
            if (t is CryptoException) {
                SecureFileCleanup.deleteFileSafely(partFile)
            }
            throw t
        } finally {
            keyMaterial?.destroy()
        }
    }

    private fun resolvePartFileForDestination(
        transferId: String,
        destination: String,
        isEncrypted: Boolean = false
    ): File? {
        if (isEncrypted) {
            val safeId = transferId.replace(Regex("[^A-Za-z0-9._-]"), "_")
            return SecureFileCleanup.getEncryptedDownloadPartFile(context, safeId)
        }
        val uri = Uri.parse(destination)
        return if (uri.scheme == "content") {
            val stagingDir = SecureFileCleanup.getDownloadStagingDir(context)
            val safeId = transferId.replace(Regex("[^A-Za-z0-9._-]"), "_")
            File(stagingDir, "$safeId.part")
        } else {
            val path = if (uri.scheme == "file") uri.path else destination
            if (path.isNullOrBlank()) return null
            val target = File(path)
            File(target.parentFile ?: context.cacheDir, "${target.name}.part")
        }
    }

    private fun commitPartFile(partFile: File, targetFile: File?, uri: Uri, isContentUri: Boolean) {
        if (isContentUri) {
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw FileNotFoundException("Could not open destination URI: $uri")
            partFile.inputStream().use { input ->
                output.use { out ->
                    input.copyTo(out)
                    out.flush()
                }
            }
            partFile.delete()
        } else {
            val destinationFile = targetFile
                ?: throw IllegalArgumentException("Missing destination file")
            destinationFile.parentFile?.mkdirs()
            if (destinationFile.exists() && !destinationFile.delete()) {
                throw IOException("Unable to replace existing target file: ${destinationFile.name}")
            }
            if (!partFile.renameTo(destinationFile)) {
                partFile.copyTo(destinationFile, overwrite = true)
                partFile.delete()
            }
        }
    }

    private fun mapUploadResultOutcome(result: UploadResult): TransferOutcome {
        return when {
            result.isSuccess -> TransferOutcome.Completed
            result.isIoError -> TransferOutcome.WaitingForNetwork("NETWORK_IO", result.errorMessage ?: "Network interrupted")
            result.statusCode == 401 -> TransferOutcome.WaitingForAuth("AUTH_REQUIRED", result.errorMessage ?: "Authentication required")
            result.statusCode > 0 -> mapHttpOutcome(result.statusCode, result.errorMessage ?: "Upload failed")
            isExplicitlyUnrecoverableMessage(result.errorMessage) ->
                TransferOutcome.Permanent("UPLOAD_FAILED", result.errorMessage ?: "Upload permanently failed")
            else -> TransferOutcome.Retryable("UPLOAD_RETRYABLE", result.errorMessage ?: "Upload failed")
        }
    }

    private fun mapCryptoOutcome(transfer: LocalTransfer, error: CryptoException): TransferOutcome {
        if (transfer.direction != TransferDirection.DOWNLOAD.name) {
            SecureFileCleanup.cleanupTransferStaging(context, transfer.id, transfer.encryptedStagePath)
        } else {
            SecureFileCleanup.cleanupDownloadTemp(context, transfer.id, removePartFile = true)
        }
        val code = when (error) {
            is KeyUnavailableException, is KeyUnwrapException -> "KEY_UNAVAILABLE"
            is WrongKeyIdException -> "WRONG_KEY_ID"
            is UnsupportedEncryptionVersionException -> "UNSUPPORTED_ENCRYPTION_VERSION"
            is InvalidEncryptedHeaderException -> "INVALID_ENCRYPTED_HEADER"
            is FrameAuthenticationException -> "FRAME_AUTH_FAILED"
            is MetadataDecryptionException -> "METADATA_DECRYPTION_FAILED"
            is CorruptedCiphertextException -> "CORRUPTED_CIPHERTEXT"
            else -> "CRYPTO_ERROR"
        }
        return TransferOutcome.Permanent(code, error.message ?: "Encryption/decryption failed")
    }

    private fun resolveSourceMimeType(uri: Uri, fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext.isNotBlank()) {
            val fromExt = runCatching {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            }.getOrNull()
            if (!fromExt.isNullOrBlank()) return fromExt
        }
        if (uri.scheme == "content") {
            val fromResolver = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            if (!fromResolver.isNullOrBlank()) return fromResolver
        }
        return "application/octet-stream"
    }

    private fun resolveSourceLastModified(uri: Uri, fallback: Long): Long {
        if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
            val path = if (uri.scheme == "file") uri.path.orEmpty() else uri.toString()
            val file = File(path)
            if (file.isFile && file.lastModified() > 0L) return file.lastModified()
        }
        return fallback
    }

    private fun parseContentRangeTotal(contentRange: String?): Long? {
        if (contentRange.isNullOrBlank()) return null
        val slashIndex = contentRange.lastIndexOf('/')
        if (slashIndex < 0 || slashIndex == contentRange.lastIndex) return null
        val totalPart = contentRange.substring(slashIndex + 1).trim()
        if (totalPart == "*") return null
        return totalPart.toLongOrNull()
    }

    private suspend fun checkInterruptedOutcome(transferId: String): TransferOutcome? {
        when (ActiveTransferSignalRegistry.currentSignal(transferId)) {
            ActiveTransferSignal.CANCEL -> return TransferOutcome.Cancelled
            ActiveTransferSignal.PAUSE -> return TransferOutcome.Paused
            null -> Unit
        }
        val row = database.transferDao().getById(transferId) ?: return null
        return when {
            row.state in setOf(TransferState.CANCELLING.name, TransferState.CANCELLED.name) ||
                row.status == "CANCELLED" -> TransferOutcome.Cancelled
            row.state in setOf(TransferState.PAUSING.name, TransferState.PAUSED.name) ||
                row.status == "PAUSED" -> TransferOutcome.Paused
            else -> null
        }
    }

    private fun mapHttpOutcome(statusCode: Int, message: String): TransferOutcome {
        if (statusCode == 507 || message.contains("quota", ignoreCase = true)) {
            return TransferOutcome.Permanent("QUOTA_EXCEEDED", message, httpStatus = statusCode)
        }
        return when (val mapped = TransferErrorMapper.fromHttp(statusCode)) {
            is TransferError.Authentication -> TransferOutcome.WaitingForAuth(mapped.code, message)
            is TransferError.Network -> TransferOutcome.WaitingForNetwork(mapped.code, message)
            is TransferError.Permission, is TransferError.InvalidRequest, is TransferError.Storage ->
                TransferOutcome.Permanent(mapped.code, message, httpStatus = statusCode)
            else -> if (statusCode in UploadResult.PERMANENT_HTTP_CODES) {
                TransferOutcome.Permanent(mapped.code, message, httpStatus = statusCode)
            } else {
                TransferOutcome.Retryable(mapped.code, message, httpStatus = statusCode)
            }
        }
    }

    private fun isExplicitlyUnrecoverableIo(error: IOException): Boolean {
        val msg = error.message.orEmpty()
        return error is FileNotFoundException ||
            error is CryptoException ||
            msg.contains("Cannot open local file stream", ignoreCase = true) ||
            msg.contains("File size is required", ignoreCase = true) ||
            msg.contains("No such file or directory", ignoreCase = true)
    }

    private fun isExplicitlyUnrecoverableException(error: Exception): Boolean {
        return error is UnsupportedProviderOperation ||
            error is CryptoException ||
            error is IllegalArgumentException ||
            error is SecurityException ||
            error is FileNotFoundException ||
            isExplicitlyUnrecoverableMessage(error.message)
    }

    private fun isExplicitlyUnrecoverableMessage(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        return message.contains("Cannot open local file stream", ignoreCase = true) ||
            message.contains("File size is required", ignoreCase = true) ||
            message.contains("quota exceeded", ignoreCase = true) ||
            message.contains("insufficient storage", ignoreCase = true)
    }

    private fun resolveSize(uri: Uri, declared: Long): Long {
        if (declared > 0L) return declared
        if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
            val path = if (uri.scheme == "file") uri.path.orEmpty() else uri.toString()
            val file = File(path)
            return if (file.isFile) file.length() else -1L
        }
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index)
            }
        }
        return try { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L }
        catch (_: Exception) { -1L }
    }
}

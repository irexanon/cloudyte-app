package com.cloudyte.app.core.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.data.TransferRepository
import com.cloudyte.app.data.CloudFileRepository
import com.cloudyte.app.data.providers.CloudProvider
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
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

/** Executes persisted transfer rows through the account-selected provider/API path. */
class CloudTransferOperationExecutor(
    private val context: Context,
    private val database: AppDatabase,
    private val transfers: TransferRepository,
    private val providers: ProviderRegistry
) : TransferOperationExecutor {
    override suspend fun onCompleted(transfer: LocalTransfer) {
        if (transfer.direction != TransferDirection.DOWNLOAD.name) {
            val folderId = transfer.parentFolderId?.takeIf { it.isNotBlank() }
                ?: transfer.remotePath?.takeIf { it.isNotBlank() }
                ?: "root"
            val repository = CloudFileRepository(database, ApiClient.apiService)
            runCatching { repository.refresh(transfer.accountId, folderId, force = true) }
            runCatching { repository.refreshStorage(transfer.accountId, force = true) }
        }
        val uri = Uri.parse(transfer.localUri ?: return)
        if (uri.scheme != "file") return
        val file = File(uri.path ?: return)
        val queueDirectory = File(context.filesDir, "queued-text").canonicalFile
        val canonical = file.canonicalFile
        if (canonical.name.startsWith("cloudyte-queued-text-") && canonical.parentFile == queueDirectory) {
            canonical.delete()
        }
    }

    override suspend fun onCancelled(transfer: LocalTransfer) {
        val latest = database.transferDao().getById(transfer.id) ?: transfer
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
                resolvePartFileForDestination(latest.id, dest)?.let { part ->
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
        // Default fallback for unexpected/transient exceptions (socket resets, JSON/protocol glitches,
        // unknown runtime errors) is Retryable so transient faults never permanently kill user transfers.
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
        val uri = Uri.parse(source)
        if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
            val localFile = File(if (uri.scheme == "file") uri.path.orEmpty() else source)
            if (!localFile.exists() || !localFile.isFile) {
                return TransferOutcome.Permanent("SOURCE_MISSING", "Local source file does not exist")
            }
        }
        val size = resolveSize(uri, transfer.totalBytes)
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
            uri = uri,
            explicitFileName = transfer.fileName,
            explicitMimeType = "application/octet-stream",
            explicitFileSize = size,
            idempotencyKey = key,
            provider = provider,
            knownChecksum = transfer.checksum,
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

    private suspend fun download(
        transfer: LocalTransfer,
        leaseOwner: String,
        onProgress: suspend (Long, Long, Long, Long?) -> Unit
    ): TransferOutcome {
        val remoteId = transfer.remoteId ?: transfer.fileId
            ?: return TransferOutcome.Permanent("REMOTE_ID_MISSING", "No remote file ID was saved")
        val destination = transfer.localUri ?: transfer.localPath
            ?: return TransferOutcome.Permanent("DESTINATION_MISSING", "No local destination URI was saved")

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

        val partFile = resolvePartFileForDestination(transfer.id, destination)
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

        if (response.code() == 416 && existingOffset > 0L) {
            if (transfer.totalBytes > 0L && existingOffset == transfer.totalBytes) {
                commitPartFile(partFile, targetFile, uri, isContentUri)
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
            transfer.totalBytes > 0L -> transfer.totalBytes
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

        commitPartFile(partFile, targetFile, uri, isContentUri)
        return TransferOutcome.Completed
    }

    private fun resolvePartFileForDestination(transferId: String, destination: String): File? {
        val uri = Uri.parse(destination)
        return if (uri.scheme == "content") {
            val stagingDir = File(context.cacheDir, "download_staging").apply { mkdirs() }
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
            msg.contains("Cannot open local file stream", ignoreCase = true) ||
            msg.contains("File size is required", ignoreCase = true) ||
            msg.contains("No such file or directory", ignoreCase = true)
    }

    private fun isExplicitlyUnrecoverableException(error: Exception): Boolean {
        return error is UnsupportedProviderOperation ||
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

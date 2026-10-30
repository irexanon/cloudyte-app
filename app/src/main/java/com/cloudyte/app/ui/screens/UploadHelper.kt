package com.cloudyte.app.ui.screens

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.UploadSessionRequest
import com.cloudyte.app.data.providers.CloudProvider
import com.cloudyte.app.data.providers.CloudyteProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class UploadResult(
    val isSuccess: Boolean,
    val statusCode: Int = 0,
    val errorMessage: String? = null,
    val fileName: String = "",
    val isIoError: Boolean = false
) {
    val isRetryable: Boolean
        get() = isIoError || statusCode == 429 || statusCode in 500..599

    val isFatalClientError: Boolean
        get() = !isIoError && statusCode in 400..499 && statusCode != 429
}

private class ContentUriStreamRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val mediaType: MediaType?,
    private val contentLengthBytes: Long = -1L
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = if (contentLengthBytes >= 0L) contentLengthBytes else -1L

    override fun writeTo(sink: BufferedSink) {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open input stream for URI: $uri")
        inputStream.source().use { source ->
            sink.writeAll(source)
        }
    }
}

object UploadHelper {
    suspend fun uploadFile(
        context: Context,
        accountId: String,
        folderId: String?,
        uri: Uri,
        explicitFileName: String? = null,
        explicitMimeType: String? = null,
        explicitFileSize: Long = -1L,
        idempotencyKey: String? = null,
        checksum: String? = null,
        uploadSession: String? = null
    ): UploadResult {
        return withContext(Dispatchers.IO) {
            var safeFileName = explicitFileName?.takeIf { it.isNotBlank() } ?: "file"
            try {
                val contentResolver = context.contentResolver
                var resolvedName = explicitFileName
                var resolvedSize = explicitFileSize

                if (resolvedName.isNullOrBlank() || resolvedSize < 0L) {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            if (resolvedName.isNullOrBlank()) {
                                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (nameIndex != -1) {
                                    resolvedName = cursor.getString(nameIndex)
                                }
                            }
                            if (resolvedSize < 0L) {
                                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                                    resolvedSize = cursor.getLong(sizeIndex)
                                }
                            }
                        }
                    }
                }
                safeFileName = resolvedName?.takeIf { it.isNotBlank() } ?: "file_${System.currentTimeMillis()}"

                val extension = safeFileName.substringAfterLast('.', "").lowercase()
                val mimeFromExt = if (extension.isNotBlank()) {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                } else {
                    null
                }
                val resolvedMimeType = explicitMimeType
                    ?: mimeFromExt
                    ?: contentResolver.getType(uri)
                    ?: "application/octet-stream"

                // Verify stream can be opened before starting request
                contentResolver.openInputStream(uri)?.close()
                    ?: return@withContext UploadResult(
                        isSuccess = false,
                        errorMessage = "Cannot open local file stream",
                        fileName = safeFileName,
                        isIoError = true
                    )

                val requestBody = ContentUriStreamRequestBody(
                    contentResolver = contentResolver,
                    uri = uri,
                    mediaType = resolvedMimeType.toMediaTypeOrNull(),
                    contentLengthBytes = resolvedSize
                )

                val filePart = MultipartBody.Part.createFormData("file", safeFileName, requestBody)
                val folderIdPart = if (!folderId.isNullOrBlank()) {
                    folderId.toRequestBody("text/plain".toMediaTypeOrNull())
                } else {
                    null
                }

                val response = ApiClient.apiService.uploadFile(
                    accountId = accountId,
                    folderIdQuery = if (!folderId.isNullOrBlank()) folderId else null,
                    file = filePart,
                    folderIdPart = folderIdPart,
                    parentIdPart = folderIdPart,
                    idempotencyKey = idempotencyKey,
                    checksum = checksum,
                    uploadSession = uploadSession
                )

                if (response.isSuccessful) {
                    UploadResult(isSuccess = true, statusCode = response.code(), fileName = safeFileName)
                } else {
                    val rawErr = response.errorBody()?.string()
                    val parsedErr = ApiClient.parseError(rawErr, response.code())
                    Log.e("UploadHelper", "Upload failed: HTTP ${response.code()} $parsedErr (Raw: $rawErr)")
                    UploadResult(
                        isSuccess = false,
                        statusCode = response.code(),
                        errorMessage = "HTTP ${response.code()}: $parsedErr",
                        fileName = safeFileName,
                        isIoError = false
                    )
                }
            } catch (e: HttpException) {
                Log.e("UploadHelper", "HTTP exception during upload", e)
                UploadResult(
                    isSuccess = false,
                    statusCode = e.code(),
                    errorMessage = "HTTP ${e.code()}: ${e.message()}",
                    fileName = safeFileName,
                    isIoError = false
                )
            } catch (e: IOException) {
                Log.e("UploadHelper", "IOException during upload", e)
                UploadResult(
                    isSuccess = false,
                    errorMessage = e.localizedMessage ?: e.message ?: "Network I/O error",
                    fileName = safeFileName,
                    isIoError = true
                )
            } catch (e: Exception) {
                Log.e("UploadHelper", "Exception during upload", e)
                UploadResult(
                    isSuccess = false,
                    errorMessage = e.localizedMessage ?: e.message ?: "Unknown upload error",
                    fileName = safeFileName,
                    isIoError = e.cause is IOException
                )
            }
        }
    }

    suspend fun uploadFileResumable(
        context: Context,
        accountId: String,
        folderId: String?,
        uri: Uri,
        explicitFileName: String,
        explicitMimeType: String,
        explicitFileSize: Long,
        idempotencyKey: String,
        provider: CloudProvider = CloudyteProvider(ApiClient.apiService)
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val totalBytes = explicitFileSize
            if (totalBytes < 0L) throw IOException("File size is required for resumable upload")
            val digest = MessageDigest.getInstance("SHA-256")
            resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            } ?: throw IOException("Cannot open local file stream")
            val checksum = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val sessionResponse = provider.createUploadSession(
                accountId,
                UploadSessionRequest(
                    fileName = explicitFileName,
                    mimeType = explicitMimeType,
                    size = totalBytes,
                    folderId = folderId,
                    checksum = checksum,
                    idempotencyKey = idempotencyKey
                )
            )
            if (!sessionResponse.isSuccessful || sessionResponse.body() == null) {
                return@withContext UploadResult(false, sessionResponse.code(), "Could not create upload session", explicitFileName)
            }
            val session = sessionResponse.body()!!
            var offset = session.receivedBytes
            val chunkSize = session.chunkSize.coerceAtLeast(64L * 1024L).coerceAtMost(16L * 1024L * 1024L)
            resolver.openInputStream(uri)?.use { input ->
                var skipped = 0L
                while (skipped < offset) {
                    val n = input.skip(offset - skipped)
                    if (n <= 0L) throw IOException("Could not seek to resumable upload offset $offset")
                    skipped += n
                }
                while (offset < totalBytes) {
                    val expected = minOf(chunkSize, totalBytes - offset).toInt()
                    val bytes = ByteArray(expected)
                    var readTotal = 0
                    while (readTotal < expected) {
                        val read = input.read(bytes, readTotal, expected - readTotal)
                        if (read <= 0) throw IOException("Unexpected end of local file")
                        readTotal += read
                    }
                    val body = bytes.toRequestBody(explicitMimeType.toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("chunk", explicitFileName, body)
                    val response = provider.uploadChunk(
                        sessionId = session.sessionId,
                        contentRange = "bytes $offset-${offset + readTotal - 1}/$totalBytes",
                        chunk = part
                    )
                    if (!response.isSuccessful || response.body() == null) {
                        return@withContext UploadResult(false, response.code(), "Chunk upload failed at offset $offset", explicitFileName)
                    }
                    offset = response.body()!!.nextOffset
                }
            } ?: throw IOException("Cannot open local file stream")
            val complete = provider.completeUploadSession(session.sessionId)
            if (complete.isSuccessful && complete.body()?.success == true) {
                UploadResult(true, complete.code(), fileName = explicitFileName)
            } else {
                UploadResult(false, complete.code(), "Could not finalize upload session", explicitFileName)
            }
        } catch (e: IOException) {
            UploadResult(false, errorMessage = e.message ?: "Resumable upload I/O error", fileName = explicitFileName, isIoError = true)
        } catch (e: Exception) {
            UploadResult(false, errorMessage = e.message ?: "Resumable upload failed", fileName = explicitFileName, isIoError = e.cause is IOException)
        }
    }

    suspend fun uploadTextContent(
        context: Context,
        accountId: String,
        folderId: String?,
        fileName: String,
        content: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                tempFile = File(context.cacheDir, fileName)
                tempFile.writeText(content)
                val requestFile = tempFile.asRequestBody("text/plain".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)
                val folderIdPart = if (!folderId.isNullOrBlank()) {
                    folderId.toRequestBody("text/plain".toMediaTypeOrNull())
                } else {
                    null
                }
                val response = ApiClient.apiService.uploadFile(
                    accountId = accountId,
                    folderIdQuery = if (!folderId.isNullOrBlank()) folderId else null,
                    file = body,
                    folderIdPart = folderIdPart,
                    parentIdPart = folderIdPart
                )
                response.isSuccessful
            } catch (e: Exception) {
                e.printStackTrace()
                false
            } finally {
                try {
                    tempFile?.delete()
                } catch (_: Exception) {
                }
            }
        }
    }
}

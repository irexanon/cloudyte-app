package com.cloudyte.app.core.cache

import android.content.Context
import com.cloudyte.app.CloudyteApplication
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.api.OfflineFileManager
import com.cloudyte.app.core.security.ContentKeyManager
import com.cloudyte.app.core.security.ContentKeyMaterial
import com.cloudyte.app.core.security.SecureFileCleanup
import com.cloudyte.app.core.security.StreamFileCrypto
import com.cloudyte.app.data.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Ephemeral, cancellable preview fetcher backed by [PreviewStore] and [SecureFileCleanup].
 *
 * Unlike [OfflineFileManager.downloadFile], this never inserts rows into the persistent
 * `transfers` table or triggers `ActiveTransferPill` overlays. Leaving a preview immediately
 * cancels the underlying OkHttp call and discards partial temp files.
 */
object PreviewFetcher {

    suspend fun resolveAccountId(
        context: Context,
        accountId: String,
        file: CloudFile?
    ): String {
        file?.effectiveAccountId?.takeIf { it.isNotBlank() }?.let { return it }
        if (accountId.isNotBlank() && accountId != "all" && accountId != "root") {
            return accountId
        }
        val app = context.applicationContext as? CloudyteApplication ?: return ""
        val fid = file?.effectiveId.orEmpty()
        if (fid.isNotBlank()) {
            val fromFile = app.container.database.cloudFileDao().getFileById(fid)?.accountId
                ?.takeIf { it.isNotBlank() && it != "all" && it != "root" }
            if (!fromFile.isNullOrBlank()) return fromFile
        }
        return app.container.database.accountDao().getAllAccounts()
            .firstOrNull()?.id?.takeIf { it.isNotBlank() }
            .orEmpty()
    }

    suspend fun resolveCachedOnly(
        context: Context,
        accountId: String,
        file: CloudFile
    ): File? = withContext(Dispatchers.IO) {
        val fid = file.effectiveId
        if (fid.isBlank()) return@withContext null
        val acc = resolveAccountId(context, accountId, file)
        if (acc.isBlank()) return@withContext null

        val offline = OfflineFileManager.findOfflineFile(context, acc, fid)
            ?.takeIf { it.isFile && it.length() > 0L }
        if (offline != null) {
            return@withContext maybeDecryptForViewer(context, acc, file, offline)
        }

        val app = context.applicationContext as? CloudyteApplication
        val providerType = app?.container?.database?.accountDao()?.getAccountById(acc)?.accountType ?: "unknown"
        val rev = file.updatedAt?.toString() ?: file.time?.toString()
        val key = CacheKeyFactory.preview(acc, providerType, fid, rev)
        val previewStore = app?.container?.previewStore ?: PreviewStore(context)
        val cached = previewStore.get(key)?.takeIf { it.isFile && it.length() > 0L }
            ?: return@withContext null
        maybeDecryptForViewer(context, acc, file, cached)
    }

    suspend fun resolvePreviewFile(
        context: Context,
        accountId: String,
        file: CloudFile,
        onProgress: ((Float) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val fid = file.effectiveId
            if (fid.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("File ID is empty"))
            }
            val acc = resolveAccountId(context, accountId, file)
            if (acc.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Account unavailable for preview"))
            }

            // 1. Reuse explicit offline download if already present on device.
            val offline = OfflineFileManager.findOfflineFile(context, acc, fid)
                ?.takeIf { it.isFile && it.length() > 0L }
            if (offline != null) {
                val ready = maybeDecryptForViewer(context, acc, file, offline)
                onProgress?.invoke(1f)
                return@withContext Result.success(ready)
            }

            // 2. Reuse cached preview entry if present.
            val app = context.applicationContext as? CloudyteApplication
            val providerType = app?.container?.database?.accountDao()?.getAccountById(acc)?.accountType ?: "unknown"
            val rev = file.updatedAt?.toString() ?: file.time?.toString()
            val key = CacheKeyFactory.preview(acc, providerType, fid, rev)
            val previewStore = app?.container?.previewStore ?: PreviewStore(context)
            val cached = previewStore.get(key)?.takeIf { it.isFile && it.length() > 0L }
            if (cached != null) {
                val ready = maybeDecryptForViewer(context, acc, file, cached)
                onProgress?.invoke(1f)
                return@withContext Result.success(ready)
            }

            // 3. Stream ephemerally via cancellable OkHttp call into PreviewStore (never touches TransferDao).
            val url = ApiClient.getFileUrl(acc, fid, preview = !file.isEncrypted)
            val requestBuilder = Request.Builder().url(url)
            val token = ApiClient.getToken()
            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            val previewFile = streamCallToPreviewStore(
                previewStore = previewStore,
                key = key,
                request = requestBuilder.build(),
                expectedBytes = (if (file.isEncrypted) file.encryptedSizeBytes ?: file.sizeBytes else file.sizeBytes).coerceAtLeast(0L),
                onProgress = onProgress
            )

            val maxCacheMb = runCatching { UserPreferences.observe(context).first().maxCacheSizeMb }
                .getOrDefault(256).coerceAtLeast(64)
            runCatching { previewStore.enforceQuota(maxCacheMb.toLong() * 1024L * 1024L) }

            val ready = maybeDecryptForViewer(context, acc, file, previewFile)
            onProgress?.invoke(1f)
            Result.success(ready)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun streamCallToPreviewStore(
        previewStore: PreviewStore,
        key: String,
        request: Request,
        expectedBytes: Long,
        onProgress: ((Float) -> Unit)?
    ): File {
        val tempFile = suspendCancellableCoroutine<File> { continuation ->
            val call = ApiClient.okHttpClient.newCall(request)
            continuation.invokeOnCancellation {
                runCatching { call.cancel() }
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        if (!res.isSuccessful) {
                            val raw = runCatching { res.body?.string() }.getOrNull()
                            val msg = ApiClient.parseError(raw, res.code)
                            if (continuation.isActive) {
                                continuation.resumeWithException(IOException(msg))
                            }
                            return
                        }
                        val body = res.body
                        if (body == null) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(IOException("Empty preview response"))
                            }
                            return
                        }
                        val total = body.contentLength().takeIf { it > 0L } ?: expectedBytes
                        val temp = previewStore.createTempFile(key)
                        try {
                            FileOutputStream(temp).use { out ->
                                val buffer = ByteArray(64 * 1024)
                                var copied = 0L
                                val input = body.byteStream()
                                while (true) {
                                    if (!continuation.isActive || call.isCanceled()) {
                                        throw CancellationException("Preview cancelled")
                                    }
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    out.write(buffer, 0, read)
                                    copied += read
                                    if (total > 0L) {
                                        onProgress?.invoke((copied.toFloat() / total.toFloat()).coerceIn(0f, 0.98f))
                                    }
                                }
                                out.flush()
                            }
                            if (!continuation.isActive || call.isCanceled()) {
                                SecureFileCleanup.deleteFileSafely(temp)
                                return
                            }
                            continuation.resume(temp)
                        } catch (cancelled: CancellationException) {
                            SecureFileCleanup.deleteFileSafely(temp)
                            if (continuation.isActive) {
                                continuation.resumeWithException(cancelled)
                            }
                        } catch (e: Exception) {
                            SecureFileCleanup.deleteFileSafely(temp)
                            if (continuation.isActive) {
                                continuation.resumeWithException(e)
                            }
                        }
                    }
                }
            })
        }
        return try {
            coroutineContext.ensureActive()
            previewStore.commitTempFile(key, tempFile)
        } catch (t: Throwable) {
            SecureFileCleanup.deleteFileSafely(tempFile)
            throw t
        }
    }

    private suspend fun maybeDecryptForViewer(
        context: Context,
        accountId: String,
        file: CloudFile,
        sourceFile: File
    ): File {
        coroutineContext.ensureActive()
        val streamCrypto = StreamFileCrypto()
        if (!streamCrypto.hasCyteMagic(sourceFile)) {
            return sourceFile
        }
        val app = context.applicationContext as? CloudyteApplication
        val keyProvider = app?.container?.contentKeyManager ?: ContentKeyManager(context)
        val viewCacheDir = SecureFileCleanup.getDecryptedViewCacheDir(context)
        val safeAcc = accountId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val safeFid = file.effectiveId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val safeName = file.effectiveName.ifBlank { "decrypted_${file.effectiveId}" }
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val viewTargetFile = File(viewCacheDir, "view_${safeAcc}_${safeFid}_$safeName")
        if (viewTargetFile.isFile && viewTargetFile.length() > 0L) {
            return viewTargetFile
        }
        val viewTempFile = File(viewCacheDir, "view_${safeAcc}_${safeFid}_$safeName.tmp")
        var keyMaterial: ContentKeyMaterial? = null
        try {
            val currentJob = coroutineContext[kotlinx.coroutines.Job]
            val header = streamCrypto.validateEncryptedFile(sourceFile, expectedKeyId = file.encryptionKeyId)
            keyMaterial = keyProvider.getContentKeyForDecryption(file.encryptionKeyId ?: header.keyId)
            streamCrypto.decryptEncryptedFileToPlaintext(
                encryptedFile = sourceFile,
                destinationTempFile = viewTempFile,
                keyMaterial = keyMaterial,
                checkCancelled = {
                    if (currentJob?.isActive == false) {
                        throw CancellationException("Preview decryption cancelled")
                    }
                }
            )
            if (viewTargetFile.exists()) viewTargetFile.delete()
            if (!viewTempFile.renameTo(viewTargetFile)) {
                viewTempFile.copyTo(viewTargetFile, overwrite = true)
                viewTempFile.delete()
            }
            return viewTargetFile
        } catch (t: Throwable) {
            SecureFileCleanup.deleteFileSafely(viewTempFile)
            SecureFileCleanup.deleteFileSafely(viewTargetFile)
            throw t
        } finally {
            keyMaterial?.destroy()
        }
    }
}

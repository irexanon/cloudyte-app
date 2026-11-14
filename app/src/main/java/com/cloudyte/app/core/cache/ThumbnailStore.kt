package com.cloudyte.app.core.cache

import android.content.Context
import android.graphics.BitmapFactory
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.db.ThumbnailDao
import com.cloudyte.app.db.ThumbnailEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Single thumbnail policy boundary for UI and background callers.
 * Returned strings are either app-private file paths or a compatibility remote fallback URL.
 */
class ThumbnailStore @JvmOverloads constructor(
    context: Context,
    private val dao: ThumbnailDao,
    private val contentKeyProvider: com.cloudyte.app.core.security.ContentKeyProvider =
        com.cloudyte.app.core.security.ContentKeyManager(context.applicationContext),
    private val streamFileCrypto: com.cloudyte.app.core.security.StreamFileCrypto =
        com.cloudyte.app.core.security.StreamFileCrypto()
) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "cloudyte_thumbnails").apply { mkdirs() }
    private val quotaManager = CacheQuotaManager(appContext, dao)
    private val concurrencySemaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
    private val inFlightMutex = Mutex()
    private val inFlightRequests: MutableMap<String, Deferred<File?>> = mutableMapOf()

    suspend fun resolve(
        accountId: String,
        providerType: String,
        file: CloudFile,
        sizePx: Int = 256,
        localSource: File? = null
    ): String? = withContext(Dispatchers.IO) {
        val resolvedAccId = when {
            file.effectiveAccountId.isNotBlank() -> file.effectiveAccountId
            accountId.isNotBlank() && accountId != "all" && accountId != "root" -> accountId
            else -> ""
        }
        if (resolvedAccId.isBlank() || file.effectiveId.isBlank() || file.isFolderItem) return@withContext null
        // If encrypted metadata is not available locally, never attempt thumbnail resolution
        if (file.isEncrypted && !file.originalMetadataAvailable) return@withContext null

        val normalizedSize = sizePx.coerceIn(64, 512)
        val key = CacheKeyFactory.thumbnail(resolvedAccId, providerType, file, normalizedSize)
        // CRITICAL: Never use remote fallback URL for encrypted files
        val fallback = if (file.isEncrypted) null else fallbackUrl(resolvedAccId, file)

        val now = System.currentTimeMillis()
        val existing = dao.get(key)
        if (existing != null) {
            val cached = File(existing.path)
            if (cached.isFile && cached.length() > 0L) {
                if (now - existing.lastAccessAt >= ACCESS_TOUCH_INTERVAL_MS) dao.touch(key, now)
                return@withContext cached.absolutePath
            }
            dao.delete(key)
        }

        val validLocalSource = localSource?.takeIf { it.isFile && it.length() > 0L }
        // CRITICAL: Never request remote thumbnails from the server for encrypted files
        if (file.isEncrypted && validLocalSource == null) {
            return@withContext null
        }

        if (existing?.nextRetryAt?.let { it > now } == true && validLocalSource == null) {
            return@withContext fallback
        }

        var isCreator = false
        val deferred: Deferred<File?> = inFlightMutex.withLock {
            val active = inFlightRequests[key]
            if (active != null && active.isActive) {
                active
            } else {
                isCreator = true
                CompletableDeferred<File?>().also { inFlightRequests[key] = it }
            }
        }

        if (!isCreator) {
            return@withContext deferred.await()?.takeIf { it.isFile && it.length() > 0L }?.absolutePath ?: fallback
        }

        val completable = deferred as CompletableDeferred<File?>
        try {
            val resolvedFile = concurrencySemaphore.withPermit {
                val permitNow = System.currentTimeMillis()
                val rechecked = dao.get(key)
                if (rechecked != null) {
                    val cached = File(rechecked.path)
                    if (cached.isFile && cached.length() > 0L) {
                        if (permitNow - rechecked.lastAccessAt >= ACCESS_TOUCH_INTERVAL_MS) dao.touch(key, permitNow)
                        return@withPermit cached
                    }
                    dao.delete(key)
                }

                if (rechecked?.nextRetryAt?.let { it > permitNow } == true && validLocalSource == null) {
                    return@withPermit null
                }

                try {
                    val generated = validLocalSource?.let {
                        generateFromLocal(it, key, resolvedAccId, providerType, file, normalizedSize, permitNow)
                    }
                    if (file.isEncrypted) {
                        generated
                    } else {
                        generated ?: downloadRemote(
                            sourceUrl = providerThumbUrl(resolvedAccId, file, normalizedSize),
                            key = key,
                            accountId = resolvedAccId,
                            providerType = providerType,
                            file = file,
                            sizePx = normalizedSize,
                            now = permitNow
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    val count = (rechecked?.failureCount ?: existing?.failureCount ?: 0) + 1
                    dao.recordFailure(key, count, permitNow + backoffMs(count))
                    null
                }
            }
            completable.complete(resolvedFile)
            resolvedFile?.takeIf { it.isFile && it.length() > 0L }?.absolutePath ?: fallback
        } catch (t: Throwable) {
            completable.completeExceptionally(t)
            throw t
        } finally {
            inFlightMutex.withLock {
                if (inFlightRequests[key] === completable) {
                    inFlightRequests.remove(key)
                }
            }
        }
    }

    /**
     * Zero-network lookup for any cached thumbnail already stored for this file (regardless of
     * list/grid target pixel size) whose revision matches the current file revision.
     */
    suspend fun findAnyCached(
        accountId: String,
        file: CloudFile
    ): File? = withContext(Dispatchers.IO) {
        val resolvedAccId = when {
            file.effectiveAccountId.isNotBlank() -> file.effectiveAccountId
            accountId.isNotBlank() && accountId != "all" && accountId != "root" -> accountId
            else -> ""
        }
        if (resolvedAccId.isBlank() || file.effectiveId.isBlank() || file.isFolderItem) return@withContext null
        val expectedRevision = file.updatedAt?.toString() ?: file.time?.toString()
        val entries = dao.findAnyForFile(resolvedAccId, file.effectiveId)
        for (entry in entries) {
            if (expectedRevision != null && entry.sourceRevision != null && entry.sourceRevision != expectedRevision) {
                continue
            }
            val cached = File(entry.path)
            if (cached.isFile && cached.length() > 0L) {
                return@withContext cached
            }
        }
        null
    }

    suspend fun clearAccount(accountId: String) = withContext(Dispatchers.IO) {
        quotaManager.clearAccount(accountId)
    }

    suspend fun enforceQuota(maxBytes: Long = CacheQuotaManager.DEFAULT_THUMBNAIL_QUOTA_BYTES) =
        quotaManager.enforceThumbnailQuota(maxBytes)

    private suspend fun generateFromLocal(
        source: File,
        key: String,
        accountId: String,
        providerType: String,
        file: CloudFile,
        sizePx: Int,
        now: Long
    ): File? {
        var tempDecryptedSource: File? = null
        var keyMaterial: com.cloudyte.app.core.security.ContentKeyMaterial? = null
        val readableSource = try {
            if (streamFileCrypto.hasCyteMagic(source)) {
                val header = streamFileCrypto.validateEncryptedFile(source, expectedKeyId = file.encryptionKeyId)
                keyMaterial = contentKeyProvider.getContentKeyForDecryption(file.encryptionKeyId ?: header.keyId)
                val tempDir = com.cloudyte.app.core.security.SecureFileCleanup.getDecryptedViewCacheDir(appContext)
                val decTemp = File(tempDir, ".thumb_dec_${key.hashCode()}_${System.nanoTime()}.tmp")
                tempDecryptedSource = decTemp
                streamFileCrypto.decryptEncryptedFileToPlaintext(source, decTemp, keyMaterial)
                decTemp
            } else {
                source
            }
        } catch (_: Exception) {
            com.cloudyte.app.core.security.SecureFileCleanup.deleteFileSafely(tempDecryptedSource)
            keyMaterial?.destroy()
            return null
        }

        try {
            val bitmap = (if (file.isVideo) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(readableSource.absolutePath)
                    retriever.getFrameAtTime(1_000_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (_: Exception) {
                    null
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(readableSource.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, sizePx)
                }
                BitmapFactory.decodeFile(readableSource.absolutePath, decodeOptions)
            }) ?: return null

            val target = targetFile(key)
            val temp = File(target.parentFile, ".${target.name}.tmp")
            try {
                temp.outputStream().use { output ->
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, output))
                }
            } finally {
                bitmap.recycle()
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                try {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                } catch (_: Exception) {}
            }
            check(target.exists() && target.length() > 0L) { "Unable to commit generated thumbnail" }
            dao.upsert(entity(key, target, accountId, providerType, file, target.length(), bitmap.width, bitmap.height, now))
            return target
        } finally {
            com.cloudyte.app.core.security.SecureFileCleanup.deleteFileSafely(tempDecryptedSource)
            keyMaterial?.destroy()
        }
    }

    private suspend fun downloadRemote(
        sourceUrl: String,
        key: String,
        accountId: String,
        providerType: String,
        file: CloudFile,
        sizePx: Int,
        now: Long
    ): File? {
        val candidateUrls = mutableListOf(sourceUrl)
        if (file.isImage) {
            val previewFallback = ApiClient.getFileUrl(accountId, file.effectiveId, preview = true)
            if (previewFallback != sourceUrl && !candidateUrls.contains(previewFallback)) {
                candidateUrls.add(previewFallback)
            }
        }

        var lastException: Exception? = null
        for (url in candidateUrls) {
            try {
                val request = Request.Builder().url(url).build()
                ApiClient.okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastException = java.io.IOException("thumbnail HTTP ${response.code} for $url")
                        return@use
                    }
                    val body = response.body ?: return@use
                    val bytes = body.bytes()
                    if (bytes.isEmpty()) {
                        lastException = java.io.IOException("empty thumbnail response from $url")
                        return@use
                    }

                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

                    val target = targetFile(key)
                    val temp = File(target.parentFile, ".${target.name}.tmp")

                    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                        val inSample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, sizePx)
                        if (inSample > 1 || bounds.outWidth > sizePx * 2) {
                            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = inSample }
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                            if (bmp != null) {
                                try {
                                    temp.outputStream().use { out ->
                                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out)
                                    }
                                } finally {
                                    bmp.recycle()
                                }
                            } else {
                                temp.outputStream().use { it.write(bytes) }
                            }
                        } else {
                            temp.outputStream().use { it.write(bytes) }
                        }
                    } else {
                        temp.outputStream().use { it.write(bytes) }
                    }

                    if (target.exists()) target.delete()
                    if (!temp.renameTo(target)) {
                        try {
                            temp.copyTo(target, overwrite = true)
                            temp.delete()
                        } catch (_: Exception) {}
                    }
                    check(target.exists() && target.length() > 0L) { "Unable to commit downloaded thumbnail" }
                    dao.upsert(
                        entity(
                            key = key,
                            target = target,
                            accountId = accountId,
                            providerType = providerType,
                            file = file,
                            sizeBytes = target.length(),
                            width = bounds.outWidth.takeIf { it > 0 },
                            height = bounds.outHeight.takeIf { it > 0 },
                            now = now
                        )
                    )
                    return target
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        if (lastException != null) throw lastException
        return null
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqSizePx: Int): Int {
        var inSampleSize = 1
        if (height > reqSizePx || width > reqSizePx) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqSizePx && halfWidth / inSampleSize >= reqSizePx) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun entity(
        key: String,
        target: File,
        accountId: String,
        providerType: String,
        file: CloudFile,
        sizeBytes: Long,
        width: Int?,
        height: Int?,
        now: Long
    ) = ThumbnailEntity(
        cacheKey = key,
        accountId = accountId,
        providerType = providerType,
        remoteId = file.effectiveId,
        sourceRevision = file.updatedAt?.toString() ?: file.time?.toString(),
        path = target.absolutePath,
        mimeType = file.mimeType ?: "image/jpeg",
        width = width,
        height = height,
        sizeBytes = sizeBytes,
        isEncrypted = file.isEncrypted,
        generatedAt = now,
        lastAccessAt = now
    )

    private fun targetFile(key: String): File = File(root, key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".jpg")

    private fun providerThumbUrl(accountId: String, file: CloudFile, sizePx: Int): String {
        val remote = file.thumbUrl
        if (!remote.isNullOrBlank()) {
            val baseFull = if (remote.startsWith("http://") || remote.startsWith("https://")) {
                remote
            } else {
                "${ApiClient.API_BASE_URL}/${remote.trimStart('/')}"
            }
            val sizedFull = if (Regex("""([?&])s=\d+""").containsMatchIn(baseFull)) {
                baseFull.replace(Regex("""([?&])s=\d+"""), "$1s=$sizePx")
            } else {
                val sep = if (baseFull.contains("?")) "&" else "?"
                "$baseFull${sep}s=$sizePx"
            }
            val token = ApiClient.getToken()
            return if (!token.isNullOrBlank() && !sizedFull.contains("token=")) {
                val sep = if (sizedFull.contains("?")) "&" else "?"
                "$sizedFull${sep}token=${android.net.Uri.encode(token)}"
            } else {
                sizedFull
            }
        }
        val acc = file.effectiveAccountId.ifBlank { accountId.takeIf { it != "all" && it != "root" }.orEmpty() }
        return ApiClient.getThumbUrl(acc, file.effectiveId, sizePx)
    }

    private fun fallbackUrl(accountId: String, file: CloudFile): String? {
        if (file.isEncrypted) return null
        val acc = file.effectiveAccountId.ifBlank { accountId.takeIf { it != "all" && it != "root" }.orEmpty() }
        if (acc.isBlank()) return null
        return if (file.isImage || file.isVideo) ApiClient.getFileUrl(acc, file.effectiveId, preview = true) else null
    }

    private fun backoffMs(failureCount: Int): Long =
        (5_000L * (1L shl failureCount.coerceIn(0, 6))).coerceAtMost(60L * 60L * 1000L)

    companion object {
        const val MAX_CONCURRENT_REQUESTS = 4
        private const val ACCESS_TOUCH_INTERVAL_MS = 60_000L
    }
}


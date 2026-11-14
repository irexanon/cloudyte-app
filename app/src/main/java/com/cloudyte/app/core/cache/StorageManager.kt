package com.cloudyte.app.core.cache

import android.content.Context
import android.os.Environment
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.OfflineFileManager
import com.cloudyte.app.core.security.CryptoConstants
import com.cloudyte.app.core.security.SecureFileCleanup
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

data class StorageBreakdown(
    val previewCacheBytes: Long = 0L,
    val coilCacheBytes: Long = 0L,
    val httpCacheBytes: Long = 0L,
    val decryptedViewCacheBytes: Long = 0L,
    val stagingCacheBytes: Long = 0L,
    val thumbnailBytes: Long = 0L,
    val thumbnailCount: Int = 0,
    val offlineVaultBytes: Long = 0L,
    val offlineCount: Int = 0,
    val exportedDownloadsBytes: Long = 0L,
    val exportedDownloadsCount: Int = 0
) {
    val totalAppCacheBytes: Long
        get() = previewCacheBytes + coilCacheBytes + httpCacheBytes + decryptedViewCacheBytes + stagingCacheBytes

    val totalDownloadsBytes: Long
        get() = offlineVaultBytes + exportedDownloadsBytes

    val totalDownloadsCount: Int
        get() = offlineCount + exportedDownloadsCount
}

data class ClearResult(
    val freedBytes: Long,
    val deletedFiles: Int,
    val remainingBytes: Long
)

object StorageManager {

    fun getExportedDownloadDirs(context: Context): List<File> {
        val list = mutableListOf(
            File(context.filesDir, "downloads"),
            File(context.filesDir, "exports")
        )
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { ext ->
            if (list.none { it.absolutePath == ext.absolutePath }) {
                list += ext
            }
        }
        return list
    }

    suspend fun getPreferredExportDir(context: Context): File {
        val dirSetting = runCatching {
            UserPreferences.observe(context).first().downloadDirectory
        }.getOrDefault("INTERNAL")
        return getPreferredExportDirSync(context, dirSetting)
    }

    fun getPreferredExportDirSync(context: Context, directorySetting: String): File {
        val dir = if (directorySetting.equals("EXTERNAL", ignoreCase = true)) {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads")
        } else {
            File(context.filesDir, "downloads")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun measure(context: Context): StorageBreakdown = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val previewBytes = dirStats(File(appContext.cacheDir, "cloudyte_previews")).first +
            dirStats(File(appContext.cacheDir, com.cloudyte.app.features.player.MediaStreamCache.CACHE_DIR_NAME)).first
        val coilDir = File(appContext.cacheDir, "image_preview_cache")
        val legacyCoilDir = File(appContext.cacheDir, "image_cache")
        val coilBytes = dirStats(coilDir).first +
            dirStats(legacyCoilDir).first +
            (runCatching { appContext.imageLoader.diskCache?.size ?: 0L }.getOrDefault(0L)
                .takeIf { !coilDir.exists() && !legacyCoilDir.exists() } ?: 0L)
        val httpBytes = dirStats(File(appContext.cacheDir, "http")).first
        val viewCacheBytes = dirStats(File(appContext.cacheDir, CryptoConstants.DECRYPTED_VIEW_CACHE_DIR)).first
        val stagingBytes = dirStats(File(appContext.cacheDir, CryptoConstants.ENCRYPTED_STAGING_DIR)).first +
            dirStats(File(appContext.cacheDir, CryptoConstants.DOWNLOAD_STAGING_DIR)).first

        val thumbDirStats = dirStats(File(appContext.filesDir, "cloudyte_thumbnails"))
        val legacyThumbDirStats = dirStats(File(appContext.cacheDir, "thumbnail_cache"))

        val offlineBytes = OfflineFileManager.getOfflineCacheSize(appContext)
        val offlineCount = OfflineFileManager.getOfflineCount(appContext)

        var exportedBytes = 0L
        var exportedCount = 0
        for (dir in getExportedDownloadDirs(appContext)) {
            val (b, c) = dirStats(dir)
            exportedBytes += b
            exportedCount += c
        }

        StorageBreakdown(
            previewCacheBytes = previewBytes,
            coilCacheBytes = coilBytes,
            httpCacheBytes = httpBytes,
            decryptedViewCacheBytes = viewCacheBytes,
            stagingCacheBytes = stagingBytes,
            thumbnailBytes = thumbDirStats.first + legacyThumbDirStats.first,
            thumbnailCount = thumbDirStats.second + legacyThumbDirStats.second,
            offlineVaultBytes = offlineBytes,
            offlineCount = offlineCount,
            exportedDownloadsBytes = exportedBytes,
            exportedDownloadsCount = exportedCount
        )
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun clearAppCache(context: Context): ClearResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val before = measure(appContext).totalAppCacheBytes
        var deletedCount = 0

        PreviewStore(appContext).clear()
        deletedCount += deleteDirContents(File(appContext.cacheDir, "cloudyte_previews"))
        com.cloudyte.app.features.player.MediaStreamCache.clear(appContext)

        runCatching {
            appContext.imageLoader.memoryCache?.clear()
            appContext.imageLoader.diskCache?.clear()
        }
        deletedCount += deleteDirContents(File(appContext.cacheDir, "image_preview_cache"))
        deletedCount += deleteDirContents(File(appContext.cacheDir, "image_cache"))

        runCatching { ApiClient.okHttpClient.cache?.evictAll() }
        deletedCount += deleteDirContents(File(appContext.cacheDir, "http"))

        SecureFileCleanup.clearDecryptedViewCache(appContext)

        val db = runCatching { AppDatabase.getDatabase(appContext) }.getOrNull()
        val activeIds = runCatching {
            db?.transferDao()?.observeActiveTransfers()?.first()?.map { it.id }?.toSet() ?: emptySet()
        }.getOrDefault(emptySet())
        deletedCount += SecureFileCleanup.cleanupStaleTempFiles(appContext, activeIds)

        val after = measure(appContext).totalAppCacheBytes
        ClearResult(
            freedBytes = (before - after).coerceAtLeast(0L),
            deletedFiles = deletedCount,
            remainingBytes = after
        )
    }

    suspend fun clearThumbnails(context: Context): ClearResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val before = measure(appContext).thumbnailBytes
        var deletedCount = 0

        deletedCount += deleteDirContents(File(appContext.filesDir, "cloudyte_thumbnails"))
        deletedCount += deleteDirContents(File(appContext.cacheDir, "thumbnail_cache"))

        runCatching {
            AppDatabase.getDatabase(appContext).thumbnailDao().deleteAll()
        }

        val after = measure(appContext).thumbnailBytes
        ClearResult(
            freedBytes = (before - after).coerceAtLeast(0L),
            deletedFiles = deletedCount,
            remainingBytes = after
        )
    }

    suspend fun clearDownloads(context: Context): ClearResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val before = measure(appContext).totalDownloadsBytes
        var deletedCount = 0

        deletedCount += OfflineFileManager.clearAllOfflineCache(appContext)
        for (dir in getExportedDownloadDirs(appContext)) {
            deletedCount += deleteDirContents(dir)
        }

        val after = measure(appContext).totalDownloadsBytes
        ClearResult(
            freedBytes = (before - after).coerceAtLeast(0L),
            deletedFiles = deletedCount,
            remainingBytes = after
        )
    }

    private fun dirStats(dir: File?): Pair<Long, Int> {
        if (dir == null || !dir.exists()) return 0L to 0
        var bytes = 0L
        var count = 0
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            bytes += file.length().coerceAtLeast(0L)
            count++
        }
        return bytes to count
    }

    private fun deleteDirContents(dir: File?): Int {
        if (dir == null || !dir.exists()) return 0
        var deleted = 0
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                deleted += deleteDirContents(child)
                child.delete()
            } else if (child.delete()) {
                deleted++
            }
        }
        return deleted
    }
}

package com.cloudyte.app.api

import android.content.Context
import android.util.Log
import com.cloudyte.app.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

object OfflineFileManager {
    private const val TAG = "OfflineFileManager"
    private const val OFFLINE_DIR_NAME = "offline_vault"

    private val _downloadedFiles = MutableStateFlow<Set<String>>(emptySet())
    val downloadedFiles: StateFlow<Set<String>> = _downloadedFiles.asStateFlow()

    // Map of fileId to download progress (0.0f .. 1.0f)
    private val _activeDownloads = MutableStateFlow<Map<String, Float>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, Float>> = _activeDownloads.asStateFlow()

    private val downloadingSet = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        scope.launch {
            refreshDownloadedSet(context)
        }
    }

    fun getInternalVaultDir(context: Context): File {
        val dir = File(context.filesDir, OFFLINE_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getExternalVaultDir(context: Context): File? {
        val ext = context.getExternalFilesDir(OFFLINE_DIR_NAME) ?: return null
        if (!ext.exists()) ext.mkdirs()
        return ext
    }

    fun getAllVaultDirs(context: Context): List<File> {
        val list = mutableListOf(getInternalVaultDir(context))
        getExternalVaultDir(context)?.let { ext ->
            if (ext.absolutePath != list.first().absolutePath) {
                list += ext
            }
        }
        return list
    }

    suspend fun getPreferredVaultDir(context: Context): File {
        val pref = runCatching {
            UserPreferences.observe(context).first().downloadDirectory
        }.getOrDefault("INTERNAL")
        return if (pref.equals("EXTERNAL", ignoreCase = true)) {
            getExternalVaultDir(context) ?: getInternalVaultDir(context)
        } else {
            getInternalVaultDir(context)
        }
    }

    fun getPreferredVaultDirSync(context: Context, directorySetting: String): File {
        return if (directorySetting.equals("EXTERNAL", ignoreCase = true)) {
            getExternalVaultDir(context) ?: getInternalVaultDir(context)
        } else {
            getInternalVaultDir(context)
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    fun getLocalFile(
        context: Context,
        accountId: String,
        fileId: String,
        fileName: String,
        vaultDir: File = getInternalVaultDir(context)
    ): File {
        val safeAcc = sanitizeFileName(accountId)
        val safeFid = sanitizeFileName(fileId)
        val safeName = sanitizeFileName(fileName)
        return File(vaultDir, "${safeAcc}_${safeFid}_$safeName")
    }

    fun findOfflineFile(context: Context, accountId: String, fileId: String): File? {
        val safeAcc = sanitizeFileName(accountId)
        val safeFid = sanitizeFileName(fileId)
        val prefix = "${safeAcc}_${safeFid}_"
        for (dir in getAllVaultDirs(context)) {
            val matches = dir.listFiles { _, name ->
                name.startsWith(prefix) && !name.endsWith(".tmp") && !name.endsWith(".part")
            }
            val found = matches?.firstOrNull { it.exists() && it.length() > 0 }
            if (found != null) return found
        }
        return null
    }

    fun isDownloaded(context: Context, accountId: String, fileId: String): Boolean {
        if (_downloadedFiles.value.contains(fileId)) return true
        val file = findOfflineFile(context, accountId, fileId)
        val exists = file != null && file.exists() && file.length() > 0
        if (exists && !_downloadedFiles.value.contains(fileId)) {
            _downloadedFiles.value = _downloadedFiles.value + fileId
        }
        return exists
    }

    fun isDownloading(fileId: String): Boolean {
        return downloadingSet.contains(fileId)
    }

    fun getDownloadProgress(fileId: String): Float? {
        return _activeDownloads.value[fileId]
    }

    fun refreshDownloadedSet(context: Context) {
        val set = mutableSetOf<String>()
        for (dir in getAllVaultDirs(context)) {
            val files = dir.listFiles { _, name -> !name.endsWith(".tmp") && !name.endsWith(".part") } ?: emptyArray()
            for (f in files) {
                if (f.length() > 0) {
                    val parts = f.name.split("_")
                    if (parts.size >= 3) {
                        set.add(parts[1])
                    }
                }
            }
        }
        _downloadedFiles.value = set
    }

    fun autoCacheOnView(context: Context, accountId: String, file: CloudFile) {
        val fid = file.effectiveId
        if (fid.isEmpty() || file.isFolderItem) return
        if (isDownloaded(context, accountId, fid) || downloadingSet.contains(fid)) {
            return
        }

        scope.launch {
            try {
                downloadFile(context, accountId, file)
            } catch (e: Exception) {
                Log.w(TAG, "Auto cache on view failed: ${e::class.simpleName}")
            }
        }
    }

    suspend fun downloadFile(
        context: Context,
        accountId: String,
        file: CloudFile,
        onProgress: ((Float) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val fid = file.effectiveId
        val fileName = file.effectiveName.ifBlank { "file_${fid}" }
        val existing = findOfflineFile(context, accountId, fid)
        if (existing != null && existing.exists() && existing.length() > 0) {
            _downloadedFiles.value = _downloadedFiles.value + fid
            onProgress?.invoke(1f)
            return@withContext Result.success(existing)
        }

        val vaultDir = getPreferredVaultDir(context)
        val targetFile = getLocalFile(context, accountId, fid, fileName, vaultDir)

        if (targetFile.exists() && targetFile.length() > 0) {
            _downloadedFiles.value = _downloadedFiles.value + fid
            onProgress?.invoke(1f)
            return@withContext Result.success(targetFile)
        }

        if (!downloadingSet.add(fid)) {
            return@withContext Result.failure(Exception("Download already in progress"))
        }

        _activeDownloads.value = _activeDownloads.value + (fid to 0f)

        val tempFile = File(vaultDir, "${targetFile.name}.tmp")
        try {
            if (tempFile.exists()) tempFile.delete()
            val app = context.applicationContext as? com.cloudyte.app.CloudyteApplication
                ?: throw IllegalStateException("Application transfer queue is unavailable")
            val transferId = app.container.transferRepository.enqueueOrGetOfflineDownload(
                accountId = accountId,
                fileId = fid,
                fileName = fileName,
                destinationPath = tempFile.absolutePath,
                totalBytes = (if (file.isEncrypted) file.encryptedSizeBytes ?: file.sizeBytes else file.sizeBytes).coerceAtLeast(0L),
                isEncrypted = file.isEncrypted,
                encryptionFormatVersion = file.encryptionFormatVersion,
                encryptionKeyId = file.encryptionKeyId,
                encryptedSizeBytes = file.encryptedSizeBytes,
                encryptedChecksum = file.encryptedChecksum,
                opaqueRemoteName = file.opaqueRemoteName,
                plaintextSizeBytes = file.plaintextSizeBytes,
                originalMetadataAvailable = file.originalMetadataAvailable
            )
            app.container.transferCoordinator.dispatch(com.cloudyte.app.core.transfer.TransferCommand.Start(transferId))
            val completed = kotlinx.coroutines.withTimeoutOrNull(30 * 60 * 1000L) {
                while (true) {
                    val row = app.container.database.transferDao().getById(transferId)
                        ?: throw IOException("Queued download disappeared")
                    val state = row.state
                    val total = row.totalBytes
                    val progress = if (total > 0L) (row.bytesTransferred.toFloat() / total).coerceIn(0f, 1f) else 0f
                    _activeDownloads.value = _activeDownloads.value + (fid to progress)
                    onProgress?.invoke(progress)
                    when (state) {
                        "COMPLETED" -> return@withTimeoutOrNull true
                        "FAILED_RETRYABLE", "FAILED_PERMANENT", "WAITING_FOR_AUTH", "CANCELLED", "EXPIRED" ->
                            throw IOException(row.lastErrorMessage ?: row.errorMessage ?: "Download failed ($state)")
                    }
                    kotlinx.coroutines.delay(250L)
                }
                false
            } ?: throw IOException("Download is still queued; try again shortly")
            if (completed && tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                _downloadedFiles.value = _downloadedFiles.value + fid
                _activeDownloads.value = _activeDownloads.value - fid
                downloadingSet.remove(fid)
                onProgress?.invoke(1f)
                return@withContext Result.success(targetFile)
            }
            throw IOException("Queued download completed without a local file")
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e::class.simpleName}")
            if (tempFile.exists()) tempFile.delete()
            _activeDownloads.value = _activeDownloads.value - fid
            downloadingSet.remove(fid)
            return@withContext Result.failure(e)
        }
    }

    suspend fun prepareDecryptedFileForViewer(
        context: Context,
        accountId: String,
        file: CloudFile,
        onProgress: ((Float) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val fid = file.effectiveId
        if (fid.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("File ID is empty"))
        }

        val offlineFile = findOfflineFile(context, accountId, fid)
            ?: downloadFile(context, accountId, file, onProgress).getOrElse { err ->
                return@withContext Result.failure(err)
            }

        val streamCrypto = com.cloudyte.app.core.security.StreamFileCrypto()
        if (!streamCrypto.hasCyteMagic(offlineFile)) {
            onProgress?.invoke(1f)
            return@withContext Result.success(offlineFile)
        }

        val app = context.applicationContext as? com.cloudyte.app.CloudyteApplication
        val keyProvider = app?.container?.contentKeyManager
            ?: com.cloudyte.app.core.security.ContentKeyManager(context)
        val viewCacheDir = com.cloudyte.app.core.security.SecureFileCleanup.getDecryptedViewCacheDir(context)
        val safeAcc = sanitizeFileName(accountId)
        val safeFid = sanitizeFileName(fid)
        val safeName = sanitizeFileName(file.effectiveName.ifBlank { "decrypted_$fid" })
        val viewTargetFile = File(viewCacheDir, "view_${safeAcc}_${safeFid}_$safeName")
        val viewTempFile = File(viewCacheDir, "view_${safeAcc}_${safeFid}_$safeName.tmp")

        var keyMaterial: com.cloudyte.app.core.security.ContentKeyMaterial? = null
        try {
            val header = streamCrypto.validateEncryptedFile(offlineFile, expectedKeyId = file.encryptionKeyId)
            keyMaterial = keyProvider.getContentKeyForDecryption(file.encryptionKeyId ?: header.keyId)
            streamCrypto.decryptEncryptedFileToPlaintext(
                encryptedFile = offlineFile,
                destinationTempFile = viewTempFile,
                keyMaterial = keyMaterial,
                onProgress = { written ->
                    if (header.plaintextSize > 0L) {
                        onProgress?.invoke((written.toFloat() / header.plaintextSize.toFloat()).coerceIn(0f, 1f))
                    }
                }
            )
            if (viewTargetFile.exists()) viewTargetFile.delete()
            if (!viewTempFile.renameTo(viewTargetFile)) {
                viewTempFile.copyTo(viewTargetFile, overwrite = true)
                viewTempFile.delete()
            }
            onProgress?.invoke(1f)
            Result.success(viewTargetFile)
        } catch (e: Exception) {
            com.cloudyte.app.core.security.SecureFileCleanup.deleteFileSafely(viewTempFile)
            com.cloudyte.app.core.security.SecureFileCleanup.deleteFileSafely(viewTargetFile)
            Result.failure(e)
        } finally {
            keyMaterial?.destroy()
        }
    }

    fun deleteOfflineFile(context: Context, accountId: String, fileId: String): Boolean {
        val file = findOfflineFile(context, accountId, fileId)
        val deleted = file?.delete() ?: false
        _downloadedFiles.value = _downloadedFiles.value - fileId
        val safeAcc = sanitizeFileName(accountId)
        val safeFid = sanitizeFileName(fileId)
        val viewCacheDir = com.cloudyte.app.core.security.SecureFileCleanup.getDecryptedViewCacheDir(context)
        viewCacheDir.listFiles { _, name -> name.startsWith("view_${safeAcc}_${safeFid}_") }?.forEach {
            com.cloudyte.app.core.security.SecureFileCleanup.deleteFileSafely(it)
        }
        return deleted
    }

    fun getOfflineCacheSize(context: Context): Long {
        var total = 0L
        for (dir in getAllVaultDirs(context)) {
            dir.listFiles()?.forEach {
                if (it.isFile) total += it.length()
            }
        }
        return total
    }

    fun getOfflineCount(context: Context): Int {
        var count = 0
        for (dir in getAllVaultDirs(context)) {
            count += dir.listFiles { _, name -> !name.endsWith(".tmp") && !name.endsWith(".part") }
                ?.count { it.isFile && it.length() > 0 }
                ?: 0
        }
        return count
    }

    fun clearAllOfflineCache(context: Context): Int {
        var deletedCount = 0
        for (dir in getAllVaultDirs(context)) {
            dir.listFiles()?.forEach { file ->
                if (file.delete()) deletedCount++
            }
        }
        com.cloudyte.app.core.security.SecureFileCleanup.clearDecryptedViewCache(context)
        _downloadedFiles.value = emptySet()
        _activeDownloads.value = emptyMap()
        return deletedCount
    }
}

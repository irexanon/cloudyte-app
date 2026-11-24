package com.cloudyte.app.data

import android.content.Context
import com.cloudyte.app.CloudyteApplication
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.DriveManifestResponse
import com.cloudyte.app.api.DriveTransferRequest
import com.cloudyte.app.api.TransferActionResponse
import com.cloudyte.app.core.cache.StorageManager
import com.cloudyte.app.core.transfer.TransferCommand
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.TransferDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class EnqueuedDriveDownloadResult(
    val queuedCount: Int,
    val destinationFolderName: String
)

object DriveTransferManager {

    suspend fun fetchManifest(
        accountId: String,
        folderId: String? = null,
        maxFiles: Int? = null
    ): Result<DriveManifestResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedFolderId = folderId?.takeIf { it.isNotBlank() && it != "root" }
            val response = ApiClient.apiService.getAccountManifest(
                accountId = accountId,
                folderId = normalizedFolderId,
                maxFiles = maxFiles
            )
            if (response.isSuccessful) {
                response.body() ?: throw IllegalStateException("Server returned an empty drive manifest.")
            } else {
                val rawErr = runCatching { response.errorBody()?.string() }.getOrNull()
                throw IllegalStateException(ApiClient.parseError(rawErr, response.code()))
            }
        }
    }

    suspend fun startDriveTransfer(
        request: DriveTransferRequest
    ): Result<TransferActionResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val response = ApiClient.apiService.startDriveTransfer(request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: TransferActionResponse(success = true, async = true, status = "queued")
                if (!body.success && !body.error.isNullOrBlank()) {
                    throw IllegalStateException(body.error)
                }
                body
            } else {
                val rawErr = runCatching { response.errorBody()?.string() }.getOrNull()
                throw IllegalStateException(ApiClient.parseError(rawErr, response.code()))
            }
        }
    }

    suspend fun enqueueManifestDownload(
        context: Context,
        manifest: DriveManifestResponse,
        rootLabelOverride: String? = null
    ): Result<EnqueuedDriveDownloadResult> = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = context.applicationContext
            val container = (appContext as? CloudyteApplication)?.container
            val db = container?.database ?: AppDatabase.getDatabase(appContext)
            val transferRepo = container?.transferRepository ?: TransferRepository(db.transferDao())
            val coordinator = container?.transferCoordinator

            val exportRoot = StorageManager.getPreferredExportDir(appContext)
            val baseFolderName = sanitizePathSegment(
                rootLabelOverride?.takeIf { it.isNotBlank() }
                    ?: manifest.driveName.takeIf { it.isNotBlank() }
                    ?: "Drive_${manifest.accountId.take(8)}"
            )
            val baseDir = File(exportRoot, baseFolderName).also { it.mkdirs() }

            // Create empty folders from manifest as well so folder structure matches remote
            manifest.folders.forEach { rawFolderPath ->
                val safeRel = sanitizeRelativePath(rawFolderPath)
                if (safeRel.isNotBlank()) {
                    File(baseDir, safeRel).mkdirs()
                }
            }

            var queuedCount = 0
            var firstTransferId: String? = null
            for (item in manifest.files) {
                if (item.id.isBlank()) continue
                val relativePath = item.relativePath.takeIf { it.isNotBlank() }
                    ?: listOfNotNull(
                        item.folderPath.takeIf { it.isNotBlank() },
                        item.name.takeIf { it.isNotBlank() }
                    ).joinToString("/")
                val safeRelPath = sanitizeRelativePath(relativePath)
                    .ifBlank { sanitizePathSegment(item.name.ifBlank { "file_${item.id.take(8)}" }) }

                val destFile = resolveSafeChildFile(baseDir, safeRelPath)
                destFile.parentFile?.mkdirs()

                val transferId = transferRepo.enqueueTransfer(
                    accountId = manifest.accountId,
                    fileName = item.name.ifBlank { destFile.name },
                    localPath = destFile.absolutePath,
                    remotePath = item.folderPath.ifBlank { null },
                    direction = TransferDirection.DOWNLOAD,
                    totalBytes = item.sizeBytes.coerceAtLeast(0L),
                    fileId = item.id,
                    isEncrypted = item.isEncrypted,
                    originalName = item.name.takeIf { it.isNotBlank() },
                    originalMimeType = item.mimeType,
                    originalSize = item.sizeBytes.takeIf { it > 0L }
                )
                if (firstTransferId == null) {
                    firstTransferId = transferId
                }
                queuedCount++
            }

            if (firstTransferId != null) {
                coordinator?.dispatch(TransferCommand.Start(firstTransferId))
            }

            EnqueuedDriveDownloadResult(
                queuedCount = queuedCount,
                destinationFolderName = baseFolderName
            )
        }
    }

    private fun sanitizePathSegment(raw: String): String {
        val cleaned = raw
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .trim('.')
        return when {
            cleaned.isBlank() || cleaned == "." || cleaned == ".." -> "item"
            cleaned.length > 120 -> cleaned.take(120)
            else -> cleaned
        }
    }

    private fun sanitizeRelativePath(raw: String): String {
        return raw.replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString(File.separator) { sanitizePathSegment(it) }
    }

    private fun resolveSafeChildFile(baseDir: File, safeRelPath: String): File {
        val candidate = File(baseDir, safeRelPath)
        val canonicalBase = runCatching { baseDir.canonicalPath }.getOrDefault(baseDir.absolutePath)
        val canonicalTarget = runCatching { candidate.canonicalPath }.getOrDefault(candidate.absolutePath)
        return if (canonicalTarget == canonicalBase || canonicalTarget.startsWith(canonicalBase + File.separator)) {
            candidate
        } else {
            File(baseDir, sanitizePathSegment(candidate.name))
        }
    }
}

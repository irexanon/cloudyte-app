package com.cloudyte.app.api
import androidx.compose.runtime.Stable

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val username: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val username: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val success: Boolean? = null,
    val token: String? = null,
    val username: String? = null,
    val isAdmin: Boolean? = false,
    val error: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class UserSettingsRequest(
    val backupEnabled: Boolean,
    val backupAccId: String?,
    val isDefaultAccId: String?
)

@JsonClass(generateAdapter = true)
data class TgDriveRegisterRequest(
    val channelId: String,
    val driveName: String
)

@JsonClass(generateAdapter = true)
data class OAuthLinkResponse(
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class PublicSettingsResponse(
    val registrationEnabled: Boolean? = true,
    val webRegistrationEnabled: Boolean? = true,
    val botRegistrationEnabled: Boolean? = true
)

@JsonClass(generateAdapter = true)
data class FolderRequest(
    val name: String,
    val parentId: String? = null
)

@JsonClass(generateAdapter = true)
data class RenameRequest(
    val name: String
)

@JsonClass(generateAdapter = true)
data class MoveCopyRequest(
    val sourceAccountId: String,
    val fileId: String,
    val targetAccountId: String,
    val targetFolderId: String? = null
)

@JsonClass(generateAdapter = true)
data class ShareRequest(
    val accountId: String,
    val fileId: String
)

@JsonClass(generateAdapter = true)
data class ShareResponse(
    val shareUrl: String? = null,
    val url: String? = null,
    val success: Boolean? = null
) {
    val effectiveUrl: String get() = shareUrl ?: url ?: ""
}

@JsonClass(generateAdapter = true)
data class UserProfile(
    val id: String? = null,
    val _id: String? = null,
    val username: String? = null,
    val email: String? = null,
    val isAdmin: Boolean? = false,
    val settings: UserSettings? = null
) {
    val displayUsername: String get() = username ?: "User"
}

@JsonClass(generateAdapter = true)
data class UserSettings(
    val backupEnabled: Boolean? = false,
    val backupAccId: String? = null,
    val isDefaultAccId: String? = null
)

@JsonClass(generateAdapter = true)
data class Account(
    val id: String? = null,
    val _id: String? = null,
    val name: String? = null,
    val driveName: String? = null,
    val email: String? = null,
    val accountType: String? = null,
    val type: String? = null,
    val provider: String? = null,
    val usage: Any? = null,
    val total: Any? = null,
    val percentage: Float? = null,
    val fileCount: Int? = null,
    val isDefault: Boolean? = false,
    val channelId: String? = null
) {
    val effectiveId: String get() = id ?: _id ?: ""
    val effectiveName: String get() = name ?: driveName ?: "Drive"
    val effectiveType: String get() = accountType ?: type ?: provider ?: "TG Drive"
    
    val usageBytes: Long get() = parseBytes(usage)
    val totalBytes: Long get() = parseBytes(total)
    
    val effectivePercentage: Float get() {
        if (percentage != null && percentage > 0f) return percentage
        val u = usageBytes
        val t = totalBytes
        if (u > 0L && t > 0L) {
            return (u.toFloat() / t.toFloat()) * 100f
        }
        return 0f
    }
}

@Stable
@JsonClass(generateAdapter = true)
data class StorageCategories(
    val images: Long = 0L,
    val documents: Long = 0L,
    val videos: Long = 0L,
    val archives: Long = 0L,
    val audio: Long = 0L,
    val other: Long = 0L
) {
    val totalCategoryBytes: Long
        get() = images.coerceAtLeast(0L) +
            documents.coerceAtLeast(0L) +
            videos.coerceAtLeast(0L) +
            archives.coerceAtLeast(0L) +
            audio.coerceAtLeast(0L) +
            other.coerceAtLeast(0L)
}

@Stable
@JsonClass(generateAdapter = true)
data class StorageSummary(
    val accountId: String = "",
    val accountType: String = "",
    val usedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val fileCount: Int? = null,
    val categories: StorageCategories? = null
) {
    val isUnlimited: Boolean
        get() = totalBytes == null ||
            totalBytes <= 0L ||
            accountType.contains("telegram", ignoreCase = true) ||
            accountType.contains("tg", ignoreCase = true)

    val percentage: Float
        get() {
            val total = totalBytes ?: return 0f
            if (isUnlimited || total <= 0L || usedBytes <= 0L) return 0f
            return ((usedBytes.toFloat() / total.toFloat()) * 100f).coerceIn(0f, 100f)
        }
}

@Stable
@JsonClass(generateAdapter = true)
data class EncryptedMetadataEnvelope(
    val metadataVersion: Int = 1,
    val algorithm: String = "AES-256-GCM",
    val keyId: String,
    val nonce: String,
    val ciphertext: String
)

@Stable
@JsonClass(generateAdapter = true)
data class CloudFile(
    val id: String? = null,
    val _id: String? = null,
    val fileId: String? = null,
    val name: String? = null,
    val fileName: String? = null,
    val title: String? = null,
    val type: String? = null,
    val fileType: String? = null,
    val mimeType: String? = null,
    val size: Any? = null,
    val time: Long? = null,
    val createdAt: String? = null,
    val updatedAt: Long? = null,
    val thumbUrl: String? = null,
    val location: String? = null,
    val path: String? = null,
    val resolution: String? = null,
    val isFolder: Boolean? = false,
    val is_dir: Boolean? = false,
    val isDirectory: Boolean? = false,
    val accountId: String? = null,
    val isEncrypted: Boolean = false,
    val encryptionFormatVersion: Int? = null,
    val encryptionKeyId: String? = null,
    val encryptedMetadata: EncryptedMetadataEnvelope? = null,
    val encryptedSizeBytes: Long? = null,
    val encryptedChecksum: String? = null,
    val opaqueRemoteName: String? = null,
    val plaintextSizeBytes: Long? = null,
    val originalMetadataAvailable: Boolean = false,
    val metadataStatus: String? = null
) {
    val effectiveId: String get() = id ?: _id ?: fileId ?: ""
    val effectiveName: String
        get() {
            if (isEncrypted && !originalMetadataAvailable) {
                return "Encrypted file"
            }
            return name ?: fileName ?: title ?: "Unnamed File"
        }
    val isFolderItem: Boolean get() = isFolder == true || is_dir == true || isDirectory == true || type.equals("folder", ignoreCase = true)
    
    val effectiveAccountId: String
        get() {
            if (!accountId.isNullOrBlank() && accountId != "all" && accountId != "root") {
                return accountId
            }
            if (!thumbUrl.isNullOrBlank()) {
                val match = Regex("""/api/(?:thumb|download)/([^/?#]+)""").find(thumbUrl)
                val extracted = match?.groupValues?.getOrNull(1)
                if (!extracted.isNullOrBlank() && extracted != "all" && extracted != "root") {
                    return extracted
                }
            }
            return ""
        }
    
    val fileExtension: String
        get() {
            if (isEncrypted && !originalMetadataAvailable) return ""
            return effectiveName.substringAfterLast('.', "").lowercase(Locale.US)
        }

    val allTypeCandidates: List<String>
        get() {
            if (isEncrypted && !originalMetadataAvailable) return emptyList()
            return listOfNotNull(
                type?.lowercase(Locale.US),
                fileType?.lowercase(Locale.US),
                mimeType?.lowercase(Locale.US),
                mimeType?.substringAfterLast('/')?.lowercase(Locale.US),
                fileExtension.takeIf { it.isNotBlank() }
            )
        }

    val effectiveType: String get() {
        if (isFolderItem) return "folder"
        if (isEncrypted && !originalMetadataAvailable) return "encrypted"
        val ext = fileExtension
        if (ext.isNotBlank() && ext.length <= 6 && !effectiveName.startsWith(".trashed")) {
            return ext
        }
        val rawType = type ?: fileType ?: mimeType?.substringAfterLast('/') ?: ext
        return if (rawType.isNotBlank()) rawType.lowercase(Locale.US) else "file"
    }

    val isImage: Boolean get() = !isFolderItem && (!isEncrypted || originalMetadataAvailable) && (
        allTypeCandidates.any {
            it in listOf("image", "photo", "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "heic", "heif", "ico", "jfif", "avif") ||
                it.startsWith("image/")
        } || effectiveName.contains("IMG_", ignoreCase = true) || effectiveName.contains("Screenshot", ignoreCase = true)
    )

    val isVideo: Boolean get() = !isFolderItem && (!isEncrypted || originalMetadataAvailable) && (
        allTypeCandidates.any {
            it in listOf("video", "mp4", "mkv", "avi", "mov", "flv", "wmv", "3gp", "webm", "m4v", "ts", "mpeg", "mpg") ||
                it.startsWith("video/")
        } || effectiveName.contains("VID_", ignoreCase = true)
    )

    val isAudio: Boolean get() = !isFolderItem && (!isEncrypted || originalMetadataAvailable) && allTypeCandidates.any {
        it in listOf("audio", "mp3", "wav", "ogg", "m4a", "flac", "aac", "opus", "wma") ||
            it.startsWith("audio/")
    }

    val isTextOrCode: Boolean get() = !isFolderItem && (!isEncrypted || originalMetadataAvailable) && allTypeCandidates.any {
        it in listOf("txt", "text", "json", "js", "ts", "py", "java", "kt", "c", "cpp", "h", "cs", "html", "css", "xml", "yml", "yaml", "sh", "sql", "log", "csv", "env", "md", "markdown", "properties", "gradle", "toml", "conf") ||
            it.startsWith("text/") || it.contains("json")
    }

    val isPdf: Boolean get() = !isFolderItem && (!isEncrypted || originalMetadataAvailable) && allTypeCandidates.any {
        it == "pdf" || it.contains("pdf")
    }
    
    val sizeBytes: Long
        get() {
            if (isEncrypted) {
                if (originalMetadataAvailable && plaintextSizeBytes != null && plaintextSizeBytes >= 0L) {
                    return plaintextSizeBytes
                }
                if (encryptedSizeBytes != null && encryptedSizeBytes > 0L) {
                    return encryptedSizeBytes
                }
            }
            return parseBytes(size)
        }
    
    val formattedSize: String get() {
        val s = sizeBytes
        if (s > 0L) return formatByteCount(s)
        if (!isEncrypted && size is String && size.contains(Regex("[a-zA-Z]"))) return size.toString()
        return "0 B"
    }
}

fun formatByteCount(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    return when {
        bytes >= 1024L * 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f TB", bytes.toDouble() / (1024L * 1024 * 1024 * 1024))
        bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes.toDouble() / (1024L * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024L * 1024))
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024)
        else -> "$bytes B"
    }
}

fun parseBytes(value: Any?): Long {
    when (value) {
        is Number -> return value.toLong()
        is String -> {
            val v = value.lowercase(Locale.US).replace(" ", "").replace(",", "")
            return when {
                v.endsWith("tb") -> (v.removeSuffix("tb").toDoubleOrNull() ?: 0.0).times(1024L * 1024 * 1024 * 1024).toLong()
                v.endsWith("gb") -> (v.removeSuffix("gb").toDoubleOrNull() ?: 0.0).times(1024L * 1024 * 1024).toLong()
                v.endsWith("mb") -> (v.removeSuffix("mb").toDoubleOrNull() ?: 0.0).times(1024L * 1024).toLong()
                v.endsWith("kb") -> (v.removeSuffix("kb").toDoubleOrNull() ?: 0.0).times(1024L).toLong()
                v.endsWith("b") -> v.removeSuffix("b").toLongOrNull() ?: 0L
                else -> v.toLongOrNull() ?: 0L
            }
        }
        else -> return 0L
    }
}

@JsonClass(generateAdapter = true)
data class UploadSessionRequest(
    val fileName: String,
    val opaqueRemoteName: String? = null,
    val mimeType: String,
    val size: Long,
    val folderId: String? = null,
    val checksum: String? = null,
    val idempotencyKey: String,
    val providerType: String? = null,
    val desiredChunkSize: Long? = null,
    val isEncrypted: Boolean = false,
    val encryptionFormatVersion: Int? = null,
    val encryptionKeyId: String? = null,
    val encryptedSizeBytes: Long? = null,
    val encryptedMetadata: EncryptedMetadataEnvelope? = null
)

@JsonClass(generateAdapter = true)
data class UploadSessionResponse(
    val sessionId: String,
    val status: String,
    val receivedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val chunkSize: Long = 8L * 1024L * 1024L,
    val checksum: String? = null,
    val providerType: String? = null,
    val providerResumable: Boolean? = null,
    val providerCancellable: Boolean? = null,
    val isEncrypted: Boolean = false,
    val encryptionFormatVersion: Int? = null,
    val encryptionKeyId: String? = null,
    val encryptedMetadata: EncryptedMetadataEnvelope? = null,
    val encryptedSizeBytes: Long? = null,
    val opaqueRemoteName: String? = null
)

@JsonClass(generateAdapter = true)
data class EncryptionKeyBackup(
    val backupVersion: Int,
    val keyId: String,
    val kdfAlgorithm: String,
    val kdfIterations: Int,
    val salt: String,
    val wrapAlgorithm: String,
    val nonce: String,
    val wrappedKey: String
)

@JsonClass(generateAdapter = true)
data class UploadChunkResponse(
    val sessionId: String,
    val receivedBytes: Long,
    val totalBytes: Long,
    val nextOffset: Long,
    val complete: Boolean = false
)

@JsonClass(generateAdapter = true)
data class UploadCompleteResponse(
    val success: Boolean = false,
    val sessionId: String,
    val checksum: String? = null
)

@JsonClass(generateAdapter = true)
data class TrashRestoreRequest(
    val accountId: String,
    val fileId: String
)

@JsonClass(generateAdapter = true)
data class ActiveSessionDto(
    val id: String = "",
    val sessionId: String? = null,
    val deviceName: String = "Cloudyte Client",
    val userAgent: String? = null,
    val createdAt: String? = null,
    val lastActiveAt: String? = null,
    val expiresAt: String? = null,
    val isCurrent: Boolean = false
) {
    val effectiveId: String get() = sessionId?.takeIf { it.isNotBlank() } ?: id
}

@JsonClass(generateAdapter = true)
data class TransferActionResponse(
    val success: Boolean = false,
    val async: Boolean? = null,
    val jobId: String? = null,
    val status: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class JobEndpointRef(
    val accountId: String? = null,
    val fileId: String? = null,
    val folderId: String? = null,
    val driveName: String? = null
)

@JsonClass(generateAdapter = true)
data class JobStatusResponse(
    val id: String = "",
    val jobId: String? = null,
    val accountId: String? = null,
    val type: String? = null,
    val status: String = "",
    val stage: String? = null,
    val progress: Int? = null,
    val progressPct: Int? = null,
    val bytesProcessed: Long? = null,
    val totalBytes: Long? = null,
    val itemsProcessed: Int? = null,
    val totalItems: Int? = null,
    val source: JobEndpointRef? = null,
    val destination: JobEndpointRef? = null,
    val isMove: Boolean? = null,
    val attempts: Int? = null,
    val maxAttempts: Int? = null,
    val deadLettered: Boolean? = null,
    val error: String? = null,
    val lastError: String? = null,
    val createdAt: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null
) {
    val effectiveId: String
        get() = jobId?.takeIf { it.isNotBlank() } ?: id

    val effectiveProgressPct: Int
        get() = (progressPct ?: progress ?: run {
            val total = totalBytes ?: 0L
            val processed = bytesProcessed ?: 0L
            if (total > 0L) ((processed * 100L) / total).toInt()
            else {
                val ti = totalItems ?: 0
                val ip = itemsProcessed ?: 0
                if (ti > 0) ((ip * 100) / ti) else 0
            }
        }).coerceIn(0, 100)

    val effectiveError: String?
        get() = error?.takeIf { it.isNotBlank() } ?: lastError?.takeIf { it.isNotBlank() }

    val normalizedStatus: String
        get() = status.lowercase()

    val isActiveStatus: Boolean
        get() = normalizedStatus in setOf("queued", "running", "retrying", "pending")

    val isCompletedStatus: Boolean
        get() = normalizedStatus == "completed"

    val isFailedOrCancelledStatus: Boolean
        get() = normalizedStatus in setOf("failed", "cancelled", "completed_with_errors", "dead_letter")
}

@JsonClass(generateAdapter = true)
data class DriveManifestFile(
    val id: String = "",
    val name: String = "",
    val folderPath: String = "",
    val relativePath: String = "",
    val sizeBytes: Long = 0L,
    val mimeType: String? = null,
    val updatedAt: String? = null,
    val isEncrypted: Boolean = false,
    val isNote: Boolean = false
)

@JsonClass(generateAdapter = true)
data class DriveManifestResponse(
    val accountId: String = "",
    val accountType: String = "",
    val driveName: String = "",
    val rootFolderId: String = "root",
    val generatedAt: String? = null,
    val truncated: Boolean = false,
    val totalFolders: Int = 0,
    val totalFiles: Int = 0,
    val totalBytes: Long = 0L,
    val folders: List<String> = emptyList(),
    val files: List<DriveManifestFile> = emptyList()
)

@JsonClass(generateAdapter = true)
data class DriveTransferRequest(
    val sourceAccountId: String,
    val targetAccountId: String,
    val sourceFolderId: String = "root",
    val targetFolderId: String = "root",
    val isMove: Boolean = false,
    val conflictStrategy: String = "skip"
)

@JsonClass(generateAdapter = true)
data class MediaLinkResponse(
    val url: String = "",
    val expiresAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class BatchBackupRequest(
    val sourceAccountId: String,
    val targetAccountId: String,
    val targetFolderId: String = "root",
    val fileIds: List<String>,
    val conflictStrategy: String = "rename"
)

@JsonClass(generateAdapter = true)
data class BackupVerifyResponse(
    val jobId: String = "",
    val verified: Boolean = false,
    val totalItems: Int = 0,
    val verifiedCount: Int = 0,
    val failedVerifications: List<String> = emptyList(),
    val verifiedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class BackupRestoreRequest(
    val targetAccountId: String,
    val targetFolderId: String = "root",
    val conflictStrategy: String = "rename"
)

data class ParsedApiError(
    val message: String,
    val code: String? = null,
    val requestId: String? = null,
    val retryable: Boolean? = null
)



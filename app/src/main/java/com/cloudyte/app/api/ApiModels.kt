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
    val isDirectory: Boolean? = false
) {
    val effectiveId: String get() = id ?: _id ?: fileId ?: ""
    val effectiveName: String get() = name ?: fileName ?: title ?: "Unnamed File"
    val isFolderItem: Boolean get() = isFolder == true || is_dir == true || isDirectory == true || type.equals("folder", ignoreCase = true)
    
    val fileExtension: String
        get() = effectiveName.substringAfterLast('.', "").lowercase(Locale.US)

    val allTypeCandidates: List<String>
        get() = listOfNotNull(
            type?.lowercase(Locale.US),
            fileType?.lowercase(Locale.US),
            mimeType?.lowercase(Locale.US),
            mimeType?.substringAfterLast('/')?.lowercase(Locale.US),
            fileExtension.takeIf { it.isNotBlank() }
        )

    val effectiveType: String get() {
        if (isFolderItem) return "folder"
        val ext = fileExtension
        if (ext.isNotBlank() && ext.length <= 6 && !effectiveName.startsWith(".trashed")) {
            return ext
        }
        val rawType = type ?: fileType ?: mimeType?.substringAfterLast('/') ?: ext
        return if (rawType.isNotBlank()) rawType.lowercase(Locale.US) else "file"
    }

    val isImage: Boolean get() = !isFolderItem && (
        allTypeCandidates.any {
            it in listOf("image", "photo", "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "heic", "heif", "ico", "jfif", "avif") ||
                it.startsWith("image/")
        } || effectiveName.contains("IMG_", ignoreCase = true) || effectiveName.contains("Screenshot", ignoreCase = true)
    )

    val isVideo: Boolean get() = !isFolderItem && (
        allTypeCandidates.any {
            it in listOf("video", "mp4", "mkv", "avi", "mov", "flv", "wmv", "3gp", "webm", "m4v", "ts", "mpeg", "mpg") ||
                it.startsWith("video/")
        } || effectiveName.contains("VID_", ignoreCase = true)
    )

    val isAudio: Boolean get() = !isFolderItem && allTypeCandidates.any {
        it in listOf("audio", "mp3", "wav", "ogg", "m4a", "flac", "aac", "opus", "wma") ||
            it.startsWith("audio/")
    }

    val isTextOrCode: Boolean get() = !isFolderItem && allTypeCandidates.any {
        it in listOf("txt", "text", "json", "js", "ts", "py", "java", "kt", "c", "cpp", "h", "cs", "html", "css", "xml", "yml", "yaml", "sh", "sql", "log", "csv", "env", "md", "markdown", "properties", "gradle", "toml", "conf") ||
            it.startsWith("text/") || it.contains("json")
    }

    val isPdf: Boolean get() = !isFolderItem && allTypeCandidates.any {
        it == "pdf" || it.contains("pdf")
    }
    
    val sizeBytes: Long get() = parseBytes(size)
    
    val formattedSize: String get() {
        if (size is String && size.contains(Regex("[a-zA-Z]"))) return size.toString()
        val s = sizeBytes
        if (s == 0L) return "0 B"
        return when {
            s >= 1024L * 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f TB", s.toDouble() / (1024L * 1024 * 1024 * 1024))
            s >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", s.toDouble() / (1024L * 1024 * 1024))
            s >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", s.toDouble() / (1024L * 1024))
            s >= 1024 -> String.format(Locale.US, "%.1f KB", s.toDouble() / 1024)
            else -> "$s B"
        }
    }
}

fun parseBytes(value: Any?): Long {
    when (value) {
        is Number -> return value.toLong()
        is String -> {
            val v = value.lowercase(Locale.US).replace(" ", "")
            return when {
                v.endsWith("tb") -> (v.removeSuffix("tb").toDoubleOrNull() ?: 0.0).times(1024L * 1024 * 1024 * 1024).toLong()
                v.endsWith("gb") -> (v.removeSuffix("gb").toDoubleOrNull() ?: 0.0).times(1024L * 1024 * 1024).toLong()
                v.endsWith("mb") -> (v.removeSuffix("mb").toDoubleOrNull() ?: 0.0).times(1024L * 1024).toLong()
                v.endsWith("kb") -> (v.removeSuffix("kb").toDoubleOrNull() ?: 0.0).times(1024L).toLong()
                v.endsWith("b") -> v.removeSuffix("b").toLongOrNull() ?: 0L
                else -> value.toLongOrNull() ?: 0L
            }
        }
        else -> return 0L
    }
}

@JsonClass(generateAdapter = true)
data class UploadSessionRequest(
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val folderId: String? = null,
    val checksum: String? = null,
    val idempotencyKey: String
)

@JsonClass(generateAdapter = true)
data class UploadSessionResponse(
    val sessionId: String,
    val status: String,
    val receivedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val chunkSize: Long = 8L * 1024L * 1024L,
    val checksum: String? = null
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

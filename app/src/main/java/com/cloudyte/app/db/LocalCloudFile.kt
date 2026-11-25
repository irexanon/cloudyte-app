package com.cloudyte.app.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.api.formatByteCount
import com.cloudyte.app.core.security.CryptoConstants
import com.cloudyte.app.core.security.EncryptedMetadataCodec

@Entity(
    tableName = "cloud_files",
    indices = [
        Index(value = ["accountId", "folderId"]),
        Index(value = ["isEncrypted"]),
        Index(value = ["isFolder", "time", "updatedAt"])
    ]
)
data class LocalCloudFile(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val size: String,
    val rawSize: Long = 0L,
    val time: Long = 0L,
    val isFolder: Boolean = false,
    val path: String = "",
    val folderId: String = "root",
    val accountId: String? = null,
    val mimeType: String? = null,
    val shareId: String? = null,
    val isShared: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long? = null,
    val thumbUrl: String? = null,
    val isEncrypted: Boolean = false,
    val encryptionFormatVersion: Int? = null,
    val encryptionKeyId: String? = null,
    val encryptedMetadata: String? = null,
    val encryptedSizeBytes: Long? = null,
    val encryptedChecksum: String? = null,
    val opaqueRemoteName: String? = null,
    val plaintextSizeBytes: Long? = null,
    val originalMetadataAvailable: Boolean = false,
    val isNote: Boolean = false
) {
    fun toCloudFile(): CloudFile {
        val parsedEnvelope = if (!encryptedMetadata.isNullOrBlank()) {
            try {
                sharedMetadataCodec.deserializeEnvelopeFromJson(encryptedMetadata)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        val effectiveDisplayName = if (isEncrypted && !originalMetadataAvailable) {
            CryptoConstants.ENCRYPTED_FALLBACK_DISPLAY_NAME
        } else {
            name
        }
        val effectiveDisplayType = if (isEncrypted && !originalMetadataAvailable) {
            "encrypted"
        } else {
            type
        }
        val effectiveDisplayMime = if (isEncrypted && !originalMetadataAvailable) {
            CryptoConstants.ENCRYPTED_MIME_TYPE
        } else {
            mimeType
        }
        val effectiveSizeBytes = when {
            isEncrypted && originalMetadataAvailable && plaintextSizeBytes != null -> plaintextSizeBytes
            isEncrypted && encryptedSizeBytes != null -> encryptedSizeBytes
            else -> rawSize
        }

        return CloudFile(
            id = this.id,
            name = effectiveDisplayName,
            type = effectiveDisplayType,
            size = if (isEncrypted) formatByteCount(effectiveSizeBytes) else this.size,
            time = this.time,
            isFolder = this.isFolder,
            path = if (isEncrypted && !originalMetadataAvailable) "" else this.path,
            mimeType = effectiveDisplayMime,
            updatedAt = this.updatedAt,
            thumbUrl = if (isEncrypted) null else this.thumbUrl,
            accountId = this.accountId,
            isEncrypted = this.isEncrypted,
            encryptionFormatVersion = this.encryptionFormatVersion,
            encryptionKeyId = this.encryptionKeyId,
            encryptedMetadata = parsedEnvelope,
            encryptedSizeBytes = this.encryptedSizeBytes,
            encryptedChecksum = this.encryptedChecksum,
            opaqueRemoteName = this.opaqueRemoteName,
            plaintextSizeBytes = this.plaintextSizeBytes,
            originalMetadataAvailable = this.originalMetadataAvailable,
            metadataStatus = when {
                !isEncrypted -> "PLAINTEXT"
                originalMetadataAvailable -> "AVAILABLE"
                else -> "UNAVAILABLE"
            },
            isNote = this.isNote
        )
    }

    companion object {
        private val sharedMetadataCodec = EncryptedMetadataCodec()

        fun fromCloudFile(file: CloudFile, accountId: String?, folderId: String? = null): LocalCloudFile {
            val safeFolderId = if (folderId.isNullOrBlank() || folderId == "root") "root" else folderId
            val resolvedAccId = file.effectiveAccountId.ifBlank { accountId.orEmpty() }
            val serializedEnvelope = file.encryptedMetadata?.let {
                try {
                    sharedMetadataCodec.serializeEnvelopeToJson(it)
                } catch (_: Exception) {
                    null
                }
            }
            val rawRemoteName = file.opaqueRemoteName
                ?: (file.name ?: file.fileName ?: file.title).takeIf { file.isEncrypted }
            val displayName = if (file.isEncrypted && !file.originalMetadataAvailable) {
                CryptoConstants.ENCRYPTED_FALLBACK_DISPLAY_NAME
            } else {
                file.effectiveName
            }
            val displayType = if (file.isEncrypted && !file.originalMetadataAvailable) {
                "encrypted"
            } else {
                file.effectiveType
            }
            val displayMime = if (file.isEncrypted && !file.originalMetadataAvailable) {
                CryptoConstants.ENCRYPTED_MIME_TYPE
            } else {
                file.mimeType
            }

            return LocalCloudFile(
                id = if (file.effectiveId.isNotBlank()) file.effectiveId else "${resolvedAccId}_${safeFolderId}_${displayName}",
                name = displayName,
                type = displayType,
                size = file.formattedSize,
                rawSize = file.sizeBytes,
                time = file.time ?: 0L,
                isFolder = file.isFolderItem,
                path = if (file.isEncrypted && !file.originalMetadataAvailable) "" else (file.path ?: ""),
                folderId = safeFolderId,
                accountId = resolvedAccId,
                mimeType = displayMime,
                cachedAt = System.currentTimeMillis(),
                updatedAt = file.updatedAt,
                thumbUrl = if (file.isEncrypted) null else file.thumbUrl,
                isEncrypted = file.isEncrypted,
                encryptionFormatVersion = file.encryptionFormatVersion,
                encryptionKeyId = file.encryptionKeyId ?: file.encryptedMetadata?.keyId,
                encryptedMetadata = serializedEnvelope,
                encryptedSizeBytes = file.encryptedSizeBytes ?: if (file.isEncrypted) parseRawBytesFromCloudFile(file) else null,
                encryptedChecksum = file.encryptedChecksum,
                opaqueRemoteName = rawRemoteName,
                plaintextSizeBytes = file.plaintextSizeBytes,
                originalMetadataAvailable = file.originalMetadataAvailable,
                isNote = file.isNoteItem
            )
        }

        private fun parseRawBytesFromCloudFile(file: CloudFile): Long {
            return com.cloudyte.app.api.parseBytes(file.size)
        }
    }
}

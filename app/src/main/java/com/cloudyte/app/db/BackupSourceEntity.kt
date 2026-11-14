package com.cloudyte.app.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class BackupPresetType(val label: String) {
    CAMERA("Camera (DCIM)"),
    PICTURES("Pictures"),
    DOCUMENTS("Documents"),
    DOWNLOADS("Downloads"),
    CUSTOM("Custom Folder")
}

enum class BackupSourcePermissionStatus {
    UNKNOWN,
    GRANTED,
    REVOKED,
    MISSING,
    UNREADABLE
}

@Entity(
    tableName = "backup_sources",
    indices = [Index(value = ["treeUri"], unique = true)]
)
data class BackupSourceEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val treeUri: String,
    val displayName: String,
    val presetType: String = BackupPresetType.CUSTOM.name,
    val targetAccountId: String? = null,
    val targetFolderId: String? = null,
    val enabled: Boolean = true,
    val permissionStatus: String = BackupSourcePermissionStatus.UNKNOWN.name,
    val lastScanAt: Long = 0L,
    val lastBackupAt: Long = 0L,
    val lastSummary: String? = null,
    val lastError: String? = null,
    val lastQueuedCount: Int = 0,
    val lastSkippedCount: Int = 0,
    val lastFailedCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

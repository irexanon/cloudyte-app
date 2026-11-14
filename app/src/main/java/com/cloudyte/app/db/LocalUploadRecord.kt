package com.cloudyte.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upload_records")
data class LocalUploadRecord(
    @PrimaryKey(autoGenerate = true) val recordId: Long = 0,
    val localUri: String,
    val fileName: String,
    val fileSize: Long,
    val lastModified: Long,
    val targetAccountId: String,
    val targetFolderId: String,
    val cloudFileId: String? = null,
    val uploadedAt: Long = System.currentTimeMillis(),
    val status: String = "SUCCESS", // "SUCCESS", "FAILED"
    val contentHash: String? = null
)

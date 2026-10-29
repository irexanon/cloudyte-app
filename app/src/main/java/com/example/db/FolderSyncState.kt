package com.example.db

import androidx.room.Entity

/** Persisted freshness/conditional-request metadata for one account and folder pair. */
@Entity(tableName = "folder_sync_state", primaryKeys = ["accountKey", "folderKey"])
data class FolderSyncState(
    val accountKey: String,
    val folderKey: String,
    val etag: String?,
    val lastSuccessfulSyncAt: Long
)

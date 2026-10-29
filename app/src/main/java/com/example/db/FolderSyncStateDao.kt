package com.example.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FolderSyncStateDao {
    @Query("SELECT * FROM folder_sync_state WHERE accountKey = :accountKey AND folderKey = :folderKey LIMIT 1")
    suspend fun get(accountKey: String, folderKey: String): FolderSyncState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: FolderSyncState)

    @Query("DELETE FROM folder_sync_state WHERE accountKey = :accountKey AND folderKey = :folderKey")
    suspend fun delete(accountKey: String, folderKey: String)
}

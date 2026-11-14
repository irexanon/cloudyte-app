package com.cloudyte.app.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupSourceDao {
    @Query("SELECT * FROM backup_sources ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<BackupSourceEntity>>

    @Query("SELECT * FROM backup_sources ORDER BY createdAt ASC")
    suspend fun getAll(): List<BackupSourceEntity>

    @Query("SELECT * FROM backup_sources WHERE enabled = 1 ORDER BY createdAt ASC")
    suspend fun getEnabledSources(): List<BackupSourceEntity>

    @Query("SELECT * FROM backup_sources WHERE treeUri = :treeUri LIMIT 1")
    suspend fun findByTreeUri(treeUri: String): BackupSourceEntity?

    @Query("SELECT * FROM backup_sources WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): BackupSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: BackupSourceEntity)

    @Update
    suspend fun update(source: BackupSourceEntity)

    @Delete
    suspend fun delete(source: BackupSourceEntity)

    @Query("DELETE FROM backup_sources WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        "UPDATE backup_sources SET permissionStatus = :status, lastError = :error, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updatePermissionStatus(id: String, status: String, error: String?, updatedAt: Long)

    @Query(
        "UPDATE backup_sources SET permissionStatus = :status, lastScanAt = :scanAt, lastBackupAt = CASE WHEN :backupSucceeded THEN :scanAt ELSE lastBackupAt END, lastSummary = :summary, lastError = :error, lastQueuedCount = :queuedCount, lastSkippedCount = :skippedCount, lastFailedCount = :failedCount, updatedAt = :scanAt WHERE id = :id"
    )
    suspend fun updateScanResult(
        id: String,
        status: String,
        scanAt: Long,
        backupSucceeded: Boolean,
        summary: String?,
        error: String?,
        queuedCount: Int,
        skippedCount: Int,
        failedCount: Int
    )
}

package com.cloudyte.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadRecordDao {
    @Query("SELECT * FROM upload_records WHERE fileName = :fileName AND targetAccountId = :accountId AND targetFolderId = :folderId ORDER BY uploadedAt DESC LIMIT 1")
    suspend fun getRecord(fileName: String, accountId: String, folderId: String): LocalUploadRecord?

    @Query("SELECT * FROM upload_records WHERE localUri = :localUri AND targetAccountId = :accountId AND targetFolderId = :folderId AND status = 'SUCCESS' ORDER BY uploadedAt DESC LIMIT 1")
    suspend fun findLatestSuccessByUri(
        localUri: String,
        accountId: String,
        folderId: String
    ): LocalUploadRecord?

    @Query("SELECT * FROM upload_records WHERE contentHash = :contentHash AND fileSize = :fileSize AND targetAccountId = :accountId AND targetFolderId = :folderId AND status = 'SUCCESS' ORDER BY uploadedAt DESC LIMIT 1")
    suspend fun findLatestSuccessByHash(
        contentHash: String,
        fileSize: Long,
        accountId: String,
        folderId: String
    ): LocalUploadRecord?

    @Query("SELECT * FROM upload_records WHERE fileName = :fileName AND fileSize = :fileSize AND targetAccountId = :accountId AND status = 'SUCCESS' LIMIT 1")
    suspend fun findMatchingSuccessRecord(fileName: String, fileSize: Long, accountId: String): LocalUploadRecord?

    @Query("SELECT * FROM upload_records WHERE fileName = :fileName AND fileSize = :fileSize AND lastModified = :lastModified AND targetAccountId = :accountId AND status = 'SUCCESS' LIMIT 1")
    suspend fun findMatchingSuccessRecord(
        fileName: String,
        fileSize: Long,
        lastModified: Long,
        accountId: String
    ): LocalUploadRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: LocalUploadRecord)

    @Query("SELECT * FROM upload_records ORDER BY uploadedAt DESC")
    fun getAllRecordsFlow(): Flow<List<LocalUploadRecord>>

    @Query("SELECT * FROM upload_records ORDER BY uploadedAt DESC")
    suspend fun getAllRecords(): List<LocalUploadRecord>

    @Query("SELECT COUNT(*) FROM upload_records WHERE status = 'SUCCESS'")
    suspend fun countSuccessfulUploads(): Int

    @Query("SELECT COUNT(*) FROM upload_records WHERE status = 'SUCCESS'")
    fun countSuccessfulUploadsFlow(): Flow<Int>

    @Query("DELETE FROM upload_records")
    suspend fun clearHistory()
}

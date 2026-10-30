package com.cloudyte.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudFileDao {
    @Query("SELECT * FROM cloud_files WHERE (accountId = :accountId OR (:accountId IS NULL AND accountId IS NULL)) AND folderId = :folderId ORDER BY isFolder DESC, time DESC, name ASC")
    fun getFilesForFolderFlow(accountId: String?, folderId: String): Flow<List<LocalCloudFile>>

    @Query("SELECT * FROM cloud_files WHERE (accountId = :accountId OR (:accountId IS NULL AND accountId IS NULL)) AND folderId = :folderId ORDER BY isFolder DESC, time DESC, name ASC")
    suspend fun getFilesForFolder(accountId: String?, folderId: String): List<LocalCloudFile>

    @Query("SELECT * FROM cloud_files WHERE (accountId = :accountId OR (:accountId IS NULL AND accountId IS NULL)) ORDER BY isFolder DESC, time DESC, name ASC")
    suspend fun getFilesForAccount(accountId: String?): List<LocalCloudFile>

    @Query("SELECT * FROM cloud_files WHERE isFolder = 0 ORDER BY time DESC, updatedAt DESC, name ASC LIMIT :limit")
    suspend fun getRecentFiles(limit: Int): List<LocalCloudFile>

    @Query("SELECT * FROM cloud_files WHERE (accountId = :accountId OR (:accountId IS NULL AND accountId IS NULL)) AND name LIKE '%' || :query || '%' ORDER BY isFolder DESC, name ASC")
    suspend fun searchFiles(accountId: String?, query: String): List<LocalCloudFile>

    @Query("SELECT * FROM cloud_files")
    suspend fun getAllFiles(): List<LocalCloudFile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<LocalCloudFile>)

    @Query("DELETE FROM cloud_files WHERE (accountId = :accountId OR (:accountId IS NULL AND accountId IS NULL)) AND folderId = :folderId")
    suspend fun deleteFilesForFolder(accountId: String?, folderId: String)

    @Query("DELETE FROM cloud_files WHERE (accountId = :accountId OR (:accountId IS NULL AND accountId IS NULL))")
    suspend fun deleteFilesForAccount(accountId: String?)

    @Query("DELETE FROM cloud_files WHERE id = :id")
    suspend fun deleteFileById(id: String)

    @Query("DELETE FROM cloud_files WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE cloud_files SET name = :name WHERE id = :id")
    suspend fun updateName(id: String, name: String)

    @Query("SELECT id FROM cloud_files WHERE (accountId = :accountId OR (:accountId IS NULL AND accountId IS NULL)) AND folderId = :folderId")
    suspend fun getFileIdsForFolder(accountId: String?, folderId: String): List<String>

    @Query("DELETE FROM cloud_files WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Transaction
    suspend fun replaceFolder(accountId: String?, folderId: String, files: List<LocalCloudFile>) {
        insertFiles(files)
        val validIds = files.map { it.id }.toSet()
        val existingIds = getFileIdsForFolder(accountId, folderId)
        val staleIds = existingIds.filter { it !in validIds }
        if (staleIds.isNotEmpty()) {
            staleIds.chunked(500).forEach { chunk ->
                deleteByIds(chunk)
            }
        }
    }

    @Query("SELECT * FROM cloud_files WHERE id = :id LIMIT 1")
    suspend fun getFileById(id: String): LocalCloudFile?

    @Query("DELETE FROM cloud_files")
    suspend fun clearAll()
}

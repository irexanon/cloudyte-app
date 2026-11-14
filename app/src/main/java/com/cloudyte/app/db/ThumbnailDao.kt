package com.cloudyte.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ThumbnailDao {
    @Query("SELECT * FROM thumbnail_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun get(cacheKey: String): ThumbnailEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ThumbnailEntity)

    @Query("UPDATE thumbnail_cache SET lastAccessAt = :now WHERE cacheKey = :cacheKey")
    suspend fun touch(cacheKey: String, now: Long)

    @Query("UPDATE thumbnail_cache SET failureCount = :failureCount, nextRetryAt = :nextRetryAt WHERE cacheKey = :cacheKey")
    suspend fun recordFailure(cacheKey: String, failureCount: Int, nextRetryAt: Long?)

    @Query("DELETE FROM thumbnail_cache WHERE cacheKey = :cacheKey")
    suspend fun delete(cacheKey: String)

    @Query("DELETE FROM thumbnail_cache WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)

    @Query("DELETE FROM thumbnail_cache")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM thumbnail_cache")
    suspend fun totalBytes(): Long

    @Query("SELECT * FROM thumbnail_cache WHERE lastAccessAt < :before ORDER BY lastAccessAt ASC")
    suspend fun expired(before: Long): List<ThumbnailEntity>

    @Query("SELECT * FROM thumbnail_cache ORDER BY lastAccessAt ASC")
    suspend fun oldestFirst(): List<ThumbnailEntity>
}

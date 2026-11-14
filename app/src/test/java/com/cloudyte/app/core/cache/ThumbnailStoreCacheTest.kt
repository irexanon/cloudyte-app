package com.cloudyte.app.core.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.ThumbnailEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThumbnailStoreCacheTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var store: ThumbnailStore
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = ThumbnailStore(context, db.thumbnailDao())
        tempDir = File(context.cacheDir, "thumb_test_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun findAnyCachedPrefersLargestMatchingRevisionWithoutNetwork() = runTest {
        val file = CloudFile(
            id = "file-123",
            name = "photo.jpg",
            type = "image",
            updatedAt = 5000L,
            accountId = "acc-1"
        )
        val smallFile = File(tempDir, "thumb_144.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val largeFile = File(tempDir, "thumb_512.jpg").apply { writeBytes(byteArrayOf(4, 5, 6, 7)) }

        val key144 = CacheKeyFactory.thumbnail("acc-1", "google", file, 144)
        val key512 = CacheKeyFactory.thumbnail("acc-1", "google", file, 512)

        db.thumbnailDao().upsert(
            ThumbnailEntity(
                cacheKey = key144,
                accountId = "acc-1",
                providerType = "google",
                remoteId = "file-123",
                sourceRevision = "5000",
                path = smallFile.absolutePath,
                mimeType = "image/jpeg",
                width = 144,
                height = 144,
                sizeBytes = smallFile.length(),
                isEncrypted = false,
                generatedAt = 100L,
                lastAccessAt = 100L
            )
        )
        db.thumbnailDao().upsert(
            ThumbnailEntity(
                cacheKey = key512,
                accountId = "acc-1",
                providerType = "google",
                remoteId = "file-123",
                sourceRevision = "5000",
                path = largeFile.absolutePath,
                mimeType = "image/jpeg",
                width = 512,
                height = 512,
                sizeBytes = largeFile.length(),
                isEncrypted = false,
                generatedAt = 200L,
                lastAccessAt = 200L
            )
        )

        val hit = store.findAnyCached("acc-1", file)
        assertNotNull(hit)
        assertEquals(largeFile.absolutePath, hit?.absolutePath)

        // Newer revision must not hit stale cached thumbnail
        val staleCheck = store.findAnyCached("acc-1", file.copy(updatedAt = 6000L))
        assertNull(staleCheck)
    }

    @Test
    fun findAnyCachedSkipsMissingDiskFile() = runTest {
        val file = CloudFile(
            id = "file-missing",
            name = "img.jpg",
            type = "image",
            updatedAt = 1000L,
            accountId = "acc-1"
        )
        val missingFile = File(tempDir, "does_not_exist.jpg")
        db.thumbnailDao().upsert(
            ThumbnailEntity(
                cacheKey = CacheKeyFactory.thumbnail("acc-1", "onedrive", file, 256),
                accountId = "acc-1",
                providerType = "onedrive",
                remoteId = "file-missing",
                sourceRevision = "1000",
                path = missingFile.absolutePath,
                mimeType = "image/jpeg",
                width = 256,
                height = 256,
                sizeBytes = 100L,
                isEncrypted = false,
                generatedAt = 100L,
                lastAccessAt = 100L
            )
        )

        assertNull(store.findAnyCached("acc-1", file))
    }
}

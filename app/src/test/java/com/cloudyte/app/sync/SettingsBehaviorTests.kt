package com.cloudyte.app.sync

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.cloudyte.app.api.OfflineFileManager
import com.cloudyte.app.core.cache.CacheCleanupWorker
import com.cloudyte.app.core.cache.StorageManager
import com.cloudyte.app.core.transfer.notification.NotificationEventType
import com.cloudyte.app.core.transfer.notification.NotificationHelper
import com.cloudyte.app.data.AccountSessionHelper
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.UserPreferencesState
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.BackupPresetType
import com.cloudyte.app.db.BackupSourceEntity
import com.cloudyte.app.db.BackupSourcePermissionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SettingsBehaviorTests {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        UserPreferences.resetAllSettings(context)
        SyncLogger.init(context)
        SyncLogger.clearLogs()
    }

    @Test
    fun migration14To15AddsBackupSourcesAndNewColumnsWithoutDataLoss() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE exclusions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, pattern TEXT NOT NULL, isRegex INTEGER NOT NULL, enabled INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO exclusions (pattern, isRegex, enabled) VALUES ('*.tmp', 0, 1)")
                        db.execSQL("CREATE TABLE upload_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, localUri TEXT NOT NULL, fileName TEXT NOT NULL, fileSize INTEGER NOT NULL, lastModified INTEGER NOT NULL, targetAccountId TEXT NOT NULL, targetFolderId TEXT NOT NULL, cloudFileId TEXT, uploadedAt INTEGER NOT NULL, status TEXT NOT NULL)")
                        db.execSQL("INSERT INTO upload_records (localUri, fileName, fileSize, lastModified, targetAccountId, targetFolderId, uploadedAt, status) VALUES ('file://a', 'a.jpg', 10, 1, 'acc1', 'root', 1, 'SUCCESS')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase
        AppDatabase.MIGRATION_14_15.migrate(db)

        db.query("PRAGMA table_info(backup_sources)").use { cursor ->
            val cols = mutableSetOf<String>()
            while (cursor.moveToNext()) cols += cursor.getString(1)
            assertTrue(cols.containsAll(setOf("id", "treeUri", "displayName", "presetType", "permissionStatus", "lastScanAt", "lastBackupAt")))
        }
        db.query("SELECT pattern, ruleType FROM exclusions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("*.tmp", cursor.getString(0))
            assertEquals("PATH_PATTERN", cursor.getString(1))
        }
        db.query("SELECT fileName, contentHash FROM upload_records").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("a.jpg", cursor.getString(0))
            assertNull(cursor.getString(1))
        }
        helper.close()
    }

    @Test
    fun permissionsHelperReflectsLiveGrantDenialAndRevocation() = runBlocking {
        val appShadow = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        appShadow.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(PermissionsHelper.isRuntimePostNotificationsGranted(context))
        assertFalse(PermissionsHelper.isNotificationPermissionGranted(context))

        appShadow.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(PermissionsHelper.isRuntimePostNotificationsGranted(context))
        assertTrue(PermissionsHelper.isNotificationPermissionGranted(context))

        // Revoking runtime permission updates check immediately (return from settings scenario)
        appShadow.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(PermissionsHelper.isNotificationPermissionGranted(context))

        // SAF content URI without persisted permission reports REVOKED, never GRANTED
        val fakeContentTree = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:Pictures")
        val health = BackupSourceManager.inspectSourceHealth(
            context,
            BackupSourceEntity(id = "s1", treeUri = fakeContentTree.toString(), displayName = "Pictures")
        )
        assertEquals(BackupSourcePermissionStatus.REVOKED, health.status)
    }

    @Test
    fun storageManagerMeasuresAndClearsCacheThumbnailsAndDownloadsSeparately() = runBlocking {
        val db = AppDatabase.getDatabase(context)
        db.thumbnailDao().deleteAll()
        StorageManager.clearAppCache(context)
        StorageManager.clearThumbnails(context)
        StorageManager.clearDownloads(context)

        val previewFile = File(File(context.cacheDir, "cloudyte_previews").apply { mkdirs() }, "p1.bin").apply {
            writeBytes(ByteArray(200))
        }
        val thumbFile = File(File(context.filesDir, "cloudyte_thumbnails").apply { mkdirs() }, "t1.jpg").apply {
            writeBytes(ByteArray(300))
        }
        val offlineFile = File(OfflineFileManager.getInternalVaultDir(context), "acc_f1_doc.pdf").apply {
            writeBytes(ByteArray(400))
        }

        val initial = StorageManager.measure(context)
        assertTrue(initial.totalAppCacheBytes >= 200L)
        assertTrue(initial.thumbnailBytes >= 300L)
        assertTrue(initial.totalDownloadsBytes >= 400L)

        // Clearing cache leaves thumbnails & downloads intact
        val cacheCleared = StorageManager.clearAppCache(context)
        assertTrue(cacheCleared.freedBytes >= 200L)
        assertFalse(previewFile.exists())
        assertTrue(thumbFile.exists())
        assertTrue(offlineFile.exists())

        // Clearing thumbnails leaves downloads intact
        val thumbsCleared = StorageManager.clearThumbnails(context)
        assertTrue(thumbsCleared.freedBytes >= 300L)
        assertFalse(thumbFile.exists())
        assertTrue(offlineFile.exists())

        // Clearing downloads deletes offline vault files
        val downloadsCleared = StorageManager.clearDownloads(context)
        assertTrue(downloadsCleared.freedBytes >= 400L)
        assertFalse(offlineFile.exists())
    }

    @Test
    fun downloadDirectorySettingSelectsInternalVsExternalAndCacheCleanupWorkerReschedules() = runBlocking {
        UserPreferences.setDownloadDirectory(context, "INTERNAL")
        assertTrue(OfflineFileManager.getPreferredVaultDir(context).absolutePath.startsWith(context.filesDir.absolutePath))
        assertTrue(StorageManager.getPreferredExportDir(context).absolutePath.startsWith(context.filesDir.absolutePath))

        UserPreferences.setDownloadDirectory(context, "EXTERNAL")
        val extExpected = context.getExternalFilesDir("offline_vault")
        if (extExpected != null) {
            assertEquals(extExpected.absolutePath, OfflineFileManager.getPreferredVaultDir(context).absolutePath)
        }

        CacheCleanupWorker.syncSchedule(context, true)
        val scheduled = WorkManager.getInstance(context).getWorkInfosForUniqueWork(CacheCleanupWorker.UNIQUE_WORK_NAME).get()
        assertEquals(WorkInfo.State.ENQUEUED, scheduled.first().state)

        CacheCleanupWorker.syncSchedule(context, false)
        val cancelled = WorkManager.getInstance(context).getWorkInfosForUniqueWork(CacheCleanupWorker.UNIQUE_WORK_NAME).get()
        assertTrue(cancelled.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun syncLoggerRedactsCredentialsRotatesFilesAndExports() {
        val raw = "GET https://api.example.com/dl?token=secret123&api_key=xyz Authorization: Bearer eyJhbGciOiJIUzI1NiJ9 password=myPass123"
        val redacted = SyncLogger.redact(raw)
        assertFalse(redacted.contains("secret123"))
        assertFalse(redacted.contains("xyz"))
        assertFalse(redacted.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertFalse(redacted.contains("myPass123"))
        assertTrue(redacted.contains("[REDACTED]"))

        SyncLogger.log(raw, SyncLogLevel.INFO)
        val outFile = File(tempFolder.root, "exported_logs.txt")
        val exportResult = SyncLogger.exportToUri(context, Uri.fromFile(outFile))
        assertTrue(exportResult.isSuccess)
        val exported = outFile.readText()
        assertFalse(exported.contains("secret123"))
        assertTrue(exported.contains("[REDACTED]"))
    }

    @Test
    fun notificationHelperHonorsMasterAndPerTypePreferences() {
        val disabledMaster = UserPreferencesState(notificationsEnabled = false, backupNotifications = true, completedAlerts = true, failureNotifications = true)
        assertFalse(NotificationHelper.shouldPostEvent(disabledMaster, NotificationEventType.BACKUP_COMPLETED))
        assertFalse(NotificationHelper.shouldPostEvent(disabledMaster, NotificationEventType.TRANSFER_FAILURE))

        val backupMuted = UserPreferencesState(notificationsEnabled = true, backupNotifications = false, completedAlerts = true, failureNotifications = true)
        assertFalse(NotificationHelper.shouldPostEvent(backupMuted, NotificationEventType.BACKUP_COMPLETED))
        assertTrue(NotificationHelper.shouldPostEvent(backupMuted, NotificationEventType.TRANSFER_COMPLETED))
    }

    @Test
    fun logoutRevokesOrWarnsAndClearsLocalCachesEvenWhenRemoteFails() = runBlocking {
        val db = AppDatabase.getDatabase(context)
        UserPreferences.setLastActiveAccountId(context, "acc1")

        val offlineLogout = AccountSessionHelper.performLogout(context) { false }
        assertFalse(offlineLogout.remoteRevoked)
        assertNotNull(offlineLogout.warningMessage)
        assertNull(UserPreferences.observe(context).first().lastActiveAccountId)
        assertTrue(db.accountDao().getAllAccounts().isEmpty())

        val onlineLogout = AccountSessionHelper.performLogout(context) { true }
        assertTrue(onlineLogout.remoteRevoked)
        assertNull(onlineLogout.warningMessage)
    }
}

package com.cloudyte.app.sync

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.core.network.NetworkPolicySnapshot
import com.cloudyte.app.core.network.NetworkStatus
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.BackupPresetType
import com.cloudyte.app.db.BackupSourceEntity
import com.cloudyte.app.db.BackupSourcePermissionStatus
import com.cloudyte.app.db.ExclusionRuleType
import com.cloudyte.app.db.LocalAccount
import com.cloudyte.app.db.LocalExclusion
import com.cloudyte.app.db.LocalUploadRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class BackupWorkerIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        BackupWorkerHooks.reset()
        UserPreferences.resetAllSettings(context)

        db = AppDatabase.getDatabase(context)
        db.backupSourceDao().getAll().forEach { db.backupSourceDao().deleteById(it.id) }
        db.exclusionDao().getEnabledExclusions().forEach { db.exclusionDao().delete(it) }
        db.transferDao().observeAllTransfers().first().forEach { db.transferDao().delete(it) }
        db.uploadRecordDao().clearHistory()
        db.accountDao().clearAccounts()

        db.accountDao().insertAccounts(
            listOf(
                LocalAccount(
                    id = "acc-1",
                    name = "Primary Drive",
                    driveName = "Primary Drive",
                    email = "user@cloudyte.test",
                    accountType = "cloudyte",
                    usageBytes = 0L,
                    totalBytes = 100_000_000L,
                    percentage = 0f,
                    fileCount = 0,
                    isDefault = true,
                    channelId = null
                )
            )
        )
        UserPreferences.setBackupAccountId(context, "acc-1")
        UserPreferences.setAutoBackupEnabled(context, true)

        BackupWorkerHooks.networkSnapshotProvider = { _, prefs ->
            NetworkPolicySnapshot(
                status = NetworkStatus.VALIDATED_UNMETERED,
                allowMeteredTransfers = prefs.allowMeteredTransfers,
                allowRoaming = prefs.allowRoaming,
                requireChargingForBackup = prefs.backupChargingOnly,
                batterySaverActive = false,
                isRoaming = false,
                isCharging = true,
                batteryNotLow = true,
                requireWifiForBackup = prefs.backupWifiOnly
            )
        }
        BackupWorkerHooks.remoteFilesProvider = { _, _ -> emptyList() }
        BackupWorkerHooks.remoteFolderCreatorProvider = { _, name, _ ->
            CloudFile(id = "remote-dir-$name", name = name, isFolder = true)
        }
        BackupWorkerHooks.scheduleKickOverride = { _, _ -> }
    }

    @After
    fun tearDown() {
        BackupWorkerHooks.reset()
    }

    @Test
    fun multiFolderScanEnforcesExclusionsAndSizeLimitBeforeQueue() = runBlocking {
        val dirA = tempFolder.newFolder("Camera")
        val dirB = tempFolder.newFolder("Documents")

        File(dirA, "keep_photo.jpg").writeBytes(ByteArray(128) { 1 })
        File(dirA, "skip.tmp").writeBytes(ByteArray(64) { 2 })
        File(dirA, ".hidden_file").writeBytes(ByteArray(32) { 3 })
        val ignoredSub = File(dirA, "Trash").apply { mkdirs() }
        File(ignoredSub, "discard.jpg").writeBytes(ByteArray(64) { 4 })

        File(dirB, "notes.txt").writeBytes(ByteArray(100) { 5 })
        // 200 MB exceeds 100 MB cap? Let's keep test fast by using MAX_SIZE_BYTES exclusion rule AND 100 MB global cap on a sparse or small file,
        // and also verify explicit MAX_SIZE_BYTES rule at 150 bytes.
        File(dirB, "oversized_rule.bin").writeBytes(ByteArray(300) { 6 })

        BackupSourceManager.persistAndAddSource(context, Uri.fromFile(dirA), BackupPresetType.CAMERA, db = db, reschedule = false)
        BackupSourceManager.persistAndAddSource(context, Uri.fromFile(dirB), BackupPresetType.DOCUMENTS, db = db, reschedule = false)

        db.exclusionDao().insert(LocalExclusion(pattern = "*.tmp", isRegex = false, enabled = true, ruleType = ExclusionRuleType.PATH_PATTERN.name))
        db.exclusionDao().insert(LocalExclusion(pattern = ".*", isRegex = false, enabled = true, ruleType = ExclusionRuleType.HIDDEN.name))
        db.exclusionDao().insert(LocalExclusion(pattern = "Trash", isRegex = false, enabled = true, ruleType = ExclusionRuleType.FOLDER_PATH.name))
        db.exclusionDao().insert(LocalExclusion(pattern = "150 B", isRegex = false, enabled = true, ruleType = ExclusionRuleType.MAX_SIZE_BYTES.name, sizeLimitBytes = 150L))

        val worker = TestListenableWorkerBuilder<BackupWorker>(context).build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        val queuedNames = db.transferDao().observeAllTransfers().first().map { it.fileName }.toSet()
        assertEquals(setOf("keep_photo.jpg", "notes.txt"), queuedNames)
        assertFalse(queuedNames.contains("skip.tmp"))
        assertFalse(queuedNames.contains(".hidden_file"))
        assertFalse(queuedNames.contains("discard.jpg"))
        assertFalse(queuedNames.contains("oversized_rule.bin"))

        val prefs = UserPreferences.observe(context).first()
        assertEquals(2, prefs.lastBackupQueuedCount)
        assertTrue(prefs.lastBackupSkippedSummary.isNotBlank())
    }

    @Test
    fun duplicateDetectionSkipsUnchangedFilesAndRequeuesChangedFiles() = runBlocking {
        val dir = tempFolder.newFolder("Pictures")
        val sameBytes = "identical-content-123".toByteArray()
        val unchangedFile = File(dir, "unchanged.png").apply { writeBytes(sameBytes) }
        val renamedClone = File(dir, "cloned_copy.png").apply { writeBytes(sameBytes) }
        val modifiedFile = File(dir, "edited.png").apply { writeBytes("version-1".toByteArray()) }

        BackupSourceManager.persistAndAddSource(context, Uri.fromFile(dir), BackupPresetType.PICTURES, db = db, reschedule = false)

        val shaIdentical = sha256Hex(sameBytes)
        val shaOldModified = sha256Hex("old-different-version".toByteArray())

        db.uploadRecordDao().insertRecord(
            LocalUploadRecord(
                localUri = Uri.fromFile(unchangedFile).toString(),
                fileName = "unchanged.png",
                fileSize = sameBytes.size.toLong(),
                lastModified = unchangedFile.lastModified(),
                targetAccountId = "acc-1",
                targetFolderId = "root",
                status = "SUCCESS",
                contentHash = shaIdentical
            )
        )
        db.uploadRecordDao().insertRecord(
            LocalUploadRecord(
                localUri = Uri.fromFile(modifiedFile).toString(),
                fileName = "edited.png",
                fileSize = 999L,
                lastModified = modifiedFile.lastModified() - 5_000L,
                targetAccountId = "acc-1",
                targetFolderId = "root",
                status = "SUCCESS",
                contentHash = shaOldModified
            )
        )

        val worker = TestListenableWorkerBuilder<BackupWorker>(context).build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        val queuedNames = db.transferDao().observeAllTransfers().first().map { it.fileName }
        // unchanged.png skipped by URI+size+mtime; cloned_copy.png skipped by identical contentHash+size; edited.png re-queued!
        assertEquals(listOf("edited.png"), queuedNames)
        assertFalse(queuedNames.contains("unchanged.png"))
        assertFalse(queuedNames.contains("cloned_copy.png"))
    }

    @Test
    fun missingAndUnreadableFoldersMarkErrorsOnBrokenSourcesWithoutBlockingHealthySource() = runBlocking {
        val missingDir = File(tempFolder.root, "DoesNotExist")
        val notADirFile = tempFolder.newFile("NotAFolder.txt").apply { writeText("oops") }
        val healthyDir = tempFolder.newFolder("Healthy")
        File(healthyDir, "doc.pdf").writeBytes("hello".toByteArray())

        val missingSource = BackupSourceEntity(
            id = "missing-src",
            treeUri = Uri.fromFile(missingDir).toString(),
            displayName = "Missing Folder",
            presetType = BackupPresetType.CUSTOM.name,
            enabled = true
        )
        val unreadableSource = BackupSourceEntity(
            id = "unreadable-src",
            treeUri = Uri.fromFile(notADirFile).toString(),
            displayName = "Unreadable Folder",
            presetType = BackupPresetType.CUSTOM.name,
            enabled = true
        )
        db.backupSourceDao().upsert(missingSource)
        db.backupSourceDao().upsert(unreadableSource)
        BackupSourceManager.persistAndAddSource(context, Uri.fromFile(healthyDir), BackupPresetType.CUSTOM, db = db, reschedule = false)

        val worker = TestListenableWorkerBuilder<BackupWorker>(context).build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        val updatedMissing = db.backupSourceDao().findById("missing-src")!!
        assertEquals(BackupSourcePermissionStatus.MISSING.name, updatedMissing.permissionStatus)
        assertTrue(!updatedMissing.lastError.isNullOrBlank())

        val updatedUnreadable = db.backupSourceDao().findById("unreadable-src")!!
        assertEquals(BackupSourcePermissionStatus.UNREADABLE.name, updatedUnreadable.permissionStatus)
        assertTrue(!updatedUnreadable.lastError.isNullOrBlank())

        val queuedNames = db.transferDao().observeAllTransfers().first().map { it.fileName }
        assertEquals(listOf("doc.pdf"), queuedNames)
    }

    @Test
    fun wifiOnlyAndChargingOnlyDefersBackupBeforeScanningOrQueueing() = runBlocking {
        val dir = tempFolder.newFolder("Deferred")
        File(dir, "photo.jpg").writeBytes("data".toByteArray())
        BackupSourceManager.persistAndAddSource(context, Uri.fromFile(dir), BackupPresetType.CUSTOM, db = db, reschedule = false)

        // Metered network while backupWifiOnly = true -> retry without queueing anything
        UserPreferences.setBackupWifiOnly(context, true)
        BackupWorkerHooks.networkSnapshotProvider = { _, prefs ->
            NetworkPolicySnapshot(
                status = NetworkStatus.VALIDATED_METERED,
                allowMeteredTransfers = true,
                allowRoaming = false,
                requireChargingForBackup = false,
                batterySaverActive = false,
                isCharging = false,
                batteryNotLow = true,
                requireWifiForBackup = prefs.backupWifiOnly
            )
        }
        val wifiBlocked = TestListenableWorkerBuilder<BackupWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.retry(), wifiBlocked)
        assertTrue(db.transferDao().observeAllTransfers().first().isEmpty())

        // Charging required while on Wi-Fi but unplugged -> retry without queueing
        UserPreferences.setBackupChargingOnly(context, true)
        BackupWorkerHooks.networkSnapshotProvider = { _, prefs ->
            NetworkPolicySnapshot(
                status = NetworkStatus.VALIDATED_UNMETERED,
                allowMeteredTransfers = prefs.allowMeteredTransfers,
                allowRoaming = prefs.allowRoaming,
                requireChargingForBackup = prefs.backupChargingOnly,
                batterySaverActive = false,
                isCharging = false,
                batteryNotLow = true,
                requireWifiForBackup = prefs.backupWifiOnly
            )
        }
        val chargingBlocked = TestListenableWorkerBuilder<BackupWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.retry(), chargingBlocked)
        assertTrue(db.transferDao().observeAllTransfers().first().isEmpty())
    }

    @Test
    fun autoSyncSchedulingAvoidsDuplicatesAndCancelsAllWhenDisabled() = runBlocking {
        UserPreferences.setAutoBackupEnabled(context, true)
        UserPreferences.setQuickMediaBackupEnabled(context, true)
        UserPreferences.setBackupIntervalHours(context, 4)
        UserPreferences.setBackupWifiOnly(context, true)
        UserPreferences.setBackupChargingOnly(context, true)

        AutoSyncManager.reschedule(context, triggerImmediateIfEnabled = false)
        AutoSyncManager.reschedule(context, triggerImmediateIfEnabled = false)

        val wm = WorkManager.getInstance(context)
        val periodic = wm.getWorkInfosForUniqueWork(AutoSyncManager.UNIQUE_PERIODIC_WORK).get()
        val quick = wm.getWorkInfosForUniqueWork(AutoSyncManager.UNIQUE_QUICK_MEDIA_WORK).get()
        assertEquals(1, periodic.size)
        assertEquals(1, quick.size)
        assertEquals(WorkInfo.State.ENQUEUED, periodic.first().state)
        assertEquals(WorkInfo.State.ENQUEUED, quick.first().state)

        AutoSyncManager.setAutoBackupSuspend(context, false)
        val periodicAfter = wm.getWorkInfosForUniqueWork(AutoSyncManager.UNIQUE_PERIODIC_WORK).get()
        val quickAfter = wm.getWorkInfosForUniqueWork(AutoSyncManager.UNIQUE_QUICK_MEDIA_WORK).get()
        assertTrue(periodicAfter.all { it.state == WorkInfo.State.CANCELLED })
        assertTrue(quickAfter.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun removingBackupSourceStopsFutureBackupsWithoutTouchingFiles() = runBlocking {
        val dir = tempFolder.newFolder("Removable")
        val file = File(dir, "kept.jpg").apply { writeBytes("ok".toByteArray()) }
        val added = BackupSourceManager.persistAndAddSource(context, Uri.fromFile(dir), BackupPresetType.CUSTOM, db = db, reschedule = false).getOrThrow()

        BackupSourceManager.removeSource(context, added.id, db, reschedule = false)
        assertTrue(db.backupSourceDao().getAll().isEmpty())
        assertTrue(file.exists())

        val worker = TestListenableWorkerBuilder<BackupWorker>(context)
            .setInputData(workDataOf("is_manual" to false))
            .build()
        val res = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), res)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

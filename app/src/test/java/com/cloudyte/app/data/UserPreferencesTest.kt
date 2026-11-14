package com.cloudyte.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        UserPreferences.resetAllSettings(context)
    }

    @Test
    fun testDefaultPreferences() = runBlocking {
        val state = UserPreferences.observe(context).first()
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertEquals(ViewMode.GRID, state.viewMode)
        assertEquals(2, state.gridColumns)
        assertEquals(6, state.backupIntervalHours)
        assertEquals(0, state.backupMaxFileSizeMb)
        assertEquals(Long.MAX_VALUE, state.backupMaxFileSizeBytes)
        assertTrue(state.backupWifiOnly)
        assertFalse(state.backupChargingOnly)
        assertFalse(state.quickMediaBackupEnabled)
        assertFalse(state.deleteAfterUpload)
        assertEquals("INTERNAL", state.downloadDirectory)
        assertEquals(3, state.transferRetryCount)
    }

    @Test
    fun testUpdatePreferencesAndIntervals() = runBlocking {
        UserPreferences.setThemeMode(context, ThemeMode.LIGHT)
        UserPreferences.setViewMode(context, ViewMode.LIST)
        UserPreferences.setGridColumns(context, 4)
        UserPreferences.setBackupWifiOnly(context, false)
        UserPreferences.setBackupChargingOnly(context, true)
        UserPreferences.setQuickMediaBackupEnabled(context, true)
        UserPreferences.setBackupIntervalHours(context, 12)
        UserPreferences.setBackupMaxFileSizeMb(context, 1024)
        UserPreferences.setDownloadDirectory(context, "external")
        UserPreferences.setTransferRetryCount(context, 0)

        val state = UserPreferences.observe(context).first()
        assertEquals(ThemeMode.LIGHT, state.themeMode)
        assertEquals(ViewMode.LIST, state.viewMode)
        assertEquals(4, state.gridColumns)
        assertFalse(state.backupWifiOnly)
        assertTrue(state.backupChargingOnly)
        assertTrue(state.quickMediaBackupEnabled)
        assertEquals(12, state.backupIntervalHours)
        assertEquals(1024, state.backupMaxFileSizeMb)
        assertEquals(1024L * 1024L * 1024L, state.backupMaxFileSizeBytes)
        assertEquals("EXTERNAL", state.downloadDirectory)
        assertEquals(0, state.transferRetryCount)
    }

    @Test
    fun testBackupIntervalSnappingAndMaxSizePresets() = runBlocking {
        assertEquals(listOf(1, 2, 4, 6, 12, 24, 48), UserPreferences.BACKUP_INTERVAL_OPTIONS_HOURS)
        UserPreferences.setBackupIntervalHours(context, 36)
        assertEquals(24, UserPreferences.observe(context).first().backupIntervalHours)

        UserPreferences.setBackupIntervalHours(context, 48)
        assertEquals(48, UserPreferences.observe(context).first().backupIntervalHours)

        assertEquals(BackupMaxFileSizeOption.UNLIMITED, BackupMaxFileSizeOption.fromMb(0))
        assertEquals(BackupMaxFileSizeOption.MB_100, BackupMaxFileSizeOption.fromMb(100))
        assertEquals(BackupMaxFileSizeOption.MB_500, BackupMaxFileSizeOption.fromMb(500))
        assertEquals(BackupMaxFileSizeOption.GB_1, BackupMaxFileSizeOption.fromMb(1024))
        assertEquals(BackupMaxFileSizeOption.GB_2, BackupMaxFileSizeOption.fromMb(2048))
        assertEquals(BackupMaxFileSizeOption.GB_4, BackupMaxFileSizeOption.fromMb(4096))
    }

    @Test
    fun testRecordBackupRunStatus() = runBlocking {
        UserPreferences.recordBackupRunStatus(
            context = context,
            timestamp = 123456L,
            summary = "Queued 3 upload(s) • 2 skipped",
            queuedCount = 3,
            skippedSummary = "1 unchanged, 1 excluded",
            failedCount = 0
        )
        val state = UserPreferences.observe(context).first()
        assertEquals(123456L, state.lastBackupAt)
        assertEquals("Queued 3 upload(s) • 2 skipped", state.lastBackupSummary)
        assertEquals(3, state.lastBackupQueuedCount)
        assertEquals("1 unchanged, 1 excluded", state.lastBackupSkippedSummary)
        assertEquals(0, state.lastBackupFailedCount)
    }

    @Test
    fun testResetAllSettings() = runBlocking {
        UserPreferences.setThemeMode(context, ThemeMode.LIGHT)
        UserPreferences.setBackupChargingOnly(context, true)
        UserPreferences.resetAllSettings(context)

        val state = UserPreferences.observe(context).first()
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertFalse(state.backupChargingOnly)
    }
}

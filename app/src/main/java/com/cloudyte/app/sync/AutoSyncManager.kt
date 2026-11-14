package com.cloudyte.app.sync

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.UserPreferencesState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object AutoSyncManager {
    private const val TAG = "AutoSyncManager"
    const val UNIQUE_PERIODIC_WORK = "AutoBackup"
    const val UNIQUE_QUICK_MEDIA_WORK = "QuickMediaBackup"
    const val UNIQUE_IMMEDIATE_WORK = "ImmediateAutoBackup"
    const val UNIQUE_MANUAL_WORK = "ManualSyncNow"

    private val fallbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var appScope: CoroutineScope = fallbackScope

    fun init(context: Context, scope: CoroutineScope) {
        appScope = scope
        SyncLogger.init(context)
        scope.launch(Dispatchers.IO) {
            runCatching {
                UserPreferences.migrateIfNeeded(context)
                BackupSourceManager.migrateLegacyBackupSourceIfNeeded(context)
                val prefs = UserPreferences.observe(context).first()
                if (prefs.autoBackupEnabled) {
                    reschedule(
                        context = context,
                        periodicPolicy = ExistingPeriodicWorkPolicy.KEEP,
                        triggerImmediateIfEnabled = false
                    )
                } else {
                    cancelScheduledWork(context)
                }
            }.onFailure { error ->
                Log.w(TAG, "AutoSyncManager init failed: ${error.message}")
            }
        }
    }

    fun requestSetAutoBackup(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        appScope.launch(Dispatchers.IO) {
            setAutoBackupSuspend(appContext, enabled)
        }
    }

    fun setAutoBackup(context: Context, enabled: Boolean) = requestSetAutoBackup(context, enabled)

    suspend fun setAutoBackupSuspend(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        UserPreferences.setAutoBackupEnabled(appContext, enabled)
        SyncPreferences.setAutoBackupEnabled(appContext, enabled)
        if (enabled) {
            reschedule(
                context = appContext,
                periodicPolicy = ExistingPeriodicWorkPolicy.UPDATE,
                triggerImmediateIfEnabled = true
            )
            SyncLogger.log("Auto-backup enabled: Scheduled periodic backup and queued initial check.", SyncLogLevel.INFO)
        } else {
            cancelScheduledWork(appContext)
            SyncLogger.log("Auto-backup disabled: Cancelled all scheduled background backup jobs.", SyncLogLevel.INFO)
        }
    }

    fun requestReschedule(
        context: Context,
        periodicPolicy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
        triggerImmediateIfEnabled: Boolean = false
    ) {
        val appContext = context.applicationContext
        appScope.launch(Dispatchers.IO) {
            reschedule(appContext, periodicPolicy, triggerImmediateIfEnabled)
        }
    }

    suspend fun reschedule(
        context: Context,
        periodicPolicy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
        triggerImmediateIfEnabled: Boolean = false
    ) {
        val appContext = context.applicationContext
        val prefs = UserPreferences.observe(appContext).first()
        val wm = WorkManager.getInstance(appContext)
        if (!prefs.autoBackupEnabled) {
            cancelScheduledWork(appContext)
            return
        }

        val constraints = buildConstraintsFromPreferences(prefs)
        val intervalHours = UserPreferences.BACKUP_INTERVAL_OPTIONS_HOURS
            .minByOrNull { kotlin.math.abs(it - prefs.backupIntervalHours) }
            ?.toLong()
            ?: 6L

        val periodicRequest = PeriodicWorkRequestBuilder<BackupWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(
                Data.Builder()
                    .putBoolean("is_manual", false)
                    .putBoolean("force_reupload", false)
                    .build()
            )
            .build()

        wm.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            periodicPolicy,
            periodicRequest
        )

        if (prefs.quickMediaBackupEnabled) {
            armQuickMediaTrigger(appContext, prefs)
        } else {
            wm.cancelUniqueWork(UNIQUE_QUICK_MEDIA_WORK)
        }

        if (triggerImmediateIfEnabled) {
            enqueueImmediateSync(appContext, prefs, forceReupload = false, isManual = false)
        }
    }

    suspend fun armQuickMediaTrigger(
        context: Context,
        prefsOverride: UserPreferencesState? = null
    ) {
        val appContext = context.applicationContext
        val prefs = prefsOverride ?: UserPreferences.observe(appContext).first()
        val wm = WorkManager.getInstance(appContext)
        if (!prefs.autoBackupEnabled || !prefs.quickMediaBackupEnabled) {
            wm.cancelUniqueWork(UNIQUE_QUICK_MEDIA_WORK)
            return
        }
        val baseNetworkType = if (prefs.backupWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val mediaConstraints = Constraints.Builder()
            .setRequiredNetworkType(baseNetworkType)
            .setRequiresCharging(prefs.backupChargingOnly)
            .setRequiresBatteryNotLow(true)
            .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            .addContentUriTrigger(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
            .setTriggerContentUpdateDelay(5, TimeUnit.SECONDS)
            .setTriggerContentMaxDelay(60, TimeUnit.SECONDS)
            .build()

        val mediaRequest = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(mediaConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(
                Data.Builder()
                    .putBoolean("is_manual", false)
                    .putBoolean("force_reupload", false)
                    .putBoolean("is_quick_media_trigger", true)
                    .build()
            )
            .build()

        wm.enqueueUniqueWork(
            UNIQUE_QUICK_MEDIA_WORK,
            ExistingWorkPolicy.REPLACE,
            mediaRequest
        )
    }

    fun buildConstraintsFromPreferences(prefs: UserPreferencesState): Constraints {
        val networkType = if (prefs.backupWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        return Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresCharging(prefs.backupChargingOnly)
            .setRequiresBatteryNotLow(true)
            .build()
    }

    suspend fun buildConstraintsFromPreferences(context: Context): Constraints {
        return buildConstraintsFromPreferences(UserPreferences.observe(context).first())
    }

    fun triggerImmediateSync(context: Context, forceReupload: Boolean = false, isManual: Boolean = false) {
        val appContext = context.applicationContext
        appScope.launch(Dispatchers.IO) {
            val prefs = UserPreferences.observe(appContext).first()
            enqueueImmediateSync(appContext, prefs, forceReupload = forceReupload, isManual = isManual)
        }
    }

    suspend fun enqueueImmediateSync(
        context: Context,
        prefsOverride: UserPreferencesState? = null,
        forceReupload: Boolean = false,
        isManual: Boolean = false
    ) {
        val appContext = context.applicationContext
        val prefs = prefsOverride ?: UserPreferences.observe(appContext).first()
        val constraints = if (isManual) {
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        } else {
            buildConstraintsFromPreferences(prefs)
        }

        val data = Data.Builder()
            .putBoolean("is_manual", isManual)
            .putBoolean("force_reupload", forceReupload)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        val workName = if (isManual) UNIQUE_MANUAL_WORK else UNIQUE_IMMEDIATE_WORK
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            workName,
            if (isManual) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            workRequest
        )
    }

    fun onFolderOrAccountChanged(context: Context) {
        val appContext = context.applicationContext
        appScope.launch(Dispatchers.IO) {
            val prefs = UserPreferences.observe(appContext).first()
            if (prefs.autoBackupEnabled) {
                SyncLogger.log("Sync configuration updated: Rescheduling automatic backup...", SyncLogLevel.INFO)
                reschedule(
                    context = appContext,
                    periodicPolicy = ExistingPeriodicWorkPolicy.UPDATE,
                    triggerImmediateIfEnabled = true
                )
            }
        }
    }

    fun cancelScheduledWork(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        wm.cancelUniqueWork(UNIQUE_PERIODIC_WORK)
        wm.cancelUniqueWork(UNIQUE_QUICK_MEDIA_WORK)
        wm.cancelUniqueWork(UNIQUE_IMMEDIATE_WORK)
    }
}

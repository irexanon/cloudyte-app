package com.cloudyte.app.sync

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cloudyte.app.core.transfer.notification.NotificationHelper
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.BackupSourceEntity
import com.cloudyte.app.db.BackupSourcePermissionStatus

enum class PermissionState {
    GRANTED,
    DENIED
}

data class AppPermissionSummary(
    val hasFullStorageAccess: Boolean = false,
    val notificationsState: PermissionState,
    val runtimePostNotificationsGranted: Boolean,
    val osNotificationsEnabled: Boolean,
    val ignoringBatteryOptimizations: Boolean = false,
    val folderSources: List<BackupSourceEntity>,
    val healthyFolderCount: Int,
    val brokenFolderCount: Int
)

object PermissionsHelper {

    fun isFullStorageAccessGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        } else {
            val readOk = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            val writeOk = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            readOk && writeOk
        }
    }

    fun isRuntimePostNotificationsGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isNotificationPermissionGranted(context: Context): Boolean {
        val runtimeOk = isRuntimePostNotificationsGranted(context)
        val managerOk = runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }
            .getOrDefault(true)
        return runtimeOk && managerOk
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val appContext = context.applicationContext
        val powerManager = (appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?: appContext.getSystemService(PowerManager::class.java)
            ?: return false
        return runCatching { powerManager.isIgnoringBatteryOptimizations(appContext.packageName) }
            .getOrDefault(false)
    }

    suspend fun inspectAppPermissions(
        context: Context,
        db: AppDatabase = AppDatabase.getDatabase(context)
    ): AppPermissionSummary {
        val fullStorageGranted = isFullStorageAccessGranted(context)
        val runtimeOk = isRuntimePostNotificationsGranted(context)
        val osEnabled = runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }
            .getOrDefault(true)
        val notifState = if (runtimeOk && osEnabled) PermissionState.GRANTED else PermissionState.DENIED
        val batteryUnrestricted = isIgnoringBatteryOptimizations(context)

        val sources = BackupSourceManager.refreshAllSourceHealth(context, db)
        val healthy = sources.count { it.permissionStatus == BackupSourcePermissionStatus.GRANTED.name }
        val broken = sources.count { it.permissionStatus != BackupSourcePermissionStatus.GRANTED.name }

        return AppPermissionSummary(
            hasFullStorageAccess = fullStorageGranted,
            notificationsState = notifState,
            runtimePostNotificationsGranted = runtimeOk,
            osNotificationsEnabled = osEnabled,
            ignoringBatteryOptimizations = batteryUnrestricted,
            folderSources = sources,
            healthyFolderCount = healthy,
            brokenFolderCount = broken
        )
    }

    fun buildAllFilesAccessSettingsIntent(context: Context, forActivityResult: Boolean = false): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appSpecificIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                if (!forActivityResult) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (appSpecificIntent.resolveActivity(context.packageManager) != null) {
                return appSpecificIntent
            }
            val generalIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                if (!forActivityResult) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (generalIntent.resolveActivity(context.packageManager) != null) {
                return generalIntent
            }
        }
        return buildAppDetailsSettingsIntent(context, forActivityResult = forActivityResult)
    }

    fun buildAppDetailsSettingsIntent(context: Context, forActivityResult: Boolean = false): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            if (!forActivityResult) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildNotificationSettingsIntent(context: Context): Intent {
        return NotificationHelper.buildAppNotificationSettingsIntent(context)
    }

    /**
     * Requests the direct system Doze whitelist dialog (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
     * when battery optimizations are still active, so Android updates
     * `PowerManager.isIgnoringBatteryOptimizations(packageName)` reliably across OEM skins
     * (ColorOS, OneUI, MIUI, Pixel). Falls back to the battery optimization list or app details.
     */
    fun buildRequestIgnoreBatteryOptimizationsIntent(
        context: Context,
        forActivityResult: Boolean = false
    ): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations(context)) {
            val directPrompt = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                if (!forActivityResult) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (directPrompt.resolveActivity(context.packageManager) != null) {
                return directPrompt
            }
        }
        return buildBatteryOptimizationSettingsIntent(context, forActivityResult = forActivityResult)
    }

    fun buildBatteryOptimizationSettingsIntent(
        context: Context,
        forActivityResult: Boolean = false
    ): Intent {
        val batteryIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            if (!forActivityResult) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (batteryIntent.resolveActivity(context.packageManager) != null) {
            batteryIntent
        } else {
            buildAppDetailsSettingsIntent(context, forActivityResult = forActivityResult)
        }
    }
}

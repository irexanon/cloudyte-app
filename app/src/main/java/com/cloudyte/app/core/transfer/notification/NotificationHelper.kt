package com.cloudyte.app.core.transfer.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cloudyte.app.R
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.UserPreferencesState
import kotlinx.coroutines.flow.first

enum class NotificationEventType {
    BACKUP_COMPLETED,
    BACKUP_FAILURE,
    TRANSFER_COMPLETED,
    TRANSFER_FAILURE
}

object NotificationHelper {
    const val CHANNEL_TRANSFERS = TransferNotification.CHANNEL_ID
    const val CHANNEL_ALERTS = "cloudyte_alerts"
    private const val BACKUP_EVENT_NOTIFICATION_ID = 2402

    fun hasSystemPermissionAndEnabled(context: Context): Boolean {
        val osEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!osEnabled) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun shouldPostEvent(prefs: UserPreferencesState, eventType: NotificationEventType): Boolean {
        if (!prefs.notificationsEnabled) return false
        return when (eventType) {
            NotificationEventType.BACKUP_COMPLETED -> prefs.backupNotifications && prefs.completedAlerts
            NotificationEventType.BACKUP_FAILURE -> prefs.backupNotifications && prefs.failureNotifications
            NotificationEventType.TRANSFER_COMPLETED -> prefs.completedAlerts
            NotificationEventType.TRANSFER_FAILURE -> prefs.failureNotifications
        }
    }

    suspend fun postEventIfEnabled(
        context: Context,
        eventType: NotificationEventType,
        title: String,
        message: String,
        notificationId: Int = BACKUP_EVENT_NOTIFICATION_ID
    ): Boolean {
        if (!hasSystemPermissionAndEnabled(context)) return false
        val prefs = runCatching { UserPreferences.observe(context).first() }.getOrNull() ?: return false
        if (!shouldPostEvent(prefs, eventType)) return false

        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(
                if (eventType == NotificationEventType.BACKUP_FAILURE || eventType == NotificationEventType.TRANSFER_FAILURE) {
                    NotificationCompat.PRIORITY_DEFAULT
                } else {
                    NotificationCompat.PRIORITY_LOW
                }
            )
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        }.getOrDefault(false)
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_TRANSFERS,
                    TransferNotification.CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Foreground progress for active uploads, downloads, and backups"
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALERTS,
                    "Backup & Transfer Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Completion and failure notifications for backups and file transfers"
                }
            )
        }
    }

    fun buildAppNotificationSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
    }

    fun buildChannelSettingsIntent(context: Context, channelId: String = CHANNEL_ALERTS): Intent {
        ensureChannels(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            }
        } else {
            buildAppNotificationSettingsIntent(context)
        }
    }
}

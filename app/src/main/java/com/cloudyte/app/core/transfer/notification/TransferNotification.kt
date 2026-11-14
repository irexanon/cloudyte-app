package com.cloudyte.app.core.transfer.notification

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.cloudyte.app.R

object TransferNotification {
    const val CHANNEL_ID = "cloudyte_transfers"
    const val CHANNEL_NAME = "Cloudyte transfers"
    const val NOTIFICATION_ID = 2001

    fun foregroundInfo(
        context: Context,
        transferred: Long,
        total: Long,
        currentFile: String? = null,
        showDetailedProgress: Boolean = true
    ): ForegroundInfo {
        NotificationHelper.ensureChannels(context)
        val title = if (showDetailedProgress && currentFile != null) {
            "Transferring $currentFile"
        } else {
            "Cloudyte transfers"
        }
        val text = if (!showDetailedProgress) {
            "Transfer queue active"
        } else if (total > 0L) {
            "${transferred.coerceAtLeast(0L)} of $total bytes"
        } else {
            "Working through transfer queue"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .apply {
                if (showDetailedProgress && total > 0L) {
                    val progress = ((transferred.coerceAtLeast(0L).toDouble() / total.toDouble()) * 1000).toInt().coerceIn(0, 1000)
                    setProgress(1000, progress, false)
                } else {
                    setProgress(0, 0, showDetailedProgress)
                }
            }
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(NOTIFICATION_ID, notification)
    }
}

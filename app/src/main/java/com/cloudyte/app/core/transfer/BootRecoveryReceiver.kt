package com.cloudyte.app.core.transfer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            WorkMaintenanceScheduler.scheduleBootRecovery(context)
            WorkMaintenanceScheduler.scheduleMaintenance(context)
            com.cloudyte.app.sync.AutoSyncManager.requestReschedule(context)
        }
    }
}

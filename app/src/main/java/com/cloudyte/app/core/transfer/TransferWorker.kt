package com.cloudyte.app.core.transfer

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.cloudyte.app.CloudyteApplication
import com.cloudyte.app.core.transfer.notification.TransferNotification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class TransferWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val application = applicationContext as? CloudyteApplication
        val queueDao = application?.container?.database?.transferQueueDao()
        val transferred = runCatching { queueDao?.aggregateBytesTransferred() ?: 0L }.getOrDefault(0L)
        val total = runCatching { queueDao?.aggregateTotalBytes() ?: 0L }.getOrDefault(0L)
        return TransferNotification.foregroundInfo(applicationContext, transferred, total)
    }

    override suspend fun doWork(): Result {
        val application = applicationContext as? CloudyteApplication
            ?: return Result.failure()
        if (inputData.getBoolean(KEY_QUEUE_DRAIN, false)) return drainQueue(application)
        val transferId = inputData.getString(KEY_TRANSFER_ID)
            ?: return Result.failure()
        val command = when (inputData.getString(KEY_COMMAND)) {
            COMMAND_START -> TransferCommand.Start(transferId)
            COMMAND_RETRY -> TransferCommand.Retry(transferId)
            COMMAND_PAUSE -> TransferCommand.Pause(transferId)
            COMMAND_RESUME -> TransferCommand.Resume(transferId)
            COMMAND_CANCEL -> TransferCommand.Cancel(transferId)
            else -> return Result.failure()
        }

        return try {
            application.container.transferCoordinator.dispatch(command)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            // Scheduling can fail transiently during process recreation or network recovery.
            // Let WorkManager apply its configured backoff instead of losing the command.
            Result.retry()
        }
    }

    private suspend fun drainQueue(application: CloudyteApplication): Result {
        val container = application.container
        if (!container.transferRolloutGate.isEnabled()) {
            val rollout = container.transferRolloutGate.current()
            container.transferDiagnostics.recordEvent(
                operationType = "rollout_gate",
                state = "DISABLED",
                errorCode = "ROLLOUT_DISABLED",
                networkState = rollout.reason,
                workerRunId = id.toString()
            )
            return Result.success()
        }
        val queueDao = container.database.transferQueueDao()
        val now = System.currentTimeMillis()
        queueDao.recoverExpiredLeases(now)
        queueDao.requeueNetworkWaiters(now)

        if (!queueDao.hasRunnableTransfers(now)) {
            scheduleFollowUpWork(container)
            return Result.success()
        }

        val prefs = com.cloudyte.app.data.UserPreferences.observe(applicationContext).first()
        val showDetailedProgress = prefs.notificationsEnabled && prefs.progressAlerts
        trySetForeground(
            TransferNotification.foregroundInfo(
                applicationContext,
                queueDao.aggregateBytesTransferred(),
                queueDao.aggregateTotalBytes(),
                showDetailedProgress = showDetailedProgress
            )
        )
        val trigger = runCatching { DrainTrigger.valueOf(inputData.getString(KEY_TRIGGER) ?: "APP_START") }
            .getOrDefault(DrainTrigger.APP_START)
        val drained = container.transferQueueDrainer.drain(trigger, { isStopped }) { snapshot ->
            setProgress(
                Data.Builder()
                    .putString("transfer_id", snapshot.transferId)
                    .putString("transfer_state", snapshot.state.name)
                    .putLong("bytes_transferred", snapshot.bytesTransferred)
                    .putLong("total_bytes", snapshot.totalBytes)
                    .putLong("bytes_per_second", snapshot.bytesPerSecond)
                    .putLong("eta_seconds", snapshot.etaSeconds ?: -1L)
                    .build()
            )
            trySetForeground(
                TransferNotification.foregroundInfo(
                    applicationContext,
                    queueDao.aggregateBytesTransferred(),
                    queueDao.aggregateTotalBytes(),
                    snapshot.transferId,
                    showDetailedProgress = showDetailedProgress
                )
            )
        }
        scheduleFollowUpWork(container)
        return when (drained) {
            is DrainResult.Drained -> if (drained.hasMore) Result.retry() else Result.success()
            DrainResult.Stopped -> Result.retry()
        }
    }

    private suspend fun scheduleFollowUpWork(container: com.cloudyte.app.AppContainer) {
        val queueDao = container.database.transferQueueDao()
        val currentNow = System.currentTimeMillis()
        val retryAt = queueDao.nextRetryAt(currentNow)
        if (retryAt != null) container.transferScheduler.scheduleRetryAt(retryAt)
        val leaseExpiry = queueDao.nextLeaseExpiry(currentNow)
        if (leaseExpiry != null) container.transferScheduler.scheduleLeaseRecoveryAt(leaseExpiry)
        if (queueDao.hasNetworkWaiters()) container.transferScheduler.scheduleNetworkRecovery()
    }

    private suspend fun trySetForeground(info: ForegroundInfo) {
        try {
            setForeground(info)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.w("TransferWorker", "Proceeding without foreground promotion: ${error.javaClass.simpleName}")
        }
    }

    companion object {
        const val KEY_QUEUE_DRAIN = "queue_drain"
        const val KEY_TRIGGER = "queue_trigger"
        const val KEY_TRANSFER_ID = "transfer_id"
        const val KEY_COMMAND = "transfer_command"
        const val COMMAND_START = "start"
        const val COMMAND_RETRY = "retry"
        const val COMMAND_PAUSE = "pause"
        const val COMMAND_RESUME = "resume"
        const val COMMAND_CANCEL = "cancel"
    }
}

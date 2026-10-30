package com.cloudyte.app.core.transfer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cloudyte.app.CloudyteApplication

class TransferWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val application = applicationContext as? CloudyteApplication
            ?: return Result.failure()
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
        } catch (error: Throwable) {
            // Scheduling can fail transiently during process recreation or network recovery.
            // Let WorkManager apply its configured backoff instead of losing the command.
            Result.retry()
        }
    }

    companion object {
        const val KEY_TRANSFER_ID = "transfer_id"
        const val KEY_COMMAND = "transfer_command"
        const val COMMAND_START = "start"
        const val COMMAND_RETRY = "retry"
        const val COMMAND_PAUSE = "pause"
        const val COMMAND_RESUME = "resume"
        const val COMMAND_CANCEL = "cancel"
    }
}

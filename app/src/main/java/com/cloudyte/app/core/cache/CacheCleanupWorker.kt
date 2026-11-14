package com.cloudyte.app.core.cache

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.db.AppDatabase
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class CacheCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            val prefs = UserPreferences.observe(applicationContext).first()
            val db = AppDatabase.getDatabase(applicationContext)

            if (prefs.autoCleanCache) {
                val totalQuotaBytes = prefs.maxCacheSizeMb.toLong().coerceAtLeast(32L) * 1024L * 1024L
                val thumbQuotaBytes = (totalQuotaBytes * 6L) / 10L
                val previewQuotaBytes = (totalQuotaBytes * 4L) / 10L
                ThumbnailStore(applicationContext, db.thumbnailDao()).enforceQuota(thumbQuotaBytes)
                PreviewStore(applicationContext).enforceQuota(previewQuotaBytes)
            }

            val activeIds = runCatching {
                db.transferDao().observeActiveTransfers().first().map { it.id }.toSet()
            }.getOrDefault(emptySet())
            com.cloudyte.app.core.security.SecureFileCleanup.cleanupStaleTempFiles(applicationContext, activeIds)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "cloudyte_cache_cleanup"

        fun syncSchedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (!enabled) {
                runCatching { wm.cancelUniqueWork(UNIQUE_WORK_NAME) }
                return
            }
            schedule(context)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CacheCleanupWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresCharging(true)
                        .build()
                )
                .build()
            runCatching {
                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }
        }
    }
}

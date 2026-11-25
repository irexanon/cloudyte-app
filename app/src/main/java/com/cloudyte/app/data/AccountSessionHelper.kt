package com.cloudyte.app.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.WorkManager
import coil.imageLoader
import com.cloudyte.app.CloudyteApplication
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.FileRepository
import com.cloudyte.app.api.OfflineFileManager
import com.cloudyte.app.core.cache.PreviewStore
import com.cloudyte.app.core.security.SecureFileCleanup
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.features.player.MediaStreamCache
import com.cloudyte.app.sync.AutoSyncManager
import com.cloudyte.app.sync.SyncLogger
import com.cloudyte.app.sync.SyncLogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class LogoutResult(
    val remoteRevoked: Boolean,
    val warningMessage: String? = null
)

object AccountSessionHelper {
    suspend fun performLogout(
        context: Context,
        remoteLogoutCall: suspend () -> Boolean = {
            val response = withTimeoutOrNull(4_000L) {
                ApiClient.apiService.logout()
            }
            response?.isSuccessful == true
        }
    ): LogoutResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val remoteOk = try {
            remoteLogoutCall()
        } catch (_: Exception) {
            false
        }

        // Always complete local sign-out cleanly even if remote revocation fails/offline.
        ApiClient.saveToken(null)
        FileRepository.clear()
        runCatching { AutoSyncManager.cancelScheduledWork(appContext) }
        runCatching { WorkManager.getInstance(appContext).cancelAllWork() }

        val app = appContext as? CloudyteApplication
        runCatching { app?.container?.contentKeyManager?.clearInMemoryCache() }

        val db = AppDatabase.getDatabase(appContext)
        runCatching {
            db.withTransaction {
                db.cloudFileDao().clearAll()
                db.accountDao().clearAccounts()
                db.storageSummaryDao().clearAll()
                db.folderSyncMetaDao().clearAll()
                db.transferDao().clearAll()
                db.fileMutationDao().clearAll()
                db.fileTombstoneDao().clearAll()
                db.backupSourceDao().clearAll()
                db.exclusionDao().clearAll()
                db.uploadRecordDao().clearHistory()
                db.thumbnailDao().deleteAll()
            }
        }

        runCatching { OfflineFileManager.clearAllOfflineCache(appContext) }
        runCatching { (app?.container?.previewStore ?: PreviewStore(appContext)).clear() }
        runCatching { MediaStreamCache.clear(appContext) }
        runCatching { SecureFileCleanup.cleanupStaleTempFiles(appContext, emptySet(), purgeAll = true) }
        runCatching { SecureFileCleanup.clearShareAndTempDirs(appContext) }
        runCatching { File(appContext.filesDir, "cloudyte_thumbnails").deleteRecursively() }
        runCatching { ApiClient.okHttpClient.cache?.evictAll() }
        runCatching {
            appContext.imageLoader.memoryCache?.clear()
            appContext.imageLoader.diskCache?.clear()
        }

        runCatching { UserPreferences.setLastActiveAccountId(appContext, null) }
        runCatching { UserPreferences.setHomeStorageAccountId(appContext, null) }
        runCatching {
            appContext.getSharedPreferences("cloudyte_drive_state", Context.MODE_PRIVATE).edit().clear().apply()
        }
        runCatching {
            appContext.getSharedPreferences("cloudyte_audio_playback", Context.MODE_PRIVATE).edit().clear().apply()
        }
        runCatching { DriveAliasStore.clearAll(appContext) }
        runCatching { SyncLogger.clearLogs() }
        runCatching { com.cloudyte.app.provider.CloudyteDocumentsProvider.notifyRootsChanged(appContext) }

        val warning = if (remoteOk) {
            null
        } else {
            "Signed out on this device, but the server session could not be revoked (offline or server error)."
        }
        SyncLogger.log(
            warning ?: "Signed out locally and revoked remote session.",
            if (remoteOk) SyncLogLevel.INFO else SyncLogLevel.WARNING
        )
        LogoutResult(remoteRevoked = remoteOk, warningMessage = warning)
    }

    suspend fun purgeDisconnectedAccount(
        context: Context,
        accountId: String
    ) = withContext(Dispatchers.IO) {
        if (accountId.isBlank()) return@withContext
        val appContext = context.applicationContext
        val app = appContext as? CloudyteApplication
        val db = AppDatabase.getDatabase(appContext)

        // 1. Signal cancellation and clean up temporary staging/download files for any transfers on this account
        val accountTransfers = runCatching { db.transferDao().getByAccountId(accountId) }.getOrDefault(emptyList())
        for (transfer in accountTransfers) {
            com.cloudyte.app.core.transfer.ActiveTransferSignalRegistry.signalCancel(transfer.id)
            runCatching {
                SecureFileCleanup.cleanupTransferStaging(
                    context = appContext,
                    transferId = transfer.id,
                    customStagePath = transfer.encryptedStagePath
                )
            }
            runCatching {
                SecureFileCleanup.cleanupDownloadTemp(
                    context = appContext,
                    transferId = transfer.id,
                    removePartFile = true
                )
            }
        }

        // 2. Delete cached thumbnail files on disk for this account
        val accountThumbs = runCatching { db.thumbnailDao().getForAccount(accountId) }.getOrDefault(emptyList())
        for (thumb in accountThumbs) {
            if (thumb.path.isNotBlank()) {
                runCatching { File(thumb.path).delete() }
            }
        }

        // 3. Atomically purge all account-scoped Room rows
        runCatching {
            db.withTransaction {
                db.accountDao().deleteAccountById(accountId)
                db.cloudFileDao().deleteFilesForAccount(accountId)
                db.storageSummaryDao().deleteByAccountId(accountId)
                db.storageSummaryDao().deleteByAccountId("all")
                db.folderSyncMetaDao().deleteForAccount(accountId)
                db.transferDao().deleteByAccountId(accountId)
                db.fileMutationDao().deleteForAccount(accountId)
                db.fileTombstoneDao().deleteForAccount(accountId)
                db.uploadRecordDao().deleteForAccount(accountId)
                db.thumbnailDao().deleteForAccount(accountId)
                db.backupSourceDao().clearTargetAccount(accountId)
            }
        }

        // 4. Clean up offline vault files, decrypted view cache, and in-memory caches for this account
        runCatching { OfflineFileManager.deleteOfflineFilesForAccount(appContext, accountId) }
        runCatching { OfflineFileManager.refreshDownloadedSetAsync(appContext) }
        runCatching { app?.container?.cloudFileRepository?.evictAccountMemoryCache(accountId) }

        // 5. Reset selected account preferences if they pointed to the disconnected account
        runCatching {
            val prefs = UserPreferences.observe(appContext).first()
            if (prefs.lastActiveAccountId == accountId) {
                UserPreferences.setLastActiveAccountId(appContext, null)
            }
            if (prefs.homeStorageAccountId == accountId) {
                UserPreferences.setHomeStorageAccountId(appContext, null)
            }
            if (prefs.backupAccountId == accountId) {
                UserPreferences.setBackupAccountId(appContext, null)
            }
        }
        runCatching {
            val drivePrefs = appContext.getSharedPreferences("cloudyte_drive_state", Context.MODE_PRIVATE)
            if (drivePrefs.getString("last_active_account_id", null) == accountId) {
                drivePrefs.edit().remove("last_active_account_id").apply()
            }
        }
        runCatching { DriveAliasStore.removeAlias(appContext, accountId) }
        runCatching { com.cloudyte.app.provider.CloudyteDocumentsProvider.notifyRootsChanged(appContext) }
    }
}

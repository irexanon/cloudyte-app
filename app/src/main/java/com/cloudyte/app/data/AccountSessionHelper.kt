package com.cloudyte.app.data

import android.content.Context
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.FileRepository
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.sync.AutoSyncManager
import com.cloudyte.app.sync.SyncLogger
import com.cloudyte.app.sync.SyncLogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
        val remoteOk = try {
            remoteLogoutCall()
        } catch (_: Exception) {
            false
        }

        // Always complete local sign-out cleanly even if remote revocation fails/offline.
        ApiClient.saveToken(null)
        FileRepository.clear()
        runCatching { AutoSyncManager.cancelScheduledWork(context) }

        val db = AppDatabase.getDatabase(context)
        runCatching { db.cloudFileDao().clearAll() }
        runCatching { db.accountDao().clearAccounts() }
        runCatching { db.storageSummaryDao().clearAll() }
        runCatching { db.folderSyncMetaDao().clearAll() }

        runCatching { UserPreferences.setLastActiveAccountId(context, null) }
        runCatching { UserPreferences.setHomeStorageAccountId(context, null) }

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
}

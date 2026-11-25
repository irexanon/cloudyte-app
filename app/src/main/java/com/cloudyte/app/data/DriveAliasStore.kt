package com.cloudyte.app.data

import android.content.Context
import com.cloudyte.app.api.Account

/**
 * Persists custom local drive rename aliases so renamed drives never revert on refresh or
 * restart when the backend does not expose a drive rename endpoint.
 */
object DriveAliasStore {
    private const val PREFS_NAME = "cloudyte_drive_aliases"

    fun setAlias(context: Context, accountId: String, customName: String) {
        if (accountId.isBlank() || customName.isBlank()) return
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(accountId, customName.trim())
            .apply()
    }

    fun removeAlias(context: Context, accountId: String) {
        if (accountId.isBlank()) return
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(accountId)
            .apply()
    }

    fun clearAll(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun applyAliases(context: Context, accounts: List<Account>): List<Account> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val all = prefs.all
        if (all.isEmpty()) return accounts
        return accounts.map { acc ->
            val alias = (all[acc.effectiveId] as? String)?.takeIf { it.isNotBlank() }
            if (alias != null) acc.copy(name = alias, driveName = alias) else acc
        }
    }
}

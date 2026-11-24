package com.cloudyte.app.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.cloudyte.app.data.ColorTheme

/**
 * Switches between the standard Cloudyte launcher icon and the monochromatic Noir launcher icon
 * using manifest `<activity-alias>` entries.
 */
object AppIconManager {

    private const val TAG = "AppIconManager"
    private const val DEFAULT_ALIAS_CLASS = "com.cloudyte.app.MainActivityDefault"
    private const val NOIR_ALIAS_CLASS = "com.cloudyte.app.MainActivityNoir"

    /**
     * Synchronizes the enabled launcher alias with [colorTheme].
     *
     * @return `true` if the launcher icon state was updated, `false` if it was already in sync.
     */
    fun syncWithColorTheme(context: Context, colorTheme: ColorTheme): Boolean {
        val appContext = context.applicationContext
        val pm = appContext.packageManager
        val defaultComponent = ComponentName(appContext, DEFAULT_ALIAS_CLASS)
        val noirComponent = ComponentName(appContext, NOIR_ALIAS_CLASS)

        val wantNoir = colorTheme == ColorTheme.NOIR

        val defaultState = runCatching { pm.getComponentEnabledSetting(defaultComponent) }
            .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        val noirState = runCatching { pm.getComponentEnabledSetting(noirComponent) }
            .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

        // Manifest default: MainActivityDefault is enabled=true, MainActivityNoir is enabled=false.
        val isDefaultActive = defaultState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            defaultState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        val isNoirActive = noirState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

        if (wantNoir && isNoirActive && !isDefaultActive) return false
        if (!wantNoir && isDefaultActive && !isNoirActive) return false

        return runCatching {
            val enableTarget = if (wantNoir) noirComponent else defaultComponent
            val disableTarget = if (wantNoir) defaultComponent else noirComponent

            // Enable the target alias first so the app never briefly vanishes from launchers.
            pm.setComponentEnabledSetting(
                enableTarget,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                disableTarget,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            true
        }.onFailure { err ->
            Log.w(TAG, "Failed to update launcher icon for $colorTheme: ${err.message}")
        }.getOrDefault(false)
    }
}

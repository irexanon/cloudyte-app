package com.cloudyte.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cloudyte.app.sync.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/** Colour palette used when Dynamic Color is off. DEFAULT is the original blue palette. */
enum class ColorTheme {
    DEFAULT,
    NOIR
}

enum class ViewMode {
    GRID,
    LIST,
    COMPACT_LIST
}

enum class SortField {
    DATE,
    NAME,
    SIZE
}

enum class SortDirection {
    ASC,
    DESC
}

data class UserPreferencesState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val colorTheme: ColorTheme = ColorTheme.DEFAULT,
    val viewMode: ViewMode = ViewMode.GRID,
    val gridColumns: Int = 2,
    val layoutDensity: String = "COMFORTABLE",
    val showRecentFiles: Boolean = false,
    val sortField: SortField = SortField.DATE,
    val sortDirection: SortDirection = SortDirection.DESC,
    val showHidden: Boolean = false,
    val autoBackupEnabled: Boolean = false,
    val backupUri: String? = null,
    val backupAccountId: String? = null,
    val backupWifiOnly: Boolean = true,
    val backupChargingOnly: Boolean = false,
    val backupIntervalHours: Int = 6,
    val backupMaxFileSizeMb: Int = 50,
    val deleteAfterUpload: Boolean = false,
    val maxCacheSizeMb: Int = 256,
    val downloadDirectory: String = "INTERNAL",
    val autoCleanCache: Boolean = false,
    val appLockEnabled: Boolean = false,
    val autoLockMinutes: Int = 5,
    val blockScreenshots: Boolean = false,
    val streamBeforeDownload: Boolean = true,
    val backgroundPlayback: Boolean = true,
    val autoPlayVideo: Boolean = false,
    val audioCrossfadeSeconds: Int = 2,
    val notificationsEnabled: Boolean = true,
    val backupNotifications: Boolean = true,
    val failureNotifications: Boolean = true,
    val progressAlerts: Boolean = true,
    val completedAlerts: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val transferConcurrency: Int = 3,
    val transferRetryCount: Int = 3,
    val allowMeteredTransfers: Boolean = false,
    val allowRoaming: Boolean = false,
    val debugLogging: Boolean = false,
    val updateCheckEnabled: Boolean = true,
    val lastUpdateCheckAt: Long = 0L,
    val language: String = "SYSTEM",
    val lastActiveAccountId: String? = null,
    val homeStorageAccountId: String? = null,
    val showNavText: Boolean = true
)

object UserPreferences {
    private val KEY_MIGRATED = booleanPreferencesKey("migrated_v1")
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    private val KEY_COLOR_THEME = stringPreferencesKey("color_theme")
    private val KEY_VIEW_MODE = stringPreferencesKey("view_mode")
    private val KEY_GRID_COLUMNS = intPreferencesKey("grid_columns")
    private val KEY_LAYOUT_DENSITY = stringPreferencesKey("layout_density")
    private val KEY_SHOW_RECENT_FILES = booleanPreferencesKey("show_recent_files")
    private val KEY_SORT_FIELD = stringPreferencesKey("sort_field")
    private val KEY_SORT_DIRECTION = stringPreferencesKey("sort_direction")
    private val KEY_SHOW_HIDDEN = booleanPreferencesKey("show_hidden")
    private val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
    private val KEY_BACKUP_URI = stringPreferencesKey("backup_uri")
    private val KEY_BACKUP_ACCOUNT_ID = stringPreferencesKey("backup_account_id")
    private val KEY_BACKUP_WIFI_ONLY = booleanPreferencesKey("backup_wifi_only")
    private val KEY_BACKUP_CHARGING_ONLY = booleanPreferencesKey("backup_charging_only")
    private val KEY_BACKUP_INTERVAL_HOURS = intPreferencesKey("backup_interval_hours")
    private val KEY_BACKUP_MAX_FILE_SIZE_MB = intPreferencesKey("backup_max_file_size_mb")
    private val KEY_DELETE_AFTER_UPLOAD = booleanPreferencesKey("delete_after_upload")
    private val KEY_MAX_CACHE_SIZE_MB = intPreferencesKey("max_cache_size_mb")
    private val KEY_DOWNLOAD_DIRECTORY = stringPreferencesKey("download_directory")
    private val KEY_AUTO_CLEAN_CACHE = booleanPreferencesKey("auto_clean_cache")
    private val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    private val KEY_AUTO_LOCK_MINUTES = intPreferencesKey("auto_lock_minutes")
    private val KEY_BLOCK_SCREENSHOTS = booleanPreferencesKey("block_screenshots")
    private val KEY_STREAM_BEFORE_DOWNLOAD = booleanPreferencesKey("stream_before_download")
    private val KEY_BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
    private val KEY_AUTO_PLAY_VIDEO = booleanPreferencesKey("auto_play_video")
    private val KEY_AUDIO_CROSSFADE_SECONDS = intPreferencesKey("audio_crossfade_seconds")
    private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    private val KEY_BACKUP_NOTIFICATIONS = booleanPreferencesKey("backup_notifications")
    private val KEY_FAILURE_NOTIFICATIONS = booleanPreferencesKey("failure_notifications")
    private val KEY_PROGRESS_ALERTS = booleanPreferencesKey("progress_alerts")
    private val KEY_COMPLETED_ALERTS = booleanPreferencesKey("completed_alerts")
    private val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    private val KEY_VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
    private val KEY_TRANSFER_CONCURRENCY = intPreferencesKey("transfer_concurrency")
    private val KEY_TRANSFER_RETRY_COUNT = intPreferencesKey("transfer_retry_count")
    private val KEY_ALLOW_METERED_TRANSFERS = booleanPreferencesKey("allow_metered_transfers")
    private val KEY_ALLOW_ROAMING = booleanPreferencesKey("allow_roaming")
    private val KEY_DEBUG_LOGGING = booleanPreferencesKey("debug_logging")
    private val KEY_UPDATE_CHECK_ENABLED = booleanPreferencesKey("update_check_enabled")
    private val KEY_LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
    private val KEY_LANGUAGE = stringPreferencesKey("language")
    private val KEY_LAST_ACTIVE_ACCOUNT_ID = stringPreferencesKey("last_active_account_id")
    private val KEY_HOME_STORAGE_ACCOUNT_ID = stringPreferencesKey("home_storage_account_id")
    private val KEY_SHOW_NAV_TEXT = booleanPreferencesKey("show_nav_text")

    // Legacy sync_prefs keys
    private val LEGACY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
    private val LEGACY_BACKUP_URI = stringPreferencesKey("backup_uri")
    private val LEGACY_BACKUP_ACCOUNT_ID = stringPreferencesKey("backup_account_id")

    suspend fun migrateIfNeeded(context: Context) {
        val currentPrefs = context.userPreferencesDataStore.data.first()
        if (currentPrefs[KEY_MIGRATED] == true) return

        val uiSharedPrefs = context.getSharedPreferences("cloudyte_ui_prefs", Context.MODE_PRIVATE)
        val driveSharedPrefs = context.getSharedPreferences("cloudyte_drive_state", Context.MODE_PRIVATE)
        val legacySyncPrefs = try {
            context.dataStore.data.first()
        } catch (_: Exception) {
            null
        }

        context.userPreferencesDataStore.edit { prefs ->
            if (prefs[KEY_MIGRATED] == true) return@edit

            if (uiSharedPrefs.contains("is_grid_view")) {
                val isGrid = uiSharedPrefs.getBoolean("is_grid_view", true)
                prefs[KEY_VIEW_MODE] = if (isGrid) ViewMode.GRID.name else ViewMode.LIST.name
            }

            val lastAccId = driveSharedPrefs.getString("last_active_account_id", null)
            if (!lastAccId.isNullOrBlank()) {
                prefs[KEY_LAST_ACTIVE_ACCOUNT_ID] = lastAccId
            }

            if (legacySyncPrefs != null) {
                legacySyncPrefs[LEGACY_AUTO_BACKUP_ENABLED]?.let {
                    prefs[KEY_AUTO_BACKUP_ENABLED] = it
                }
                legacySyncPrefs[LEGACY_BACKUP_URI]?.let {
                    prefs[KEY_BACKUP_URI] = it
                }
                legacySyncPrefs[LEGACY_BACKUP_ACCOUNT_ID]?.let {
                    prefs[KEY_BACKUP_ACCOUNT_ID] = it
                }
            }

            prefs[KEY_MIGRATED] = true
        }
    }

    fun observe(context: Context): Flow<UserPreferencesState> {
        return context.userPreferencesDataStore.data.map { prefs ->
            UserPreferencesState(
                themeMode = prefs[KEY_THEME_MODE]?.let {
                    runCatching { ThemeMode.valueOf(it) }.getOrNull()
                } ?: ThemeMode.DARK,
                dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
                colorTheme = prefs[KEY_COLOR_THEME]?.let {
                    runCatching { ColorTheme.valueOf(it) }.getOrNull()
                } ?: ColorTheme.DEFAULT,
                viewMode = prefs[KEY_VIEW_MODE]?.let {
                    runCatching { ViewMode.valueOf(it) }.getOrNull()
                } ?: ViewMode.GRID,
                gridColumns = (prefs[KEY_GRID_COLUMNS] ?: 2).coerceIn(2, 5),
                layoutDensity = prefs[KEY_LAYOUT_DENSITY] ?: "COMFORTABLE",
                showRecentFiles = prefs[KEY_SHOW_RECENT_FILES] ?: false,
                sortField = prefs[KEY_SORT_FIELD]?.let {
                    runCatching { SortField.valueOf(it) }.getOrNull()
                } ?: SortField.DATE,
                sortDirection = prefs[KEY_SORT_DIRECTION]?.let {
                    runCatching { SortDirection.valueOf(it) }.getOrNull()
                } ?: SortDirection.DESC,
                showHidden = prefs[KEY_SHOW_HIDDEN] ?: false,
                autoBackupEnabled = prefs[KEY_AUTO_BACKUP_ENABLED] ?: false,
                backupUri = prefs[KEY_BACKUP_URI],
                backupAccountId = prefs[KEY_BACKUP_ACCOUNT_ID],
                backupWifiOnly = prefs[KEY_BACKUP_WIFI_ONLY] ?: true,
                backupChargingOnly = prefs[KEY_BACKUP_CHARGING_ONLY] ?: false,
                backupIntervalHours = (prefs[KEY_BACKUP_INTERVAL_HOURS] ?: 6).coerceAtLeast(1),
                backupMaxFileSizeMb = (prefs[KEY_BACKUP_MAX_FILE_SIZE_MB] ?: 50).coerceIn(1, 100),
                deleteAfterUpload = prefs[KEY_DELETE_AFTER_UPLOAD] ?: false,
                maxCacheSizeMb = (prefs[KEY_MAX_CACHE_SIZE_MB] ?: 256).coerceAtLeast(32),
                downloadDirectory = prefs[KEY_DOWNLOAD_DIRECTORY] ?: "INTERNAL",
                autoCleanCache = prefs[KEY_AUTO_CLEAN_CACHE] ?: false,
                appLockEnabled = prefs[KEY_APP_LOCK_ENABLED] ?: false,
                autoLockMinutes = prefs[KEY_AUTO_LOCK_MINUTES] ?: 5,
                blockScreenshots = prefs[KEY_BLOCK_SCREENSHOTS] ?: false,
                streamBeforeDownload = prefs[KEY_STREAM_BEFORE_DOWNLOAD] ?: true,
                backgroundPlayback = prefs[KEY_BACKGROUND_PLAYBACK] ?: true,
                autoPlayVideo = prefs[KEY_AUTO_PLAY_VIDEO] ?: false,
                audioCrossfadeSeconds = (prefs[KEY_AUDIO_CROSSFADE_SECONDS] ?: 2).coerceIn(0, 10),
                notificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: true,
                backupNotifications = prefs[KEY_BACKUP_NOTIFICATIONS] ?: true,
                failureNotifications = prefs[KEY_FAILURE_NOTIFICATIONS] ?: true,
                progressAlerts = prefs[KEY_PROGRESS_ALERTS] ?: true,
                completedAlerts = prefs[KEY_COMPLETED_ALERTS] ?: true,
                soundEnabled = prefs[KEY_SOUND_ENABLED] ?: true,
                vibrationEnabled = prefs[KEY_VIBRATION_ENABLED] ?: true,
                transferConcurrency = (prefs[KEY_TRANSFER_CONCURRENCY] ?: 3).coerceIn(1, 5),
                transferRetryCount = (prefs[KEY_TRANSFER_RETRY_COUNT] ?: 3).coerceIn(1, 10),
                allowMeteredTransfers = prefs[KEY_ALLOW_METERED_TRANSFERS] ?: false,
                allowRoaming = prefs[KEY_ALLOW_ROAMING] ?: false,
                debugLogging = prefs[KEY_DEBUG_LOGGING] ?: false,
                updateCheckEnabled = prefs[KEY_UPDATE_CHECK_ENABLED] ?: true,
                lastUpdateCheckAt = prefs[KEY_LAST_UPDATE_CHECK_AT] ?: 0L,
                language = prefs[KEY_LANGUAGE] ?: "SYSTEM",
                lastActiveAccountId = prefs[KEY_LAST_ACTIVE_ACCOUNT_ID],
                homeStorageAccountId = prefs[KEY_HOME_STORAGE_ACCOUNT_ID],
                showNavText = prefs[KEY_SHOW_NAV_TEXT] ?: true
            )
        }
    }

    suspend fun setShowNavText(context: Context, show: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_SHOW_NAV_TEXT] = show }
    }

    suspend fun setThemeMode(context: Context, mode: ThemeMode) {
        context.userPreferencesDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setColorTheme(context: Context, theme: ColorTheme) {
        context.userPreferencesDataStore.edit { it[KEY_COLOR_THEME] = theme.name }
    }

    suspend fun setDynamicColor(context: Context, enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setViewMode(context: Context, mode: ViewMode) {
        context.userPreferencesDataStore.edit { it[KEY_VIEW_MODE] = mode.name }
        context.getSharedPreferences("cloudyte_ui_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("is_grid_view", mode == ViewMode.GRID).apply()
    }

    suspend fun setGridColumns(context: Context, columns: Int) {
        context.userPreferencesDataStore.edit { it[KEY_GRID_COLUMNS] = columns.coerceIn(2, 5) }
    }

    suspend fun setLayoutDensity(context: Context, density: String) {
        context.userPreferencesDataStore.edit { it[KEY_LAYOUT_DENSITY] = density }
    }

    suspend fun setShowRecentFiles(context: Context, show: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_SHOW_RECENT_FILES] = show }
    }

    suspend fun setSortField(context: Context, field: SortField) {
        context.userPreferencesDataStore.edit { it[KEY_SORT_FIELD] = field.name }
    }

    suspend fun setSortDirection(context: Context, direction: SortDirection) {
        context.userPreferencesDataStore.edit { it[KEY_SORT_DIRECTION] = direction.name }
    }

    suspend fun setShowHidden(context: Context, show: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_SHOW_HIDDEN] = show }
    }

    suspend fun setAutoBackupEnabled(context: Context, enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_AUTO_BACKUP_ENABLED] = enabled }
    }

    suspend fun setBackupUri(context: Context, uri: String?) {
        context.userPreferencesDataStore.edit {
            if (uri == null) it.remove(KEY_BACKUP_URI)
            else it[KEY_BACKUP_URI] = uri
        }
    }

    suspend fun setBackupAccountId(context: Context, accountId: String?) {
        context.userPreferencesDataStore.edit {
            if (accountId == null) it.remove(KEY_BACKUP_ACCOUNT_ID)
            else it[KEY_BACKUP_ACCOUNT_ID] = accountId
        }
    }

    suspend fun setBackupWifiOnly(context: Context, wifiOnly: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_BACKUP_WIFI_ONLY] = wifiOnly }
    }

    suspend fun setBackupChargingOnly(context: Context, chargingOnly: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_BACKUP_CHARGING_ONLY] = chargingOnly }
    }

    suspend fun setBackupIntervalHours(context: Context, hours: Int) {
        context.userPreferencesDataStore.edit { it[KEY_BACKUP_INTERVAL_HOURS] = hours.coerceAtLeast(1) }
    }

    suspend fun setBackupMaxFileSizeMb(context: Context, sizeMb: Int) {
        context.userPreferencesDataStore.edit { it[KEY_BACKUP_MAX_FILE_SIZE_MB] = sizeMb.coerceIn(1, 100) }
    }

    suspend fun setDeleteAfterUpload(context: Context, delete: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_DELETE_AFTER_UPLOAD] = delete }
    }

    suspend fun setMaxCacheSizeMb(context: Context, sizeMb: Int) {
        context.userPreferencesDataStore.edit { it[KEY_MAX_CACHE_SIZE_MB] = sizeMb.coerceAtLeast(32) }
    }

    suspend fun setDownloadDirectory(context: Context, dir: String) {
        context.userPreferencesDataStore.edit { it[KEY_DOWNLOAD_DIRECTORY] = dir }
    }

    suspend fun setAutoCleanCache(context: Context, enable: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_AUTO_CLEAN_CACHE] = enable }
    }

    suspend fun setAppLockEnabled(context: Context, enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setAutoLockMinutes(context: Context, minutes: Int) {
        context.userPreferencesDataStore.edit { it[KEY_AUTO_LOCK_MINUTES] = minutes }
    }

    suspend fun setBlockScreenshots(context: Context, block: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_BLOCK_SCREENSHOTS] = block }
    }

    suspend fun setStreamBeforeDownload(context: Context, stream: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_STREAM_BEFORE_DOWNLOAD] = stream }
    }

    suspend fun setBackgroundPlayback(context: Context, enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_BACKGROUND_PLAYBACK] = enabled }
    }

    suspend fun setAutoPlayVideo(context: Context, enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_AUTO_PLAY_VIDEO] = enabled }
    }

    suspend fun setAudioCrossfadeSeconds(context: Context, seconds: Int) {
        context.userPreferencesDataStore.edit { it[KEY_AUDIO_CROSSFADE_SECONDS] = seconds.coerceIn(0, 10) }
    }

    suspend fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setBackupNotifications(context: Context, enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_BACKUP_NOTIFICATIONS] = enabled }
    }

    suspend fun setFailureNotifications(context: Context, enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_FAILURE_NOTIFICATIONS] = enabled }
    }

    suspend fun setProgressAlerts(context: Context, enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_PROGRESS_ALERTS] = enabled }
    }

    suspend fun setCompletedAlerts(context: Context, enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_COMPLETED_ALERTS] = enabled }
    }

    suspend fun setSoundEnabled(context: Context, enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_SOUND_ENABLED] = enabled }
    }

    suspend fun setVibrationEnabled(context: Context, enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_VIBRATION_ENABLED] = enabled }
    }

    suspend fun setTransferConcurrency(context: Context, count: Int) {
        context.userPreferencesDataStore.edit { it[KEY_TRANSFER_CONCURRENCY] = count.coerceIn(1, 5) }
    }

    suspend fun setTransferRetryCount(context: Context, count: Int) {
        context.userPreferencesDataStore.edit { it[KEY_TRANSFER_RETRY_COUNT] = count.coerceIn(1, 10) }
    }

    suspend fun setAllowMeteredTransfers(context: Context, allow: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_ALLOW_METERED_TRANSFERS] = allow }
    }

    suspend fun setAllowRoaming(context: Context, allow: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_ALLOW_ROAMING] = allow }
    }

    suspend fun setDebugLogging(context: Context, enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_DEBUG_LOGGING] = enabled }
    }

    suspend fun setUpdateCheckEnabled(context: Context, enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[KEY_UPDATE_CHECK_ENABLED] = enabled }
    }

    suspend fun setLastUpdateCheckAt(context: Context, timestamp: Long) {
        context.userPreferencesDataStore.edit { it[KEY_LAST_UPDATE_CHECK_AT] = timestamp }
    }

    suspend fun setLanguage(context: Context, lang: String) {
        context.userPreferencesDataStore.edit { it[KEY_LANGUAGE] = lang }
    }

    suspend fun setLastActiveAccountId(context: Context, accountId: String?) {
        context.userPreferencesDataStore.edit {
            if (accountId == null) it.remove(KEY_LAST_ACTIVE_ACCOUNT_ID)
            else it[KEY_LAST_ACTIVE_ACCOUNT_ID] = accountId
        }
        context.getSharedPreferences("cloudyte_drive_state", Context.MODE_PRIVATE)
            .edit().putString("last_active_account_id", accountId).apply()
    }

    suspend fun setHomeStorageAccountId(context: Context, accountId: String?) {
        context.userPreferencesDataStore.edit {
            if (accountId == null) it.remove(KEY_HOME_STORAGE_ACCOUNT_ID)
            else it[KEY_HOME_STORAGE_ACCOUNT_ID] = accountId
        }
    }

    suspend fun resetAllSettings(context: Context) {
        context.userPreferencesDataStore.edit { it.clear() }
    }
}

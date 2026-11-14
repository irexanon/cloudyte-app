package com.cloudyte.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cloudyte.app.R
import com.cloudyte.app.core.cache.CacheCleanupWorker
import com.cloudyte.app.core.cache.StorageBreakdown
import com.cloudyte.app.core.cache.StorageManager
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.UserPreferencesState
import com.cloudyte.app.data.formatStorageSize
import com.cloudyte.app.ui.components.ChoiceDialog
import com.cloudyte.app.ui.components.ConfirmDialog
import com.cloudyte.app.ui.components.GroupedList
import com.cloudyte.app.ui.components.GroupedListItem
import com.cloudyte.app.ui.components.SettingsClickRow
import com.cloudyte.app.ui.components.SettingsSectionTitle
import com.cloudyte.app.ui.components.SettingsSliderRow
import com.cloudyte.app.ui.components.SettingsSwitchRow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun StorageSettingsSection(
    prefsState: UserPreferencesState,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var breakdown by remember { mutableStateOf(StorageBreakdown()) }
    var isBusy by remember { mutableStateOf(false) }
    var showConfirmClearCache by remember { mutableStateOf(false) }
    var showConfirmClearThumbnails by remember { mutableStateOf(false) }
    var showConfirmClearDownloads by remember { mutableStateOf(false) }
    var showDownloadDirDialog by remember { mutableStateOf(false) }

    suspend fun reloadBreakdown() {
        breakdown = StorageManager.measure(context)
    }

    LaunchedEffect(Unit) {
        reloadBreakdown()
    }

    SettingsSectionTitle(stringResource(R.string.settings_section_storage))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Local Storage Breakdown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            StorageSummaryLine("App Cache (Previews, Images, HTTP, View/Staging)", formatStorageSize(breakdown.totalAppCacheBytes))
            StorageSummaryLine("Thumbnails (${breakdown.thumbnailCount})", formatStorageSize(breakdown.thumbnailBytes))
            StorageSummaryLine("Saved Offline & Downloads (${breakdown.totalDownloadsCount})", formatStorageSize(breakdown.totalDownloadsBytes))
        }
    }

    val downloadDirs = stringArrayResource(R.array.download_dir_options)
    val storageItems = listOf(
        GroupedListItem(key = "clear_cache") {
            SettingsClickRow(
                title = stringResource(R.string.settings_clear_cache),
                subtitle = "${formatStorageSize(breakdown.totalAppCacheBytes)} • Previews, Coil, HTTP, viewer & staging cache",
                onClick = { if (!isBusy) showConfirmClearCache = true },
                modifier = Modifier.testTag("storage_clear_cache_row")
            )
        },
        GroupedListItem(key = "clear_thumbnails") {
            SettingsClickRow(
                title = stringResource(R.string.settings_clear_thumbnails),
                subtitle = "${breakdown.thumbnailCount} thumbnails • ${formatStorageSize(breakdown.thumbnailBytes)}",
                onClick = { if (!isBusy) showConfirmClearThumbnails = true },
                modifier = Modifier.testTag("storage_clear_thumbnails_row")
            )
        },
        GroupedListItem(key = "clear_downloads") {
            SettingsClickRow(
                title = stringResource(R.string.settings_clear_downloads),
                subtitle = "${breakdown.totalDownloadsCount} files • ${formatStorageSize(breakdown.totalDownloadsBytes)} (offline vault & exported files)",
                onClick = { if (!isBusy) showConfirmClearDownloads = true },
                modifier = Modifier.testTag("storage_clear_downloads_row")
            )
        },
        GroupedListItem(key = "download_dir") {
            val activeDir = StorageManager.getPreferredExportDirSync(context, prefsState.downloadDirectory)
            SettingsClickRow(
                title = stringResource(R.string.settings_download_directory),
                subtitle = "${if (prefsState.downloadDirectory == "INTERNAL") downloadDirs[0] else downloadDirs[1]} (${activeDir.name})",
                onClick = { showDownloadDirDialog = true },
                modifier = Modifier.testTag("storage_download_dir_row")
            )
        },
        GroupedListItem(key = "auto_clean") {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_auto_clean_cache),
                subtitle = "Periodically trims preview and HTTP cache when total size exceeds limit",
                checked = prefsState.autoCleanCache,
                onChange = { enabled ->
                    scope.launch {
                        UserPreferences.setAutoCleanCache(context, enabled)
                        CacheCleanupWorker.syncSchedule(context, enabled)
                    }
                },
                modifier = Modifier.testTag("storage_auto_clean_switch")
            )
        },
        GroupedListItem(key = "max_cache_size", visible = prefsState.autoCleanCache) {
            SettingsSliderRow(
                title = stringResource(R.string.settings_max_cache_size),
                value = prefsState.maxCacheSizeMb.toFloat(),
                range = 64f..2048f,
                steps = 14,
                onChange = { valMb ->
                    val snapped = ((valMb / 64f).roundToInt() * 64).coerceIn(64, 2048)
                    scope.launch { UserPreferences.setMaxCacheSizeMb(context, snapped) }
                },
                valueLabel = "${prefsState.maxCacheSizeMb} MB",
                modifier = Modifier.testTag("storage_max_cache_slider")
            )
        }
    )
    GroupedList(items = storageItems)

    if (showDownloadDirDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_download_directory),
            options = downloadDirs.toList(),
            selectedIndex = if (prefsState.downloadDirectory == "INTERNAL") 0 else 1,
            onSelect = { idx ->
                scope.launch {
                    UserPreferences.setDownloadDirectory(context, if (idx == 0) "INTERNAL" else "EXTERNAL")
                }
            },
            onDismiss = { showDownloadDirDialog = false }
        )
    }

    if (showConfirmClearCache) {
        val itemizedMsg = buildString {
            append(stringResource(R.string.settings_clear_cache_confirm, formatStorageSize(breakdown.totalAppCacheBytes)))
            append("\n\n• Previews: ${formatStorageSize(breakdown.previewCacheBytes)}")
            append("\n• Image cache: ${formatStorageSize(breakdown.coilCacheBytes)}")
            append("\n• HTTP cache: ${formatStorageSize(breakdown.httpCacheBytes)}")
            append("\n• Decrypted viewer & staging: ${formatStorageSize(breakdown.decryptedViewCacheBytes + breakdown.stagingCacheBytes)}")
        }
        ConfirmDialog(
            title = stringResource(R.string.settings_clear_cache),
            message = itemizedMsg,
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = {
                scope.launch {
                    isBusy = true
                    try {
                        val res = StorageManager.clearAppCache(context)
                        reloadBreakdown()
                        snackbarHostState.showSnackbar(
                            "Cleared ${formatStorageSize(res.freedBytes)} of app cache (${res.deletedFiles} files removed)."
                        )
                    } finally {
                        isBusy = false
                    }
                }
            },
            onDismiss = { showConfirmClearCache = false }
        )
    }

    if (showConfirmClearThumbnails) {
        ConfirmDialog(
            title = stringResource(R.string.settings_clear_thumbnails),
            message = stringResource(
                R.string.settings_clear_thumbnails_confirm,
                breakdown.thumbnailCount,
                formatStorageSize(breakdown.thumbnailBytes)
            ),
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = {
                scope.launch {
                    isBusy = true
                    try {
                        val res = StorageManager.clearThumbnails(context)
                        reloadBreakdown()
                        snackbarHostState.showSnackbar(
                            "Cleared ${formatStorageSize(res.freedBytes)} of thumbnails (${res.deletedFiles} files removed)."
                        )
                    } finally {
                        isBusy = false
                    }
                }
            },
            onDismiss = { showConfirmClearThumbnails = false }
        )
    }

    if (showConfirmClearDownloads) {
        val itemizedMsg = buildString {
            append(
                stringResource(
                    R.string.settings_clear_downloads_confirm,
                    breakdown.totalDownloadsCount,
                    formatStorageSize(breakdown.totalDownloadsBytes)
                )
            )
            append("\n\n• Offline vault: ${breakdown.offlineCount} files (${formatStorageSize(breakdown.offlineVaultBytes)})")
            append("\n• Exported downloads: ${breakdown.exportedDownloadsCount} files (${formatStorageSize(breakdown.exportedDownloadsBytes)})")
        }
        ConfirmDialog(
            title = stringResource(R.string.settings_clear_downloads),
            message = itemizedMsg,
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                scope.launch {
                    isBusy = true
                    try {
                        val res = StorageManager.clearDownloads(context)
                        reloadBreakdown()
                        snackbarHostState.showSnackbar(
                            "Deleted ${res.deletedFiles} downloaded files (${formatStorageSize(res.freedBytes)} freed)."
                        )
                    } finally {
                        isBusy = false
                    }
                }
            },
            onDismiss = { showConfirmClearDownloads = false }
        )
    }
}

@Composable
private fun StorageSummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

package com.cloudyte.app.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cloudyte.app.R
import com.cloudyte.app.db.BackupSourceEntity
import com.cloudyte.app.db.BackupSourcePermissionStatus
import com.cloudyte.app.sync.AppPermissionSummary
import com.cloudyte.app.sync.BackupSourceManager
import com.cloudyte.app.sync.PermissionState
import com.cloudyte.app.sync.PermissionsHelper
import com.cloudyte.app.ui.components.GroupedList
import com.cloudyte.app.ui.components.GroupedListItem
import com.cloudyte.app.ui.components.SettingsClickRow
import com.cloudyte.app.ui.components.SettingsSectionTitle
import kotlinx.coroutines.launch

@Composable
fun PermissionsSection(
    snackbarHostState: SnackbarHostState,
    onOpenBackupFolders: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var summary by remember {
        mutableStateOf(
            AppPermissionSummary(
                notificationsState = PermissionState.DENIED,
                runtimePostNotificationsGranted = false,
                osNotificationsEnabled = false,
                ignoringBatteryOptimizations = false,
                folderSources = emptyList(),
                healthyFolderCount = 0,
                brokenFolderCount = 0
            )
        )
    }
    var pendingRegrantSourceId by remember { mutableStateOf<String?>(null) }

    suspend fun reloadPermissions(withDelayedRecheck: Boolean = false) {
        summary = PermissionsHelper.inspectAppPermissions(context)
        if (withDelayedRecheck) {
            kotlinx.coroutines.delay(350L)
            summary = PermissionsHelper.inspectAppPermissions(context)
        }
    }

    LaunchedEffect(Unit) {
        reloadPermissions()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { reloadPermissions(withDelayedRecheck = true) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val settingsResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        scope.launch { reloadPermissions(withDelayedRecheck = true) }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch {
            reloadPermissions()
            if (!granted) {
                snackbarHostState.showSnackbar(
                    "Notifications denied. Tap again or open Android settings to enable alerts."
                )
            }
        }
    }

    val legacyStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        scope.launch {
            reloadPermissions()
            if (results.values.any { !it }) {
                snackbarHostState.showSnackbar(
                    "Storage permission denied. Open Android settings to allow storage access."
                )
            }
        }
    }

    val regrantFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val targetId = pendingRegrantSourceId
        pendingRegrantSourceId = null
        if (uri == null || targetId == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = BackupSourceManager.regrantSource(context, targetId, uri)
            reloadPermissions()
            result.onSuccess { updated ->
                snackbarHostState.showSnackbar("Access restored for '${updated.displayName}'.")
            }.onFailure { err ->
                snackbarHostState.showSnackbar("Failed to restore folder access: ${err.message ?: "Unknown error"}")
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    val fullStorageGranted = summary.hasFullStorageAccess
    val notifGranted = summary.notificationsState == PermissionState.GRANTED
    val batteryUnrestricted = summary.ignoringBatteryOptimizations
    val foldersGranted = fullStorageGranted || (summary.folderSources.isNotEmpty() && summary.brokenFolderCount == 0)
    val folderCritical = summary.brokenFolderCount > 0

    val permissionItems = listOf(
        GroupedListItem(key = "perm_full_storage") {
            TeleWardenPermissionRow(
                title = stringResource(R.string.settings_perm_full_storage),
                subtitle = if (fullStorageGranted) {
                    stringResource(R.string.settings_perm_full_storage_allowed)
                } else {
                    stringResource(
                        R.string.settings_perm_not_allowed_rationale,
                        stringResource(R.string.settings_perm_full_storage_rationale)
                    )
                },
                granted = fullStorageGranted,
                critical = !fullStorageGranted,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        runCatching {
                            settingsResultLauncher.launch(
                                PermissionsHelper.buildAllFilesAccessSettingsIntent(context, forActivityResult = true)
                            )
                        }.onFailure {
                            context.startActivity(PermissionsHelper.buildAllFilesAccessSettingsIntent(context))
                        }
                    } else if (fullStorageGranted) {
                        context.startActivity(PermissionsHelper.buildAppDetailsSettingsIntent(context))
                    } else {
                        legacyStorageLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                        )
                    }
                },
                modifier = Modifier.testTag("perm_all_files_access_row")
            )
        },
        GroupedListItem(key = "perm_notif") {
            TeleWardenPermissionRow(
                title = stringResource(R.string.settings_perm_notifications),
                subtitle = if (notifGranted) {
                    stringResource(R.string.settings_perm_allowed)
                } else {
                    stringResource(
                        R.string.settings_perm_not_allowed_rationale,
                        stringResource(R.string.settings_perm_notifications_rationale)
                    )
                },
                granted = notifGranted,
                critical = false,
                onClick = {
                    if (notifGranted) {
                        context.startActivity(PermissionsHelper.buildNotificationSettingsIntent(context))
                    } else if (!summary.runtimePostNotificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(PermissionsHelper.buildNotificationSettingsIntent(context))
                    }
                },
                modifier = Modifier.testTag("perm_notifications_row")
            )
        },
        GroupedListItem(key = "perm_battery") {
            TeleWardenPermissionRow(
                title = stringResource(R.string.settings_perm_battery),
                subtitle = if (batteryUnrestricted) {
                    stringResource(R.string.settings_perm_allowed)
                } else {
                    stringResource(
                        R.string.settings_perm_not_allowed_rationale,
                        stringResource(R.string.settings_perm_battery_rationale)
                    )
                },
                granted = batteryUnrestricted,
                critical = false,
                onClick = {
                    val intent = if (batteryUnrestricted) {
                        PermissionsHelper.buildBatteryOptimizationSettingsIntent(context, forActivityResult = true)
                    } else {
                        PermissionsHelper.buildRequestIgnoreBatteryOptimizationsIntent(context, forActivityResult = true)
                    }
                    runCatching {
                        settingsResultLauncher.launch(intent)
                    }.onFailure {
                        context.startActivity(PermissionsHelper.buildBatteryOptimizationSettingsIntent(context))
                    }
                },
                modifier = Modifier.testTag("perm_battery_row")
            )
        },
        GroupedListItem(key = "perm_folders_overview") {
            val subtitle = when {
                summary.folderSources.isEmpty() -> stringResource(R.string.settings_perm_folders_rationale)
                summary.brokenFolderCount > 0 ->
                    "Not allowed · ${summary.healthyFolderCount} granted, ${summary.brokenFolderCount} need access"
                else ->
                    "Allowed (${summary.healthyFolderCount} backup folder${if (summary.healthyFolderCount == 1) "" else "s"})"
            }
            TeleWardenPermissionRow(
                title = stringResource(R.string.settings_perm_media),
                subtitle = subtitle,
                granted = foldersGranted,
                critical = folderCritical,
                onClick = onOpenBackupFolders,
                modifier = Modifier.testTag("perm_folders_row")
            )
        },
        GroupedListItem(key = "perm_app_settings") {
            SettingsClickRow(
                title = "Open Android App Settings",
                subtitle = "Review system permissions, notifications, or battery usage",
                icon = Icons.Outlined.AdminPanelSettings,
                onClick = { context.startActivity(PermissionsHelper.buildAppDetailsSettingsIntent(context)) },
                modifier = Modifier.testTag("perm_open_system_settings_row")
            )
        }
    )
    GroupedList(items = permissionItems)

    if (summary.folderSources.isNotEmpty()) {
        SettingsSectionTitle("Backup folder permissions")
        val folderItems = summary.folderSources.map { source ->
            GroupedListItem(key = "folder_perm_${source.id}") {
                FolderPermissionRow(
                    source = source,
                    onClick = {
                        pendingRegrantSourceId = source.id
                        val preset = runCatching {
                            com.cloudyte.app.db.BackupPresetType.valueOf(source.presetType)
                        }.getOrDefault(com.cloudyte.app.db.BackupPresetType.CUSTOM)
                        regrantFolderLauncher.launch(BackupSourceManager.suggestedInitialUri(preset))
                    }
                )
            }
        }
        GroupedList(items = folderItems)
    }

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun TeleWardenPermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    critical: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (granted || !critical) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Outlined.Cancel,
            contentDescription = if (granted) {
                stringResource(R.string.settings_perm_allowed)
            } else {
                stringResource(R.string.settings_perm_not_allowed)
            },
            modifier = Modifier.size(22.dp),
            tint = when {
                granted -> MaterialTheme.colorScheme.primary
                critical -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outline
            }
        )
    }
}

@Composable
private fun FolderPermissionRow(
    source: BackupSourceEntity,
    onClick: () -> Unit
) {
    val status = runCatching { BackupSourcePermissionStatus.valueOf(source.permissionStatus) }
        .getOrDefault(BackupSourcePermissionStatus.UNKNOWN)
    val isHealthy = status == BackupSourcePermissionStatus.GRANTED
    val subtitle = when (status) {
        BackupSourcePermissionStatus.GRANTED -> stringResource(R.string.settings_perm_allowed)
        BackupSourcePermissionStatus.REVOKED ->
            "Not allowed · Tap to re-grant access"
        BackupSourcePermissionStatus.MISSING ->
            "Not allowed · Folder missing, tap to pick again"
        BackupSourcePermissionStatus.UNREADABLE ->
            "Not allowed · ${source.lastError ?: "Folder unreadable, tap to re-grant"}"
        BackupSourcePermissionStatus.UNKNOWN ->
            "Not allowed · Tap to grant folder access"
    }

    TeleWardenPermissionRow(
        title = source.displayName,
        subtitle = subtitle,
        granted = isHealthy,
        critical = !isHealthy,
        onClick = onClick,
        modifier = Modifier.testTag("perm_regrant_${source.id}")
    )
}

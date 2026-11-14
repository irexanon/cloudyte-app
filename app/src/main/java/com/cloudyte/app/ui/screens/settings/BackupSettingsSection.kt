package com.cloudyte.app.ui.screens.settings

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cloudyte.app.R
import com.cloudyte.app.api.Account
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.data.BackupMaxFileSizeOption
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.UserPreferencesState
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.BackupPresetType
import com.cloudyte.app.db.BackupSourceEntity
import com.cloudyte.app.db.BackupSourcePermissionStatus
import com.cloudyte.app.db.LocalAccount
import com.cloudyte.app.sync.AutoSyncManager
import com.cloudyte.app.sync.BackupSourceManager
import com.cloudyte.app.sync.SyncLogger
import com.cloudyte.app.sync.SyncPreferences
import com.cloudyte.app.sync.SyncState
import com.cloudyte.app.ui.components.ChoiceDialog
import com.cloudyte.app.ui.components.ConfirmDialog
import com.cloudyte.app.ui.components.GroupedList
import com.cloudyte.app.ui.components.GroupedListItem
import com.cloudyte.app.ui.components.SettingsClickRow
import com.cloudyte.app.ui.components.SettingsSectionTitle
import com.cloudyte.app.ui.components.SettingsSwitchRow
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupSettingsSection(
    prefsState: UserPreferencesState,
    snackbarHostState: SnackbarHostState,
    onNavigateExclusions: () -> Unit,
    onOpenSyncLogs: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val db = remember { AppDatabase.getDatabase(context) }

    val sources by db.backupSourceDao().observeAll().collectAsState(initial = emptyList())
    val activeTransfers by db.transferDao().observeActiveTransfers().collectAsState(initial = emptyList())
    val syncStatus by SyncLogger.statusFlow.collectAsState()

    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var pendingPreset by remember { mutableStateOf(BackupPresetType.CUSTOM) }
    var pendingRegrantId by remember { mutableStateOf<String?>(null) }
    var sourcePendingDelete by remember { mutableStateOf<BackupSourceEntity?>(null) }
    var showTargetDriveDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showMaxSizeDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteLocal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        SyncLogger.init(context)
        BackupSourceManager.refreshAllSourceHealth(context, db)
        runCatching {
            val cached = db.accountDao().getAllAccounts()
            if (cached.isNotEmpty()) accounts = cached.map { it.toAccount() }
        }
        runCatching {
            val fetched = ApiClient.apiService.getAccounts()
            if (fetched.isNotEmpty()) {
                accounts = fetched
                db.accountDao().insertAccounts(fetched.map { LocalAccount.fromAccount(it) })
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { BackupSourceManager.refreshAllSourceHealth(context, db) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val addFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val preset = pendingPreset
        pendingPreset = BackupPresetType.CUSTOM
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = BackupSourceManager.persistAndAddSource(
                context = context,
                treeUri = uri,
                presetType = preset,
                targetAccountId = prefsState.backupAccountId,
                db = db
            )
            result.onSuccess { added ->
                snackbarHostState.showSnackbar("Added backup folder '${added.displayName}'.")
            }.onFailure { err ->
                snackbarHostState.showSnackbar("Could not add folder: ${err.message ?: "Access denied"}")
            }
        }
    }

    val regrantLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val id = pendingRegrantId
        pendingRegrantId = null
        if (uri == null || id == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = BackupSourceManager.regrantSource(context, id, uri, db = db)
            result.onSuccess { restored ->
                snackbarHostState.showSnackbar("Restored access for '${restored.displayName}'.")
            }.onFailure { err ->
                snackbarHostState.showSnackbar("Re-grant failed: ${err.message ?: "Permission error"}")
            }
        }
    }

    // Live Backup Status Card
    SettingsSectionTitle("Backup Status")
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val statusHeadline = when (syncStatus.state) {
                SyncState.SCANNING -> "Scanning folders: ${syncStatus.currentAction}"
                SyncState.SYNCING -> "Uploading (${syncStatus.uploadedFilesCount}/${syncStatus.totalFilesCount})"
                SyncState.ERROR -> "Last run encountered issues"
                SyncState.SUCCESS -> "Backup scan complete"
                SyncState.IDLE -> if (prefsState.autoBackupEnabled) "Automatic backup active" else "Automatic backup paused"
            }
            Text(text = statusHeadline, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            val lastTs = if (prefsState.lastBackupAt > 0L) {
                DateFormat.format("MMM d, HH:mm", Date(prefsState.lastBackupAt)).toString()
            } else {
                "Never"
            }
            Text(
                text = "Last run: $lastTs • ${prefsState.lastBackupSummary.ifBlank { syncStatus.lastSyncSummary }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Active queued transfers: ${activeTransfers.size} • Last failures: ${prefsState.lastBackupFailedCount}",
                style = MaterialTheme.typography.bodySmall,
                color = if (prefsState.lastBackupFailedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (prefsState.lastBackupSkippedSummary.isNotBlank()) {
                Text(
                    text = "Skipped summary: ${prefsState.lastBackupSkippedSummary}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (sources.none { it.enabled }) {
                            scope.launch { snackbarHostState.showSnackbar("Add or enable at least one backup folder first.") }
                        } else {
                            AutoSyncManager.triggerImmediateSync(context, isManual = true)
                            scope.launch { snackbarHostState.showSnackbar("Backup scan started.") }
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("backup_now_button")
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_backup_now))
                }
                OutlinedButton(
                    onClick = onOpenSyncLogs,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("backup_view_logs_button")
                ) {
                    Text("View Logs")
                }
            }
        }
    }

    // Schedule & Conditions
    SettingsSectionTitle("Schedule & Conditions")
    val selectedDriveName = accounts.firstOrNull { it.effectiveId == prefsState.backupAccountId }?.effectiveName
        ?: accounts.firstOrNull { it.isDefault == true }?.let { "${it.effectiveName} (Default)" }
        ?: "Default connected drive"
    val intervalHours = prefsState.backupIntervalHours
    val scheduleItems = listOf(
        GroupedListItem(key = "target_drive") {
            SettingsClickRow(
                title = stringResource(R.string.settings_backup_target_drive),
                subtitle = selectedDriveName,
                onClick = { showTargetDriveDialog = true },
                modifier = Modifier.testTag("backup_target_drive_row")
            )
        },
        GroupedListItem(key = "auto_backup") {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_auto_backup),
                subtitle = stringResource(R.string.settings_auto_backup_sub),
                checked = prefsState.autoBackupEnabled,
                onChange = { enabled ->
                    scope.launch {
                        AutoSyncManager.setAutoBackupSuspend(context, enabled)
                    }
                },
                modifier = Modifier.testTag("backup_auto_switch")
            )
        },
        GroupedListItem(key = "quick_media") {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_backup_quick_media),
                subtitle = stringResource(R.string.settings_backup_quick_media_sub),
                checked = prefsState.quickMediaBackupEnabled,
                enabled = prefsState.autoBackupEnabled,
                onChange = { enabled ->
                    scope.launch {
                        UserPreferences.setQuickMediaBackupEnabled(context, enabled)
                        AutoSyncManager.reschedule(context)
                    }
                },
                modifier = Modifier.testTag("backup_quick_media_switch")
            )
        },
        GroupedListItem(key = "schedule") {
            SettingsClickRow(
                title = stringResource(R.string.settings_backup_schedule),
                subtitle = if (intervalHours == 1) "Every 1 hour" else "Every $intervalHours hours",
                onClick = { showIntervalDialog = true },
                modifier = Modifier.testTag("backup_interval_row")
            )
        },
        GroupedListItem(key = "wifi_only") {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_backup_wifi_only),
                subtitle = stringResource(R.string.settings_backup_wifi_only_sub),
                checked = prefsState.backupWifiOnly,
                onChange = { wifiOnly ->
                    scope.launch {
                        UserPreferences.setBackupWifiOnly(context, wifiOnly)
                        AutoSyncManager.reschedule(context)
                    }
                },
                modifier = Modifier.testTag("backup_wifi_only_switch")
            )
        },
        GroupedListItem(key = "charging_only") {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_backup_charging_only),
                subtitle = stringResource(R.string.settings_backup_charging_only_sub),
                checked = prefsState.backupChargingOnly,
                onChange = { chargingOnly ->
                    scope.launch {
                        UserPreferences.setBackupChargingOnly(context, chargingOnly)
                        AutoSyncManager.reschedule(context)
                    }
                },
                modifier = Modifier.testTag("backup_charging_only_switch")
            )
        }
    )
    GroupedList(items = scheduleItems)

    // Backup Folders (Multi-Source)
    SettingsSectionTitle(stringResource(R.string.settings_backup_folders))
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Quick-add standard folders or pick any folder via Android's folder picker:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                BackupPresetType.CAMERA,
                BackupPresetType.PICTURES,
                BackupPresetType.DOCUMENTS,
                BackupPresetType.DOWNLOADS
            ).forEach { preset ->
                AssistChip(
                    onClick = {
                        pendingPreset = preset
                        addFolderLauncher.launch(BackupSourceManager.suggestedInitialUri(preset))
                    },
                    label = { Text(preset.label) },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                    modifier = Modifier.testTag("backup_preset_${preset.name.lowercase()}")
                )
            }
            AssistChip(
                onClick = {
                    pendingPreset = BackupPresetType.CUSTOM
                    addFolderLauncher.launch(null)
                },
                label = { Text(stringResource(R.string.settings_backup_add_custom)) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                modifier = Modifier.testTag("backup_add_custom_folder_chip")
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (sources.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No backup folders configured yet. Select one or more folders above to start automatic backups.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sources.forEach { source ->
                    BackupSourceCard(
                        source = source,
                        onToggleEnabled = { enabled ->
                            scope.launch { BackupSourceManager.setSourceEnabled(context, source.id, enabled, db) }
                        },
                        onRegrant = {
                            pendingRegrantId = source.id
                            regrantLauncher.launch(null)
                        },
                        onRemove = { sourcePendingDelete = source }
                    )
                }
            }
        }
    }

    // Limits & Exclusions
    SettingsSectionTitle("Limits & Safety")
    val maxOption = BackupMaxFileSizeOption.fromMb(prefsState.backupMaxFileSizeMb)
    val limitItems = listOf(
        GroupedListItem(key = "max_size") {
            SettingsClickRow(
                title = stringResource(R.string.settings_max_file_size),
                subtitle = maxOption.label,
                onClick = { showMaxSizeDialog = true },
                modifier = Modifier.testTag("backup_max_size_row")
            )
        },
        GroupedListItem(key = "exclusions") {
            SettingsClickRow(
                title = stringResource(R.string.settings_exclusions),
                subtitle = stringResource(R.string.settings_exclusions_sub),
                onClick = onNavigateExclusions,
                modifier = Modifier.testTag("backup_exclusions_row")
            )
        },
        GroupedListItem(key = "delete_after_upload") {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_delete_after_upload),
                subtitle = stringResource(R.string.settings_delete_after_upload_sub),
                checked = prefsState.deleteAfterUpload,
                onChange = { wantEnable ->
                    if (wantEnable) {
                        showConfirmDeleteLocal = true
                    } else {
                        scope.launch { UserPreferences.setDeleteAfterUpload(context, false) }
                    }
                },
                modifier = Modifier.testTag("backup_delete_after_upload_switch")
            )
        }
    )
    GroupedList(items = limitItems)

    // Dialogs
    if (showTargetDriveDialog) {
        val driveLabels = if (accounts.isEmpty()) listOf("Default connected drive") else accounts.map { "${it.effectiveName} (${it.effectiveType})" }
        val selectedIdx = accounts.indexOfFirst { it.effectiveId == prefsState.backupAccountId }.coerceAtLeast(0)
        ChoiceDialog(
            title = stringResource(R.string.settings_backup_target_drive),
            options = driveLabels,
            selectedIndex = selectedIdx,
            onSelect = { idx ->
                val chosen = accounts.getOrNull(idx) ?: return@ChoiceDialog
                scope.launch {
                    UserPreferences.setBackupAccountId(context, chosen.effectiveId)
                    SyncPreferences.setBackupAccountId(context, chosen.effectiveId)
                    AutoSyncManager.onFolderOrAccountChanged(context)
                }
            },
            onDismiss = { showTargetDriveDialog = false }
        )
    }

    if (showIntervalDialog) {
        val intervals = UserPreferences.BACKUP_INTERVAL_OPTIONS_HOURS
        val labels = intervals.map { h -> if (h == 1) "Every 1 hour" else "Every $h hours" }
        val selectedIdx = intervals.indexOf(intervalHours).coerceAtLeast(0)
        ChoiceDialog(
            title = stringResource(R.string.settings_backup_schedule),
            options = labels,
            selectedIndex = selectedIdx,
            onSelect = { idx ->
                val hours = intervals[idx]
                scope.launch {
                    UserPreferences.setBackupIntervalHours(context, hours)
                    AutoSyncManager.reschedule(context)
                }
            },
            onDismiss = { showIntervalDialog = false }
        )
    }

    if (showMaxSizeDialog) {
        val options = BackupMaxFileSizeOption.entries
        ChoiceDialog(
            title = stringResource(R.string.settings_max_file_size),
            options = options.map { it.label },
            selectedIndex = options.indexOf(maxOption).coerceAtLeast(0),
            onSelect = { idx ->
                val chosen = options[idx]
                scope.launch {
                    UserPreferences.setBackupMaxFileSizeMb(context, chosen.mb)
                    AutoSyncManager.reschedule(context)
                }
            },
            onDismiss = { showMaxSizeDialog = false }
        )
    }

    sourcePendingDelete?.let { source ->
        ConfirmDialog(
            title = stringResource(R.string.settings_backup_remove_title),
            message = stringResource(R.string.settings_backup_remove_msg, source.displayName),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                scope.launch {
                    BackupSourceManager.removeSource(context, source.id, db)
                    snackbarHostState.showSnackbar("Stopped backups from '${source.displayName}'. Remote files remain safe.")
                }
            },
            onDismiss = { sourcePendingDelete = null }
        )
    }

    if (showConfirmDeleteLocal) {
        ConfirmDialog(
            title = stringResource(R.string.settings_delete_after_upload_confirm_title),
            message = stringResource(R.string.settings_delete_after_upload_confirm_msg),
            confirmLabel = stringResource(R.string.action_confirm),
            onConfirm = {
                scope.launch { UserPreferences.setDeleteAfterUpload(context, true) }
            },
            onDismiss = { showConfirmDeleteLocal = false }
        )
    }
}

@Composable
private fun BackupSourceCard(
    source: BackupSourceEntity,
    onToggleEnabled: (Boolean) -> Unit,
    onRegrant: () -> Unit,
    onRemove: () -> Unit
) {
    val permStatus = runCatching { BackupSourcePermissionStatus.valueOf(source.permissionStatus) }
        .getOrDefault(BackupSourcePermissionStatus.UNKNOWN)
    val isHealthy = permStatus == BackupSourcePermissionStatus.GRANTED
    val statusText = when (permStatus) {
        BackupSourcePermissionStatus.GRANTED -> "Granted"
        BackupSourcePermissionStatus.REVOKED -> "Permission Revoked"
        BackupSourcePermissionStatus.MISSING -> "Folder Missing"
        BackupSourcePermissionStatus.UNREADABLE -> "Unreadable"
        BackupSourcePermissionStatus.UNKNOWN -> "Unknown"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val lastScanStr = if (source.lastScanAt > 0L) {
                        "Last scan ${DateFormat.format("MMM d, HH:mm", Date(source.lastScanAt))}"
                    } else {
                        "Not scanned yet"
                    }
                    Text(
                        text = "$statusText • $lastScanStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isHealthy) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                }
                Switch(
                    checked = source.enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.testTag("backup_source_switch_${source.id}")
                )
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag("backup_source_remove_${source.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove ${source.displayName}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (!source.lastSummary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = source.lastSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isHealthy) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = source.lastError ?: "Folder permission was lost or folder cannot be read.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = onRegrant,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("backup_source_regrant_${source.id}")
                        ) {
                            Text(stringResource(R.string.settings_backup_regrant))
                        }
                    }
                }
            }
        }
    }
}

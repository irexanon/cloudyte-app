package com.cloudyte.app.ui.screens.settings

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
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
    var sourceForDrivePicker by remember { mutableStateOf<BackupSourceEntity?>(null) }
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
                targetAccountId = null,
                db = db
            )
            result.onSuccess { added ->
                snackbarHostState.showSnackbar("Added backup folder '${added.displayName}'.")
            }.onFailure { err ->
                snackbarHostState.showSnackbar("Could not read folder: ${err.message ?: "Access denied"}")
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

    Spacer(modifier = Modifier.height(8.dp))

    // Live Backup Status & Quick Actions Card
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
                    text = "Skipped: ${prefsState.lastBackupSkippedSummary}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (sources.none { it.enabled }) {
                            scope.launch { snackbarHostState.showSnackbar("Enable at least one backup folder below first.") }
                        } else {
                            AutoSyncManager.triggerImmediateSync(context, isManual = true)
                            scope.launch { snackbarHostState.showSnackbar("Backup scan started.") }
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("backup_now_button")
                ) {
                    Icon(Icons.Outlined.Sync, contentDescription = null)
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

    Spacer(modifier = Modifier.height(12.dp))

    // TeleWarden-style Main Backup Controls Group
    val selectedDriveName = accounts.firstOrNull { it.effectiveId == prefsState.backupAccountId }?.effectiveName
        ?: accounts.firstOrNull { it.isDefault == true }?.let { "${it.effectiveName} (Default)" }
        ?: "Default connected drive"
    val intervalHours = prefsState.backupIntervalHours
    val intervalBaseLabel = if (intervalHours == 1) "Every 1 hour" else "Every $intervalHours hours"
    val intervalSubtitle = if (prefsState.quickMediaBackupEnabled && prefsState.autoBackupEnabled) {
        stringResource(R.string.settings_backup_interval_with_sweep, intervalBaseLabel)
    } else {
        intervalBaseLabel
    }
    val maxOption = BackupMaxFileSizeOption.fromMb(prefsState.backupMaxFileSizeMb)

    val mainItems = buildList {
        add(
            GroupedListItem(key = "auto_backup") {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_auto_backup),
                    icon = Icons.Outlined.AutoMode,
                    checked = prefsState.autoBackupEnabled,
                    onChange = { enabled ->
                        scope.launch { AutoSyncManager.setAutoBackupSuspend(context, enabled) }
                    },
                    modifier = Modifier.testTag("backup_auto_switch")
                )
            }
        )
        if (prefsState.autoBackupEnabled) {
            add(
                GroupedListItem(key = "quick_media") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_backup_quick_media),
                        subtitle = stringResource(R.string.settings_backup_quick_media_sub),
                        icon = Icons.Outlined.PhotoLibrary,
                        checked = prefsState.quickMediaBackupEnabled,
                        onChange = { enabled ->
                            scope.launch {
                                UserPreferences.setQuickMediaBackupEnabled(context, enabled)
                                AutoSyncManager.reschedule(context)
                            }
                        },
                        modifier = Modifier.testTag("backup_quick_media_switch")
                    )
                }
            )
        }
        val customDriveCount = sources.count { !it.targetAccountId.isNullOrBlank() }
        val defaultDriveRowSubtitle = if (customDriveCount > 0) {
            "$selectedDriveName • $customDriveCount folder(s) use a custom drive"
        } else {
            selectedDriveName
        }
        add(
            GroupedListItem(key = "target_drive") {
                SettingsClickRow(
                    title = stringResource(R.string.settings_backup_default_drive_title),
                    subtitle = defaultDriveRowSubtitle,
                    icon = Icons.Outlined.CloudUpload,
                    onClick = { showTargetDriveDialog = true },
                    modifier = Modifier.testTag("backup_target_drive_row")
                )
            }
        )
        add(
            GroupedListItem(key = "schedule") {
                SettingsClickRow(
                    title = stringResource(R.string.settings_backup_schedule),
                    subtitle = intervalSubtitle,
                    icon = Icons.Outlined.Schedule,
                    onClick = { showIntervalDialog = true },
                    modifier = Modifier.testTag("backup_interval_row")
                )
            }
        )
        add(
            GroupedListItem(key = "wifi_only") {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_backup_wifi_only),
                    subtitle = stringResource(R.string.settings_backup_wifi_only_sub),
                    icon = Icons.Outlined.Wifi,
                    checked = prefsState.backupWifiOnly,
                    onChange = { wifiOnly ->
                        scope.launch {
                            UserPreferences.setBackupWifiOnly(context, wifiOnly)
                            AutoSyncManager.reschedule(context)
                        }
                    },
                    modifier = Modifier.testTag("backup_wifi_only_switch")
                )
            }
        )
        add(
            GroupedListItem(key = "charging_only") {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_backup_charging_only),
                    icon = Icons.Outlined.BatteryChargingFull,
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
        add(
            GroupedListItem(key = "max_size") {
                SettingsClickRow(
                    title = stringResource(R.string.settings_max_file_size),
                    subtitle = maxOption.label,
                    icon = Icons.Outlined.Scale,
                    onClick = { showMaxSizeDialog = true },
                    modifier = Modifier.testTag("backup_max_size_row")
                )
            }
        )
        add(
            GroupedListItem(key = "exclusions") {
                SettingsClickRow(
                    title = stringResource(R.string.settings_exclusions),
                    subtitle = stringResource(R.string.settings_exclusions_sub),
                    icon = Icons.Outlined.FilterAltOff,
                    onClick = onNavigateExclusions,
                    modifier = Modifier.testTag("backup_exclusions_row")
                )
            }
        )
        add(
            GroupedListItem(key = "delete_after_upload") {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_delete_after_upload),
                    subtitle = stringResource(R.string.settings_delete_after_upload_sub),
                    icon = Icons.Outlined.DeleteSweep,
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
    }
    GroupedList(items = mainItems)

    // TeleWarden-style Backup folders Section
    Spacer(modifier = Modifier.height(8.dp))
    SettingsSectionTitle(stringResource(R.string.settings_backup_folders))

    val activeSourcesCount = sources.count { it.enabled }
    if (prefsState.autoBackupEnabled && activeSourcesCount == 0) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_backup_no_folders_selected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
    }

    val standardPresets = BackupSourceManager.standardPresets
    val matchedStandardSources = remember(sources) {
        standardPresets.associateWith { preset -> BackupSourceManager.findSourceForPreset(preset, sources) }
    }
    val matchedIds = remember(matchedStandardSources) {
        matchedStandardSources.values.mapNotNull { it?.id }.toSet()
    }
    val customSources = remember(sources, matchedIds) {
        sources.filterNot { it.id in matchedIds }
    }

    val folderItems = buildList {
        standardPresets.forEach { preset ->
            val linkedSource = matchedStandardSources[preset]
            val isHealthy = linkedSource?.permissionStatus == BackupSourcePermissionStatus.GRANTED.name
            val isChecked = linkedSource != null && linkedSource.enabled && isHealthy

            val sub = when {
                linkedSource == null -> preset.relativePath
                !isHealthy -> linkedSource.lastError ?: "${preset.relativePath} • Permission required (tap to grant)"
                linkedSource.enabled -> {
                    val lastScanStr = if (linkedSource.lastScanAt > 0L) {
                        "Last scan ${DateFormat.format("MMM d, HH:mm", Date(linkedSource.lastScanAt))}"
                    } else {
                        "Granted"
                    }
                    "${preset.relativePath} • $lastScanStr"
                }
                else -> "${preset.relativePath} • Paused"
            }

            add(
                GroupedListItem(key = "std_${preset.name}") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsSwitchRow(
                            title = preset.label,
                            subtitle = sub,
                            icon = Icons.Outlined.FolderOpen,
                            checked = isChecked,
                            onChange = { wantEnable ->
                                when {
                                    linkedSource == null -> {
                                        val parentGrant = BackupSourceManager.findParentTreeGrantForRelativePath(
                                            context,
                                            preset.relativePath
                                        )
                                        if (parentGrant != null) {
                                            val derivedUri = BackupSourceManager.buildSubfolderTreeUri(
                                                parentGrant.first,
                                                parentGrant.second
                                            )
                                            scope.launch {
                                                val result = BackupSourceManager.persistAndAddSource(
                                                    context = context,
                                                    treeUri = derivedUri,
                                                    presetType = preset,
                                                    targetAccountId = null,
                                                    db = db
                                                )
                                                result.onSuccess { added ->
                                                    snackbarHostState.showSnackbar("Enabled '${added.displayName}' using existing folder grant.")
                                                }.onFailure {
                                                    pendingPreset = preset
                                                    addFolderLauncher.launch(BackupSourceManager.suggestedInitialUri(preset))
                                                }
                                            }
                                        } else {
                                            pendingPreset = preset
                                            addFolderLauncher.launch(BackupSourceManager.suggestedInitialUri(preset))
                                        }
                                    }
                                    wantEnable && !isHealthy -> {
                                        val parentGrant = BackupSourceManager.findParentTreeGrantForRelativePath(
                                            context,
                                            preset.relativePath
                                        )
                                        if (parentGrant != null) {
                                            val derivedUri = BackupSourceManager.buildSubfolderTreeUri(
                                                parentGrant.first,
                                                parentGrant.second
                                            )
                                            scope.launch {
                                                val result = BackupSourceManager.regrantSource(
                                                    context = context,
                                                    sourceId = linkedSource.id,
                                                    grantedTreeUri = derivedUri,
                                                    db = db
                                                )
                                                result.onSuccess { restored ->
                                                    snackbarHostState.showSnackbar("Restored '${restored.displayName}' using existing folder grant.")
                                                }.onFailure {
                                                    pendingRegrantId = linkedSource.id
                                                    regrantLauncher.launch(BackupSourceManager.suggestedInitialUri(preset))
                                                }
                                            }
                                        } else {
                                            pendingRegrantId = linkedSource.id
                                            regrantLauncher.launch(BackupSourceManager.suggestedInitialUri(preset))
                                        }
                                    }
                                    else -> {
                                        scope.launch {
                                            BackupSourceManager.setSourceEnabled(context, linkedSource.id, wantEnable, db)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag("backup_preset_${preset.name.lowercase()}")
                        )

                        if (linkedSource != null && accounts.isNotEmpty()) {
                            val overrideAcc = linkedSource.targetAccountId?.let { id ->
                                accounts.firstOrNull { it.effectiveId == id }
                            }
                            FolderTargetDriveChip(
                                driveLabel = overrideAcc?.effectiveName
                                    ?: stringResource(R.string.settings_backup_folder_drive_default_chip, selectedDriveName),
                                isCustomDrive = overrideAcc != null,
                                onClick = { sourceForDrivePicker = linkedSource },
                                modifier = Modifier
                                    .padding(start = 56.dp, end = 16.dp, bottom = 12.dp)
                                    .testTag("backup_preset_drive_${preset.name.lowercase()}")
                            )
                        }
                    }
                }
            )
        }

        customSources.forEach { source ->
            val permStatus = runCatching { BackupSourcePermissionStatus.valueOf(source.permissionStatus) }
                .getOrDefault(BackupSourcePermissionStatus.UNKNOWN)
            val isHealthy = permStatus == BackupSourcePermissionStatus.GRANTED
            val overrideAcc = source.targetAccountId?.let { id ->
                accounts.firstOrNull { it.effectiveId == id }
            }
            val sub = when {
                !isHealthy -> source.lastError ?: "Permission revoked · Tap to re-grant or remove"
                source.lastScanAt > 0L ->
                    "Granted • Last scan ${DateFormat.format("MMM d, HH:mm", Date(source.lastScanAt))}"
                else -> "Granted • Custom folder"
            }
            add(
                GroupedListItem(key = "custom_${source.id}") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsClickRow(
                            title = source.displayName,
                            subtitle = sub,
                            icon = Icons.Outlined.FolderOpen,
                            titleColor = if (isHealthy) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                            trailing = {
                                IconButton(
                                    onClick = { sourcePendingDelete = source },
                                    modifier = Modifier.testTag("backup_source_remove_${source.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Remove ${source.displayName}",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            onClick = {
                                if (!isHealthy) {
                                    pendingRegrantId = source.id
                                    regrantLauncher.launch(null)
                                } else if (accounts.isNotEmpty()) {
                                    sourceForDrivePicker = source
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("backup_custom_source_${source.id}")
                        )
                        if (accounts.isNotEmpty()) {
                            FolderTargetDriveChip(
                                driveLabel = overrideAcc?.effectiveName
                                    ?: stringResource(R.string.settings_backup_folder_drive_default_chip, selectedDriveName),
                                isCustomDrive = overrideAcc != null,
                                onClick = { sourceForDrivePicker = source },
                                modifier = Modifier
                                    .padding(start = 56.dp, end = 16.dp, bottom = 12.dp)
                                    .testTag("backup_custom_drive_${source.id}")
                            )
                        }
                    }
                }
            )
        }

        add(
            GroupedListItem(key = "add_another_folder") {
                SettingsClickRow(
                    title = stringResource(R.string.settings_backup_add_custom),
                    subtitle = stringResource(R.string.settings_backup_choose_folder_device),
                    icon = Icons.Outlined.CreateNewFolder,
                    onClick = {
                        pendingPreset = BackupPresetType.CUSTOM
                        addFolderLauncher.launch(null)
                    },
                    modifier = Modifier.testTag("backup_add_custom_folder_chip")
                )
            }
        )
    }
    GroupedList(items = folderItems)

    Spacer(modifier = Modifier.height(16.dp))

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

    sourceForDrivePicker?.let { source ->
        val defaultOptionLabel = stringResource(R.string.settings_backup_use_default_drive_format, selectedDriveName)
        val options = listOf(defaultOptionLabel) + accounts.map { "${it.effectiveName} (${it.effectiveType})" }
        val selectedIdx = if (source.targetAccountId.isNullOrBlank()) {
            0
        } else {
            val accIdx = accounts.indexOfFirst { it.effectiveId == source.targetAccountId }
            if (accIdx >= 0) accIdx + 1 else 0
        }
        ChoiceDialog(
            title = stringResource(R.string.settings_backup_folder_drive_dialog_title, source.displayName),
            options = options,
            selectedIndex = selectedIdx,
            onSelect = { idx ->
                scope.launch {
                    if (idx == 0) {
                        BackupSourceManager.setSourceTargetAccount(
                            context = context,
                            sourceId = source.id,
                            targetAccountId = null,
                            db = db
                        )
                        snackbarHostState.showSnackbar("'${source.displayName}' will back up to default drive ($selectedDriveName).")
                    } else {
                        val chosen = accounts.getOrNull(idx - 1) ?: return@launch
                        BackupSourceManager.setSourceTargetAccount(
                            context = context,
                            sourceId = source.id,
                            targetAccountId = chosen.effectiveId,
                            targetDriveLabel = chosen.effectiveName,
                            db = db
                        )
                        snackbarHostState.showSnackbar("'${source.displayName}' will back up to '${chosen.effectiveName}'.")
                    }
                }
            },
            onDismiss = { sourceForDrivePicker = null }
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
private fun FolderTargetDriveChip(
    driveLabel: String,
    isCustomDrive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isCustomDrive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (isCustomDrive) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        } else {
            null
        },
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudUpload,
                contentDescription = null,
                tint = if (isCustomDrive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = driveLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isCustomDrive) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isCustomDrive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = "Choose backup drive",
                tint = if (isCustomDrive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

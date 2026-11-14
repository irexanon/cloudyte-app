package com.cloudyte.app.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
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
                folderSources = emptyList(),
                healthyFolderCount = 0,
                brokenFolderCount = 0
            )
        )
    }
    var pendingRegrantSourceId by remember { mutableStateOf<String?>(null) }

    suspend fun reloadPermissions() {
        summary = PermissionsHelper.inspectAppPermissions(context)
    }

    LaunchedEffect(Unit) {
        reloadPermissions()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { reloadPermissions() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    SettingsSectionTitle(stringResource(R.string.settings_section_permissions))

    val notifGranted = summary.notificationsState == PermissionState.GRANTED
    val folderStatusText = when {
        summary.folderSources.isEmpty() -> "No backup folders selected yet (uses Android folder picker per folder)"
        summary.brokenFolderCount > 0 -> "${summary.healthyFolderCount} granted • ${summary.brokenFolderCount} need attention"
        else -> "Granted (${summary.healthyFolderCount} folder${if (summary.healthyFolderCount == 1) "" else "s"} accessible)"
    }

    val topItems = listOf(
        GroupedListItem(key = "perm_notif") {
            val sub = when {
                notifGranted -> stringResource(R.string.settings_perm_granted)
                !summary.runtimePostNotificationsGranted -> "Not Granted — tap to request Android notification permission"
                else -> "Disabled in Android system settings — tap to open notification settings"
            }
            SettingsClickRow(
                title = stringResource(R.string.settings_perm_notifications),
                subtitle = sub,
                trailing = {
                    StatusBadge(
                        text = if (notifGranted) stringResource(R.string.settings_perm_granted)
                        else stringResource(R.string.settings_perm_not_granted),
                        isPositive = notifGranted
                    )
                },
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
        GroupedListItem(key = "perm_folders_overview") {
            SettingsClickRow(
                title = "Scoped Backup Folder Access",
                subtitle = folderStatusText,
                trailing = {
                    StatusBadge(
                        text = when {
                            summary.folderSources.isEmpty() -> "None Selected"
                            summary.brokenFolderCount == 0 -> stringResource(R.string.settings_perm_granted)
                            else -> "Action Needed"
                        },
                        isPositive = summary.folderSources.isNotEmpty() && summary.brokenFolderCount == 0
                    )
                },
                onClick = onOpenBackupFolders,
                modifier = Modifier.testTag("perm_folders_row")
            )
        },
        GroupedListItem(key = "perm_app_settings") {
            SettingsClickRow(
                title = "Open Android App Settings",
                subtitle = "Review system permissions or revoke/restore app capabilities",
                onClick = { context.startActivity(PermissionsHelper.buildAppDetailsSettingsIntent(context)) },
                modifier = Modifier.testTag("perm_open_system_settings_row")
            )
        }
    )
    GroupedList(items = topItems)

    if (summary.folderSources.isNotEmpty()) {
        SettingsSectionTitle("Folder Access Details")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            summary.folderSources.forEach { source ->
                FolderPermissionCard(
                    source = source,
                    onRegrant = {
                        pendingRegrantSourceId = source.id
                        regrantFolderLauncher.launch(null)
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun FolderPermissionCard(
    source: BackupSourceEntity,
    onRegrant: () -> Unit
) {
    val status = runCatching { BackupSourcePermissionStatus.valueOf(source.permissionStatus) }
        .getOrDefault(BackupSourcePermissionStatus.UNKNOWN)
    val isHealthy = status == BackupSourcePermissionStatus.GRANTED
    val statusLabel = when (status) {
        BackupSourcePermissionStatus.GRANTED -> stringResource(R.string.settings_perm_granted)
        BackupSourcePermissionStatus.REVOKED -> "Permission Revoked"
        BackupSourcePermissionStatus.MISSING -> "Folder Missing"
        BackupSourcePermissionStatus.UNREADABLE -> "Unreadable"
        BackupSourcePermissionStatus.UNKNOWN -> stringResource(R.string.settings_perm_not_granted)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isHealthy) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
            }
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = source.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(text = statusLabel, isPositive = isHealthy)
            }
            if (!isHealthy) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = source.lastError
                        ?: "Cloudyte can no longer read this folder. Re-grant access via Android's folder picker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRegrant,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("perm_regrant_${source.id}")
                ) {
                    Text(stringResource(R.string.settings_backup_regrant))
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, isPositive: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isPositive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isPositive) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

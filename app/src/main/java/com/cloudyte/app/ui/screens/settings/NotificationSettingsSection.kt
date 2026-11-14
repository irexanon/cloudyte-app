package com.cloudyte.app.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.cloudyte.app.core.transfer.notification.NotificationHelper
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.UserPreferencesState
import com.cloudyte.app.sync.PermissionsHelper
import com.cloudyte.app.ui.components.GroupedList
import com.cloudyte.app.ui.components.GroupedListItem
import com.cloudyte.app.ui.components.SettingsClickRow
import com.cloudyte.app.ui.components.SettingsSectionTitle
import com.cloudyte.app.ui.components.SettingsSwitchRow
import kotlinx.coroutines.launch

@Composable
fun NotificationSettingsSection(
    prefsState: UserPreferencesState,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var osPermGranted by remember { mutableStateOf(PermissionsHelper.isNotificationPermissionGranted(context)) }
    var runtimeGranted by remember { mutableStateOf(PermissionsHelper.isRuntimePostNotificationsGranted(context)) }

    val refreshStatus = {
        osPermGranted = PermissionsHelper.isNotificationPermissionGranted(context)
        runtimeGranted = PermissionsHelper.isRuntimePostNotificationsGranted(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requestPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshStatus()
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar("Notification permission denied. Open system settings to allow alerts.")
            }
        }
    }

    SettingsSectionTitle(stringResource(R.string.settings_section_notifications))

    if (!osPermGranted) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Android Notifications Blocked",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Backup, progress, and failure alerts cannot appear until Android notification permission is enabled for Cloudyte.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        if (!runtimeGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(NotificationHelper.buildAppNotificationSettingsIntent(context))
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("notif_fix_permission_button")
                ) {
                    Text(stringResource(R.string.action_grant))
                }
            }
        }
    }

    val notifItems = listOf(
        GroupedListItem(key = "enable_notif") {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_enable_notifications),
                subtitle = if (osPermGranted) "Master toggle for Cloudyte notifications" else "Requires Android notification permission above",
                checked = prefsState.notificationsEnabled,
                onChange = { scope.launch { UserPreferences.setNotificationsEnabled(context, it) } },
                modifier = Modifier.testTag("notif_master_switch")
            )
        },
        GroupedListItem(key = "backup_alerts", visible = prefsState.notificationsEnabled) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_backup_alerts),
                subtitle = "Summary notifications when automatic backup finishes or hits errors",
                checked = prefsState.backupNotifications,
                onChange = { scope.launch { UserPreferences.setBackupNotifications(context, it) } },
                modifier = Modifier.testTag("notif_backup_switch")
            )
        },
        GroupedListItem(key = "progress_alerts", visible = prefsState.notificationsEnabled) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_progress_alerts),
                subtitle = "Ongoing foreground transfer notifications during active uploads/downloads",
                checked = prefsState.progressAlerts,
                onChange = { scope.launch { UserPreferences.setProgressAlerts(context, it) } },
                modifier = Modifier.testTag("notif_progress_switch")
            )
        },
        GroupedListItem(key = "completed_alerts", visible = prefsState.notificationsEnabled) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_completed_alerts),
                subtitle = "Notify when queued backups or transfers complete",
                checked = prefsState.completedAlerts,
                onChange = { scope.launch { UserPreferences.setCompletedAlerts(context, it) } },
                modifier = Modifier.testTag("notif_completed_switch")
            )
        },
        GroupedListItem(key = "failure_alerts", visible = prefsState.notificationsEnabled) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_failure_alerts),
                subtitle = "Alert when backups or transfers fail permanently or lose folder access",
                checked = prefsState.failureNotifications,
                onChange = { scope.launch { UserPreferences.setFailureNotifications(context, it) } },
                modifier = Modifier.testTag("notif_failure_switch")
            )
        },
        GroupedListItem(key = "system_channels") {
            SettingsClickRow(
                title = stringResource(R.string.settings_system_channel_settings),
                subtitle = stringResource(R.string.settings_system_channel_settings_sub),
                onClick = {
                    runCatching {
                        context.startActivity(NotificationHelper.buildAppNotificationSettingsIntent(context))
                    }.onFailure { err ->
                        scope.launch {
                            snackbarHostState.showSnackbar(err.message ?: "Unable to open system notification settings")
                        }
                    }
                },
                modifier = Modifier.testTag("notif_system_channels_row")
            )
        }
    )
    GroupedList(items = notifItems)
}

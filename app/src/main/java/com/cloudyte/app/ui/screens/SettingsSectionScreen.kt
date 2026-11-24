package com.cloudyte.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.EnhancedEncryption
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cloudyte.app.BuildConfig
import com.cloudyte.app.R
import com.cloudyte.app.api.Account
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.UserProfile
import com.cloudyte.app.data.ColorTheme
import com.cloudyte.app.data.ThemeMode
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.UserPreferencesState
import com.cloudyte.app.data.ViewMode
import com.cloudyte.app.data.formatStorageSize
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.LocalAccount
import com.cloudyte.app.sync.AutoSyncManager
import com.cloudyte.app.ui.components.ActiveSessionsBottomSheet
import com.cloudyte.app.ui.components.ChoiceDialog
import com.cloudyte.app.ui.components.ConfirmDialog
import com.cloudyte.app.ui.components.GroupedList
import com.cloudyte.app.ui.components.GroupedListItem
import com.cloudyte.app.ui.components.LocalBottomBarInset
import com.cloudyte.app.ui.components.SettingsClickRow
import com.cloudyte.app.ui.components.SettingsSectionTitle
import com.cloudyte.app.ui.components.SettingsSliderRow
import com.cloudyte.app.ui.components.SettingsSwitchRow
import com.cloudyte.app.ui.screens.settings.BackupSettingsSection
import com.cloudyte.app.ui.screens.settings.NotificationSettingsSection
import com.cloudyte.app.ui.screens.settings.PermissionsSection
import com.cloudyte.app.ui.screens.settings.StorageSettingsSection
import com.cloudyte.app.ui.screens.settings.SyncLogsDialog
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSectionScreen(
    section: SettingsSectionType,
    onBack: () -> Unit,
    onNavigateExclusions: () -> Unit = {},
    onNavigateAccounts: () -> Unit = {},
    onNavigateBackup: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsState by UserPreferences.observe(context).collectAsState(initial = UserPreferencesState())
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    var showConfirmLogout by remember { mutableStateOf(false) }
    var showConfirmResetSettings by remember { mutableStateOf(false) }
    var showSyncLogsDialog by remember { mutableStateOf(false) }
    var showActiveSessionsSheet by remember { mutableStateOf(false) }
    var activeDialogType by remember { mutableStateOf<String?>(null) }

    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }

    LaunchedEffect(section) {
        if (section == SettingsSectionType.ACCOUNT) {
            val db = AppDatabase.getDatabase(context)
            runCatching {
                val cached = db.accountDao().getAllAccounts()
                if (cached.isNotEmpty()) accounts = cached.map { it.toAccount() }
            }
            runCatching { profile = ApiClient.apiService.getProfile() }
            runCatching {
                val fetched = ApiClient.apiService.getAccounts()
                if (fetched.isNotEmpty()) {
                    accounts = fetched
                    db.accountDao().insertAccounts(fetched.map { LocalAccount.fromAccount(it) })
                }
            }
        }
    }

    val isScrolled = scrollState.value > 0
    val topBarContainerColor = if (isScrolled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(section.titleRes), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_section_back")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarContainerColor)
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = LocalBottomBarInset.current)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
        ) {
            when (section) {
                SettingsSectionType.ACCOUNT -> {
                    val username = profile?.displayUsername ?: "User"
                    val userId = profile?.id ?: profile?._id ?: "Active Session"
                    val email = profile?.email?.takeIf { it.isNotBlank() } ?: "Not set"
                    val roleLabel = if (profile?.isAdmin == true) "Administrator" else "Standard User"
                    val usedTotal = accounts.sumOf { it.usageBytes.coerceAtLeast(0L) }
                    val finiteTotals = accounts.map { it.totalBytes }.filter { it > 0L }
                    val hasUnlimited = accounts.any {
                        it.totalBytes <= 0L ||
                            it.effectiveType.contains("tg", ignoreCase = true) ||
                            it.effectiveType.contains("tele", ignoreCase = true)
                    }
                    val totalCap = if (hasUnlimited || finiteTotals.isEmpty()) 0L else finiteTotals.sum()
                    val quotaText = if (totalCap > 0L) {
                        "${formatStorageSize(usedTotal)} of ${formatStorageSize(totalCap)} used"
                    } else {
                        "${formatStorageSize(usedTotal)} used • Unlimited"
                    }
                    val progressFraction = if (totalCap > 0L) (usedTotal.toFloat() / totalCap.toFloat()).coerceIn(0f, 1f) else null
                    var drivesExpanded by remember { mutableStateOf(false) }

                    SettingsSectionTitle("User Profile")
                    val profileItems = buildList {
                        add(GroupedListItem(key = "username") { SettingsClickRow(title = "Username", subtitle = username, icon = Icons.Outlined.Person, onClick = {}) })
                        add(GroupedListItem(key = "account_role") { SettingsClickRow(title = "Account Type", subtitle = roleLabel, icon = Icons.Outlined.Badge, onClick = {}) })
                        add(
                            GroupedListItem(key = "user_id") {
                                SettingsClickRow(
                                    title = "Account ID",
                                    subtitle = userId,
                                    icon = Icons.Outlined.Fingerprint,
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Account ID", userId))
                                        Toast.makeText(context, "Account ID copied", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        )
                        if (email != "Not set") {
                            add(GroupedListItem(key = "email") { SettingsClickRow(title = "Email", subtitle = email, icon = Icons.Outlined.Email, onClick = {}) })
                        }
                        add(
                            GroupedListItem(key = "server_url") {
                                SettingsClickRow(
                                    title = stringResource(R.string.settings_server_url),
                                    subtitle = ApiClient.API_BASE_URL,
                                    icon = Icons.Outlined.Dns,
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Server URL", ApiClient.API_BASE_URL))
                                        Toast.makeText(context, context.getString(R.string.settings_server_copied), Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        )
                    }
                    GroupedList(items = profileItems)

                    SettingsSectionTitle("Storage & Linked Drives")
                    val driveItems = buildList {
                        add(
                            GroupedListItem(key = "storage_quota") {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_storage_quota),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (progressFraction == null) {
                                            Surface(
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    text = "Unlimited",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = quotaText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (progressFraction != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LinearProgressIndicator(progress = { progressFraction }, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        )
                        add(
                            GroupedListItem(key = "drives_dropdown_toggle") {
                                SettingsClickRow(
                                    title = "Linked Storage Drives (${accounts.size})",
                                    subtitle = if (drivesExpanded) "Tap to collapse connected drives" else "Tap to view connected drives",
                                    icon = Icons.Outlined.CloudQueue,
                                    trailing = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = if (drivesExpanded) "Hide" else "Show",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Icon(
                                                imageVector = if (drivesExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                                contentDescription = if (drivesExpanded) "Collapse drives" else "Expand drives",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = { drivesExpanded = !drivesExpanded }
                                )
                            }
                        )
                        if (drivesExpanded) {
                            accounts.forEach { acc ->
                                val accSub = buildString {
                                    append(acc.effectiveType)
                                    val detail = acc.email?.takeIf { it.isNotBlank() }
                                        ?: acc.channelId?.takeIf { it.isNotBlank() }?.let { "Channel: $it" }
                                    if (!detail.isNullOrBlank()) append(" • ").append(detail)
                                    append(" • ").append(formatStorageSize(acc.usageBytes))
                                    if (acc.isDefault == true) append(" (Default)")
                                }
                                add(GroupedListItem(key = "drive_${acc.effectiveId}") { SettingsClickRow(title = acc.effectiveName, subtitle = accSub, icon = Icons.Outlined.Cloud, onClick = onNavigateAccounts) })
                            }
                            add(
                                GroupedListItem(key = "manage_drives") {
                                    SettingsClickRow(
                                        title = "Manage / Add Storage Drives",
                                        subtitle = "Connect Telegram, Google Drive, or OneDrive",
                                        icon = Icons.Outlined.AddCircleOutline,
                                        titleColor = MaterialTheme.colorScheme.primary,
                                        onClick = onNavigateAccounts
                                    )
                                }
                            )
                        }
                        add(
                            GroupedListItem(key = "active_sessions_account") {
                                SettingsClickRow(
                                    title = "Active Devices & Sessions",
                                    subtitle = "View signed-in devices or sign out of other sessions",
                                    icon = Icons.Outlined.Devices,
                                    onClick = { showActiveSessionsSheet = true },
                                    modifier = Modifier.testTag("settings_active_sessions_account_row")
                                )
                            }
                        )
                        add(
                            GroupedListItem(key = "logout") {
                                SettingsClickRow(
                                    title = stringResource(R.string.settings_logout),
                                    icon = Icons.AutoMirrored.Outlined.Logout,
                                    titleColor = MaterialTheme.colorScheme.error,
                                    onClick = { showConfirmLogout = true },
                                    modifier = Modifier.testTag("settings_logout_row")
                                )
                            }
                        )
                    }
                    GroupedList(items = driveItems)
                }

                SettingsSectionType.BACKUP -> {
                    BackupSettingsSection(
                        prefsState = prefsState,
                        snackbarHostState = snackbarHostState,
                        onNavigateExclusions = onNavigateExclusions,
                        onOpenSyncLogs = { showSyncLogsDialog = true }
                    )
                }

                SettingsSectionType.STORAGE -> {
                    StorageSettingsSection(
                        prefsState = prefsState,
                        snackbarHostState = snackbarHostState
                    )
                }

                SettingsSectionType.PERMISSIONS -> {
                    PermissionsSection(
                        snackbarHostState = snackbarHostState,
                        onOpenBackupFolders = onNavigateBackup
                    )
                }

                SettingsSectionType.SECURITY -> {
                    val settingsVm: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    val keyStatus by settingsVm.encryptionKeyStatus.collectAsState()
                    val backupStatus by settingsVm.recoveryBackupStatus.collectAsState()
                    val generatedBackupJson by settingsVm.generatedRecoveryBackupJson.collectAsState()
                    val securityFeedback by settingsVm.securityFeedback.collectAsState()

                    var showCreateBackupDialog by remember { mutableStateOf(false) }
                    var showRestoreBackupDialog by remember { mutableStateOf(false) }
                    var createPassphrase by remember { mutableStateOf("") }
                    var confirmPassphrase by remember { mutableStateOf("") }
                    var restorePassphrase by remember { mutableStateOf("") }
                    var restoreBackupJson by remember { mutableStateOf("") }

                    LaunchedEffect(Unit) { settingsVm.refreshKeyStatus() }
                    LaunchedEffect(securityFeedback) {
                        securityFeedback?.let {
                            snackbarHostState.showSnackbar(it)
                            settingsVm.clearSecurityFeedback()
                        }
                    }

                    if (showCreateBackupDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                showCreateBackupDialog = false
                                createPassphrase = ""
                                confirmPassphrase = ""
                            },
                            title = { Text(stringResource(R.string.settings_create_recovery_backup)) },
                            text = {
                                Column {
                                    Text(
                                        text = "Enter a strong passphrase to encrypt your recovery key backup. The server never receives this passphrase.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    androidx.compose.material3.OutlinedTextField(
                                        value = createPassphrase,
                                        onValueChange = { createPassphrase = it },
                                        label = { Text("Recovery passphrase") },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    androidx.compose.material3.OutlinedTextField(
                                        value = confirmPassphrase,
                                        onValueChange = { confirmPassphrase = it },
                                        label = { Text("Confirm passphrase") },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                androidx.compose.material3.Button(
                                    onClick = {
                                        val p1 = createPassphrase
                                        val p2 = confirmPassphrase
                                        createPassphrase = ""
                                        confirmPassphrase = ""
                                        showCreateBackupDialog = false
                                        settingsVm.createRecoveryBackup(p1, p2)
                                    }
                                ) { Text(stringResource(R.string.action_create)) }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showCreateBackupDialog = false
                                        createPassphrase = ""
                                        confirmPassphrase = ""
                                    }
                                ) { Text(stringResource(R.string.action_cancel)) }
                            }
                        )
                    }

                    if (generatedBackupJson != null) {
                        AlertDialog(
                            onDismissRequest = { settingsVm.clearGeneratedRecoveryBackup() },
                            title = { Text("Recovery Key Backup Ready") },
                            text = {
                                Column {
                                    Text(
                                        text = "Copy and store this encrypted key backup in a safe place (e.g., password manager).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    androidx.compose.material3.OutlinedTextField(
                                        value = generatedBackupJson ?: "",
                                        onValueChange = {},
                                        readOnly = true,
                                        maxLines = 6,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                androidx.compose.material3.Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Cloudyte Key Backup", generatedBackupJson))
                                        Toast.makeText(context, "Recovery backup copied to clipboard", Toast.LENGTH_SHORT).show()
                                        settingsVm.clearGeneratedRecoveryBackup()
                                    }
                                ) { Text("Copy Backup") }
                            },
                            dismissButton = {
                                TextButton(onClick = { settingsVm.clearGeneratedRecoveryBackup() }) { Text(stringResource(R.string.action_close)) }
                            }
                        )
                    }

                    if (showRestoreBackupDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                showRestoreBackupDialog = false
                                restorePassphrase = ""
                                restoreBackupJson = ""
                            },
                            title = { Text(stringResource(R.string.settings_restore_recovery_backup)) },
                            text = {
                                Column {
                                    Text(
                                        text = "Paste your recovery backup JSON and enter the recovery passphrase to restore your encryption key.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    androidx.compose.material3.OutlinedTextField(
                                        value = restorePassphrase,
                                        onValueChange = { restorePassphrase = it },
                                        label = { Text("Recovery passphrase") },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    androidx.compose.material3.OutlinedTextField(
                                        value = restoreBackupJson,
                                        onValueChange = { restoreBackupJson = it },
                                        label = { Text("Recovery backup JSON") },
                                        maxLines = 5,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                androidx.compose.material3.Button(
                                    onClick = {
                                        val pass = restorePassphrase
                                        val json = restoreBackupJson
                                        restorePassphrase = ""
                                        restoreBackupJson = ""
                                        showRestoreBackupDialog = false
                                        settingsVm.restoreRecoveryBackup(pass, json)
                                    }
                                ) { Text("Restore") }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showRestoreBackupDialog = false
                                        restorePassphrase = ""
                                        restoreBackupJson = ""
                                    }
                                ) { Text(stringResource(R.string.action_cancel)) }
                            }
                        )
                    }

                    SettingsSectionTitle(stringResource(R.string.settings_section_security))
                    val timeouts = stringArrayResource(R.array.lock_timeout_options)
                    val secItems = listOf(
                        GroupedListItem(key = "app_lock") {
                            SettingsSwitchRow(
                                title = stringResource(R.string.settings_app_lock),
                                icon = Icons.Outlined.Lock,
                                checked = prefsState.appLockEnabled,
                                onChange = { settingsVm.setAppLockEnabled(it) }
                            )
                        },
                        GroupedListItem(key = "lock_timeout", visible = prefsState.appLockEnabled) {
                            SettingsClickRow(
                                title = stringResource(R.string.settings_lock_timeout),
                                subtitle = when (prefsState.autoLockMinutes) {
                                    0 -> timeouts[0]
                                    1 -> timeouts[1]
                                    5 -> timeouts[2]
                                    else -> timeouts[3]
                                },
                                icon = Icons.Outlined.Timer,
                                onClick = { activeDialogType = "LOCK_TIMEOUT" }
                            )
                        },
                        GroupedListItem(key = "block_screenshots") {
                            SettingsSwitchRow(
                                title = stringResource(R.string.settings_block_screenshots),
                                subtitle = stringResource(R.string.settings_block_screenshots_sub),
                                icon = Icons.Outlined.VisibilityOff,
                                checked = prefsState.blockScreenshots,
                                onChange = { scope.launch { UserPreferences.setBlockScreenshots(context, it) } }
                            )
                        },
                        GroupedListItem(key = "active_sessions_security") {
                            SettingsClickRow(
                                title = "Active Devices & Sessions",
                                subtitle = "Manage signed-in devices and revoke sessions",
                                icon = Icons.Outlined.Devices,
                                onClick = { showActiveSessionsSheet = true },
                                modifier = Modifier.testTag("settings_active_sessions_security_row")
                            )
                        }
                    )
                    GroupedList(items = secItems)

                    SettingsSectionTitle("End-to-End File Encryption")
                    val encryptionStatusSubtitle = buildString {
                        if (prefsState.fullPrivacyMode) append("Full privacy mode active")
                        else if (prefsState.encryptNewUploads || prefsState.encryptOfflineFiles) append("Enabled")
                        else append("Disabled")
                        append(" • Key: ").append(keyStatus)
                    }
                    val encryptionItems = listOf(
                        GroupedListItem(key = "encrypt_new_uploads") {
                            SettingsSwitchRow(
                                title = stringResource(R.string.settings_encrypt_new_uploads),
                                subtitle = stringResource(R.string.settings_encrypt_new_uploads_sub),
                                icon = Icons.Outlined.EnhancedEncryption,
                                checked = prefsState.isUploadEncryptionEnabled,
                                onChange = { settingsVm.setEncryptNewUploads(it) }
                            )
                        },
                        GroupedListItem(key = "encrypt_offline_files") {
                            SettingsSwitchRow(
                                title = stringResource(R.string.settings_encrypt_offline_files),
                                subtitle = stringResource(R.string.settings_encrypt_offline_files_sub),
                                icon = Icons.Outlined.OfflinePin,
                                checked = prefsState.isOfflineEncryptionEnabled,
                                onChange = { settingsVm.setEncryptOfflineFiles(it) }
                            )
                        },
                        GroupedListItem(key = "full_privacy_mode") {
                            SettingsSwitchRow(
                                title = stringResource(R.string.settings_full_privacy_mode),
                                subtitle = stringResource(R.string.settings_full_privacy_mode_sub),
                                icon = Icons.Outlined.PrivacyTip,
                                checked = prefsState.fullPrivacyMode,
                                onChange = { settingsVm.setFullPrivacyMode(it) }
                            )
                        },
                        GroupedListItem(key = "encryption_status") {
                            SettingsClickRow(
                                title = stringResource(R.string.settings_encryption_status),
                                subtitle = encryptionStatusSubtitle,
                                icon = Icons.Outlined.VerifiedUser,
                                onClick = { settingsVm.refreshKeyStatus() }
                            )
                        },
                        GroupedListItem(key = "recovery_backup_status") {
                            SettingsClickRow(
                                title = stringResource(R.string.settings_recovery_backup_status),
                                subtitle = backupStatus,
                                icon = Icons.Outlined.Key,
                                onClick = {}
                            )
                        },
                        GroupedListItem(key = "create_recovery_backup") {
                            SettingsClickRow(
                                title = stringResource(R.string.settings_create_recovery_backup),
                                subtitle = stringResource(R.string.settings_create_recovery_backup_sub),
                                icon = Icons.Outlined.Backup,
                                onClick = { showCreateBackupDialog = true }
                            )
                        },
                        GroupedListItem(key = "restore_recovery_backup") {
                            SettingsClickRow(
                                title = stringResource(R.string.settings_restore_recovery_backup),
                                subtitle = stringResource(R.string.settings_restore_recovery_backup_sub),
                                icon = Icons.Outlined.Restore,
                                onClick = { showRestoreBackupDialog = true }
                            )
                        }
                    )
                    GroupedList(items = encryptionItems)
                }

                SettingsSectionType.APPEARANCE -> {
                    SettingsSectionTitle(stringResource(R.string.settings_section_appearance))
                    val themes = stringArrayResource(R.array.theme_options)
                    val viewModes = stringArrayResource(R.array.view_mode_options)
                    val densities = stringArrayResource(R.array.density_options)
                    val colorThemes = stringArrayResource(R.array.color_theme_options)

                    val appItems = listOf(
                        GroupedListItem(key = "theme") {
                            SettingsClickRow(
                                title = stringResource(R.string.settings_theme_mode),
                                subtitle = when (prefsState.themeMode) {
                                    ThemeMode.SYSTEM -> themes[0]
                                    ThemeMode.LIGHT -> themes[1]
                                    ThemeMode.DARK -> themes[2]
                                },
                                icon = Icons.Outlined.DarkMode,
                                onClick = { activeDialogType = "THEME" }
                            )
                        },
                        GroupedListItem(key = "color_theme") {
                            SettingsClickRow(
                                title = stringResource(R.string.settings_color_theme),
                                subtitle = when {
                                    prefsState.dynamicColor -> stringResource(R.string.settings_color_theme_dynamic_note)
                                    prefsState.colorTheme == ColorTheme.NOIR -> colorThemes[1]
                                    else -> colorThemes[0]
                                },
                                icon = Icons.Outlined.Palette,
                                onClick = { activeDialogType = "COLOR_THEME" }
                            )
                        },
                        GroupedListItem(key = "dynamic_color") {
                            SettingsSwitchRow(
                                title = stringResource(R.string.settings_dynamic_color),
                                icon = Icons.Outlined.AutoAwesome,
                                checked = prefsState.dynamicColor,
                                onChange = { scope.launch { UserPreferences.setDynamicColor(context, it) } }
                            )
                        },
                        GroupedListItem(key = "view_mode") {
                            SettingsClickRow(
                                title = stringResource(R.string.settings_view_mode),
                                subtitle = when (prefsState.viewMode) {
                                    ViewMode.GRID -> viewModes[0]
                                    ViewMode.LIST -> viewModes[1]
                                    ViewMode.COMPACT_LIST -> viewModes[2]
                                },
                                icon = Icons.Outlined.GridView,
                                onClick = { activeDialogType = "VIEW_MODE" }
                            )
                        },
                        GroupedListItem(key = "grid_columns", visible = prefsState.viewMode == ViewMode.GRID) {
                            SettingsSliderRow(
                                title = stringResource(R.string.settings_grid_columns),
                                value = prefsState.gridColumns.toFloat(),
                                range = 2f..5f,
                                steps = 2,
                                onChange = { scope.launch { UserPreferences.setGridColumns(context, it.toInt()) } },
                                valueLabel = "${prefsState.gridColumns}",
                                icon = Icons.Outlined.ViewColumn
                            )
                        },
                        GroupedListItem(key = "density") {
                            SettingsClickRow(
                                title = stringResource(R.string.settings_layout_density),
                                subtitle = if (prefsState.layoutDensity == "COMFORTABLE") densities[0] else densities[1],
                                icon = Icons.Outlined.ViewAgenda,
                                onClick = { activeDialogType = "DENSITY" }
                            )
                        },
                        GroupedListItem(key = "recent_files") {
                            SettingsSwitchRow(
                                title = stringResource(R.string.settings_show_recent_files),
                                icon = Icons.Outlined.History,
                                checked = prefsState.showRecentFiles,
                                onChange = { scope.launch { UserPreferences.setShowRecentFiles(context, it) } }
                            )
                        }
                    )
                    GroupedList(items = appItems)
                }

                SettingsSectionType.PLAYBACK -> {
                    SettingsSectionTitle(stringResource(R.string.settings_section_playback))
                    val playItems = listOf(
                        GroupedListItem(key = "stream_before_download") {
                            SettingsSwitchRow(
                                title = stringResource(R.string.settings_stream_before_download),
                                subtitle = stringResource(R.string.settings_stream_before_download_sub),
                                icon = Icons.Outlined.PlayCircle,
                                checked = prefsState.streamBeforeDownload,
                                onChange = { scope.launch { UserPreferences.setStreamBeforeDownload(context, it) } },
                                modifier = Modifier.testTag("playback_stream_switch")
                            )
                        },
                        GroupedListItem(key = "background_audio") {
                            SettingsSwitchRow(
                                title = stringResource(R.string.settings_background_audio),
                                subtitle = stringResource(R.string.settings_background_audio_sub),
                                icon = Icons.Outlined.Headphones,
                                checked = prefsState.backgroundPlayback,
                                onChange = { scope.launch { UserPreferences.setBackgroundPlayback(context, it) } },
                                modifier = Modifier.testTag("playback_background_switch")
                            )
                        },
                        GroupedListItem(key = "auto_play_video") {
                            SettingsSwitchRow(
                                title = stringResource(R.string.settings_auto_play_video),
                                subtitle = stringResource(R.string.settings_auto_play_video_sub),
                                icon = Icons.Outlined.SmartDisplay,
                                checked = prefsState.autoPlayVideo,
                                onChange = { scope.launch { UserPreferences.setAutoPlayVideo(context, it) } },
                                modifier = Modifier.testTag("playback_autoplay_switch")
                            )
                        }
                    )
                    GroupedList(items = playItems)
                }

                SettingsSectionType.NOTIFICATIONS -> {
                    NotificationSettingsSection(
                        prefsState = prefsState,
                        snackbarHostState = snackbarHostState
                    )
                }

                SettingsSectionType.ADVANCED -> {
                    SettingsSectionTitle(stringResource(R.string.settings_section_advanced))
                    val retryCount = prefsState.transferRetryCount
                    val advItems = listOf(
                        GroupedListItem(key = "retries") {
                            SettingsSliderRow(
                                title = stringResource(R.string.settings_max_retries),
                                value = retryCount.toFloat(),
                                range = 0f..10f,
                                steps = 9,
                                onChange = { raw ->
                                    scope.launch { UserPreferences.setTransferRetryCount(context, raw.roundToInt()) }
                                },
                                valueLabel = if (retryCount == 0) "0 (Off)" else "$retryCount",
                                icon = Icons.Outlined.Replay,
                                modifier = Modifier.testTag("advanced_retries_slider")
                            )
                        },
                        GroupedListItem(key = "metered") {
                            SettingsSwitchRow(
                                title = stringResource(R.string.settings_metered_transfers),
                                subtitle = "Allow active transfers over metered cellular or hotspot connections",
                                icon = Icons.Outlined.SignalCellularAlt,
                                checked = prefsState.allowMeteredTransfers,
                                onChange = { scope.launch { UserPreferences.setAllowMeteredTransfers(context, it) } },
                                modifier = Modifier.testTag("advanced_metered_switch")
                            )
                        },
                        GroupedListItem(key = "roaming") {
                            SettingsSwitchRow(
                                title = stringResource(R.string.settings_roaming_transfers),
                                subtitle = stringResource(R.string.settings_roaming_transfers_sub),
                                icon = Icons.Outlined.Public,
                                checked = prefsState.allowRoaming,
                                onChange = { scope.launch { UserPreferences.setAllowRoaming(context, it) } },
                                modifier = Modifier.testTag("advanced_roaming_switch")
                            )
                        },
                        GroupedListItem(key = "debug_logging") {
                            SettingsSwitchRow(
                                title = stringResource(R.string.settings_debug_logging),
                                subtitle = "Include redacted verbose sync and transfer diagnostics in logs",
                                icon = Icons.Outlined.BugReport,
                                checked = prefsState.debugLogging,
                                onChange = { scope.launch { UserPreferences.setDebugLogging(context, it) } },
                                modifier = Modifier.testTag("advanced_debug_logging_switch")
                            )
                        },
                        GroupedListItem(key = "export_logs") {
                            SettingsClickRow(
                                title = stringResource(R.string.settings_export_logs),
                                subtitle = "View, copy, clear, or export redacted sync & transfer logs",
                                icon = Icons.Outlined.Description,
                                onClick = { showSyncLogsDialog = true },
                                modifier = Modifier.testTag("advanced_export_logs_row")
                            )
                        },
                        GroupedListItem(key = "reset_all") {
                            SettingsClickRow(
                                title = stringResource(R.string.settings_reset_all),
                                icon = Icons.Outlined.RestartAlt,
                                titleColor = MaterialTheme.colorScheme.error,
                                onClick = { showConfirmResetSettings = true },
                                modifier = Modifier.testTag("advanced_reset_all_row")
                            )
                        }
                    )
                    GroupedList(items = advItems)
                }

                SettingsSectionType.ABOUT -> {
                    SettingsSectionTitle(stringResource(R.string.settings_section_about))
                    val aboutItems = listOf(
                        GroupedListItem(key = "version") {
                            SettingsClickRow(
                                title = stringResource(R.string.settings_app_version),
                                subtitle = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                icon = Icons.Outlined.Info,
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("App Version", "Cloudyte v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                                    )
                                    Toast.makeText(context, "Version copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        GroupedListItem(key = "tos") {
                            SettingsClickRow(
                                title = stringResource(R.string.settings_terms_of_service),
                                icon = Icons.Outlined.Gavel,
                                onClick = { activeDialogType = "TOS_INFO" }
                            )
                        },
                        GroupedListItem(key = "privacy") {
                            SettingsClickRow(
                                title = stringResource(R.string.settings_privacy_policy),
                                icon = Icons.Outlined.Policy,
                                onClick = { activeDialogType = "PRIVACY_INFO" }
                            )
                        },
                        GroupedListItem(key = "licenses") {
                            SettingsClickRow(
                                title = stringResource(R.string.settings_open_source_licenses),
                                icon = Icons.Outlined.Code,
                                onClick = { activeDialogType = "LICENSES_INFO" }
                            )
                        }
                    )
                    GroupedList(items = aboutItems)
                }
            }
            Spacer(modifier = Modifier.height(24.dp + LocalBottomBarInset.current))
        }
    }

    if (showSyncLogsDialog) {
        SyncLogsDialog(
            snackbarHostState = snackbarHostState,
            onDismiss = { showSyncLogsDialog = false }
        )
    }

    if (showActiveSessionsSheet) {
        ActiveSessionsBottomSheet(
            onDismiss = { showActiveSessionsSheet = false },
            onFeedback = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }

    when (activeDialogType) {
        "TOS_INFO" -> {
            AlertDialog(
                onDismissRequest = { activeDialogType = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text(stringResource(R.string.settings_terms_of_service), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "Cloudyte is a client for managing your own connected cloud storage providers and self-hosted Cloudyte server. You are responsible for the credentials, keys, and content stored on your linked drives.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = { TextButton(onClick = { activeDialogType = null }) { Text(stringResource(R.string.action_close)) } }
            )
        }
        "PRIVACY_INFO" -> {
            AlertDialog(
                onDismissRequest = { activeDialogType = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text(stringResource(R.string.settings_privacy_policy), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "End-to-end encryption keys are stored locally in Android Keystore and never transmitted to the server. Redacted diagnostics are only exported when you explicitly choose to copy or share them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = { TextButton(onClick = { activeDialogType = null }) { Text(stringResource(R.string.action_close)) } }
            )
        }
        "LICENSES_INFO" -> {
            AlertDialog(
                onDismissRequest = { activeDialogType = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text(stringResource(R.string.settings_open_source_licenses), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "Built with open-source software:\n• Jetpack Compose & Material 3 (Apache-2.0)\n• AndroidX Room, WorkManager & Security (Apache-2.0)\n• Retrofit & OkHttp (Apache-2.0)\n• AndroidX Media3 ExoPlayer & MediaSession (Apache-2.0)\n• Coil Image Loading (Apache-2.0)\n• Kotlinx Serialization & Coroutines (Apache-2.0)\n• Roboto Flex & JetBrains Mono (OFL-1.1)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = { TextButton(onClick = { activeDialogType = null }) { Text(stringResource(R.string.action_close)) } }
            )
        }
        "LOCK_TIMEOUT" -> {
            val timeouts = stringArrayResource(R.array.lock_timeout_options)
            ChoiceDialog(
                title = stringResource(R.string.settings_lock_timeout),
                options = timeouts.toList(),
                selectedIndex = when (prefsState.autoLockMinutes) {
                    0 -> 0
                    1 -> 1
                    5 -> 2
                    else -> 3
                },
                onSelect = { idx ->
                    val mins = when (idx) {
                        0 -> 0
                        1 -> 1
                        2 -> 5
                        else -> 15
                    }
                    scope.launch { UserPreferences.setAutoLockMinutes(context, mins) }
                },
                onDismiss = { activeDialogType = null }
            )
        }
        "THEME" -> {
            val themes = stringArrayResource(R.array.theme_options)
            ChoiceDialog(
                title = stringResource(R.string.settings_theme_mode),
                options = themes.toList(),
                selectedIndex = when (prefsState.themeMode) {
                    ThemeMode.SYSTEM -> 0
                    ThemeMode.LIGHT -> 1
                    ThemeMode.DARK -> 2
                },
                onSelect = { idx ->
                    val mode = when (idx) {
                        0 -> ThemeMode.SYSTEM
                        1 -> ThemeMode.LIGHT
                        else -> ThemeMode.DARK
                    }
                    scope.launch { UserPreferences.setThemeMode(context, mode) }
                },
                onDismiss = { activeDialogType = null }
            )
        }
        "COLOR_THEME" -> {
            val colorThemes = stringArrayResource(R.array.color_theme_options)
            ChoiceDialog(
                title = stringResource(R.string.settings_color_theme),
                options = colorThemes.toList(),
                selectedIndex = if (prefsState.colorTheme == ColorTheme.NOIR) 1 else 0,
                onSelect = { idx ->
                    val theme = if (idx == 1) ColorTheme.NOIR else ColorTheme.DEFAULT
                    scope.launch {
                        UserPreferences.setColorTheme(context, theme)
                        val iconChanged = com.cloudyte.app.ui.theme.AppIconManager.syncWithColorTheme(context, theme)
                        if (iconChanged) {
                            snackbarHostState.showSnackbar(
                                if (theme == ColorTheme.NOIR) {
                                    "Noir theme & monochrome launcher icon enabled"
                                } else {
                                    "Default theme & launcher icon restored"
                                }
                            )
                        }
                    }
                },
                onDismiss = { activeDialogType = null }
            )
        }
        "VIEW_MODE" -> {
            val viewModes = stringArrayResource(R.array.view_mode_options)
            ChoiceDialog(
                title = stringResource(R.string.settings_view_mode),
                options = viewModes.toList(),
                selectedIndex = when (prefsState.viewMode) {
                    ViewMode.GRID -> 0
                    ViewMode.LIST -> 1
                    ViewMode.COMPACT_LIST -> 2
                },
                onSelect = { idx ->
                    val mode = when (idx) {
                        0 -> ViewMode.GRID
                        1 -> ViewMode.LIST
                        else -> ViewMode.COMPACT_LIST
                    }
                    scope.launch { UserPreferences.setViewMode(context, mode) }
                },
                onDismiss = { activeDialogType = null }
            )
        }
        "DENSITY" -> {
            val densities = stringArrayResource(R.array.density_options)
            ChoiceDialog(
                title = stringResource(R.string.settings_layout_density),
                options = densities.toList(),
                selectedIndex = if (prefsState.layoutDensity == "COMFORTABLE") 0 else 1,
                onSelect = { scope.launch { UserPreferences.setLayoutDensity(context, if (it == 0) "COMFORTABLE" else "COMPACT") } },
                onDismiss = { activeDialogType = null }
            )
        }
    }

    if (showConfirmLogout) {
        ConfirmDialog(
            title = stringResource(R.string.settings_logout_confirm_title),
            message = stringResource(R.string.settings_logout_confirm_msg),
            confirmLabel = stringResource(R.string.settings_logout),
            onConfirm = { onLogout() },
            onDismiss = { showConfirmLogout = false }
        )
    }

    if (showConfirmResetSettings) {
        ConfirmDialog(
            title = stringResource(R.string.settings_reset_confirm_title),
            message = stringResource(R.string.settings_reset_confirm_msg),
            confirmLabel = stringResource(R.string.action_reset),
            onConfirm = {
                scope.launch {
                    UserPreferences.resetAllSettings(context)
                    AutoSyncManager.cancelScheduledWork(context)
                }
            },
            onDismiss = { showConfirmResetSettings = false }
        )
    }
}

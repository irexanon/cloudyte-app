package com.cloudyte.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cloudyte.app.api.Account
import com.cloudyte.app.sync.AppPermissionSummary
import com.cloudyte.app.sync.PermissionState
import com.cloudyte.app.sync.PermissionsHelper
import com.cloudyte.app.ui.theme.cloudyteColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnboardingSetupScreen(
    username: String?,
    accounts: List<Account>,
    isRefreshing: Boolean,
    onRefreshAccounts: () -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val ext = MaterialTheme.cloudyteColors
    val snackbarHostState = remember { SnackbarHostState() }

    // Step 0 = Link first cloud drive, Step 1 = Quick device setup
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    var activeProviderDialog by remember { mutableStateOf<CloudProviderType?>(null) }

    var permissionSummary by remember {
        mutableStateOf(
            AppPermissionSummary(
                hasFullStorageAccess = PermissionsHelper.isFullStorageAccessGranted(context),
                notificationsState = if (PermissionsHelper.isNotificationPermissionGranted(context)) {
                    PermissionState.GRANTED
                } else {
                    PermissionState.DENIED
                },
                runtimePostNotificationsGranted = PermissionsHelper.isRuntimePostNotificationsGranted(context),
                osNotificationsEnabled = true,
                ignoringBatteryOptimizations = PermissionsHelper.isIgnoringBatteryOptimizations(context),
                folderSources = emptyList(),
                healthyFolderCount = 0,
                brokenFolderCount = 0
            )
        )
    }

    suspend fun reloadPermissions(withDelayedRecheck: Boolean = false) {
        permissionSummary = PermissionsHelper.inspectAppPermissions(context)
        if (withDelayedRecheck) {
            delay(350L)
            permissionSummary = PermissionsHelper.inspectAppPermissions(context)
        }
    }

    LaunchedEffect(Unit) {
        reloadPermissions()
    }

    // Automatically advance to Step 2 once at least one drive is linked.
    LaunchedEffect(accounts.size) {
        if (accounts.isNotEmpty() && currentStep == 0) {
            currentStep = 1
        }
    }

    // Refresh accounts (in case user returned from browser OAuth) and permissions on resume.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshAccounts()
                scope.launch { reloadPermissions(withDelayedRecheck = true) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = currentStep > 0) {
        currentStep = 0
    }

    val settingsResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        scope.launch { reloadPermissions(withDelayedRecheck = true) }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        scope.launch { reloadPermissions(withDelayedRecheck = false) }
    }

    val legacyStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        scope.launch { reloadPermissions(withDelayedRecheck = false) }
    }

    if (activeProviderDialog != null) {
        AddAccountDialog(
            initialProvider = activeProviderDialog ?: CloudProviderType.GOOGLE_DRIVE,
            showChooserFirst = false,
            onDismiss = { activeProviderDialog = null },
            onSuccess = {
                activeProviderDialog = null
                onRefreshAccounts()
                scope.launch {
                    snackbarHostState.showSnackbar("Cloud drive linked!")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 20.dp)
            ) {
                // Top step bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = if (currentStep == 0) "Step 1 of 2 · Connect Drive" else "Step 2 of 2 · Device Setup",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("onboarding_skip_button")
                    ) {
                        Text(
                            text = if (accounts.isEmpty()) "Skip for now" else "Open Dashboard",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { if (currentStep == 0) 0.5f else 1f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "OnboardingStepContent",
                    modifier = Modifier.weight(1f)
                ) { step ->
                    if (step == 0) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Welcome hero
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cloud,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Text(
                                        text = if (!username.isNullOrBlank()) {
                                            "Welcome, $username"
                                        } else {
                                            "Welcome to Cloudyte"
                                        },
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "You don't have any drives linked yet. Connect your first cloud storage below to browse files, stream media, and enable auto-backups.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = "Choose a cloud provider",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )

                            OnboardingProviderCard(
                                title = "Google Drive",
                                subtitle = "Link via Google sign-in for photos, docs & backups",
                                icon = Icons.Default.Cloud,
                                iconTint = ext.providerGoogle,
                                iconBg = ext.providerGoogleBg,
                                onClick = { activeProviderDialog = CloudProviderType.GOOGLE_DRIVE },
                                modifier = Modifier.testTag("onboarding_connect_google")
                            )

                            OnboardingProviderCard(
                                title = "Microsoft OneDrive",
                                subtitle = "Link your personal or work OneDrive storage",
                                icon = Icons.Default.CloudQueue,
                                iconTint = ext.providerOneDrive,
                                iconBg = ext.providerOneDriveBg,
                                onClick = { activeProviderDialog = CloudProviderType.ONEDRIVE },
                                modifier = Modifier.testTag("onboarding_connect_onedrive")
                            )

                            OnboardingProviderCard(
                                title = "Telegram Cloud",
                                subtitle = "Unlimited private cloud storage via your Telegram channel",
                                icon = Icons.AutoMirrored.Filled.Send,
                                iconTint = ext.providerTelegram,
                                iconBg = ext.providerTelegramBg,
                                onClick = { activeProviderDialog = CloudProviderType.TELEGRAM },
                                modifier = Modifier.testTag("onboarding_connect_telegram")
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedButton(
                                onClick = onRefreshAccounts,
                                enabled = !isRefreshing,
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("onboarding_refresh_drives")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isRefreshing) "Checking linked drives…" else "I already linked a drive in browser",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    } else {
                        val fullStorageGranted = permissionSummary.hasFullStorageAccess
                        val notifGranted = permissionSummary.notificationsState == PermissionState.GRANTED
                        val batteryGranted = permissionSummary.ignoringBatteryOptimizations

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDone,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (accounts.isNotEmpty()) "Drive connected!" else "Device Setup",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (accounts.isNotEmpty()) {
                                                Text(
                                                    text = accounts.joinToString(", ") { it.effectiveName },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "Enable these optional permissions so auto-backups, direct downloads, and background transfers work reliably.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            OnboardingPermissionCard(
                                title = "Full storage access",
                                subtitle = if (fullStorageGranted) {
                                    "Allowed · Direct Downloads/Cloudyte & 1-tap folder backups"
                                } else {
                                    "Needed for auto-backup folders and public Downloads access"
                                },
                                icon = Icons.Default.FolderOpen,
                                granted = fullStorageGranted,
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        runCatching {
                                            settingsResultLauncher.launch(
                                                PermissionsHelper.buildAllFilesAccessSettingsIntent(
                                                    context,
                                                    forActivityResult = true
                                                )
                                            )
                                        }.onFailure {
                                            context.startActivity(PermissionsHelper.buildAllFilesAccessSettingsIntent(context))
                                        }
                                    } else {
                                        legacyStorageLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("onboarding_perm_storage")
                            )

                            OnboardingPermissionCard(
                                title = "Notifications",
                                subtitle = if (notifGranted) {
                                    "Allowed · Transfer & backup alerts active"
                                } else {
                                    "Show upload, download, and sync progress in status bar"
                                },
                                icon = Icons.Default.Notifications,
                                granted = notifGranted,
                                onClick = {
                                    if (!notifGranted &&
                                        !permissionSummary.runtimePostNotificationsGranted &&
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                    ) {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        context.startActivity(PermissionsHelper.buildNotificationSettingsIntent(context))
                                    }
                                },
                                modifier = Modifier.testTag("onboarding_perm_notifications")
                            )

                            OnboardingPermissionCard(
                                title = "Unrestricted battery use",
                                subtitle = if (batteryGranted) {
                                    "Allowed · Background uploads won't be paused"
                                } else {
                                    "Keeps large uploads and auto-backups running when screen is off"
                                },
                                icon = Icons.Default.BatteryChargingFull,
                                granted = batteryGranted,
                                onClick = {
                                    val intent = if (batteryGranted) {
                                        PermissionsHelper.buildBatteryOptimizationSettingsIntent(
                                            context,
                                            forActivityResult = true
                                        )
                                    } else {
                                        PermissionsHelper.buildRequestIgnoreBatteryOptimizationsIntent(
                                            context,
                                            forActivityResult = true
                                        )
                                    }
                                    runCatching {
                                        settingsResultLauncher.launch(intent)
                                    }.onFailure {
                                        context.startActivity(PermissionsHelper.buildBatteryOptimizationSettingsIntent(context))
                                    }
                                },
                                modifier = Modifier.testTag("onboarding_perm_battery")
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                // Bottom action area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep == 0) {
                        OutlinedButton(
                            onClick = { currentStep = 1 },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("onboarding_next_permissions")
                        ) {
                            Text("Next: Device Permissions", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = onFinish,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("onboarding_finish_button")
                        ) {
                            Text(
                                text = "Start using Cloudyte",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingProviderCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Connect $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OnboardingPermissionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    granted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (granted) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (granted) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (granted) "Allowed" else "Not allowed",
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

package com.cloudyte.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cloudyte.app.R
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.UserPreferencesState
import com.cloudyte.app.db.TransferStatus
import com.cloudyte.app.domain.model.Transfer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudyte.app.features.accounts.AccountsScreen as FeatureAccountsScreen
import com.cloudyte.app.features.files.FilesScreen as FeatureFilesScreen
import com.cloudyte.app.features.home.HomeScreen as FeatureHomeScreen
import com.cloudyte.app.features.settings.SettingsScreen as FeatureSettingsScreen
import com.cloudyte.app.features.transfers.TransfersScreen as FeatureTransfersScreen
import com.cloudyte.app.ui.components.FloatingNavItem
import com.cloudyte.app.ui.components.FloatingNavigationBar
import com.cloudyte.app.ui.components.LocalBottomBarInset
import com.cloudyte.app.ui.navigation.NavigationTransitions

fun NavController.navigateSingleTopRestore(route: String) {
    val startDestination = graph.findStartDestination().route ?: "home"
    navigate(route) {
        popUpTo(startDestination) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private val tabOrder = listOf("home", "files", "transfers", "settings", "accounts")

@Composable
fun MainScreen(rootNavController: NavController) {
    val context = LocalContext.current
    val bottomNavController = rememberNavController()
    val homeViewModel: HomeViewModel = viewModel()
    val filesViewModel: FilesViewModel = viewModel()
    val prefsState by UserPreferences.observe(context).collectAsState(initial = UserPreferencesState())
    val hasInitialContent by homeViewModel.hasInitialContent.collectAsStateWithLifecycle()
    val homeAccounts by homeViewModel.accounts.collectAsStateWithLifecycle()
    val isHomeRefreshing by homeViewModel.isLoading.collectAsStateWithLifecycle()
    val homeError by homeViewModel.errorMessage.collectAsStateWithLifecycle()
    val homeProfile by homeViewModel.profile.collectAsStateWithLifecycle()
    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    var isSettingsInSubScreen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    var enteredOnboarding by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var onboardingDismissed by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        homeViewModel.loadData(forceStorageRefresh = false)
    }

    androidx.compose.runtime.LaunchedEffect(hasInitialContent, isHomeRefreshing, homeError, homeAccounts.size, onboardingDismissed) {
        if (hasInitialContent && !isHomeRefreshing && homeError == null && homeAccounts.isEmpty() && !onboardingDismissed) {
            enteredOnboarding = true
        }
    }

    if (enteredOnboarding && !onboardingDismissed) {
        OnboardingSetupScreen(
            username = homeProfile?.username,
            accounts = homeAccounts,
            isRefreshing = isHomeRefreshing,
            onRefreshAccounts = {
                homeViewModel.loadData(forceStorageRefresh = true)
                filesViewModel.refresh(force = true)
            },
            onFinish = {
                onboardingDismissed = true
                enteredOnboarding = false
                homeViewModel.loadData(forceStorageRefresh = true)
                filesViewModel.refresh(force = true)
            },
            onSkip = {
                onboardingDismissed = true
                enteredOnboarding = false
            }
        )
        return
    }

    val app = context.applicationContext as? com.cloudyte.app.CloudyteApplication
    val activeTransfers by remember(app) {
        app?.container?.transferCoordinator?.observeActiveTransfers()
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val activeTransferSummary = remember(activeTransfers) {
        val runningOrPending = activeTransfers.filter {
            it.status == TransferStatus.RUNNING.name ||
                it.status == TransferStatus.PENDING.name
        }
        if (runningOrPending.isEmpty()) {
            null
        } else {
            val hasUpload = runningOrPending.any { it.direction.equals("UPLOAD", ignoreCase = true) }
            val hasDownload = runningOrPending.any { !it.direction.equals("UPLOAD", ignoreCase = true) }
            val direction = when {
                hasUpload && hasDownload -> com.cloudyte.app.ui.components.ActiveTransferDirection.BOTH
                hasDownload -> com.cloudyte.app.ui.components.ActiveTransferDirection.DOWNLOAD
                else -> com.cloudyte.app.ui.components.ActiveTransferDirection.UPLOAD
            }
            val totalBytesSum = runningOrPending.sumOf { it.totalBytes.coerceAtLeast(0L) }
            val transferredBytesSum = runningOrPending.sumOf {
                it.bytesTransferred.coerceIn(0L, it.totalBytes.coerceAtLeast(it.bytesTransferred).coerceAtLeast(0L))
            }
            val progress = if (totalBytesSum > 0L) {
                (transferredBytesSum.toFloat() / totalBytesSum.toFloat()).coerceIn(0f, 1f)
            } else {
                val primary = runningOrPending.first()
                if (primary.totalBytes > 0L) {
                    (primary.bytesTransferred.toFloat() / primary.totalBytes.toFloat()).coerceIn(0f, 1f)
                } else {
                    0.05f
                }
            }
            com.cloudyte.app.ui.components.ActiveTransferSummary(
                direction = direction,
                progress = progress,
                activeCount = runningOrPending.size
            )
        }
    }

    val isHomeLoading = currentRoute == "home" && !hasInitialContent
    val hideBottomBar = isHomeLoading ||
        (currentRoute == "settings" && isSettingsInSubScreen) ||
        currentRoute == "accounts"

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        val useNavigationRail = maxWidth >= 600.dp
        val bottomInset = if (useNavigationRail || hideBottomBar) 0.dp else 90.dp

        CompositionLocalProvider(LocalBottomBarInset provides bottomInset) {
            if (useNavigationRail) {
                val railItems = listOf(
                    FloatingNavItem("home", stringResource(R.string.nav_home), Icons.Default.Home),
                    FloatingNavItem("files", stringResource(R.string.nav_files), Icons.Default.Folder),
                    FloatingNavItem("transfers", stringResource(R.string.nav_transfers), Icons.Default.SwapVert),
                    FloatingNavItem("settings", stringResource(R.string.nav_settings), Icons.Default.Settings)
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    if (!hideBottomBar) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Spacer(modifier = Modifier.weight(0.2f))
                            railItems.forEach { item ->
                                val isTransfersItem = item.route == "transfers"
                                val summary = if (isTransfersItem) activeTransferSummary else null
                                NavigationRailItem(
                                    selected = currentRoute == item.route,
                                    onClick = { bottomNavController.navigateSingleTopRestore(item.route) },
                                    icon = {
                                        if (summary != null) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                androidx.compose.material3.CircularProgressIndicator(
                                                    progress = { summary.progress.coerceIn(0.05f, 1f) },
                                                    modifier = Modifier.size(28.dp),
                                                    strokeWidth = 2.2.dp
                                                )
                                                Icon(
                                                    imageVector = when (summary.direction) {
                                                        com.cloudyte.app.ui.components.ActiveTransferDirection.UPLOAD -> Icons.Default.Upload
                                                        com.cloudyte.app.ui.components.ActiveTransferDirection.DOWNLOAD -> Icons.Default.Download
                                                        com.cloudyte.app.ui.components.ActiveTransferDirection.BOTH -> Icons.Default.SwapVert
                                                    },
                                                    contentDescription = item.label,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        } else {
                                            Icon(item.icon, contentDescription = item.label)
                                        }
                                    },
                                    label = if (prefsState.showNavText) {
                                        {
                                            if (summary != null) {
                                                val pct = (summary.progress * 100f).roundToInt().coerceIn(0, 100)
                                                Text("$pct%", style = com.cloudyte.app.ui.theme.MetricLabelTextStyle)
                                            } else {
                                                Text(item.label, style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    } else null,
                                    modifier = Modifier.testTag("nav_item_${item.route}")
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        MainNavHost(
                            bottomNavController = bottomNavController,
                            rootNavController = rootNavController,
                            homeViewModel = homeViewModel,
                            filesViewModel = filesViewModel,
                            onSettingsSubScreenChanged = { isSettingsInSubScreen = it },
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = 720.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainNavHost(
                        bottomNavController = bottomNavController,
                        rootNavController = rootNavController,
                        homeViewModel = homeViewModel,
                        filesViewModel = filesViewModel,
                        onSettingsSubScreenChanged = { isSettingsInSubScreen = it },
                        modifier = Modifier.fillMaxSize()
                    )

                    FloatingNavigationBar(
                        navController = bottomNavController,
                        visible = !hideBottomBar,
                        showNavText = prefsState.showNavText,
                        activeTransferSummary = activeTransferSummary,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        onNavigate = { route ->
                            bottomNavController.navigateSingleTopRestore(route)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainNavHost(
    bottomNavController: androidx.navigation.NavHostController,
    rootNavController: NavController,
    homeViewModel: HomeViewModel,
    filesViewModel: FilesViewModel,
    onSettingsSubScreenChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = bottomNavController,
        startDestination = "home",
        modifier = modifier,
        enterTransition = {
            val initialRoute = initialState.destination.route ?: "home"
            val targetRoute = targetState.destination.route ?: "home"
            val fromIndex = tabOrder.indexOf(initialRoute).coerceAtLeast(0)
            val toIndex = tabOrder.indexOf(targetRoute).coerceAtLeast(0)
            NavigationTransitions.tabEnter(toIndex >= fromIndex)
        },
        exitTransition = {
            val initialRoute = initialState.destination.route ?: "home"
            val targetRoute = targetState.destination.route ?: "home"
            val fromIndex = tabOrder.indexOf(initialRoute).coerceAtLeast(0)
            val toIndex = tabOrder.indexOf(targetRoute).coerceAtLeast(0)
            NavigationTransitions.tabExit(toIndex >= fromIndex)
        }
    ) {
        composable("home") {
            FeatureHomeScreen(
                navController = bottomNavController,
                rootNavController = rootNavController,
                viewModel = homeViewModel,
                onSearchClick = {
                    filesViewModel.requestSearchFocus()
                    bottomNavController.navigateSingleTopRestore("files")
                }
            )
        }
        composable("files") { FeatureFilesScreen(bottomNavController, rootNavController, filesViewModel) }
        composable("transfers") {
            FeatureTransfersScreen(
                onBack = { bottomNavController.navigateSingleTopRestore("home") },
                showBackButton = false
            )
        }
        composable("accounts") {
            FeatureAccountsScreen(
                navController = bottomNavController,
                rootNavController = rootNavController,
                onBack = { bottomNavController.navigateSingleTopRestore("home") }
            )
        }
        composable("settings") {
            FeatureSettingsScreen(
                navController = bottomNavController,
                rootNavController = rootNavController,
                onSubScreenChanged = onSettingsSubScreenChanged
            )
        }
    }
}


package com.cloudyte.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.UserPreferencesState
import com.cloudyte.app.features.accounts.AccountsScreen as FeatureAccountsScreen
import com.cloudyte.app.features.files.FilesScreen as FeatureFilesScreen
import com.cloudyte.app.features.home.HomeScreen as FeatureHomeScreen
import com.cloudyte.app.features.settings.SettingsScreen as FeatureSettingsScreen
import com.cloudyte.app.features.transfers.TransfersScreen as FeatureTransfersScreen
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

private val tabOrder = listOf("home", "files", "transfers", "accounts")

@Composable
fun MainScreen(rootNavController: NavController) {
    val context = LocalContext.current
    val bottomNavController = rememberNavController()
    val filesViewModel: FilesViewModel = viewModel()
    val prefsState by UserPreferences.observe(context).collectAsState(initial = UserPreferencesState())

    CompositionLocalProvider(LocalBottomBarInset provides 16.dp) {
        Scaffold(
            bottomBar = {
                FloatingNavigationBar(
                    navController = bottomNavController,
                    showNavText = prefsState.showNavText,
                    onNavigate = { route ->
                        bottomNavController.navigateSingleTopRestore(route)
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                NavHost(
                    navController = bottomNavController,
                    startDestination = "home",
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
                    composable("home") { FeatureHomeScreen(bottomNavController, rootNavController) }
                    composable("files") { FeatureFilesScreen(bottomNavController, rootNavController, filesViewModel) }
                    composable("transfers") { FeatureTransfersScreen(onBack = { bottomNavController.navigateSingleTopRestore("home") }) }
                    composable("accounts") { FeatureAccountsScreen(bottomNavController, rootNavController) }
                    composable("settings") { FeatureSettingsScreen(bottomNavController, rootNavController) }
                }
            }
        }
    }
}

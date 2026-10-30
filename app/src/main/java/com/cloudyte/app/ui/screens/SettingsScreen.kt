package com.cloudyte.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cloudyte.app.R
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.ui.components.GroupedList
import com.cloudyte.app.ui.components.GroupedListItem
import com.cloudyte.app.ui.components.SectionRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    bottomNavController: NavController? = null,
    rootNavController: NavController? = null,
    onNavigateSection: (SettingsSectionType) -> Unit = {},
    onNavigateExclusions: () -> Unit = {},
    onNavigateTransfers: () -> Unit = {},
    onLogout: () -> Unit = {
        ApiClient.saveToken(null)
        rootNavController?.navigate("login") {
            popUpTo("main") { inclusive = true }
        }
    }
) {
    val context = LocalContext.current

    var selectedSection by remember { mutableStateOf<SettingsSectionType?>(null) }
    var currentSubScreen by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = currentSubScreen != null || selectedSection != null) {
        when {
            currentSubScreen != null -> currentSubScreen = null
            else -> selectedSection = null
        }
    }

    if (currentSubScreen == "EXCLUSIONS") {
        ExclusionsScreen(onBack = { currentSubScreen = null })
        return
    }

    if (currentSubScreen == "TRANSFERS") {
        TransfersScreen(onBack = { currentSubScreen = null })
        return
    }

    if (currentSubScreen == "ACCOUNTS") {
        AccountsScreen(
            navController = bottomNavController ?: remember { androidx.navigation.NavHostController(context) },
            rootNavController = rootNavController ?: remember { androidx.navigation.NavHostController(context) }
        )
        return
    }

    selectedSection?.let { section ->
        SettingsSectionScreen(
            section = section,
            onBack = { selectedSection = null },
            onNavigateExclusions = { currentSubScreen = "EXCLUSIONS" },
            onNavigateAccounts = {
                // Accounts are managed from Settings, not the primary navigation bar.
                currentSubScreen = "ACCOUNTS"
            },
            onLogout = onLogout
        )
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val isScrolled = scrollState.value > 0
    val topBarContainerColor = if (isScrolled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarContainerColor
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
        ) {
            val sectionItems = SettingsSectionType.entries.map { section ->
                GroupedListItem(key = section.name) {
                    SectionRow(
                        section = section,
                        onClick = {
                            selectedSection = section
                            onNavigateSection(section)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            GroupedList(items = sectionItems)
            Spacer(modifier = Modifier.height(24.dp + com.cloudyte.app.ui.components.LocalBottomBarInset.current))
        }
    }
}

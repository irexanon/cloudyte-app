package com.cloudyte.app.ui.screens

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cloudyte.app.R
import com.cloudyte.app.api.Account
import com.cloudyte.app.api.AccountRenameRequest
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.UserSettingsRequest
import com.cloudyte.app.data.DriveAliasStore
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.LocalAccount
import com.cloudyte.app.ui.components.AppLoadingIndicator
import com.cloudyte.app.ui.components.DriveCopyMoveDialog
import com.cloudyte.app.ui.components.DriveManifestDownloadDialog
import com.cloudyte.app.ui.components.LocalBottomBarInset
import com.cloudyte.app.ui.components.WavyProgressBar
import com.cloudyte.app.ui.theme.MetricTextStyle
import com.cloudyte.app.ui.theme.cloudyteColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import kotlin.math.roundToInt

class AccountsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadAccounts(initial = true)
    }

    fun loadAccounts(initial: Boolean = false) {
        viewModelScope.launch {
            if (initial) {
                val cached = db.accountDao().getAllAccounts()
                if (cached.isNotEmpty()) {
                    _accounts.value = cached.map { it.toAccount() }
                    _isLoading.value = false
                }
            } else {
                _isRefreshing.value = true
            }

            _errorMessage.value = null
            try {
                val accs = DriveAliasStore.applyAliases(getApplication(), ApiClient.apiService.getAccounts())
                val validIds = accs.mapTo(mutableSetOf()) { it.effectiveId }
                val staleIds = db.accountDao().getAllAccounts()
                    .map { it.id }
                    .filter { it.isNotBlank() && it !in validIds }
                _accounts.value = accs
                db.accountDao().insertAccounts(accs.map { LocalAccount.fromAccount(it) })
                for (staleId in staleIds) {
                    com.cloudyte.app.data.AccountSessionHelper.purgeDisconnectedAccount(getApplication(), staleId)
                }
                com.cloudyte.app.provider.CloudyteDocumentsProvider.notifyRootsChanged(getApplication())
            } catch (e: Exception) {
                Log.e("AccountsViewModel", "Failed to load accounts", e)
                if (_accounts.value.isEmpty()) {
                    _errorMessage.value = e.localizedMessage ?: "Failed to connect to accounts"
                }
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    fun deleteAccount(id: String, onResult: (success: Boolean, errorMessage: String?) -> Unit) {
        viewModelScope.launch {
            var deleted = false
            var failureMessage: String? = null
            try {
                val res = ApiClient.apiService.deleteAccount(id)
                if (res.isSuccessful || res.code() == 404) {
                    DriveAliasStore.removeAlias(getApplication(), id)
                    com.cloudyte.app.data.AccountSessionHelper.purgeDisconnectedAccount(getApplication(), id)
                    _accounts.value = _accounts.value.filter { it.effectiveId != id }
                    deleted = true
                } else {
                    val raw = runCatching { res.errorBody()?.string() }.getOrNull()
                    failureMessage = ApiClient.parseError(raw, res.code())
                    _errorMessage.value = failureMessage
                }
            } catch (e: Exception) {
                Log.e("AccountsViewModel", "Delete account error", e)
                failureMessage = e.localizedMessage ?: "Failed to disconnect account"
                _errorMessage.value = failureMessage
            } finally {
                onResult(deleted, failureMessage)
            }
        }
    }

    fun setDefaultAccount(id: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                // Only send isDefaultAccId so backend preserves existing backupEnabled/backupAccId settings.
                ApiClient.apiService.updateSettings(
                    UserSettingsRequest(isDefaultAccId = id)
                )
            } catch (e: Exception) {
                Log.e("AccountsViewModel", "Set default account error", e)
            }
            _accounts.value = _accounts.value.map {
                it.copy(isDefault = (it.effectiveId == id))
            }
            db.accountDao().insertAccounts(_accounts.value.map { LocalAccount.fromAccount(it) })
            runCatching { UserPreferences.setHomeStorageAccountId(getApplication(), null) }
            onComplete()
        }
    }

    fun renameAccount(id: String, newName: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val trimmed = newName.trim()
            if (trimmed.isEmpty()) return@launch
            var savedOnServer = false
            try {
                val res = ApiClient.apiService.renameAccount(id, AccountRenameRequest(name = trimmed, driveName = trimmed))
                savedOnServer = res.isSuccessful
            } catch (e: Exception) {
                Log.w("AccountsViewModel", "Server rename unavailable, persisting local alias: ${e.message}")
            }
            if (savedOnServer) {
                DriveAliasStore.removeAlias(getApplication(), id)
            } else {
                DriveAliasStore.setAlias(getApplication(), id, trimmed)
            }
            _accounts.value = _accounts.value.map {
                if (it.effectiveId == id) it.copy(name = trimmed, driveName = trimmed) else it
            }
            db.accountDao().insertAccounts(_accounts.value.map { LocalAccount.fromAccount(it) })
            onComplete(if (savedOnServer) "Drive renamed" else "Drive renamed (saved on device)")
        }
    }

    fun importTgDriveJson(accountId: String, uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    val resolver = getApplication<Application>().contentResolver
                    val bytes = resolver.openInputStream(uri)?.use { input ->
                        val maxBytes = 15 * 1024 * 1024
                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(16 * 1024)
                        var total = 0
                        while (true) {
                            val read = input.read(chunk)
                            if (read <= 0) break
                            total += read
                            if (total > maxBytes) {
                                return@withContext "Import file exceeds 15 MB limit."
                            }
                            buffer.write(chunk, 0, read)
                        }
                        buffer.toByteArray()
                    } ?: return@withContext "Could not read selected JSON file."

                    if (bytes.isEmpty()) return@withContext "Selected JSON file is empty."

                    val body = bytes.toRequestBody("application/json; charset=utf-8".toMediaType())
                    val resp = ApiClient.apiService.importTgDrive(accountId, body)
                    if (resp.isSuccessful) {
                        val data = resp.body()
                        val imported = data?.importedFiles ?: 0
                        val skipped = data?.skippedDuplicates ?: 0
                        val topicsPart = if ((data?.importedTopics ?: 0) > 0) ", ${data?.importedTopics} topic(s)" else ""
                        "Imported $imported file(s)$topicsPart" + if (skipped > 0) " ($skipped duplicate skipped)" else ""
                    } else {
                        val raw = runCatching { resp.errorBody()?.string() }.getOrNull()
                        ApiClient.parseError(raw, resp.code())
                    }
                } catch (e: Exception) {
                    Log.e("AccountsViewModel", "TG import failed", e)
                    e.localizedMessage ?: "Telegram Drive import failed."
                }
            }
            loadAccounts()
            onResult(msg)
        }
    }

    fun exportTgDriveJson(accountId: String, uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    val resp = ApiClient.apiService.exportTgDrive(accountId)
                    if (!resp.isSuccessful) {
                        val raw = runCatching { resp.errorBody()?.string() }.getOrNull()
                        return@withContext ApiClient.parseError(raw, resp.code())
                    }
                    val responseBody = resp.body() ?: return@withContext "Empty export response from server."
                    val resolver = getApplication<Application>().contentResolver
                    val written = resolver.openOutputStream(uri)?.use { out ->
                        responseBody.byteStream().use { input ->
                            input.copyTo(out)
                        }
                    } ?: return@withContext "Could not open destination file for writing."
                    "Exported TG Drive index (${formatBytes(written)})"
                } catch (e: Exception) {
                    Log.e("AccountsViewModel", "TG export failed", e)
                    e.localizedMessage ?: "Telegram Drive export failed."
                }
            }
            onResult(msg)
        }
    }
}

private sealed interface AccountsScreenUiState {
    data object Loading : AccountsScreenUiState
    data class Error(val message: String) : AccountsScreenUiState
    data object Empty : AccountsScreenUiState
    data class Success(val accounts: List<Account>) : AccountsScreenUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    navController: NavController,
    rootNavController: NavController,
    viewModel: AccountsViewModel = viewModel(),
    onBack: (() -> Unit)? = null
) {
    BackHandler(enabled = onBack != null) {
        onBack?.invoke()
    }
    val accounts by viewModel.accounts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val ext = MaterialTheme.cloudyteColors

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedAddProvider by remember { mutableStateOf(CloudProviderType.GOOGLE_DRIVE) }
    var showChooserInAddDialog by remember { mutableStateOf(true) }
    var activeProviderFilter by remember { mutableStateOf<CloudProviderType?>(null) }

    var accountForDisconnect by remember { mutableStateOf<Account?>(null) }
    var accountForRename by remember { mutableStateOf<Account?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var driveForCopyMove by remember { mutableStateOf<Pair<Account, Boolean>?>(null) }
    var driveForDownload by remember { mutableStateOf<Account?>(null) }
    var tgImportTargetAccount by remember { mutableStateOf<Account?>(null) }
    var tgExportTargetAccount by remember { mutableStateOf<Account?>(null) }

    val tgImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = tgImportTargetAccount
        tgImportTargetAccount = null
        if (uri != null && target != null) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Importing Telegram Drive index...")
            }
            viewModel.importTgDriveJson(target.effectiveId, uri) { msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    val tgExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val target = tgExportTargetAccount
        tgExportTargetAccount = null
        if (uri != null && target != null) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Exporting Telegram Drive index...")
            }
            viewModel.exportTgDriveJson(target.effectiveId, uri) { msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    val listState = rememberLazyListState()

    val uiState: AccountsScreenUiState = when {
        isLoading && accounts.isEmpty() -> AccountsScreenUiState.Loading
        errorMessage != null && accounts.isEmpty() -> AccountsScreenUiState.Error(errorMessage ?: "Connection failed")
        accounts.isEmpty() -> AccountsScreenUiState.Empty
        else -> AccountsScreenUiState.Success(accounts)
    }

    val totalUsedBytes = remember(accounts) { accounts.sumOf { it.usageBytes.coerceAtLeast(0L) } }
    val totalQuotaBytes = remember(accounts) {
        accounts.filter { !isTelegramAccount(it) && it.totalBytes > 0L }.sumOf { it.totalBytes }
    }
    val subtitleText = if (totalQuotaBytes > 0L) {
        "${accounts.size} linked · ${formatBytes(totalUsedBytes)} of ${formatBytes(totalQuotaBytes)} used"
    } else {
        stringResource(R.string.drives_subtitle_format, accounts.size, formatBytes(totalUsedBytes))
    }

    if (showAddDialog) {
        AddAccountDialog(
            initialProvider = selectedAddProvider,
            showChooserFirst = showChooserInAddDialog,
            onDismiss = { showAddDialog = false },
            onSuccess = {
                showAddDialog = false
                viewModel.loadAccounts()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Storage account linked successfully")
                }
            }
        )
    }

    if (accountForDisconnect != null) {
        val target = accountForDisconnect
        val providerName = when {
            target?.effectiveType?.contains("google", ignoreCase = true) == true -> "Google Drive"
            target?.effectiveType?.contains("one", ignoreCase = true) == true -> "OneDrive"
            else -> "Telegram Cloud"
        }
        AlertDialog(
            onDismissRequest = { accountForDisconnect = null },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                Text(
                    text = "Disconnect account?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Remove ${target?.effectiveName}? Your files stay in $providerName.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val acc = target ?: return@Button
                        accountForDisconnect = null
                        viewModel.deleteAccount(acc.effectiveId) { success, errMsg ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    if (success) "Account disconnected" else (errMsg ?: "Failed to disconnect account")
                                )
                            }
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountForDisconnect = null }) {
                    Text(stringResource(R.string.action_cancel), color = ext.lightAccent)
                }
            }
        )
    }

    if (accountForRename != null) {
        AlertDialog(
            onDismissRequest = { accountForRename = null },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                Text(
                    text = "Rename Drive",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Drive Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = ext.accentBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val acc = accountForRename ?: return@Button
                        if (renameInput.isNotBlank()) {
                            viewModel.renameAccount(acc.effectiveId, renameInput.trim()) { statusMsg ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(statusMsg)
                                }
                            }
                        }
                        accountForRename = null
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.action_save), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountForRename = null }) {
                    Text(stringResource(R.string.action_cancel), color = ext.lightAccent)
                }
            }
        )
    }

    driveForCopyMove?.let { (sourceAccount, initialIsMove) ->
        DriveCopyMoveDialog(
            sourceAccount = sourceAccount,
            allAccounts = accounts,
            initialIsMove = initialIsMove,
            onDismiss = { driveForCopyMove = null },
            onStarted = { _, isMoveAction, targetName ->
                driveForCopyMove = null
                coroutineScope.launch {
                    val verb = if (isMoveAction) "Move" else "Copy"
                    val res = snackbarHostState.showSnackbar(
                        message = "$verb started: ${sourceAccount.effectiveName} → $targetName",
                        actionLabel = "View",
                        withDismissAction = true
                    )
                    if (res == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        navController.navigateSingleTopRestore("transfers")
                    }
                }
            }
        )
    }

    driveForDownload?.let { targetDrive ->
        DriveManifestDownloadDialog(
            accountId = targetDrive.effectiveId,
            driveName = targetDrive.effectiveName,
            onDismiss = { driveForDownload = null },
            onQueued = { count, destFolder ->
                driveForDownload = null
                coroutineScope.launch {
                    val res = snackbarHostState.showSnackbar(
                        message = "Queued $count file(s) to downloads/$destFolder",
                        actionLabel = "View",
                        withDismissAction = true
                    )
                    if (res == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        navController.navigateSingleTopRestore("transfers")
                    }
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = LocalBottomBarInset.current)
            )
        },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = if (onBack != null) Arrangement.SpaceBetween else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onBack)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { viewModel.loadAccounts() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isRefreshing) {
                            AppLoadingIndicator(size = 20.dp, color = ext.lightAccent)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                                tint = ext.lightAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    showChooserInAddDialog = true
                    showAddDialog = true
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 6.dp
                ),
                modifier = Modifier
                    .padding(bottom = LocalBottomBarInset.current)
                    .height(64.dp)
                    .testTag("accounts_add_fab"),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.drives_add_fab),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.loadAccounts() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "AccountsScreenUiStateTransition",
                modifier = Modifier.fillMaxSize()
            ) { state ->
                when (state) {
                    is AccountsScreenUiState.Loading -> {
                        AccountsLoadingSkeleton()
                    }
                    is AccountsScreenUiState.Error -> {
                        AccountsErrorState(
                            message = state.message,
                            onRetry = { viewModel.loadAccounts() }
                        )
                    }
                    is AccountsScreenUiState.Empty -> {
                        AccountsEmptyState(
                            onAddAccount = {
                                showChooserInAddDialog = true
                                showAddDialog = true
                            }
                        )
                    }
                    is AccountsScreenUiState.Success -> {
                        val visibleAccounts = remember(state.accounts, activeProviderFilter) {
                            when (activeProviderFilter) {
                                null -> state.accounts
                                CloudProviderType.TELEGRAM -> state.accounts.filter { isTelegramAccount(it) }
                                CloudProviderType.GOOGLE_DRIVE -> state.accounts.filter {
                                    it.effectiveType.contains("google", ignoreCase = true)
                                }
                                CloudProviderType.ONEDRIVE -> state.accounts.filter {
                                    it.effectiveType.contains("one", ignoreCase = true)
                                }
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(
                                top = 4.dp,
                                bottom = LocalBottomBarInset.current + 104.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item(key = "drives_header") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.drives_title),
                                        fontSize = 44.sp,
                                        lineHeight = 46.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.03).em,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = subtitleText,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            item(key = "provider_summary_chips") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(bottom = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ProviderSummaryChip(
                                        label = stringResource(R.string.provider_telegram_title),
                                        dotColor = ext.providerTelegram,
                                        selected = activeProviderFilter == CloudProviderType.TELEGRAM,
                                        onClick = {
                                            activeProviderFilter =
                                                if (activeProviderFilter == CloudProviderType.TELEGRAM) null
                                                else CloudProviderType.TELEGRAM
                                        }
                                    )
                                    ProviderSummaryChip(
                                        label = stringResource(R.string.provider_google_title),
                                        dotColor = ext.providerGoogle,
                                        selected = activeProviderFilter == CloudProviderType.GOOGLE_DRIVE,
                                        onClick = {
                                            activeProviderFilter =
                                                if (activeProviderFilter == CloudProviderType.GOOGLE_DRIVE) null
                                                else CloudProviderType.GOOGLE_DRIVE
                                        }
                                    )
                                    ProviderSummaryChip(
                                        label = stringResource(R.string.provider_onedrive_title),
                                        dotColor = ext.providerOneDrive,
                                        selected = activeProviderFilter == CloudProviderType.ONEDRIVE,
                                        onClick = {
                                            activeProviderFilter =
                                                if (activeProviderFilter == CloudProviderType.ONEDRIVE) null
                                                else CloudProviderType.ONEDRIVE
                                        }
                                    )
                                }
                            }

                            if (visibleAccounts.isEmpty()) {
                                item(key = "filtered_empty") {
                                    Surface(
                                        shape = RoundedCornerShape(28.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "No ${activeProviderFilter?.displayName ?: "matching"} drives linked",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            TextButton(onClick = { activeProviderFilter = null }) {
                                                Text("Show all drives", color = ext.lightAccent, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            } else {
                                itemsIndexed(
                                    items = visibleAccounts,
                                    key = { _, acc -> acc.effectiveId }
                                ) { index, account ->
                                    val count = visibleAccounts.size
                                    val cardShape = when {
                                        count == 1 -> RoundedCornerShape(28.dp)
                                        index == 0 -> RoundedCornerShape(
                                            topStart = 28.dp, topEnd = 28.dp,
                                            bottomStart = 8.dp, bottomEnd = 8.dp
                                        )
                                        index == count - 1 -> RoundedCornerShape(
                                            topStart = 8.dp, topEnd = 8.dp,
                                            bottomStart = 28.dp, bottomEnd = 28.dp
                                        )
                                        else -> RoundedCornerShape(8.dp)
                                    }
                                    AccountCard(
                                        account = account,
                                        shape = cardShape,
                                        onCopyDrive = {
                                            driveForCopyMove = account to false
                                        },
                                        onMoveDrive = {
                                            driveForCopyMove = account to true
                                        },
                                        onDownloadDrive = {
                                            driveForDownload = account
                                        },
                                        onRename = {
                                            renameInput = account.effectiveName
                                            accountForRename = account
                                        },
                                        onSetDefault = {
                                            viewModel.setDefaultAccount(account.effectiveId) {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("${account.effectiveName} set as default drive")
                                                }
                                            }
                                        },
                                        onRefresh = {
                                            viewModel.loadAccounts()
                                        },
                                        onDisconnect = {
                                            accountForDisconnect = account
                                        },
                                        onReconnect = {
                                            selectedAddProvider = when {
                                                account.effectiveType.contains("google", true) -> CloudProviderType.GOOGLE_DRIVE
                                                account.effectiveType.contains("one", true) -> CloudProviderType.ONEDRIVE
                                                else -> CloudProviderType.TELEGRAM
                                            }
                                            showChooserInAddDialog = false
                                            showAddDialog = true
                                        },
                                        onImportTgDrive = if (isTelegramAccount(account)) {
                                            {
                                                tgImportTargetAccount = account
                                                tgImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                            }
                                        } else null,
                                        onExportTgDrive = if (isTelegramAccount(account)) {
                                            {
                                                tgExportTargetAccount = account
                                                val safeName = account.effectiveName
                                                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                                    .ifBlank { "tgdrive" }
                                                tgExportLauncher.launch("${safeName}_export.json")
                                            }
                                        } else null,
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderSummaryChip(
    label: String,
    dotColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.cloudyteColors.lightAccent else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun isTelegramAccount(account: Account): Boolean =
    account.effectiveType.contains("tele", ignoreCase = true) ||
        account.effectiveType.contains("tg", ignoreCase = true) ||
        account.channelId != null

@Composable
fun AccountCard(
    account: Account,
    onCopyDrive: () -> Unit,
    onMoveDrive: () -> Unit,
    onDownloadDrive: () -> Unit,
    onRename: () -> Unit,
    onSetDefault: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onImportTgDrive: (() -> Unit)? = null,
    onExportTgDrive: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp)
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val ext = MaterialTheme.cloudyteColors

    val isTelegram = remember(account.effectiveType, account.channelId) { isTelegramAccount(account) }
    val isGoogle = remember(account.effectiveType) { account.effectiveType.contains("google", ignoreCase = true) }

    val providerColor = when {
        isGoogle -> ext.providerGoogle
        account.effectiveType.contains("one", ignoreCase = true) -> ext.providerOneDrive
        else -> ext.providerTelegram
    }
    val providerBg = when {
        isGoogle -> ext.providerGoogleBg
        account.effectiveType.contains("one", ignoreCase = true) -> ext.providerOneDriveBg
        else -> ext.providerTelegramBg
    }
    val providerIcon: ImageVector = when {
        isGoogle -> Icons.Default.Cloud
        account.effectiveType.contains("one", ignoreCase = true) -> Icons.Default.CloudQueue
        else -> Icons.AutoMirrored.Filled.Send
    }
    val providerLabel = when {
        isGoogle -> stringResource(R.string.provider_google_title)
        account.effectiveType.contains("one", ignoreCase = true) -> stringResource(R.string.provider_onedrive_title)
        else -> stringResource(R.string.provider_telegram_title)
    }

    val percent = account.effectivePercentage
    val progressValue = (percent / 100f).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressValue,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "AccountStorageProgress"
    )

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(if (isGoogle) CircleShape else RoundedCornerShape(18.dp))
                        .background(providerBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = providerIcon,
                        contentDescription = providerLabel,
                        tint = providerColor,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = account.effectiveName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (account.isDefault == true) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.drives_default_badge),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (account.isForumGroup) {
                            Surface(
                                color = ext.providerTelegramBg,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Forum,
                                        contentDescription = null,
                                        tint = ext.providerTelegram,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Forum",
                                        color = ext.providerTelegram,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    val subtitleText = if (!account.email.isNullOrBlank()) {
                        account.email
                    } else {
                        providerLabel
                    }
                    Text(
                        text = subtitleText,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Account options for ${account.effectiveName}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.drive_action_copy), color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = ext.lightAccent) },
                            onClick = {
                                menuExpanded = false
                                onCopyDrive()
                            },
                            modifier = Modifier.testTag("account_menu_copy_drive_${account.effectiveId}")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.drive_action_move), color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, tint = ext.lightAccent) },
                            onClick = {
                                menuExpanded = false
                                onMoveDrive()
                            },
                            modifier = Modifier.testTag("account_menu_move_drive_${account.effectiveId}")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.drive_action_download), color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = ext.mintSuccess) },
                            onClick = {
                                menuExpanded = false
                                onDownloadDrive()
                            },
                            modifier = Modifier.testTag("account_menu_download_drive_${account.effectiveId}")
                        )
                        if (onImportTgDrive != null) {
                            DropdownMenuItem(
                                text = { Text("Import TG Index (JSON)", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null, tint = ext.providerTelegram) },
                                onClick = {
                                    menuExpanded = false
                                    onImportTgDrive()
                                },
                                modifier = Modifier.testTag("account_menu_tg_import_${account.effectiveId}")
                            )
                        }
                        if (onExportTgDrive != null) {
                            DropdownMenuItem(
                                text = { Text("Export TG Index (JSON)", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = ext.providerTelegram) },
                                onClick = {
                                    menuExpanded = false
                                    onExportTgDrive()
                                },
                                modifier = Modifier.testTag("account_menu_tg_export_${account.effectiveId}")
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename), color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        if (account.isDefault != true) {
                            DropdownMenuItem(
                                text = { Text("Set as default", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = ext.lightAccent) },
                                onClick = {
                                    menuExpanded = false
                                    onSetDefault()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_refresh), color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                menuExpanded = false
                                onRefresh()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Reconnect", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(Icons.Default.CloudQueue, contentDescription = null, tint = ext.lightAccent) },
                            onClick = {
                                menuExpanded = false
                                onReconnect()
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DropdownMenuItem(
                            text = { Text("Disconnect", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.LinkOff, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDisconnect()
                            }
                        )
                    }
                }
            }

            val fileCountSuffix = account.fileCount?.let { count ->
                " · $count ${if (count == 1) "file" else "files"}"
            }.orEmpty()
            val statsLeftText = if (!isTelegram && account.totalBytes > 0L) {
                "${formatBytes(account.usageBytes)} of ${formatBytes(account.totalBytes)} used$fileCountSuffix"
            } else {
                "${formatBytes(account.usageBytes)} used$fileCountSuffix"
            }
            val statsRightText = if (!isTelegram && account.totalBytes > 0L) {
                "${percent.roundToInt().coerceIn(0, 100)}%"
            } else if (isTelegram) {
                stringResource(R.string.home_unlimited_symbol)
            } else {
                "—"
            }
            val statsAccentColor = if (!isTelegram && account.totalBytes > 0L && percent >= 90f) {
                MaterialTheme.colorScheme.error
            } else {
                ext.mintSuccess
            }

            if (!isTelegram && account.totalBytes > 0L) {
                Spacer(modifier = Modifier.height(10.dp))
                WavyProgressBar(
                    progress = animatedProgress,
                    indicatorColor = statsAccentColor,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    strokeWidth = 3.5.dp,
                    waveAmplitude = 2.5.dp,
                    waveLength = 16.dp
                )
                Spacer(modifier = Modifier.height(6.dp))
            } else {
                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statsLeftText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statsRightText,
                    style = MetricTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = statsAccentColor,
                    maxLines = 1
                )
            }

            if (account.hasError) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Drive connection error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = account.effectiveErrorMessage ?: "Connection expired — reconnect required",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onReconnect,
                            modifier = Modifier.testTag("account_reconnect_error_${account.effectiveId}")
                        ) {
                            Text(
                                text = "Reconnect",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountsEmptyState(onAddAccount: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudQueue,
                contentDescription = null,
                tint = MaterialTheme.cloudyteColors.lightAccent,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "No drives connected",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Link your Telegram Channel, Google Drive, or OneDrive to start managing your cloud files.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAddAccount,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = CircleShape,
            modifier = Modifier.heightIn(min = 52.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.drives_add_fab), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AccountsErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Connection issue",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.heightIn(min = 52.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_retry), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AccountsLoadingSkeleton() {
    val transition = rememberInfiniteTransition(label = "SkeletonTransition")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SkeletonAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(4) { idx ->
            val shape = when (idx) {
                0 -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                3 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
                else -> RoundedCornerShape(8.dp)
            }
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(18.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.35f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha))
                        )
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    return when {
        bytes >= 1024L * 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f TB", bytes.toDouble() / (1024L * 1024 * 1024 * 1024))
        bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes.toDouble() / (1024L * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024L * 1024))
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024L)
        else -> "$bytes B"
    }
}

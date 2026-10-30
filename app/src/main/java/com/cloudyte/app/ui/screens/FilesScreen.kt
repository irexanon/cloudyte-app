package com.cloudyte.app.ui.screens

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cloudyte.app.api.Account
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.api.CloudyteApi
import com.cloudyte.app.api.FolderRequest
import com.cloudyte.app.api.RenameRequest
import com.cloudyte.app.data.CloudFileRepository
import com.cloudyte.app.data.SortDirection
import com.cloudyte.app.data.SortField
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.UserPreferencesState
import com.cloudyte.app.data.ViewMode
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.LocalAccount
import com.cloudyte.app.ui.components.AppLoadingIndicator
import com.cloudyte.app.ui.components.FileThumb
import com.cloudyte.app.ui.components.LocalBottomBarInset
import com.cloudyte.app.ui.navigation.NavigationTransitions
import com.cloudyte.app.ui.openFileInViewer
import com.cloudyte.app.ui.theme.ProviderGoogle
import com.cloudyte.app.ui.theme.ProviderOneDrive
import com.cloudyte.app.ui.theme.ProviderTelegram
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BreadcrumbItem(val id: String?, val name: String)

private val CloudFile.formattedDate: String
    get() {
        val t = time ?: updatedAt
        if (t != null && t > 0L) {
            val millis = if (t < 100000000000L) t * 1000L else t
            val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US)
            return sdf.format(java.util.Date(millis))
        }
        if (!createdAt.isNullOrBlank()) {
            return createdAt.take(10)
        }
        return "Recent"
    }

enum class FilesNavDirection { FORWARD, BACKWARD }

enum class FileSortOrder(val displayName: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First")
}

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModel(
    application: Application,
    private val db: AppDatabase,
    private val api: CloudyteApi,
    private val repo: CloudFileRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        db = AppDatabase.getDatabase(application),
        api = ApiClient.apiService,
        repo = CloudFileRepository(AppDatabase.getDatabase(application), ApiClient.apiService)
    )

    private val prefs = application.getSharedPreferences("cloudyte_drive_state", Context.MODE_PRIVATE)

    val currentAccountId = MutableStateFlow<String?>(prefs.getString("last_active_account_id", null))
    val currentFolderId = MutableStateFlow<String?>(null)

    val accounts: StateFlow<List<Account>> = db.accountDao().getAllAccountsFlow()
        .map { list -> list.map { it.toAccount() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val files: StateFlow<List<CloudFile>> = combine(
        currentAccountId,
        accounts,
        currentFolderId
    ) { accId, accs, folderId ->
        val effectiveAccId = accId ?: accs.firstOrNull { it.isDefault == true }?.effectiveId ?: accs.firstOrNull()?.effectiveId
        effectiveAccId to folderId
    }.flatMapLatest { (accId, folderId) ->
        repo.observeFolder(accId, folderId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _breadcrumbs = MutableStateFlow<List<BreadcrumbItem>>(listOf(BreadcrumbItem(null, "Files")))
    val breadcrumbs: StateFlow<List<BreadcrumbItem>> = _breadcrumbs.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _uploadProgress = MutableStateFlow(0)
    val uploadProgress: StateFlow<Int> = _uploadProgress.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _userEvents = MutableSharedFlow<String>()
    val userEvents: SharedFlow<String> = _userEvents.asSharedFlow()

    var refreshJob: Job? = null
        private set

    init {
        viewModelScope.launch {
            try {
                val cached = db.accountDao().getAllAccounts()
                if (currentAccountId.value == null && cached.isNotEmpty()) {
                    val def = cached.find { it.isDefault == true } ?: cached.first()
                    currentAccountId.value = def.id
                }
                val fetched = api.getAccounts()
                if (fetched.isNotEmpty()) {
                    db.accountDao().insertAccounts(fetched.map { LocalAccount.fromAccount(it) })
                    if (currentAccountId.value == null) {
                        val def = fetched.find { it.isDefault == true } ?: fetched.first()
                        currentAccountId.value = def.effectiveId
                    }
                }
            } catch (_: Exception) {}
            refresh(force = false)
        }
        viewModelScope.launch {
            accounts.collect { accs ->
                if (currentAccountId.value == null && accs.isNotEmpty()) {
                    val defaultAcc = accs.find { it.isDefault == true } ?: accs.first()
                    currentAccountId.value = defaultAcc.effectiveId
                }
            }
        }
    }

    fun selectAccount(accountId: String) {
        if (currentAccountId.value != accountId) {
            currentAccountId.value = accountId
            currentFolderId.value = null
            _breadcrumbs.value = listOf(BreadcrumbItem(null, "Files"))
            prefs.edit().putString("last_active_account_id", accountId).apply()
            refresh(force = false)
        }
    }

    fun enterFolder(folderId: String, folderName: String) {
        currentFolderId.value = folderId
        _breadcrumbs.value = _breadcrumbs.value + BreadcrumbItem(folderId, folderName)
        refresh(force = false)
    }

    fun navigateBack(): Boolean {
        val currentCrumbs = _breadcrumbs.value
        if (currentCrumbs.size > 1) {
            val updated = currentCrumbs.dropLast(1)
            _breadcrumbs.value = updated
            currentFolderId.value = updated.last().id
            refresh(force = false)
            return true
        }
        return false
    }

    fun navigateToBreadcrumb(index: Int) {
        val currentCrumbs = _breadcrumbs.value
        if (index in currentCrumbs.indices) {
            val updated = currentCrumbs.take(index + 1)
            _breadcrumbs.value = updated
            currentFolderId.value = updated.last().id
            refresh(force = false)
        }
    }

    fun refresh(force: Boolean = false) {
        val accId = currentAccountId.value
            ?: accounts.value.firstOrNull { it.isDefault == true }?.effectiveId
            ?: accounts.value.firstOrNull()?.effectiveId
        val folderId = currentFolderId.value
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (!force && repo.isFolderFresh(accId, folderId)) {
                _isOffline.value = false
                _isRefreshing.value = false
                return@launch
            }
            _isRefreshing.value = true
            try {
                val res = repo.refresh(accId, folderId, force = force)
                _isOffline.value = res.isFailure
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.w("FilesViewModel", "Sync failed: ${e.message}")
                    _isOffline.value = true
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun deleteFile(fileId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val accId = currentAccountId.value
                ?: accounts.value.firstOrNull { it.isDefault == true }?.effectiveId
                ?: accounts.value.firstOrNull()?.effectiveId
            try {
                val res = repo.delete(fileId, accId)
                if (res.isSuccess) onSuccess()
                else _userEvents.emit(res.exceptionOrNull()?.message ?: "Failed to delete item")
            } catch (e: Exception) {
                Log.e("FilesViewModel", "Delete failed", e)
                _userEvents.emit(e.message ?: "Failed to delete item")
            }
        }
    }

    fun renameFile(fileId: String, newName: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val accId = currentAccountId.value
                ?: accounts.value.firstOrNull { it.isDefault == true }?.effectiveId
                ?: accounts.value.firstOrNull()?.effectiveId
            try {
                val res = repo.rename(fileId, accId, newName)
                if (res.isSuccess) onSuccess()
                else _userEvents.emit(res.exceptionOrNull()?.message ?: "Failed to rename item")
            } catch (e: Exception) {
                Log.e("FilesViewModel", "Rename failed", e)
                _userEvents.emit(e.message ?: "Failed to rename item")
            }
        }
    }

    fun createFolder(name: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val accId = currentAccountId.value
                ?: accounts.value.firstOrNull { it.isDefault == true }?.effectiveId
                ?: accounts.value.firstOrNull()?.effectiveId
            try {
                val res = repo.createFolder(name, accId, currentFolderId.value)
                if (res.isSuccess) onSuccess()
                else _userEvents.emit(res.exceptionOrNull()?.message ?: "Failed to create folder")
            } catch (e: Exception) {
                Log.e("FilesViewModel", "Create folder failed", e)
                _userEvents.emit(e.message ?: "Failed to create folder")
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    navController: NavController,
    rootNavController: NavController,
    viewModel: FilesViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val files by viewModel.files.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val currentAccountId by viewModel.currentAccountId.collectAsStateWithLifecycle()
    val currentFolderId by viewModel.currentFolderId.collectAsStateWithLifecycle()
    val breadcrumbs by viewModel.breadcrumbs.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isUploading by viewModel.isUploading.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()

    val prefsState by UserPreferences.observe(context).collectAsStateWithLifecycle(initialValue = UserPreferencesState())
    val isGridView = prefsState.viewMode == ViewMode.GRID

    // 2 columns by default matching the original screenshot, or user's gridColumns preference
    val gridCells = remember(prefsState.gridColumns) {
        GridCells.Fixed(if (prefsState.gridColumns > 0) prefsState.gridColumns else 2)
    }

    var selectedFileIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedFileIds.isNotEmpty()

    fun toggleSelection(fileId: String) {
        selectedFileIds = if (selectedFileIds.contains(fileId)) {
            selectedFileIds - fileId
        } else {
            selectedFileIds + fileId
        }
    }

    BackHandler(enabled = isSelectionMode || breadcrumbs.size > 1) {
        if (isSelectionMode) {
            selectedFileIds = emptySet()
        } else if (breadcrumbs.size > 1) {
            viewModel.navigateBack()
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var showSortDropdown by remember { mutableStateOf(false) }
    var showDriveDropdown by remember { mutableStateOf(false) }

    val sortOrder = remember(prefsState.sortField, prefsState.sortDirection) {
        when {
            prefsState.sortField == SortField.NAME && prefsState.sortDirection == SortDirection.ASC -> FileSortOrder.NAME_ASC
            prefsState.sortField == SortField.NAME && prefsState.sortDirection == SortDirection.DESC -> FileSortOrder.NAME_DESC
            prefsState.sortField == SortField.SIZE && prefsState.sortDirection == SortDirection.ASC -> FileSortOrder.SIZE_ASC
            prefsState.sortField == SortField.SIZE && prefsState.sortDirection == SortDirection.DESC -> FileSortOrder.SIZE_DESC
            prefsState.sortField == SortField.DATE && prefsState.sortDirection == SortDirection.ASC -> FileSortOrder.DATE_ASC
            else -> FileSortOrder.DATE_DESC
        }
    }

    // Filter and sort files
    val filteredAndSortedFiles = remember(files, searchQuery, sortOrder) {
        val filtered = files.filter { file ->
            searchQuery.isBlank() || file.effectiveName.contains(searchQuery, ignoreCase = true)
        }
        when (sortOrder) {
            FileSortOrder.DATE_DESC -> filtered.sortedWith(compareByDescending<CloudFile> { it.isFolderItem }.thenByDescending { it.time ?: 0L })
            FileSortOrder.DATE_ASC -> filtered.sortedWith(compareByDescending<CloudFile> { it.isFolderItem }.thenBy { it.time ?: 0L })
            FileSortOrder.NAME_ASC -> filtered.sortedWith(compareByDescending<CloudFile> { it.isFolderItem }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.effectiveName })
            FileSortOrder.NAME_DESC -> filtered.sortedWith(compareByDescending<CloudFile> { it.isFolderItem }.thenByDescending(String.CASE_INSENSITIVE_ORDER) { it.effectiveName })
            FileSortOrder.SIZE_DESC -> filtered.sortedWith(compareByDescending<CloudFile> { it.isFolderItem }.thenByDescending { it.sizeBytes })
            FileSortOrder.SIZE_ASC -> filtered.sortedWith(compareByDescending<CloudFile> { it.isFolderItem }.thenBy { it.sizeBytes })
        }
    }

    val folders = remember(filteredAndSortedFiles) {
        filteredAndSortedFiles.filter { it.isFolderItem }.distinctBy { it.effectiveId.ifEmpty { it.effectiveName } }
    }
    val regularFiles = remember(filteredAndSortedFiles) {
        filteredAndSortedFiles.filter { !it.isFolderItem }.distinctBy { it.effectiveId.ifEmpty { it.effectiveName } }
    }

    val activeAccount = remember(accounts, currentAccountId) {
        accounts.find { it.effectiveId == currentAccountId } ?: accounts.firstOrNull()
    }
    val effectiveAccountId = activeAccount?.effectiveId ?: currentAccountId ?: "root"

    // Dialogs state
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            snackbarMessage = null
        }
    }

    LaunchedEffect(viewModel.userEvents) {
        viewModel.userEvents.collect { message -> snackbarMessage = message }
    }

    LaunchedEffect(Unit) {
        // Room renders immediately; revalidate the visible folder when this screen is entered.
        viewModel.refresh(force = false)
    }

    var fileForRename by remember { mutableStateOf<CloudFile?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var fileForDelete by remember { mutableStateOf<CloudFile?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            snackbarMessage = "Uploading file..."
        }
    }
    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { snackbarMessage = "Uploading folder..." }
    }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    // Rename Dialog
    if (fileForRename != null) {
        AlertDialog(
            onDismissRequest = { fileForRename = null },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Rename item", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = fileForRename ?: return@Button
                        if (renameInput.isNotBlank()) {
                            val newName = renameInput.trim()
                            fileForRename = null
                            viewModel.renameFile(target.effectiveId, newName) {
                                snackbarMessage = "Renamed successfully"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Rename", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileForRename = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Delete Dialog
    if (fileForDelete != null) {
        AlertDialog(
            onDismissRequest = { fileForDelete = null },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Delete item?", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "Are you sure you want to delete '${fileForDelete?.effectiveName}'?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = fileForDelete ?: return@Button
                        fileForDelete = null
                        viewModel.deleteFile(target.effectiveId) {
                            snackbarMessage = "Item deleted"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileForDelete = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Create Folder Dialog
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("New folder", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            val nameToCreate = newFolderName.trim()
                            showCreateFolderDialog = false
                            newFolderName = ""
                            viewModel.createFolder(nameToCreate) {
                                snackbarMessage = "Folder created"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Create", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showAddAccountDialog) {
        AddAccountDialog(
            onDismiss = { showAddAccountDialog = false },
            onSuccess = {
                showAddAccountDialog = false
                viewModel.refresh(force = true)
                snackbarMessage = "Drive connected"
            }
        )
    }

    var navDirection by remember { mutableStateOf(FilesNavDirection.FORWARD) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedFileIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedFileIds = emptySet() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear selection",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val allIds = filteredAndSortedFiles.map { it.effectiveId }.toSet()
                            selectedFileIds = if (selectedFileIds.size == allIds.size) emptySet() else allIds
                        }) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "Select all",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = {
                            val toDelete = files.filter { selectedFileIds.contains(it.effectiveId) }
                            selectedFileIds = emptySet()
                            coroutineScope.launch {
                                toDelete.forEach { f ->
                                    viewModel.deleteFile(f.effectiveId) {}
                                }
                                snackbarMessage = "${toDelete.size} items deleted"
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            } else {
                OriginalFilesHeaderBar(
                    activeAccount = activeAccount,
                    accounts = accounts,
                    showDriveDropdown = showDriveDropdown,
                    onToggleDriveDropdown = { showDriveDropdown = it },
                    onSelectAccount = { accId ->
                        showDriveDropdown = false
                        viewModel.selectAccount(accId)
                    },
                    onAddDrive = {
                        showDriveDropdown = false
                        showAddAccountDialog = true
                    },
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    isRefreshing = isRefreshing && files.isNotEmpty(),
                    onRefresh = { viewModel.refresh(force = true) },
                    isGridView = isGridView,
                    onToggleViewMode = {
                        coroutineScope.launch {
                            UserPreferences.setViewMode(
                                context,
                                if (isGridView) ViewMode.LIST else ViewMode.GRID
                            )
                        }
                    },
                    sortOrder = sortOrder,
                    showSortDropdown = showSortDropdown,
                    onToggleSortDropdown = { showSortDropdown = it },
                    onSelectSortOrder = { newOrder ->
                        coroutineScope.launch {
                            val (field, dir) = when (newOrder) {
                                FileSortOrder.DATE_DESC -> Pair(SortField.DATE, SortDirection.DESC)
                                FileSortOrder.DATE_ASC -> Pair(SortField.DATE, SortDirection.ASC)
                                FileSortOrder.NAME_ASC -> Pair(SortField.NAME, SortDirection.ASC)
                                FileSortOrder.NAME_DESC -> Pair(SortField.NAME, SortDirection.DESC)
                                FileSortOrder.SIZE_DESC -> Pair(SortField.SIZE, SortDirection.DESC)
                                FileSortOrder.SIZE_ASC -> Pair(SortField.SIZE, SortDirection.ASC)
                            }
                            UserPreferences.setSortField(context, field)
                            UserPreferences.setSortDirection(context, dir)
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = LocalBottomBarInset.current)
            ) {
                if (isFabExpanded) {
                    SmallFloatingActionButton(
                        onClick = { isFabExpanded = false; showCreateFolderDialog = true },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) { Icon(Icons.Default.CreateNewFolder, "New Folder") }
                    SmallFloatingActionButton(
                        onClick = { isFabExpanded = false; filePickerLauncher.launch("*/*") },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) { Icon(Icons.Default.FolderOpen, "Upload file") }
                    SmallFloatingActionButton(
                        onClick = { isFabExpanded = false; folderPickerLauncher.launch(null) },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) { Icon(Icons.Default.Folder, "Upload folder") }
                }
                FloatingActionButton(
                    onClick = { if (!isUploading) isFabExpanded = !isFabExpanded },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(56.dp)
                ) {
                    if (isUploading) {
                        AppLoadingIndicator(color = MaterialTheme.colorScheme.onPrimary, size = 24.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = if (isFabExpanded) "Close actions" else "Add",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing && files.isNotEmpty(),
            onRefresh = { viewModel.refresh(force = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentFolderId,
                transitionSpec = {
                    NavigationTransitions.sharedAxisEnter(forward = navDirection == FilesNavDirection.FORWARD) togetherWith
                    NavigationTransitions.sharedAxisExit(forward = navDirection == FilesNavDirection.FORWARD)
                },
                label = "FolderNavigationTransition",
                modifier = Modifier.fillMaxSize()
            ) { _ ->
                if (isRefreshing && files.isEmpty()) {
                    // Material Loader when opening an uncached folder or loading initial files
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        DriveAndBreadcrumbRow(
                            driveName = activeAccount?.effectiveName ?: "TG Drive",
                            breadcrumbs = breadcrumbs,
                            onBackClick = {
                                navDirection = FilesNavDirection.BACKWARD
                                viewModel.navigateBack()
                            },
                            onBreadcrumbClick = { idx ->
                                navDirection = FilesNavDirection.BACKWARD
                                viewModel.navigateToBreadcrumb(idx)
                            }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            AppLoadingIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                size = 48.dp
                            )
                        }
                    }
                } else if (isGridView) {
                    // GRID VIEW (2-column layout matching Image 1)
                    LazyVerticalGrid(
                        columns = gridCells,
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = LocalBottomBarInset.current + 130.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Drive & Breadcrumbs indicator row (`[Cloud] TG Drive`)
                        item(span = { GridItemSpan(maxLineSpan) }, key = "drive_breadcrumb_header") {
                            DriveAndBreadcrumbRow(
                                driveName = activeAccount?.effectiveName ?: "TG Drive",
                                breadcrumbs = breadcrumbs,
                                onBackClick = {
                                    navDirection = FilesNavDirection.BACKWARD
                                    viewModel.navigateBack()
                                },
                                onBreadcrumbClick = { idx ->
                                    navDirection = FilesNavDirection.BACKWARD
                                    viewModel.navigateToBreadcrumb(idx)
                                }
                            )
                        }

                        // Offline Indicator Banner
                        if (isOffline && files.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "offline_banner") {
                                OfflineBanner()
                            }
                        }

                        // Folders are rendered inline with the drive contents.
                        if (folders.isNotEmpty()) {
                            items(
                                items = folders,
                                key = { it.effectiveId.ifEmpty { "folder_${it.effectiveName}" } }
                            ) { folder ->
                                FolderGridItem(
                                    folder = folder,
                                    navController = rootNavController,
                                    accountId = effectiveAccountId,
                                    isSelected = selectedFileIds.contains(folder.effectiveId),
                                    isSelectionMode = isSelectionMode,
                                    onToggleSelect = { toggleSelection(folder.effectiveId) },
                                    onClick = {
                                        navDirection = FilesNavDirection.FORWARD
                                        viewModel.enterFolder(folder.effectiveId, folder.effectiveName)
                                    },
                                    onRenameClick = {
                                        fileForRename = folder
                                        renameInput = folder.effectiveName
                                    },
                                    onDeleteClick = { fileForDelete = folder },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        // Files continue directly after folders with no section divider.
                        if (regularFiles.isNotEmpty()) {
                            items(
                                items = regularFiles,
                                key = { it.effectiveId.ifEmpty { "file_${it.effectiveName}" } }
                            ) { file ->
                                FileGridItem(
                                    file = file,
                                    navController = rootNavController,
                                    accountId = effectiveAccountId,
                                    isSelected = selectedFileIds.contains(file.effectiveId),
                                    isSelectionMode = isSelectionMode,
                                    onToggleSelect = { toggleSelection(file.effectiveId) },
                                    onClick = { openFileInViewer(file, effectiveAccountId, rootNavController, regularFiles) },
                                    onRenameClick = {
                                        fileForRename = file
                                        renameInput = file.effectiveName
                                    },
                                    onDeleteClick = { fileForDelete = file },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        if (folders.isEmpty() && regularFiles.isEmpty() && !isRefreshing) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "empty_state") {
                                if (isOffline && files.isEmpty()) {
                                    FilesOfflineEmptyState(
                                        onRetry = { viewModel.refresh(force = true) }
                                    )
                                } else {
                                    FilesEmptyState(
                                        isSearch = searchQuery.isNotBlank(),
                                        onCreateFolder = { showCreateFolderDialog = true },
                                        onUploadFile = { filePickerLauncher.launch("*/*") }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // LIST VIEW
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = LocalBottomBarInset.current + 130.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "drive_breadcrumb_header") {
                            DriveAndBreadcrumbRow(
                                driveName = activeAccount?.effectiveName ?: "TG Drive",
                                breadcrumbs = breadcrumbs,
                                onBackClick = {
                                    navDirection = FilesNavDirection.BACKWARD
                                    viewModel.navigateBack()
                                },
                                onBreadcrumbClick = { idx ->
                                    navDirection = FilesNavDirection.BACKWARD
                                    viewModel.navigateToBreadcrumb(idx)
                                }
                            )
                        }

                        if (isOffline && files.isNotEmpty()) {
                            item(key = "offline_banner") {
                                OfflineBanner()
                            }
                        }

                        if (folders.isNotEmpty()) {
                            item(key = "folders_header") {
                                Text(
                                    text = "Folders",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                            }
                            items(
                                items = folders,
                                key = { it.effectiveId.ifEmpty { "folder_${it.effectiveName}" } }
                            ) { folder ->
                                FileRow(
                                    file = folder,
                                    navController = rootNavController,
                                    accountId = effectiveAccountId,
                                    isSelected = selectedFileIds.contains(folder.effectiveId),
                                    isSelectionMode = isSelectionMode,
                                    onToggleSelect = { toggleSelection(folder.effectiveId) },
                                    onClick = {
                                        navDirection = FilesNavDirection.FORWARD
                                        viewModel.enterFolder(folder.effectiveId, folder.effectiveName)
                                    },
                                    onRenameClick = {
                                        fileForRename = folder
                                        renameInput = folder.effectiveName
                                    },
                                    onDeleteClick = { fileForDelete = folder },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        if (regularFiles.isNotEmpty()) {
                            item(key = "files_header") {
                                Text(
                                    text = "Files",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                                )
                            }
                            items(
                                items = regularFiles,
                                key = { it.effectiveId.ifEmpty { "file_${it.effectiveName}" } }
                            ) { file ->
                                FileRow(
                                    file = file,
                                    navController = rootNavController,
                                    accountId = effectiveAccountId,
                                    isSelected = selectedFileIds.contains(file.effectiveId),
                                    isSelectionMode = isSelectionMode,
                                    onToggleSelect = { toggleSelection(file.effectiveId) },
                                    onClick = { openFileInViewer(file, effectiveAccountId, rootNavController, regularFiles) },
                                    onRenameClick = {
                                        fileForRename = file
                                        renameInput = file.effectiveName
                                    },
                                    onDeleteClick = { fileForDelete = file },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        if (folders.isEmpty() && regularFiles.isEmpty() && !isRefreshing) {
                            item(key = "empty_state") {
                                if (isOffline && files.isEmpty()) {
                                    FilesOfflineEmptyState(
                                        onRetry = { viewModel.refresh(force = true) }
                                    )
                                } else {
                                    FilesEmptyState(
                                        isSearch = searchQuery.isNotBlank(),
                                        onCreateFolder = { showCreateFolderDialog = true },
                                        onUploadFile = { filePickerLauncher.launch("*/*") }
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
private fun OriginalFilesHeaderBar(
    activeAccount: Account?,
    accounts: List<Account>,
    showDriveDropdown: Boolean,
    onToggleDriveDropdown: (Boolean) -> Unit,
    onSelectAccount: (String) -> Unit,
    onAddDrive: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    isGridView: Boolean,
    onToggleViewMode: () -> Unit,
    sortOrder: FileSortOrder,
    showSortDropdown: Boolean,
    onToggleSortDropdown: (Boolean) -> Unit,
    onSelectSortOrder: (FileSortOrder) -> Unit
) {
    val driveInitial = (activeAccount?.effectiveName?.firstOrNull() ?: 'T').uppercaseChar().toString()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Left Drive Avatar Button (48.dp circle with 2.dp blue border and bold initial)
        Box {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), CircleShape)
                    .clickable { onToggleDriveDropdown(true) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = driveInitial,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            DropdownMenu(
                expanded = showDriveDropdown,
                onDismissRequest = { onToggleDriveDropdown(false) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Text(
                    text = "Switch Drive",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                accounts.forEach { acc ->
                    val isSelected = acc.effectiveId == activeAccount?.effectiveId
                    val providerColor = when {
                        acc.effectiveType.contains("google", true) -> ProviderGoogle
                        acc.effectiveType.contains("one", true) -> ProviderOneDrive
                        else -> ProviderTelegram
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = acc.effectiveName,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(providerColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (acc.effectiveName.firstOrNull() ?: 'D').uppercaseChar().toString(),
                                    color = providerColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        trailingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        onClick = { onSelectAccount(acc.effectiveId) }
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Add Drive",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = onAddDrive
                )
            }
        }

        // 2. Center Search Pill Bar
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search in...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 3. View Mode Toggle Button (48.dp circle)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable { onToggleViewMode() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    contentDescription = if (isGridView) "Switch to List view" else "Switch to Grid view",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 4. Filter / Sort Button (48.dp circle)
        Box {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onToggleSortDropdown(true) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Sort & Filter",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = showSortDropdown,
                onDismissRequest = { onToggleSortDropdown(false) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Text(
                    text = "Sort by",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FileSortOrder.entries.forEach { order ->
                    val isSelected = sortOrder == order
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = order.displayName,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        trailingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        onClick = {
                            onToggleSortDropdown(false)
                            onSelectSortOrder(order)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DriveAndBreadcrumbRow(
    driveName: String,
    breadcrumbs: List<BreadcrumbItem>,
    onBackClick: () -> Unit,
    onBreadcrumbClick: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (breadcrumbs.size > 1) {
            item(key = "back_button") {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onBackClick() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        itemsIndexed(breadcrumbs) { index, item ->
            val isLast = index == breadcrumbs.lastIndex
            val isRoot = index == 0
            val label = if (isRoot) driveName else item.name

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !isLast) { onBreadcrumbClick(index) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                if (isRoot) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = label,
                    color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1
                )
            }

            if (!isLast) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun OfflineBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Viewing cached files (Offline)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderGridItem(
    folder: CloudFile,
    navController: NavController,
    accountId: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onRenameClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    val cardBg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        label = "FolderGridBg"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode && onToggleSelect != null) {
                        onToggleSelect()
                    } else {
                        onClick?.invoke()
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleSelect?.invoke()
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Top inner rounded box with centered blue Folder icon (matching Image 1)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .align(Alignment.TopEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom row: Folder name + "Folder" subtitle on left, 3-dot menu on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.effectiveName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Folder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Folder options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {
                                showMenu = false
                                onRenameClick?.invoke()
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDeleteClick?.invoke()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    file: CloudFile,
    navController: NavController,
    accountId: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onRenameClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    val cardBg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        label = "FileGridBg"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode && onToggleSelect != null) {
                        onToggleSelect()
                    } else {
                        onClick?.invoke()
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleSelect?.invoke()
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Top inner rounded box with full-bleed thumbnail or uppercase type badge ("OTHE")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            ) {
                FileThumb(
                    file = file,
                    accountId = accountId,
                    sizeDp = 160.dp,
                    cornerRadius = 16.dp,
                    showTypeBadgeInGrid = true,
                    modifier = Modifier.fillMaxSize()
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .align(Alignment.TopEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom row: File name + formatted size on left, 3-dot menu on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.effectiveName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = file.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Details", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {
                                showMenu = false
                                navController.navigate("file_detail/$accountId/${file.effectiveId}")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {
                                showMenu = false
                                onRenameClick?.invoke()
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDeleteClick?.invoke()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    file: CloudFile,
    navController: NavController,
    accountId: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onRenameClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    val rowBg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        label = "RowBg"
    )

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = rowBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode && onToggleSelect != null) {
                        onToggleSelect()
                    } else {
                        onClick?.invoke()
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleSelect?.invoke()
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            FileThumb(
                file = file,
                accountId = accountId,
                sizeDp = 48.dp,
                cornerRadius = 12.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.effectiveName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = if (file.isFolderItem) {
                    "${file.formattedDate} • Folder"
                } else {
                    "${file.formattedDate} • ${file.formattedSize}"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    DropdownMenuItem(
                        text = { Text("Details", color = MaterialTheme.colorScheme.onSurface) },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = {
                            showMenu = false
                            navController.navigate("file_detail/$accountId/${file.effectiveId}")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename", color = MaterialTheme.colorScheme.onSurface) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = {
                            showMenu = false
                            onRenameClick?.invoke()
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDeleteClick?.invoke()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FilesEmptyState(
    isSearch: Boolean,
    onCreateFolder: () -> Unit,
    onUploadFile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSearch) Icons.Default.Search else Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isSearch) "No matching files" else "No files in this folder",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isSearch) "Try searching for something else" else "Upload files or create folders to get started",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!isSearch) {
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onUploadFile,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload", fontSize = 13.sp)
                }
                Button(
                    onClick = onCreateFolder,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Folder", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun FilesOfflineEmptyState(
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Offline — Unable to load folder",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Check your connection and try again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Retry", fontSize = 13.sp)
        }
    }
}

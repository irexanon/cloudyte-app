package com.example.ui.screens

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
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
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.R
import com.example.api.Account
import com.example.api.ApiClient
import com.example.api.CloudFile
import com.example.api.CloudyteApi
import com.example.api.FolderRequest
import com.example.api.RenameRequest
import com.example.api.ShareRequest
import com.example.data.CloudFileRepository
import com.example.data.SortDirection
import com.example.data.SortField
import com.example.data.UserPreferences
import com.example.data.UserPreferencesState
import com.example.data.ViewMode
import com.example.db.AppDatabase
import com.example.db.LocalAccount
import com.example.ui.components.AppLoadingIndicator
import com.example.ui.components.FileThumb
import com.example.ui.components.LocalBottomBarInset
import com.example.ui.navigation.NavigationTransitions
import com.example.ui.openFileInViewer
import com.example.ui.theme.ProviderGoogle
import com.example.ui.theme.ProviderOneDrive
import com.example.ui.theme.ProviderTelegram
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

    private val currentFilesLocation: StateFlow<Pair<String?, String?>> =
        combine(currentAccountId, currentFolderId) { accId, folderId -> accId to folderId }
            .stateIn(viewModelScope, SharingStarted.Eagerly, currentAccountId.value to currentFolderId.value)

    private val observedFiles: StateFlow<Pair<Pair<String?, String?>, List<CloudFile>>?> =
        currentFilesLocation.flatMapLatest { location ->
            repo.observeFolder(location.first, location.second).map { rows -> location to rows }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Do not briefly expose the previous account/folder snapshot after navigation.
    val files: StateFlow<List<CloudFile>> = combine(currentFilesLocation, observedFiles) { location, snapshot ->
        snapshot?.takeIf { it.first == location }?.second.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<Account>> = db.accountDao().getAllAccountsFlow()
        .map { list -> list.map { it.toAccount() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _breadcrumbs = MutableStateFlow<List<BreadcrumbItem>>(listOf(BreadcrumbItem(null, "Files")))
    val breadcrumbs: StateFlow<List<BreadcrumbItem>> = _breadcrumbs.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _showRefreshIndicator = MutableStateFlow(false)
    val showRefreshIndicator: StateFlow<Boolean> = _showRefreshIndicator.asStateFlow()

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
    private var refreshGeneration: Long = 0L

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
            // Show cached Room rows first; refresh only if this account/folder is stale.
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

    fun refresh(force: Boolean = false, showIndicator: Boolean = false) {
        val accId = currentAccountId.value ?: accounts.value.firstOrNull()?.effectiveId
        val folderId = currentFolderId.value
        val generation = ++refreshGeneration
        refreshJob?.cancel()
        _isRefreshing.value = true
        _showRefreshIndicator.value = showIndicator
        _isOffline.value = false
        refreshJob = viewModelScope.launch {
            try {
                // Repository failures are returned as Result, not thrown automatically.
                repo.refresh(accId, folderId, force = force).getOrThrow()
                _isOffline.value = false
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.w("FilesViewModel", "Sync failed: ${e.message}")
                    _isOffline.value = true
                }
            } finally {
                if (generation == refreshGeneration) {
                    _isRefreshing.value = false
                    _showRefreshIndicator.value = false
                }
            }
        }
    }

    fun deleteFile(fileId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val accId = currentAccountId.value
            try {
                api.deleteFile(fileId, accId)
                db.cloudFileDao().deleteById(fileId)
                refresh(force = true)
            } catch (e: Exception) {
                Log.e("FilesViewModel", "Delete failed", e)
            } finally {
                onComplete()
            }
        }
    }

    fun renameFile(fileId: String, newName: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val accId = currentAccountId.value
            try {
                api.renameFile(fileId, accId, RenameRequest(name = newName))
                refresh(force = true)
            } catch (e: Exception) {
                Log.e("FilesViewModel", "Rename failed", e)
            } finally {
                onComplete()
            }
        }
    }

    fun createFolder(name: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val accId = currentAccountId.value
            try {
                api.createFolder(accId, FolderRequest(name = name, parentId = currentFolderId.value))
                refresh(force = true)
            } catch (e: Exception) {
                Log.e("FilesViewModel", "Create folder failed", e)
            } finally {
                onComplete()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilesScreen(
    navController: NavController,
    rootNavController: NavController,
    viewModel: FilesViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val files by viewModel.files.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val currentAccountId by viewModel.currentAccountId.collectAsState()
    val currentFolderId by viewModel.currentFolderId.collectAsState()
    val breadcrumbs by viewModel.breadcrumbs.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val showRefreshIndicator by viewModel.showRefreshIndicator.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()

    val prefsState by UserPreferences.observe(context).collectAsState(initial = UserPreferencesState())
    val isGridView = prefsState.viewMode == ViewMode.GRID

    // Grid configuration: Adaptive 104.dp by default, or user's gridColumns when configured
    val gridCells = remember(prefsState.gridColumns) {
        if (prefsState.gridColumns > 0) {
            GridCells.Fixed(prefsState.gridColumns)
        } else {
            GridCells.Adaptive(minSize = 104.dp)
        }
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
    var isSearchActive by remember { mutableStateOf(false) }
    var showSortDropdown by remember { mutableStateOf(false) }

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

    val effectiveAccountId = currentAccountId ?: accounts.firstOrNull()?.effectiveId ?: "root"

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

    var fileForRename by remember { mutableStateOf<CloudFile?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var fileForDelete by remember { mutableStateOf<CloudFile?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var fileForMoveCopy by remember { mutableStateOf<Pair<CloudFile, Boolean>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        // Handle upload
        uri?.let {
            snackbarMessage = "Uploading file..."
        }
    }
    val mediaPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            snackbarMessage = "Uploading media..."
        }
    }

    var fabMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val canScrollBackward by remember {
        derivedStateOf {
            if (isGridView) gridState.canScrollBackward else listState.canScrollBackward
        }
    }

    val topBarContainerColor by animateColorAsState(
        targetValue = if (canScrollBackward) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        label = "TopBarContainerColor"
    )

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
                        containerColor = topBarContainerColor,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        val currentTitle = if (breadcrumbs.size > 1) breadcrumbs.last().name else "Files"
                        Text(
                            text = currentTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        if (breadcrumbs.size > 1) {
                            IconButton(onClick = {
                                navDirection = FilesNavDirection.BACKWARD
                                viewModel.navigateBack()
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = !isSearchActive }) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (isSearchActive) "Close search" else "Search",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { viewModel.refresh(force = true, showIndicator = true) }) {
                            if (showRefreshIndicator && isRefreshing && files.isNotEmpty()) {
                                AppLoadingIndicator(size = 20.dp, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = topBarContainerColor,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        floatingActionButton = {
            if (isUploading) {
                FloatingActionButton(
                    onClick = { },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    AppLoadingIndicator(color = MaterialTheme.colorScheme.onPrimary, size = 24.dp)
                }
            } else {
                FloatingActionButtonMenu(
                    expanded = fabMenuExpanded,
                    button = {
                        ToggleFloatingActionButton(
                            checked = fabMenuExpanded,
                            onCheckedChange = { fabMenuExpanded = it }
                        ) {
                            val rotation by animateFloatAsState(
                                targetValue = if (fabMenuExpanded) 45f else 0f,
                                label = "FabRotation"
                            )
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = if (fabMenuExpanded) "Close menu" else "Add actions",
                                modifier = Modifier.rotate(rotation)
                            )
                        }
                    }
                ) {
                    FloatingActionButtonMenuItem(
                        onClick = {
                            fabMenuExpanded = false
                            filePickerLauncher.launch("*/*")
                        },
                        text = { Text("Upload file") },
                        icon = { Icon(Icons.Default.InsertDriveFile, contentDescription = null) }
                    )
                    FloatingActionButtonMenuItem(
                        onClick = {
                            fabMenuExpanded = false
                            mediaPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        text = { Text("Upload photo/video") },
                        icon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null) }
                    )
                    FloatingActionButtonMenuItem(
                        onClick = {
                            fabMenuExpanded = false
                            showCreateFolderDialog = true
                        },
                        text = { Text("New folder") },
                        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) }
                    )
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = showRefreshIndicator && isRefreshing && files.isNotEmpty(),
            onRefresh = { viewModel.refresh(force = true, showIndicator = true) },
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
                if (isGridView) {
                    // GRID VIEW
                    LazyVerticalGrid(
                        columns = gridCells,
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = LocalBottomBarInset.current + 88.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header 1: Active Search Bar (when search is toggled)
                        if (isSearchActive) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "search_header") {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search in files...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    shape = MaterialTheme.shapes.extraLarge,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                )
                            }
                        }

                        // Header 2: Horizontally scrollable account switcher chips row
                        item(span = { GridItemSpan(maxLineSpan) }, key = "account_switcher_header") {
                            AccountSwitcherChipRow(
                                accounts = accounts,
                                currentAccountId = currentAccountId,
                                onSelectAccount = { viewModel.selectAccount(it) },
                                onAddDrive = { showAddAccountDialog = true }
                            )
                        }

                        // Header 3: Controls Row (Sort Dropdown on left + connected ButtonGroup on right)
                        item(span = { GridItemSpan(maxLineSpan) }, key = "controls_header") {
                            ControlsRow(
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
                                },
                                isGridView = true,
                                onToggleViewMode = {
                                    coroutineScope.launch {
                                        UserPreferences.setViewMode(context, if (it) ViewMode.GRID else ViewMode.LIST)
                                    }
                                }
                            )
                        }

                        // Header 4: Breadcrumbs row (only when inside a folder)
                        if (breadcrumbs.size > 1) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "breadcrumbs_header") {
                                BreadcrumbsRow(
                                    breadcrumbs = breadcrumbs,
                                    onBreadcrumbClick = { idx ->
                                        navDirection = FilesNavDirection.BACKWARD
                                        viewModel.navigateToBreadcrumb(idx)
                                    }
                                )
                            }
                        }

                        // Offline Indicator Banner
                        if (isOffline && files.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "offline_banner") {
                                OfflineBanner()
                            }
                        }

                        // Folders section
                        if (folders.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "folders_header") {
                                Text(
                                    text = "Folders",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
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

                        // Files section
                        if (regularFiles.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "files_header") {
                                Text(
                                    text = "Files",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                            }
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

                        if (folders.isEmpty() && regularFiles.isEmpty()) {
                            if (isRefreshing && files.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "loading_skeleton") {
                                    FilesLoadingSkeleton(isGrid = true)
                                }
                            } else if (isOffline && files.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "load_error") {
                                    FilesLoadErrorState(onRetry = { viewModel.refresh(force = true, showIndicator = true) })
                                }
                            } else {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "empty_state") {
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
                            top = 8.dp,
                            bottom = LocalBottomBarInset.current + 88.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Header 1: Active Search Bar
                        if (isSearchActive) {
                            item(key = "search_header") {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search in files...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    shape = MaterialTheme.shapes.extraLarge,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                )
                            }
                        }

                        // Header 2: Account switcher chips row
                        item(key = "account_switcher_header") {
                            AccountSwitcherChipRow(
                                accounts = accounts,
                                currentAccountId = currentAccountId,
                                onSelectAccount = { viewModel.selectAccount(it) },
                                onAddDrive = { showAddAccountDialog = true }
                            )
                        }

                        // Header 3: Controls Row
                        item(key = "controls_header") {
                            ControlsRow(
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
                                },
                                isGridView = false,
                                onToggleViewMode = {
                                    coroutineScope.launch {
                                        UserPreferences.setViewMode(context, if (it) ViewMode.GRID else ViewMode.LIST)
                                    }
                                }
                            )
                        }

                        // Header 4: Breadcrumbs row (only when inside a folder)
                        if (breadcrumbs.size > 1) {
                            item(key = "breadcrumbs_header") {
                                BreadcrumbsRow(
                                    breadcrumbs = breadcrumbs,
                                    onBreadcrumbClick = { idx ->
                                        navDirection = FilesNavDirection.BACKWARD
                                        viewModel.navigateToBreadcrumb(idx)
                                    }
                                )
                            }
                        }

                        // Offline Banner
                        if (isOffline && files.isNotEmpty()) {
                            item(key = "offline_banner") {
                                OfflineBanner()
                            }
                        }

                        // Folders section
                        if (folders.isNotEmpty()) {
                            item(key = "folders_header") {
                                Text(
                                    text = "Folders",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
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

                        // Files section
                        if (regularFiles.isNotEmpty()) {
                            item(key = "files_header") {
                                Text(
                                    text = "Files",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
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

                        if (folders.isEmpty() && regularFiles.isEmpty()) {
                            if (isRefreshing && files.isEmpty()) {
                                item(key = "loading_skeleton") { FilesLoadingSkeleton(isGrid = false) }
                            } else if (isOffline && files.isEmpty()) {
                                item(key = "load_error") {
                                    FilesLoadErrorState(onRetry = { viewModel.refresh(force = true, showIndicator = true) })
                                }
                            } else {
                                item(key = "empty_state") {
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
fun AccountSwitcherChipRow(
    accounts: List<Account>, currentAccountId: String?,
    onSelectAccount: (String) -> Unit, onAddDrive: () -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        items(accounts, key = { it.effectiveId }) { acc ->
            val selected = acc.effectiveId == currentAccountId
            val providerColor = when {
                acc.effectiveType.contains("google", true) -> ProviderGoogle
                acc.effectiveType.contains("one", true) -> ProviderOneDrive
                else -> ProviderTelegram
            }
            val initial = (acc.effectiveName.firstOrNull() ?: 'D').uppercaseChar().toString()
            FilterChip(
                selected = selected, onClick = { onSelectAccount(acc.effectiveId) },
                label = { Text(acc.effectiveName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 144.dp)) },
                leadingIcon = {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(providerColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center) {
                        Text(initial, color = providerColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                },
                trailingIcon = if (selected) {
                    { Icon(Icons.Default.ArrowDropDown, contentDescription = "Active account", modifier = Modifier.size(18.dp)) }
                } else null
            )
        }
        item(key = "add_drive") {
            AssistChip(onClick = onAddDrive, label = { Text("Add drive") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ControlsRow(
    sortOrder: FileSortOrder,
    showSortDropdown: Boolean,
    onToggleSortDropdown: (Boolean) -> Unit,
    onSelectSortOrder: (FileSortOrder) -> Unit,
    isGridView: Boolean,
    onToggleViewMode: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Sort Dropdown on the left: ("Name" + chevron)
        Box {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.clickable { onToggleSortDropdown(true) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sortLabel = when (sortOrder) {
                        FileSortOrder.NAME_ASC, FileSortOrder.NAME_DESC -> "Name"
                        FileSortOrder.DATE_DESC, FileSortOrder.DATE_ASC -> "Date"
                        FileSortOrder.SIZE_DESC, FileSortOrder.SIZE_ASC -> "Size"
                    }
                    Text(
                        text = sortLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Sort order",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
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

        // Connected ButtonGroup on the right with ToggleButtons (List, Grid)
        ButtonGroup {
            ToggleButton(
                checked = !isGridView,
                onCheckedChange = { if (isGridView) onToggleViewMode(false) }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = "List view",
                    modifier = Modifier.size(18.dp)
                )
            }
            ToggleButton(
                checked = isGridView,
                onCheckedChange = { if (!isGridView) onToggleViewMode(true) }
            ) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = "Grid view",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun BreadcrumbsRow(
    breadcrumbs: List<BreadcrumbItem>,
    onBreadcrumbClick: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(breadcrumbs) { index, item ->
            val isLast = index == breadcrumbs.lastIndex
            val isRoot = index == 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(!isLast) { onBreadcrumbClick(index) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = if (isRoot) Icons.Default.Cloud else Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = item.name,
                    color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
            if (!isLast) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
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

@Composable
private fun FilesLoadingSkeleton(isGrid: Boolean) {
    val transition = rememberInfiniteTransition(label = "filesSkeleton")
    val alpha by transition.animateFloat(0.35f, 0.75f,
        animationSpec = infiniteRepeatable(tween(850), repeatMode = RepeatMode.Reverse), label = "filesSkeletonAlpha")
    val placeholder = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    if (isGrid) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(2) {
                        Column(Modifier.weight(1f).clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainer).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = placeholder, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().height(92.dp)) {}
                            Surface(color = placeholder, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(0.82f).height(12.dp)) {}
                            Surface(color = placeholder, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(0.48f).height(9.dp)) {}
                        }
                    }
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) {
                Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = placeholder, shape = MaterialTheme.shapes.medium, modifier = Modifier.size(48.dp)) {}
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Surface(color = placeholder, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(0.7f).height(12.dp)) {}
                        Surface(color = placeholder, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(0.42f).height(9.dp)) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesLoadErrorState(onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(64.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Couldn't load files", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text("Check your connection and try again.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp)); Text("Try again")
        }
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.92f)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Top-left: 40.dp rounded square in primaryContainer with folder icon in primary
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .align(Alignment.TopStart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Top-right: selection checkmark or overflow icon
            if (isSelected) {
                Box(
                    modifier = Modifier
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
            } else {
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
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

            // Bottom: folder name (labelLarge, 2 lines max, ellipsis) and subtitle ("N items" or "Folder")
            Column(
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Text(
                    text = folder.effectiveName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.92f)
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
        Column(modifier = Modifier.fillMaxSize()) {
            // Top 70%: thumbnail fills the space
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.70f)
            ) {
                FileThumb(
                    file = file,
                    accountId = accountId,
                    sizeDp = 120.dp,
                    cornerRadius = 14.dp,
                    modifier = Modifier.fillMaxSize()
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
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
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
                            modifier = Modifier.size(26.dp)
                        ) {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
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

            // Bottom 30%: name and size below
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.30f)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = file.effectiveName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
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
        elevation = CardDefaults.cardElevation(),
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

            // 48.dp rounded icon badge or thumbnail
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
                    maxLines = 1,
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
                .clip(MaterialTheme.shapes.medium)
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Folder", fontSize = 13.sp)
                }
            }
        }
    }
}

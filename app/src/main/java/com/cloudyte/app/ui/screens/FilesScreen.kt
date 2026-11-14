package com.cloudyte.app.ui.screens

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
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
import com.cloudyte.app.CloudyteApplication
import com.cloudyte.app.core.transfer.TransferCommand
import com.cloudyte.app.data.CloudFileRepository
import com.cloudyte.app.data.FileCryptoActions
import com.cloudyte.app.data.SortDirection
import com.cloudyte.app.data.SortField
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.UserPreferencesState
import com.cloudyte.app.data.ViewMode
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.LocalAccount
import com.cloudyte.app.db.TransferDirection
import com.cloudyte.app.ui.components.AppLoadingIndicator
import com.cloudyte.app.ui.components.FileThumb
import com.cloudyte.app.ui.components.LocalBottomBarInset
import com.cloudyte.app.ui.navigation.NavigationTransitions
import com.cloudyte.app.ui.openFileInViewer
import com.cloudyte.app.ui.theme.MetricLabelTextStyle
import com.cloudyte.app.ui.theme.ProviderGoogle
import com.cloudyte.app.ui.theme.ProviderOneDrive
import com.cloudyte.app.ui.theme.ProviderTelegram
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BreadcrumbItem(val id: String?, val name: String)

private data class FolderFilesSnapshot(
    val folderKey: Pair<String?, String?>,
    val files: List<CloudFile>
)

/**
 * Everything one folder screen needs to draw itself. AnimatedContent keeps the outgoing
 * pane's last value, so the old folder stays visually frozen while it slides out.
 */
private data class FolderPane(
    val folderId: String?,
    val folders: List<CloudFile>,
    val regularFiles: List<CloudFile>,
    val hasFiles: Boolean,
    val loaded: Boolean,
    val busy: Boolean,
    val breadcrumbs: List<BreadcrumbItem>
)

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
        repo = (application as? CloudyteApplication)?.container?.cloudFileRepository
            ?: CloudFileRepository(AppDatabase.getDatabase(application), ApiClient.apiService)
    )

    private val prefs = application.getSharedPreferences("cloudyte_drive_state", Context.MODE_PRIVATE)

    val currentAccountId = MutableStateFlow<String?>(prefs.getString("last_active_account_id", null))
    val currentFolderId = MutableStateFlow<String?>(null)

    val accounts: StateFlow<List<Account>> = db.accountDao().getAllAccountsFlow()
        .map { list -> list.map { it.toAccount() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val selectedFolder: StateFlow<Pair<String?, String?>> = combine(
        currentAccountId,
        accounts,
        currentFolderId
    ) { accId, accs, folderId ->
        val effectiveAccId = accId ?: accs.firstOrNull { it.isDefault == true }?.effectiveId ?: accs.firstOrNull()?.effectiveId
        effectiveAccId to folderId
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        currentAccountId.value to currentFolderId.value
    )

    // Every emission is tagged with the folder it belongs to, so a stale list from the
    // previous folder is never shown under the newly selected one.
    private val folderSnapshot: StateFlow<FolderFilesSnapshot?> = selectedFolder
        .flatMapLatest { folderKey ->
            repo.observeFolder(folderKey.first, folderKey.second).map { rows ->
                FolderFilesSnapshot(folderKey, rows)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val files: StateFlow<List<CloudFile>> = combine(selectedFolder, folderSnapshot) { folderKey, snapshot ->
        if (snapshot?.folderKey == folderKey) snapshot.files else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** True once the database has answered for the currently selected folder. */
    val isFolderDataLoaded: StateFlow<Boolean> = combine(selectedFolder, folderSnapshot) { folderKey, snapshot ->
        snapshot?.folderKey == folderKey
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchFocusTrigger = MutableStateFlow(0)
    val searchFocusTrigger: StateFlow<Int> = _searchFocusTrigger.asStateFlow()

    val displayFoldersAndFiles: StateFlow<Pair<List<CloudFile>, List<CloudFile>>> = combine(
        files,
        _searchQuery,
        UserPreferences.observe(application)
    ) { currentFiles, query, prefs ->
        val sortOrder = when {
            prefs.sortField == SortField.NAME && prefs.sortDirection == SortDirection.ASC -> FileSortOrder.NAME_ASC
            prefs.sortField == SortField.NAME && prefs.sortDirection == SortDirection.DESC -> FileSortOrder.NAME_DESC
            prefs.sortField == SortField.SIZE && prefs.sortDirection == SortDirection.ASC -> FileSortOrder.SIZE_ASC
            prefs.sortField == SortField.SIZE && prefs.sortDirection == SortDirection.DESC -> FileSortOrder.SIZE_DESC
            prefs.sortField == SortField.DATE && prefs.sortDirection == SortDirection.ASC -> FileSortOrder.DATE_ASC
            else -> FileSortOrder.DATE_DESC
        }
        val filtered = if (query.isBlank()) {
            currentFiles
        } else {
            currentFiles.filter { it.effectiveName.contains(query, ignoreCase = true) }
        }
        val sorted = when (sortOrder) {
            FileSortOrder.DATE_DESC -> filtered.sortedWith(compareByDescending<CloudFile> { it.isFolderItem }.thenByDescending { it.time ?: 0L })
            FileSortOrder.DATE_ASC -> filtered.sortedWith(compareByDescending<CloudFile> { it.isFolderItem }.thenBy { it.time ?: 0L })
            FileSortOrder.NAME_ASC -> filtered.sortedWith(compareByDescending<CloudFile> { it.isFolderItem }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.effectiveName })
            FileSortOrder.NAME_DESC -> filtered.sortedWith(compareByDescending<CloudFile> { it.isFolderItem }.thenByDescending(String.CASE_INSENSITIVE_ORDER) { it.effectiveName })
            FileSortOrder.SIZE_DESC -> filtered.sortedWith(compareByDescending<CloudFile> { it.isFolderItem }.thenByDescending { it.sizeBytes })
            FileSortOrder.SIZE_ASC -> filtered.sortedWith(compareByDescending<CloudFile> { it.isFolderItem }.thenBy { it.sizeBytes })
        }
        val foldersList = sorted.filter { it.isFolderItem }.distinctBy { it.effectiveId.ifEmpty { it.effectiveName } }
        val filesList = sorted.filter { !it.isFolderItem }.distinctBy { it.effectiveId.ifEmpty { it.effectiveName } }
        foldersList to filesList
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<CloudFile>() to emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun requestSearchFocus() {
        _searchFocusTrigger.value += 1
    }

    private val _breadcrumbs = MutableStateFlow<List<BreadcrumbItem>>(listOf(BreadcrumbItem(null, "Files")))
    val breadcrumbs: StateFlow<List<BreadcrumbItem>> = _breadcrumbs.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // True from the moment a folder is opened until we know whether it needs a network sync.
    // Keeps the "empty folder" state from flashing during that short gap.
    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

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
    private var refreshTarget: Pair<String?, String?>? = null
    private var refreshWasForced = false
    private var refreshRequestId = 0L

    init {
        viewModelScope.launch {
            try {
                val cached = db.accountDao().getAllAccounts()
                if (cached.isNotEmpty()) {
                    val current = currentAccountId.value
                    if (current == null || cached.none { it.id == current }) {
                        val def = cached.find { it.isDefault == true } ?: cached.first()
                        currentAccountId.value = def.id
                    }
                }
                val fetched = api.getAccounts()
                if (fetched.isNotEmpty()) {
                    db.accountDao().insertAccounts(fetched.map { LocalAccount.fromAccount(it) })
                    val current = currentAccountId.value
                    if (current == null || fetched.none { it.effectiveId == current }) {
                        val def = fetched.find { it.isDefault == true } ?: fetched.first()
                        currentAccountId.value = def.effectiveId
                    }
                }
            } catch (_: Exception) {}
            refresh(force = false)
        }
        viewModelScope.launch {
            accounts.collect { accs ->
                val current = currentAccountId.value
                if (accs.isNotEmpty() && (current == null || accs.none { it.effectiveId == current })) {
                    val defaultAcc = accs.find { it.isDefault == true } ?: accs.first()
                    currentAccountId.value = defaultAcc.effectiveId
                }
            }
        }
        // Refresh the current folder whenever one of our queued uploads completes.
        viewModelScope.launch {
            val app = getApplication<CloudyteApplication>()
            app.container.transferRepository
                .observeByStatus(com.cloudyte.app.db.TransferStatus.COMPLETED.name)
                .map { list ->
                    list.filter { it.direction == TransferDirection.UPLOAD.name }
                        .mapTo(mutableSetOf()) { it.id }
                }
                .distinctUntilChanged()
                .collect { completedIds ->
                    if (completedIds.isNotEmpty()) {
                        refresh(force = true)
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
        val target = accId to folderId
        val activeJob = refreshJob

        // Screen entry and ViewModel init can request the same refresh back to back.
        // Coalesce them instead of cancelling and restarting the request.
        if (activeJob?.isActive == true && refreshTarget == target && (!force || refreshWasForced)) {
            return
        }

        val requestId = ++refreshRequestId
        activeJob?.cancel()
        refreshTarget = target
        refreshWasForced = force
        _isRefreshing.value = false
        _isResolving.value = true

        refreshJob = viewModelScope.launch {
            try {
                if (!force && repo.isFolderFresh(accId, folderId)) {
                    if (requestId == refreshRequestId) _isOffline.value = false
                    return@launch
                }
                if (requestId == refreshRequestId) {
                    _isResolving.value = false
                    _isRefreshing.value = true
                }
                val res = repo.refresh(accId, folderId, force = force)
                if (requestId == refreshRequestId) _isOffline.value = res.isFailure
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException && requestId == refreshRequestId) {
                    Log.w("FilesViewModel", "Sync failed: ${e.message}")
                    _isOffline.value = true
                }
            } finally {
                // A cancelled older request must not hide the active request's state.
                if (requestId == refreshRequestId) {
                    _isRefreshing.value = false
                    _isResolving.value = false
                }
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

    fun enqueueFileUpload(uri: Uri) {
        enqueueUploadUris(listOf(uri))
    }

    fun createEncryptedCopy(targetFile: CloudFile) {
        viewModelScope.launch {
            val accId = targetFile.effectiveAccountId.ifBlank {
                currentAccountId.value
                    ?: accounts.value.firstOrNull { it.isDefault == true }?.effectiveId
                    ?: accounts.value.firstOrNull()?.effectiveId
                    ?: ""
            }
            val app = getApplication<Application>() as? CloudyteApplication ?: return@launch
            try {
                val feedback = FileCryptoActions.createEncryptedCopy(
                    context = app,
                    accountId = accId,
                    targetFile = targetFile,
                    fallbackParentFolderId = currentFolderId.value,
                    hasExplicitParentFolderId = true,
                    cloudFileRepository = repo,
                    transferRepository = app.container.transferRepository,
                    transferCoordinator = app.container.transferCoordinator
                )
                _userEvents.emit(feedback)
            } catch (e: Exception) {
                Log.e("FilesViewModel", "Encrypt copy failed", e)
                _userEvents.emit(e.message ?: "Could not queue encrypted copy")
            }
        }
    }

    fun decryptAndExportFile(targetFile: CloudFile) {
        viewModelScope.launch {
            val accId = targetFile.effectiveAccountId.ifBlank {
                currentAccountId.value
                    ?: accounts.value.firstOrNull { it.isDefault == true }?.effectiveId
                    ?: accounts.value.firstOrNull()?.effectiveId
                    ?: ""
            }
            val app = getApplication<Application>() as? CloudyteApplication ?: return@launch
            try {
                val feedback = FileCryptoActions.decryptAndExportFile(
                    context = app,
                    accountId = accId,
                    targetFile = targetFile,
                    transferRepository = app.container.transferRepository,
                    transferCoordinator = app.container.transferCoordinator
                )
                _userEvents.emit(feedback)
            } catch (e: Exception) {
                Log.e("FilesViewModel", "Decrypt export failed", e)
                _userEvents.emit(e.message ?: "Failed to decrypt file")
            }
        }
    }

    fun deleteSelected(fileIds: List<String>) {
        if (fileIds.isEmpty()) return
        viewModelScope.launch {
            val accId = currentAccountId.value
                ?: accounts.value.firstOrNull { it.isDefault == true }?.effectiveId
                ?: accounts.value.firstOrNull()?.effectiveId
            var queued = 0
            fileIds.forEach { fid ->
                if (repo.delete(fid, accId).isSuccess) queued++
            }
            if (queued > 0) {
                _userEvents.emit("$queued ${if (queued == 1) "delete" else "deletes"} queued for sync")
            } else {
                _userEvents.emit("Failed to delete selected items")
            }
        }
    }

    fun encryptSelected(items: List<CloudFile>) {
        val candidates = items.filter { !it.isEncrypted }
        if (candidates.isEmpty()) {
            viewModelScope.launch { _userEvents.emit("Selected items are already encrypted") }
            return
        }
        viewModelScope.launch {
            val app = getApplication<Application>() as? CloudyteApplication ?: return@launch
            val fallbackAcc = currentAccountId.value
                ?: accounts.value.firstOrNull { it.isDefault == true }?.effectiveId
                ?: accounts.value.firstOrNull()?.effectiveId
                ?: ""
            var processed = 0
            var lastMsg = ""
            for (targetFile in candidates) {
                val accId = targetFile.effectiveAccountId.ifBlank { fallbackAcc }
                val msg = runCatching {
                    FileCryptoActions.createEncryptedCopy(
                        context = app,
                        accountId = accId,
                        targetFile = targetFile,
                        fallbackParentFolderId = currentFolderId.value,
                        hasExplicitParentFolderId = true,
                        cloudFileRepository = repo,
                        transferRepository = app.container.transferRepository,
                        transferCoordinator = app.container.transferCoordinator
                    )
                }.getOrElse { it.message ?: "Failed" }
                lastMsg = msg
                processed++
            }
            _userEvents.emit(
                if (processed == 1) lastMsg
                else "Queued encryption for $processed selected items"
            )
        }
    }

    fun downloadSelected(items: List<CloudFile>) {
        val downloadables = items.filter { !it.isFolderItem }
        if (downloadables.isEmpty()) {
            viewModelScope.launch { _userEvents.emit("Select one or more files to download") }
            return
        }
        viewModelScope.launch {
            val app = getApplication<Application>() as? CloudyteApplication ?: return@launch
            val fallbackAcc = currentAccountId.value
                ?: accounts.value.firstOrNull { it.isDefault == true }?.effectiveId
                ?: accounts.value.firstOrNull()?.effectiveId
                ?: ""
            var processed = 0
            var lastMsg = ""
            for (targetFile in downloadables) {
                val accId = targetFile.effectiveAccountId.ifBlank { fallbackAcc }
                val msg = runCatching {
                    FileCryptoActions.decryptAndExportFile(
                        context = app,
                        accountId = accId,
                        targetFile = targetFile,
                        transferRepository = app.container.transferRepository,
                        transferCoordinator = app.container.transferCoordinator
                    )
                }.getOrElse { it.message ?: "Failed" }
                lastMsg = msg
                processed++
            }
            _userEvents.emit(
                if (processed == 1) lastMsg
                else "Started downloads/exports for $processed files"
            )
        }
    }

    fun enqueueFolderUpload(treeUri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        treeUri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val files = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val root = DocumentFile.fromTreeUri(context, treeUri)
                    buildList {
                        if (root != null) collectDocumentFiles(root, this)
                    }
                }
                if (files.isEmpty()) {
                    _userEvents.emit("No files found in selected folder")
                } else {
                    enqueueUploadUris(files.map { it.uri })
                }
            } catch (error: Exception) {
                Log.e("FilesViewModel", "Folder upload queue failed", error)
                _userEvents.emit(error.message ?: "Could not queue folder upload")
            }
        }
    }

    private fun collectDocumentFiles(file: DocumentFile, result: MutableList<DocumentFile>) {
        if (file.isDirectory) {
            file.listFiles().forEach { collectDocumentFiles(it, result) }
        } else if (file.isFile) {
            result += file
        }
    }

    private fun enqueueUploadUris(uris: List<Uri>) {
        viewModelScope.launch {
            _isUploading.value = true
            try {
                val accountId = currentAccountId.value
                    ?: accounts.value.firstOrNull { it.isDefault == true }?.effectiveId
                    ?: accounts.value.firstOrNull()?.effectiveId
                if (accountId.isNullOrBlank()) {
                    _userEvents.emit("Select a drive before uploading")
                    return@launch
                }
                val context = getApplication<Application>()
                val app = context.applicationContext as CloudyteApplication
                val encryptUploads = runCatching {
                    UserPreferences.observe(context).first().isUploadEncryptionEnabled
                }.getOrDefault(false)
                var queued = 0
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    uris.forEach { uri ->
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                        val metadata = readUploadMetadata(context, uri)
                        val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                        val transferId = app.container.transferRepository.enqueueTransfer(
                            accountId = accountId,
                            fileName = metadata.first,
                            localPath = uri.toString(),
                            remotePath = currentFolderId.value,
                            direction = TransferDirection.UPLOAD,
                            totalBytes = metadata.second,
                            isEncrypted = encryptUploads,
                            originalName = if (encryptUploads) metadata.first else null,
                            originalMimeType = if (encryptUploads) mimeType else null,
                            originalSize = if (encryptUploads) metadata.second else null
                        )
                        app.container.transferCoordinator.dispatch(TransferCommand.Start(transferId))
                        queued++
                    }
                }
                _userEvents.emit(if (queued == 1) "Upload added to transfers" else "$queued uploads added to transfers")
            } catch (error: Exception) {
                Log.e("FilesViewModel", "Upload queue failed", error)
                _userEvents.emit(error.message ?: "Could not queue upload")
            } finally {
                _isUploading.value = false
            }
        }
    }

    private fun readUploadMetadata(context: Context, uri: Uri): Pair<String, Long> {
        var name = "upload_${System.currentTimeMillis()}"
        var size = 0L
        context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex)?.let { name = it }
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return name to size
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
    val isFolderDataLoaded by viewModel.isFolderDataLoaded.collectAsStateWithLifecycle()
    val isResolving by viewModel.isResolving.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val currentAccountId by viewModel.currentAccountId.collectAsStateWithLifecycle()
    val currentFolderId by viewModel.currentFolderId.collectAsStateWithLifecycle()
    val breadcrumbs by viewModel.breadcrumbs.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isUploading by viewModel.isUploading.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()

    val prefsState by UserPreferences.observe(context).collectAsStateWithLifecycle(initialValue = UserPreferencesState())
    val isGridView = prefsState.viewMode == ViewMode.GRID
    val isCompactDensity = prefsState.layoutDensity.equals("COMPACT", ignoreCase = true) ||
        prefsState.viewMode == ViewMode.COMPACT_LIST
    val gridSpacing = if (isCompactDensity) 8.dp else 16.dp
    val listSpacing = if (isCompactDensity) 4.dp else 8.dp

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

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchFocusTrigger by viewModel.searchFocusTrigger.collectAsStateWithLifecycle()
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(searchFocusTrigger) {
        if (searchFocusTrigger > 0) {
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    var showSortDropdown by remember { mutableStateOf(false) }
    var showDriveDropdown by remember { mutableStateOf(false) }
    var showSelectionMoreMenu by remember { mutableStateOf(false) }
    var batchMoveCopyIsMove by remember { mutableStateOf<Boolean?>(null) }

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

    val displayFoldersAndFiles by viewModel.displayFoldersAndFiles.collectAsStateWithLifecycle()
    val folders = displayFoldersAndFiles.first
    val regularFiles = displayFoldersAndFiles.second
    val filteredAndSortedFiles = remember(folders, regularFiles) { folders + regularFiles }
    val selectedFilesList = remember(filteredAndSortedFiles, selectedFileIds) {
        filteredAndSortedFiles.filter { selectedFileIds.contains(it.effectiveId) }
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

    LaunchedEffect(currentAccountId) {
        // Room renders immediately; revalidate the visible folder once the active account is resolved.
        if (currentAccountId != null) {
            viewModel.refresh(force = false)
        }
    }

    val networkStatus by remember(context) {
        (context.applicationContext as? CloudyteApplication)?.container?.networkMonitor?.status
            ?: kotlinx.coroutines.flow.flowOf(com.cloudyte.app.core.network.NetworkStatus.VALIDATED_UNMETERED)
    }.collectAsStateWithLifecycle(initialValue = com.cloudyte.app.core.network.NetworkStatus.VALIDATED_UNMETERED)

    LaunchedEffect(networkStatus) {
        if (isOffline &&
            networkStatus != com.cloudyte.app.core.network.NetworkStatus.UNAVAILABLE &&
            networkStatus != com.cloudyte.app.core.network.NetworkStatus.CAPTIVE_PORTAL
        ) {
            viewModel.refresh(force = true)
        }
    }

    var fileForRename by remember { mutableStateOf<CloudFile?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var fileForDelete by remember { mutableStateOf<CloudFile?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::enqueueFileUpload)
    }
    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::enqueueFolderUpload)
    }


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
                                snackbarMessage = "Rename saved locally; syncing when online"
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
                            snackbarMessage = "Delete queued; it will sync when online"
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
                                snackbarMessage = "Folder saved locally; syncing when online"
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

    if (batchMoveCopyIsMove != null && selectedFilesList.isNotEmpty()) {
        MoveCopyDialog(
            files = selectedFilesList,
            sourceAccountId = effectiveAccountId,
            isMove = batchMoveCopyIsMove!!,
            onDismiss = { batchMoveCopyIsMove = null },
            onSuccess = { msg ->
                batchMoveCopyIsMove = null
                selectedFileIds = emptySet()
                snackbarMessage = msg
                viewModel.refresh(force = true)
            }
        )
    }

    var navDirection by remember { mutableStateOf(FilesNavDirection.FORWARD) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = LocalBottomBarInset.current)
            )
        },
        topBar = {
            Column {
            if (isSelectionMode) {
                val hasDownloadableFiles = selectedFilesList.any { !it.isFolderItem }
                val hasUnencryptedItems = selectedFilesList.any { !it.isEncrypted }
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
                        if (hasDownloadableFiles) {
                            IconButton(onClick = {
                                val targets = selectedFilesList
                                selectedFileIds = emptySet()
                                viewModel.downloadSelected(targets)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download selected",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (hasUnencryptedItems) {
                            IconButton(onClick = {
                                val targets = selectedFilesList
                                selectedFileIds = emptySet()
                                viewModel.encryptSelected(targets)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Encrypt selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = {
                            val toDelete = selectedFilesList.map { it.effectiveId }
                            selectedFileIds = emptySet()
                            viewModel.deleteSelected(toDelete)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        Box {
                            IconButton(onClick = { showSelectionMoreMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More batch actions",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = showSelectionMoreMenu,
                                onDismissRequest = { showSelectionMoreMenu = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Move to…", color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    onClick = {
                                        showSelectionMoreMenu = false
                                        batchMoveCopyIsMove = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy to…", color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    onClick = {
                                        showSelectionMoreMenu = false
                                        batchMoveCopyIsMove = false
                                    }
                                )
                            }
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
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    searchFocusRequester = searchFocusRequester,
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
            // M3 LinearProgressIndicator below header when refreshing or uploading
            AnimatedVisibility(
                visible = isRefreshing || isUploading,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            } // end Column topBar
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = LocalBottomBarInset.current)
            ) {
                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SpeedDialPillButton(
                            icon = Icons.Default.Upload,
                            label = "Upload files",
                            onClick = { isFabExpanded = false; filePickerLauncher.launch(arrayOf("*/*")) }
                        )
                        SpeedDialPillButton(
                            icon = Icons.Default.Folder,
                            label = "Upload folder",
                            onClick = { isFabExpanded = false; folderPickerLauncher.launch(null) }
                        )
                        SpeedDialPillButton(
                            icon = Icons.Default.CreateNewFolder,
                            label = "New folder",
                            onClick = { isFabExpanded = false; showCreateFolderDialog = true }
                        )
                    }
                }
                // Main circular FAB with pulsing ring animation when uploading
                Box(contentAlignment = Alignment.Center) {
                    if (isUploading) {
                        val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.6f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(900, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "fab_pulse_scale"
                        )
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.5f,
                            targetValue = 0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(900, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "fab_pulse_alpha"
                        )
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .scale(pulseScale)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                                    shape = CircleShape
                                )
                        )
                    }
                    val fabIconRotation by animateFloatAsState(
                        targetValue = if (isFabExpanded) 45f else 0f,
                        label = "files_fab_rotation"
                    )
                    FloatingActionButton(
                        onClick = { if (!isUploading) isFabExpanded = !isFabExpanded },
                        shape = CircleShape,
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
                                modifier = Modifier
                                    .size(26.dp)
                                    .rotate(fabIconRotation)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing && files.isNotEmpty(),
            onRefresh = { viewModel.refresh(force = true) },
            indicator = {},
            modifier = Modifier.fillMaxSize()
        ) {
            val currentPane = FolderPane(
                folderId = currentFolderId,
                folders = folders,
                regularFiles = regularFiles,
                hasFiles = files.isNotEmpty(),
                loaded = isFolderDataLoaded,
                busy = isRefreshing || isResolving,
                breadcrumbs = breadcrumbs
            )
            AnimatedContent(
                targetState = currentPane,
                // Same folder => update in place with no transition; new folder => slide.
                contentKey = { it.folderId },
                transitionSpec = {
                    NavigationTransitions.folderEnter(forward = navDirection == FilesNavDirection.FORWARD) togetherWith
                    NavigationTransitions.folderExit(forward = navDirection == FilesNavDirection.FORWARD)
                },
                label = "FolderNavigationTransition",
                modifier = Modifier.fillMaxSize()
            ) { pane ->
                // Each pane owns its scroll position, so folders never inherit each other's offset.
                val listState = rememberLazyListState()
                val gridState = rememberLazyGridState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                if (!pane.loaded || (!pane.hasFiles && pane.busy)) {
                    // Header-only pane until the database answers; the loader shows only for a real wait.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        DriveAndBreadcrumbRow(
                            driveName = activeAccount?.effectiveName ?: "TG Drive",
                            breadcrumbs = pane.breadcrumbs,
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
                            if (pane.loaded) {
                                AppLoadingIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    size = 48.dp
                                )
                            }
                        }
                    }
                } else if (isGridView) {
                    // GRID VIEW (2-column layout matching Image 1)
                    LazyVerticalGrid(
                        columns = gridCells,
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = if (isCompactDensity) 12.dp else 16.dp,
                            end = if (isCompactDensity) 12.dp else 16.dp,
                            top = if (isCompactDensity) 4.dp else 8.dp,
                            bottom = LocalBottomBarInset.current + 128.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                        verticalArrangement = Arrangement.spacedBy(gridSpacing)
                    ) {
                        // Drive & Breadcrumbs indicator row (`[Cloud] TG Drive`)
                        item(span = { GridItemSpan(maxLineSpan) }, key = "drive_breadcrumb_header") {
                            DriveAndBreadcrumbRow(
                                driveName = activeAccount?.effectiveName ?: "TG Drive",
                                breadcrumbs = pane.breadcrumbs,
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
                        if (isOffline && pane.hasFiles) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "offline_banner") {
                                OfflineBanner()
                            }
                        }

                        // Folders are rendered inline with the drive contents.
                        if (pane.folders.isNotEmpty()) {
                            items(
                                items = pane.folders,
                                key = { it.effectiveId.ifEmpty { "folder_${it.effectiveName}" } }
                            ) { folder ->
                                FolderGridItem(
                                    folder = folder,
                                    navController = rootNavController,
                                    accountId = effectiveAccountId,
                                    isCompact = isCompactDensity,
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
                                    onEncryptClick = { viewModel.createEncryptedCopy(folder) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        // Files continue directly after folders with no section divider.
                        if (pane.regularFiles.isNotEmpty()) {
                            items(
                                items = pane.regularFiles,
                                key = { it.effectiveId.ifEmpty { "file_${it.effectiveName}" } }
                            ) { file ->
                                FileGridItem(
                                    file = file,
                                    navController = rootNavController,
                                    accountId = effectiveAccountId,
                                    isCompact = isCompactDensity,
                                    isSelected = selectedFileIds.contains(file.effectiveId),
                                    isSelectionMode = isSelectionMode,
                                    onToggleSelect = { toggleSelection(file.effectiveId) },
                                    onClick = { openFileInViewer(file, effectiveAccountId, rootNavController, pane.regularFiles) },
                                    onRenameClick = {
                                        fileForRename = file
                                        renameInput = file.effectiveName
                                    },
                                    onDeleteClick = { fileForDelete = file },
                                    onEncryptClick = { viewModel.createEncryptedCopy(file) },
                                    onDecryptExportClick = { viewModel.decryptAndExportFile(file) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        if (pane.folders.isEmpty() && pane.regularFiles.isEmpty() && !pane.busy) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "empty_state") {
                                if (isOffline && !pane.hasFiles) {
                                    FilesOfflineEmptyState(
                                        onRetry = { viewModel.refresh(force = true) }
                                    )
                                } else {
                                    FilesEmptyState(
                                        isSearch = searchQuery.isNotBlank(),
                                        onCreateFolder = { showCreateFolderDialog = true },
                                        onUploadFile = { filePickerLauncher.launch(arrayOf("*/*")) }
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
                            start = if (isCompactDensity) 12.dp else 16.dp,
                            end = if (isCompactDensity) 12.dp else 16.dp,
                            top = if (isCompactDensity) 4.dp else 8.dp,
                            bottom = LocalBottomBarInset.current + 128.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(listSpacing)
                    ) {
                        item(key = "drive_breadcrumb_header") {
                            DriveAndBreadcrumbRow(
                                driveName = activeAccount?.effectiveName ?: "TG Drive",
                                breadcrumbs = pane.breadcrumbs,
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

                        if (isOffline && pane.hasFiles) {
                            item(key = "offline_banner") {
                                OfflineBanner()
                            }
                        }

                        if (pane.folders.isNotEmpty()) {
                            item(key = "folders_header") {
                                Text(
                                    text = "Folders",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                )
                            }
                            items(
                                items = pane.folders,
                                key = { it.effectiveId.ifEmpty { "folder_${it.effectiveName}" } }
                            ) { folder ->
                                FileRow(
                                    file = folder,
                                    navController = rootNavController,
                                    accountId = effectiveAccountId,
                                    isCompact = isCompactDensity,
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
                                    onEncryptClick = { viewModel.createEncryptedCopy(folder) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        if (pane.regularFiles.isNotEmpty()) {
                            item(key = "files_header") {
                                Text(
                                    text = "Files",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                )
                            }
                            items(
                                items = pane.regularFiles,
                                key = { it.effectiveId.ifEmpty { "file_${it.effectiveName}" } }
                            ) { file ->
                                FileRow(
                                    file = file,
                                    navController = rootNavController,
                                    accountId = effectiveAccountId,
                                    isCompact = isCompactDensity,
                                    isSelected = selectedFileIds.contains(file.effectiveId),
                                    isSelectionMode = isSelectionMode,
                                    onToggleSelect = { toggleSelection(file.effectiveId) },
                                    onClick = { openFileInViewer(file, effectiveAccountId, rootNavController, pane.regularFiles) },
                                    onRenameClick = {
                                        fileForRename = file
                                        renameInput = file.effectiveName
                                    },
                                    onDeleteClick = { fileForDelete = file },
                                    onEncryptClick = { viewModel.createEncryptedCopy(file) },
                                    onDecryptExportClick = { viewModel.decryptAndExportFile(file) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        if (pane.folders.isEmpty() && pane.regularFiles.isEmpty() && !pane.busy) {
                            item(key = "empty_state") {
                                if (isOffline && !pane.hasFiles) {
                                    FilesOfflineEmptyState(
                                        onRetry = { viewModel.refresh(force = true) }
                                    )
                                } else {
                                    FilesEmptyState(
                                        isSearch = searchQuery.isNotBlank(),
                                        onCreateFolder = { showCreateFolderDialog = true },
                                        onUploadFile = { filePickerLauncher.launch(arrayOf("*/*")) }
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = isFabExpanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.36f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isFabExpanded = false }
            )
        }
        }
    }
}

@Composable
private fun SpeedDialPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
        tonalElevation = 4.dp,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
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
    searchFocusRequester: FocusRequester,
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Left Drive Avatar Button (48.dp circle with 2.dp border and bold initial)
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
                    style = MaterialTheme.typography.titleMedium,
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
                                    style = MaterialTheme.typography.labelMedium,
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
                    .padding(start = 16.dp, end = if (searchQuery.isNotEmpty()) 4.dp else 16.dp),
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
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocusRequester)
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
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
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (breadcrumbs.size > 1) {
            item(key = "back_button") {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.size(32.dp)
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
        }

        itemsIndexed(breadcrumbs) { index, item ->
            val isLast = index == breadcrumbs.lastIndex
            val isRoot = index == 0
            val label = if (isRoot) driveName else item.name

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !isLast) { onBreadcrumbClick(index) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
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
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
            style = MaterialTheme.typography.labelMedium
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
    isCompact: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onRenameClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onEncryptClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    val cardBg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        label = "FolderGridBg"
    )

    Card(
        shape = RoundedCornerShape(if (isCompact) 12.dp else 16.dp),
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
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top full-bleed preview box with centered Folder icon (no border around preview)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (isCompact) 1.45f else 1.15f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (isCompact) 36.dp else 44.dp)
                )

                if (folder.isEncrypted) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Encrypted folder",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

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

            // Bottom row: Folder name + "Folder" subtitle on left, 3-dot menu on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 0.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.effectiveName,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = "Folder",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Folder options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Encrypt to new folder", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showMenu = false
                                onEncryptClick?.invoke()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Details", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {
                                showMenu = false
                                navController.navigate("file_detail/$accountId/${folder.effectiveId}")
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
fun FileGridItem(
    file: CloudFile,
    navController: NavController,
    accountId: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isCompact: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onRenameClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onEncryptClick: (() -> Unit)? = null,
    onDecryptExportClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    val cardBg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        label = "FileGridBg"
    )

    Card(
        shape = RoundedCornerShape(if (isCompact) 12.dp else 16.dp),
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
        Column(modifier = Modifier.fillMaxWidth()) {
            // Full-bleed top preview thumbnail (no border around thumbnail)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (isCompact) 1.45f else 1.15f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            ) {
                FileThumb(
                    file = file,
                    accountId = accountId,
                    sizeDp = 160.dp,
                    cornerRadius = 0.dp,
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

            // Bottom row: File name + formatted size on left, 3-dot menu on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 0.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.effectiveName,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = file.formattedSize,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        if (file.isEncrypted) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Encrypted",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (file.isEncrypted) "Decrypt / Export" else "Create encrypted copy",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (file.isEncrypted) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                showMenu = false
                                if (file.isEncrypted) {
                                    onDecryptExportClick?.invoke()
                                } else {
                                    onEncryptClick?.invoke()
                                }
                            }
                        )
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
    isCompact: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onRenameClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onEncryptClick: (() -> Unit)? = null,
    onDecryptExportClick: (() -> Unit)? = null
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
            .heightIn(min = if (isCompact) 52.dp else 64.dp)
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
                .padding(
                    horizontal = if (isCompact) 10.dp else 12.dp,
                    vertical = if (isCompact) 4.dp else 8.dp
                ),
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
                sizeDp = if (isCompact) 36.dp else 48.dp,
                cornerRadius = if (isCompact) 8.dp else 12.dp
            )

            Spacer(modifier = Modifier.width(if (isCompact) 10.dp else 12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.effectiveName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    lineHeight = if (isCompact) 15.sp else 17.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (isCompact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = if (file.isFolderItem) {
                    "${file.formattedDate} • Folder"
                } else {
                    "${file.formattedDate} • ${file.formattedSize}"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (file.isEncrypted) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Encrypted",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "E2EE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
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
                        text = {
                            Text(
                                text = when {
                                    file.isEncrypted -> "Decrypt / Export"
                                    file.isFolderItem -> "Encrypt to new folder"
                                    else -> "Create encrypted copy"
                                },
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (file.isEncrypted) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            showMenu = false
                            if (file.isEncrypted) {
                                onDecryptExportClick?.invoke()
                            } else {
                                onEncryptClick?.invoke()
                            }
                        }
                    )
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
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onUploadFile,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onCreateFolder,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Folder", style = MaterialTheme.typography.labelLarge)
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
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Check your connection and try again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry", style = MaterialTheme.typography.labelLarge)
        }
    }
}

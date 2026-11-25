package com.cloudyte.app.ui.screens

import android.app.Application
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cloudyte.app.CloudyteApplication
import com.cloudyte.app.R
import com.cloudyte.app.core.network.NetworkStatus
import com.cloudyte.app.core.network.isOnline
import com.cloudyte.app.api.Account
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.api.CloudyteApi
import com.cloudyte.app.api.FileRepository
import com.cloudyte.app.api.StorageCategories
import com.cloudyte.app.api.StorageSummary
import com.cloudyte.app.api.UserProfile
import com.cloudyte.app.data.ALL_ACCOUNTS_STORAGE_ID
import com.cloudyte.app.data.CloudFileRepository
import com.cloudyte.app.data.FileCryptoActions
import com.cloudyte.app.data.StorageCategoryType
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.UserPreferencesState
import com.cloudyte.app.data.aggregateStorageSummaries
import com.cloudyte.app.data.calculateSegmentProportions
import com.cloudyte.app.data.formatStorageSize
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.LocalAccount
import com.cloudyte.app.ui.components.ExpressiveStarburstBadge
import com.cloudyte.app.ui.components.FileThumb
import com.cloudyte.app.ui.components.LocalBottomBarInset
import com.cloudyte.app.ui.components.WavyProgressBar
import com.cloudyte.app.ui.openFileInViewer
import com.cloudyte.app.ui.theme.RobotoFlexFontFamily
import com.cloudyte.app.ui.theme.cloudyteColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    application: Application,
    private val db: AppDatabase,
    private val api: CloudyteApi,
    private val repository: CloudFileRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        db = AppDatabase.getDatabase(application),
        api = ApiClient.apiService,
        repository = (application as? com.cloudyte.app.CloudyteApplication)?.container?.cloudFileRepository
            ?: CloudFileRepository(AppDatabase.getDatabase(application), ApiClient.apiService)
    )

    private val drivePrefs by lazy {
        application.getSharedPreferences("cloudyte_drive_state", android.content.Context.MODE_PRIVATE)
    }

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _currentAccountId = MutableStateFlow<String?>(null)
    val currentAccountId: StateFlow<String?> = _currentAccountId.asStateFlow()

    private val _recentFiles = MutableStateFlow<List<CloudFile>>(emptyList())
    val recentFiles: StateFlow<List<CloudFile>> = _recentFiles.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasInitialContent = MutableStateFlow(false)
    val hasInitialContent: StateFlow<Boolean> = _hasInitialContent.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _immediateStorageSummary = MutableStateFlow<StorageSummary?>(null)

    private var loadJob: Job? = null
    private var lastLoadForced = false
    @Volatile
    private var lastLoadedAtMs: Long = 0L

    companion object {
        internal const val AUTO_REFRESH_INTERVAL_MS = 30_000L
    }

    private val preferredStorageAccountFlow = UserPreferences.observe(application)
        .map { it.homeStorageAccountId }
        .distinctUntilChanged()

    val selectedStorageAccountId: StateFlow<String> = combine(
        preferredStorageAccountFlow,
        _accounts
    ) { preferredId, accs ->
        val defaultAccId = accs.firstOrNull { it.isDefault == true }?.effectiveId ?: accs.firstOrNull()?.effectiveId
        when {
            !preferredId.isNullOrBlank() && (preferredId == ALL_ACCOUNTS_STORAGE_ID || accs.isEmpty() || accs.any { it.effectiveId == preferredId }) ->
                preferredId
            !defaultAccId.isNullOrBlank() -> defaultAccId
            accs.size == 1 -> accs.first().effectiveId.ifBlank { ALL_ACCOUNTS_STORAGE_ID }
            else -> ALL_ACCOUNTS_STORAGE_ID
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ALL_ACCOUNTS_STORAGE_ID)

    val storageSummary: StateFlow<StorageSummary?> = combine(
        selectedStorageAccountId,
        _accounts
    ) { accountId, accs ->
        if (accountId == ALL_ACCOUNTS_STORAGE_ID && accs.isEmpty()) null else accountId
    }
        .distinctUntilChanged()
        .flatMapLatest { accountId ->
            if (accountId == null) {
                flowOf(null)
            } else {
                combine(
                    repository.observeStorage(accountId),
                    _immediateStorageSummary
                ) { observed, immediate ->
                    observed ?: immediate?.takeIf {
                        it.accountId == accountId || (accountId == ALL_ACCOUNTS_STORAGE_ID && it.accountId == "all")
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isStorageRefreshing: StateFlow<Boolean> = combine(
        selectedStorageAccountId,
        repository.storageRefreshing
    ) { accountId, refreshingSet ->
        accountId in refreshingSet
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val storageRefreshError: StateFlow<String?> = combine(
        selectedStorageAccountId,
        repository.storageRefreshErrors
    ) { accountId, errorsMap ->
        errorsMap[accountId]
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (_profile.value == null) {
                drivePrefs.getString("cached_username", null)?.takeIf { it.isNotBlank() }?.let {
                    if (_profile.value == null) {
                        _profile.value = UserProfile(username = it)
                    }
                }
            }
        }
        viewModelScope.launch {
            db.accountDao().getAllAccountsFlow()
                .map { rows -> rows.map { it.toAccount() } }
                .collect { accs ->
                    if (accs.isNotEmpty()) {
                        _accounts.value = accs
                    }
                }
        }
    }

    fun selectStorageAccount(accountId: String) {
        viewModelScope.launch {
            UserPreferences.setHomeStorageAccountId(getApplication(), accountId)
        }
    }

    private fun resolveTargetStorageId(accs: List<Account>, preferredStorageId: String?): String {
        val defaultAcc = accs.firstOrNull { it.isDefault == true } ?: accs.firstOrNull()
        val resolvedDefaultId = defaultAcc?.effectiveId?.takeIf { it.isNotBlank() && it != "root" }
        return when {
            !preferredStorageId.isNullOrBlank() &&
                (preferredStorageId == ALL_ACCOUNTS_STORAGE_ID || accs.any { it.effectiveId == preferredStorageId }) ->
                preferredStorageId
            !resolvedDefaultId.isNullOrBlank() -> resolvedDefaultId
            else -> ALL_ACCOUNTS_STORAGE_ID
        }
    }

    fun loadData(forceStorageRefresh: Boolean = false) {
        val active = loadJob
        if (active?.isActive == true && (!forceStorageRefresh || lastLoadForced)) {
            return
        }
        if (forceStorageRefresh) {
            active?.cancel()
        }
        lastLoadForced = forceStorageRefresh
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val cachedAccounts = db.accountDao().getAllAccounts().map { it.toAccount() }
                var cachedPreferredStorageId: String? = null
                if (cachedAccounts.isNotEmpty()) {
                    cachedPreferredStorageId = preferredStorageAccountFlow.first()
                    _accounts.value = cachedAccounts
                    val cachedDefault = cachedAccounts.firstOrNull { it.isDefault == true } ?: cachedAccounts.firstOrNull()
                    _currentAccountId.value = cachedDefault?.effectiveId?.takeIf { it.isNotBlank() && it != "root" }
                    val cachedTargetId = resolveTargetStorageId(cachedAccounts, cachedPreferredStorageId)
                    val cachedSummary = db.storageSummaryDao().getByAccountId(
                        if (cachedTargetId == ALL_ACCOUNTS_STORAGE_ID) "all" else cachedTargetId
                    )
                    if (cachedSummary != null) {
                        _immediateStorageSummary.value = cachedSummary.toStorageSummary()
                        _hasInitialContent.value = true
                    }
                }

                val cachedFiles = db.cloudFileDao().getRecentFiles(8)
                if (cachedFiles.isNotEmpty()) {
                    _recentFiles.value = cachedFiles.map { it.toCloudFile() }.take(8)
                    FileRepository.cacheFiles(_recentFiles.value)
                }

                coroutineScope {
                    val profileDeferred = async {
                        runCatching { api.getProfile() }
                            .onFailure { Log.w("HomeViewModel", "Profile call failed: ${it.message}") }
                            .getOrNull()
                    }
                    val accountsDeferred = async {
                        runCatching {
                            com.cloudyte.app.data.DriveAliasStore.applyAliases(
                                getApplication(),
                                api.getAccounts()
                            )
                        }
                            .onFailure { Log.w("HomeViewModel", "Accounts call failed: ${it.message}") }
                    }
                    val preferredDeferred = async {
                        cachedPreferredStorageId ?: preferredStorageAccountFlow.first()
                    }

                    // If cached accounts already exist, start storage & file refresh immediately in parallel
                    val preTargetStorageId = if (cachedAccounts.isNotEmpty()) {
                        resolveTargetStorageId(cachedAccounts, cachedPreferredStorageId)
                    } else null
                    val preStorageJob = preTargetStorageId?.let { targetId ->
                        val needsForce = forceStorageRefresh ||
                            (storageSummary.value?.usedBytes == 0L && cachedAccounts.any { it.usageBytes > 0L }) ||
                            storageSummary.value?.categories == null
                        async { repository.refreshStorage(targetId, force = needsForce) }
                    }
                    val preAccId = preTargetStorageId?.takeIf { it != ALL_ACCOUNTS_STORAGE_ID }
                        ?: (cachedAccounts.firstOrNull { it.isDefault == true } ?: cachedAccounts.firstOrNull())
                            ?.effectiveId?.takeIf { it.isNotBlank() && it != "root" }
                    val preFilesJob = if (cachedAccounts.isNotEmpty()) {
                        async { repository.refresh(preAccId, "root", force = forceStorageRefresh) }
                    } else null

                    val accountsResult = accountsDeferred.await()
                    val preferredStorageId = preferredDeferred.await()
                    val freshAccounts = accountsResult.getOrNull()
                    if (freshAccounts != null) {
                        lastLoadedAtMs = System.currentTimeMillis()
                        _accounts.value = freshAccounts
                        if (freshAccounts.isNotEmpty()) {
                            db.accountDao().insertAccounts(freshAccounts.map { LocalAccount.fromAccount(it) })
                        }
                    } else if (cachedAccounts.isEmpty()) {
                        _errorMessage.value = accountsResult.exceptionOrNull()?.localizedMessage
                            ?: "Failed to load cloud data"
                    }

                    val activeAccounts = _accounts.value
                    val defaultAcc = activeAccounts.firstOrNull { it.isDefault == true } ?: activeAccounts.firstOrNull()
                    val resolvedDefaultId = defaultAcc?.effectiveId?.takeIf { it.isNotBlank() && it != "root" }
                    val targetStorageId = resolveTargetStorageId(activeAccounts, preferredStorageId)
                    val accId = targetStorageId.takeIf { it != ALL_ACCOUNTS_STORAGE_ID } ?: resolvedDefaultId
                    _currentAccountId.value = accId

                    val storageJob = if (preTargetStorageId == targetStorageId && preStorageJob != null) {
                        preStorageJob
                    } else {
                        val needsForce = forceStorageRefresh ||
                            (storageSummary.value?.usedBytes == 0L && activeAccounts.any { it.usageBytes > 0L }) ||
                            storageSummary.value?.categories == null
                        async { repository.refreshStorage(targetStorageId, force = needsForce) }
                    }
                    val filesJob = if (preFilesJob != null && preAccId == accId) {
                        preFilesJob
                    } else {
                        async { repository.refresh(accId, "root", force = forceStorageRefresh) }
                    }

                    profileDeferred.await()?.let { fetchedProfile ->
                        _profile.value = fetchedProfile
                        fetchedProfile.username?.takeIf { it.isNotBlank() }?.let { uname ->
                            drivePrefs.edit().putString("cached_username", uname).apply()
                        }
                    }
                    storageJob.await()
                    filesJob.await()
                    val persistedSummary = db.storageSummaryDao().getByAccountId(
                        if (targetStorageId == ALL_ACCOUNTS_STORAGE_ID) "all" else targetStorageId
                    )
                    if (persistedSummary != null) {
                        _immediateStorageSummary.value = persistedSummary.toStorageSummary()
                    }
                }

                val updatedFiles = db.cloudFileDao().getRecentFiles(8).map { it.toCloudFile() }
                if (updatedFiles.isNotEmpty()) {
                    _recentFiles.value = updatedFiles.take(8)
                    FileRepository.cacheFiles(updatedFiles)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("HomeViewModel", "General data loading error", e)
                if (_accounts.value.isEmpty()) {
                    _errorMessage.value = e.localizedMessage ?: "Failed to load cloud data"
                }
            } finally {
                _hasInitialContent.value = true
                _isLoading.value = false
            }
        }
    }

    fun refreshIfNeeded(
        isOnline: Boolean,
        wasOffline: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (!isOnline) return
        val stale = lastLoadedAtMs == 0L || (nowMs - lastLoadedAtMs) >= AUTO_REFRESH_INTERVAL_MS
        if (wasOffline || stale) {
            loadData(forceStorageRefresh = wasOffline)
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetLoadTimestampForTest(timestampMs: Long) {
        lastLoadedAtMs = timestampMs
    }

    fun createFolder(name: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val accId = _currentAccountId.value
                val res = repository.createFolder(name, accId, "root")
                if (res.isSuccess) {
                    loadData(forceStorageRefresh = false)
                    onSuccess()
                } else {
                    _errorMessage.value = res.exceptionOrNull()?.message ?: "Failed to create folder"
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Create folder error", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to create folder"
            }
        }
    }

    fun createEncryptedCopy(accountId: String, targetFile: CloudFile, onFeedback: (String) -> Unit) {
        viewModelScope.launch {
            val app = getApplication<Application>() as? com.cloudyte.app.CloudyteApplication ?: return@launch
            try {
                val feedback = FileCryptoActions.createEncryptedCopy(
                    context = app,
                    accountId = accountId,
                    targetFile = targetFile,
                    fallbackParentFolderId = null,
                    cloudFileRepository = repository,
                    transferRepository = app.container.transferRepository,
                    transferCoordinator = app.container.transferCoordinator
                )
                onFeedback(feedback)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Encrypt copy failed", e)
                onFeedback(e.message ?: "Could not queue encrypted copy")
            }
        }
    }

    fun decryptAndExportFile(accountId: String, targetFile: CloudFile, onFeedback: (String) -> Unit) {
        viewModelScope.launch {
            val app = getApplication<Application>() as? com.cloudyte.app.CloudyteApplication ?: return@launch
            try {
                val feedback = FileCryptoActions.decryptAndExportFile(
                    context = app,
                    accountId = accountId,
                    targetFile = targetFile,
                    transferRepository = app.container.transferRepository,
                    transferCoordinator = app.container.transferCoordinator
                )
                if (targetFile.isEncrypted || feedback != "Download added to transfers") {
                    onFeedback(feedback)
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Decrypt export failed", e)
                onFeedback(e.message ?: "Failed to decrypt file")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    rootNavController: NavController,
    viewModel: HomeViewModel = viewModel(),
    onSearchClick: (() -> Unit)? = null
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val currentAccountId by viewModel.currentAccountId.collectAsStateWithLifecycle()
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hasInitialContent by viewModel.hasInitialContent.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val selectedStorageAccountId by viewModel.selectedStorageAccountId.collectAsStateWithLifecycle()
    val storageSummary by viewModel.storageSummary.collectAsStateWithLifecycle()
    val isStorageRefreshing by viewModel.isStorageRefreshing.collectAsStateWithLifecycle()
    val storageRefreshError by viewModel.storageRefreshError.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val prefsState by UserPreferences.observe(context).collectAsState(initial = UserPreferencesState())
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isManualRefreshing by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderNameInput by remember { mutableStateOf("") }

    val appContainer = remember(context) {
        (context.applicationContext as? CloudyteApplication)?.container
    }
    val networkStatus by remember(appContainer) {
        appContainer?.networkMonitor?.status ?: flowOf(NetworkStatus.VALIDATED_UNMETERED)
    }.collectAsStateWithLifecycle(
        initialValue = appContainer?.networkMonitor?.current() ?: NetworkStatus.VALIDATED_UNMETERED
    )
    val foregroundEpoch by remember(appContainer) {
        appContainer?.foregroundEpoch ?: flowOf(0L)
    }.collectAsStateWithLifecycle(initialValue = 0L)

    var previouslyOnline by remember { mutableStateOf(networkStatus.isOnline()) }
    var showBackOnlineBadge by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading) {
        if (!isLoading) isManualRefreshing = false
    }

    LaunchedEffect(Unit) {
        viewModel.loadData(forceStorageRefresh = false)
    }

    LaunchedEffect(networkStatus) {
        val onlineNow = networkStatus.isOnline()
        if (!previouslyOnline && onlineNow) {
            showBackOnlineBadge = true
            viewModel.refreshIfNeeded(isOnline = true, wasOffline = true)
            delay(2_200L)
            showBackOnlineBadge = false
        } else if (!onlineNow) {
            showBackOnlineBadge = false
        }
        previouslyOnline = onlineNow
    }

    LaunchedEffect(foregroundEpoch) {
        if (foregroundEpoch > 0L) {
            viewModel.refreshIfNeeded(isOnline = networkStatus.isOnline(), wasOffline = false)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val accId = currentAccountId ?: accounts.firstOrNull()?.effectiveId
            if (!accId.isNullOrEmpty()) {
                coroutineScope.launch {
                    try {
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                it,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                        var name = "upload_${System.currentTimeMillis()}"
                        var size = 0L
                        context.contentResolver.query(
                            it,
                            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                            }
                        }
                        val app = context.applicationContext as com.cloudyte.app.CloudyteApplication
                        val encryptUploads = prefsState.isUploadEncryptionEnabled
                        val mimeType = runCatching { context.contentResolver.getType(it) }.getOrNull()
                        val transferId = app.container.transferRepository.enqueueTransfer(
                            accountId = accId,
                            fileName = name,
                            localPath = it.toString(),
                            direction = com.cloudyte.app.db.TransferDirection.UPLOAD,
                            totalBytes = size,
                            isEncrypted = encryptUploads,
                            originalName = if (encryptUploads) name else null,
                            originalMimeType = if (encryptUploads) mimeType else null,
                            originalSize = if (encryptUploads) size else null
                        )
                        app.container.transferCoordinator.dispatch(
                            com.cloudyte.app.core.transfer.TransferCommand.Start(transferId)
                        )
                        snackbarHostState.showSnackbar("$name added to transfers")
                    } catch (error: Exception) {
                        snackbarHostState.showSnackbar(error.message ?: "Could not queue upload")
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    text = stringResource(R.string.home_create_folder_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    label = { Text(stringResource(R.string.home_folder_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderNameInput.isNotBlank()) {
                            viewModel.createFolder(folderNameInput.trim()) {
                                showCreateFolderDialog = false
                                folderNameInput = ""
                            }
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.action_create), color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    val effectiveSummary: StorageSummary? = remember(
        storageSummary,
        accounts,
        selectedStorageAccountId,
        recentFiles
    ) {
        val base = storageSummary ?: if (accounts.isNotEmpty()) {
            deriveSummaryFromAccounts(accounts, selectedStorageAccountId, recentFiles)
        } else {
            null
        }
        if (base != null && base.categories == null) {
            base.copy(categories = deriveCategoriesFromFiles(recentFiles, base.usedBytes))
        } else {
            base
        }
    }

    val ext = MaterialTheme.cloudyteColors

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = LocalBottomBarInset.current)
            )
        }
    ) { innerPadding ->
        if (!hasInitialContent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
                    .testTag("home_initial_loading"),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(92.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else {
        PullToRefreshBox(
            isRefreshing = isManualRefreshing && isLoading,
            onRefresh = {
                isManualRefreshing = true
                viewModel.loadData(forceStorageRefresh = true)
            },
            indicator = {},
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(
                visible = isManualRefreshing && isLoading,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = LocalBottomBarInset.current + 36.dp
                )
            ) {
                // Header: Welcome back + Username on left, 56dp Settings squircle on right
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.home_welcome_lower),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                HomeConnectionStatusPill(
                                    networkStatus = networkStatus,
                                    showRecovered = showBackOnlineBadge
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = profile?.displayUsername ?: stringResource(R.string.home_default_user),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            onClick = { navController.navigateSingleTopRestore("accounts") },
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("home_drives_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = stringResource(R.string.action_drives),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Expressive Hero Storage Card
                item {
                    StorageOverviewCard(
                        summary = effectiveSummary,
                        isLoading = effectiveSummary == null && (isStorageRefreshing || isLoading),
                        refreshError = storageRefreshError,
                        accounts = accounts,
                        selectedAccountId = selectedStorageAccountId,
                        onSelectAccount = { viewModel.selectStorageAccount(it) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Asymmetric Stat Tiles (Total files & Linked drives) + Category Bento Grid
                item {
                    val active = effectiveSummary
                    val selectedAccForStats = accounts.firstOrNull { it.effectiveId == selectedStorageAccountId }
                    val totalFilesCount = active?.fileCount ?: if (selectedStorageAccountId == ALL_ACCOUNTS_STORAGE_ID) {
                        accounts.sumOf { it.fileCount ?: 0 }
                    } else {
                        selectedAccForStats?.fileCount ?: 0
                    }
                    val categories = active?.categories ?: deriveCategoriesFromFiles(recentFiles, active?.usedBytes ?: 0L)
                    val segments = remember(categories, active?.usedBytes) {
                        calculateSegmentProportions(categories, active?.usedBytes ?: 0L)
                    }
                    val byCategory = remember(segments) { segments.associateBy { it.category } }

                    // Asymmetric Stat Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 28.dp,
                                topEnd = 28.dp,
                                bottomStart = 28.dp,
                                bottomEnd = 10.dp
                            ),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                                Text(
                                    text = stringResource(R.string.home_total_files_label),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "$totalFilesCount",
                                    fontFamily = RobotoFlexFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 36.sp,
                                    lineHeight = 40.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 28.dp,
                                topEnd = 28.dp,
                                bottomStart = 10.dp,
                                bottomEnd = 28.dp
                            ),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            onClick = { navController.navigateSingleTopRestore("accounts") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_linked_drives_tile")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                                Text(
                                    text = stringResource(R.string.home_linked_drives_label),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${accounts.size}",
                                    fontFamily = RobotoFlexFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 36.sp,
                                    lineHeight = 40.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category Bento Grid
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.testTag("storage_category_legend")
                    ) {
                        val otherBytes = byCategory[StorageCategoryType.OTHER]?.bytes ?: categories.other
                        val imageBytes = byCategory[StorageCategoryType.IMAGES]?.bytes ?: categories.images
                        val docsBytes = byCategory[StorageCategoryType.DOCUMENTS]?.bytes ?: categories.documents
                        val mediaBytes = (byCategory[StorageCategoryType.VIDEOS]?.bytes ?: categories.videos) +
                            (byCategory[StorageCategoryType.AUDIO]?.bytes ?: categories.audio)
                        val zippedBytes = byCategory[StorageCategoryType.ARCHIVES]?.bytes ?: categories.archives
                        val categorizedSum = otherBytes + imageBytes + docsBytes + mediaBytes + zippedBytes
                        val unknownBytes = ((active?.usedBytes ?: 0L) - categorizedSum).coerceAtLeast(0L)

                        // Top 2 Hero Category Cards: Other & Images
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HeroCategoryCard(
                                label = stringResource(R.string.category_other),
                                bytesText = formatStorageSize(otherBytes),
                                icon = Icons.Default.InsertDriveFile,
                                containerColor = ext.mintSuccess,
                                contentColor = ext.onMintSuccess,
                                iconBadgeColor = ext.onMintSuccess.copy(alpha = 0.14f),
                                badgeShape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            HeroCategoryCard(
                                label = stringResource(R.string.category_images),
                                bytesText = formatStorageSize(imageBytes),
                                icon = Icons.Default.Image,
                                containerColor = ext.categoryImages,
                                contentColor = ext.onCategoryImages,
                                iconBadgeColor = ext.onCategoryImages.copy(alpha = 0.14f),
                                badgeShape = CircleShape,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Bottom 4 Compact Category Tiles: Docs, Media, Zipped, Unknown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactCategoryTile(
                                label = stringResource(R.string.category_docs),
                                bytesText = formatStorageSize(docsBytes),
                                icon = Icons.Default.Description,
                                badgeColor = ext.categoryDocs,
                                iconTint = ext.onCategoryDocs,
                                modifier = Modifier.weight(1f)
                            )
                            CompactCategoryTile(
                                label = stringResource(R.string.category_media),
                                bytesText = formatStorageSize(mediaBytes),
                                icon = Icons.Default.PlayArrow,
                                badgeColor = ext.categoryMedia,
                                iconTint = ext.onCategoryMedia,
                                modifier = Modifier.weight(1f)
                            )
                            CompactCategoryTile(
                                label = stringResource(R.string.category_zipped),
                                bytesText = formatStorageSize(zippedBytes),
                                icon = Icons.Default.FolderZip,
                                badgeColor = ext.categoryZipped,
                                iconTint = ext.onCategoryZipped,
                                modifier = Modifier.weight(1f)
                            )
                            CompactCategoryTile(
                                label = stringResource(R.string.category_unknown),
                                bytesText = formatStorageSize(unknownBytes),
                                icon = Icons.Default.HelpOutline,
                                badgeColor = ext.categoryUnknown,
                                iconTint = ext.onCategoryUnknown,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))
                }

                if (errorMessage != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = errorMessage ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Button(
                                    onClick = { viewModel.loadData(forceStorageRefresh = true) },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(stringResource(R.string.action_retry), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                // Quick Actions Section
                item {
                    Text(
                        text = stringResource(R.string.home_quick_actions_label),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    QuickActionsRow(
                        onUploadClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        onNewFolderClick = { showCreateFolderDialog = true },
                        onSearchClick = {
                            onSearchClick?.invoke() ?: navController.navigateSingleTopRestore("files")
                        },
                        onDrivesClick = { navController.navigateSingleTopRestore("accounts") }
                    )
                }

                if (prefsState.showRecentFiles && recentFiles.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.home_recent_files_label),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    items(recentFiles, key = { it.effectiveId.ifEmpty { it.effectiveName } }) { file ->
                        val resolvedAccountId = file.accountId?.takeIf { it.isNotBlank() && it != "all" && it != "root" }
                            ?: currentAccountId?.takeIf { it.isNotBlank() && it != "all" && it != "root" }
                            ?: accounts.firstOrNull()?.effectiveId.orEmpty()
                        Box(modifier = Modifier.padding(vertical = 4.dp)) {
                            FileListItem(
                                file = file,
                                navController = rootNavController,
                                accountId = resolvedAccountId,
                                files = recentFiles,
                                onEncryptClick = {
                                    viewModel.createEncryptedCopy(resolvedAccountId, file) { msg ->
                                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                },
                                onDecryptExportClick = {
                                    viewModel.decryptAndExportFile(resolvedAccountId, file) { msg ->
                                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun HeroCategoryCard(
    label: String,
    bytesText: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    iconBadgeColor: Color,
    badgeShape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(badgeShape)
                        .background(iconBadgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = bytesText,
                fontFamily = RobotoFlexFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactCategoryTile(
    label: String,
    bytesText: String,
    icon: ImageVector,
    badgeColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = bytesText,
                fontFamily = RobotoFlexFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = if (bytesText.length >= 8) 11.sp else 12.sp,
                lineHeight = 15.sp,
                letterSpacing = (-0.3).sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun deriveCategoriesFromFiles(
    files: List<CloudFile>,
    totalUsedBytes: Long
): StorageCategories {
    var images = 0L
    var documents = 0L
    var videos = 0L
    var archives = 0L
    var audio = 0L
    var other = 0L

    files.filter { !it.isFolderItem }.forEach { file ->
        val bytes = file.sizeBytes.coerceAtLeast(0L)
        val ext = file.effectiveType.lowercase()
        when {
            file.isImage -> images += bytes
            file.isVideo -> videos += bytes
            file.isAudio -> audio += bytes
            file.isPdf || file.isTextOrCode || ext in listOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "txt", "rtf", "odt") ->
                documents += bytes
            ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "iso") ->
                archives += bytes
            else -> other += bytes
        }
    }

    return StorageCategories(
        images = images,
        documents = documents,
        videos = videos,
        archives = archives,
        audio = audio,
        other = other
    )
}

private fun deriveSummaryFromAccounts(
    accounts: List<Account>,
    selectedAccountId: String,
    recentFiles: List<CloudFile> = emptyList()
): StorageSummary {
    val perAccountSummaries = accounts.map { acc ->
        val isTg = acc.effectiveType.contains("tg", ignoreCase = true) ||
            acc.effectiveType.contains("tele", ignoreCase = true)
        StorageSummary(
            accountId = acc.effectiveId,
            accountType = acc.effectiveType,
            usedBytes = acc.usageBytes,
            totalBytes = if (isTg || acc.totalBytes <= 0L) null else acc.totalBytes,
            fileCount = acc.fileCount,
            categories = null
        )
    }
    val aggregated = if (selectedAccountId == ALL_ACCOUNTS_STORAGE_ID) {
        aggregateStorageSummaries(perAccountSummaries)
    } else {
        perAccountSummaries.firstOrNull { it.accountId == selectedAccountId }
            ?: aggregateStorageSummaries(perAccountSummaries)
    }
    return aggregated.copy(
        categories = aggregated.categories ?: deriveCategoriesFromFiles(recentFiles, aggregated.usedBytes)
    )
}

@Composable
fun StorageOverviewCard(
    summary: StorageSummary?,
    isLoading: Boolean = false,
    refreshError: String? = null,
    accounts: List<Account> = emptyList(),
    selectedAccountId: String = ALL_ACCOUNTS_STORAGE_ID,
    onSelectAccount: (String) -> Unit = {}
) {
    val ext = MaterialTheme.cloudyteColors
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_storage_card")
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (isLoading && summary == null) {
                StorageCardSkeleton(hasMultipleAccounts = accounts.size > 1)
            } else {
                val activeSummary = summary ?: StorageSummary(
                    accountId = selectedAccountId,
                    accountType = "unknown",
                    usedBytes = 0L,
                    totalBytes = null,
                    categories = StorageCategories()
                )
                val selectedAcc = accounts.firstOrNull { it.effectiveId == selectedAccountId }
                    ?: accounts.firstOrNull { it.isDefault == true }
                    ?: accounts.firstOrNull()

                var accountsExpanded by remember { mutableStateOf(false) }

                // Top Row: Avatar + Active Account Name + Expandable accounts pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        val displayName = when {
                            selectedAccountId == ALL_ACCOUNTS_STORAGE_ID && accounts.size > 1 -> "All drives"
                            else -> selectedAcc?.effectiveName ?: "Cloudyte"
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ext.avatarBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = ext.lightAccent
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (accounts.isNotEmpty()) {
                        val pillLabel = if (accounts.size == 1) {
                            stringResource(R.string.home_account_pill_single)
                        } else {
                            stringResource(R.string.home_accounts_pill_format, accounts.size)
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            onClick = { if (accounts.size > 1) accountsExpanded = !accountsExpanded },
                            modifier = Modifier.heightIn(min = 36.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = pillLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                if (accounts.size > 1) {
                                    Icon(
                                        imageVector = if (accountsExpanded) {
                                            Icons.Default.KeyboardArrowUp
                                        } else {
                                            Icons.Default.KeyboardArrowDown
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (accountsExpanded && accounts.size > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    StorageAccountChipRow(
                        accounts = accounts,
                        selectedAccountId = selectedAccountId,
                        onSelectAccount = onSelectAccount
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Middle Row: STORAGE USED + huge metric figure on left; 84dp Starburst Cloud badge on right
                val formattedUsed = formatStorageSize(activeSummary.usedBytes)
                val usedParts = formattedUsed.trim().split(" ")
                val usedNumber = usedParts.firstOrNull() ?: "0"
                val usedUnit = usedParts.drop(1).joinToString(" ")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.home_storage_used_caps),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        fontFamily = RobotoFlexFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 48.sp,
                                        letterSpacing = (-1.5).sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    append(usedNumber)
                                }
                                if (usedUnit.isNotEmpty()) {
                                    withStyle(
                                        SpanStyle(
                                            fontFamily = RobotoFlexFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 22.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        append(" $usedUnit")
                                    }
                                }
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("storage_usage_text")
                        )
                        if (refreshError != null && summary == null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Couldn't refresh",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("storage_refresh_error")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    ExpressiveStarburstBadge(
                        size = 84.dp,
                        containerColor = ext.brandStarburst
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = ext.onBrandStarburst,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom: Expressive Wavy Progress Bar + Availability Footer
                val totalCap = activeSummary.totalBytes ?: 0L
                val isFiniteQuota = totalCap > 0L
                val rawRatio = if (isFiniteQuota) {
                    (activeSummary.usedBytes.toFloat() / totalCap.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val targetProgress = if (isFiniteQuota) {
                    if (activeSummary.usedBytes > 0L) rawRatio.coerceIn(0.12f, 1f) else 0f
                } else {
                    if (activeSummary.usedBytes > 0L) 0.35f else 0.08f
                }

                var appeared by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { appeared = true }
                val animatedProgress by animateFloatAsState(
                    targetValue = if (appeared) targetProgress else 0f,
                    animationSpec = tween(durationMillis = 650, easing = EmphasizedDecelerate),
                    label = "wavy_storage_progress"
                )

                WavyProgressBar(
                    progress = animatedProgress,
                    indicatorColor = ext.mintSuccess,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    modifier = Modifier.testTag("storage_segmented_bar")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isFiniteQuota) {
                            stringResource(R.string.home_of_available_format, formatStorageSize(totalCap))
                        } else {
                            stringResource(R.string.home_unlimited_storage)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val percentageLabel = when {
                        !isFiniteQuota -> stringResource(R.string.home_unlimited_symbol)
                        activeSummary.usedBytes <= 0L -> "0%"
                        rawRatio * 100f < 1f -> "< 1%"
                        else -> "${(rawRatio * 100f).roundToInt()}%"
                    }
                    Text(
                        text = percentageLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = ext.mintSuccess
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageAccountChipRow(
    accounts: List<Account>,
    selectedAccountId: String,
    onSelectAccount: (String) -> Unit
) {
    val ext = MaterialTheme.cloudyteColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("storage_account_chips"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StorageAccountChip(
            label = "All accounts",
            avatarLetter = "A",
            avatarColor = MaterialTheme.colorScheme.primary,
            avatarContentColor = MaterialTheme.colorScheme.onPrimary,
            selected = selectedAccountId == ALL_ACCOUNTS_STORAGE_ID,
            onClick = { onSelectAccount(ALL_ACCOUNTS_STORAGE_ID) },
            testTag = "storage_chip_all"
        )
        accounts.forEach { account ->
            val accId = account.effectiveId
            val pColor = when {
                account.effectiveType.contains("google", ignoreCase = true) ||
                    account.effectiveType.contains("gdrive", ignoreCase = true) -> ext.providerGoogle
                account.effectiveType.contains("onedrive", ignoreCase = true) -> ext.providerOneDrive
                else -> ext.providerTelegram
            }
            val pTextColor = if (pColor.luminance() > 0.5f) {
                MaterialTheme.colorScheme.surface
            } else {
                Color.White
            }
            StorageAccountChip(
                label = account.effectiveName,
                avatarLetter = account.effectiveName.take(1).uppercase(),
                avatarColor = pColor,
                avatarContentColor = pTextColor,
                selected = selectedAccountId == accId,
                onClick = { onSelectAccount(accId) },
                testTag = "storage_chip_$accId"
            )
        }
    }
}

@Composable
private fun StorageAccountChip(
    label: String,
    avatarLetter: String,
    avatarColor: Color,
    avatarContentColor: Color = Color.White,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = CircleShape,
        color = bgColor,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(avatarColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = avatarLetter,
                    color = avatarContentColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StorageCardSkeleton(hasMultipleAccounts: Boolean) {
    val transition = rememberInfiniteTransition(label = "storage_skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)

    Column(modifier = Modifier.testTag("storage_card_skeleton")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(placeholderColor)
            )
            if (hasMultipleAccounts) {
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(28.dp)
                        .clip(CircleShape)
                        .background(placeholderColor)
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(placeholderColor)
        )
        Spacer(modifier = Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(placeholderColor)
        )
    }
}

@Composable
fun QuickActionsRow(
    onUploadClick: () -> Unit,
    onNewFolderClick: () -> Unit,
    onSearchClick: () -> Unit,
    onDrivesClick: () -> Unit
) {
    val ext = MaterialTheme.cloudyteColors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickActionButton(
            icon = Icons.Default.Upload,
            label = stringResource(R.string.action_upload),
            onClick = onUploadClick,
            isPrimary = true,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Default.CreateNewFolder,
            label = stringResource(R.string.home_new_folder_short),
            onClick = onNewFolderClick,
            accentTint = ext.accentBlue,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Default.Search,
            label = stringResource(R.string.action_search),
            onClick = onSearchClick,
            accentTint = ext.accentBlue,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Default.Cloud,
            label = stringResource(R.string.action_drives),
            onClick = onDrivesClick,
            accentTint = ext.accentBlue,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    accentTint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isPrimary) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val iconColor = if (isPrimary) MaterialTheme.colorScheme.onPrimary else accentTint
    val textColor = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        onClick = onClick,
        modifier = modifier.height(84.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                letterSpacing = (-0.2).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FileListItem(
    file: CloudFile,
    navController: NavController,
    accountId: String,
    files: List<CloudFile>? = null,
    onEncryptClick: (() -> Unit)? = null,
    onDecryptExportClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val downloadedIds by com.cloudyte.app.api.OfflineFileManager.downloadedFiles.collectAsStateWithLifecycle()
    val activeOfflineDownloads by com.cloudyte.app.api.OfflineFileManager.activeDownloads.collectAsStateWithLifecycle()
    val isOfflinePinned = !file.isFolderItem && file.effectiveId in downloadedIds
    val offlineProgress = activeOfflineDownloads[file.effectiveId]

    var showActionSheet by remember { mutableStateOf(false) }
    var showQuickInfo by remember { mutableStateOf(false) }
    var showMoveCopy by remember { mutableStateOf<Boolean?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val resolvedAcc = file.effectiveAccountId.ifBlank { accountId }
    val app = context.applicationContext as? com.cloudyte.app.CloudyteApplication
    val ext = MaterialTheme.cloudyteColors

    if (showQuickInfo) {
        com.cloudyte.app.features.player.GDriveInfoBottomSheet(
            file = file,
            accountId = resolvedAcc,
            isDownloadedOffline = isOfflinePinned,
            context = context,
            onDismiss = { showQuickInfo = false }
        )
    }

    if (showMoveCopy != null) {
        MoveCopyDialog(
            file = file,
            sourceAccountId = resolvedAcc,
            isMove = showMoveCopy!!,
            onDismiss = { showMoveCopy = null },
            onSuccess = { showMoveCopy = null }
        )
    }

    if (showRenameDialog && app != null) {
        com.cloudyte.app.features.player.GDriveRenameDialog(
            initialName = file.effectiveName,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                coroutineScope.launch {
                    app.container.cloudFileRepository.rename(file.effectiveId, resolvedAcc.ifBlank { null }, newName)
                }
            }
        )
    }

    if (showDeleteDialog && app != null) {
        com.cloudyte.app.features.player.GDriveDeleteConfirmDialog(
            fileName = file.effectiveName,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                coroutineScope.launch {
                    app.container.cloudFileRepository.delete(file.effectiveId, resolvedAcc.ifBlank { null })
                }
            }
        )
    }

    if (showActionSheet) {
        com.cloudyte.app.ui.components.DriveItemActionSheet(
            file = file,
            accountId = resolvedAcc,
            isOfflinePinned = isOfflinePinned,
            offlineDownloadProgress = offlineProgress,
            onDismiss = { showActionSheet = false },
            onOpen = { openFileInViewer(file, resolvedAcc, navController, files) },
            onToggleOffline = {
                coroutineScope.launch {
                    if (isOfflinePinned) {
                        com.cloudyte.app.api.OfflineFileManager.deleteOfflineFile(context, resolvedAcc, file.effectiveId)
                    } else {
                        com.cloudyte.app.api.OfflineFileManager.downloadFile(context, resolvedAcc, file)
                    }
                }
            },
            onShare = {
                coroutineScope.launch {
                    com.cloudyte.app.features.player.performShareFileLink(context, resolvedAcc, file)
                }
            },
            onDownload = {
                onDecryptExportClick?.invoke() ?: coroutineScope.launch {
                    com.cloudyte.app.features.player.performInAppDownload(context, resolvedAcc, file)
                }
            },
            onMove = { showMoveCopy = true },
            onCopy = { showMoveCopy = false },
            onRename = { showRenameDialog = true },
            onEncryptOrDecrypt = {
                if (file.isEncrypted) {
                    onDecryptExportClick?.invoke()
                } else {
                    onEncryptClick?.invoke()
                }
            },
            onQuickInfo = { showQuickInfo = true },
            onOpenFullDetails = {
                navController.navigate("file_detail/$resolvedAcc/${file.effectiveId}")
            },
            onDelete = { showDeleteDialog = true }
        )
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { openFileInViewer(file, accountId, navController, files) }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (file.isFolderItem) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else {
                FileThumb(
                    file = file,
                    accountId = accountId,
                    sizeDp = 48.dp,
                    cornerRadius = 16.dp
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.effectiveName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (offlineProgress != null) {
                        androidx.compose.material3.CircularProgressIndicator(
                            progress = { offlineProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.6.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (isOfflinePinned) {
                        Icon(
                            imageVector = Icons.Default.DownloadDone,
                            contentDescription = "Available offline",
                            tint = ext.mintSuccess,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = if (file.isFolderItem) stringResource(R.string.files_type_folder) else file.formattedSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!file.isFolderItem) {
                        Text(
                            text = "• ${file.effectiveType.uppercase()}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (file.isEncrypted) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
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
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            IconButton(onClick = { showActionSheet = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.action_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HomeConnectionStatusPill(
    networkStatus: NetworkStatus,
    showRecovered: Boolean
) {
    val isOffline = networkStatus == NetworkStatus.UNAVAILABLE
    val isCaptive = networkStatus == NetworkStatus.CAPTIVE_PORTAL
    val visible = isOffline || isCaptive || showRecovered

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(300))
    ) {
        val ext = MaterialTheme.cloudyteColors
        val containerColor = when {
            isOffline -> MaterialTheme.colorScheme.errorContainer
            isCaptive -> MaterialTheme.colorScheme.tertiaryContainer
            else -> ext.mintContainer
        }
        val contentColor = when {
            isOffline -> MaterialTheme.colorScheme.onErrorContainer
            isCaptive -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> ext.onMintSuccess
        }
        val dotColor = when {
            isOffline -> MaterialTheme.colorScheme.error
            isCaptive -> MaterialTheme.colorScheme.tertiary
            else -> ext.mintSuccess
        }
        val label = when {
            isOffline -> stringResource(R.string.status_offline)
            isCaptive -> stringResource(R.string.status_captive_portal)
            else -> stringResource(R.string.status_back_online)
        }
        val pulseAlpha = if (isOffline || isCaptive) {
            val transition = rememberInfiniteTransition(label = "net_dot_pulse")
            transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "net_dot_alpha"
            ).value
        } else {
            1f
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = containerColor,
            modifier = Modifier.testTag("home_connection_status_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = pulseAlpha))
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


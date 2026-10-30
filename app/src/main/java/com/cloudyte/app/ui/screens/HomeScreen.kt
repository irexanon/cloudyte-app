package com.cloudyte.app.ui.screens

import android.app.Application
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cloudyte.app.R
import com.cloudyte.app.api.Account
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.api.FileRepository
import com.cloudyte.app.api.FolderRequest
import com.cloudyte.app.api.StorageCategories
import com.cloudyte.app.api.StorageSummary
import com.cloudyte.app.api.UserProfile
import com.cloudyte.app.data.ALL_ACCOUNTS_STORAGE_ID
import com.cloudyte.app.data.CloudFileRepository
import com.cloudyte.app.data.StorageCategoryType
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.aggregateStorageSummaries
import com.cloudyte.app.data.calculateSegmentProportions
import com.cloudyte.app.data.formatStorageSize
import com.cloudyte.app.data.formatStorageUsageText
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.LocalAccount
import com.cloudyte.app.db.LocalCloudFile
import com.cloudyte.app.ui.components.AppLoadingIndicator
import com.cloudyte.app.ui.components.FileThumb
import com.cloudyte.app.ui.components.LocalBottomBarInset
import com.cloudyte.app.ui.openFileInViewer
import com.cloudyte.app.ui.theme.BrandPrimary
import com.cloudyte.app.ui.theme.ProviderGoogle
import com.cloudyte.app.ui.theme.ProviderOneDrive
import com.cloudyte.app.ui.theme.ProviderTelegram
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

// Category palette matching the Storage card reference design
private val CardImagesBlue = Color(0xFF3B66F5)
private val CardDocsAmber = Color(0xFFF6B93B)
private val CardMediaCoral = Color(0xFFEB5757)
private val CardOtherGreen = Color(0xFF56C596)
private val CardZippedPurple = Color(0xFF9B51E0)
private val CardUnknownSlate = Color(0xFFB0B7D1)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = CloudFileRepository(db, ApiClient.apiService)

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _currentAccountId = MutableStateFlow<String?>(null)
    val currentAccountId: StateFlow<String?> = _currentAccountId.asStateFlow()

    private val _recentFiles = MutableStateFlow<List<CloudFile>>(emptyList())
    val recentFiles: StateFlow<List<CloudFile>> = _recentFiles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val preferredStorageAccountFlow = UserPreferences.observe(application)
        .map { it.homeStorageAccountId }
        .distinctUntilChanged()

    val selectedStorageAccountId: StateFlow<String> = combine(
        preferredStorageAccountFlow,
        _accounts
    ) { preferredId, accs ->
        when {
            !preferredId.isNullOrBlank() && (preferredId == ALL_ACCOUNTS_STORAGE_ID || accs.isEmpty() || accs.any { it.effectiveId == preferredId }) ->
                preferredId
            accs.size > 1 -> ALL_ACCOUNTS_STORAGE_ID
            accs.size == 1 -> accs.first().effectiveId.ifBlank { ALL_ACCOUNTS_STORAGE_ID }
            else -> ALL_ACCOUNTS_STORAGE_ID
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ALL_ACCOUNTS_STORAGE_ID)

    val storageSummary: StateFlow<StorageSummary?> = selectedStorageAccountId
        .flatMapLatest { accountId ->
            repository.observeStorage(accountId)
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

    fun selectStorageAccount(accountId: String) {
        viewModelScope.launch {
            UserPreferences.setHomeStorageAccountId(getApplication(), accountId)
        }
    }

    fun loadData(forceStorageRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val cachedAccounts = db.accountDao().getAllAccounts()
                if (cachedAccounts.isNotEmpty()) {
                    _accounts.value = cachedAccounts.map { it.toAccount() }
                }

                val cachedDefault = _accounts.value.firstOrNull { it.isDefault == true } ?: _accounts.value.firstOrNull()
                val cachedAccountId = cachedDefault?.effectiveId?.takeIf { it.isNotBlank() && it != "root" }
                _currentAccountId.value = cachedAccountId
                val cachedFiles = db.cloudFileDao().getRecentFiles(8)
                if (cachedFiles.isNotEmpty()) {
                    _recentFiles.value = cachedFiles.map { it.toCloudFile() }.take(8)
                    FileRepository.cacheFiles(_recentFiles.value)
                }

                try {
                    val p = ApiClient.apiService.getProfile()
                    _profile.value = p
                } catch (e: Exception) {
                    Log.w("HomeViewModel", "Profile call failed: ${e.message}")
                }

                val accs = try {
                    ApiClient.apiService.getAccounts()
                } catch (e: Exception) {
                    Log.w("HomeViewModel", "Accounts call failed: ${e.message}")
                    emptyList()
                }
                if (accs.isNotEmpty()) {
                    _accounts.value = accs
                    db.accountDao().insertAccounts(accs.map { LocalAccount.fromAccount(it) })
                }

                val defaultAcc = _accounts.value.firstOrNull { it.isDefault == true } ?: _accounts.value.firstOrNull()
                val accId = defaultAcc?.effectiveId?.takeIf { it.isNotBlank() && it != "root" }
                _currentAccountId.value = accId

                if (forceStorageRefresh) {
                    repository.refreshStorage(selectedStorageAccountId.value, force = true)
                }

                repository.refresh(accId, "root", force = forceStorageRefresh)
                val updatedFiles = db.cloudFileDao().getRecentFiles(8).map { it.toCloudFile() }
                if (updatedFiles.isNotEmpty()) {
                    _recentFiles.value = updatedFiles.take(8)
                    FileRepository.cacheFiles(updatedFiles)
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "General data loading error", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to load cloud data"
            } finally {
                _isLoading.value = false
            }
        }
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
}

@Composable
fun HomeScreen(
    navController: NavController,
    rootNavController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val currentAccountId by viewModel.currentAccountId.collectAsStateWithLifecycle()
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val selectedStorageAccountId by viewModel.selectedStorageAccountId.collectAsStateWithLifecycle()
    val storageSummary by viewModel.storageSummary.collectAsStateWithLifecycle()
    val isStorageRefreshing by viewModel.isStorageRefreshing.collectAsStateWithLifecycle()
    val storageRefreshError by viewModel.storageRefreshError.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderNameInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadData(forceStorageRefresh = false)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val accId = currentAccountId ?: accounts.firstOrNull()?.effectiveId
            if (!accId.isNullOrEmpty()) {
                isUploading = true
                coroutineScope.launch {
                    val uploadResult = UploadHelper.uploadFile(context, accId, null, it)
                    isUploading = false
                    if (uploadResult.isSuccess) {
                        viewModel.loadData(forceStorageRefresh = true)
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                Text(
                    text = stringResource(R.string.home_create_folder_title),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                OutlinedTextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    label = {
                        Text(
                            text = stringResource(R.string.home_folder_name_label),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = stringResource(R.string.action_create),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    val effectiveSummary: StorageSummary? = remember(storageSummary, accounts, selectedStorageAccountId, isStorageRefreshing, recentFiles) {
        val base = storageSummary ?: if (!isStorageRefreshing && accounts.isNotEmpty()) {
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (isUploading) {
                FloatingActionButton(
                    onClick = { },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(bottom = LocalBottomBarInset.current)
                        .size(56.dp)
                ) {
                    AppLoadingIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        size = 24.dp
                    )
                }
            } else {
                FloatingActionButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(bottom = LocalBottomBarInset.current)
                        .size(56.dp)
                        .testTag("home_upload_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_upload_file),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = LocalBottomBarInset.current + 88.dp
            )
        ) {
            // Original Dashboard Header: Welcome Back + Username + Multi-cloud drive manager + Refresh button
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Welcome Back 👋",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = profile?.displayUsername ?: "User",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Multi-cloud drive manager",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.loadData(forceStorageRefresh = true) }
                            .testTag("home_refresh_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Storage Card (Exact segmented bar + 2x3 category breakdown from reference card)
            item {
                StorageOverviewCard(
                    summary = effectiveSummary,
                    isLoading = effectiveSummary == null && (isStorageRefreshing || isLoading),
                    refreshError = storageRefreshError,
                    accounts = accounts,
                    selectedAccountId = selectedStorageAccountId,
                    onSelectAccount = { viewModel.selectStorageAccount(it) }
                )
                Spacer(modifier = Modifier.height(28.dp))
            }

            if (errorMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp
                            )
                            Button(
                                onClick = { viewModel.loadData(forceStorageRefresh = true) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.action_retry),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Quick Actions Section
            item {
                Text(
                    text = stringResource(R.string.home_quick_actions),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(16.dp))
                QuickActionsRow(
                    onUploadClick = { filePickerLauncher.launch("*/*") },
                    onNewFolderClick = { showCreateFolderDialog = true },
                    onSearchClick = { navController.navigateSingleTopRestore("files") },
                    onDrivesClick = { navController.navigateSingleTopRestore("settings") }
                )
            }

            if (recentFiles.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = "Recent Files",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                items(recentFiles, key = { it.effectiveId.ifEmpty { it.effectiveName } }) { file ->
                    Box(modifier = Modifier.padding(vertical = 6.dp)) {
                        FileListItem(
                            file = file,
                            navController = rootNavController,
                            accountId = currentAccountId ?: accounts.firstOrNull()?.effectiveId.orEmpty(),
                            files = recentFiles
                        )
                    }
                }
            }
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

    val sum = images + documents + videos + archives + audio + other
    if (totalUsedBytes > sum) {
        other += (totalUsedBytes - sum)
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
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 6.dp,
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

                // Top Row: "Storage" on left, usage on right
                val defaultAccount = accounts.firstOrNull { it.isDefault == true } ?: accounts.firstOrNull()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Storage",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatStorageUsageText(activeSummary),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("storage_usage_text")
                        )
                        if (refreshError != null && summary == null) {
                            Text(
                                text = "Couldn't refresh",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                modifier = Modifier.testTag("storage_refresh_error")
                            )
                        }
                    }
                }

                if (defaultAccount != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = defaultAccount.effectiveName.take(1).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Default: ${defaultAccount.effectiveName}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${accounts.size} linked",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Account Switcher Chip Row (shown when user has multiple drives)
                if (accounts.size > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    StorageAccountChipRow(
                        accounts = accounts,
                        selectedAccountId = selectedAccountId,
                        onSelectAccount = onSelectAccount
                    )
                }

                val effectiveCategories = activeSummary.categories ?: deriveCategoriesFromFiles(emptyList(), activeSummary.usedBytes)
                val segments = remember(effectiveCategories, activeSummary.usedBytes) {
                    calculateSegmentProportions(effectiveCategories, activeSummary.usedBytes)
                }

                var appeared by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    appeared = true
                }

                val animatedProportions = segments.map { segment ->
                    val anim by animateFloatAsState(
                        targetValue = if (appeared) segment.proportion else 0f,
                        animationSpec = tween(durationMillis = 600, easing = EmphasizedDecelerate),
                        label = "segment_${segment.category.name}"
                    )
                    anim
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Multi-segment horizontal storage bar matching attached reference card
                val trackColor = CardUnknownSlate.copy(alpha = 0.28f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(CircleShape)
                        .background(trackColor)
                        .testTag("storage_segmented_bar")
                ) {
                    var currentX = 0f
                    segments.forEachIndexed { index, segment ->
                        val prop = animatedProportions[index].coerceIn(0f, 1f)
                        val segWidth = (prop * size.width).coerceAtMost(size.width - currentX)
                        if (segWidth > 0f) {
                            drawRect(
                                color = categoryColor(segment.category),
                                topLeft = Offset(currentX, 0f),
                                size = Size(segWidth, size.height)
                            )
                            currentX += segWidth
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 2 rows x 3 columns Category Legend Grid matching attached reference card:
                // Row 1: Images, Documents, Media
                // Row 2: Other, Zipped, Unknown
                val byCategory = remember(segments) { segments.associateBy { it.category } }
                val row1Items = listOf(
                    Triple("Images", CardImagesBlue, byCategory[StorageCategoryType.IMAGES]?.bytes ?: 0L),
                    Triple("Documents", CardDocsAmber, byCategory[StorageCategoryType.DOCUMENTS]?.bytes ?: 0L),
                    Triple("Media", CardMediaCoral, byCategory[StorageCategoryType.VIDEOS]?.bytes ?: 0L)
                )
                val row2Items = listOf(
                    Triple("Other", CardOtherGreen, byCategory[StorageCategoryType.OTHER]?.bytes ?: 0L),
                    Triple("Zipped", CardZippedPurple, byCategory[StorageCategoryType.ARCHIVES]?.bytes ?: 0L),
                    Triple("Unknown", CardUnknownSlate, byCategory[StorageCategoryType.AUDIO]?.bytes ?: 0L)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.testTag("storage_category_legend")
                ) {
                    listOf(row1Items, row2Items).forEach { rowList ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowList.forEach { (label, pillColor, bytes) ->
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(6.dp)
                                            .height(28.dp)
                                            .clip(CircleShape)
                                            .background(pillColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatStorageSize(bytes),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (accounts.isNotEmpty() || (activeSummary.fileCount != null && activeSummary.fileCount > 0)) {
                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total Files",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${activeSummary.fileCount ?: accounts.sumOf { it.fileCount ?: 0 }}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Linked Drives",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${accounts.size}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
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
            avatarColor = BrandPrimary,
            selected = selectedAccountId == ALL_ACCOUNTS_STORAGE_ID,
            onClick = { onSelectAccount(ALL_ACCOUNTS_STORAGE_ID) },
            testTag = "storage_chip_all"
        )
        accounts.forEach { account ->
            val accId = account.effectiveId
            val pColor = when {
                account.effectiveType.contains("google", ignoreCase = true) ||
                    account.effectiveType.contains("gdrive", ignoreCase = true) -> ProviderGoogle
                account.effectiveType.contains("onedrive", ignoreCase = true) -> ProviderOneDrive
                else -> ProviderTelegram
            }
            StorageAccountChip(
                label = account.effectiveName,
                avatarLetter = account.effectiveName.take(1).uppercase(),
                avatarColor = pColor,
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
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = CircleShape,
        color = bgColor,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(avatarColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = avatarLetter,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
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
                    .width(76.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(placeholderColor)
            )
            Box(
                modifier = Modifier
                    .width(136.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(placeholderColor)
            )
        }

        if (hasMultipleAccounts) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(88.dp)
                            .height(28.dp)
                            .clip(CircleShape)
                            .background(placeholderColor)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(placeholderColor)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(28.dp)
                                    .clip(CircleShape)
                                    .background(placeholderColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(48.dp)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                    .background(placeholderColor)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(56.dp)
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(placeholderColor)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun categoryColor(category: StorageCategoryType): Color = when (category) {
    StorageCategoryType.IMAGES -> CardImagesBlue
    StorageCategoryType.DOCUMENTS -> CardDocsAmber
    StorageCategoryType.VIDEOS -> CardMediaCoral
    StorageCategoryType.OTHER -> CardOtherGreen
    StorageCategoryType.ARCHIVES -> CardZippedPurple
    StorageCategoryType.AUDIO -> CardUnknownSlate
}

@Composable
fun QuickActionsRow(
    onUploadClick: () -> Unit,
    onNewFolderClick: () -> Unit,
    onSearchClick: () -> Unit,
    onDrivesClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        QuickActionButton(Icons.Default.Upload, stringResource(R.string.action_upload), onUploadClick)
        QuickActionButton(Icons.Default.CreateNewFolder, stringResource(R.string.action_new_folder), onNewFolderClick)
        QuickActionButton(Icons.Default.Search, stringResource(R.string.action_search), onSearchClick)
        QuickActionButton(Icons.Default.Cloud, stringResource(R.string.action_drives), onDrivesClick)
    }
}

@Composable
fun QuickActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.size(60.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}

@Composable
fun FileListItem(
    file: CloudFile,
    navController: NavController,
    accountId: String,
    files: List<CloudFile>? = null
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                openFileInViewer(file, accountId, navController, files)
            }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (file.isFolderItem) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                FileThumb(
                    file = file,
                    accountId = accountId,
                    sizeDp = 48.dp,
                    cornerRadius = 12.dp
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.effectiveName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (file.isFolderItem) stringResource(R.string.files_type_folder) else file.formattedSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    if (!file.isFolderItem) {
                        Text(
                            text = " • ${file.effectiveType.uppercase()}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
            if (!file.isFolderItem) {
                IconButton(onClick = { navController.navigate("file_detail/${accountId}/${file.effectiveId}") }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.action_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

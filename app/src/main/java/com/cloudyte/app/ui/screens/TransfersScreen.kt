package com.cloudyte.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudyte.app.CloudyteApplication
import com.cloudyte.app.R
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.api.JobStatusResponse
import com.cloudyte.app.core.transfer.TransferCommand
import com.cloudyte.app.data.TransferRepository
import com.cloudyte.app.data.formatStorageSize
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.TransferStatus
import com.cloudyte.app.domain.model.Transfer
import com.cloudyte.app.sync.AutoSyncManager
import com.cloudyte.app.ui.components.EmptyState
import com.cloudyte.app.ui.components.FileThumb
import com.cloudyte.app.ui.components.LocalBottomBarInset
import com.cloudyte.app.ui.theme.cloudyteColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun TransfersScreen(
    onBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = remember { (context.applicationContext as? CloudyteApplication)?.container }
    val db = remember { container?.database ?: AppDatabase.getDatabase(context) }
    val repo = remember { container?.transferRepository ?: TransferRepository(db.transferDao()) }
    val coordinator = remember {
        (context.applicationContext as CloudyteApplication).container.transferCoordinator
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var serverJobs by remember { mutableStateOf<List<JobStatusResponse>>(emptyList()) }
    var accountNamesById by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var serverJobsRefreshNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        runCatching {
            val cached = db.accountDao().getAllAccounts()
            if (cached.isNotEmpty()) {
                accountNamesById = cached.associate { it.id to it.name }
            }
        }
    }

    LaunchedEffect(serverJobsRefreshNonce) {
        while (true) {
            val loaded = runCatching {
                val res = ApiClient.apiService.getJobs()
                if (res.isSuccessful) res.body().orEmpty() else null
            }.getOrNull()
            if (loaded != null) {
                serverJobs = loaded.filter { it.effectiveId.isNotBlank() }
            }
            val hasActiveServerJobs = serverJobs.any { it.isActiveStatus }
            delay(if (hasActiveServerJobs) 3000L else 15000L)
        }
    }

    val allLabel = stringResource(R.string.transfers_tab_all)
    val activeLabel = stringResource(R.string.transfers_tab_active)
    val completedLabel = stringResource(R.string.transfers_tab_completed)
    val failedLabel = stringResource(R.string.transfers_tab_failed)

    val allTransfers by coordinator.observeTransfers().collectAsStateWithLifecycle(initialValue = emptyList())

    val activeTransfers = remember(allTransfers) {
        allTransfers.filter {
            it.status == TransferStatus.PENDING.name ||
                it.status == TransferStatus.RUNNING.name ||
                it.status == TransferStatus.PAUSED.name
        }
    }
    val completedTransfers = remember(allTransfers) {
        allTransfers.filter { it.status == TransferStatus.COMPLETED.name }
    }
    val failedTransfers = remember(allTransfers) {
        allTransfers.filter {
            it.status == TransferStatus.FAILED.name ||
                it.status == TransferStatus.CANCELLED.name
        }
    }

    val filteredServerJobs = remember(serverJobs, selectedTab) {
        when (selectedTab) {
            1 -> serverJobs.filter { it.isActiveStatus }
            2 -> serverJobs.filter { it.isCompletedStatus }
            3 -> serverJobs.filter { it.isFailedOrCancelledStatus }
            else -> serverJobs
        }
    }

    val tabs = listOf(allLabel, activeLabel, completedLabel, failedLabel)

    val filteredTransfers = when (selectedTab) {
        1 -> activeTransfers
        2 -> completedTransfers
        3 -> failedTransfers
        else -> allTransfers
    }

    val pausableActive = remember(activeTransfers) {
        activeTransfers.filter {
            it.status == TransferStatus.RUNNING.name || it.status == TransferStatus.PENDING.name
        }
    }
    val retryableFailed = remember(failedTransfers) {
        failedTransfers.filter { it.status == TransferStatus.FAILED.name }
    }

    val listState = rememberLazyListState()
    val ext = MaterialTheme.cloudyteColors

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Expressive Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (showBackButton) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            onClick = onBack,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.transfers_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        onClick = { serverJobsRefreshNonce++ },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("transfers_refresh_jobs_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (completedTransfers.isNotEmpty() || pausableActive.isNotEmpty() || retryableFailed.isNotEmpty()) {
                        Box {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("transfers_overflow_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.action_options),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                if (pausableActive.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Pause all active (${pausableActive.size})") },
                                        leadingIcon = { Icon(Icons.Default.Pause, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            scope.launch {
                                                pausableActive.forEach { t ->
                                                    coordinator.dispatch(TransferCommand.Pause(t.id))
                                                }
                                            }
                                        }
                                    )
                                }
                                if (retryableFailed.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Retry all failed (${retryableFailed.size})") },
                                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            scope.launch {
                                                retryableFailed.forEach { t ->
                                                    coordinator.dispatch(TransferCommand.Retry(t.id))
                                                }
                                            }
                                        }
                                    )
                                }
                                if (completedTransfers.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.transfers_clear_completed)) },
                                        leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            scope.launch { repo.clearCompleted() }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Pill Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                        onClick = { selectedTab = index },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("transfers_filter_$index")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else ext.lightAccent,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredTransfers.isEmpty() && filteredServerJobs.isEmpty()) {
                val (emptyTitle, emptySubtitle) = when (selectedTab) {
                    1 -> "No active transfers" to "Uploads, downloads, and drive copy/move jobs appear here"
                    2 -> "No completed transfers" to "Finished transfers and server jobs appear here"
                    3 -> "No failed transfers" to "Interrupted or failed transfers can be retried here"
                    else -> stringResource(R.string.transfers_empty) to "Upload, download, or copy/move a drive to track progress"
                }
                EmptyState(
                    icon = Icons.Default.SwapVert,
                    title = emptyTitle,
                    subtitle = emptySubtitle,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = LocalBottomBarInset.current)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 6.dp,
                        bottom = LocalBottomBarInset.current + 36.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filteredServerJobs.isNotEmpty()) {
                        item(key = "server_jobs_header") {
                            Text(
                                text = stringResource(R.string.transfers_section_server_jobs),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 6.dp)
                            )
                        }
                        itemsIndexed(filteredServerJobs, key = { _, job -> "server_job_${job.effectiveId}" }) { index, job ->
                            val topRadius = if (index == 0) 28.dp else 10.dp
                            val bottomRadius = if (index == filteredServerJobs.lastIndex) 28.dp else 10.dp
                            val itemShape = RoundedCornerShape(
                                topStart = topRadius,
                                topEnd = topRadius,
                                bottomStart = bottomRadius,
                                bottomEnd = bottomRadius
                            )
                            ServerJobItemCard(
                                job = job,
                                accountNamesById = accountNamesById,
                                shape = itemShape,
                                onCancel = {
                                    scope.launch {
                                        runCatching { ApiClient.apiService.cancelJob(job.effectiveId) }
                                        serverJobsRefreshNonce++
                                    }
                                },
                                onRetry = {
                                    scope.launch {
                                        runCatching { ApiClient.apiService.retryJob(job.effectiveId) }
                                        serverJobsRefreshNonce++
                                    }
                                }
                            )
                        }
                        if (filteredTransfers.isNotEmpty()) {
                            item(key = "device_queue_header") {
                                Text(
                                    text = stringResource(R.string.transfers_section_device_queue),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 6.dp)
                                )
                            }
                        }
                    }

                    itemsIndexed(filteredTransfers, key = { _, item -> item.id }) { index, transfer ->
                        val topRadius = if (index == 0) 28.dp else 10.dp
                        val bottomRadius = if (index == filteredTransfers.lastIndex) 28.dp else 10.dp
                        val itemShape = RoundedCornerShape(
                            topStart = topRadius,
                            topEnd = topRadius,
                            bottomStart = bottomRadius,
                            bottomEnd = bottomRadius
                        )

                        TransferItemCard(
                            transfer = transfer,
                            shape = itemShape,
                            onPause = { scope.launch { coordinator.dispatch(TransferCommand.Pause(transfer.id)) } },
                            onResume = {
                                scope.launch { coordinator.dispatch(TransferCommand.Resume(transfer.id)) }
                            },
                            onRetry = {
                                scope.launch { coordinator.dispatch(TransferCommand.Retry(transfer.id)) }
                            },
                            onCancel = {
                                scope.launch {
                                    coordinator.dispatch(TransferCommand.Cancel(transfer.id))
                                    if (transfer.direction == "BACKUP") {
                                        androidx.work.WorkManager.getInstance(context)
                                            .cancelUniqueWork(AutoSyncManager.UNIQUE_MANUAL_WORK)
                                        androidx.work.WorkManager.getInstance(context)
                                            .cancelUniqueWork(AutoSyncManager.UNIQUE_IMMEDIATE_WORK)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransferItemCard(
    transfer: Transfer,
    shape: Shape = RoundedCornerShape(24.dp),
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val ext = MaterialTheme.cloudyteColors
    var lastActionAtMs by remember(transfer.id, transfer.status) { mutableStateOf(0L) }
    val triggerOnce: (() -> Unit) -> Unit = { action ->
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastActionAtMs >= 400L) {
            lastActionAtMs = now
            action()
        }
    }
    var previousSample by remember(transfer.id) {
        mutableStateOf(TransferMetricSample(transfer.bytesTransferred, transfer.updatedAt))
    }
    var emaSpeedBps by remember(transfer.id) { mutableDoubleStateOf(0.0) }
    LaunchedEffect(transfer.id, transfer.bytesTransferred, transfer.updatedAt, transfer.status) {
        if (transfer.status != TransferStatus.RUNNING.name) {
            emaSpeedBps = 0.0
            previousSample = TransferMetricSample(transfer.bytesTransferred, transfer.updatedAt)
            return@LaunchedEffect
        }
        val elapsedMs = transfer.updatedAt - previousSample.timestamp
        val deltaBytes = transfer.bytesTransferred - previousSample.bytes
        if (deltaBytes > 0L && elapsedMs >= 150L) {
            val instantBps = (deltaBytes * 1000.0) / elapsedMs.toDouble()
            emaSpeedBps = if (emaSpeedBps <= 0.0) instantBps else (0.35 * instantBps + 0.65 * emaSpeedBps)
            previousSample = TransferMetricSample(transfer.bytesTransferred, transfer.updatedAt)
        } else if (deltaBytes < 0L) {
            emaSpeedBps = 0.0
            previousSample = TransferMetricSample(transfer.bytesTransferred, transfer.updatedAt)
        }
    }
    val speedBytesPerSecond = emaSpeedBps.toLong()

    val directionLabel = when (transfer.direction) {
        "UPLOAD" -> "Upload"
        "BACKUP" -> "Backup"
        else -> "Download"
    }
    val isActiveOrPaused = transfer.status == TransferStatus.RUNNING.name ||
        transfer.status == TransferStatus.PENDING.name ||
        transfer.status == TransferStatus.PAUSED.name

    val progress = if (transfer.totalBytes > 0) {
        (transfer.bytesTransferred.toFloat() / transfer.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val previewLocalFile = remember(transfer.localPath) {
        transfer.localPath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
    }
    val previewCloudId = transfer.remoteId?.takeIf { it.isNotBlank() } ?: transfer.fileId?.takeIf { it.isNotBlank() }
    val previewExt = remember(transfer.fileName) {
        transfer.fileName.substringAfterLast('.', "").lowercase()
    }
    val previewCloudFile = remember(transfer.id, previewCloudId, transfer.fileName, previewExt, transfer.totalBytes, transfer.isEncrypted, transfer.accountId) {
        if (previewCloudId != null || previewLocalFile != null) {
            CloudFile(
                id = previewCloudId ?: transfer.id,
                name = transfer.fileName,
                type = previewExt.ifBlank { null },
                size = transfer.totalBytes,
                isEncrypted = transfer.isEncrypted,
                accountId = transfer.accountId
            )
        } else null
    }

    val isImageFile = previewExt in listOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp")
    val isFailed = transfer.status == TransferStatus.FAILED.name

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (previewCloudFile != null && !isFailed) {
                    FileThumb(
                        file = previewCloudFile,
                        accountId = transfer.accountId,
                        sizeDp = 52.dp,
                        cornerRadius = if (isImageFile) 26.dp else 18.dp,
                        showEncryptedBadge = false,
                        localSource = previewLocalFile
                    )
                } else {
                    val iconShape = if (isImageFile && !isFailed) CircleShape else RoundedCornerShape(18.dp)
                    val iconBg = when {
                        isFailed -> MaterialTheme.colorScheme.errorContainer
                        isImageFile -> ext.categoryImages
                        else -> ext.unknownFileBg
                    }
                    val iconTint = when {
                        isFailed -> MaterialTheme.colorScheme.error
                        isImageFile -> ext.onCategoryImages
                        else -> ext.accentBlue
                    }
                    val leadingIcon = when {
                        isFailed -> Icons.Default.ErrorOutline
                        isImageFile -> Icons.Default.Image
                        transfer.direction == "UPLOAD" || transfer.direction == "BACKUP" -> Icons.Default.Upload
                        previewExt in listOf("pdf", "doc", "docx", "txt", "md") -> Icons.Default.Description
                        else -> Icons.Default.Download
                    }
                    Surface(
                        shape = iconShape,
                        color = iconBg,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = leadingIcon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = transfer.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (transfer.isEncrypted) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Encrypted",
                                        tint = ext.lightAccent,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val sizeLabel = if (transfer.totalBytes > 0L) formatStorageSize(transfer.totalBytes) else null

                    when (transfer.status) {
                        TransferStatus.CANCELLED.name -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = listOfNotNull(directionLabel, sizeLabel).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Text(
                                        text = stringResource(R.string.transfers_cancelled),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        TransferStatus.FAILED.name -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = stringResource(R.string.transfers_failed_status),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                if (sizeLabel != null) {
                                    Text(
                                        text = sizeLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        TransferStatus.COMPLETED.name -> {
                            Text(
                                text = listOfNotNull(directionLabel, sizeLabel).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TransferStatus.PAUSED.name -> {
                            val pct = (progress * 100f).roundToInt().coerceIn(0, 100)
                            Text(
                                text = if (transfer.totalBytes > 0L) "$directionLabel paused • $pct%" else "$directionLabel paused",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TransferStatus.PENDING.name -> {
                            Text(
                                text = listOfNotNull("$directionLabel queued", sizeLabel).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        else -> {
                            val verb = if (transfer.direction == "UPLOAD" || transfer.direction == "BACKUP") "Uploading" else "Downloading"
                            val pct = (progress * 100f).roundToInt().coerceIn(0, 100)
                            val progText = when {
                                transfer.totalBytes > 0L -> "${formatStorageSize(transfer.bytesTransferred)} / ${formatStorageSize(transfer.totalBytes)} ($pct%)"
                                transfer.bytesTransferred > 0L -> formatStorageSize(transfer.bytesTransferred)
                                else -> null
                            }
                            val rateText = if (speedBytesPerSecond > 0L) {
                                val remaining = (transfer.totalBytes - transfer.bytesTransferred).coerceAtLeast(0L)
                                val eta = if (remaining > 0L) remaining / speedBytesPerSecond else 0L
                                "${formatTransferRate(speedBytesPerSecond)} • ${formatTransferEta(eta)} left"
                            } else null
                            Text(
                                text = listOfNotNull(verb, progText, rateText).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                when (transfer.status) {
                    TransferStatus.RUNNING.name, TransferStatus.PENDING.name -> {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { triggerOnce(onPause) },
                            modifier = Modifier.testTag("transfer_pause_${transfer.id}")
                        ) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = "${stringResource(R.string.action_pause)} ${transfer.fileName}"
                            )
                        }
                        IconButton(
                            onClick = { triggerOnce(onCancel) },
                            modifier = Modifier.testTag("transfer_cancel_${transfer.id}")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "${stringResource(R.string.action_cancel)} ${transfer.fileName}"
                            )
                        }
                    }
                    TransferStatus.PAUSED.name -> {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { triggerOnce(onResume) },
                            modifier = Modifier.testTag("transfer_resume_${transfer.id}")
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "${stringResource(R.string.action_resume)} ${transfer.fileName}"
                            )
                        }
                        IconButton(
                            onClick = { triggerOnce(onCancel) },
                            modifier = Modifier.testTag("transfer_cancel_${transfer.id}")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "${stringResource(R.string.action_cancel)} ${transfer.fileName}"
                            )
                        }
                    }
                    TransferStatus.FAILED.name -> {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            onClick = { triggerOnce(onRetry) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("transfer_retry_${transfer.id}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "${stringResource(R.string.action_retry)} ${transfer.fileName}",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (isActiveOrPaused) {
                Spacer(modifier = Modifier.height(10.dp))
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
                    label = "transfer_wavy_progress"
                )
                val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                val isPausedState = transfer.status == TransferStatus.PAUSED.name
                if (!isPausedState && progress <= 0f) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = ext.mintSuccess,
                        trackColor = trackColor
                    )
                } else {
                    LinearWavyProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = ext.mintSuccess,
                        trackColor = trackColor,
                        waveSpeed = if (transfer.status == TransferStatus.RUNNING.name) {
                            WavyProgressIndicatorDefaults.LinearDeterminateWavelength
                        } else {
                            0.dp
                        }
                    )
                }
            }

            val displayError = transfer.errorMessage?.takeIf { it.isNotBlank() }
            if (displayError != null && transfer.status == TransferStatus.FAILED.name) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ext.errorDeepBox,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = displayError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            } else if (displayError != null && transfer.status == TransferStatus.PENDING.name) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Retrying: $displayError",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class TransferMetricSample(val bytes: Long, val timestamp: Long)

private fun formatTransferRate(bytesPerSecond: Long): String {
    return when {
        bytesPerSecond >= 1024L * 1024L -> "%.1f MB/s".format(bytesPerSecond / (1024.0 * 1024.0))
        bytesPerSecond >= 1024L -> "%.1f KB/s".format(bytesPerSecond / 1024.0)
        else -> "$bytesPerSecond B/s"
    }
}

private fun formatTransferEta(seconds: Long): String {
    val safeSeconds = max(0L, seconds)
    val minutes = safeSeconds / 60L
    val remainder = safeSeconds % 60L
    return if (minutes > 0L) "${minutes}m ${remainder}s" else "${remainder}s"
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ServerJobItemCard(
    job: JobStatusResponse,
    accountNamesById: Map<String, String>,
    shape: Shape = RoundedCornerShape(24.dp),
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val ext = MaterialTheme.cloudyteColors
    val isMoveJob = job.isMove == true || job.stage?.contains("move", ignoreCase = true) == true
    val jobTitle = when (job.type?.lowercase()) {
        "drive_transfer" -> if (isMoveJob) "Drive Move" else "Drive Copy"
        "cross_account_transfer" -> if (isMoveJob) "Cross-Drive Move" else "Cross-Drive Copy"
        "batch_backup" -> "Batch Backup"
        "batch_restore" -> "Batch Restore"
        else -> "Server Job"
    }

    val sourceName = job.source?.driveName?.takeIf { it.isNotBlank() }
        ?: job.source?.accountId?.let { accountNamesById[it] }
    val destName = job.destination?.driveName?.takeIf { it.isNotBlank() }
        ?: job.destination?.accountId?.let { accountNamesById[it] }
    val routeLine = when {
        !sourceName.isNullOrBlank() && !destName.isNullOrBlank() -> "$sourceName → $destName"
        !destName.isNullOrBlank() -> "To $destName"
        !sourceName.isNullOrBlank() -> "From $sourceName"
        else -> null
    }

    val pct = job.effectiveProgressPct
    val progress = (pct / 100f).coerceIn(0f, 1f)
    val itemsPart = if ((job.totalItems ?: 0) > 0) {
        "${job.itemsProcessed ?: 0} / ${job.totalItems} files"
    } else null
    val bytesPart = if ((job.totalBytes ?: 0L) > 0L) {
        "${formatStorageSize(job.bytesProcessed ?: 0L)} / ${formatStorageSize(job.totalBytes ?: 0L)}"
    } else null
    val statusBadge = when (job.normalizedStatus) {
        "queued" -> "Queued"
        "running" -> "Running"
        "retrying" -> "Retrying"
        "completed" -> "Completed"
        "completed_with_errors" -> "Partial"
        "cancelled" -> "Cancelled"
        "failed", "dead_letter" -> "Failed"
        else -> job.status.replaceFirstChar { it.uppercase() }
    }

    val isFailed = job.isFailedOrCancelledStatus && job.normalizedStatus != "cancelled"
    val leadingIcon = when {
        isFailed -> Icons.Default.ErrorOutline
        isMoveJob -> Icons.AutoMirrored.Filled.DriveFileMove
        job.type?.lowercase() == "drive_transfer" -> Icons.Default.ContentCopy
        else -> Icons.Default.CloudSync
    }
    val iconBg = if (isFailed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val iconTint = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("server_job_card_${job.effectiveId}")
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = iconBg,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = jobTitle,
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = jobTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = CircleShape,
                            color = when {
                                isFailed -> MaterialTheme.colorScheme.errorContainer
                                job.isCompletedStatus -> ext.mintSuccess.copy(alpha = 0.18f)
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ) {
                            Text(
                                text = statusBadge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isFailed -> MaterialTheme.colorScheme.onErrorContainer
                                    job.isCompletedStatus -> ext.mintSuccess
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (routeLine != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = routeLine,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    val detailLine = listOfNotNull(
                        itemsPart,
                        bytesPart,
                        if (job.isActiveStatus) "$pct%" else null
                    ).joinToString(" • ").ifBlank {
                        job.stage?.takeIf { it.isNotBlank() } ?: statusBadge
                    }
                    Text(
                        text = detailLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (job.isActiveStatus) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("server_job_cancel_${job.effectiveId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "${stringResource(R.string.action_cancel)} $jobTitle"
                        )
                    }
                } else if (job.isFailedOrCancelledStatus) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        onClick = onRetry,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("server_job_retry_${job.effectiveId}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "${stringResource(R.string.action_retry)} $jobTitle",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            if (job.isActiveStatus) {
                Spacer(modifier = Modifier.height(10.dp))
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
                    label = "server_job_wavy_progress"
                )
                val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                if (progress <= 0f) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = trackColor
                    )
                } else {
                    LinearWavyProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = trackColor
                    )
                }
            }

            val errText = job.effectiveError
            if (errText != null && job.isFailedOrCancelledStatus) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ext.errorDeepBox,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

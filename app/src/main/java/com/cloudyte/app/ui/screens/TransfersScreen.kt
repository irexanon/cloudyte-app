package com.cloudyte.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudyte.app.CloudyteApplication
import com.cloudyte.app.R
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.core.transfer.TransferCommand
import com.cloudyte.app.data.TransferRepository
import com.cloudyte.app.data.formatStorageSize
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.TransferStatus
import com.cloudyte.app.domain.model.Transfer
import com.cloudyte.app.sync.AutoSyncManager
import com.cloudyte.app.ui.components.EmptyState
import com.cloudyte.app.ui.components.FileThumb
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(
    onBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val repo = remember { TransferRepository(db.transferDao()) }
    val coordinator = remember {
        (context.applicationContext as CloudyteApplication).container.transferCoordinator
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showOverflowMenu by remember { mutableStateOf(false) }

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

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val isScrolled by remember {
        androidx.compose.runtime.derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val topBarContainerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isScrolled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "transfersTopBarColor"
    )

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.transfers_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                            }
                        }
                    },
                    actions = {
                        if (selectedTab == 1 && pausableActive.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        pausableActive.forEach { t ->
                                            coordinator.dispatch(TransferCommand.Pause(t.id))
                                        }
                                    }
                                }
                            ) {
                                Text("Pause All", color = MaterialTheme.colorScheme.primary)
                            }
                        } else if (selectedTab == 3 && retryableFailed.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        retryableFailed.forEach { t ->
                                            coordinator.dispatch(TransferCommand.Retry(t.id))
                                        }
                                    }
                                }
                            ) {
                                Text("Retry All", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (completedTransfers.isNotEmpty() || pausableActive.isNotEmpty() || retryableFailed.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.action_options)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = topBarContainerColor
                    )
                )
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Tab(
                            selected = isSelected,
                            onClick = { selectedTab = index }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 4.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
            if (filteredTransfers.isEmpty()) {
                val (emptyTitle, emptySubtitle) = when (selectedTab) {
                    1 -> "No active transfers" to "Uploads and downloads currently running appear here"
                    2 -> "No completed transfers" to "Finished uploads and downloads appear here"
                    3 -> "No failed transfers" to "Interrupted or failed transfers can be retried here"
                    else -> stringResource(R.string.transfers_empty) to "Upload or download files from Home or Files to track progress"
                }
                EmptyState(
                    icon = Icons.Default.SwapVert,
                    title = emptyTitle,
                    subtitle = emptySubtitle,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = com.cloudyte.app.ui.components.LocalBottomBarInset.current)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = com.cloudyte.app.ui.components.LocalBottomBarInset.current + 24.dp
                    )
                ) {
                    items(filteredTransfers, key = { it.id }) { transfer ->
                        TransferItemCard(
                            transfer = transfer,
                            onPause = { scope.launch { coordinator.dispatch(TransferCommand.Pause(transfer.id)) } },
                            onResume = {
                                scope.launch {
                                    coordinator.dispatch(TransferCommand.Resume(transfer.id))
                                }
                            },
                            onRetry = {
                                scope.launch {
                                    coordinator.dispatch(TransferCommand.Retry(transfer.id))
                                }
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
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TransferItemCard(
    transfer: Transfer,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
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

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (previewCloudFile != null) {
                    FileThumb(
                        file = previewCloudFile,
                        accountId = transfer.accountId,
                        sizeDp = 44.dp,
                        cornerRadius = 10.dp,
                        showEncryptedBadge = false,
                        localSource = previewLocalFile
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (transfer.direction == "UPLOAD" || transfer.direction == "BACKUP") Icons.Default.Upload else Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = transfer.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (transfer.isEncrypted) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Encrypted",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val sizeLabel = if (transfer.totalBytes > 0L) formatStorageSize(transfer.totalBytes) else null
                    val subtitleText = when (transfer.status) {
                        TransferStatus.COMPLETED.name -> listOfNotNull(directionLabel, sizeLabel).joinToString(" • ")
                        TransferStatus.FAILED.name -> listOfNotNull("$directionLabel failed", sizeLabel).joinToString(" • ")
                        TransferStatus.CANCELLED.name -> "$directionLabel • Cancelled"
                        TransferStatus.PAUSED.name -> {
                            val pct = (progress * 100f).roundToInt().coerceIn(0, 100)
                            if (transfer.totalBytes > 0L) "$directionLabel paused • $pct%" else "$directionLabel paused"
                        }
                        TransferStatus.PENDING.name -> listOfNotNull("$directionLabel queued", sizeLabel).joinToString(" • ")
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
                            listOfNotNull(verb, progText, rateText).joinToString(" • ")
                        }
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isActiveOrPaused || transfer.status == TransferStatus.FAILED.name) {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                when (transfer.status) {
                    TransferStatus.RUNNING.name, TransferStatus.PENDING.name -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.action_pause))
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                        }
                    }
                    TransferStatus.PAUSED.name -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.action_resume))
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                        }
                    }
                    TransferStatus.FAILED.name -> {
                        IconButton(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_retry))
                        }
                    }
                }
            }

            if (isActiveOrPaused) {
                Spacer(modifier = Modifier.height(8.dp))
                @OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
                androidx.compose.material3.LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val displayError = transfer.errorMessage?.takeIf { it.isNotBlank() }
            if (displayError != null && transfer.status == TransferStatus.FAILED.name) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = displayError,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (displayError != null && transfer.status == TransferStatus.PENDING.name) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Retrying: $displayError",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
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

package com.cloudyte.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.cloudyte.app.CloudyteApplication
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.api.FileRepository
import com.cloudyte.app.api.OfflineFileManager
import com.cloudyte.app.data.FileCryptoActions
import com.cloudyte.app.features.player.GDriveActionsBottomSheet
import com.cloudyte.app.features.player.GDriveDeleteConfirmDialog
import com.cloudyte.app.features.player.GDriveImagePage
import com.cloudyte.app.features.player.GDriveInfoBottomSheet
import com.cloudyte.app.features.player.GDriveRenameDialog
import com.cloudyte.app.features.player.GDriveTopBar
import com.cloudyte.app.features.player.GDriveVideoContent
import com.cloudyte.app.features.player.ViewerDarkBackdrop
import com.cloudyte.app.features.player.ViewerSheetContainer
import com.cloudyte.app.features.player.formatFileSubtitle
import com.cloudyte.app.features.player.performInAppDownload
import com.cloudyte.app.features.player.performShareFileLink
import com.cloudyte.app.ui.theme.FileAudio
import com.cloudyte.app.ui.theme.FileCode
import com.cloudyte.app.ui.theme.FilePdf
import com.cloudyte.app.ui.theme.MetricLabelTextStyle
import kotlinx.coroutines.launch

@Composable
fun ImageViewerScreen(
    navController: NavController,
    accountId: String,
    fileId: String
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val app = context.applicationContext as? CloudyteApplication

    var dbFiles by remember(fileId) { mutableStateOf<List<CloudFile>>(emptyList()) }
    LaunchedEffect(fileId, accountId) {
        if (app != null) {
            val dbFile = app.container.database.cloudFileDao().getFileById(fileId)?.toCloudFile()
            if (dbFile != null) {
                FileRepository.cacheFiles(listOf(dbFile))
                val folderFiles = if (accountId.isNotBlank() && accountId != "all" && accountId != "root") {
                    app.container.database.cloudFileDao().getFilesForAccount(accountId).map { it.toCloudFile() }.filter { !it.isFolderItem }
                } else {
                    app.container.database.cloudFileDao().getAllFiles().map { it.toCloudFile() }.filter { !it.isFolderItem }
                }
                dbFiles = folderFiles.ifEmpty { listOf(dbFile) }
            }
        }
    }

    val initialFile = remember(fileId, dbFiles) {
        FileRepository.getFile(fileId) ?: dbFiles.firstOrNull { it.effectiveId == fileId }
    }
    val allFilesInFolder = remember(fileId, dbFiles) {
        val list = FileRepository.getActiveList().filter { it.isFolder != true }
        when {
            list.any { it.effectiveId == fileId } -> list
            dbFiles.any { it.effectiveId == fileId } -> dbFiles
            initialFile != null -> listOf(initialFile)
            else -> dbFiles
        }
    }

    var filterMediaOnly by remember { mutableStateOf(false) }

    val displayedFiles = remember(allFilesInFolder, filterMediaOnly, fileId) {
        if (filterMediaOnly) {
            val mediaList = allFilesInFolder.filter { it.isImage || it.isVideo }
            if (mediaList.isNotEmpty()) mediaList else allFilesInFolder
        } else {
            allFilesInFolder
        }
    }

    val initialIndex = remember(displayedFiles, fileId) {
        val idx = displayedFiles.indexOfFirst { it.effectiveId == fileId }
        if (idx >= 0) idx else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (displayedFiles.size - 1).coerceAtLeast(0)),
        pageCount = { displayedFiles.size.coerceAtLeast(1) }
    )

    var showControls by remember { mutableStateOf(true) }
    var currentScale by remember { mutableFloatStateOf(1f) }
    var resetZoomSignal by remember { mutableIntStateOf(0) }
    var showActionsSheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoveCopyDialog by remember { mutableStateOf(false) }
    var isMoveAction by remember { mutableStateOf(true) }

    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMsg by remember { mutableStateOf<String?>(null) }

    val downloadedIds by OfflineFileManager.downloadedFiles.collectAsState()

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMsg = null
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        currentScale = 1f
        resetZoomSignal = 0
    }

    LaunchedEffect(pagerState.currentPage, displayedFiles) {
        val loader = context.imageLoader
        listOf(pagerState.currentPage - 1, pagerState.currentPage + 1)
            .distinct()
            .filter { it in displayedFiles.indices }
            .forEach { idx ->
                val adj = displayedFiles[idx]
                if (!adj.isEncrypted && adj.isImage) {
                    val adjAcc = adj.effectiveAccountId.ifBlank {
                        accountId.takeIf { it.isNotBlank() && it != "all" && it != "root" }.orEmpty()
                    }
                    if (adjAcc.isNotBlank() && adj.effectiveId.isNotBlank()) {
                        val rev = adj.updatedAt?.toString() ?: adj.time?.toString() ?: adj.sizeBytes.toString()
                        val cacheKey = "img_preview_${adjAcc}_${adj.effectiveId}_$rev"
                        val url = ApiClient.getFileUrl(adjAcc, adj.effectiveId, preview = true)
                        loader.enqueue(
                            ImageRequest.Builder(context)
                                .data(url)
                                .diskCacheKey(cacheKey)
                                .memoryCacheKey(cacheKey)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .build()
                        )
                    }
                }
            }
    }

    val currentFile = displayedFiles.getOrNull(pagerState.currentPage) ?: initialFile
    val resolvedAccId = currentFile?.effectiveAccountId?.takeIf { it.isNotBlank() }
        ?: accountId.takeIf { it.isNotBlank() && it != "all" && it != "root" }.orEmpty()

    if (showMoveCopyDialog && currentFile != null) {
        MoveCopyDialog(
            file = currentFile,
            sourceAccountId = resolvedAccId.ifBlank { accountId },
            isMove = isMoveAction,
            onDismiss = { showMoveCopyDialog = false },
            onSuccess = { msg ->
                showMoveCopyDialog = false
                snackbarMsg = msg
            }
        )
    }

    if (showInfoSheet && currentFile != null) {
        GDriveInfoBottomSheet(
            file = currentFile,
            accountId = resolvedAccId,
            isDownloadedOffline = currentFile.effectiveId in downloadedIds,
            context = context,
            onDismiss = { showInfoSheet = false }
        )
    }

    if (showActionsSheet && currentFile != null) {
        GDriveActionsBottomSheet(
            file = currentFile,
            accountId = resolvedAccId,
            isDownloadedOffline = currentFile.effectiveId in downloadedIds,
            filterMediaOnly = filterMediaOnly,
            totalFileCount = allFilesInFolder.size,
            onDismiss = { showActionsSheet = false },
            onShare = {
                coroutineScope.launch {
                    val err = performShareFileLink(context, resolvedAccId, currentFile)
                    if (!err.isNullOrBlank()) snackbarMsg = err
                }
            },
            onDownload = {
                coroutineScope.launch {
                    snackbarMsg = performInAppDownload(context, resolvedAccId, currentFile)
                }
            },
            onOpenDetails = {
                val navAcc = resolvedAccId.ifBlank { accountId }
                navController.navigate("file_detail/$navAcc/${currentFile.effectiveId}")
            },
            onRename = { showRenameDialog = true },
            onMove = {
                isMoveAction = true
                showMoveCopyDialog = true
            },
            onCopy = {
                isMoveAction = false
                showMoveCopyDialog = true
            },
            onEncryptCopy = if (!currentFile.isEncrypted && app != null) {
                {
                    coroutineScope.launch {
                        snackbarMsg = FileCryptoActions.createEncryptedCopy(
                            context = context,
                            accountId = resolvedAccId,
                            targetFile = currentFile,
                            fallbackParentFolderId = null,
                            cloudFileRepository = app.container.cloudFileRepository,
                            transferRepository = app.container.transferRepository,
                            transferCoordinator = app.container.transferCoordinator
                        )
                    }
                }
            } else null,
            onToggleMediaFilter = { filterMediaOnly = !filterMediaOnly },
            onDelete = { showDeleteDialog = true }
        )
    }

    if (showRenameDialog && currentFile != null && app != null) {
        GDriveRenameDialog(
            initialName = currentFile.effectiveName,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                coroutineScope.launch {
                    val res = app.container.cloudFileRepository.rename(
                        fileId = currentFile.effectiveId,
                        accountId = resolvedAccId.ifBlank { null },
                        newName = newName
                    )
                    snackbarMsg = if (res.isSuccess) "Renamed to $newName" else (res.exceptionOrNull()?.message ?: "Rename failed")
                }
            }
        )
    }

    if (showDeleteDialog && currentFile != null && app != null) {
        GDriveDeleteConfirmDialog(
            fileName = currentFile.effectiveName,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                coroutineScope.launch {
                    val res = app.container.cloudFileRepository.delete(
                        fileId = currentFile.effectiveId,
                        accountId = resolvedAccId.ifBlank { null }
                    )
                    if (res.isSuccess) {
                        navController.popBackStack()
                    } else {
                        snackbarMsg = res.exceptionOrNull()?.message ?: "Delete failed"
                    }
                }
            }
        )
    }

    Scaffold(
        containerColor = ViewerDarkBackdrop,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ViewerDarkBackdrop)
                .testTag("image_viewer_root")
        ) {
            if (displayedFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No files available",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = currentScale <= 1.05f,
                    pageSpacing = 12.dp
                ) { page ->
                    val pageFile = displayedFiles.getOrNull(page)
                    if (pageFile != null) {
                        val pageAcc = pageFile.effectiveAccountId.ifBlank { resolvedAccId }
                        val isCurrent = pagerState.currentPage == page
                        when {
                            pageFile.isImage -> {
                                GDriveImagePage(
                                    file = pageFile,
                                    accountId = pageAcc,
                                    isCurrentPage = isCurrent,
                                    resetZoomSignal = resetZoomSignal,
                                    onScaleChanged = { scale ->
                                        if (isCurrent) currentScale = scale
                                    },
                                    onTap = { showControls = !showControls }
                                )
                            }
                            pageFile.isVideo -> {
                                GDriveVideoContent(
                                    file = pageFile,
                                    accountId = pageAcc,
                                    isActive = isCurrent,
                                    showControls = showControls,
                                    onToggleControls = { showControls = !showControls },
                                    bottomPaddingExtra = displayedFiles.size > 1
                                )
                            }
                            pageFile.isPdf -> {
                                NonImagePreviewCard(
                                    icon = Icons.Default.PictureAsPdf,
                                    iconTint = FilePdf,
                                    title = pageFile.effectiveName,
                                    subtitle = "${pageFile.formattedSize} • PDF Document",
                                    primaryLabel = "Open PDF",
                                    primaryIcon = Icons.Default.MenuBook,
                                    onTapBackdrop = { showControls = !showControls },
                                    onPrimaryClick = {
                                        navController.navigate("pdf_viewer/${pageAcc.ifBlank { accountId }}/${pageFile.effectiveId}")
                                    }
                                )
                            }
                            pageFile.isAudio -> {
                                NonImagePreviewCard(
                                    icon = Icons.Default.Audiotrack,
                                    iconTint = FileAudio,
                                    title = pageFile.effectiveName,
                                    subtitle = "${pageFile.formattedSize} • Audio",
                                    primaryLabel = "Play Audio",
                                    primaryIcon = Icons.Default.PlayArrow,
                                    onTapBackdrop = { showControls = !showControls },
                                    onPrimaryClick = {
                                        navController.navigate("audio_player/${pageAcc.ifBlank { accountId }}/${pageFile.effectiveId}")
                                    }
                                )
                            }
                            pageFile.isTextOrCode -> {
                                NonImagePreviewCard(
                                    icon = Icons.Default.Code,
                                    iconTint = FileCode,
                                    title = pageFile.effectiveName,
                                    subtitle = "${pageFile.formattedSize} • Document / Code",
                                    primaryLabel = "Open Document",
                                    primaryIcon = Icons.Default.EditNote,
                                    onTapBackdrop = { showControls = !showControls },
                                    onPrimaryClick = {
                                        navController.navigate("text_editor/${pageAcc.ifBlank { accountId }}/${pageFile.effectiveId}")
                                    }
                                )
                            }
                            else -> {
                                NonImagePreviewCard(
                                    icon = if (pageFile.isEncrypted) Icons.Default.Lock else Icons.AutoMirrored.Filled.InsertDriveFile,
                                    iconTint = Color(0xFF8AB4F8),
                                    title = pageFile.effectiveName,
                                    subtitle = "${pageFile.formattedSize} • ${pageFile.effectiveType.uppercase()}",
                                    primaryLabel = "Download",
                                    primaryIcon = Icons.Default.Download,
                                    secondaryLabel = "File Details",
                                    secondaryIcon = Icons.Default.Info,
                                    onTapBackdrop = { showControls = !showControls },
                                    onPrimaryClick = {
                                        coroutineScope.launch {
                                            snackbarMsg = performInAppDownload(context, pageAcc, pageFile)
                                        }
                                    },
                                    onSecondaryClick = {
                                        navController.navigate("file_detail/${pageAcc.ifBlank { accountId }}/${pageFile.effectiveId}")
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Google Drive Translucent Top Bar
            val subtitleText = if (currentFile != null) {
                val pageInfo = if (displayedFiles.size > 1) "${pagerState.currentPage + 1} of ${displayedFiles.size}" else null
                formatFileSubtitle(currentFile, pageInfo)
            } else null

            GDriveTopBar(
                visible = showControls,
                title = currentFile?.effectiveName ?: "Preview",
                subtitle = subtitleText,
                file = currentFile,
                isDownloadedOffline = currentFile?.effectiveId in downloadedIds,
                onBack = { navController.popBackStack() },
                onDownload = if (currentFile != null) {
                    {
                        coroutineScope.launch {
                            snackbarMsg = performInAppDownload(context, resolvedAccId, currentFile)
                        }
                    }
                } else null,
                onInfo = if (currentFile != null) {
                    { showInfoSheet = true }
                } else null,
                onMore = if (currentFile != null) {
                    { showActionsSheet = true }
                } else null,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Subtle Google Drive page position pill (only when multiple files and current page isn't a video scrubber)
            AnimatedVisibility(
                visible = showControls && displayedFiles.size > 1 && currentFile?.isVideo != true,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.testTag("viewer_page_pill")
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} of ${displayedFiles.size}",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MetricLabelTextStyle,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NonImagePreviewCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    primaryLabel: String,
    primaryIcon: ImageVector,
    secondaryLabel: String? = null,
    secondaryIcon: ImageVector? = null,
    onTapBackdrop: () -> Unit,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ViewerDarkBackdrop)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTapBackdrop() })
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ViewerSheetContainer,
            tonalElevation = 4.dp,
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(iconTint.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.68f),
                    style = MetricLabelTextStyle,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = onPrimaryClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8AB4F8),
                        contentColor = Color(0xFF0B1C3A)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(primaryIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(primaryLabel, fontWeight = FontWeight.SemiBold)
                }

                if (secondaryLabel != null && secondaryIcon != null && onSecondaryClick != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onSecondaryClick,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(secondaryIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(secondaryLabel, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

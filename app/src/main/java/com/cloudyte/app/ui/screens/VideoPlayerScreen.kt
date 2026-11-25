package com.cloudyte.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cloudyte.app.CloudyteApplication
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.api.FileRepository
import com.cloudyte.app.api.OfflineFileManager
import com.cloudyte.app.data.FileCryptoActions
import com.cloudyte.app.features.player.GDriveActionsBottomSheet
import com.cloudyte.app.features.player.GDriveDeleteConfirmDialog
import com.cloudyte.app.features.player.GDriveInfoBottomSheet
import com.cloudyte.app.features.player.GDriveRenameDialog
import com.cloudyte.app.features.player.GDriveTopBar
import com.cloudyte.app.features.player.GDriveVideoContent
import com.cloudyte.app.features.player.ViewerDarkBackdrop
import com.cloudyte.app.features.player.formatFileSubtitle
import com.cloudyte.app.features.player.performInAppDownload
import com.cloudyte.app.ui.components.AdvancedShareBottomSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VideoPlayerScreen(
    navController: NavController,
    accountId: String,
    fileId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as? CloudyteApplication

    var dbVideos by remember(fileId) { mutableStateOf<List<CloudFile>>(emptyList()) }

    LaunchedEffect(fileId, accountId) {
        if (app != null) {
            val localRow = app.container.database.cloudFileDao().getFileById(fileId)
            val dbFile = localRow?.toCloudFile()
            if (localRow != null && dbFile != null) {
                FileRepository.cacheFiles(listOf(dbFile))
                val folderVideos = app.container.database.cloudFileDao()
                    .getFilesForFolder(localRow.accountId, localRow.folderId)
                    .map { it.toCloudFile() }
                    .filter { it.isVideo && !it.isFolderItem }
                dbVideos = folderVideos.ifEmpty { listOf(dbFile) }
            }
        }
    }

    val videoList = remember(dbVideos, fileId) {
        val activeVideos = FileRepository.getActiveList().filter { it.isVideo && !it.isFolderItem }
        when {
            activeVideos.any { it.effectiveId == fileId } -> activeVideos
            dbVideos.any { it.effectiveId == fileId } -> dbVideos
            else -> listOfNotNull(FileRepository.getFile(fileId) ?: dbVideos.firstOrNull())
        }
    }

    val initialIndex = remember(videoList, fileId) {
        videoList.indexOfFirst { it.effectiveId == fileId }.coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (videoList.size - 1).coerceAtLeast(0)),
        pageCount = { videoList.size.coerceAtLeast(1) }
    )

    // Keep pager aligned if dbVideos finishes loading asynchronously with sibling videos
    LaunchedEffect(videoList.size) {
        val idx = videoList.indexOfFirst { it.effectiveId == fileId }
        if (idx >= 0 && pagerState.currentPage != idx && videoList.size > 1) {
            pagerState.scrollToPage(idx)
        }
    }

    val currentVideoIndex = pagerState.currentPage.coerceIn(0, (videoList.size - 1).coerceAtLeast(0))
    val file = videoList.getOrNull(currentVideoIndex)
        ?: remember(fileId) { FileRepository.getFile(fileId) }
    val currentActiveFileId = file?.effectiveId ?: fileId
    val fileName = file?.effectiveName ?: "Video Player"

    val effectiveAccId = file?.effectiveAccountId?.takeIf { it.isNotBlank() }
        ?: accountId.takeIf { it.isNotBlank() && it != "all" && it != "root" }.orEmpty()

    var showControls by remember { mutableStateOf(true) }
    var isControlsLocked by remember { mutableStateOf(false) }
    var isActiveVideoPlaying by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var interactionNonce by remember { mutableIntStateOf(0) }

    var showActionsSheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoveCopyDialog by remember { mutableStateOf(false) }
    var showAdvancedShareSheet by remember { mutableStateOf(false) }
    var isMoveAction by remember { mutableStateOf(true) }

    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMsg by remember { mutableStateOf<String?>(null) }
    val downloadedIds by OfflineFileManager.downloadedFiles.collectAsStateWithLifecycle()

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMsg = null
        }
    }

    // Immersive cinema mode: hide system bars when controls are hidden; restore on exit
    LaunchedEffect(showControls, isControlsLocked) {
        val activity = context.findPlayerActivity() ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (showControls && !isControlsLocked) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Keep screen awake while actively playing video
    LaunchedEffect(isActiveVideoPlaying) {
        val window = context.findPlayerActivity()?.window ?: return@LaunchedEffect
        if (isActiveVideoPlaying) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Restore system bars, orientation, brightness, and keep-screen-on cleanly when exiting player
    DisposableEffect(Unit) {
        onDispose {
            val activity = context.findPlayerActivity() ?: return@onDispose
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val lp = activity.window.attributes
            if (lp.screenBrightness >= 0f) {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                activity.window.attributes = lp
            }
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Smart auto-hide controls only while playing, not scrubbing, and no dialogs open
    LaunchedEffect(
        showControls,
        isActiveVideoPlaying,
        isScrubbing,
        showActionsSheet,
        showInfoSheet,
        showAdvancedShareSheet,
        interactionNonce
    ) {
        if (showControls &&
            isActiveVideoPlaying &&
            !isScrubbing &&
            !showActionsSheet &&
            !showInfoSheet &&
            !showAdvancedShareSheet
        ) {
            delay(4000L)
            showControls = false
        }
    }

    if (showMoveCopyDialog && file != null) {
        MoveCopyDialog(
            file = file,
            sourceAccountId = effectiveAccId.ifBlank { accountId },
            isMove = isMoveAction,
            onDismiss = { showMoveCopyDialog = false },
            onSuccess = { msg ->
                showMoveCopyDialog = false
                snackbarMsg = msg
            }
        )
    }

    if (showAdvancedShareSheet && file != null) {
        AdvancedShareBottomSheet(
            accountId = effectiveAccId.ifBlank { accountId },
            fileId = file.effectiveId,
            fileName = file.effectiveName,
            isEncrypted = file.isEncrypted,
            onDismiss = { showAdvancedShareSheet = false },
            onFeedback = { msg -> snackbarMsg = msg }
        )
    }

    if (showInfoSheet && file != null) {
        GDriveInfoBottomSheet(
            file = file,
            accountId = effectiveAccId,
            isDownloadedOffline = file.effectiveId in downloadedIds,
            context = context,
            onDismiss = { showInfoSheet = false }
        )
    }

    if (showActionsSheet && file != null) {
        GDriveActionsBottomSheet(
            file = file,
            accountId = effectiveAccId,
            isDownloadedOffline = file.effectiveId in downloadedIds,
            onDismiss = { showActionsSheet = false },
            onShare = {
                showActionsSheet = false
                showAdvancedShareSheet = true
            },
            onDownload = {
                scope.launch {
                    snackbarMsg = performInAppDownload(context, effectiveAccId, file)
                }
            },
            onOpenDetails = {
                val navAcc = effectiveAccId.ifBlank { accountId }
                navController.navigate("file_detail/$navAcc/${file.effectiveId}")
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
            onEncryptCopy = if (!file.isEncrypted && app != null) {
                {
                    scope.launch {
                        snackbarMsg = FileCryptoActions.createEncryptedCopy(
                            context = context,
                            accountId = effectiveAccId,
                            targetFile = file,
                            fallbackParentFolderId = null,
                            cloudFileRepository = app.container.cloudFileRepository,
                            transferRepository = app.container.transferRepository,
                            transferCoordinator = app.container.transferCoordinator
                        )
                    }
                }
            } else null,
            onDelete = { showDeleteDialog = true }
        )
    }

    if (showRenameDialog && file != null && app != null) {
        GDriveRenameDialog(
            initialName = file.effectiveName,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                scope.launch {
                    val res = app.container.cloudFileRepository.rename(
                        fileId = file.effectiveId,
                        accountId = effectiveAccId.ifBlank { null },
                        newName = newName
                    )
                    snackbarMsg = if (res.isSuccess) "Renamed to $newName" else (res.exceptionOrNull()?.message ?: "Rename failed")
                }
            }
        )
    }

    if (showDeleteDialog && file != null && app != null) {
        GDriveDeleteConfirmDialog(
            fileName = file.effectiveName,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                scope.launch {
                    val res = app.container.cloudFileRepository.delete(
                        fileId = file.effectiveId,
                        accountId = effectiveAccId.ifBlank { null }
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
                .testTag("video_player_root")
        ) {
            if (videoList.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    pageSpacing = 12.dp,
                    userScrollEnabled = !isControlsLocked && videoList.size > 1,
                    key = { idx -> videoList.getOrNull(idx)?.effectiveId ?: "vid_$idx" },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("video_horizontal_pager")
                ) { pageIndex ->
                    val pageVideo = videoList[pageIndex]
                    val pageAccId = pageVideo.effectiveAccountId.ifBlank {
                        accountId.takeIf { it.isNotBlank() && it != "all" && it != "root" }.orEmpty()
                    }
                    val isCurrentPage = pagerState.currentPage == pageIndex
                    GDriveVideoContent(
                        file = pageVideo,
                        accountId = pageAccId.ifBlank { accountId },
                        isActive = isCurrentPage,
                        showControls = showControls,
                        onToggleControls = { showControls = !showControls },
                        hasPrevious = pageIndex > 0,
                        hasNext = pageIndex < videoList.size - 1,
                        onPrevious = if (videoList.size > 1) {
                            {
                                if (pageIndex > 0) {
                                    scope.launch { pagerState.animateScrollToPage(pageIndex - 1) }
                                }
                            }
                        } else null,
                        onNext = if (videoList.size > 1) {
                            {
                                if (pageIndex < videoList.size - 1) {
                                    scope.launch { pagerState.animateScrollToPage(pageIndex + 1) }
                                }
                            }
                        } else null,
                        isControlsLockedExternal = isControlsLocked,
                        onControlsLockedChange = { isControlsLocked = it },
                        onUserInteraction = { interactionNonce++ },
                        onPlayingStateChange = { playing ->
                            if (isCurrentPage) isActiveVideoPlaying = playing
                        },
                        onScrubbingStateChange = { scrubbing ->
                            if (isCurrentPage) isScrubbing = scrubbing
                        }
                    )
                }
            }

            val pageInfo = if (videoList.size > 1) "${currentVideoIndex + 1} of ${videoList.size}" else null
            GDriveTopBar(
                visible = showControls && !isControlsLocked,
                title = fileName,
                subtitle = file?.let { formatFileSubtitle(it, pageInfo) },
                file = file,
                isDownloadedOffline = currentActiveFileId in downloadedIds,
                onBack = { navController.popBackStack() },
                onDownload = if (file != null) {
                    {
                        scope.launch {
                            snackbarMsg = performInAppDownload(context, effectiveAccId, file)
                        }
                    }
                } else null,
                onInfo = if (file != null) {
                    { showInfoSheet = true }
                } else null,
                onMore = if (file != null) {
                    { showActionsSheet = true }
                } else null,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

private tailrec fun Context.findPlayerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findPlayerActivity()
    else -> null
}

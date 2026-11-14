package com.cloudyte.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.cloudyte.app.features.player.performShareFileLink
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

    var currentActiveFileId by remember { mutableStateOf(fileId) }
    var dbVideo by remember(currentActiveFileId) { mutableStateOf<CloudFile?>(null) }

    LaunchedEffect(currentActiveFileId) {
        if (app != null) {
            val local = app.container.database.cloudFileDao().getFileById(currentActiveFileId)?.toCloudFile()
            if (local != null) {
                FileRepository.cacheFiles(listOf(local))
                dbVideo = local
            }
        }
    }

    val videoList = remember(dbVideo, fileId) {
        val list = FileRepository.getActiveList().filter { it.isVideo }
        when {
            list.any { it.effectiveId == fileId } -> list
            dbVideo != null -> listOf(dbVideo!!)
            else -> listOfNotNull(FileRepository.getFile(fileId))
        }
    }

    val currentVideoIndex = videoList.indexOfFirst { it.effectiveId == currentActiveFileId }.coerceAtLeast(0)
    val file = videoList.getOrNull(currentVideoIndex)
        ?: remember(currentActiveFileId) { FileRepository.getFile(currentActiveFileId) }
        ?: dbVideo
    val fileName = file?.effectiveName ?: "Video Player"

    val effectiveAccId = file?.effectiveAccountId?.takeIf { it.isNotBlank() }
        ?: accountId.takeIf { it.isNotBlank() && it != "all" && it != "root" }.orEmpty()

    var showControls by remember { mutableStateOf(true) }
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

    LaunchedEffect(showControls) {
        if (showControls && !showActionsSheet && !showInfoSheet) {
            delay(4500)
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
                scope.launch {
                    val err = performShareFileLink(context, effectiveAccId, file)
                    if (!err.isNullOrBlank()) snackbarMsg = err
                }
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
            if (file != null) {
                GDriveVideoContent(
                    file = file,
                    accountId = effectiveAccId.ifBlank { accountId },
                    isActive = true,
                    showControls = showControls,
                    onToggleControls = { showControls = !showControls },
                    hasPrevious = currentVideoIndex > 0,
                    hasNext = currentVideoIndex < videoList.size - 1,
                    onPrevious = if (videoList.size > 1) {
                        {
                            if (currentVideoIndex > 0) {
                                currentActiveFileId = videoList[currentVideoIndex - 1].effectiveId
                            }
                        }
                    } else null,
                    onNext = if (videoList.size > 1) {
                        {
                            if (currentVideoIndex < videoList.size - 1) {
                                currentActiveFileId = videoList[currentVideoIndex + 1].effectiveId
                            }
                        }
                    } else null
                )
            }

            val pageInfo = if (videoList.size > 1) "${currentVideoIndex + 1} of ${videoList.size}" else null
            GDriveTopBar(
                visible = showControls,
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

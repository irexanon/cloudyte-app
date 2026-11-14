package com.cloudyte.app.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cloudyte.app.CloudyteApplication
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.api.FileRepository
import com.cloudyte.app.api.OfflineFileManager
import com.cloudyte.app.core.cache.PreviewFetcher
import com.cloudyte.app.data.FileCryptoActions
import com.cloudyte.app.features.player.GDriveActionsBottomSheet
import com.cloudyte.app.features.player.GDriveDeleteConfirmDialog
import com.cloudyte.app.features.player.GDriveInfoBottomSheet
import com.cloudyte.app.features.player.GDriveRenameDialog
import com.cloudyte.app.features.player.GDriveTopBar
import com.cloudyte.app.features.player.ViewerDocumentBackdrop
import com.cloudyte.app.features.player.formatFileSubtitle
import com.cloudyte.app.features.player.performInAppDownload
import com.cloudyte.app.features.player.performShareFileLink
import com.cloudyte.app.ui.theme.FilePdf
import com.cloudyte.app.ui.theme.MetricLabelTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfViewerScreen(
    navController: NavController,
    accountId: String,
    fileId: String
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val app = context.applicationContext as? CloudyteApplication
    val density = LocalDensity.current

    var dbFile by remember(fileId) { mutableStateOf<CloudFile?>(null) }
    var dbLookupDone by remember(fileId) { mutableStateOf(FileRepository.getFile(fileId) != null || app == null) }
    LaunchedEffect(fileId) {
        if (app != null && FileRepository.getFile(fileId) == null) {
            val local = withContext(Dispatchers.IO) {
                app.container.database.cloudFileDao().getFileById(fileId)?.toCloudFile()
            }
            if (local != null) {
                FileRepository.cacheFiles(listOf(local))
                dbFile = local
            }
        }
        dbLookupDone = true
    }
    val file = remember(fileId, dbFile) { FileRepository.getFile(fileId) ?: dbFile }
    val fileName = file?.effectiveName ?: "Document.pdf"

    val effectiveAccId = file?.effectiveAccountId?.takeIf { it.isNotBlank() }
        ?: accountId.takeIf { it.isNotBlank() && it != "all" && it != "root" }.orEmpty()

    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    var pageCount by remember { mutableIntStateOf(0) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    val renderMutex = remember { Mutex() }

    val pageCache = remember(fileId, retryTrigger) {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        val cacheKb = (maxKb / 8).coerceIn(8 * 1024, 32 * 1024)
        object : LruCache<Int, Bitmap>(cacheKb) {
            override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    var showControls by remember { mutableStateOf(true) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showActionsSheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoveCopyDialog by remember { mutableStateOf(false) }
    var isMoveAction by remember { mutableStateOf(true) }

    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMsg by remember { mutableStateOf<String?>(null) }
    val downloadedIds by OfflineFileManager.downloadedFiles.collectAsState()

    val listState = rememberLazyListState()
    val currentVisiblePage by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset / 2
            val centered = layoutInfo.visibleItemsInfo.minByOrNull { item ->
                kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
            }
            (centered?.index ?: listState.firstVisibleItemIndex).coerceAtLeast(0)
        }
    }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMsg = null
        }
    }

    LaunchedEffect(fileId, dbLookupDone, file?.isEncrypted, effectiveAccId, retryTrigger) {
        if (!dbLookupDone) return@LaunchedEffect
        isLoading = true
        loadProgress = 0f
        errorMessage = null
        try {
            val resolvedFile = file ?: CloudFile(id = fileId, name = fileName, accountId = effectiveAccId)
            val cachedPdfFile = PreviewFetcher.resolvePreviewFile(
                context = context,
                accountId = effectiveAccId.ifBlank { accountId },
                file = resolvedFile
            ) { p ->
                loadProgress = p
            }.getOrThrow()

            val descriptor = withContext(Dispatchers.IO) {
                ParcelFileDescriptor.open(cachedPdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            val pdfRenderer = withContext(Dispatchers.IO) { PdfRenderer(descriptor) }
            renderMutex.withLock {
                runCatching { renderer?.close() }
                runCatching { pfd?.close() }
                pfd = descriptor
                renderer = pdfRenderer
                pageCount = pdfRenderer.pageCount
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.localizedMessage ?: "Failed to open PDF"
        } finally {
            isLoading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
        }
    }

    if (showJumpDialog && pageCount > 1) {
        var pageInput by remember { mutableStateOf((currentVisiblePage + 1).toString()) }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Go to page") },
            text = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { next ->
                        if (next.all { it.isDigit() }) pageInput = next
                    },
                    singleLine = true,
                    label = { Text("Page (1 - $pageCount)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                val parsed = pageInput.toIntOrNull()
                Button(
                    onClick = {
                        val targetIdx = ((parsed ?: 1) - 1).coerceIn(0, pageCount - 1)
                        showJumpDialog = false
                        coroutineScope.launch { listState.scrollToItem(targetIdx) }
                    },
                    enabled = parsed != null && parsed in 1..pageCount,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancel")
                }
            }
        )
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
            isDownloadedOffline = fileId in downloadedIds,
            context = context,
            onDismiss = { showInfoSheet = false }
        )
    }

    if (showActionsSheet && file != null) {
        GDriveActionsBottomSheet(
            file = file,
            accountId = effectiveAccId,
            isDownloadedOffline = fileId in downloadedIds,
            onDismiss = { showActionsSheet = false },
            onShare = {
                coroutineScope.launch {
                    val err = performShareFileLink(context, effectiveAccId, file)
                    if (!err.isNullOrBlank()) snackbarMsg = err
                }
            },
            onDownload = {
                coroutineScope.launch {
                    snackbarMsg = performInAppDownload(context, effectiveAccId, file)
                }
            },
            onOpenDetails = {
                val navAcc = effectiveAccId.ifBlank { accountId }
                navController.navigate("file_detail/$navAcc/$fileId")
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
                    coroutineScope.launch {
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
                coroutineScope.launch {
                    val res = app.container.cloudFileRepository.rename(fileId, effectiveAccId.ifBlank { null }, newName)
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
                coroutineScope.launch {
                    val res = app.container.cloudFileRepository.delete(fileId, effectiveAccId.ifBlank { null })
                    if (res.isSuccess) navController.popBackStack()
                    else snackbarMsg = res.exceptionOrNull()?.message ?: "Delete failed"
                }
            }
        )
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 3.5f)
        scale = nextScale
        if (nextScale <= 1.02f) {
            panOffset = Offset.Zero
        } else {
            val maxX = ((viewportSize.width * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
            val maxY = ((viewportSize.height * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
            panOffset = Offset(
                x = (panOffset.x + panChange.x).coerceIn(-maxX, maxX),
                y = (panOffset.y + panChange.y).coerceIn(-maxY, maxY)
            )
        }
    }

    Scaffold(
        containerColor = ViewerDocumentBackdrop,
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
                .background(ViewerDocumentBackdrop)
                .onSizeChanged { viewportSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = {
                            if (scale > 1.15f) {
                                scale = 1f
                                panOffset = Offset.Zero
                            } else {
                                scale = 2f
                            }
                        }
                    )
                }
                .transformable(
                    state = transformState,
                    canPan = { scale > 1.02f }
                )
                .testTag("pdf_viewer_root")
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            progress = { if (loadProgress in 0.05f..0.99f) loadProgress else 0f },
                            color = FilePdf,
                            trackColor = Color.White.copy(alpha = 0.16f),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Opening PDF…",
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = FilePdf,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Cannot open PDF",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage.orEmpty(),
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = { retryTrigger++ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.16f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Retry")
                        }
                    }
                }
                pageCount > 0 -> {
                    val targetWidthPx = remember(viewportSize.width, density) {
                        val base = if (viewportSize.width > 0) viewportSize.width else with(density) { 400.dp.roundToPx() }
                        (base * 1.5f).toInt().coerceIn(720, 2048)
                    }
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = scale <= 1.05f,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(
                            start = 10.dp,
                            end = 10.dp,
                            top = 88.dp,
                            bottom = 76.dp
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = panOffset.x
                                translationY = panOffset.y
                            }
                            .testTag("pdf_pages_list")
                    ) {
                        items(count = pageCount, key = { it }) { index ->
                            PdfPageCard(
                                pageIndex = index,
                                renderer = renderer,
                                renderMutex = renderMutex,
                                cache = pageCache,
                                targetWidthPx = targetWidthPx
                            )
                        }
                    }
                }
            }

            // Google Drive Translucent Top Bar
            val pageSubtitle = if (pageCount > 0) "$pageCount pages" else null
            GDriveTopBar(
                visible = showControls,
                title = fileName,
                subtitle = file?.let { formatFileSubtitle(it, pageSubtitle) } ?: pageSubtitle,
                file = file,
                isDownloadedOffline = fileId in downloadedIds,
                onBack = { navController.popBackStack() },
                onDownload = if (file != null) {
                    {
                        coroutineScope.launch {
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

            // Google Drive Signature Floating Page Pill ("1 / 14", tap to jump)
            AnimatedVisibility(
                visible = !isLoading && errorMessage == null && pageCount > 0,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(if (showControls) Alignment.TopStart else Alignment.BottomCenter)
                    .then(
                        if (showControls) {
                            Modifier
                                .statusBarsPadding()
                                .padding(start = 16.dp, top = 66.dp)
                        } else {
                            Modifier
                                .navigationBarsPadding()
                                .padding(bottom = 18.dp)
                        }
                    )
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = pageCount > 1) { showJumpDialog = true }
                        .testTag("pdf_page_pill")
                ) {
                    Text(
                        text = "${currentVisiblePage + 1} / $pageCount",
                        color = Color.White,
                        style = MetricLabelTextStyle,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPageCard(
    pageIndex: Int,
    renderer: PdfRenderer?,
    renderMutex: Mutex,
    cache: LruCache<Int, Bitmap>,
    targetWidthPx: Int
) {
    var bitmap by remember(pageIndex, renderer) { mutableStateOf(cache.get(pageIndex)) }
    var aspectRatio by remember(pageIndex, renderer) { mutableFloatStateOf(0.707f) }

    LaunchedEffect(pageIndex, renderer, targetWidthPx) {
        val existing = cache.get(pageIndex)
        if (existing != null) {
            bitmap = existing
            if (existing.height > 0) {
                aspectRatio = existing.width.toFloat() / existing.height.toFloat()
            }
            return@LaunchedEffect
        }
        val activeRenderer = renderer ?: return@LaunchedEffect
        val rendered = withContext(Dispatchers.IO) {
            renderMutex.withLock {
                if (pageIndex < 0 || pageIndex >= activeRenderer.pageCount) return@withLock null
                val page = activeRenderer.openPage(pageIndex)
                try {
                    val pageW = page.width.coerceAtLeast(1)
                    val pageH = page.height.coerceAtLeast(1)
                    val ratio = pageW.toFloat() / pageH.toFloat()
                    val outW = targetWidthPx
                    val outH = (outW / ratio).toInt().coerceIn(200, 4096)
                    val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(pageIndex, bmp)
                    bmp
                } finally {
                    page.close()
                }
            }
        }
        if (rendered != null) {
            if (rendered.height > 0) {
                aspectRatio = rendered.width.toFloat() / rendered.height.toFloat()
            }
            bitmap = rendered
        }
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceIn(0.4f, 2.2f))
    ) {
        val ready = bitmap
        if (ready != null) {
            Image(
                bitmap = ready.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = FilePdf,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

package com.cloudyte.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.cloudyte.app.features.player.performInAppDownload
import com.cloudyte.app.features.player.performShareFileLink
import com.cloudyte.app.ui.theme.JetBrainsMonoFontFamily
import com.cloudyte.app.ui.theme.MetricLabelTextStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    navController: NavController,
    accountId: String,
    fileId: String
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val app = context.applicationContext as? CloudyteApplication

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
    val fileName = file?.effectiveName ?: "Document"

    var textContent by remember { mutableStateOf("") }
    var originalContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var isEditMode by remember { mutableStateOf(false) }
    var fontSize by remember { mutableFloatStateOf(13f) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showActionsSheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoveCopyDialog by remember { mutableStateOf(false) }
    var isMoveAction by remember { mutableStateOf(true) }

    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMsg by remember { mutableStateOf<String?>(null) }
    val downloadedIds by OfflineFileManager.downloadedFiles.collectAsState()

    val effectiveAccId = file?.effectiveAccountId?.takeIf { it.isNotBlank() }
        ?: accountId.takeIf { it.isNotBlank() && it != "all" && it != "root" }.orEmpty()

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMsg = null
        }
    }

    LaunchedEffect(fileId, dbLookupDone, effectiveAccId, file?.isEncrypted, retryTrigger) {
        if (!dbLookupDone) return@LaunchedEffect
        isLoading = true
        errorMessage = null
        try {
            val resolvedFile = file ?: CloudFile(id = fileId, name = fileName, accountId = effectiveAccId)
            val readyFile = PreviewFetcher.resolvePreviewFile(
                context = context,
                accountId = effectiveAccId.ifBlank { accountId },
                file = resolvedFile
            ).getOrThrow()
            val content = withContext(Dispatchers.IO) { readyFile.readText() }
            textContent = content
            originalContent = content
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            errorMessage = e.localizedMessage ?: "Failed to read file"
        } finally {
            isLoading = false
        }
    }

    fun saveContent() {
        if (isSaving || app == null) return
        isSaving = true
        coroutineScope.launch {
            try {
                val queueDirectory = java.io.File(context.filesDir, "queued-text").apply { mkdirs() }
                val stagedFile = java.io.File.createTempFile("cloudyte-queued-text-", ".txt", queueDirectory)
                stagedFile.writeText(textContent)
                val shouldEncrypt = file?.isEncrypted == true || runCatching {
                    com.cloudyte.app.data.UserPreferences.observe(context).first().isUploadEncryptionEnabled
                }.getOrDefault(false)
                val targetAccount = effectiveAccId.ifBlank { accountId }
                val transferId = app.container.transferRepository.enqueueTransfer(
                    accountId = targetAccount,
                    fileName = fileName,
                    localPath = Uri.fromFile(stagedFile).toString(),
                    direction = com.cloudyte.app.db.TransferDirection.UPLOAD,
                    totalBytes = stagedFile.length(),
                    isEncrypted = shouldEncrypt,
                    originalName = if (shouldEncrypt) fileName else null,
                    originalMimeType = if (shouldEncrypt) (file?.mimeType ?: "text/plain") else null,
                    originalSize = if (shouldEncrypt) stagedFile.length() else null
                )
                app.container.transferCoordinator.dispatch(com.cloudyte.app.core.transfer.TransferCommand.Start(transferId))
                originalContent = textContent
                isEditMode = false
                snackbarMsg = "Save queued for upload"
            } catch (error: Exception) {
                Toast.makeText(context, error.message ?: "Failed to queue save", Toast.LENGTH_LONG).show()
            } finally {
                isSaving = false
            }
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

    val lines = remember(textContent) { textContent.lines() }
    val lineCount = lines.size
    val wordCount = remember(textContent) {
        if (textContent.isBlank()) 0 else textContent.trim().split(Regex("\\s+")).size
    }
    val charCount = textContent.length

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = fileName,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isEditMode) "Editing mode" else "Read only",
                            color = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("viewer_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchBar = !showSearchBar }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Find in document",
                            tint = if (showSearchBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = { isEditMode = !isEditMode }) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Visibility else Icons.Default.Edit,
                            contentDescription = if (isEditMode) "View mode" else "Edit mode",
                            tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (isEditMode || textContent != originalContent) {
                        IconButton(onClick = { saveContent() }) {
                            if (isSaving) {
                                com.cloudyte.app.ui.components.AppLoadingIndicator(size = 20.dp, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(Icons.Default.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.testTag("viewer_more_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Increase font size (A+)") },
                                onClick = {
                                    if (fontSize < 24f) fontSize += 2f
                                    showOverflowMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Decrease font size (A-)") },
                                onClick = {
                                    if (fontSize > 9f) fontSize -= 2f
                                    showOverflowMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy all text") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText(fileName, textContent))
                                    snackbarMsg = "Copied to clipboard"
                                }
                            )
                            if (file != null) {
                                DropdownMenuItem(
                                    text = { Text(if (fileId in downloadedIds) "Saved offline • Export copy" else "Download") },
                                    leadingIcon = {
                                        Icon(
                                            if (fileId in downloadedIds) Icons.Default.DownloadDone else Icons.Default.Download,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        coroutineScope.launch {
                                            snackbarMsg = performInAppDownload(context, effectiveAccId, file)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Details & Info") },
                                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showInfoSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("All file actions…") },
                                    onClick = {
                                        showOverflowMenu = false
                                        showActionsSheet = true
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("text_editor_root")
        ) {
            AnimatedVisibility(visible = showSearchBar) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Find in document…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotBlank()) {
                            val matches = lines.count { it.contains(searchQuery, ignoreCase = true) }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$matches lines",
                                color = MaterialTheme.colorScheme.primary,
                                style = MetricLabelTextStyle,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                com.cloudyte.app.ui.components.LoadingState(message = "Opening document…")
            } else if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Unable to display document", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = { retryTrigger++ },
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            } else {
                val verticalScroll = rememberScrollState()
                val horizontalScroll = rememberScrollState()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(verticalScroll)
                    ) {
                        Column(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            for (i in 1..lineCount) {
                                Text(
                                    text = "$i",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    fontSize = fontSize.sp,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    lineHeight = (fontSize * 1.4f).sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalScroll)
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            if (isEditMode) {
                                BasicTextField(
                                    value = textContent,
                                    onValueChange = { textContent = it },
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontSize = fontSize.sp,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        lineHeight = (fontSize * 1.4f).sp
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = textContent,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = fontSize.sp,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    lineHeight = (fontSize * 1.4f).sp
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lines: $lineCount • Words: $wordCount • Chars: $charCount",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MetricLabelTextStyle
                        )
                        Text(
                            text = file?.effectiveType?.uppercase() ?: "TEXT",
                            color = MaterialTheme.colorScheme.primary,
                            style = MetricLabelTextStyle,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

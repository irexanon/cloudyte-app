package com.cloudyte.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cloudyte.app.R
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.ui.components.AdvancedShareBottomSheet
import com.cloudyte.app.ui.components.AppLoadingIndicator
import com.cloudyte.app.ui.components.ExpressiveStarburstBadge
import com.cloudyte.app.ui.components.FileThumb
import com.cloudyte.app.ui.components.LoadingState
import com.cloudyte.app.ui.components.LocalBottomBarInset
import com.cloudyte.app.ui.openFileInViewer
import com.cloudyte.app.ui.theme.MetricTextStyle
import com.cloudyte.app.ui.theme.cloudyteColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FileDetailScreen(
    navController: NavController,
    accountId: String,
    fileId: String,
    viewModel: FileDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefsState by com.cloudyte.app.data.UserPreferences.observe(context).collectAsState(initial = com.cloudyte.app.data.UserPreferencesState())
    val file by viewModel.file.collectAsState()
    val shareUrl by viewModel.shareUrl.collectAsState()
    val shareError by viewModel.shareError.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()
    val isProcessingCryptoAction by viewModel.isProcessingCryptoAction.collectAsState()
    var showRenameDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoveCopyDialog by remember { mutableStateOf(false) }
    var isMoveAction by remember { mutableStateOf(true) }
    var showAdvancedShareSheet by remember { mutableStateOf(false) }
    var actionFeedbackMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val strShareLink = stringResource(R.string.files_menu_share_link)
    val strDownloadStarted = stringResource(R.string.detail_download_started)
    val strDownloadingDesc = stringResource(R.string.detail_downloading_desc)
    val ext = MaterialTheme.cloudyteColors

    LaunchedEffect(shareError) {
        shareError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearShareError()
        }
    }

    LaunchedEffect(actionFeedbackMessage) {
        actionFeedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            actionFeedbackMessage = null
        }
    }

    LaunchedEffect(fileId) {
        viewModel.loadFile(fileId)
    }

    if (showMoveCopyDialog && file != null) {
        MoveCopyDialog(
            file = file!!,
            sourceAccountId = accountId,
            isMove = isMoveAction,
            onDismiss = { showMoveCopyDialog = false },
            onSuccess = { msg ->
                showMoveCopyDialog = false
                actionFeedbackMessage = msg
                if (isMoveAction) {
                    navController.popBackStack()
                }
            }
        )
    }

    if (showAdvancedShareSheet && file != null) {
        val target = file!!
        val shareAccId = target.effectiveAccountId.ifBlank {
            accountId.takeIf { it.isNotBlank() && it != "all" && it != "root" }.orEmpty()
        }
        AdvancedShareBottomSheet(
            accountId = shareAccId,
            fileId = target.effectiveId,
            fileName = target.effectiveName,
            isEncrypted = target.isEncrypted,
            onDismiss = { showAdvancedShareSheet = false },
            onFeedback = { msg -> actionFeedbackMessage = msg }
        )
    }

    if (shareUrl != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearShareUrl() },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    text = stringResource(R.string.files_menu_share_link),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = shareUrl!!,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, strShareLink))
                        viewModel.clearShareUrl()
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.action_share), color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearShareUrl() }) {
                    Text(stringResource(R.string.action_close), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    text = stringResource(R.string.detail_delete_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.detail_delete_confirm),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        val fid = file?.effectiveId ?: fileId
                        viewModel.deleteFile(accountId, fid) {
                            navController.popBackStack()
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = LocalBottomBarInset.current)
            )
        },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("file_detail_back_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.detail_title_sentence),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                val renameEnabled = file?.isEncrypted != true || file?.originalMetadataAvailable == true
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (renameEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    onClick = { if (renameEnabled) showRenameDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("file_detail_rename_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.action_rename),
                            tint = if (renameEnabled) ext.lightAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val nonNullFileForDialog = file
        if (showRenameDialog && nonNullFileForDialog != null) {
            if (newFileName.isEmpty()) newFileName = nonNullFileForDialog.effectiveName
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(28.dp),
                title = {
                    Text(
                        text = stringResource(R.string.files_rename_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newFileName.isNotBlank()) {
                                viewModel.renameFile(accountId, nonNullFileForDialog.effectiveId, newFileName) {
                                    showRenameDialog = false
                                    actionFeedbackMessage = "Rename saved locally; syncing when online"
                                }
                            }
                        },
                        shape = CircleShape
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        if (file == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                LoadingState(stringResource(R.string.detail_loading))
            }
            return@Scaffold
        }

        val nonNullFile = file!!
        val detailAccId = nonNullFile.effectiveAccountId.ifBlank {
            accountId.takeIf { it.isNotBlank() && it != "all" && it != "root" }.orEmpty()
        }
        val canPreviewMedia = (nonNullFile.isImage || nonNullFile.isVideo) &&
            !nonNullFile.isFolderItem &&
            (!nonNullFile.isEncrypted || nonNullFile.originalMetadataAvailable)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Expressive Preview Hero Card
            Surface(
                shape = RoundedCornerShape(36.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .clickable(enabled = !nonNullFile.isFolderItem && (!nonNullFile.isEncrypted || nonNullFile.originalMetadataAvailable)) {
                        openFileInViewer(nonNullFile, detailAccId, navController)
                    }
            ) {
                if (canPreviewMedia) {
                    FileThumb(
                        file = nonNullFile,
                        accountId = detailAccId,
                        sizeDp = 168.dp,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 36.dp
                    )
                } else {
                    val (starburstBg, starburstTint, heroIcon) = when {
                        nonNullFile.isFolderItem -> Triple(MaterialTheme.colorScheme.primaryContainer, ext.accentBlue, Icons.Default.Folder)
                        nonNullFile.isEncrypted && !nonNullFile.originalMetadataAvailable -> Triple(MaterialTheme.colorScheme.primaryContainer, ext.lightAccent, Icons.Default.Lock)
                        nonNullFile.isPdf -> Triple(ext.mediaContainer, ext.categoryMedia, Icons.Default.PictureAsPdf)
                        nonNullFile.isAudio -> Triple(ext.mintContainer, ext.mintSuccess, Icons.Default.MusicNote)
                        nonNullFile.isTextOrCode -> Triple(ext.mintContainer, ext.mintSuccess, Icons.Default.Code)
                        else -> Triple(ext.mintContainer, ext.mintSuccess, Icons.Default.InsertDriveFile)
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ExpressiveStarburstBadge(
                            size = 112.dp,
                            containerColor = starburstBg
                        ) {
                            Icon(
                                imageVector = heroIcon,
                                contentDescription = null,
                                tint = starburstTint,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Title & Pill Chips
            Text(
                text = nonNullFile.effectiveName,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Text(
                        text = if (nonNullFile.isFolderItem) stringResource(R.string.files_type_folder) else nonNullFile.formattedSize,
                        style = MetricTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                val extLabel = when {
                    nonNullFile.isNoteItem -> "TG NOTE"
                    else -> nonNullFile.effectiveType.uppercase().ifBlank { "FILE" }
                }
                Surface(
                    shape = CircleShape,
                    color = ext.mintSuccess
                ) {
                    Text(
                        text = extLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = ext.onMintSuccess,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                if (nonNullFile.isEncrypted) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = ext.lightAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "E2EE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ext.lightAccent
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            val dateFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
            val dateStr = nonNullFile.time?.let { dateFormat.format(Date(it)) }
                ?: nonNullFile.createdAt
                ?: stringResource(R.string.detail_uploaded_recent)

            // Details Card with crisp 1dp outlineVariant dividers
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    DetailRow(
                        label = stringResource(R.string.detail_location),
                        value = nonNullFile.location ?: nonNullFile.path ?: stringResource(R.string.detail_location_root)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailRow(
                        label = stringResource(R.string.detail_uploaded),
                        value = dateStr
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailRow(
                        label = stringResource(R.string.detail_size),
                        value = nonNullFile.formattedSize,
                        isMetric = true
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailRow(
                        label = stringResource(R.string.detail_type),
                        value = nonNullFile.effectiveType.uppercase()
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailRow(
                        label = "Encryption",
                        value = if (nonNullFile.isEncrypted) {
                            if (nonNullFile.originalMetadataAvailable) "AES-256-GCM (E2EE)" else "Encrypted (Key Required)"
                        } else {
                            "Standard (Plaintext)"
                        }
                    )
                    if (nonNullFile.isEncrypted && !nonNullFile.encryptionKeyId.isNullOrBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailRow(
                            label = "Key ID",
                            value = nonNullFile.encryptionKeyId.take(16),
                            isMetric = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Primary Open Pill Button (60.dp)
            if (!nonNullFile.isFolderItem && (!nonNullFile.isEncrypted || nonNullFile.originalMetadataAvailable)) {
                Button(
                    onClick = { openFileInViewer(nonNullFile, accountId, navController) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) {
                    Icon(
                        imageVector = when {
                            nonNullFile.isVideo -> Icons.Default.PlayArrow
                            nonNullFile.isImage -> Icons.Default.Visibility
                            nonNullFile.isAudio -> Icons.Default.MusicNote
                            nonNullFile.isTextOrCode -> Icons.Default.Edit
                            nonNullFile.isPdf -> Icons.Default.PictureAsPdf
                            else -> Icons.AutoMirrored.Filled.OpenInNew
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when {
                            nonNullFile.isVideo -> stringResource(R.string.detail_play_video)
                            nonNullFile.isImage -> stringResource(R.string.detail_view_image)
                            nonNullFile.isAudio -> stringResource(R.string.detail_play_audio)
                            nonNullFile.isTextOrCode -> stringResource(R.string.detail_open_editor)
                            nonNullFile.isPdf -> stringResource(R.string.detail_view_pdf)
                            else -> stringResource(R.string.detail_open_file)
                        },
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Secondary E2EE Action Pill (56.dp)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = {
                    if (!isProcessingCryptoAction) {
                        if (nonNullFile.isEncrypted) {
                            viewModel.downloadOrDecryptExportFile(accountId, nonNullFile) { msg ->
                                actionFeedbackMessage = msg
                            }
                        } else {
                            viewModel.createEncryptedCopy(accountId, nonNullFile) { msg ->
                                actionFeedbackMessage = msg
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (nonNullFile.isEncrypted) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = ext.lightAccent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            nonNullFile.isEncrypted -> stringResource(R.string.detail_decrypt_export)
                            nonNullFile.isFolderItem -> "Encrypt to new folder"
                            else -> stringResource(R.string.detail_create_encrypted_copy)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = ext.lightAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Split-Rounded Row 1: Move & Copy
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 10.dp, bottomEnd = 10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    onClick = {
                        isMoveAction = true
                        showMoveCopyDialog = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 54.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = ext.lightAccent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_move),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 28.dp, bottomEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    onClick = {
                        isMoveAction = false
                        showMoveCopyDialog = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 54.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = ext.lightAccent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_copy),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Split-Rounded Row 2: Download, Share, Delete
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 10.dp, bottomEnd = 10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    onClick = {
                        val useInternal = prefsState.downloadDirectory != "EXTERNAL"
                        if (nonNullFile.isEncrypted || useInternal) {
                            viewModel.downloadOrDecryptExportFile(accountId, nonNullFile) { msg ->
                                actionFeedbackMessage = msg
                            }
                        } else {
                            val token = ApiClient.getToken()
                            val downloadUrl = ApiClient.getCleanDownloadUrl(accountId, nonNullFile.effectiveId, preview = false)
                            try {
                                val request = android.app.DownloadManager.Request(Uri.parse(downloadUrl))
                                if (!token.isNullOrBlank() && ApiClient.API_BASE_URL.startsWith("https://", ignoreCase = true)) {
                                    request.addRequestHeader("Authorization", "Bearer $token")
                                }
                                request.setTitle(nonNullFile.effectiveName)
                                request.setDescription(strDownloadingDesc)
                                request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                request.setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, nonNullFile.effectiveName)

                                val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                downloadManager.enqueue(request)
                                actionFeedbackMessage = strDownloadStarted
                            } catch (e: Exception) {
                                viewModel.downloadOrDecryptExportFile(accountId, nonNullFile) { msg ->
                                    actionFeedbackMessage = msg
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 54.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = ext.lightAccent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_download),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    onClick = { showAdvancedShareSheet = true },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 54.dp)
                        .testTag("file_detail_share_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = ext.lightAccent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_share),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 28.dp, bottomEnd = 28.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .width(64.dp)
                        .heightIn(min = 54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isDeleting) {
                            AppLoadingIndicator(color = MaterialTheme.colorScheme.error, size = 20.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp + LocalBottomBarInset.current))
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, isMetric: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = if (isMetric) MetricTextStyle else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

package com.cloudyte.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cloudyte.app.R
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.ui.components.FileThumb
import com.cloudyte.app.ui.components.LocalBottomBarInset
import com.cloudyte.app.ui.openFileInViewer
import com.cloudyte.app.ui.theme.MetricLabelTextStyle
import com.cloudyte.app.ui.theme.MetricTextStyle
import kotlinx.coroutines.flow.first
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
    var actionFeedbackMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val strShareLink = stringResource(R.string.files_menu_share_link)
    val strDownloadStarted = stringResource(R.string.detail_download_started)
    val strDownloadFailed = stringResource(R.string.detail_download_failed)
    val strDownloadingDesc = stringResource(R.string.detail_downloading_desc)

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

    if (shareUrl != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearShareUrl() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = stringResource(R.string.files_menu_share_link),
                    color = MaterialTheme.colorScheme.onSurface
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = stringResource(R.string.action_share),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearShareUrl() }) {
                    Text(
                        text = stringResource(R.string.action_close),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = stringResource(R.string.detail_delete_title),
                    color = MaterialTheme.colorScheme.onSurface
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = stringResource(R.string.detail_title),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { showRenameDialog = true },
                    enabled = file?.isEncrypted != true || file?.originalMetadataAvailable == true
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.action_rename),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    ) { innerPadding ->
        val nonNullFileForDialog = file
        if (showRenameDialog && nonNullFileForDialog != null) {
            if (newFileName.isEmpty()) newFileName = nonNullFileForDialog.effectiveName
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.files_rename_title),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface,
                confirmButton = {
                    TextButton(onClick = {
                        if (newFileName.isNotBlank()) {
                            viewModel.renameFile(accountId, nonNullFileForDialog.effectiveId, newFileName) {
                                showRenameDialog = false
                                actionFeedbackMessage = "Rename saved locally; syncing when online"
                            }
                        }
                    }) {
                        Text(
                            text = stringResource(R.string.action_save),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }

        if (file == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                com.cloudyte.app.ui.components.LoadingState(stringResource(R.string.detail_loading))
            }
            return@Scaffold
        }

        val nonNullFile = file!!

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Media Preview Area using FileThumb
            val detailAccId = nonNullFile.effectiveAccountId.ifBlank {
                accountId.takeIf { it.isNotBlank() && it != "all" && it != "root" }.orEmpty()
            }
            FileThumb(
                file = nonNullFile,
                accountId = detailAccId,
                sizeDp = 180.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clickable(enabled = !nonNullFile.isFolderItem && (!nonNullFile.isEncrypted || nonNullFile.originalMetadataAvailable)) {
                        openFileInViewer(nonNullFile, detailAccId, navController)
                    },
                cornerRadius = 16.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = nonNullFile.effectiveName,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${nonNullFile.formattedSize} • ${nonNullFile.effectiveType.uppercase()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MetricLabelTextStyle
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val dateFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
            val dateStr = nonNullFile.time?.let { dateFormat.format(Date(it)) }
                ?: nonNullFile.createdAt
                ?: stringResource(R.string.detail_uploaded_recent)

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.detail_section_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow(
                        label = stringResource(R.string.detail_location),
                        value = nonNullFile.location ?: nonNullFile.path ?: stringResource(R.string.detail_location_root)
                    )
                    DetailRow(
                        label = stringResource(R.string.detail_uploaded),
                        value = dateStr
                    )
                    DetailRow(
                        label = stringResource(R.string.detail_size),
                        value = nonNullFile.formattedSize,
                        isMetric = true
                    )
                    DetailRow(
                        label = stringResource(R.string.detail_type),
                        value = nonNullFile.effectiveType.uppercase()
                    )
                    DetailRow(
                        label = "Encryption",
                        value = if (nonNullFile.isEncrypted) {
                            if (nonNullFile.originalMetadataAvailable) "End-to-End Encrypted (AES-256-GCM)"
                            else "Encrypted (Key Required)"
                        } else {
                            "Standard (Plaintext)"
                        }
                    )
                    if (nonNullFile.isEncrypted && !nonNullFile.encryptionKeyId.isNullOrBlank()) {
                        DetailRow(
                            label = "Key ID",
                            value = nonNullFile.encryptionKeyId.take(16),
                            isMetric = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val primaryColor = MaterialTheme.colorScheme.primary

            // Action button: Open in viewer / player / editor (for non-folder items with available metadata)
            if (!nonNullFile.isFolderItem && (!nonNullFile.isEncrypted || nonNullFile.originalMetadataAvailable)) {
                Button(
                    onClick = {
                        openFileInViewer(nonNullFile, accountId, navController)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = when {
                            nonNullFile.isVideo -> Icons.Default.PlayArrow
                            nonNullFile.isImage -> Icons.Default.Visibility
                            nonNullFile.isAudio -> Icons.Default.MusicNote
                            nonNullFile.isTextOrCode -> Icons.Default.Edit
                            nonNullFile.isPdf -> Icons.Default.PictureAsPdf
                            else -> Icons.Default.OpenInNew
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
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
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Explicit Encryption / Decryption Action Row
            OutlinedButton(
                onClick = {
                    if (nonNullFile.isEncrypted) {
                        viewModel.downloadOrDecryptExportFile(accountId, nonNullFile) { msg ->
                            actionFeedbackMessage = msg
                        }
                    } else {
                        viewModel.createEncryptedCopy(accountId, nonNullFile) { msg ->
                            actionFeedbackMessage = msg
                        }
                    }
                },
                enabled = !isProcessingCryptoAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
            ) {
                Icon(
                    imageVector = if (nonNullFile.isEncrypted) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        nonNullFile.isEncrypted -> "Decrypt / Export file"
                        nonNullFile.isFolderItem -> "Encrypt to new folder"
                        else -> "Create encrypted copy"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Move & Copy actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        isMoveAction = true
                        showMoveCopyDialog = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.action_move), style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = {
                        isMoveAction = false
                        showMoveCopyDialog = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.action_copy), style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val useInternal = prefsState.downloadDirectory != "EXTERNAL"
                        if (nonNullFile.isEncrypted || useInternal) {
                            viewModel.downloadOrDecryptExportFile(accountId, nonNullFile) { msg ->
                                actionFeedbackMessage = msg
                            }
                        } else {
                            val token = ApiClient.getToken()
                            val downloadUrl = "${ApiClient.API_BASE_URL}/api/download/$accountId/${nonNullFile.effectiveId}?token=$token"

                            try {
                                val request = android.app.DownloadManager.Request(Uri.parse(downloadUrl))
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
                        .heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.action_download), style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = { viewModel.shareFile(accountId, nonNullFile.effectiveId) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.action_share), style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isDeleting) {
                        com.cloudyte.app.ui.components.AppLoadingIndicator(
                            color = MaterialTheme.colorScheme.error,
                            size = 18.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp + LocalBottomBarInset.current))
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, isMetric: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = if (isMetric) MetricTextStyle else MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

package com.cloudyte.app.features.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cloudyte.app.CloudyteApplication
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.api.OfflineFileManager
import com.cloudyte.app.api.ShareRequest
import com.cloudyte.app.api.formatByteCount
import com.cloudyte.app.data.FileCryptoActions
import com.cloudyte.app.ui.components.FileThumb
import com.cloudyte.app.ui.theme.FileAudio
import com.cloudyte.app.ui.theme.FileCode
import com.cloudyte.app.ui.theme.FileDoc
import com.cloudyte.app.ui.theme.FilePdf
import com.cloudyte.app.ui.theme.FileVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

val ViewerDarkBackdrop = Color(0xFF000000)
val ViewerDocumentBackdrop = Color(0xFF121318)
val ViewerSheetContainer = Color(0xFF1E2026)

@Composable
fun GDriveFileTypeIcon(
    file: CloudFile,
    modifier: Modifier = Modifier
) {
    val (icon, tint) = when {
        file.isImage -> Icons.Default.Image to Color(0xFFEA4335)
        file.isVideo -> Icons.Default.Movie to FileVideo
        file.isAudio -> Icons.Default.MusicNote to FileAudio
        file.isPdf -> Icons.Default.PictureAsPdf to FilePdf
        file.isTextOrCode -> Icons.Default.Code to FileCode
        file.isEncrypted -> Icons.Default.Lock to FileDoc
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to Color(0xFF8AB4F8)
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(20.dp)
    )
}

@Composable
fun GDriveTopBar(
    visible: Boolean,
    title: String,
    subtitle: String?,
    file: CloudFile?,
    isDownloadedOffline: Boolean,
    onBack: () -> Unit,
    onDownload: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    extraActions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.84f),
                            Color.Black.copy(alpha = 0.46f),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("viewer_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                if (file != null) {
                    Spacer(modifier = Modifier.width(2.dp))
                    GDriveFileTypeIcon(file = file)
                    Spacer(modifier = Modifier.width(10.dp))
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                extraActions()

                if (onDownload != null) {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("viewer_download_button")
                    ) {
                        Icon(
                            imageVector = if (isDownloadedOffline) Icons.Default.DownloadDone else Icons.Default.Download,
                            contentDescription = if (isDownloadedOffline) "Saved offline" else "Download",
                            tint = if (isDownloadedOffline) Color(0xFF81C995) else Color.White
                        )
                    }
                }

                if (onInfo != null) {
                    IconButton(
                        onClick = onInfo,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("viewer_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Details & Info",
                            tint = Color.White
                        )
                    }
                }

                if (onMore != null) {
                    IconButton(
                        onClick = onMore,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("viewer_more_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GDriveActionsBottomSheet(
    file: CloudFile,
    accountId: String,
    isDownloadedOffline: Boolean,
    filterMediaOnly: Boolean? = null,
    totalFileCount: Int = 0,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onOpenDetails: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onEncryptCopy: (() -> Unit)? = null,
    onToggleMediaFilter: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ViewerSheetContainer,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                FileThumb(
                    file = file,
                    accountId = accountId,
                    sizeDp = 44.dp,
                    cornerRadius = 10.dp,
                    showEncryptedBadge = false
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.effectiveName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val encLabel = if (file.isEncrypted) " • Encrypted" else ""
                    Text(
                        text = "${file.formattedSize} • ${file.effectiveType.uppercase()}$encLabel",
                        color = Color.White.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = Color.White.copy(alpha = 0.1f)
            )

            ViewerActionSheetRow(
                icon = Icons.Default.Share,
                title = "Share link",
                subtitle = if (file.isEncrypted) "Disabled for encrypted files" else null,
                onClick = { onDismiss(); onShare() },
                testTag = "viewer_action_share"
            )
            ViewerActionSheetRow(
                icon = if (isDownloadedOffline) Icons.Default.DownloadDone else Icons.Default.Download,
                title = if (isDownloadedOffline) "Export another copy" else "Download",
                onClick = { onDismiss(); onDownload() },
                testTag = "viewer_action_download"
            )
            ViewerActionSheetRow(
                icon = Icons.Default.OpenInNew,
                title = "File details",
                onClick = { onDismiss(); onOpenDetails() },
                testTag = "viewer_action_details"
            )
            ViewerActionSheetRow(
                icon = Icons.Default.Edit,
                title = "Rename",
                onClick = { onDismiss(); onRename() },
                testTag = "viewer_action_rename"
            )
            ViewerActionSheetRow(
                icon = Icons.AutoMirrored.Filled.DriveFileMove,
                title = "Move to…",
                onClick = { onDismiss(); onMove() },
                testTag = "viewer_action_move"
            )
            ViewerActionSheetRow(
                icon = Icons.Default.ContentCopy,
                title = "Make a copy…",
                onClick = { onDismiss(); onCopy() },
                testTag = "viewer_action_copy"
            )
            if (!file.isEncrypted && onEncryptCopy != null) {
                ViewerActionSheetRow(
                    icon = Icons.Default.Lock,
                    title = "Create encrypted copy",
                    onClick = { onDismiss(); onEncryptCopy() },
                    testTag = "viewer_action_encrypt"
                )
            }
            if (onToggleMediaFilter != null && filterMediaOnly != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = Color.White.copy(alpha = 0.1f)
                )
                ViewerActionSheetRow(
                    icon = Icons.Default.FilterList,
                    title = if (filterMediaOnly) "Show all files ($totalFileCount)" else "Show media only",
                    onClick = { onDismiss(); onToggleMediaFilter() },
                    testTag = "viewer_action_media_filter"
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = Color.White.copy(alpha = 0.1f)
            )
            ViewerActionSheetRow(
                icon = Icons.Default.Delete,
                title = "Delete",
                isDestructive = true,
                onClick = { onDismiss(); onDelete() },
                testTag = "viewer_action_delete"
            )
        }
    }
}

@Composable
private fun ViewerActionSheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    isDestructive: Boolean = false,
    testTag: String,
    onClick: () -> Unit
) {
    val contentColor = if (isDestructive) Color(0xFFF28B82) else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = contentColor.copy(alpha = if (isDestructive) 1f else 0.88f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = contentColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.54f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GDriveInfoBottomSheet(
    file: CloudFile,
    accountId: String,
    isDownloadedOffline: Boolean,
    context: Context,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var driveLabel by remember(accountId) { mutableStateOf(accountId.ifBlank { "Drive" }) }
    LaunchedEffect(accountId, file.effectiveAccountId) {
        val app = context.applicationContext as? CloudyteApplication
        val resolved = file.effectiveAccountId.ifBlank { accountId }
        if (app != null && resolved.isNotBlank()) {
            val acc = withContext(Dispatchers.IO) {
                app.container.database.accountDao().getAccountById(resolved)
            }
            if (acc != null) {
                driveLabel = acc.name?.takeIf { it.isNotBlank() } ?: acc.accountType.ifBlank { resolved }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ViewerSheetContainer,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GDriveFileTypeIcon(file = file, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = file.effectiveName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            Spacer(modifier = Modifier.height(12.dp))

            InfoRow("Type", file.mimeType?.takeIf { it.isNotBlank() } ?: file.effectiveType.uppercase())
            val exactBytes = file.sizeBytes
            val sizeDisplay = if (exactBytes > 0L) {
                "${file.formattedSize} (${formatByteCount(exactBytes)})"
            } else {
                file.formattedSize
            }
            InfoRow("Size", sizeDisplay)
            InfoRow("Drive", driveLabel)
            InfoRow("Location", file.path?.takeIf { it.isNotBlank() } ?: file.location?.takeIf { it.isNotBlank() } ?: "Root")
            val timestamp = file.updatedAt ?: file.time
            val dateText = if (timestamp != null && timestamp > 0L) {
                val millis = if (timestamp < 10_000_000_000L) timestamp * 1000L else timestamp
                DateFormat.format("MMM d, yyyy • h:mm a", Date(millis)).toString()
            } else {
                file.createdAt?.takeIf { it.isNotBlank() } ?: "Unknown"
            }
            InfoRow("Modified", dateText)
            InfoRow("Encryption", if (file.isEncrypted) "AES-256-GCM Encrypted" else "Standard")
            InfoRow("Offline availability", if (isDownloadedOffline) "Saved on device" else "Cloud only (preview cache)")
            if (!file.resolution.isNullOrBlank()) {
                InfoRow("Dimensions", file.resolution)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.56f),
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun GDriveRenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var draft by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Rename file") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                label = { Text("Name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("viewer_rename_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(draft.trim()) },
                enabled = draft.trim().isNotBlank() && draft.trim() != initialName,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GDriveDeleteConfirmDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Delete file?") },
        text = { Text("Are you sure you want to delete '$fileName' from your drive?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.onError)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        }
    )
}

suspend fun performInAppDownload(
    context: Context,
    accountId: String,
    file: CloudFile
): String {
    val app = context.applicationContext as? CloudyteApplication
        ?: return "App context unavailable"
    val acc = accountId.takeIf { it.isNotBlank() && it != "all" && it != "root" }
        ?: file.effectiveAccountId
    return FileCryptoActions.decryptAndExportFile(
        context = context,
        accountId = acc,
        targetFile = file,
        transferRepository = app.container.transferRepository,
        transferCoordinator = app.container.transferCoordinator
    )
}

suspend fun performShareFileLink(
    context: Context,
    accountId: String,
    file: CloudFile
): String? {
    if (file.isEncrypted) {
        return "Encrypted sharing is not supported"
    }
    val acc = file.effectiveAccountId.ifBlank {
        accountId.takeIf { it.isNotBlank() && it != "all" && it != "root" }.orEmpty()
    }
    if (acc.isBlank() || file.effectiveId.isBlank()) {
        return "Select a valid drive first"
    }
    val shareUrl = withContext(Dispatchers.IO) {
        runCatching {
            val response = ApiClient.apiService.shareFile(ShareRequest(acc, file.effectiveId))
            response.effectiveUrl
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: ApiClient.getFileUrl(acc, file.effectiveId, preview = false)
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(file.effectiveName, shareUrl))
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, file.effectiveName)
        putExtra(Intent.EXTRA_TEXT, shareUrl)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Link"))
    return null
}

fun formatFileSubtitle(file: CloudFile, pageInfo: String? = null): String {
    val parts = mutableListOf(file.formattedSize, file.effectiveType.uppercase())
    if (!pageInfo.isNullOrBlank()) parts += pageInfo
    return parts.joinToString(" • ")
}

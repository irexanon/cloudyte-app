package com.cloudyte.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudyte.app.R
import com.cloudyte.app.api.Account
import com.cloudyte.app.api.DriveManifestResponse
import com.cloudyte.app.api.DriveTransferRequest
import com.cloudyte.app.data.DriveTransferManager
import com.cloudyte.app.data.formatStorageSize
import com.cloudyte.app.ui.theme.cloudyteColors
import kotlinx.coroutines.launch

@Composable
fun DriveCopyMoveDialog(
    sourceAccount: Account,
    allAccounts: List<Account>,
    initialIsMove: Boolean = false,
    sourceFolderId: String = "root",
    sourceFolderName: String? = null,
    onDismiss: () -> Unit,
    onStarted: (jobId: String?, isMove: Boolean, targetDriveName: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val ext = MaterialTheme.cloudyteColors

    var isMove by remember { mutableStateOf(initialIsMove) }
    val otherAccounts = remember(allAccounts, sourceAccount.effectiveId) {
        allAccounts.filter { it.effectiveId != sourceAccount.effectiveId }
    }
    val candidateTargets = remember(allAccounts, sourceAccount.effectiveId) {
        otherAccounts + sourceAccount
    }
    var selectedTargetId by remember(candidateTargets) {
        mutableStateOf(otherAccounts.firstOrNull()?.effectiveId ?: sourceAccount.effectiveId)
    }
    var targetFolderInput by remember {
        mutableStateOf(
            if (sourceFolderName.isNullOrBlank()) "" else ""
        )
    }
    var conflictStrategy by remember { mutableStateOf("skip") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val normalizedTargetFolder = targetFolderInput.trim().trim('/').ifBlank { "root" }
    val isSameDriveRootLoop = selectedTargetId == sourceAccount.effectiveId &&
        (sourceFolderId.isBlank() || sourceFolderId == "root") &&
        normalizedTargetFolder == "root"

    val dialogTitle = when {
        !sourceFolderName.isNullOrBlank() && isMove -> "Move '$sourceFolderName'"
        !sourceFolderName.isNullOrBlank() -> "Copy '$sourceFolderName'"
        isMove -> stringResource(R.string.drive_transfer_dialog_move_title)
        else -> stringResource(R.string.drive_transfer_dialog_copy_title)
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isMove) Icons.AutoMirrored.Filled.DriveFileMove else Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = dialogTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "From ${sourceAccount.effectiveName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mode Toggle: Copy vs Move
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeChip(
                        label = stringResource(R.string.drive_transfer_mode_copy),
                        selected = !isMove,
                        onClick = { if (!isSubmitting) isMove = false },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("drive_transfer_mode_copy")
                    )
                    ModeChip(
                        label = stringResource(R.string.drive_transfer_mode_move),
                        selected = isMove,
                        onClick = { if (!isSubmitting) isMove = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("drive_transfer_mode_move")
                    )
                }

                if (isMove) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.drive_transfer_move_safety_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.drive_transfer_target_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    candidateTargets.forEach { acc ->
                        val isSelected = acc.effectiveId == selectedTargetId
                        val isSameDrive = acc.effectiveId == sourceAccount.effectiveId
                        val dotColor = when {
                            acc.effectiveType.contains("google", true) -> ext.providerGoogle
                            acc.effectiveType.contains("one", true) -> ext.providerOneDrive
                            else -> ext.providerTelegram
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = !isSubmitting) {
                                    selectedTargetId = acc.effectiveId
                                    errorMessage = null
                                }
                                .testTag("drive_transfer_target_${acc.effectiveId}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = if (isSubmitting) null else {
                                        {
                                            selectedTargetId = acc.effectiveId
                                            errorMessage = null
                                        }
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isSameDrive) "${acc.effectiveName} (Same drive)" else acc.effectiveName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = acc.effectiveType,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = targetFolderInput,
                    onValueChange = {
                        targetFolderInput = it
                        errorMessage = null
                    },
                    enabled = !isSubmitting,
                    label = { Text(stringResource(R.string.drive_transfer_target_folder_label)) },
                    placeholder = { Text(stringResource(R.string.drive_transfer_target_folder_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("drive_transfer_target_folder_input")
                )

                Text(
                    text = stringResource(R.string.drive_transfer_conflict_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "skip" to stringResource(R.string.drive_transfer_conflict_skip),
                        "rename" to stringResource(R.string.drive_transfer_conflict_rename),
                        "overwrite" to stringResource(R.string.drive_transfer_conflict_overwrite)
                    ).forEach { (key, label) ->
                        ModeChip(
                            label = label,
                            selected = conflictStrategy == key,
                            onClick = { if (!isSubmitting) conflictStrategy = key },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("drive_transfer_conflict_$key")
                        )
                    }
                }

                if (isSameDriveRootLoop) {
                    Text(
                        text = "Choose a different destination drive or enter a subfolder path (e.g. Backups/Copy).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                errorMessage?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting && selectedTargetId.isNotBlank() && !isSameDriveRootLoop,
                onClick = {
                    val targetAccount = candidateTargets.firstOrNull { it.effectiveId == selectedTargetId }
                    isSubmitting = true
                    errorMessage = null
                    scope.launch {
                        val request = DriveTransferRequest(
                            sourceAccountId = sourceAccount.effectiveId,
                            targetAccountId = selectedTargetId,
                            sourceFolderId = sourceFolderId.ifBlank { "root" },
                            targetFolderId = normalizedTargetFolder,
                            isMove = isMove,
                            conflictStrategy = conflictStrategy
                        )
                        val result = DriveTransferManager.startDriveTransfer(request)
                        isSubmitting = false
                        result.onSuccess { resp ->
                            onStarted(
                                resp.jobId,
                                isMove,
                                targetAccount?.effectiveName ?: "target drive"
                            )
                        }.onFailure { err ->
                            errorMessage = err.message ?: "Failed to start drive transfer"
                        }
                    }
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMove) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("drive_transfer_confirm_button")
            ) {
                if (isSubmitting) {
                    AppLoadingIndicator(
                        size = 18.dp,
                        color = if (isMove) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = if (isMove) {
                            stringResource(R.string.drive_transfer_start_move)
                        } else {
                            stringResource(R.string.drive_transfer_start_copy)
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.action_cancel), color = ext.lightAccent)
            }
        }
    )
}

@Composable
fun DriveManifestDownloadDialog(
    accountId: String,
    driveName: String,
    folderId: String? = null,
    folderName: String? = null,
    onDismiss: () -> Unit,
    onQueued: (queuedCount: Int, destFolderName: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = MaterialTheme.cloudyteColors

    var isLoading by remember { mutableStateOf(true) }
    var isQueueing by remember { mutableStateOf(false) }
    var manifest by remember { mutableStateOf<DriveManifestResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryCounter by remember { mutableIntStateOf(0) }

    LaunchedEffect(accountId, folderId, retryCounter) {
        isLoading = true
        errorMessage = null
        val result = DriveTransferManager.fetchManifest(
            accountId = accountId,
            folderId = folderId
        )
        result.onSuccess { data ->
            manifest = data
        }.onFailure { err ->
            errorMessage = err.message ?: "Failed to load drive manifest"
        }
        isLoading = false
    }

    val isFolderScoped = !folderId.isNullOrBlank() && folderId != "root"
    val targetLabel = folderName?.takeIf { it.isNotBlank() } ?: driveName

    AlertDialog(
        onDismissRequest = { if (!isQueueing) onDismiss() },
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = if (isFolderScoped) {
                            stringResource(R.string.drive_download_folder_title)
                        } else {
                            stringResource(R.string.drive_download_title)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = targetLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    isLoading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppLoadingIndicator(size = 24.dp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.drive_download_scanning),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    errorMessage != null -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = errorMessage ?: "Manifest error",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    manifest != null -> {
                        val m = manifest!!
                        if (m.files.isEmpty()) {
                            Text(
                                text = stringResource(R.string.drive_download_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Files",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${m.files.size}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Folders",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${m.totalFolders}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Total size",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatStorageSize(m.totalBytes),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Downloads are resumable and preserve the folder structure inside your configured downloads directory.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (m.truncated) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.drive_download_truncated_warning, m.files.size),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (errorMessage != null) {
                Button(
                    onClick = { retryCounter++ },
                    shape = CircleShape,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("drive_download_retry_button")
                ) {
                    Text(stringResource(R.string.action_retry), fontWeight = FontWeight.Bold)
                }
            } else {
                val fileCount = manifest?.files?.size ?: 0
                Button(
                    enabled = !isLoading && !isQueueing && fileCount > 0,
                    onClick = {
                        val m = manifest ?: return@Button
                        isQueueing = true
                        scope.launch {
                            val result = DriveTransferManager.enqueueManifestDownload(
                                context = context,
                                manifest = m,
                                rootLabelOverride = folderName?.takeIf { it.isNotBlank() } ?: driveName
                            )
                            isQueueing = false
                            result.onSuccess { res ->
                                onQueued(res.queuedCount, res.destinationFolderName)
                            }.onFailure { err ->
                                errorMessage = err.message ?: "Failed to queue downloads"
                            }
                        }
                    },
                    shape = CircleShape,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("drive_download_confirm_button")
                ) {
                    if (isQueueing) {
                        AppLoadingIndicator(size = 18.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(
                            text = if (fileCount > 0) {
                                stringResource(R.string.drive_download_confirm_format, fileCount)
                            } else {
                                stringResource(R.string.action_download)
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isQueueing,
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.action_cancel), color = ext.lightAccent)
            }
        }
    )
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

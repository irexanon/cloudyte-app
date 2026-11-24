package com.cloudyte.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudyte.app.api.CloudFile
import kotlinx.coroutines.launch
import java.util.Locale

enum class FileQuickFilter(
    val label: String,
    val icon: ImageVector? = null
) {
    ALL("All", null),
    OFFLINE("Available offline", Icons.Default.OfflinePin),
    PHOTOS("Photos", Icons.Default.Image),
    VIDEOS("Videos", Icons.Default.Movie),
    DOCUMENTS("Documents", Icons.Default.PictureAsPdf),
    AUDIO("Audio", Icons.Default.MusicNote),
    ARCHIVES("Archives", Icons.Default.FolderZip),
    ENCRYPTED("Encrypted", Icons.Default.Lock);

    fun matches(file: CloudFile, downloadedIds: Set<String>): Boolean {
        if (this == ALL) return true
        if (this == OFFLINE) return !file.isFolderItem && file.effectiveId in downloadedIds
        if (this == ENCRYPTED) return file.isEncrypted
        if (file.isFolderItem) return false
        return when (this) {
            PHOTOS -> file.isImage
            VIDEOS -> file.isVideo
            AUDIO -> file.isAudio
            DOCUMENTS -> file.isPdf || file.isTextOrCode || file.effectiveName.lowercase(Locale.US).let {
                it.endsWith(".doc") || it.endsWith(".docx") || it.endsWith(".xls") ||
                    it.endsWith(".xlsx") || it.endsWith(".ppt") || it.endsWith(".pptx")
            }
            ARCHIVES -> file.effectiveName.lowercase(Locale.US).let {
                it.endsWith(".zip") || it.endsWith(".rar") || it.endsWith(".7z") ||
                    it.endsWith(".tar") || it.endsWith(".gz") || it.endsWith(".apk")
            }
            else -> true
        }
    }
}

@Composable
fun DriveQuickFilterBar(
    selectedFilter: FileQuickFilter,
    onSelectFilter: (FileQuickFilter) -> Unit,
    offlineCount: Int,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(FileQuickFilter.entries, key = { it.name }) { filter ->
            val selected = filter == selectedFilter
            val chipLabel = if (filter == FileQuickFilter.OFFLINE && offlineCount > 0) {
                "${filter.label} ($offlineCount)"
            } else {
                filter.label
            }
            FilterChip(
                selected = selected,
                onClick = {
                    onSelectFilter(if (selected && filter != FileQuickFilter.ALL) FileQuickFilter.ALL else filter)
                },
                label = {
                    Text(
                        text = chipLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                    )
                },
                leadingIcon = {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (filter.icon != null) {
                        Icon(
                            imageVector = filter.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconColor = com.cloudyte.app.ui.theme.LocalCloudyteColors.current.lightAccent,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                ),
                border = null,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("filter_chip_${filter.name.lowercase(Locale.US)}")
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FilesFilterSortSheet(
    selectedFilter: FileQuickFilter,
    onSelectFilter: (FileQuickFilter) -> Unit,
    offlineCount: Int,
    sortOptions: List<Pair<String, Boolean>>,
    onSelectSort: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ext = com.cloudyte.app.ui.theme.LocalCloudyteColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(ext.dragHandle)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Filter & Sort",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (selectedFilter != FileQuickFilter.ALL) {
                    androidx.compose.material3.TextButton(
                        onClick = { onSelectFilter(FileQuickFilter.ALL) }
                    ) {
                        Text(
                            text = "Reset filter",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Filter by type",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FileQuickFilter.entries.forEach { filter ->
                    val selected = filter == selectedFilter
                    val chipLabel = if (filter == FileQuickFilter.OFFLINE && offlineCount > 0) {
                        "${filter.label} ($offlineCount)"
                    } else {
                        filter.label
                    }
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onSelectFilter(if (selected && filter != FileQuickFilter.ALL) FileQuickFilter.ALL else filter)
                        },
                        label = {
                            Text(
                                text = chipLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else if (filter.icon != null) {
                                Icon(
                                    imageVector = filter.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconColor = ext.lightAccent,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = null,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("filter_chip_${filter.name.lowercase(Locale.US)}")
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Text(
                text = "Sort by",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            sortOptions.forEachIndexed { index, (label, isSelected) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .clickable {
                            onSelectSort(index)
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) ext.lightAccent else MaterialTheme.colorScheme.onSurface
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = ext.lightAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveItemActionSheet(
    file: CloudFile,
    accountId: String,
    isOfflinePinned: Boolean,
    offlineDownloadProgress: Float?,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onToggleOffline: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onEncryptOrDecrypt: () -> Unit,
    onQuickInfo: () -> Unit,
    onOpenFullDetails: () -> Unit,
    onSendFileCopy: (() -> Unit)? = null,
    onOpenWithExternal: (() -> Unit)? = null,
    onDownloadFolder: (() -> Unit)? = null,
    onTransferFolderToDrive: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val ext = com.cloudyte.app.ui.theme.LocalCloudyteColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.padding(top = 36.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(ext.dragHandle)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            // Compact Header Row: Thumbnail + Title/Subtitle + Small Info Icon Button (Zero extra row gap)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
            ) {
                FileThumb(
                    file = file,
                    accountId = accountId,
                    sizeDp = 44.dp,
                    cornerRadius = 10.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.effectiveName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val subtitle = if (file.isFolderItem) {
                            "Folder"
                        } else {
                            "${file.formattedSize} • ${file.effectiveType.uppercase()}"
                        }
                        Text(
                            text = subtitle,
                            style = com.cloudyte.app.ui.theme.MetricLabelTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isOfflinePinned && !file.isFolderItem) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DownloadDone,
                                        contentDescription = "Available offline",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "Offline",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
                androidx.compose.material3.IconButton(
                    onClick = { onDismiss(); onQuickInfo() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (offlineDownloadProgress != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Saving for offline access… ${(offlineDownloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { offlineDownloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            SheetActionItem(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                title = if (file.isFolderItem) "Open folder" else "Open preview",
                onClick = { onDismiss(); onOpen() }
            )

            if (!file.isFolderItem) {
                SheetActionItem(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    title = "Open with…",
                    subtitle = "Open in another installed app",
                    onClick = {
                        onDismiss()
                        if (onOpenWithExternal != null) {
                            onOpenWithExternal()
                        } else {
                            coroutineScope.launch {
                                com.cloudyte.app.features.player.performOpenWithExternalApp(context, accountId, file)
                            }
                        }
                    }
                )
                SheetActionItem(
                    icon = if (isOfflinePinned) Icons.Default.DownloadDone else Icons.Default.OfflinePin,
                    title = if (isOfflinePinned) "Remove from offline access" else "Make available offline",
                    subtitle = if (isOfflinePinned) "Stored in local offline vault" else "Save for instant viewing without internet",
                    onClick = { onDismiss(); onToggleOffline() }
                )
                SheetActionItem(
                    icon = Icons.Default.Share,
                    title = "Share link",
                    subtitle = if (file.isEncrypted) "Disabled for encrypted files" else "Copy link & open share sheet",
                    onClick = { onDismiss(); onShare() }
                )
                SheetActionItem(
                    icon = Icons.Default.Share,
                    title = "Send file copy",
                    subtitle = if (file.isEncrypted) "Decrypts & shares actual file to other apps" else "Share actual file via Android Share Sheet",
                    onClick = {
                        onDismiss()
                        if (onSendFileCopy != null) {
                            onSendFileCopy()
                        } else {
                            coroutineScope.launch {
                                com.cloudyte.app.features.player.performSendFileCopy(context, accountId, file)
                            }
                        }
                    }
                )
                SheetActionItem(
                    icon = Icons.Default.Download,
                    title = "Download to device",
                    onClick = { onDismiss(); onDownload() }
                )
            } else {
                if (onDownloadFolder != null) {
                    SheetActionItem(
                        icon = Icons.Default.Download,
                        title = "Download folder",
                        subtitle = "Resumable download preserving subfolders",
                        onClick = { onDismiss(); onDownloadFolder() }
                    )
                }
                if (onTransferFolderToDrive != null) {
                    SheetActionItem(
                        icon = Icons.Default.ContentCopy,
                        title = "Copy / Move folder to drive…",
                        subtitle = "Server-side folder transfer to another drive",
                        onClick = { onDismiss(); onTransferFolderToDrive() }
                    )
                }
            }

            SheetActionItem(
                icon = Icons.AutoMirrored.Filled.DriveFileMove,
                title = "Move to…",
                onClick = { onDismiss(); onMove() }
            )
            SheetActionItem(
                icon = Icons.Default.ContentCopy,
                title = "Make a copy…",
                onClick = { onDismiss(); onCopy() }
            )
            SheetActionItem(
                icon = Icons.Default.Edit,
                title = "Rename",
                onClick = { onDismiss(); onRename() }
            )
            SheetActionItem(
                icon = if (file.isEncrypted) Icons.Default.LockOpen else Icons.Default.Lock,
                title = when {
                    file.isEncrypted -> "Decrypt / Export"
                    file.isFolderItem -> "Encrypt to new folder"
                    else -> "Create encrypted copy (E2EE)"
                },
                onClick = { onDismiss(); onEncryptOrDecrypt() }
            )
            SheetActionItem(
                icon = Icons.Default.Info,
                title = "Details & activity",
                onClick = { onDismiss(); onOpenFullDetails() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            SheetActionItem(
                icon = Icons.Default.Delete,
                title = "Delete",
                isDestructive = true,
                onClick = { onDismiss(); onDelete() }
            )
        }
    }
}

@Composable
private fun QuickActionPill(
    icon: ImageVector,
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (highlighted) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SheetActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                fontWeight = FontWeight.Medium
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

package com.cloudyte.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cloudyte.app.api.ActiveShareDto
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.ShareRequest
import kotlinx.coroutines.launch
import retrofit2.HttpException

private data class ExpiryOption(val label: String, val hours: Int?)

private val SHARE_EXPIRY_OPTIONS = listOf(
    ExpiryOption("Never", null),
    ExpiryOption("1h", 1),
    ExpiryOption("24h", 24),
    ExpiryOption("7d", 168),
    ExpiryOption("30d", 720)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedShareBottomSheet(
    accountId: String,
    fileId: String,
    fileName: String,
    isEncrypted: Boolean,
    onDismiss: () -> Unit,
    onFeedback: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedExpiryHours by remember { mutableStateOf<Int?>(null) }
    var maxDownloadsText by remember { mutableStateOf("") }
    var pinText by remember { mutableStateOf("") }
    var allowDownload by remember { mutableStateOf(true) }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var createdUrl by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Share file",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isEncrypted) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "End-to-end encrypted files cannot be shared via public links. Decrypt & export the file first if you want to share it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Close")
                }
                return@Column
            }

            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (createdUrl != null) {
                val finalUrl = createdUrl!!
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Public share link ready",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = finalUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(fileName, finalUrl))
                            Toast.makeText(context, "Share link copied", Toast.LENGTH_SHORT).show()
                            onFeedback("Share link copied")
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .testTag("share_sheet_copy_button")
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy")
                    }

                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, fileName)
                                putExtra(Intent.EXTRA_TEXT, finalUrl)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Link"))
                            onDismiss()
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .testTag("share_sheet_send_button")
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Done")
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Link expiry",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SHARE_EXPIRY_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = selectedExpiryHours == option.hours,
                                onClick = { selectedExpiryHours = option.hours },
                                label = { Text(option.label) },
                                modifier = Modifier.testTag("share_expiry_${option.label.lowercase()}")
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = maxDownloadsText,
                        onValueChange = { input ->
                            maxDownloadsText = input.filter { it.isDigit() }.take(5)
                            errorMessage = null
                        },
                        label = { Text("Max downloads") },
                        placeholder = { Text("Unlimited") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_max_downloads_input")
                    )
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { input ->
                            pinText = input.take(16)
                            errorMessage = null
                        },
                        label = { Text("PIN lock (optional)") },
                        placeholder = { Text("e.g. 4829") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_pin_input")
                    )
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Allow download",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Turn off for view-only preview links",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = allowDownload,
                            onCheckedChange = { allowDownload = it },
                            modifier = Modifier.testTag("share_allow_download_switch")
                        )
                    }
                }

                Button(
                    onClick = {
                        if (accountId.isBlank() || fileId.isBlank()) {
                            errorMessage = "Select a valid drive and file first."
                            return@Button
                        }
                        val parsedMaxDownloads = maxDownloadsText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
                        if (maxDownloadsText.isNotBlank() && (parsedMaxDownloads == null || parsedMaxDownloads <= 0)) {
                            errorMessage = "Max downloads must be a positive number."
                            return@Button
                        }
                        val cleanPin = pinText.trim().takeIf { it.isNotEmpty() }
                        isSubmitting = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val resp = ApiClient.apiService.shareFile(
                                    ShareRequest(
                                        accountId = accountId,
                                        fileId = fileId,
                                        expiresInHours = selectedExpiryHours,
                                        maxDownloads = parsedMaxDownloads,
                                        pin = cleanPin,
                                        allowDownload = allowDownload
                                    )
                                )
                                val resolvedUrl = resp.effectiveUrl
                                if (resolvedUrl.isNotBlank()) {
                                    createdUrl = resolvedUrl
                                } else {
                                    errorMessage = resp.error?.takeIf { it.isNotBlank() }
                                        ?: "Server did not return a public share link."
                                }
                            } catch (e: HttpException) {
                                val rawErr = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                                errorMessage = ApiClient.parseError(rawErr, e.code())
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage ?: "Failed to create share link."
                            } finally {
                                isSubmitting = false
                            }
                        }
                    },
                    enabled = !isSubmitting,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("share_create_link_button")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Creating link...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Share Link", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSharesBottomSheet(
    onDismiss: () -> Unit,
    onFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var shares by remember { mutableStateOf<List<ActiveShareDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var revokingId by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun loadShares() {
        scope.launch {
            isLoading = true
            errorText = null
            try {
                shares = ApiClient.apiService.getShares()
            } catch (e: HttpException) {
                val rawErr = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                errorText = ApiClient.parseError(rawErr, e.code())
            } catch (e: Exception) {
                errorText = e.localizedMessage ?: "Could not load active share links"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadShares() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Active share links",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Copy or revoke public links you have created",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (errorText != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = errorText.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                shares.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active share links",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(shares, key = { it.effectiveShareId.ifBlank { it.hashCode().toString() } }) { share ->
                            val sid = share.effectiveShareId
                            val isBusy = revokingId == sid
                            val downloadsMeta = if (share.maxDownloads != null && share.maxDownloads > 0) {
                                "${share.effectiveDownloadCount}/${share.maxDownloads} downloads"
                            } else {
                                "${share.effectiveDownloadCount} downloads"
                            }
                            val expiryMeta = share.expiresAt?.take(16)?.replace('T', ' ')?.let { "Expires $it" } ?: "No expiry"

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = share.fileName?.takeIf { it.isNotBlank() } ?: "Shared file ($sid)",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (share.effectiveHasPin) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Lock,
                                                    contentDescription = "PIN protected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "$downloadsMeta · $expiryMeta",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!share.effectiveAllowDownload) {
                                            Text(
                                                text = "View-only",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    if (share.effectiveShareUrl.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Share Link", share.effectiveShareUrl))
                                                Toast.makeText(context, "Share link copied", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("copy_share_$sid")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ContentCopy,
                                                contentDescription = "Copy share link",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (isBusy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .padding(horizontal = 12.dp)
                                                .size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        IconButton(
                                            onClick = {
                                                if (sid.isBlank()) return@IconButton
                                                scope.launch {
                                                    revokingId = sid
                                                    errorText = null
                                                    try {
                                                        val resp = ApiClient.apiService.revokeShare(sid)
                                                        if (resp.isSuccessful || resp.code() == 404) {
                                                            shares = shares.filterNot { it.effectiveShareId == sid }
                                                            onFeedback("Share link revoked")
                                                        } else {
                                                            val rawErr = runCatching { resp.errorBody()?.string() }.getOrNull()
                                                            errorText = ApiClient.parseError(rawErr, resp.code())
                                                        }
                                                    } catch (e: Exception) {
                                                        errorText = e.localizedMessage ?: "Failed to revoke share link"
                                                    } finally {
                                                        revokingId = null
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("revoke_share_$sid")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = "Revoke share link",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

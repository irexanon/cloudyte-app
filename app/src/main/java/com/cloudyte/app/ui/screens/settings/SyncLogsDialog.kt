package com.cloudyte.app.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cloudyte.app.R
import com.cloudyte.app.sync.SyncLogLevel
import com.cloudyte.app.sync.SyncLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncLogsDialog(
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logs by SyncLogger.logsFlow.collectAsState()

    LaunchedEffect(Unit) {
        SyncLogger.init(context)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { SyncLogger.exportToUri(context, uri) }
            result.onSuccess { byteCount ->
                snackbarHostState.showSnackbar("Exported sync logs ($byteCount bytes)")
            }.onFailure { err ->
                snackbarHostState.showSnackbar("Failed to export sync logs: ${err.message ?: "Write error"}")
            }
        }
    }

    val scrollState = rememberScrollState()
    val logPreview = remember(logs) { SyncLogger.getExportText(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = "Sync & Transfer Logs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Sensitive tokens, passphrases, and query credentials are redacted automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (logs.isEmpty()) {
                            Text(
                                text = logPreview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            logs.takeLast(120).asReversed().forEach { entry ->
                                val tint = when (entry.level) {
                                    SyncLogLevel.ERROR -> MaterialTheme.colorScheme.error
                                    SyncLogLevel.WARNING -> MaterialTheme.colorScheme.tertiary
                                    SyncLogLevel.SUCCESS -> MaterialTheme.colorScheme.primary
                                    SyncLogLevel.INFO -> MaterialTheme.colorScheme.onSurface
                                }
                                Text(
                                    text = "${entry.timeFormatted} [${entry.level.name}] ${entry.message}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = tint
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Cloudyte Sync Logs", logPreview))
                            scope.launch { snackbarHostState.showSnackbar("Copied redacted logs to clipboard") }
                        },
                        modifier = Modifier.testTag("sync_logs_copy_button")
                    ) {
                        Text("Copy")
                    }
                    TextButton(
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            exportLauncher.launch("cloudyte_sync_logs_$stamp.txt")
                        },
                        modifier = Modifier.testTag("sync_logs_export_button")
                    ) {
                        Text("Export .txt")
                    }
                    TextButton(
                        onClick = {
                            SyncLogger.clearLogs()
                            scope.launch { snackbarHostState.showSnackbar("Cleared sync logs") }
                        },
                        modifier = Modifier.testTag("sync_logs_clear_button")
                    ) {
                        Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("sync_logs_close_button")) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

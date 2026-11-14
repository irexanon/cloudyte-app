package com.cloudyte.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cloudyte.app.CloudyteApplication
import com.cloudyte.app.ui.theme.MetricLabelTextStyle
import com.cloudyte.app.ui.theme.MetricTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val app = context.applicationContext as CloudyteApplication
    val diagnostics = app.container.transferDiagnostics
    val snapshot = diagnostics.snapshot()
    val report = remember(snapshot) { diagnostics.export(context, CURRENT_SCHEMA_VERSION) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(report)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy diagnostics")
                    }
                    IconButton(onClick = {
                        val intent = Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Cloudyte diagnostics")
                                putExtra(Intent.EXTRA_TEXT, report)
                            },
                            "Share diagnostics"
                        )
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share diagnostics")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 16.dp + com.cloudyte.app.ui.components.LocalBottomBarInset.current
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Diagnostics contain redacted state only. Credentials, raw file names, URLs, and file contents are excluded.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Command summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        SummaryRow("Started", snapshot.started.toString())
                        SummaryRow("Retried", snapshot.retried.toString())
                        SummaryRow("Paused", snapshot.paused.toString())
                        SummaryRow("Resumed", snapshot.resumed.toString())
                        SummaryRow("Cancelled", snapshot.cancelled.toString())
                        SummaryRow("Events retained", snapshot.events.size.toString())
                    }
                }
            }
            if (snapshot.events.isEmpty()) {
                item { Text("No transfer events recorded in this app session.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                item { Text("Recent events", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                items(snapshot.events.asReversed(), key = { it.timestamp.toString() + it.correlationId }) { event ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(event.operationType, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "state=${event.state ?: "-"}  transfer=${event.transferId ?: "-"}  retry=${event.retryCount}",
                                style = MetricLabelTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "correlation=${event.correlationId}  account=${event.accountHash ?: "-"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { clipboard.setText(AnnotatedString(report)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Copy full report")
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MetricTextStyle, fontWeight = FontWeight.Medium)
    }
}

private const val CURRENT_SCHEMA_VERSION = 15

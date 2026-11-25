package com.cloudyte.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cloudyte.app.api.ApiClient

@Composable
fun ServerUrlConfigDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val context = LocalContext.current
    var inputUrl by remember(currentUrl) { mutableStateOf(currentUrl) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = "Server URL",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Point Cloudyte to a custom backend domain or testing instance. Leave blank or reset to use the default server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = {
                        inputUrl = it
                        validationError = null
                    },
                    label = { Text("Server address") },
                    placeholder = { Text(ApiClient.DEFAULT_API_BASE_URL) },
                    singleLine = true,
                    isError = validationError != null,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_url_input")
                )
                if (validationError != null) {
                    Text(
                        text = validationError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            inputUrl = ApiClient.DEFAULT_API_BASE_URL
                            validationError = null
                        },
                        modifier = Modifier.testTag("server_url_reset_button")
                    ) {
                        Text("Reset default")
                    }
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Server URL", currentUrl))
                            Toast.makeText(context, "Server URL copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("server_url_copy_button")
                    ) {
                        Text("Copy current")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = ApiClient.setBaseUrl(context, inputUrl)
                    result.fold(
                        onSuccess = { normalized ->
                            onSaved(normalized)
                        },
                        onFailure = { err ->
                            validationError = err.message ?: "Invalid server URL"
                        }
                    )
                },
                shape = CircleShape,
                modifier = Modifier.testTag("server_url_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

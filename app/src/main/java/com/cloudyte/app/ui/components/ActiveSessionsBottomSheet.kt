package com.cloudyte.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cloudyte.app.api.ActiveSessionDto
import com.cloudyte.app.api.ApiClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionsBottomSheet(
    onDismiss: () -> Unit,
    onFeedback: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    var sessions by remember { mutableStateOf<List<ActiveSessionDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var revokingId by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun loadSessions() {
        coroutineScope.launch {
            isLoading = true
            errorText = null
            try {
                sessions = ApiClient.apiService.getSessions()
            } catch (e: Exception) {
                errorText = e.localizedMessage ?: "Could not load active sessions"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadSessions()
    }

    val otherSessionsCount = remember(sessions) { sessions.count { !it.isCurrent } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Active devices & sessions",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Sessions automatically expire after 30 days of inactivity",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (otherSessionsCount > 0 && !isLoading) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            try {
                                val resp = ApiClient.apiService.revokeOtherSessions()
                                if (resp.isSuccessful) {
                                    sessions = sessions.filter { it.isCurrent }
                                    onFeedback("Signed out of $otherSessionsCount other device(s)")
                                } else {
                                    errorText = "Failed to revoke other sessions (HTTP ${resp.code()})"
                                }
                            } catch (e: Exception) {
                                errorText = e.localizedMessage ?: "Failed to revoke other sessions"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("revoke_other_sessions_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign out of $otherSessionsCount other device(s)")
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
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                sessions.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active sessions found",
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
                        items(sessions, key = { it.effectiveId }) { session ->
                            val isMobile = session.deviceName.contains("android", ignoreCase = true) ||
                                session.deviceName.contains("ios", ignoreCase = true) ||
                                session.userAgent?.contains("okhttp", ignoreCase = true) == true
                            val isBusy = revokingId == session.effectiveId

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
                                    Icon(
                                        imageVector = if (isMobile) Icons.Outlined.Smartphone else Icons.Outlined.Laptop,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = session.deviceName.ifBlank { "Cloudyte Client" },
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (session.isCurrent) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = "This device",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        val lastSeen = session.effectiveLastSeenRaw?.take(16)?.replace('T', ' ')
                                            ?: "Active now"
                                        val ipSummary = buildString {
                                            val primaryIp = session.ip?.takeIf { it.isNotBlank() }
                                                ?: session.ipHistory?.firstOrNull { it.isNotBlank() }
                                            if (!primaryIp.isNullOrBlank()) {
                                                append("IP: ").append(primaryIp)
                                                val extraIps = (session.ipHistory?.distinct()?.size ?: 0) - 1
                                                if (extraIps > 0) {
                                                    append(" (+$extraIps)")
                                                }
                                            }
                                        }
                                        Text(
                                            text = "Last active: $lastSeen",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (ipSummary.isNotBlank()) {
                                            Text(
                                                text = ipSummary,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (!session.isCurrent) {
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
                                                    coroutineScope.launch {
                                                        revokingId = session.effectiveId
                                                        try {
                                                            val resp = ApiClient.apiService.revokeSession(session.effectiveId)
                                                            if (resp.isSuccessful) {
                                                                sessions = sessions.filterNot { it.effectiveId == session.effectiveId }
                                                                onFeedback("Session revoked")
                                                            } else {
                                                                errorText = "Could not revoke session (HTTP ${resp.code()})"
                                                            }
                                                        } catch (e: Exception) {
                                                            errorText = e.localizedMessage ?: "Could not revoke session"
                                                        } finally {
                                                            revokingId = null
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("revoke_session_${session.effectiveId}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                                                    contentDescription = "Revoke session",
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
}

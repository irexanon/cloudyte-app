package com.cloudyte.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cloudyte.app.R
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.TgDriveRegisterRequest
import com.cloudyte.app.ui.components.AppLoadingIndicator
import com.cloudyte.app.ui.theme.cloudyteColors
import kotlinx.coroutines.launch

enum class CloudProviderType(val displayName: String, val subtitle: String) {
    GOOGLE_DRIVE("Google Drive", "Connect via Google OAuth"),
    ONEDRIVE("OneDrive", "Connect via Microsoft OAuth"),
    TELEGRAM("Telegram Cloud", "Unlimited storage via Telegram Channel")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountDialog(
    initialProvider: CloudProviderType = CloudProviderType.GOOGLE_DRIVE,
    showChooserFirst: Boolean = true,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val ext = MaterialTheme.cloudyteColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isChooserStep by remember(initialProvider, showChooserFirst) { mutableStateOf(showChooserFirst) }
    var selectedProvider by remember(initialProvider) { mutableStateOf(initialProvider) }

    // Telegram inputs
    var channelId by remember { mutableStateOf("") }
    var driveName by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var oauthWaitingForReturn by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { if (!isLoading) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(ext.dragHandle)
            )
        }
    ) {
        AnimatedContent(
            targetState = isChooserStep,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "AddDriveStepTransition",
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) { chooser ->
            if (chooser) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.add_drive_title),
                            fontSize = 30.sp,
                            lineHeight = 33.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.03).em,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.add_drive_subtitle),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ProviderChoiceRow(
                            title = stringResource(R.string.provider_telegram_title),
                            subtitle = stringResource(R.string.provider_telegram_desc),
                            icon = Icons.AutoMirrored.Filled.Send,
                            iconTint = ext.providerTelegram,
                            badgeBg = ext.providerTelegramBg,
                            badgeShape = RoundedCornerShape(20.dp),
                            cardShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                            onClick = {
                                selectedProvider = CloudProviderType.TELEGRAM
                                errorMsg = null
                                oauthWaitingForReturn = false
                                isChooserStep = false
                            }
                        )
                        ProviderChoiceRow(
                            title = stringResource(R.string.provider_google_title),
                            subtitle = stringResource(R.string.provider_google_desc),
                            icon = Icons.Default.Cloud,
                            iconTint = ext.providerGoogle,
                            badgeBg = ext.providerGoogleBg,
                            badgeShape = CircleShape,
                            cardShape = RoundedCornerShape(8.dp),
                            onClick = {
                                selectedProvider = CloudProviderType.GOOGLE_DRIVE
                                errorMsg = null
                                oauthWaitingForReturn = false
                                isChooserStep = false
                            }
                        )
                        ProviderChoiceRow(
                            title = stringResource(R.string.provider_onedrive_title),
                            subtitle = stringResource(R.string.provider_onedrive_desc),
                            icon = Icons.Default.CloudQueue,
                            iconTint = ext.providerOneDrive,
                            badgeBg = ext.providerOneDriveBg,
                            badgeShape = RoundedCornerShape(20.dp),
                            cardShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp),
                            onClick = {
                                selectedProvider = CloudProviderType.ONEDRIVE
                                errorMsg = null
                                oauthWaitingForReturn = false
                                isChooserStep = false
                            }
                        )
                    }

                    TextButton(
                        onClick = onDismiss,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            color = ext.lightAccent,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !isLoading) {
                                    errorMsg = null
                                    oauthWaitingForReturn = false
                                    isChooserStep = true
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedProvider.displayName,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.02).em,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = selectedProvider.subtitle,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (errorMsg != null) {
                        Surface(
                            color = ext.errorDeepBox,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMsg!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    when (selectedProvider) {
                        CloudProviderType.GOOGLE_DRIVE -> {
                            GoogleDriveLinkPanel(
                                isLoading = isLoading,
                                oauthWaiting = oauthWaitingForReturn,
                                onConnectClick = {
                                    isLoading = true
                                    errorMsg = null
                                    coroutineScope.launch {
                                        try {
                                            val resp = ApiClient.apiService.getGoogleDriveLink()
                                            val url = resp.url
                                            val parsed = url?.let { runCatching { Uri.parse(it) }.getOrNull() }
                                            if (parsed != null && parsed.scheme.equals("https", ignoreCase = true) && !parsed.host.isNullOrBlank()) {
                                                val intent = Intent(Intent.ACTION_VIEW, parsed).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                try {
                                                    context.startActivity(intent)
                                                    oauthWaitingForReturn = true
                                                } catch (_: android.content.ActivityNotFoundException) {
                                                    errorMsg = "No browser app found to open Google sign-in."
                                                }
                                            } else {
                                                errorMsg = "Server did not return a valid HTTPS Google authorization URL."
                                            }
                                        } catch (e: Exception) {
                                            Log.e("AddAccountDialog", "Google link error", e)
                                            errorMsg = e.localizedMessage ?: "Failed to initiate Google Drive linking."
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                onVerifyCompleted = onSuccess
                            )
                        }
                        CloudProviderType.ONEDRIVE -> {
                            OneDriveLinkPanel(
                                isLoading = isLoading,
                                oauthWaiting = oauthWaitingForReturn,
                                onConnectClick = {
                                    isLoading = true
                                    errorMsg = null
                                    coroutineScope.launch {
                                        try {
                                            val resp = ApiClient.apiService.getOneDriveLink()
                                            val url = resp.url
                                            val parsed = url?.let { runCatching { Uri.parse(it) }.getOrNull() }
                                            if (parsed != null && parsed.scheme.equals("https", ignoreCase = true) && !parsed.host.isNullOrBlank()) {
                                                val intent = Intent(Intent.ACTION_VIEW, parsed).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                try {
                                                    context.startActivity(intent)
                                                    oauthWaitingForReturn = true
                                                } catch (_: android.content.ActivityNotFoundException) {
                                                    errorMsg = "No browser app found to open OneDrive sign-in."
                                                }
                                            } else {
                                                errorMsg = "Server did not return a valid HTTPS OneDrive authorization URL."
                                            }
                                        } catch (e: Exception) {
                                            Log.e("AddAccountDialog", "OneDrive link error", e)
                                            errorMsg = e.localizedMessage ?: "Failed to initiate OneDrive linking."
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                onVerifyCompleted = onSuccess
                            )
                        }
                        CloudProviderType.TELEGRAM -> {
                            TelegramLinkPanel(
                                driveName = driveName,
                                onDriveNameChange = { driveName = it },
                                channelId = channelId,
                                onChannelIdChange = { channelId = it },
                                isLoading = isLoading,
                                onSubmit = {
                                    if (channelId.isBlank()) {
                                        errorMsg = "Channel ID is required (e.g. -1001987654321)"
                                        return@TelegramLinkPanel
                                    }
                                    isLoading = true
                                    errorMsg = null
                                    coroutineScope.launch {
                                        try {
                                            val response = ApiClient.apiService.registerTgDrive(
                                                TgDriveRegisterRequest(channelId.trim(), driveName.trim())
                                            )
                                            if (response.isSuccessful) {
                                                onSuccess()
                                            } else {
                                                val rawErr = runCatching { response.errorBody()?.string() }.getOrNull()
                                                errorMsg = ApiClient.parseError(rawErr, response.code())
                                            }
                                        } catch (e: Exception) {
                                            Log.e("AddAccountDialog", "TG register error", e)
                                            errorMsg = e.localizedMessage ?: "Network error"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            )
                        }
                    }

                    TextButton(
                        onClick = onDismiss,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            color = ext.lightAccent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderChoiceRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    badgeBg: Color,
    badgeShape: Shape,
    cardShape: Shape,
    onClick: () -> Unit
) {
    Surface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clip(cardShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(badgeShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun GoogleDriveLinkPanel(
    isLoading: Boolean,
    oauthWaiting: Boolean,
    onConnectClick: () -> Unit,
    onVerifyCompleted: () -> Unit
) {
    val ext = MaterialTheme.cloudyteColors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(ext.providerGoogleBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Cloud, contentDescription = "Google Drive", tint = ext.providerGoogle, modifier = Modifier.size(32.dp))
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Securely connect your personal or workspace Google Drive account via OAuth 2.0.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        if (oauthWaiting) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Browser window opened. Once you approve permissions, tap 'Done' below to refresh your linked drives.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onVerifyCompleted,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Done / Refresh Drives",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            Button(
                onClick = onConnectClick,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = ext.providerGoogle, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    AppLoadingIndicator(size = 20.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Opening Google...", color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect Google Drive", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun OneDriveLinkPanel(
    isLoading: Boolean,
    oauthWaiting: Boolean,
    onConnectClick: () -> Unit,
    onVerifyCompleted: () -> Unit
) {
    val ext = MaterialTheme.cloudyteColors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(ext.providerOneDriveBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CloudQueue, contentDescription = "OneDrive", tint = ext.providerOneDrive, modifier = Modifier.size(32.dp))
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Access Microsoft OneDrive and Office 365 cloud files seamlessly with Microsoft Graph.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        if (oauthWaiting) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Browser window opened. Once you approve permissions, tap 'Done' below to refresh your linked drives.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onVerifyCompleted,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Done / Refresh Drives",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            Button(
                onClick = onConnectClick,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = ext.providerOneDrive, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    AppLoadingIndicator(size = 20.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Opening Microsoft...", color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect OneDrive", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TelegramLinkPanel(
    driveName: String,
    onDriveNameChange: (String) -> Unit,
    channelId: String,
    onChannelIdChange: (String) -> Unit,
    isLoading: Boolean,
    onSubmit: () -> Unit
) {
    val ext = MaterialTheme.cloudyteColors
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = ext.providerTelegram, modifier = Modifier.size(20.dp))
                Text(
                    text = "Ensure the Cloudyte Bot is added as an Administrator with upload permissions in your Telegram channel.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        OutlinedTextField(
            value = driveName,
            onValueChange = onDriveNameChange,
            label = { Text("Drive Name (Optional)") },
            placeholder = { Text("e.g. My Personal Cloud") },
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedBorderColor = ext.providerTelegram,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = channelId,
            onValueChange = onChannelIdChange,
            label = { Text("Channel ID *") },
            placeholder = { Text("e.g. -1001987654321") },
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedBorderColor = ext.providerTelegram,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onSubmit,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = ext.providerTelegram, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                AppLoadingIndicator(size = 20.dp, color = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Registering...", color = Color.White, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Register Telegram Drive", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

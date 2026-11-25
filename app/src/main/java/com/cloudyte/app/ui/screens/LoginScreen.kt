package com.cloudyte.app.ui.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cloudyte.app.R
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.LoginRequest
import com.cloudyte.app.api.RegisterRequest
import com.cloudyte.app.ui.components.AmbientCornerSquircle
import com.cloudyte.app.ui.components.AppLoadingIndicator
import com.cloudyte.app.ui.components.CloudyteLogoMark
import com.cloudyte.app.ui.components.CloudyteWordmark
import com.cloudyte.app.ui.components.ServerUrlConfigDialog
import com.cloudyte.app.ui.theme.cloudyteColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isRegisterMode = MutableStateFlow(false)
    val isRegisterMode: StateFlow<Boolean> = _isRegisterMode.asStateFlow()

    fun toggleMode() {
        _isRegisterMode.value = !_isRegisterMode.value
        _error.value = null
    }

    fun clearError() {
        _error.value = null
    }

    fun authenticate(username: String, password: String, onSuccess: () -> Unit) {
        val cleanUsername = username.trim()
        val cleanPassword = password.trim()

        if (cleanUsername.isEmpty() || cleanPassword.isEmpty()) {
            _error.value = "Please enter both username and password."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                ApiClient.saveToken(null)

                if (_isRegisterMode.value) {
                    val regResponse = ApiClient.apiService.register(RegisterRequest(cleanUsername, cleanPassword))
                    if (regResponse.isSuccessful) {
                        val loginResponse = ApiClient.apiService.login(LoginRequest(cleanUsername, cleanPassword))
                        if (loginResponse.isSuccessful) {
                            val token = loginResponse.body()?.token
                            if (!token.isNullOrEmpty()) {
                                ApiClient.saveToken(token)
                                onSuccess()
                                return@launch
                            }
                        }
                        _isRegisterMode.value = false
                        _error.value = "Registration successful! Please log in with your credentials."
                    } else {
                        val errBody = regResponse.errorBody()?.string()
                        _error.value = ApiClient.parseError(errBody, regResponse.code(), isRegister = true)
                    }
                } else {
                    val response = ApiClient.apiService.login(LoginRequest(cleanUsername, cleanPassword))
                    if (response.isSuccessful) {
                        val body = response.body()
                        val token = body?.token
                        if (!token.isNullOrEmpty()) {
                            ApiClient.saveToken(token)
                            onSuccess()
                        } else if (body?.error != null || body?.message != null) {
                            _error.value = body.error ?: body.message
                        } else {
                            _error.value = "Login failed: No authentication token received."
                        }
                    } else {
                        val errBody = response.errorBody()?.string()
                        _error.value = ApiClient.parseError(errBody, response.code(), isRegister = false)
                    }
                }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Auth error", e)
                val msg = e.localizedMessage
                if (msg != null && (msg.contains("401") || msg.contains("Unauthorized"))) {
                    _error.value = "Invalid username or password. If you don't have an account, tap Register below."
                } else {
                    _error.value = e.localizedMessage ?: "Network connection error. Please try again."
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
}

@Composable
fun LoginScreen(navController: NavController, viewModel: LoginViewModel = viewModel()) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }

    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isRegisterMode by viewModel.isRegisterMode.collectAsState()
    val currentServerUrl by ApiClient.baseUrlFlow.collectAsState()
    var showServerDialog by remember { mutableStateOf(false) }
    val ext = MaterialTheme.cloudyteColors

    if (showServerDialog) {
        ServerUrlConfigDialog(
            currentUrl = currentServerUrl,
            onDismiss = { showServerDialog = false },
            onSaved = {
                showServerDialog = false
                viewModel.clearError()
            }
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            AmbientCornerSquircle(
                size = 240.dp,
                cornerRadius = 72.dp,
                rotationDegrees = 24f,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 96.dp, y = (-72).dp)
            )

            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .align(Alignment.Center)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Brand Lockup
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    CloudyteLogoMark(size = 48.dp, showDots = true)
                    CloudyteWordmark(fontSize = 26.sp)
                }

                Spacer(modifier = Modifier.height(28.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isRegisterMode) {
                            stringResource(R.string.register_title)
                        } else {
                            stringResource(R.string.login_title)
                        },
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isRegisterMode) {
                            stringResource(R.string.register_subtitle)
                        } else {
                            stringResource(R.string.login_subtitle)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    ExpressiveLoginField(
                        label = stringResource(R.string.login_username_label),
                        value = username,
                        onValueChange = {
                            username = it
                            viewModel.clearError()
                        },
                        leadingIcon = Icons.Default.Person,
                        imeAction = ImeAction.Next,
                        modifier = Modifier.testTag("login_username_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    ExpressiveLoginField(
                        label = stringResource(R.string.login_password_label),
                        value = password,
                        onValueChange = {
                            password = it
                            viewModel.clearError()
                        },
                        leadingIcon = Icons.Default.Lock,
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        onTogglePassword = { passwordVisible = !passwordVisible },
                        imeAction = ImeAction.Done,
                        onDone = {
                            if (username.isNotBlank() && password.isNotBlank() && !isLoading) {
                                viewModel.authenticate(username, password) {
                                    navController.navigate("main") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag("login_password_input")
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Remember Me row with M3 Expressive Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rememberMe = !rememberMe }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.login_remember_me),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            thumbContent = if (rememberMe) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedIconColor = MaterialTheme.colorScheme.primary,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }

                    if (error != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = error!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(26.dp))

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AppLoadingIndicator(
                                size = 44.dp,
                                color = ext.accentBlue
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.authenticate(username, password) {
                                    navController.navigate("main") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .testTag("login_submit_button"),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            ),
                            enabled = username.isNotBlank() && password.isNotBlank()
                        ) {
                            Text(
                                text = if (isRegisterMode) {
                                    stringResource(R.string.register_cta)
                                } else {
                                    stringResource(R.string.login_cta)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isRegisterMode) {
                                stringResource(R.string.login_have_account)
                            } else {
                                stringResource(R.string.login_no_account)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { viewModel.toggleMode() }) {
                            Text(
                                text = if (isRegisterMode) {
                                    stringResource(R.string.login_cta)
                                } else {
                                    stringResource(R.string.login_register_action)
                                },
                                color = ext.lightAccent,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    TextButton(
                        onClick = { showServerDialog = true },
                        modifier = Modifier.testTag("login_server_url_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Server: ${currentServerUrl.removePrefix("https://").removePrefix("http://")}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveLoginField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val ext = MaterialTheme.cloudyteColors

    val borderStroke = if (isFocused) {
        BorderStroke(2.dp, ext.accentBlue)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = borderStroke,
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (isFocused) ext.lightAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFocused) ext.lightAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    interactionSource = interactionSource,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    ),
                    cursorBrush = SolidColor(ext.accentBlue),
                    visualTransformation = if (isPassword && !passwordVisible) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(imeAction = imeAction),
                    keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text(
                                    text = if (isPassword) "••••••••" else "Enter $label",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
            if (isPassword && onTogglePassword != null) {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle password visibility",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

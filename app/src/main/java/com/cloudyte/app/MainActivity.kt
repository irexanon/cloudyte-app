package com.cloudyte.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.core.transfer.TransferCommand
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.UserPreferencesState
import com.cloudyte.app.db.TransferDirection
import com.cloudyte.app.navigation.AppNavHost
import com.cloudyte.app.ui.theme.CloudyteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    private val pendingShareIntentState = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val startupReadyState = MutableStateFlow(false)
        val initialPrefsState = MutableStateFlow(UserPreferencesState())
        splashScreen.setKeepOnScreenCondition { !startupReadyState.value }

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                UserPreferences.migrateIfNeeded(this@MainActivity)
                val loaded = UserPreferences.observe(this@MainActivity).first()
                initialPrefsState.value = loaded
            }
            runCatching { ApiClient.awaitInitialized() }
            startupReadyState.value = true
        }

        if (savedInstanceState == null && isShareIntent(intent)) {
            pendingShareIntentState.value = Intent(intent)
            intent?.action = null
        }

        enableEdgeToEdge()
        setContent {
            val startupReady by startupReadyState.collectAsStateWithLifecycle()
            val initialPrefs by initialPrefsState.collectAsStateWithLifecycle()
            val prefs by UserPreferences.observe(this).collectAsStateWithLifecycle(initialValue = initialPrefs)
            val pendingShareIntent by pendingShareIntentState.collectAsStateWithLifecycle()

            LaunchedEffect(prefs.blockScreenshots) {
                if (prefs.blockScreenshots) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            LaunchedEffect(prefs.debugLogging) {
                ApiClient.setDebugLogging(prefs.debugLogging)
            }

            LaunchedEffect(startupReady, prefs.colorTheme) {
                if (startupReady) {
                    com.cloudyte.app.ui.theme.AppIconManager.syncWithColorTheme(
                        this@MainActivity,
                        prefs.colorTheme
                    )
                }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            var lastBackgroundMs by remember { mutableLongStateOf(0L) }
            var isLocked by remember { mutableStateOf(initialPrefs.appLockEnabled) }

            LaunchedEffect(startupReady, initialPrefs.appLockEnabled) {
                if (startupReady && initialPrefs.appLockEnabled && lastBackgroundMs == 0L) {
                    isLocked = true
                }
            }

            LaunchedEffect(prefs.appLockEnabled) {
                if (!prefs.appLockEnabled) {
                    isLocked = false
                }
            }

            LaunchedEffect(pendingShareIntent, isLocked, prefs.appLockEnabled, startupReady) {
                if (!startupReady) return@LaunchedEffect
                val share = pendingShareIntent ?: return@LaunchedEffect
                if (isLocked && prefs.appLockEnabled) return@LaunchedEffect
                pendingShareIntentState.value = null
                ApiClient.awaitInitialized()
                if (ApiClient.getToken().isNullOrBlank()) {
                    Toast.makeText(
                        this@MainActivity,
                        "Sign in to Cloudyte before uploading shared files",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    handleIncomingShareIntent(share)
                }
            }

            DisposableEffect(lifecycleOwner, prefs.appLockEnabled, prefs.autoLockMinutes) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            lastBackgroundMs = System.currentTimeMillis()
                        }
                        Lifecycle.Event.ON_START -> {
                            if (prefs.appLockEnabled && lastBackgroundMs > 0L) {
                                val elapsedMs = System.currentTimeMillis() - lastBackgroundMs
                                val timeoutMs = prefs.autoLockMinutes * 60_000L
                                if (elapsedMs >= timeoutMs) {
                                    isLocked = true
                                }
                            }
                        }
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val credentialLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    isLocked = false
                }
            }

            CloudyteTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColor,
                colorTheme = prefs.colorTheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isLocked && prefs.appLockEnabled) {
                        AppLockScreen(
                            onUnlockRequest = {
                                val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                                @Suppress("DEPRECATION")
                                val confirmIntent = km?.createConfirmDeviceCredentialIntent(
                                    getString(R.string.lock_screen_title),
                                    getString(R.string.lock_screen_subtitle)
                                )
                                if (confirmIntent != null) {
                                    credentialLauncher.launch(confirmIntent)
                                } else if (km?.isDeviceSecure == true) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Unable to open device unlock prompt",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    isLocked = false
                                }
                            }
                        )
                    } else {
                        AppNavHost()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isShareIntent(intent)) {
            pendingShareIntentState.value = Intent(intent)
            intent.action = null
        }
    }

    private fun isShareIntent(incoming: Intent?): Boolean {
        val action = incoming?.action ?: return false
        return action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE
    }

    private fun handleIncomingShareIntent(incoming: Intent?) {
        if (incoming == null) return
        val action = incoming.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return

        val uris = buildList<Uri> {
            when (action) {
                Intent.ACTION_SEND -> {
                    val single = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        incoming.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    if (single != null) add(single)
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    val multiple = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        incoming.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        incoming.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    }
                    if (!multiple.isNullOrEmpty()) addAll(multiple.filterNotNull())
                }
            }
            val clip = incoming.clipData
            if (isEmpty() && clip != null) {
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i)?.uri?.let { add(it) }
                }
            }
        }.distinct()

        if (uris.isEmpty()) return

        lifecycleScope.launch {
            val app = applicationContext as? CloudyteApplication ?: return@launch
            val drivePrefs = getSharedPreferences("cloudyte_drive_state", Context.MODE_PRIVATE)
            val savedAccId = drivePrefs.getString("last_active_account_id", null)
            val targetAccId = withContext(Dispatchers.IO) {
                val cached = app.container.database.accountDao().getAllAccounts()
                savedAccId?.takeIf { id -> cached.any { it.id == id } }
                    ?: cached.find { it.isDefault == true }?.id
                    ?: cached.firstOrNull()?.id
            }
            if (targetAccId.isNullOrBlank()) {
                Toast.makeText(this@MainActivity, "Connect a drive in Cloudyte before sharing files", Toast.LENGTH_LONG).show()
                return@launch
            }
            val encryptUploads = runCatching {
                UserPreferences.observe(this@MainActivity).first().isUploadEncryptionEnabled
            }.getOrDefault(false)

            var queuedCount = 0
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    var name = "shared_${System.currentTimeMillis()}"
                    var size = 0L
                    runCatching {
                        contentResolver.query(
                            uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                                    cursor.getString(nameIdx)?.let { name = it }
                                }
                                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                                    size = cursor.getLong(sizeIdx)
                                }
                            }
                        }
                    }
                    val mimeType = runCatching { contentResolver.getType(uri) }.getOrNull()
                    val transferId = app.container.transferRepository.enqueueTransfer(
                        accountId = targetAccId,
                        fileName = name,
                        localPath = uri.toString(),
                        remotePath = null,
                        direction = TransferDirection.UPLOAD,
                        totalBytes = size,
                        isEncrypted = encryptUploads,
                        originalName = if (encryptUploads) name else null,
                        originalMimeType = if (encryptUploads) mimeType else null,
                        originalSize = if (encryptUploads) size else null
                    )
                    app.container.transferCoordinator.dispatch(TransferCommand.Start(transferId))
                    queuedCount++
                }
            }
            if (queuedCount > 0) {
                Toast.makeText(
                    this@MainActivity,
                    "Uploading $queuedCount shared file(s) to Cloudyte",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

@Composable
private fun AppLockScreen(onUnlockRequest: () -> Unit) {
    com.cloudyte.app.ui.screens.CloudyteAppLockContent(onUnlock = onUnlockRequest)
}

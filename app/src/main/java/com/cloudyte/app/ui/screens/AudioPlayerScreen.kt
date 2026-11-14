package com.cloudyte.app.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavController
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.FileRepository
import com.cloudyte.app.api.OfflineFileManager
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.UserPreferencesState
import com.cloudyte.app.features.player.AudioPlaybackService
import com.cloudyte.app.ui.theme.*
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun AudioPlayerScreen(
    navController: NavController,
    accountId: String,
    fileId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(fileId) { FileRepository.getFile(fileId) }
    val fileName = file?.effectiveName ?: "Audio Track"

    val effectiveAccId = remember(accountId, file?.effectiveAccountId) {
        when {
            file?.effectiveAccountId?.isNotBlank() == true -> file.effectiveAccountId
            accountId.isNotBlank() && accountId != "all" && accountId != "root" -> accountId
            else -> ""
        }
    }

    val audioUrl = remember(effectiveAccId, fileId) {
        val acc = effectiveAccId.ifBlank { accountId }
        ApiClient.getFileUrl(acc, fileId, preview = true)
    }

    val prefsState by UserPreferences.observe(context).collectAsState(initial = UserPreferencesState())
    val initialPrefs = remember {
        kotlinx.coroutines.runBlocking {
            runCatching { UserPreferences.observe(context).first() }.getOrDefault(UserPreferencesState())
        }
    }

    var activePlayer by remember { mutableStateOf<Player?>(null) }
    var isLocalFallbackPlayer by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var statusNote by remember { mutableStateOf<String?>(null) }
    var isLooping by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var attemptedDownloadFallback by remember(fileId) { mutableStateOf(false) }

    fun createLocalPlayer(): ExoPlayer {
        val token = ApiClient.getToken()
        val headers = mutableMapOf<String, String>()
        if (!token.isNullOrEmpty()) {
            headers["Authorization"] = "Bearer $token"
        }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }

    // Bind MediaController to AudioPlaybackService when backgroundPlayback is enabled;
    // otherwise use a screen-scoped ExoPlayer released on dispose.
    DisposableEffect(initialPrefs.backgroundPlayback) {
        var controllerFuture: ListenableFuture<MediaController>? = null
        var boundPlayer: Player? = null

        if (initialPrefs.backgroundPlayback) {
            try {
                val token = SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
                val future = MediaController.Builder(context, token).buildAsync()
                controllerFuture = future
                future.addListener({
                    try {
                        val controller = future.get()
                        boundPlayer = controller
                        activePlayer = controller
                        isLocalFallbackPlayer = false
                    } catch (_: Exception) {
                        val fallback = createLocalPlayer()
                        boundPlayer = fallback
                        activePlayer = fallback
                        isLocalFallbackPlayer = true
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (_: Exception) {
                val fallback = createLocalPlayer()
                boundPlayer = fallback
                activePlayer = fallback
                isLocalFallbackPlayer = true
            }
        } else {
            val local = createLocalPlayer()
            boundPlayer = local
            activePlayer = local
            isLocalFallbackPlayer = true
        }

        onDispose {
            val currentControllerFuture = controllerFuture
            val currentPlayer = boundPlayer
            if (currentControllerFuture != null && !isLocalFallbackPlayer) {
                if (!prefsState.backgroundPlayback) {
                    currentPlayer?.stop()
                    context.stopService(Intent(context, AudioPlaybackService::class.java))
                }
                MediaController.releaseFuture(currentControllerFuture)
            } else {
                currentPlayer?.release()
            }
            activePlayer = null
        }
    }

    // Load media item honoring streamBeforeDownload and offline/encryption requirements
    LaunchedEffect(activePlayer, fileId, effectiveAccId, file?.isEncrypted, initialPrefs.streamBeforeDownload) {
        val player = activePlayer ?: return@LaunchedEffect
        val acc = effectiveAccId.ifBlank { accountId }
        val metadata = MediaMetadata.Builder()
            .setTitle(fileName)
            .setArtist("Cloudyte")
            .build()

        val mediaUri: Uri? = if (file?.isEncrypted == true) {
            isBuffering = true
            statusNote = "Decrypting encrypted audio…"
            val decrypted = withContext(Dispatchers.IO) {
                OfflineFileManager.prepareDecryptedFileForViewer(context, acc, file).getOrNull()
            }
            statusNote = null
            decrypted?.takeIf { it.exists() && it.length() > 0 }?.let { Uri.fromFile(it) }
        } else {
            val existingOffline = withContext(Dispatchers.IO) {
                OfflineFileManager.findOfflineFile(context, acc, fileId)
            }
            if (existingOffline != null && existingOffline.exists() && existingOffline.length() > 0) {
                statusNote = "Playing from offline storage"
                Uri.fromFile(existingOffline)
            } else if (!initialPrefs.streamBeforeDownload && file != null && acc.isNotBlank()) {
                isBuffering = true
                statusNote = "Downloading before playback…"
                val downloaded = withContext(Dispatchers.IO) {
                    OfflineFileManager.downloadFile(context, acc, file).getOrNull()
                }
                if (downloaded != null && downloaded.exists() && downloaded.length() > 0) {
                    statusNote = "Downloaded locally"
                    Uri.fromFile(downloaded)
                } else {
                    statusNote = "Local download failed; streaming instead"
                    Uri.parse(audioUrl)
                }
            } else {
                statusNote = null
                if (acc.isNotBlank() && acc != "all" && acc != "root" && file != null) {
                    OfflineFileManager.autoCacheOnView(context, acc, file)
                }
                Uri.parse(audioUrl)
            }
        }

        if (mediaUri != null) {
            val item = MediaItem.Builder()
                .setMediaId(fileId)
                .setUri(mediaUri)
                .setMediaMetadata(metadata)
                .build()
            player.setMediaItem(item)
            player.prepare()
            player.playWhenReady = true
        } else {
            isBuffering = false
            statusNote = "Unable to prepare audio file"
        }
    }

    DisposableEffect(activePlayer) {
        val player = activePlayer ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!attemptedDownloadFallback && file != null && !file.isEncrypted) {
                    attemptedDownloadFallback = true
                    val acc = effectiveAccId.ifBlank { accountId }
                    if (acc.isNotBlank()) {
                        scope.launch {
                            statusNote = "Stream failed; downloading locally…"
                            val downloaded = withContext(Dispatchers.IO) {
                                OfflineFileManager.downloadFile(context, acc, file).getOrNull()
                            }
                            if (downloaded != null && downloaded.exists() && downloaded.length() > 0) {
                                statusNote = "Playing local copy"
                                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(downloaded)))
                                player.prepare()
                                player.playWhenReady = true
                            } else {
                                isBuffering = false
                                statusNote = error.localizedMessage ?: "Audio playback failed"
                            }
                        }
                    }
                } else {
                    isBuffering = false
                    statusNote = error.localizedMessage ?: "Audio playback failed"
                }
            }
        }
        player.addListener(listener)
        isPlaying = player.isPlaying
        duration = player.duration.coerceAtLeast(0L)
        currentPosition = player.currentPosition.coerceAtLeast(0L)

        onDispose {
            player.removeListener(listener)
        }
    }

    // Position tracker
    LaunchedEffect(activePlayer, isPlaying) {
        val player = activePlayer ?: return@LaunchedEffect
        while (isPlaying) {
            currentPosition = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "disc_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Playback Speed", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    speeds.forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable {
                                    playbackSpeed = speed
                                    activePlayer?.setPlaybackSpeed(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = playbackSpeed == speed,
                                onClick = {
                                    playbackSpeed = speed
                                    activePlayer?.setPlaybackSpeed(speed)
                                    showSpeedDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (speed == 1f) "Normal (1x)" else "${speed}x",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Audio Player",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (prefsState.backgroundPlayback) {
                            Text(
                                "Background playback active",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (file?.isEncrypted == true) {
                            android.widget.Toast.makeText(context, "Encrypted sharing is not supported", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, audioUrl)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Audio Link"))
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = {
                        if (file?.isEncrypted == true) {
                            navController.navigate("file_detail/$accountId/$fileId")
                        } else {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(audioUrl))
                            context.startActivity(intent)
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                    )
                    .rotate(if (isPlaying) rotation else 0f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = fileName,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${file?.formattedSize ?: ""} • ${file?.effectiveType?.uppercase() ?: "AUDIO"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MetricLabelTextStyle
                )
                if (!statusNote.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusNote.orEmpty(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                var isDragging by remember { mutableStateOf(false) }
                var dragPosition by remember { mutableFloatStateOf(0f) }

                val sliderValue = if (isDragging) dragPosition else {
                    if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                }

                Slider(
                    value = sliderValue,
                    onValueChange = {
                        isDragging = true
                        dragPosition = it
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        if (duration > 0) {
                            val seekTarget = (dragPosition * duration).toLong()
                            activePlayer?.seekTo(seekTarget)
                            currentPosition = seekTarget
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatAudioTime(currentPosition),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MetricLabelTextStyle
                    )
                    Text(
                        text = formatAudioTime(duration),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MetricLabelTextStyle
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        isLooping = !isLooping
                        activePlayer?.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    }
                ) {
                    Icon(
                        Icons.Default.Repeat,
                        contentDescription = "Loop",
                        tint = if (isLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        val player = activePlayer ?: return@IconButton
                        val newPos = (player.currentPosition - 10000L).coerceAtLeast(0L)
                        player.seekTo(newPos)
                        currentPosition = newPos
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(32.dp))
                }

                IconButton(
                    onClick = {
                        val player = activePlayer ?: return@IconButton
                        if (isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)),
                            CircleShape
                        )
                ) {
                    if (isBuffering) {
                        com.cloudyte.app.ui.components.AppLoadingIndicator(size = 32.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val player = activePlayer ?: return@IconButton
                        val maxDur = player.duration.coerceAtLeast(0L)
                        val newPos = (player.currentPosition + 10000L).let { if (maxDur > 0L) it.coerceAtMost(maxDur) else it }
                        player.seekTo(newPos)
                        currentPosition = newPos
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(32.dp))
                }

                IconButton(onClick = { showSpeedDialog = true }) {
                    Text(
                        text = if (playbackSpeed == 1f) "1x" else "${playbackSpeed}x",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MetricTextStyle,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun formatAudioTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        val remainingMinutes = minutes % 60
        String.format(Locale.US, "%d:%02d:%02d", hours, remainingMinutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

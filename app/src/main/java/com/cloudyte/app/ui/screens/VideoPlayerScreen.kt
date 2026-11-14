package com.cloudyte.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.api.FileRepository
import com.cloudyte.app.api.OfflineFileManager
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.UserPreferencesState
import com.cloudyte.app.ui.theme.MetricLabelTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    navController: NavController,
    accountId: String,
    fileId: String
) {
    val context = LocalContext.current
    var currentActiveFileId by remember { mutableStateOf(fileId) }

    var dbVideo by remember(currentActiveFileId) { mutableStateOf<CloudFile?>(null) }
    LaunchedEffect(currentActiveFileId) {
        val app = context.applicationContext as? com.cloudyte.app.CloudyteApplication
        if (app != null) {
            val local = app.container.database.cloudFileDao().getFileById(currentActiveFileId)?.toCloudFile()
            if (local != null) {
                FileRepository.cacheFiles(listOf(local))
                dbVideo = local
            }
        }
    }

    val videoList = remember(dbVideo) {
        val list = FileRepository.getActiveList().filter { it.isVideo }
        when {
            list.any { it.effectiveId == fileId } -> list
            dbVideo != null -> listOf(dbVideo!!)
            else -> {
                val f = FileRepository.getFile(fileId)
                if (f != null) listOf(f) else emptyList()
            }
        }
    }

    val currentVideoIndex = videoList.indexOfFirst { it.effectiveId == currentActiveFileId }.coerceAtLeast(0)
    val file = videoList.getOrNull(currentVideoIndex)
        ?: remember(currentActiveFileId) { FileRepository.getFile(currentActiveFileId) }
        ?: dbVideo
    val fileName = file?.effectiveName ?: "Video Player"

    val initialPrefs = remember {
        kotlinx.coroutines.runBlocking {
            runCatching { UserPreferences.observe(context).first() }.getOrDefault(UserPreferencesState())
        }
    }
    var isPlaying by remember { mutableStateOf(initialPrefs.autoPlayVideo) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var videoStatusNote by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var attemptedDownloadFallback by remember(currentActiveFileId) { mutableStateOf(false) }

    val effectiveAccId = remember(accountId, file?.effectiveAccountId) {
        when {
            file?.effectiveAccountId?.isNotBlank() == true -> file.effectiveAccountId
            accountId.isNotBlank() && accountId != "all" && accountId != "root" -> accountId
            else -> ""
        }
    }

    val exoPlayer = remember {
        val token = ApiClient.getToken()
        val headers = mutableMapOf<String, String>()
        if (!token.isNullOrEmpty()) {
            headers["Authorization"] = "Bearer $token"
        }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }

    // Update media source when switching videos, honoring streamBeforeDownload and autoPlayVideo
    LaunchedEffect(currentActiveFileId, effectiveAccId, file?.isEncrypted, initialPrefs.streamBeforeDownload, initialPrefs.autoPlayVideo) {
        val acc = effectiveAccId.ifBlank { accountId }
        val mediaItem = if (file?.isEncrypted == true) {
            isBuffering = true
            videoStatusNote = "Decrypting video…"
            val decrypted = withContext(Dispatchers.IO) {
                OfflineFileManager.prepareDecryptedFileForViewer(context, acc, file).getOrNull()
            }
            videoStatusNote = null
            if (decrypted != null && decrypted.exists() && decrypted.length() > 0) {
                MediaItem.fromUri(Uri.fromFile(decrypted))
            } else {
                isBuffering = false
                null
            }
        } else {
            val offlineFile = if (acc.isNotBlank()) {
                withContext(Dispatchers.IO) { OfflineFileManager.findOfflineFile(context, acc, currentActiveFileId) }
            } else null
            if (offlineFile != null && offlineFile.exists() && offlineFile.length() > 0) {
                videoStatusNote = null
                val prepared = withContext(Dispatchers.IO) {
                    if (file != null) OfflineFileManager.prepareDecryptedFileForViewer(context, acc, file).getOrNull() else offlineFile
                } ?: offlineFile
                MediaItem.fromUri(Uri.fromFile(prepared))
            } else if (!initialPrefs.streamBeforeDownload && file != null && acc.isNotBlank()) {
                isBuffering = true
                videoStatusNote = "Downloading before playback…"
                val downloaded = withContext(Dispatchers.IO) {
                    OfflineFileManager.downloadFile(context, acc, file).getOrNull()
                }
                videoStatusNote = null
                if (downloaded != null && downloaded.exists() && downloaded.length() > 0) {
                    MediaItem.fromUri(Uri.fromFile(downloaded))
                } else {
                    val url = ApiClient.getFileUrl(acc, currentActiveFileId, preview = true)
                    MediaItem.fromUri(url)
                }
            } else {
                videoStatusNote = null
                if (acc.isNotBlank() && file != null) {
                    OfflineFileManager.autoCacheOnView(context, acc, file)
                }
                val url = ApiClient.getFileUrl(acc, currentActiveFileId, preview = true)
                MediaItem.fromUri(url)
            }
        }
        if (mediaItem != null) {
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = initialPrefs.autoPlayVideo
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!attemptedDownloadFallback && file != null && !file.isEncrypted) {
                    attemptedDownloadFallback = true
                    val acc = effectiveAccId.ifBlank { accountId }
                    if (acc.isNotBlank()) {
                        coroutineScope.launch {
                            videoStatusNote = "Stream failed; downloading locally…"
                            val downloaded = withContext(Dispatchers.IO) {
                                OfflineFileManager.downloadFile(context, acc, file).getOrNull()
                            }
                            videoStatusNote = null
                            if (downloaded != null && downloaded.exists() && downloaded.length() > 0) {
                                exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(downloaded)))
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = initialPrefs.autoPlayVideo
                            } else {
                                isBuffering = false
                                videoStatusNote = error.localizedMessage ?: "Video playback failed"
                            }
                        }
                    }
                } else {
                    isBuffering = false
                    videoStatusNote = error.localizedMessage ?: "Video playback failed"
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Ticking position loop
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Auto-hide controls timer
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

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
                                    exoPlayer.setPlaybackSpeed(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = playbackSpeed == speed,
                                onClick = {
                                    playbackSpeed = speed
                                    exoPlayer.setPlaybackSpeed(speed)
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

    var totalDragX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(videoList, currentVideoIndex) {
                detectHorizontalDragGestures(
                    onDragStart = { totalDragX = 0f },
                    onDragEnd = {
                        if (totalDragX < -150f && currentVideoIndex < videoList.size - 1) {
                            currentActiveFileId = videoList[currentVideoIndex + 1].effectiveId
                        } else if (totalDragX > 150f && currentVideoIndex > 0) {
                            currentActiveFileId = videoList[currentVideoIndex - 1].effectiveId
                        }
                        totalDragX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDragX += dragAmount
                    }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    this.resizeMode = resizeMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering / status Indicator
        if (isBuffering || !videoStatusNote.isNullOrBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isBuffering) {
                        com.cloudyte.app.ui.components.AppLoadingIndicator(
                            size = 52.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!videoStatusNote.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = videoStatusNote.orEmpty(),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            val videoUrl = ApiClient.getFileUrl(accountId, currentActiveFileId, preview = true)

            Box(modifier = Modifier.fillMaxSize()) {
                // Top Header Gradient Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = fileName,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                if (file != null) {
                                    val countStr = if (videoList.size > 1) " • (${currentVideoIndex + 1}/${videoList.size})" else ""
                                    Text(
                                        text = "${file.formattedSize}$countStr",
                                        color = Color.White.copy(alpha = 0.72f),
                                        style = MetricLabelTextStyle
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { showSpeedDialog = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = "Speed", tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            ) {
                                Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                            }
                        }
                    }
                }

                // Center Play/Pause & Skip Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (videoList.size > 1) {
                        IconButton(
                            onClick = {
                                if (currentVideoIndex > 0) {
                                    currentActiveFileId = videoList[currentVideoIndex - 1].effectiveId
                                }
                            },
                            enabled = currentVideoIndex > 0,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = if (currentVideoIndex > 0) 0.6f else 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Video", tint = if (currentVideoIndex > 0) Color.White else Color.Gray)
                        }
                    }

                    IconButton(
                        onClick = {
                            val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(newPos)
                            currentPosition = newPos
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                    }

                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration)
                            exoPlayer.seekTo(newPos)
                            currentPosition = newPos
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                    }

                    if (videoList.size > 1) {
                        IconButton(
                            onClick = {
                                if (currentVideoIndex < videoList.size - 1) {
                                    currentActiveFileId = videoList[currentVideoIndex + 1].effectiveId
                                }
                            },
                            enabled = currentVideoIndex < videoList.size - 1,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = if (currentVideoIndex < videoList.size - 1) 0.6f else 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next Video", tint = if (currentVideoIndex < videoList.size - 1) Color.White else Color.Gray)
                        }
                    }
                }

                // Bottom Timeline & Scrubber Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
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
                                    exoPlayer.seekTo(seekTarget)
                                    currentPosition = seekTarget
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(currentPosition),
                                color = Color.White,
                                style = MetricLabelTextStyle,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatTime(duration),
                                color = Color.White.copy(alpha = 0.72f),
                                style = MetricLabelTextStyle,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, remainingMinutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}


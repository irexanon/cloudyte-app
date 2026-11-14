package com.cloudyte.app.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.cloudyte.app.CloudyteApplication
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.api.FileRepository
import com.cloudyte.app.api.OfflineFileManager
import com.cloudyte.app.core.cache.PreviewFetcher
import com.cloudyte.app.data.FileCryptoActions
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.UserPreferencesState
import com.cloudyte.app.features.player.AudioPlaybackService
import com.cloudyte.app.features.player.GDriveActionsBottomSheet
import com.cloudyte.app.features.player.GDriveDeleteConfirmDialog
import com.cloudyte.app.features.player.GDriveInfoBottomSheet
import com.cloudyte.app.features.player.GDriveRenameDialog
import com.cloudyte.app.features.player.performInAppDownload
import com.cloudyte.app.features.player.performShareFileLink
import com.cloudyte.app.ui.theme.MetricLabelTextStyle
import com.cloudyte.app.ui.theme.MetricTextStyle
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    val app = context.applicationContext as? CloudyteApplication

    var dbFile by remember(fileId) { mutableStateOf<CloudFile?>(null) }
    LaunchedEffect(fileId) {
        if (app != null) {
            val local = app.container.database.cloudFileDao().getFileById(fileId)?.toCloudFile()
            if (local != null) {
                FileRepository.cacheFiles(listOf(local))
                dbFile = local
            }
        }
    }
    val file = remember(fileId, dbFile) { FileRepository.getFile(fileId) ?: dbFile }
    val fileName = file?.effectiveName ?: "Audio Track"

    val effectiveAccId = file?.effectiveAccountId?.takeIf { it.isNotBlank() }
        ?: accountId.takeIf { it.isNotBlank() && it != "all" && it != "root" }.orEmpty()

    val audioUrl = remember(effectiveAccId, fileId) {
        val acc = effectiveAccId.ifBlank { accountId }
        ApiClient.getCleanDownloadUrl(acc, fileId, preview = true)
    }

    val prefsState by UserPreferences.observe(context).collectAsState(initial = UserPreferencesState())
    var resolvedPrefs by remember { mutableStateOf<UserPreferencesState?>(null) }
    LaunchedEffect(Unit) {
        resolvedPrefs = runCatching { UserPreferences.observe(context).first() }.getOrDefault(UserPreferencesState())
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
    var attemptedPreviewFallback by remember(fileId) { mutableStateOf(false) }

    var showActionsSheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoveCopyDialog by remember { mutableStateOf(false) }
    var isMoveAction by remember { mutableStateOf(true) }

    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMsg by remember { mutableStateOf<String?>(null) }
    val downloadedIds by OfflineFileManager.downloadedFiles.collectAsState()

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMsg = null
        }
    }

    fun createLocalPlayer(): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(com.cloudyte.app.features.player.MediaStreamCache.createMediaSourceFactory(context))
            .setLoadControl(com.cloudyte.app.features.player.MediaStreamCache.createFastLoadControl())
            .build()
    }

    val backgroundPlaybackSetting = resolvedPrefs?.backgroundPlayback
    DisposableEffect(backgroundPlaybackSetting) {
        if (backgroundPlaybackSetting == null) {
            return@DisposableEffect onDispose {}
        }
        var controllerFuture: ListenableFuture<MediaController>? = null
        var boundPlayer: Player? = null

        if (backgroundPlaybackSetting) {
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

    LaunchedEffect(activePlayer, fileId, effectiveAccId, file?.isEncrypted, resolvedPrefs) {
        val player = activePlayer ?: return@LaunchedEffect
        val prefs = resolvedPrefs ?: return@LaunchedEffect
        val streamBeforeDownload = prefs.streamBeforeDownload
        val acc = PreviewFetcher.resolveAccountId(context, effectiveAccId.ifBlank { accountId }, file)
        val metadata = MediaMetadata.Builder()
            .setTitle(fileName)
            .setArtist("Cloudyte")
            .build()

        val mediaUri: Uri? = if (file?.isEncrypted == true || !streamBeforeDownload) {
            isBuffering = true
            statusNote = if (file?.isEncrypted == true) "Decrypting audio…" else "Preparing audio preview…"
            val ready = file?.let {
                PreviewFetcher.resolvePreviewFile(context, acc, it).getOrNull()
            }
            statusNote = null
            when {
                ready != null && ready.isFile && ready.length() > 0L -> Uri.fromFile(ready)
                file?.isEncrypted == true -> null
                else -> Uri.parse(audioUrl)
            }
        } else {
            val cached = file?.let { PreviewFetcher.resolveCachedOnly(context, acc, it) }
            if (cached != null) {
                statusNote = null
                Uri.fromFile(cached)
            } else {
                statusNote = null
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
                if (!attemptedPreviewFallback && file != null && !file.isEncrypted) {
                    attemptedPreviewFallback = true
                    scope.launch {
                        val acc = PreviewFetcher.resolveAccountId(context, effectiveAccId.ifBlank { accountId }, file)
                        statusNote = "Buffering audio preview…"
                        val ready = PreviewFetcher.resolvePreviewFile(context, acc, file).getOrNull()
                        if (ready != null && ready.isFile && ready.length() > 0L) {
                            statusNote = null
                            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(ready)))
                            player.prepare()
                            player.playWhenReady = true
                        } else {
                            isBuffering = false
                            statusNote = error.localizedMessage ?: "Audio playback failed"
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

    if (showMoveCopyDialog && file != null) {
        MoveCopyDialog(
            file = file,
            sourceAccountId = effectiveAccId.ifBlank { accountId },
            isMove = isMoveAction,
            onDismiss = { showMoveCopyDialog = false },
            onSuccess = { msg ->
                showMoveCopyDialog = false
                snackbarMsg = msg
            }
        )
    }

    if (showInfoSheet && file != null) {
        GDriveInfoBottomSheet(
            file = file,
            accountId = effectiveAccId,
            isDownloadedOffline = fileId in downloadedIds,
            context = context,
            onDismiss = { showInfoSheet = false }
        )
    }

    if (showActionsSheet && file != null) {
        GDriveActionsBottomSheet(
            file = file,
            accountId = effectiveAccId,
            isDownloadedOffline = fileId in downloadedIds,
            onDismiss = { showActionsSheet = false },
            onShare = {
                scope.launch {
                    val err = performShareFileLink(context, effectiveAccId, file)
                    if (!err.isNullOrBlank()) snackbarMsg = err
                }
            },
            onDownload = {
                scope.launch {
                    snackbarMsg = performInAppDownload(context, effectiveAccId, file)
                }
            },
            onOpenDetails = {
                val navAcc = effectiveAccId.ifBlank { accountId }
                navController.navigate("file_detail/$navAcc/$fileId")
            },
            onRename = { showRenameDialog = true },
            onMove = {
                isMoveAction = true
                showMoveCopyDialog = true
            },
            onCopy = {
                isMoveAction = false
                showMoveCopyDialog = true
            },
            onEncryptCopy = if (!file.isEncrypted && app != null) {
                {
                    scope.launch {
                        snackbarMsg = FileCryptoActions.createEncryptedCopy(
                            context = context,
                            accountId = effectiveAccId,
                            targetFile = file,
                            fallbackParentFolderId = null,
                            cloudFileRepository = app.container.cloudFileRepository,
                            transferRepository = app.container.transferRepository,
                            transferCoordinator = app.container.transferCoordinator
                        )
                    }
                }
            } else null,
            onDelete = { showDeleteDialog = true }
        )
    }

    if (showRenameDialog && file != null && app != null) {
        GDriveRenameDialog(
            initialName = file.effectiveName,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                scope.launch {
                    val res = app.container.cloudFileRepository.rename(fileId, effectiveAccId.ifBlank { null }, newName)
                    snackbarMsg = if (res.isSuccess) "Renamed to $newName" else (res.exceptionOrNull()?.message ?: "Rename failed")
                }
            }
        )
    }

    if (showDeleteDialog && file != null && app != null) {
        GDriveDeleteConfirmDialog(
            fileName = file.effectiveName,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                scope.launch {
                    val res = app.container.cloudFileRepository.delete(fileId, effectiveAccId.ifBlank { null })
                    if (res.isSuccess) navController.popBackStack()
                    else snackbarMsg = res.exceptionOrNull()?.message ?: "Delete failed"
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = fileName,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (prefsState.backgroundPlayback) {
                            Text(
                                text = "Background playback active",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("viewer_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    if (file != null) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    snackbarMsg = performInAppDownload(context, effectiveAccId, file)
                                }
                            },
                            modifier = Modifier.testTag("viewer_download_button")
                        ) {
                            val saved = fileId in downloadedIds
                            Icon(
                                imageVector = if (saved) Icons.Default.DownloadDone else Icons.Default.Download,
                                contentDescription = if (saved) "Saved offline" else "Download",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(
                            onClick = { showInfoSheet = true },
                            modifier = Modifier.testTag("viewer_info_button")
                        ) {
                            Icon(Icons.Outlined.Info, contentDescription = "Details & Info", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        IconButton(
                            onClick = { showActionsSheet = true },
                            modifier = Modifier.testTag("viewer_more_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onBackground)
                        }
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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
                    },
                    modifier = Modifier.size(48.dp)
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
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .testTag("audio_play_pause_button")
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

                IconButton(
                    onClick = { showSpeedDialog = true },
                    modifier = Modifier.size(48.dp)
                ) {
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

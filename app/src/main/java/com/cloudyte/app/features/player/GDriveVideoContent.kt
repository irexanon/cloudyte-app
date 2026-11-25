package com.cloudyte.app.features.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.cloudyte.app.CloudyteApplication
import com.cloudyte.app.R
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.core.cache.PreviewFetcher
import com.cloudyte.app.core.network.isOnline
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.data.UserPreferencesState
import com.cloudyte.app.ui.components.FileThumb
import com.cloudyte.app.ui.theme.MetricLabelTextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun GDriveVideoContent(
    file: CloudFile,
    accountId: String,
    isActive: Boolean,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    hasPrevious: Boolean = false,
    hasNext: Boolean = false,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    bottomPaddingExtra: Boolean = false,
    isControlsLockedExternal: Boolean? = null,
    onControlsLockedChange: ((Boolean) -> Unit)? = null,
    onUserInteraction: (() -> Unit)? = null,
    onPlayingStateChange: ((Boolean) -> Unit)? = null,
    onScrubbingStateChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var statusNote by remember { mutableStateOf<String?>(null) }
    var playbackError by remember(file.effectiveId) { mutableStateOf<String?>(null) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var aspectModeToast by remember { mutableStateOf<String?>(null) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var isSpeedBoostActive by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var showRemainingTime by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var attemptedFallback by remember(file.effectiveId) { mutableStateOf(false) }
    var playingLocalFile by remember(file.effectiveId) { mutableStateOf(false) }
    var retryCounter by remember { mutableIntStateOf(0) }
    var autoPlayVideo by remember { mutableStateOf(true) }
    var internalLocked by remember { mutableStateOf(false) }
    val isControlsLocked = isControlsLockedExternal ?: internalLocked
    fun setLocked(locked: Boolean) {
        internalLocked = locked
        onControlsLockedChange?.invoke(locked)
    }

    var isScrubbing by remember { mutableStateOf(false) }
    var cumulativeSeekSec by remember { mutableIntStateOf(0) }
    var seekOverlayIsForward by remember { mutableStateOf(true) }
    var seekOverlayNonce by remember { mutableIntStateOf(0) }
    var resumedFromMs by remember(file.effectiveId) { mutableStateOf<Long?>(null) }
    var didApplyInitialResume by remember(file.effectiveId) { mutableStateOf(false) }
    var isFirstFrameRendered by remember(file.effectiveId) { mutableStateOf(false) }

    // Vertical swipe HUD states (Brightness on left half, Volume on right half)
    var activeVerticalGesture by remember { mutableStateOf<VerticalGestureType?>(null) }
    var gestureLevelFraction by remember { mutableFloatStateOf(0.5f) }
    var gestureHudNonce by remember { mutableIntStateOf(0) }

    val playbackPrefs = remember(context) {
        context.getSharedPreferences("cloudyte_video_playback", Context.MODE_PRIVATE)
    }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    LaunchedEffect(seekOverlayNonce) {
        if (cumulativeSeekSec != 0) {
            delay(750L)
            cumulativeSeekSec = 0
        }
    }

    LaunchedEffect(gestureHudNonce) {
        if (activeVerticalGesture != null) {
            delay(900L)
            activeVerticalGesture = null
        }
    }

    LaunchedEffect(aspectModeToast) {
        if (aspectModeToast != null) {
            delay(1100L)
            aspectModeToast = null
        }
    }

    LaunchedEffect(resumedFromMs) {
        if (resumedFromMs != null) {
            delay(5000L)
            resumedFromMs = null
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(MediaStreamCache.createMediaSourceFactory(context))
            .setLoadControl(MediaStreamCache.createFastLoadControl())
            .build()
    }

    // Pause immediately when swiped off page inside HorizontalPager; resume smoothly when active.
    LaunchedEffect(isActive) {
        if (!isActive) {
            if (exoPlayer.isPlaying) exoPlayer.pause()
            if (isSpeedBoostActive) {
                isSpeedBoostActive = false
                exoPlayer.setPlaybackSpeed(playbackSpeed)
            }
        } else if (autoPlayVideo && exoPlayer.playbackState == Player.STATE_READY) {
            exoPlayer.play()
        }
    }

    val localMediaSourceFactory = remember(context) {
        MediaStreamCache.createLocalFileMediaSourceFactory(context)
    }

    LaunchedEffect(file.effectiveId, accountId, file.isEncrypted, isActive, retryCounter) {
        if (!isActive) return@LaunchedEffect
        playbackError = null
        attemptedFallback = false
        playingLocalFile = false
        isBuffering = true
        isFirstFrameRendered = false
        val prefs = runCatching { UserPreferences.observe(context).first() }.getOrDefault(UserPreferencesState())
        autoPlayVideo = prefs.autoPlayVideo
        val acc = PreviewFetcher.resolveAccountId(context, accountId, file)
        val cachedLocal = PreviewFetcher.resolveCachedOnly(context, acc, file)
        if (cachedLocal != null && cachedLocal.isFile && cachedLocal.length() > 0L) {
            statusNote = null
            playingLocalFile = true
            val mediaSource = localMediaSourceFactory.createMediaSource(
                MediaItem.fromUri(Uri.fromFile(cachedLocal))
            )
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = isActive && prefs.autoPlayVideo
        } else {
            val isOnline = (context.applicationContext as? CloudyteApplication)
                ?.container?.networkMonitor?.refresh()?.isOnline() ?: true
            if (acc.isBlank()) {
                isBuffering = false
                playbackError = if (!isOnline) {
                    "Offline — download this video to watch without internet"
                } else {
                    "Drive account unavailable"
                }
            } else if (!isOnline) {
                isBuffering = false
                playbackError = "Offline — download this video to watch without internet"
            } else if (file.isEncrypted && prefs.streamBeforeDownload) {
                statusNote = null
                val cyteFactory = CyteStreamingDataSource.Factory(context, acc, file)
                val streamItem = MediaItem.fromUri(ApiClient.getFileUrl(acc, file.effectiveId, preview = true))
                val mediaSource = DefaultMediaSourceFactory(cyteFactory).createMediaSource(streamItem)
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = isActive && prefs.autoPlayVideo
            } else if (file.isEncrypted || !prefs.streamBeforeDownload) {
                statusNote = if (file.isEncrypted) "Decrypting video…" else "Preparing preview…"
                val ready = PreviewFetcher.resolvePreviewFile(context, acc, file).getOrNull()
                statusNote = null
                if (ready != null && ready.isFile && ready.length() > 0L) {
                    playingLocalFile = true
                    val mediaSource = localMediaSourceFactory.createMediaSource(
                        MediaItem.fromUri(Uri.fromFile(ready))
                    )
                    exoPlayer.setMediaSource(mediaSource)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = isActive && prefs.autoPlayVideo
                } else if (file.isEncrypted) {
                    isBuffering = false
                    playbackError = "Unable to prepare encrypted video"
                } else {
                    val streamUrl = ApiClient.resolveSignedMediaUrl(acc, file.effectiveId, preview = true)
                    exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = isActive && prefs.autoPlayVideo
                }
            } else {
                statusNote = null
                val streamUrl = ApiClient.resolveSignedMediaUrl(acc, file.effectiveId, preview = true)
                exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = isActive && prefs.autoPlayVideo
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (isActive) onPlayingStateChange?.invoke(playing)
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    val totalDur = exoPlayer.duration.coerceAtLeast(0L)
                    duration = totalDur
                    bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                    if (!didApplyInitialResume && file.effectiveId.isNotBlank() && totalDur > 10_000L) {
                        didApplyInitialResume = true
                        val savedPos = playbackPrefs.getLong("pos_${file.effectiveId}", 0L)
                        if (savedPos in 3_000L..(totalDur - 5_000L)) {
                            exoPlayer.seekTo(savedPos)
                            currentPosition = savedPos
                            resumedFromMs = savedPos
                        }
                    }
                } else if (state == Player.STATE_ENDED && file.effectiveId.isNotBlank()) {
                    playbackPrefs.edit().remove("pos_${file.effectiveId}").apply()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val isOnline = (context.applicationContext as? CloudyteApplication)
                    ?.container?.networkMonitor?.current()?.isOnline() ?: true
                if (!attemptedFallback && !playingLocalFile) {
                    attemptedFallback = true
                    scope.launch {
                        val acc = PreviewFetcher.resolveAccountId(context, accountId, file)
                        val cachedOnly = PreviewFetcher.resolveCachedOnly(context, acc, file)
                        if (cachedOnly != null && cachedOnly.isFile && cachedOnly.length() > 0L) {
                            playingLocalFile = true
                            val mediaSource = localMediaSourceFactory.createMediaSource(
                                MediaItem.fromUri(Uri.fromFile(cachedOnly))
                            )
                            exoPlayer.setMediaSource(mediaSource)
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = isActive && autoPlayVideo
                            return@launch
                        }
                        if (!isOnline || acc.isBlank()) {
                            isBuffering = false
                            playbackError = "Offline — download this video to watch without internet"
                            return@launch
                        }
                        statusNote = if (file.isEncrypted) "Decrypting local preview…" else "Buffering local preview…"
                        val localFile = PreviewFetcher.resolvePreviewFile(context, acc, file).getOrNull()
                        statusNote = null
                        if (localFile != null && localFile.isFile && localFile.length() > 0L) {
                            playingLocalFile = true
                            val mediaSource = localMediaSourceFactory.createMediaSource(
                                MediaItem.fromUri(Uri.fromFile(localFile))
                            )
                            exoPlayer.setMediaSource(mediaSource)
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = isActive && autoPlayVideo
                        } else {
                            isBuffering = false
                            playbackError = error.localizedMessage ?: "Video playback failed"
                        }
                    }
                } else {
                    isBuffering = false
                    playbackError = when {
                        playingLocalFile -> "Unable to play local video (${error.errorCodeName})"
                        !isOnline -> "Offline — download this video to watch without internet"
                        else -> error.localizedMessage ?: "Video playback failed"
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            if (file.effectiveId.isNotBlank()) {
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration
                if (dur > 10_000L && pos in 3_000L..(dur - 5_000L)) {
                    playbackPrefs.edit().putLong("pos_${file.effectiveId}", pos).apply()
                } else if (dur > 0L && pos >= dur - 5_000L) {
                    playbackPrefs.edit().remove("pos_${file.effectiveId}").apply()
                }
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Smooth 20Hz scrubber & buffer updates while playing (zero stutter vs old 400ms jump)
    LaunchedEffect(isPlaying, isActive) {
        var tick = 0
        while (isActive && isPlaying) {
            if (!isScrubbing) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            if (dur > 0L) duration = dur
            tick++
            if (tick % 40 == 0 && file.effectiveId.isNotBlank() && duration > 10_000L && currentPosition > 3_000L) {
                playbackPrefs.edit().putLong("pos_${file.effectiveId}", currentPosition).apply()
            }
            delay(50L)
        }
    }

    if (showSpeedDialog) {
        VideoSpeedPickerDialog(
            currentSpeed = playbackSpeed,
            onSelectSpeed = { speed ->
                playbackSpeed = speed
                exoPlayer.setPlaybackSpeed(speed)
                showSpeedDialog = false
                onUserInteraction?.invoke()
            },
            onDismiss = { showSpeedDialog = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ViewerDarkBackdrop)
            // Vertical drag on left half = Brightness, right half = Volume.
            // Horizontal drags pass straight through to HorizontalPager!
            .pointerInput(isControlsLocked) {
                if (isControlsLocked) return@pointerInput
                var gestureSide: VerticalGestureType? = null
                var startLevel = 0.5f
                var accumulatedDragY = 0f

                detectVerticalDragGestures(
                    onDragStart = { startOffset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        gestureSide = if (startOffset.x < width * 0.5f) {
                            VerticalGestureType.BRIGHTNESS
                        } else {
                            VerticalGestureType.VOLUME
                        }
                        accumulatedDragY = 0f
                        startLevel = when (gestureSide) {
                            VerticalGestureType.BRIGHTNESS -> {
                                val winBright = context.findHostActivity()?.window?.attributes?.screenBrightness ?: -1f
                                if (winBright in 0f..1f) {
                                    winBright
                                } else {
                                    val sys = runCatching {
                                        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                                    }.getOrDefault(128)
                                    (sys / 255f).coerceIn(0.05f, 1f)
                                }
                            }
                            VerticalGestureType.VOLUME -> {
                                val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
                                val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                                (curVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
                            }
                            null -> 0.5f
                        }
                        activeVerticalGesture = gestureSide
                        gestureLevelFraction = startLevel
                        gestureHudNonce++
                    },
                    onVerticalDrag = { change, dragAmount ->
                        val side = gestureSide ?: return@detectVerticalDragGestures
                        change.consume()
                        accumulatedDragY += dragAmount
                        val sensitivityPx = (size.height.toFloat() * 0.6f).coerceAtLeast(240f)
                        val newLevel = (startLevel - accumulatedDragY / sensitivityPx).coerceIn(0f, 1f)
                        gestureLevelFraction = newLevel
                        activeVerticalGesture = side
                        gestureHudNonce++

                        when (side) {
                            VerticalGestureType.BRIGHTNESS -> {
                                context.findHostActivity()?.window?.let { win ->
                                    val lp = win.attributes
                                    lp.screenBrightness = newLevel.coerceIn(0.02f, 1f)
                                    win.attributes = lp
                                }
                            }
                            VerticalGestureType.VOLUME -> {
                                audioManager?.let { am ->
                                    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    val targetVol = (newLevel * maxVol).roundToInt().coerceIn(0, maxVol)
                                    runCatching {
                                        am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                    }
                                    isMuted = targetVol == 0
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        gestureSide = null
                        gestureHudNonce++
                    },
                    onDragCancel = {
                        gestureSide = null
                        gestureHudNonce++
                    }
                )
            }
            // Tap, cumulative double-tap seek, and hold-for-2x-speed boost
            .pointerInput(isControlsLocked, playbackSpeed) {
                var consumedByBoost = false
                detectTapGestures(
                    onPress = {
                        consumedByBoost = false
                        if (!isControlsLocked) {
                            val boostJob = scope.launch {
                                delay(380L)
                                consumedByBoost = true
                                isSpeedBoostActive = true
                                exoPlayer.setPlaybackSpeed(2f)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            tryAwaitRelease()
                            boostJob.cancel()
                            if (isSpeedBoostActive) {
                                isSpeedBoostActive = false
                                exoPlayer.setPlaybackSpeed(playbackSpeed)
                            }
                        }
                    },
                    onTap = {
                        if (!consumedByBoost) {
                            onToggleControls()
                            onUserInteraction?.invoke()
                        }
                    },
                    onDoubleTap = { offset ->
                        if (isControlsLocked) return@detectTapGestures
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val fraction = offset.x / width
                        when {
                            fraction < 0.36f -> {
                                val newPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(newPos)
                                currentPosition = newPos
                                cumulativeSeekSec = if (!seekOverlayIsForward && cumulativeSeekSec < 0) {
                                    cumulativeSeekSec - 10
                                } else {
                                    -10
                                }
                                seekOverlayIsForward = false
                                seekOverlayNonce++
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUserInteraction?.invoke()
                            }
                            fraction > 0.64f -> {
                                val maxPos = exoPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                                val newPos = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxPos)
                                exoPlayer.seekTo(newPos)
                                currentPosition = newPos
                                cumulativeSeekSec = if (seekOverlayIsForward && cumulativeSeekSec > 0) {
                                    cumulativeSeekSec + 10
                                } else {
                                    10
                                }
                                seekOverlayIsForward = true
                                seekOverlayNonce++
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUserInteraction?.invoke()
                            }
                            else -> {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                onUserInteraction?.invoke()
                            }
                        }
                    }
                )
            }
    ) {
        // TextureView-backed PlayerView eliminates SurfaceView flicker/lag inside HorizontalPager!
        AndroidView(
            factory = { ctx ->
                val inflated = LayoutInflater.from(ctx).inflate(R.layout.view_texture_player, null) as PlayerView
                inflated.apply {
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

        // Smooth thumbnail poster backdrop until ExoPlayer renders the first video frame
        AnimatedVisibility(
            visible = !isFirstFrameRendered && playbackError == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                FileThumb(
                    file = file,
                    accountId = accountId,
                    sizeDp = 320.dp,
                    cornerRadius = 0.dp,
                    showTypeBadgeInGrid = false,
                    showEncryptedBadge = false,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                )
            }
        }

        // Top HUD: 2x Hold-to-Speed-Boost pill
        SpeedBoostHud(
            visible = isSpeedBoostActive,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp)
        )

        // Top-Center HUD: Brightness / Volume vertical drag pill
        GestureLevelHud(
            visible = activeVerticalGesture != null,
            type = activeVerticalGesture ?: VerticalGestureType.VOLUME,
            levelFraction = gestureLevelFraction,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 72.dp)
        )

        // Aspect Ratio mode subtle toast
        AnimatedVisibility(
            visible = aspectModeToast != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 120.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.78f),
                shape = CircleShape
            ) {
                Text(
                    text = aspectModeToast.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        if (playbackError != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = playbackError ?: "Playback error",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        attemptedFallback = false
                        retryCounter++
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry")
                }
            }
        } else if (isBuffering || !statusNote.isNullOrBlank()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(40.dp)
                )
                if (!statusNote.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = statusNote.orEmpty(),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Cumulative double-tap seek indicator
        val seekLabel = when {
            cumulativeSeekSec > 0 -> "+${cumulativeSeekSec}s"
            cumulativeSeekSec < 0 -> "${cumulativeSeekSec}s"
            else -> ""
        }
        DoubleTapSeekHud(
            visible = cumulativeSeekSec != 0,
            text = seekLabel,
            isForward = seekOverlayIsForward,
            modifier = Modifier
                .align(if (seekOverlayIsForward) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 36.dp)
        )

        // Locked unlock chip
        AnimatedVisibility(
            visible = showControls && isControlsLocked && playbackError == null,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.80f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        setLocked(false)
                        onUserInteraction?.invoke()
                    }
                    .testTag("video_unlock_controls_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Controls locked • Tap to unlock",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Main Cinema Controls Overlay
        AnimatedVisibility(
            visible = showControls && !isControlsLocked && playbackError == null,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.20f))
            ) {
                // Center playback cluster (frosted cinema circles)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (onPrevious != null) {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPrevious()
                                onUserInteraction?.invoke()
                            },
                            enabled = hasPrevious,
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    Color.Black.copy(alpha = if (hasPrevious) 0.56f else 0.22f),
                                    CircleShape
                                )
                                .testTag("video_prev_button")
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous video",
                                tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.35f)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val newPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(newPos)
                            currentPosition = newPos
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onUserInteraction?.invoke()
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color.Black.copy(alpha = 0.58f), CircleShape)
                            .testTag("video_rewind_button")
                    ) {
                        Icon(
                            Icons.Default.Replay10,
                            contentDescription = "Rewind 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            onUserInteraction?.invoke()
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.Black.copy(alpha = 0.72f), CircleShape)
                            .testTag("video_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val maxPos = exoPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                            val newPos = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxPos)
                            exoPlayer.seekTo(newPos)
                            currentPosition = newPos
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onUserInteraction?.invoke()
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color.Black.copy(alpha = 0.58f), CircleShape)
                            .testTag("video_forward_button")
                    ) {
                        Icon(
                            Icons.Default.Forward10,
                            contentDescription = "Forward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    if (onNext != null) {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNext()
                                onUserInteraction?.invoke()
                            },
                            enabled = hasNext,
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    Color.Black.copy(alpha = if (hasNext) 0.56f else 0.22f),
                                    CircleShape
                                )
                                .testTag("video_next_button")
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next video",
                                tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f)
                            )
                        }
                    }
                }

                // Bottom gradient dock: Resume chip + 60fps Scrubber + Action bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Black.copy(alpha = 0.90f)
                                )
                            )
                        )
                        .navigationBarsPadding()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = if (bottomPaddingExtra) 44.dp else 10.dp
                        )
                ) {
                    resumedFromMs?.let { resumedMs ->
                        Surface(
                            color = Color.Black.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Resumed from ${formatCinemaVideoTime(resumedMs)}",
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = "Start over",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        exoPlayer.seekTo(0L)
                                        currentPosition = 0L
                                        resumedFromMs = null
                                        onUserInteraction?.invoke()
                                    }
                                )
                            }
                        }
                    }

                    VideoScrubberBar(
                        currentPositionMs = currentPosition,
                        bufferedPositionMs = bufferedPosition,
                        durationMs = duration,
                        onScrubStart = {
                            isScrubbing = true
                            onScrubbingStateChange?.invoke(true)
                            onUserInteraction?.invoke()
                        },
                        onScrubChange = { previewMs ->
                            currentPosition = previewMs
                            onUserInteraction?.invoke()
                        },
                        onScrubFinished = { targetMs ->
                            isScrubbing = false
                            onScrubbingStateChange?.invoke(false)
                            exoPlayer.seekTo(targetMs)
                            currentPosition = targetMs
                            onUserInteraction?.invoke()
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val timeText = if (showRemainingTime && duration > 0L) {
                            "-${formatCinemaVideoTime((duration - currentPosition).coerceAtLeast(0L))} / ${formatCinemaVideoTime(duration)}"
                        } else {
                            "${formatCinemaVideoTime(currentPosition)} / ${formatCinemaVideoTime(duration)}"
                        }
                        Text(
                            text = timeText,
                            color = Color.White.copy(alpha = 0.92f),
                            style = MetricLabelTextStyle,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable {
                                    showRemainingTime = !showRemainingTime
                                    onUserInteraction?.invoke()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    setLocked(true)
                                    onUserInteraction?.invoke()
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("video_lock_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = "Lock controls",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                    exoPlayer.volume = if (isMuted) 0f else 1f
                                    onUserInteraction?.invoke()
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            Surface(
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .heightIn(min = 34.dp)
                                    .clickable {
                                        showSpeedDialog = true
                                        onUserInteraction?.invoke()
                                    }
                                    .testTag("video_speed_button")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = if (playbackSpeed == 1f) "1x" else "${playbackSpeed}x",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
                            ) {
                                IconButton(
                                    onClick = {
                                        context.findHostActivity()?.let { activity ->
                                            runCatching {
                                                val params = PictureInPictureParams.Builder()
                                                    .setAspectRatio(Rational(16, 9))
                                                    .build()
                                                activity.enterPictureInPictureMode(params)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("video_pip_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureInPictureAlt,
                                        contentDescription = "Picture-in-Picture",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    aspectModeToast = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Fill Screen"
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
                                        else -> "Fit to Screen"
                                    }
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUserInteraction?.invoke()
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("video_aspect_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Cycle aspect ratio",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    context.findHostActivity()?.let { act ->
                                        act.requestedOrientation = if (isLandscape) {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        }
                                    }
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUserInteraction?.invoke()
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("video_rotate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScreenRotation,
                                    contentDescription = if (isLandscape) "Switch to portrait" else "Switch to landscape",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

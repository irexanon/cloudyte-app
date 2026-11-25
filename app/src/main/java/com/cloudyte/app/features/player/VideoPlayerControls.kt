package com.cloudyte.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cloudyte.app.ui.theme.MetricLabelTextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Cinema-grade custom video scrubber with buffered track, spring-animated thumb & track height,
 * tap-to-seek, drag scrubbing, and floating time preview bubble above the user's finger.
 */
@Composable
fun VideoScrubberBar(
    currentPositionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    onScrubStart: () -> Unit,
    onScrubChange: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }

    val playedFraction = if (isScrubbing) {
        scrubFraction
    } else if (durationMs > 0L) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val bufferedFraction = if (durationMs > 0L) {
        (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val trackHeight by animateDpAsState(
        targetValue = if (isScrubbing) 6.5.dp else 3.5.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "scrubber_track_height"
    )
    val thumbRadius by animateDpAsState(
        targetValue = if (isScrubbing) 9.dp else 6.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "scrubber_thumb_radius"
    )

    val accentColor = MaterialTheme.colorScheme.primary
    val previewTimeMs = (scrubFraction * durationMs).toLong().coerceIn(0L, durationMs.coerceAtLeast(0L))

    Column(modifier = modifier.fillMaxWidth()) {
        // Floating time preview pill above finger while dragging
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
        ) {
            val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val pillWidthPx = with(density) { 80.dp.toPx() }
            val rawX = (playedFraction * maxW - pillWidthPx / 2f)
                .coerceIn(0f, (maxW - pillWidthPx).coerceAtLeast(0f))

            androidx.compose.animation.AnimatedVisibility(
                visible = isScrubbing && durationMs > 0L,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier.offset { IntOffset(rawX.roundToInt(), 0) }
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.86f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.width(80.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = formatCinemaVideoTime(previewTimeMs),
                            color = Color.White,
                            style = MetricLabelTextStyle,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Touch-friendly 40dp hit area housing the sleek custom Canvas bar
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .testTag("video_scrubber_bar")
                .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(durationMs) {
                    detectTapGestures { tapOffset ->
                        if (durationMs <= 0L) return@detectTapGestures
                        val fraction = (tapOffset.x / barWidthPx).coerceIn(0f, 1f)
                        val targetMs = (fraction * durationMs).toLong()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onScrubFinished(targetMs)
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { startOffset ->
                            if (durationMs <= 0L) return@detectHorizontalDragGestures
                            isScrubbing = true
                            scrubFraction = (startOffset.x / barWidthPx).coerceIn(0f, 1f)
                            onScrubStart()
                            onScrubChange((scrubFraction * durationMs).toLong())
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onHorizontalDrag = { change, _ ->
                            if (!isScrubbing || durationMs <= 0L) return@detectHorizontalDragGestures
                            change.consume()
                            scrubFraction = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                            onScrubChange((scrubFraction * durationMs).toLong())
                        },
                        onDragEnd = {
                            if (!isScrubbing) return@detectHorizontalDragGestures
                            isScrubbing = false
                            val targetMs = (scrubFraction * durationMs).toLong().coerceIn(0L, durationMs)
                            onScrubFinished(targetMs)
                        },
                        onDragCancel = {
                            if (!isScrubbing) return@detectHorizontalDragGestures
                            isScrubbing = false
                            val targetMs = (scrubFraction * durationMs).toLong().coerceIn(0L, durationMs)
                            onScrubFinished(targetMs)
                        }
                    )
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                val trackH = trackHeight.toPx()
                val centerY = size.height / 2f
                val corner = CornerRadius(trackH / 2f, trackH / 2f)

                // Base inactive track
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.24f),
                    topLeft = Offset(0f, centerY - trackH / 2f),
                    size = Size(size.width, trackH),
                    cornerRadius = corner
                )

                // Buffered progress track
                if (bufferedFraction > 0f) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.48f),
                        topLeft = Offset(0f, centerY - trackH / 2f),
                        size = Size((size.width * bufferedFraction).coerceIn(0f, size.width), trackH),
                        cornerRadius = corner
                    )
                }

                // Played progress track
                val playedWidth = (size.width * playedFraction).coerceIn(0f, size.width)
                if (playedWidth > 0f) {
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset(0f, centerY - trackH / 2f),
                        size = Size(playedWidth, trackH),
                        cornerRadius = corner
                    )
                }

                // Soft outer glow when scrubbing
                val radiusPx = thumbRadius.toPx()
                val thumbX = playedWidth.coerceIn(radiusPx, (size.width - radiusPx).coerceAtLeast(radiusPx))
                if (isScrubbing) {
                    drawCircle(
                        color = accentColor.copy(alpha = 0.34f),
                        radius = radiusPx * 1.9f,
                        center = Offset(thumbX, centerY)
                    )
                }
                // Crisp thumb
                drawCircle(
                    color = Color.White,
                    radius = radiusPx,
                    center = Offset(thumbX, centerY)
                )
            }
        }
    }
}

enum class VerticalGestureType {
    BRIGHTNESS,
    VOLUME
}

/**
 * Glassmorphic pill HUD displayed during vertical swipe gestures for Brightness or Volume.
 */
@Composable
fun GestureLevelHud(
    visible: Boolean,
    type: VerticalGestureType,
    levelFraction: Float,
    modifier: Modifier = Modifier
) {
    val animatedLevel by animateFloatAsState(
        targetValue = levelFraction.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "gesture_hud_level"
    )
    val percent = (animatedLevel * 100f).roundToInt().coerceIn(0, 100)
    val icon = when (type) {
        VerticalGestureType.BRIGHTNESS -> when {
            percent < 30 -> Icons.Default.BrightnessLow
            percent < 70 -> Icons.Default.BrightnessMedium
            else -> Icons.Default.BrightnessHigh
        }
        VerticalGestureType.VOLUME -> if (percent == 0) {
            Icons.AutoMirrored.Filled.VolumeOff
        } else {
            Icons.AutoMirrored.Filled.VolumeUp
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = modifier
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.78f),
            shape = RoundedCornerShape(22.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Box(
                    modifier = Modifier
                        .width(108.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.24f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedLevel.coerceIn(0f, 1f))
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Text(
                    text = "$percent%",
                    color = Color.White,
                    style = MetricLabelTextStyle,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Floating "2x >>" pill shown while holding down for 2x playback speed boost.
 */
@Composable
fun SpeedBoostHud(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.88f),
        exit = fadeOut() + scaleOut(targetScale = 0.88f),
        modifier = modifier
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.82f),
            shape = CircleShape
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "2x",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "2x speed boost active",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Cumulative double-tap seek indicator ("-10s", "+20s", etc.).
 */
@Composable
fun DoubleTapSeekHud(
    visible: Boolean,
    text: String,
    isForward: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.82f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = modifier
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.78f),
            shape = CircleShape
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isForward) Icons.Default.Forward10 else Icons.Default.Replay10,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = text,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Playback speed selector dialog.
 */
@Composable
fun VideoSpeedPickerDialog(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ViewerSheetContainer,
        titleContentColor = Color.White,
        title = { Text("Playback speed", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                speeds.forEach { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onSelectSpeed(speed) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSpeed == speed,
                            onClick = { onSelectSpeed(speed) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = Color.White.copy(alpha = 0.6f)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (speed == 1f) "Normal (1x)" else "${speed}x",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {}
    )
}

fun formatCinemaVideoTime(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = totalSeconds / 3600L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

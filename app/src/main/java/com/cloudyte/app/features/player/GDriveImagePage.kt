package com.cloudyte.app.features.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.cloudyte.app.CloudyteApplication
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.core.cache.PreviewFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GDriveImagePage(
    file: CloudFile,
    accountId: String,
    isCurrentPage: Boolean,
    resetZoomSignal: Int,
    onScaleChanged: (Float) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as? CloudyteApplication

    var reloadTrigger by remember { mutableIntStateOf(0) }
    var resolvedAccId by remember(accountId, file.effectiveId) {
        mutableStateOf(
            file.effectiveAccountId.ifBlank {
                accountId.takeIf { it.isNotBlank() && it != "all" && it != "root" }.orEmpty()
            }
        )
    }

    var localPreviewFile by remember(file.effectiveId, resolvedAccId, reloadTrigger) { mutableStateOf<File?>(null) }
    var encryptedError by remember(file.effectiveId, reloadTrigger) { mutableStateOf<String?>(null) }
    var encryptedProgress by remember(file.effectiveId, reloadTrigger) { mutableFloatStateOf(0f) }
    var thumbnailPlaceholderFile by remember(file.effectiveId, resolvedAccId) { mutableStateOf<File?>(null) }

    val revision = remember(file.updatedAt, file.time, file.sizeBytes) {
        file.updatedAt?.toString() ?: file.time?.toString() ?: file.sizeBytes.toString()
    }

    LaunchedEffect(file.effectiveId, accountId, reloadTrigger) {
        val acc = PreviewFetcher.resolveAccountId(context, accountId, file)
        resolvedAccId = acc
        if (acc.isBlank()) return@LaunchedEffect

        // 1. Instant 0ms lookup for any already-cached thumbnail (list/grid size) without network I/O.
        if (app != null && !file.isEncrypted) {
            val anyCachedThumb = app.container.thumbnailStore.findAnyCached(acc, file)
            if (anyCachedThumb != null) {
                thumbnailPlaceholderFile = anyCachedThumb
            }
        }

        // 2. Check offline/preview cache immediately without touching TransferDao.
        val cached = PreviewFetcher.resolveCachedOnly(context, acc, file)
        if (cached != null) {
            localPreviewFile = cached
            return@LaunchedEffect
        }

        // 3. Encrypted files must be streamed and decrypted ephemerally via PreviewFetcher (never TransferDao).
        if (file.isEncrypted) {
            encryptedError = null
            encryptedProgress = 0f
            val result = PreviewFetcher.resolvePreviewFile(context, acc, file) { p ->
                encryptedProgress = p
            }
            result.fold(
                onSuccess = { ready -> localPreviewFile = ready },
                onFailure = { err -> encryptedError = err.message ?: "Unable to decrypt preview" }
            )
        } else if (app != null) {
            // 4. Fetch fast 512px preview (~40 KB) so user sees crisp image quickly while full-res loads.
            val providerType = withContext(Dispatchers.IO) {
                app.container.database.accountDao().getAccountById(acc)?.accountType ?: "tgdrive"
            }
            val thumbPath = withContext(Dispatchers.IO) {
                app.container.thumbnailStore.resolve(
                    accountId = acc,
                    providerType = providerType,
                    file = file,
                    sizePx = 512,
                    localSource = null
                )
            }
            if (!thumbPath.isNullOrBlank() && thumbPath.startsWith("/")) {
                val tf = File(thumbPath)
                if (tf.isFile && tf.length() > 0L) {
                    thumbnailPlaceholderFile = tf
                }
            }
        }
    }

    val previewUrl = remember(file.isEncrypted, resolvedAccId, file.effectiveId) {
        if (!file.isEncrypted && resolvedAccId.isNotBlank() && file.effectiveId.isNotBlank()) {
            ApiClient.getCleanDownloadUrl(resolvedAccId, file.effectiveId, preview = true)
        } else {
            ""
        }
    }

    val cacheKey = remember(resolvedAccId, file.effectiveId, revision) {
        "img_preview_${resolvedAccId}_${file.effectiveId}_$revision"
    }

    val imageModel: Any? = remember(file.isEncrypted, localPreviewFile, previewUrl) {
        when {
            localPreviewFile != null && localPreviewFile!!.isFile && localPreviewFile!!.length() > 0L -> localPreviewFile
            !file.isEncrypted && previewUrl.isNotBlank() -> previewUrl
            else -> null
        }
    }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val scaleAnim = remember { Animatable(1f) }

    fun clampOffset(candidate: Offset, targetScale: Float): Offset {
        if (targetScale <= 1.01f || viewportSize.width == 0 || viewportSize.height == 0) {
            return Offset.Zero
        }
        val maxX = ((viewportSize.width * (targetScale - 1f)) / 2f).coerceAtLeast(0f)
        val maxY = ((viewportSize.height * (targetScale - 1f)) / 2f).coerceAtLeast(0f)
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY)
        )
    }

    LaunchedEffect(isCurrentPage, resetZoomSignal) {
        if (!isCurrentPage || resetZoomSignal > 0) {
            scale = 1f
            offset = Offset.Zero
            scaleAnim.snapTo(1f)
            onScaleChanged(1f)
        }
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        offset = if (nextScale <= 1.02f) {
            Offset.Zero
        } else {
            clampOffset(offset + panChange, nextScale)
        }
        onScaleChanged(nextScale)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ViewerDarkBackdrop)
            .onSizeChanged { viewportSize = it }
            .pointerInput(file.effectiveId) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapOffset ->
                        scope.launch {
                            val target = if (scale > 1.15f) 1f else 2.5f
                            val startScale = scale
                            val startOffset = offset
                            val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                            val targetOffset = if (target <= 1.05f) {
                                Offset.Zero
                            } else {
                                clampOffset((center - tapOffset) * (target - 1f), target)
                            }
                            scaleAnim.snapTo(startScale)
                            scaleAnim.animateTo(
                                targetValue = target,
                                animationSpec = tween(durationMillis = 240)
                            ) {
                                val fraction = if (target == startScale) 1f else ((value - startScale) / (target - startScale)).coerceIn(0f, 1f)
                                scale = value
                                offset = Offset(
                                    x = startOffset.x + (targetOffset.x - startOffset.x) * fraction,
                                    y = startOffset.y + (targetOffset.y - startOffset.y) * fraction
                                )
                                onScaleChanged(value)
                            }
                        }
                    }
                )
            }
            .transformable(
                state = transformState,
                canPan = { scale > 1.02f }
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            encryptedError != null -> {
                PreviewErrorState(
                    message = encryptedError ?: "Failed to load preview",
                    onRetry = { reloadTrigger++ }
                )
            }
            imageModel == null -> {
                CircularProgressIndicator(
                    progress = { if (encryptedProgress in 0.05f..0.99f) encryptedProgress else 0f },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
            else -> {
                val request = remember(imageModel, cacheKey, context, reloadTrigger) {
                    ImageRequest.Builder(context)
                        .data(imageModel)
                        .apply {
                            if (imageModel is String) {
                                diskCacheKey(cacheKey)
                                memoryCacheKey(cacheKey)
                                diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            }
                        }
                        .crossfade(180)
                        .build()
                }
                SubcomposeAsyncImage(
                    model = request,
                    contentDescription = file.effectiveName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                ) {
                    when (painter.state) {
                        is AsyncImagePainter.State.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (thumbnailPlaceholderFile != null) {
                                    AsyncImage(
                                        model = thumbnailPlaceholderFile,
                                        contentDescription = file.effectiveName,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                CircularProgressIndicator(
                                    color = Color.White,
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                    strokeWidth = 3.dp,
                                    modifier = Modifier
                                        .then(
                                            if (thumbnailPlaceholderFile != null) {
                                                Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(24.dp)
                                                    .size(24.dp)
                                            } else {
                                                Modifier.size(34.dp)
                                            }
                                        )
                                )
                            }
                        }
                        is AsyncImagePainter.State.Error -> {
                            PreviewErrorState(
                                message = "Couldn't load image preview",
                                onRetry = { reloadTrigger++ }
                            )
                        }
                        else -> {
                            SubcomposeAsyncImageContent()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(28.dp)
    ) {
        Icon(
            imageVector = Icons.Default.BrokenImage,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(52.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White
            ),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("viewer_retry_button")
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text("Retry", fontWeight = FontWeight.Medium)
        }
    }
}

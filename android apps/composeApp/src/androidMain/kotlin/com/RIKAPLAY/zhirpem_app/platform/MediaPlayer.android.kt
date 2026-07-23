package com.RIKAPLAY.zhirpem_app.platform

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.RIKAPLAY.zhirpem_app.bounceClick

@Composable
actual fun GifPlayer(
    gifUrl: String,
    modifier: Modifier
) {
    val context = LocalContext.current
    val imageRequest = ImageRequest.Builder(context)
        .data(gifUrl)
        .decoderFactory(
            if (Build.VERSION.SDK_INT >= 28) {
                AnimatedImageDecoder.Factory()
            } else {
                GifDecoder.Factory()
            }
        )
        .crossfade(true)
        .build()

    AsyncImage(
        model = imageRequest,
        contentDescription = "GIF Animation",
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}

@Composable
actual fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier
) {
    VideoPlayerInternal(videoUrl = videoUrl, modifier = modifier, isFullScreenMode = false)
}

@Composable
private fun VideoPlayerInternal(
    videoUrl: String,
    modifier: Modifier,
    isFullScreenMode: Boolean
) {
    val context = LocalContext.current
    var isFullScreenOpen by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(videoUrl)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setControllerShowTimeoutMs(1500) 
                    setShowFastForwardButton(false)
                    setShowRewindButton(false)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)

                    resizeMode = if (isFullScreenMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isFullScreenMode) {
            IconButton(
                onClick = { isFullScreenOpen = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                    .size(36.dp)
                    .bounceClick()
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (isFullScreenOpen) {
        Dialog(
            onDismissRequest = { isFullScreenOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                VideoPlayerInternal(
                    videoUrl = videoUrl,
                    modifier = Modifier.fillMaxSize(),
                    isFullScreenMode = true
                )
            }
        }
    }
}

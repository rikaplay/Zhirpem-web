package com.RIKAPLAY.zhirpem_app

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Видеосообщение. Пока его не включили, показывается обложка (первый кадр с Cloudinary),
 * а плеер создаётся только по нажатию: раньше каждое видеосообщение чата сразу создавало плеер
 * и начинало загрузку видео, что сильно нагружало телефон.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoMessageBubble(rawVideoUrl: String, isMyMessage: Boolean, durationMs: Long = 0L) {
    val context = LocalContext.current

    val optimizedVideoUrl = remember(rawVideoUrl) {
        if (rawVideoUrl.contains("upload/")) {
            rawVideoUrl.replace("upload/", "upload/w_400,h_400,c_fill,q_auto,f_mp4/")
        } else {
            rawVideoUrl
        }
    }
    // Обложка: первый кадр видео в виде картинки
    val thumbnailUrl = remember(rawVideoUrl) {
        if (rawVideoUrl.contains("upload/")) {
            rawVideoUrl.replace("upload/", "upload/so_0,w_400,h_400,c_fill,q_auto,f_jpg/")
                .substringBeforeLast('.') + ".jpg"
        } else null
    }

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(durationMs) }

    fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(context).build()
        p.setMediaItem(MediaItem.fromUri(optimizedVideoUrl))
        p.repeatMode = Player.REPEAT_MODE_ALL
        p.prepare()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && p.duration > 0) duration = p.duration
            }
        })
        player = p
        return p
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            position = player?.currentPosition ?: 0L
            delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isMyMessage) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.Black)
                .clickable {
                    val p = ensurePlayer()
                    if (p.isPlaying) p.pause() else p.play()
                },
            contentAlignment = Alignment.Center
        ) {
            val activePlayer = player
            if (activePlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            this.player = activePlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (!isPlaying) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Длительность (при воспроизведении показывает, сколько осталось)
            if (duration > 0) {
                val shown = if (isPlaying) (duration - position).coerceAtLeast(0) else duration
                val seconds = shown / 1000
                Text(
                    text = String.format("%d:%02d", seconds / 60, seconds % 60),
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

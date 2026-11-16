package com.RIKAPLAY.zhirpem_app

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

private fun formatVoiceTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format("%d:%02d", s / 60, s % 60)
}

/**
 * Голосовое сообщение с волной звука. Волна записывается при записи сообщения;
 * у старых сообщений она заменяется запасной. По волне можно перематывать.
 * Плеер создаётся только при первом нажатии, поэтому длинный чат с голосовыми не грузит сеть и память.
 */
@OptIn(UnstableApi::class)
@Composable
fun VoiceMessageBubble(
    audioUrl: String,
    isMyMessage: Boolean,
    peaks: List<Long> = emptyList(),
    durationMs: Long = 0L
) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(durationMs) }

    val bars = remember(peaks, audioUrl) {
        if (peaks.isNotEmpty()) peaks.map { it.toInt() } else AudioWaveformData.fallback(audioUrl).peaks
    }
    val stopMe = remember { { player?.pause(); Unit } }

    fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(context).build()
        p.setMediaItem(MediaItem.fromUri(audioUrl))
        p.prepare()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && p.duration > 0) duration = p.duration
                if (playbackState == Player.STATE_ENDED) {
                    p.seekTo(0)
                    p.pause()
                    position = 0L
                }
            }
        })
        player = p
        return p
    }

    fun seekToFraction(fraction: Float) {
        val p = ensurePlayer()
        val total = if (duration > 0) duration else p.duration
        if (total > 0) {
            val target = (total * fraction.coerceIn(0f, 1f)).toLong()
            p.seekTo(target)
            position = target
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            position = player?.currentPosition ?: 0L
            delay(100)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            AudioPlaybackCoordinator.release(stopMe)
            player?.release()
            player = null
        }
    }

    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isMyMessage) Arrangement.End else Arrangement.Start
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(accent.copy(alpha = if (isMyMessage) 0.18f else 0.10f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .clickable {
                        val p = ensurePlayer()
                        if (p.isPlaying) {
                            p.pause()
                        } else {
                            AudioPlaybackCoordinator.activate(stopMe)
                            p.play()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Пауза" else "Слушать",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            Column {
                WaveformBars(
                    peaks = bars,
                    progress = if (duration > 0) position.toFloat() / duration else 0f,
                    color = accent,
                    modifier = Modifier
                        .width(170.dp)
                        .height(32.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { offset -> seekToFraction(offset.x / size.width) }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                seekToFraction(change.position.x / size.width)
                            }
                        }
                )
                Text(
                    text = if (isPlaying || position > 0) "${formatVoiceTime(position)} / ${formatVoiceTime(duration)}"
                    else formatVoiceTime(duration),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

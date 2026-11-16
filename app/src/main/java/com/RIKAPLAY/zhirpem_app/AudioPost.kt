package com.RIKAPLAY.zhirpem_app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/** Форма звуковой волны трека: [peaks] от 0 до 100, по одному значению на столбик. */
data class AudioWaveformData(val peaks: List<Int>, val durationMs: Long) {
    companion object {
        const val BARS = 64

        /** Запасной вариант, если файл не удалось разобрать: волна, одинаковая для одного и того же трека. */
        fun fallback(seed: String): AudioWaveformData {
            val rnd = Random(seed.hashCode())
            var level = 50
            val peaks = List(BARS) {
                level = (level + rnd.nextInt(-25, 26)).coerceIn(12, 100)
                level
            }
            return AudioWaveformData(peaks, 0L)
        }
    }
}

/** Читает аудиофайл и строит по нему волну (форму звука), которая показывается в посте. */
object AudioWaveformExtractor {
    private const val TIMEOUT_MS = 25_000L

    /** Вызывать не на главном потоке. Возвращает null, если файл не удалось декодировать. */
    fun extract(context: Context, uri: Uri, bars: Int = AudioWaveformData.BARS): AudioWaveformData? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)

            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    extractor.selectTrack(i)
                    format = f
                    break
                }
            }
            if (format == null) return null

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val declaredDurationUs =
                if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val times = ArrayList<Long>()
            val values = ArrayList<Int>()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val startedAt = System.currentTimeMillis()

            while (!outputDone) {
                if (System.currentTimeMillis() - startedAt > TIMEOUT_MS) return null

                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuffer = codec.getInputBuffer(inIndex)
                        val size = if (inBuffer != null) extractor.readSampleData(inBuffer, 0) else -1
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outIndex >= 0) {
                    val out = codec.getOutputBuffer(outIndex)
                    if (out != null && info.size > 0) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val samples = out.order(ByteOrder.nativeOrder()).asShortBuffer()
                        var peak = 0
                        while (samples.hasRemaining()) {
                            val v = abs(samples.get().toInt())
                            if (v > peak) peak = v
                        }
                        times.add(info.presentationTimeUs)
                        values.add(peak)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }

            if (values.isEmpty()) return null

            val totalUs = max(declaredDurationUs, (times.lastOrNull() ?: 0L) + 1)
            val buckets = IntArray(bars)
            for (i in values.indices) {
                val index = (times[i].toDouble() / totalUs * bars).toInt().coerceIn(0, bars - 1)
                if (values[i] > buckets[index]) buckets[index] = values[i]
            }
            // Пустые столбики берут значение у соседа, чтобы волна не «дырявилась»
            for (i in 1 until bars) if (buckets[i] == 0) buckets[i] = buckets[i - 1]

            val maxPeak = max(1, buckets.max())
            val peaks = buckets.map { p ->
                (sqrt(p.toFloat() / maxPeak) * 100f).toInt().coerceIn(6, 100)
            }
            return AudioWaveformData(peaks, totalUs / 1000)
        } catch (e: Exception) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }
}

/** Название файла для подписи в посте. */
fun audioDisplayName(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Аудио"
    } catch (e: Exception) {
        "Аудио"
    }
}

/** Рисует волну звука; закрашенная часть показывает, сколько уже проиграно. */
@Composable
fun WaveformBars(
    peaks: List<Int>,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (peaks.isEmpty()) return@Canvas
        val count = peaks.size
        val slot = size.width / count
        val barWidth = slot * 0.62f
        for (i in 0 until count) {
            val h = max(size.height * 0.08f, size.height * (peaks[i] / 100f))
            val x = i * slot + (slot - barWidth) / 2f
            val played = (i + 0.5f) / count <= progress
            drawRoundRect(
                color = if (played) color else color.copy(alpha = 0.28f),
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}

/** Чтобы одновременно играл только один аудиопост в ленте. */
object AudioPlaybackCoordinator {
    private var stopCurrent: (() -> Unit)? = null

    fun activate(stop: () -> Unit) {
        if (stopCurrent !== stop) stopCurrent?.invoke()
        stopCurrent = stop
    }

    fun release(stop: () -> Unit) {
        if (stopCurrent === stop) stopCurrent = null
    }
}

private fun formatAudioTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

/** Плеер аудиопоста: кнопка, волна звука (по ней можно перематывать), название и время. */
@OptIn(UnstableApi::class)
@Composable
fun AudioPostPlayer(
    url: String,
    peaks: List<Int>,
    title: String,
    durationHintMs: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(durationHintMs) }

    val shownPeaks = remember(peaks, url) {
        if (peaks.isEmpty()) AudioWaveformData.fallback(url).peaks else peaks
    }
    val stopMe = remember { { player?.pause(); Unit } }

    // Плеер создаётся только при первом нажатии, чтобы лента с десятками аудиопостов не держала десятки плееров
    fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(context).build()
        p.setMediaItem(MediaItem.fromUri(url))
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
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
                contentDescription = if (isPlaying) "Пауза" else "Играть",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.MusicNote, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title.ifBlank { "Аудио" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Волна: нажатие и протягивание перематывают трек
            WaveformBars(
                peaks = shownPeaks,
                progress = if (duration > 0) position.toFloat() / duration else 0f,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
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

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (duration > 0) "${formatAudioTime(position)} / ${formatAudioTime(duration)}" else formatAudioTime(position),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

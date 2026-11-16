package com.RIKAPLAY.zhirpem_app

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

enum class PendingState { UPLOADING, FAILED }

/** Исходящее медиа, которое ещё отправляется (или не отправилось). Показывается в чате до появления настоящего сообщения. */
class PendingMedia(
    val localId: String,
    val kind: String, // "voice", "video_square" или "image"
    val uri: Uri,
    val durationMs: Long = 0L,
    val peaks: List<Int> = emptyList()
) {
    var state by mutableStateOf(PendingState.UPLOADING)
    var progress by mutableFloatStateOf(0f)
    var requestId: String? = null
    /** Причина последней неудачи, показывается под сообщением. */
    var errorMessage by mutableStateOf<String?>(null)
}

/**
 * Кольцо прогресса, как в Telegram. Заполняется по мере отправки; пока прогресс ещё не пошёл,
 * дуга крутится по кругу.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    trackColor: Color = Color.White.copy(alpha = 0.25f),
    strokeWidth: Dp = 3.dp
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(250, easing = LinearEasing),
        label = "ringProgress"
    )
    val spin = rememberInfiniteTransition(label = "ringSpin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "ringAngle"
    )

    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke)
        )
        if (animated <= 0.02f) {
            // Отправка только началась: показываем вращающуюся дугу
            drawArc(
                color = color,
                startAngle = angle - 90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        } else {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

/** Появление нового сообщения: своё «влетает» справа, чужое слева, с лёгким отскоком. */
@Composable
fun AnimatedMessageEntry(
    isMine: Boolean,
    animate: Boolean,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            progress.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 380f))
        }
    }
    Box(
        modifier = Modifier.graphicsLayer {
            val p = progress.value
            alpha = p.coerceIn(0f, 1f)
            val scale = 0.8f + 0.2f * p
            scaleX = scale
            scaleY = scale
            translationY = (1f - p) * 36.dp.toPx()
            translationX = (1f - p) * (if (isMine) 48.dp.toPx() else -48.dp.toPx())
            transformOrigin = TransformOrigin(if (isMine) 1f else 0f, 1f)
        }
    ) {
        content()
    }
}

private fun formatPendingTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format("%d:%02d", s / 60, s % 60)
}

/** Сообщение, которое отправляется: кольцо прогресса, а при ошибке кнопка «повторить». */
@Composable
fun PendingMediaBubble(
    item: PendingMedia,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val failed = item.state == PendingState.FAILED
    val error = MaterialTheme.colorScheme.error

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        if (item.kind == "voice") {
            // ----- Голосовое -----
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (failed) error.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (failed) error else MaterialTheme.colorScheme.primary)
                                .clickable { if (failed) onRetry() else onCancel() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (failed) Icons.Default.Refresh else Icons.Default.Close,
                                contentDescription = if (failed) "Отправить ещё раз" else "Отменить отправку",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        if (!failed) {
                            ProgressRing(
                                progress = item.progress,
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    Column {
                        WaveformBars(
                            peaks = if (item.peaks.isNotEmpty()) item.peaks else AudioWaveformData.fallback(item.localId).peaks,
                            progress = 1f,
                            color = if (failed) error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(150.dp).height(30.dp)
                        )
                        Text(
                            text = formatPendingTime(item.durationMs),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    if (failed) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Удалить",
                            tint = error,
                            modifier = Modifier.size(22.dp).clickable { onCancel() }
                        )
                    }
                }
                if (failed) {
                    Text(
                        "Не отправлено" + (item.errorMessage?.let { ": " + it.take(90) } ?: "") + ". Нажмите ↻, чтобы повторить",
                        fontSize = 11.sp,
                        color = error,
                        modifier = Modifier.padding(top = 2.dp, end = 4.dp)
                    )
                }
            }
        } else {
            // ----- Видеосообщение или фото -----
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .size(if (item.kind == "image") 160.dp else 180.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.kind == "image") {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.Videocam, contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(56.dp)
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))

                    // Кнопка по центру: «отменить» при отправке, «повторить» при ошибке
                    Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(if (failed) error else Color.Black.copy(alpha = 0.55f))
                                .clickable { if (failed) onRetry() else onCancel() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (failed) Icons.Default.Refresh else Icons.Default.Close,
                                contentDescription = if (failed) "Отправить ещё раз" else "Отменить отправку",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        if (!failed) {
                            ProgressRing(progress = item.progress, modifier = Modifier.size(64.dp))
                        }
                    }

                    if (failed) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { onCancel() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (failed) {
                    Text(
                        "Не отправлено" + (item.errorMessage?.let { ": " + it.take(90) } ?: "") + ". Нажмите ↻, чтобы повторить",
                        fontSize = 11.sp,
                        color = error,
                        modifier = Modifier.padding(top = 2.dp, end = 4.dp)
                    )
                }
            }
        }
    }
}

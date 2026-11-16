package com.RIKAPLAY.zhirpem_app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import android.content.Context
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.sqrt

private enum class RecState { IDLE, HOLDING, LOCKED }

private const val MIN_RECORD_MS = 700L
private const val MAX_AUDIO_MS = 10 * 60_000L
private const val MAX_VIDEO_MS = 60_000L

/** Сводит записанные уровни громкости к фиксированному числу столбиков волны (значения 8..100). */
fun downsamplePeaks(samples: List<Int>, bars: Int = 40): List<Int> {
    if (samples.isEmpty()) return emptyList()
    val result = IntArray(bars)
    for (b in 0 until bars) {
        val from = (b * samples.size / bars).coerceAtMost(samples.size - 1)
        val to = max(from + 1, (b + 1) * samples.size / bars).coerceAtMost(samples.size)
        var peak = 0
        for (i in from until to) if (samples[i] > peak) peak = samples[i]
        result[b] = peak
    }
    val maxValue = max(1, result.max())
    return result.map { (sqrt(it.toFloat() / maxValue) * 100f).toInt().coerceIn(8, 100) }
}

private fun formatTimer(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format("%d:%02d", s / 60, s % 60)
}

/**
 * Поле ввода сообщения с записью голосовых и видеосообщений, как в Telegram:
 *  - зажмите кнопку, чтобы записывать, отпустите, чтобы отправить;
 *  - свайп вверх закрепляет запись (замок), после этого можно отпустить палец;
 *  - свайп влево отменяет запись;
 *  - короткое нажатие переключает «голосовое / видео».
 */
@Composable
fun ChatInputBar(
    onSendText: (String) -> Unit,
    onStartAudioRecord: () -> Boolean,
    onStopAudioRecord: (shouldSend: Boolean, peaks: List<Int>, durationMs: Long) -> Unit,
    onStartVideoRecord: () -> Boolean,
    onStopVideoRecord: (shouldSend: Boolean, durationMs: Long) -> Unit,
    onMoreClick: () -> Unit,
    audioAmplitude: () -> Int = { 0 },
    onTyping: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sharedPrefs = remember { context.getSharedPreferences("user_session", Context.MODE_PRIVATE) }
    val isVibrationEnabled = remember(sharedPrefs) { sharedPrefs.getBoolean("vibration_enabled", true) }

    val startAudio by rememberUpdatedState(onStartAudioRecord)
    val stopAudio by rememberUpdatedState(onStopAudioRecord)
    val startVideo by rememberUpdatedState(onStartVideoRecord)
    val stopVideo by rememberUpdatedState(onStopVideoRecord)
    val readAmplitude by rememberUpdatedState(audioAmplitude)

    var textState by remember { mutableStateOf("") }
    var inputMode by remember { mutableStateOf(ChatInputMode.AUDIO) }
    var recState by remember { mutableStateOf(RecState.IDLE) }
    var startMs by remember { mutableLongStateOf(0L) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val samples = remember { mutableStateListOf<Int>() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val lockPx = with(density) { 72.dp.toPx() }
    val cancelPx = with(density) { 96.dp.toPx() }

    fun vibrate() {
        if (isVibrationEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /** Останавливает запись: [send] = отправить или выбросить. Слишком короткие записи не отправляются. */
    fun finishRecording(send: Boolean) {
        if (recState == RecState.IDLE) return
        val duration = System.currentTimeMillis() - startMs
        val reallySend = send && duration >= MIN_RECORD_MS
        val mode = inputMode
        val peaks = downsamplePeaks(samples.toList())
        recState = RecState.IDLE
        RecordingState.isActive = false
        dragX = 0f
        dragY = 0f
        if (mode == ChatInputMode.AUDIO) stopAudio(reallySend, peaks, duration) else stopVideo(reallySend, duration)
        samples.clear()
        elapsedMs = 0L
    }
    val finish by rememberUpdatedState<(Boolean) -> Unit> { finishRecording(it) }

    // Если экран закрылся во время записи, запись отменяется, а жест панели снова включается
    DisposableEffect(Unit) {
        onDispose {
            if (recState != RecState.IDLE) finishRecording(false)
            RecordingState.isActive = false
        }
    }

    // Таймер и уровень громкости, пока идёт запись
    LaunchedEffect(recState) {
        while (recState != RecState.IDLE) {
            elapsedMs = System.currentTimeMillis() - startMs
            if (inputMode == ChatInputMode.AUDIO) {
                samples.add(readAmplitude())
                if (samples.size > 6000) samples.removeAt(0)
            }
            val limit = if (inputMode == ChatInputMode.AUDIO) MAX_AUDIO_MS else MAX_VIDEO_MS
            if (elapsedMs >= limit) {
                finish(true)
                break
            }
            delay(60)
        }
    }

    // Логика печатания
    LaunchedEffect(textState) {
        if (textState.isNotEmpty()) {
            onTyping(true)
            delay(3000)
            onTyping(false)
        } else {
            onTyping(false)
        }
    }

    val isRecording = recState != RecState.IDLE
    val buttonSize by animateDpAsState(
        targetValue = if (isRecording) 68.dp else 48.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "ButtonSizeAnimation"
    )

    val lockProgress = (-dragY / lockPx).coerceIn(0f, 1f)
    val cancelProgress = (-dragX / cancelPx).coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isRecording) {
            IconButton(onClick = onMoreClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "Дополнительно")
            }

            OutlinedTextField(
                value = textState,
                onValueChange = { textState = it },
                placeholder = { Text("Сообщение") },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                shape = RoundedCornerShape(24.dp),
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )
        } else {
            // Панель записи: красная точка, время, подсказка или волна
            val pulse = rememberInfiniteTransition(label = "recDot")
            val dotAlpha by pulse.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "dotAlpha"
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (recState == RecState.LOCKED) {
                    // Закреплённая запись: кнопка «удалить»
                    IconButton(onClick = { finish(false) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить запись", tint = MaterialTheme.colorScheme.error)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .graphicsLayer { alpha = dotAlpha }
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(formatTimer(elapsedMs), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.width(12.dp))

                if (recState == RecState.HOLDING) {
                    Text(
                        text = "‹  Влево — отмена",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.graphicsLayer {
                            alpha = 1f - cancelProgress
                            translationX = dragX.coerceAtMost(0f) * 0.5f
                        }
                    )
                } else if (inputMode == ChatInputMode.AUDIO) {
                    // Живая волна голоса
                    val live = samples.takeLast(36).map { ((it / 32767f) * 100f).toInt().coerceIn(8, 100) }
                    WaveformBars(
                        peaks = live,
                        progress = 1f,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f).height(28.dp)
                    )
                } else {
                    Text(
                        "Видеосообщение · можно переключить камеру",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Box(
            modifier = Modifier.size(68.dp),
            contentAlignment = Alignment.Center
        ) {
            // Замок над кнопкой: тяните вверх, чтобы закрепить запись
            if (recState == RecState.HOLDING) {
                Column(
                    modifier = Modifier
                        .offset(y = (-92).dp)
                        .graphicsLayer { translationY = dragY.coerceIn(-lockPx, 0f) * 0.6f }
                        .size(width = 44.dp, height = 88.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Icon(
                        imageVector = if (lockProgress > 0.6f) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Закрепить запись",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f * (1f - lockProgress)),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .clip(CircleShape)
                    .background(
                        if (isRecording) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (textState.trim().isNotEmpty() && !isRecording) {
                    IconButton(
                        onClick = {
                            vibrate()
                            onSendText(textState)
                            textState = ""
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Отправить текст",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)

                                    // Запись закреплена: нажатие на кнопку отправляет её
                                    if (recState == RecState.LOCKED) {
                                        val up = waitForUpOrCancellation()
                                        if (up != null) {
                                            up.consume()
                                            finish(true)
                                        }
                                        return@awaitEachGesture
                                    }

                                    // С первого касания отключаем жест боковой панели, иначе он сбросит запись
                                    RecordingState.isActive = true

                                    val longPress = awaitLongPressOrCancellation(down.id)
                                    if (longPress == null) {
                                        // Это тап или палец ушёл в сторону
                                        val stillPressed = currentEvent.changes.firstOrNull { it.id == down.id }?.pressed == true
                                        if (stillPressed) {
                                            waitForUpOrCancellation()
                                        } else {
                                            inputMode = if (inputMode == ChatInputMode.AUDIO) ChatInputMode.VIDEO else ChatInputMode.AUDIO
                                        }
                                        RecordingState.isActive = false
                                        return@awaitEachGesture
                                    }

                                    // Длинное нажатие: начинаем запись
                                    val started = if (inputMode == ChatInputMode.AUDIO) startAudio() else startVideo()
                                    if (!started) {
                                        RecordingState.isActive = false
                                        waitForUpOrCancellation()
                                        return@awaitEachGesture
                                    }
                                    MessageSounds.play(context, MessageSounds.Sound.RECORD_START)
                                    vibrate()
                                    samples.clear()
                                    startMs = System.currentTimeMillis()
                                    dragX = 0f
                                    dragY = 0f
                                    recState = RecState.HOLDING

                                    // 0 = отпустили, 1 = закрепили, 2 = отменили
                                    var outcome = 0
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        // Смещение считаем до consume(): у поглощённого события positionChange() возвращает ноль
                                        val delta = change.positionChange()
                                        change.consume()
                                        if (!change.pressed) break
                                        dragX += delta.x
                                        dragY += delta.y
                                        if (-dragY > lockPx) { outcome = 1; break }
                                        if (-dragX > cancelPx) { outcome = 2; break }
                                    }

                                    when (outcome) {
                                        0 -> finish(true)
                                        2 -> {
                                            vibrate()
                                            finish(false)
                                        }
                                        else -> {
                                            vibrate()
                                            dragX = 0f
                                            dragY = 0f
                                            recState = RecState.LOCKED
                                            // Дожидаемся, пока палец отпустят: сама запись продолжается
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                change.consume()
                                                if (!change.pressed) break
                                            }
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = if (recState == RecState.LOCKED) "locked" else inputMode.name,
                            label = "MediaIconAnimation"
                        ) { state ->
                            Icon(
                                imageVector = when (state) {
                                    "locked" -> Icons.AutoMirrored.Filled.Send
                                    ChatInputMode.AUDIO.name -> Icons.Default.Mic
                                    else -> Icons.Default.Videocam
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.RIKAPLAY.zhirpem_app

import android.annotation.SuppressLint
import android.net.Uri
import android.os.SystemClock
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val Accent = Color(0xFFFF2D55)

private suspend fun awaitCameraProvider(context: android.content.Context): ProcessCameraProvider =
    suspendCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
    }

/**
 * Съёмка ZHIRPEM Shorts: запись до 3 минут с паузой, переворотом камеры, вспышкой,
 * импортом из галереи и выбором звука из библиотеки.
 *
 * Если выбран звук, микрофон отключается: видео записывается без своей дорожки,
 * а при просмотре вместо неё играет выбранный звук (так делает и TikTok).
 */
@SuppressLint("MissingPermission")
@Composable
fun ShortsCreateScreen(
    vm: ShortsViewModel,
    me: String,
    initialSound: ShortSound?,
    onClose: () -> Unit,
    onPublished: (ShortVideo) -> Unit
) {
    CameraPermissionWrapper(
        onPermissionGranted = {
            ShortsCreateContent(vm, me, initialSound, onClose, onPublished)
        },
        onClose = onClose
    )
}

@SuppressLint("MissingPermission")
@Composable
private fun ShortsCreateContent(
    vm: ShortsViewModel,
    me: String,
    initialSound: ShortSound?,
    onClose: () -> Unit,
    onPublished: (ShortVideo) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var sound by remember { mutableStateOf(initialSound) }
    var showPicker by remember { mutableStateOf(false) }

    // Результат съёмки / импорта
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var videoDurationMs by remember { mutableLongStateOf(0L) }

    // Камера
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    // Запись
    var recording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var recordedMs by remember { mutableLongStateOf(0L) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var discardOnFinish by remember { mutableStateOf(false) }

    // Звук играет во время съёмки, чтобы под него можно было двигаться и петь
    val soundPlayer = remember { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE } }
    DisposableEffect(Unit) { onDispose { soundPlayer.release() } }
    LaunchedEffect(sound?.id) {
        val s = sound
        if (s != null) {
            soundPlayer.setMediaItem(MediaItem.fromUri(s.audioUrl))
            soundPlayer.prepare()
        } else {
            soundPlayer.stop()
            soundPlayer.clearMediaItems()
        }
    }

    // ---------- привязка камеры ----------
    LaunchedEffect(lensFacing, videoUri) {
        val provider = awaitCameraProvider(context)
        if (videoUri != null) {
            // На предпросмотре камера не нужна
            try { provider.unbindAll() } catch (_: Exception) {}
            return@LaunchedEffect
        }
        val recorder = Recorder.Builder()
            // 3,5 Мбит/с: трёхминутный ролик получается около 80 МБ и проходит лимит загрузки Cloudinary
            .setTargetVideoEncodingBitRate(3_500_000)
            .setQualitySelector(
                QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
            )
            .build()
        val capture = VideoCapture.withOutput(recorder)
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            videoCapture = capture
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось запустить камеру", Toast.LENGTH_SHORT).show()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            recording?.stop()
            try { ProcessCameraProvider.getInstance(context).get().unbindAll() } catch (_: Exception) {}
        }
    }
    LaunchedEffect(torchOn, camera) { camera?.cameraControl?.enableTorch(torchOn) }

    // ---------- таймер записи (паузы не считаются) ----------
    val currentRecording by rememberUpdatedState(recording)
    LaunchedEffect(isRecording, isPaused) {
        if (isRecording && !isPaused) {
            var last = SystemClock.elapsedRealtime()
            while (true) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                recordedMs += now - last
                last = now
                if (recordedMs >= SHORT_MAX_MS) {
                    currentRecording?.stop() // ровно 3 минуты, дальше нельзя
                    break
                }
            }
        }
    }

    fun startRecording() {
        val capture = videoCapture ?: return
        val file = File(context.cacheDir, "short_${System.currentTimeMillis()}.mp4")
        outputFile = file
        discardOnFinish = false
        var pending = capture.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
        // С выбранным звуком свой микрофон выключаем
        if (sound == null) pending = pending.withAudioEnabled()
        recordedMs = 0L
        recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                isRecording = false
                isPaused = false
                soundPlayer.pause()
                soundPlayer.seekTo(0)
                if (discardOnFinish) {
                    file.delete()
                } else if (!event.hasError() || file.length() > 0L) {
                    videoDurationMs = recordedMs.coerceAtLeast(ShortsPublisher.readDurationMs(context, Uri.fromFile(file)))
                        .coerceAtMost(SHORT_MAX_MS)
                    videoUri = Uri.fromFile(file)
                } else {
                    Toast.makeText(context, "Не удалось записать видео", Toast.LENGTH_SHORT).show()
                }
                recording = null
            }
        }
        isRecording = true
        isPaused = false
        if (sound != null) {
            soundPlayer.seekTo(0)
            soundPlayer.playWhenReady = true
        }
    }

    fun stopRecording() { recording?.stop() }

    fun togglePause() {
        val r = recording ?: return
        if (isPaused) {
            r.resume()
            if (sound != null) soundPlayer.playWhenReady = true
        } else {
            r.pause()
            soundPlayer.playWhenReady = false
        }
        isPaused = !isPaused
    }

    // Импорт из галереи
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val d = ShortsPublisher.readDurationMs(context, uri)
            if (d > SHORT_MAX_MS) {
                Toast.makeText(context, "Видео должно быть не длиннее 3 минут", Toast.LENGTH_LONG).show()
            } else if (ShortsPublisher.readSizeBytes(context, uri) > SHORT_MAX_UPLOAD_BYTES) {
                Toast.makeText(context, "Видео больше 100 МБ. Выберите файл поменьше", Toast.LENGTH_LONG).show()
            } else {
                videoDurationMs = d
                videoUri = uri
            }
        }
    }

    BackHandler {
        when {
            showPicker -> showPicker = false
            isRecording -> { discardOnFinish = true; stopRecording() }
            videoUri != null -> { videoUri = null; recordedMs = 0L }
            else -> onClose()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val uri = videoUri
        if (uri == null) {
            // ======================= СЪЁМКА =======================
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            Box(
                Modifier.fillMaxWidth().height(150.dp).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))
            )

            // Верхняя строка: закрыть, выбрать звук
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundIcon("close", "Закрыть") {
                    if (isRecording) { discardOnFinish = true; stopRecording() }
                    onClose()
                }
                Spacer(Modifier.weight(1f))
                SoundChooser(
                    sound = sound,
                    enabled = !isRecording,
                    onChoose = { showPicker = true },
                    onClear = { sound = null }
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(44.dp))
            }

            // Правая колонка инструментов
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RoundIcon("flip", "Перевернуть камеру", enabled = !isRecording) {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                        CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    torchOn = false
                }
                if (camera?.cameraInfo?.hasFlashUnit() == true) {
                    RoundIcon("bolt", "Вспышка", active = torchOn) { torchOn = !torchOn }
                }
            }

            // Низ: таймер, кнопка записи, галерея, пауза
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "${formatDuration(recordedMs)} / 3:00",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
                if (sound != null && !isRecording) {
                    Text(
                        "Микрофон выключен: во время съёмки играет выбранный звук",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        if (!isRecording) {
                            Icon(
                                SolarIcons.get("gallery"), "Из галереи", tint = Color.White,
                                modifier = Modifier
                                    .size(52.dp).clip(RoundedCornerShape(14.dp))
                                    .border(1.5.dp, Color.White, RoundedCornerShape(14.dp))
                                    .clickable {
                                        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                    }
                                    .padding(13.dp)
                            )
                        }
                    }
                    RecordButton(
                        recording = isRecording,
                        progress = recordedMs.toFloat() / SHORT_MAX_MS
                    ) {
                        if (isRecording) stopRecording() else startRecording()
                    }
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        if (isRecording) {
                            Icon(
                                SolarIcons.get(if (isPaused) "play" else "pause", bold = true),
                                if (isPaused) "Продолжить" else "Пауза",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(52.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .clickable { togglePause() }
                                    .padding(14.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // ======================= ПРЕДПРОСМОТР И ПУБЛИКАЦИЯ =======================
            ShortReviewScreen(
                uri = uri,
                durationMs = videoDurationMs,
                sound = sound,
                onChangeSound = { showPicker = true },
                onClearSound = { sound = null },
                onRetake = { videoUri = null; recordedMs = 0L },
                onPublished = onPublished
            )
        }

        // ======================= ВЫБОР ЗВУКА =======================
        if (showPicker) {
            SoundLibraryScreen(
                vm = vm,
                me = me,
                pickMode = true,
                bottomInset = 24.dp,
                onPick = { sound = it; showPicker = false },
                onOpenSound = {},
                onBack = { showPicker = false }
            )
        }
    }
}

// ----------------------------------------------------------------------

@Composable
private fun RoundIcon(
    icon: String,
    description: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Icon(
        SolarIcons.get(icon, bold = active),
        contentDescription = description,
        tint = if (active) Color(0xFFFFC83D) else Color.White,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = if (enabled) 0.35f else 0.15f))
            .clickable(enabled = enabled) { onClick() }
            .padding(11.dp)
    )
}

/** Кнопка «Выбрать звук» сверху, как в TikTok. */
@Composable
private fun SoundChooser(sound: ShortSound?, enabled: Boolean, onChoose: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(enabled = enabled) { onChoose() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(SolarIcons.get("music"), null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            sound?.title ?: "Выбрать звук",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp)
        )
        if (sound != null && enabled) {
            Spacer(Modifier.size(6.dp))
            Icon(
                SolarIcons.get("close"), "Убрать звук", tint = Color.White,
                modifier = Modifier.size(22.dp).clip(CircleShape).clickable { onClear() }.padding(4.dp)
            )
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, progress: Float, onClick: () -> Unit) {
    Box(
        Modifier.size(92.dp).clip(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color.White.copy(alpha = 0.4f), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke)
            )
            drawArc(
                color = Accent, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Box(
            Modifier
                .size(if (recording) 34.dp else 68.dp)
                .clip(if (recording) RoundedCornerShape(9.dp) else CircleShape)
                .background(if (recording) Accent else Color.White)
        )
    }
}

// ======================================================================
//  ПРЕДПРОСМОТР, ПОДПИСЬ И ПУБЛИКАЦИЯ
// ======================================================================

@Composable
private fun ShortReviewScreen(
    uri: Uri,
    durationMs: Long,
    sound: ShortSound?,
    onChangeSound: () -> Unit,
    onClearSound: () -> Unit,
    onRetake: () -> Unit,
    onPublished: (ShortVideo) -> Unit
) {
    val context = LocalContext.current
    var caption by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    val audio = remember(sound?.id) {
        sound?.let {
            ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(MediaItem.fromUri(it.audioUrl))
                prepare()
                playWhenReady = true
            }
        }
    }
    // С выбранным звуком собственная дорожка видео не играет — как и после публикации
    LaunchedEffect(sound?.id) { player.volume = if (sound != null) 0f else 1f }
    DisposableEffect(player) { onDispose { player.release() } }
    DisposableEffect(audio) { onDispose { audio?.release() } }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.view_short_player, null) as PlayerView)
                    .apply { this.player = player }
            }
        )
        Box(
            Modifier.fillMaxWidth().height(140.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))
        )
        Box(
            Modifier.fillMaxWidth().height(320.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
        )

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIcon("back", "Переснять", enabled = !uploading) { onRetake() }
            Spacer(Modifier.weight(1f))
            SoundChooser(sound, enabled = !uploading, onChoose = onChangeSound, onClear = onClearSound)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(44.dp))
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()
                .navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextField(
                value = caption,
                onValueChange = { if (it.length <= 300) caption = it },
                placeholder = { Text("Опишите видео… можно с #хештегами") },
                enabled = !uploading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Black.copy(alpha = 0.45f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.45f),
                    disabledContainerColor = Color.Black.copy(alpha = 0.3f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f)
                ),
                maxLines = 3
            )
            if (uploading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = Accent
                )
                Text("Загрузка… ${(progress * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
            } else {
                Button(
                    onClick = {
                        uploading = true
                        progress = 0f
                        ShortsPublisher.publish(
                            context = context,
                            videoUri = uri,
                            caption = caption,
                            sound = sound,
                            durationMs = durationMs,
                            onDone = { v ->
                                uploading = false
                                Toast.makeText(context, "Видео опубликовано", Toast.LENGTH_SHORT).show()
                                onPublished(v)
                            },
                            onError = { msg ->
                                uploading = false
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            },
                            onProgress = { progress = it }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
                ) {
                    Text("Опубликовать", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

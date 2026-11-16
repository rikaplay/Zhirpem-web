package com.RIKAPLAY.zhirpem_app

import android.view.LayoutInflater
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

private val ShortsRed = Color(0xFFFF2D55)
private val ShortsTextShadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 6f)

/** Сплошное (залитое) сердце для лайка: у линейных иконок заливка слишком бледная. */
private val FilledHeart: ImageVector by lazy {
    ImageVector.Builder(
        name = "FilledHeart", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        addPath(
            pathData = addPathNodes(
                "M12 20.5 C12 20.5 3.5 15.2 3.5 9.2 C3.5 6.6 5.5 4.5 8 4.5 C9.7 4.5 11.2 5.4 12 6.8 " +
                    "C12.8 5.4 14.3 4.5 16 4.5 C18.5 4.5 20.5 6.6 20.5 9.2 C20.5 15.2 12 20.5 12 20.5 Z"
            ),
            fill = SolidColor(Color.Black)
        )
    }.build()
}

// ======================================================================
//  ПАГЕР ВИДЕО
// ======================================================================

/**
 * Вертикальная лента видео как в TikTok. Слева: лайк, комментарии, репост, «поделиться»
 * (отправка в личные сообщения) и под ними звук с кнопкой «в избранное».
 * Нижняя панель приложения остаётся поверх, поэтому снизу оставлен отступ [bottomInset].
 */
@Composable
fun ShortsPager(
    videos: List<ShortVideo>,
    startIndex: Int,
    vm: ShortsViewModel,
    me: String,
    screenActive: Boolean,
    bottomInset: Dp,
    onOpenProfile: (String) -> Unit,
    onOpenSound: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLoadMore: () -> Unit = {},
    emptyText: String = "Пока нет видео",
    isLoading: Boolean = false
) {
    var commentsFor by remember { mutableStateOf<ShortVideo?>(null) }
    var shareFor by remember { mutableStateOf<ShortVideo?>(null) }
    var deleteFor by remember { mutableStateOf<ShortVideo?>(null) }
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Удалённые видео сразу исчезают из ленты
    @Suppress("NAME_SHADOWING")
    val videos = videos.filter { !vm.isDeleted(it.id) }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (videos.isEmpty()) {
            Text(
                text = if (isLoading) "Загрузка…" else emptyText,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            val pagerState = rememberPagerState(
                initialPage = startIndex.coerceIn(0, videos.lastIndex)
            ) { videos.size }

            LaunchedEffect(pagerState.currentPage, videos.size) {
                if (pagerState.currentPage >= videos.size - 3) onLoadMore()
            }

            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { videos[it].id }
            ) { page ->
                val video = vm.resolve(videos[page])
                ShortPage(
                    video = video,
                    isCurrent = pagerState.currentPage == page,
                    screenActive = screenActive,
                    vm = vm,
                    me = me,
                    bottomInset = bottomInset,
                    onOpenProfile = onOpenProfile,
                    onOpenSound = onOpenSound,
                    onComments = { commentsFor = video },
                    onShare = { shareFor = video },
                    onLongPress = {
                        // Удерживать можно только своё видео
                        if (video.authorUsername == me) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            deleteFor = video
                        }
                    }
                )
            }
        }
    }

    deleteFor?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Удалить видео?") },
            text = { Text("Видео и все комментарии к нему будут удалены. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteShort(target)
                    deleteFor = null
                    android.widget.Toast.makeText(context, "Видео удалено", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Отмена") } }
        )
    }

    commentsFor?.let { v ->
        ShortCommentsSheet(
            video = vm.resolve(v),
            vm = vm,
            me = me,
            onOpenProfile = { commentsFor = null; onOpenProfile(it) },
            onDismiss = { commentsFor = null }
        )
    }
    shareFor?.let { v ->
        // Тот же диалог, что и у обычных постов: видео уходит в личные сообщения карточкой
        SharePostDialog(
            postId = "short:${v.id}",
            postText = "🎬 " + v.caption.take(80),
            onDismiss = { shareFor = null }
        )
    }
}

// ======================================================================
//  ОДНО ВИДЕО
// ======================================================================

@Composable
private fun ShortPage(
    video: ShortVideo,
    isCurrent: Boolean,
    screenActive: Boolean,
    vm: ShortsViewModel,
    me: String,
    bottomInset: Dp,
    onOpenProfile: (String) -> Unit,
    onOpenSound: (String) -> Unit,
    onComments: () -> Unit,
    onShare: () -> Unit,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var userPaused by remember(video.id) { mutableStateOf(false) }
    var appResumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) appResumed = false
            if (event == Lifecycle.Event.ON_START) appResumed = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val playing = isCurrent && screenActive && appResumed && !userPaused

    // Звук из библиотеки: подгружаем только для текущего видео
    var sound by remember(video.soundId) { mutableStateOf<ShortSound?>(null) }
    var soundGone by remember(video.soundId) { mutableStateOf(false) }
    LaunchedEffect(video.soundId, isCurrent) {
        val id = video.soundId
        if (isCurrent && !id.isNullOrEmpty() && sound == null) {
            FirebaseFirestore.getInstance().collection("sounds").document(id).get()
                .addOnSuccessListener { d ->
                    if (d.exists()) d.toObject(ShortSound::class.java)?.let { sound = it.copy(id = d.id) }
                    else soundGone = true // автор удалил звук: играем исходную дорожку видео
                }
        }
    }
    var favorite by remember(video.soundId, sound?.id) { mutableStateOf(me in (sound?.favoritedBy ?: emptyList())) }

    var progress by remember(video.id) { mutableFloatStateOf(0f) }

    // Просмотр засчитываем, если видео посмотрели больше 2 секунд
    LaunchedEffect(video.id, playing) {
        if (playing) {
            delay(2000)
            vm.markViewed(video, me)
        }
    }

    // Сердце по двойному тапу
    var burstKey by remember(video.id) { mutableIntStateOf(0) }
    var burstPos by remember(video.id) { mutableStateOf(Offset.Zero) }
    val liked = me in video.likedBy

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(video.id) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = { userPaused = !userPaused },
                    onDoubleTap = { pos ->
                        burstPos = pos
                        burstKey++
                        if (!liked) vm.toggleLike(context, video, me)
                    }
                )
            }
    ) {
        // Превью, пока видео не загрузилось (и когда страница не текущая)
        AsyncImage(
            model = shortThumbUrl(video.videoUrl, 720),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (isCurrent) {
            ShortVideoSurface(
                video = video,
                soundUrl = if (video.muteOriginal) sound?.audioUrl else null,
                soundGone = soundGone,
                playing = playing,
                onProgress = { progress = it }
            )
        }

        // Затемнение сверху и снизу для читаемости
        Box(
            Modifier.fillMaxWidth().height(160.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
        )
        Box(
            Modifier.fillMaxWidth().height(380.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
        )

        // Иконка паузы
        val pauseAlpha by animateFloatAsState(if (userPaused && isCurrent) 1f else 0f, label = "pause")
        Icon(
            SolarIcons.get("play", bold = true),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.align(Alignment.Center).size(84.dp).alpha(pauseAlpha * 0.8f)
        )

        HeartBurst(burstKey, burstPos)

        // Колонка действий справа, как в TikTok: лайк, комментарии, репост, «поделиться»,
        // а под ними звук (звезда «в избранное» и вращающийся диск)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                // Колонка стоит над кнопкой «+», чтобы не перекрываться с ней
                .padding(end = 10.dp, bottom = bottomInset + 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ActionButton(
                icon = if (liked) FilledHeart else SolarIcons.get("heart"),
                tint = if (liked) ShortsRed else Color.White,
                count = compactCount(video.likes),
                bounce = liked
            ) { vm.toggleLike(context, video, me) }

            ActionButton(SolarIcons.get("chat"), Color.White, compactCount(video.commentsCount)) { onComments() }

            val reposted = me in video.repostedBy
            ActionButton(
                icon = SolarIcons.get("repost", bold = reposted),
                tint = if (reposted) Color(0xFF4CD964) else Color.White,
                count = compactCount(video.repostsCount),
                bounce = reposted
            ) { vm.toggleRepost(video, me) }

            ActionButton(SolarIcons.get("share"), Color.White, "Поделиться") { onShare() }

            SoundRail(
                video = video,
                sound = sound,
                favorite = favorite,
                playing = playing,
                onToggleFavorite = {
                    sound?.let {
                        favorite = !favorite
                        vm.toggleFavoriteSound(it, me, favorite)
                    }
                },
                onOpenSound = onOpenSound
            )
        }

        // Автор, подпись и название звука — слева внизу
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 88.dp, bottom = bottomInset + 8.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AuthorBlock(video, me, onOpenProfile)
            SoundTitle(video, onOpenSound)
        }

        // Тонкая полоска прогресса над нижней панелью
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = bottomInset - 6.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.18f))
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Color.White))
        }
    }
}

// ---------------- Видеоповерхность ----------------

@Composable
private fun ShortVideoSurface(
    video: ShortVideo,
    soundUrl: String?,
    soundGone: Boolean,
    playing: Boolean,
    onProgress: (Float) -> Unit
) {
    val context = LocalContext.current
    val replaceAudio = video.muteOriginal && soundUrl != null

    val player = remember(video.id) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(shortPlayUrl(video.videoUrl)))
            prepare()
        }
    }
    // Своя дорожка выключена, если вместо неё играет звук из библиотеки
    LaunchedEffect(replaceAudio, video.muteOriginal, soundGone) {
        player.volume = if (video.muteOriginal && !soundGone) 0f else 1f
    }

    val audio = remember(video.id, soundUrl, replaceAudio) {
        if (replaceAudio && soundUrl != null) {
            ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(MediaItem.fromUri(soundUrl))
                prepare()
            }
        } else null
    }

    var videoRatio by remember(video.id) { mutableFloatStateOf(0.5625f) }
    // audio может появиться позже (звук подгружается из базы), поэтому читаем его через State
    val audioState = androidx.compose.runtime.rememberUpdatedState(audio)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) {
                    val swap = size.unappliedRotationDegrees % 180 != 0
                    val w = if (swap) size.height else size.width
                    val h = if (swap) size.width else size.height
                    videoRatio = w * size.pixelWidthHeightRatio / h
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // Видео пошло по кругу: звук тоже начинаем сначала, чтобы не разъезжались
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) audioState.value?.seekTo(0)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    // Аудиоплеер освобождаем отдельно: он пересоздаётся при смене звука, а видеоплеер остаётся
    DisposableEffect(audio) {
        onDispose { audio?.release() }
    }

    LaunchedEffect(playing, audio) {
        player.playWhenReady = playing
        audio?.playWhenReady = playing
    }

    LaunchedEffect(player) {
        while (true) {
            delay(120)
            val d = player.duration
            if (d > 0) onProgress(player.currentPosition.toFloat() / d)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.view_short_player, null) as PlayerView).apply {
                this.player = player
            }
        },
        update = { view ->
            view.player = player
            // Вертикальные видео растягиваем на весь экран, горизонтальные вписываем целиком
            view.resizeMode = if (videoRatio > 0.8f) AspectRatioFrameLayout.RESIZE_MODE_FIT
            else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
    )
}

// ---------------- Кнопки ----------------

@Composable
private fun ActionButton(
    icon: ImageVector,
    tint: Color,
    count: String,
    bounce: Boolean = false,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (bounce) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bounce"
    )
    Column(
        modifier = Modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(36.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        )
        Text(
            count,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(shadow = ShortsTextShadow)
        )
    }
}

/** Звук под кнопкой «Поделиться»: звезда «в избранное» и вращающийся диск (открывает страницу звука). */
@Composable
private fun SoundRail(
    video: ShortVideo,
    sound: ShortSound?,
    favorite: Boolean,
    playing: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenSound: (String) -> Unit
) {
    val soundId = video.soundId
    val spin = rememberInfiniteTransition(label = "disc")
    val angle by spin.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "discAngle"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!soundId.isNullOrEmpty()) {
            Icon(
                SolarIcons.get("star", bold = favorite),
                contentDescription = "Добавить звук в избранное",
                tint = if (favorite) Color(0xFFFFC83D) else Color.White,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(enabled = sound != null) { onToggleFavorite() }
                    .padding(8.dp)
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .rotate(if (playing) angle else 0f)
                .clip(CircleShape)
                .background(Brush.sweepGradient(listOf(Color(0xFF2B2B2B), Color(0xFF6A6A6A), Color(0xFF2B2B2B))))
                .border(1.5.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                .clickable(enabled = !soundId.isNullOrEmpty()) { soundId?.let(onOpenSound) },
            contentAlignment = Alignment.Center
        ) {
            Icon(SolarIcons.get("music"), null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** Название звука бегущей строкой слева внизу. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoundTitle(video: ShortVideo, onOpenSound: (String) -> Unit) {
    val title = video.soundTitle.ifEmpty { "Оригинальный звук — @${video.authorUsername}" }
    val soundId = video.soundId
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(enabled = !soundId.isNullOrEmpty()) { soundId?.let(onOpenSound) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(SolarIcons.get("music"), null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(6.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 200.dp).basicMarquee()
        )
    }
}

/** Автор, подписка и подпись к видео. */
@Composable
private fun AuthorBlock(video: ShortVideo, me: String, onOpenProfile: (String) -> Unit) {
    val context = LocalContext.current
    val avatar = rememberUserAvatar(video.authorUsername, video.authorAvatarUrl)

    var isFollowing by remember(video.authorUsername) { mutableStateOf(true) }
    LaunchedEffect(video.authorUsername, me) {
        if (me.isNotEmpty() && me != video.authorUsername) {
            FirebaseFirestore.getInstance().collection("follows")
                .whereEqualTo("follower", me).whereEqualTo("following", video.authorUsername)
                .limit(1).get()
                .addOnSuccessListener { isFollowing = !it.isEmpty }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .border(1.5.dp, Color.White, CircleShape)
                    .clickable { onOpenProfile(video.authorUsername) },
                contentAlignment = Alignment.Center
            ) {
                if (avatar != null) {
                    AsyncImage(
                        model = CloudinaryUrls.avatar(avatar, 112),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(video.authorName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.size(10.dp))
            Text(
                "@${video.authorUsername}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = ShortsTextShadow),
                modifier = Modifier.weight(1f, fill = false).clickable { onOpenProfile(video.authorUsername) }
            )
            if (!isFollowing && me.isNotEmpty() && me != video.authorUsername) {
                Spacer(Modifier.size(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White, RoundedCornerShape(8.dp))
                        .clickable {
                            isFollowing = true
                            val db = FirebaseFirestore.getInstance()
                            db.collection("follows").add(
                                hashMapOf(
                                    "follower" to me,
                                    "following" to video.authorUsername,
                                    "timestamp" to FieldValue.serverTimestamp()
                                )
                            )
                            notifyFollowed(context, db, me, video.authorUsername)
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Подписаться", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (video.caption.isNotBlank()) {
            Text(
                video.caption,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = ShortsTextShadow)
            )
        }
    }
}

@Composable
private fun HeartBurst(key: Int, pos: Offset) {
    if (key == 0) return
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(650)) }
    val p = progress.value
    if (p >= 1f) return
    Icon(
        FilledHeart,
        contentDescription = null,
        tint = ShortsRed,
        modifier = Modifier
            .offset(
                x = with(androidx.compose.ui.platform.LocalDensity.current) { (pos.x).toDp() } - 48.dp,
                y = with(androidx.compose.ui.platform.LocalDensity.current) { (pos.y).toDp() } - 48.dp - (p * 40).dp
            )
            .size(96.dp)
            .graphicsLayer {
                val s = if (p < 0.3f) 0.4f + p / 0.3f * 0.8f else 1.2f - (p - 0.3f) * 0.3f
                scaleX = s
                scaleY = s
                alpha = if (p < 0.6f) 1f else 1f - (p - 0.6f) / 0.4f
            }
    )
}

// ======================================================================
//  КОММЕНТАРИИ
// ======================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortCommentsSheet(
    video: ShortVideo,
    vm: ShortsViewModel,
    me: String,
    onOpenProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var comments by remember { mutableStateOf(listOf<ShortComment>()) }
    var text by remember { mutableStateOf("") }

    DisposableEffect(video.id) {
        val reg = FirebaseFirestore.getInstance().collection("shorts_comments")
            .whereEqualTo("shortId", video.id)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    comments = snap.documents
                        .mapNotNull { d -> d.toObject(ShortComment::class.java)?.copy(id = d.id) }
                        .sortedByDescending { it.timestamp?.toDate()?.time ?: Long.MAX_VALUE }
                }
            }
        onDispose { reg.remove() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.78f).imePadding()) {
            Text(
                "Комментарии · ${compactCount(video.commentsCount)}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (comments.isEmpty()) {
                    item {
                        Text(
                            "Комментариев пока нет. Напишите первым!",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 24.dp)
                        )
                    }
                }
                items(comments, key = { it.id }) { c ->
                    val avatar = rememberUserAvatar(c.authorUsername)
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .clickable { onOpenProfile(c.authorUsername.replace("@", "")) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatar != null) {
                                AsyncImage(
                                    model = CloudinaryUrls.avatar(avatar, 96),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(c.author.take(1).uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text(
                                c.author.ifEmpty { c.authorUsername },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(c.text, fontSize = 14.sp)
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Добавить комментарий…") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    maxLines = 3
                )
                Spacer(Modifier.size(8.dp))
                Icon(
                    SolarIcons.get("send", bold = text.isNotBlank()),
                    contentDescription = "Отправить",
                    tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(enabled = text.isNotBlank()) {
                            vm.addComment(context, video, me, text)
                            text = ""
                        }
                        .padding(10.dp)
                )
            }
        }
    }
}

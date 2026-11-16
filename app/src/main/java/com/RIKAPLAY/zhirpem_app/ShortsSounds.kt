package com.RIKAPLAY.zhirpem_app

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

// ======================================================================
//  ОБЩИЕ ЭЛЕМЕНТЫ
// ======================================================================

/** Превью видео в сетке 9:16 со счётчиком просмотров. Если задан [onLongClick], работает удержание. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortThumb(
    video: ShortVideo,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { handler ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        handler()
                    }
                }
            )
    ) {
        AsyncImage(
            model = shortThumbUrl(video.videoUrl, 320),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(44.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
        )
        Row(
            Modifier.align(Alignment.BottomStart).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(SolarIcons.get("play", bold = true), null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(3.dp))
            Text(compactCount(video.views), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SoundDisc(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.sweepGradient(listOf(Color(0xFF2B2B2B), Color(0xFF707070), Color(0xFF2B2B2B))))
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(SolarIcons.get("music"), null, tint = Color.White, modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
fun ShortsTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Icon(
                SolarIcons.get("back"), "Назад",
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onBack() }.padding(10.dp)
            )
        } else Spacer(Modifier.size(12.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action()
    }
}

/** Один общий плеер для прослушивания звуков в списке. */
private class SoundPreviewer(val player: ExoPlayer) {
    var playingId by mutableStateOf<String?>(null)

    fun toggle(sound: ShortSound) {
        if (playingId == sound.id) {
            player.pause()
            playingId = null
        } else {
            player.setMediaItem(MediaItem.fromUri(sound.audioUrl))
            player.prepare()
            player.playWhenReady = true
            playingId = sound.id
        }
    }

    fun stop() {
        try { player.pause() } catch (e: Exception) { /* плеер уже освобождён */ }
        playingId = null
    }
}

@Composable
private fun rememberSoundPreviewer(): SoundPreviewer {
    val context = LocalContext.current
    val previewer = remember { SoundPreviewer(ExoPlayer.Builder(context).build()) }
    DisposableEffect(previewer) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) previewer.playingId = null
            }
        }
        previewer.player.addListener(l)
        onDispose {
            previewer.player.removeListener(l)
            previewer.player.release()
        }
    }
    return previewer
}

// ======================================================================
//  БИБЛИОТЕКА ЗВУКОВ
// ======================================================================

private val SoundTabs = listOf("Популярные", "Новые", "Избранные", "Мои")

/**
 * Площадка со звуками. Любой пользователь может загрузить свой звук, и он станет
 * доступен всем. В режиме [pickMode] нажатие на звук выбирает его для съёмки.
 */
@Composable
fun SoundLibraryScreen(
    vm: ShortsViewModel,
    me: String,
    pickMode: Boolean,
    bottomInset: Dp,
    onPick: (ShortSound) -> Unit,
    onOpenSound: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val previewer = rememberSoundPreviewer()

    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var sounds by remember { mutableStateOf(listOf<ShortSound>()) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var deleteSoundTarget by remember { mutableStateOf<ShortSound?>(null) }

    // Загрузка звука в библиотеку
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var uploadTitle by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val d = ShortsPublisher.readDurationMs(context, uri)
            if (d > SHORT_MAX_MS) {
                Toast.makeText(context, "Звук должен быть не длиннее 3 минут", Toast.LENGTH_LONG).show()
            } else {
                pendingUri = uri
                uploadTitle = ""
            }
        }
    }

    LaunchedEffect(tab, refreshKey) {
        loading = true
        previewer.stop()
        val db = FirebaseFirestore.getInstance()
        val q: Query = when (tab) {
            0 -> db.collection("sounds").orderBy("uses", Query.Direction.DESCENDING).limit(60)
            1 -> db.collection("sounds").orderBy("timestamp", Query.Direction.DESCENDING).limit(60)
            2 -> db.collection("sounds").whereArrayContains("favoritedBy", me)
            else -> db.collection("sounds").whereEqualTo("authorUsername", me)
        }
        q.get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { d -> d.toObject(ShortSound::class.java)?.copy(id = d.id) }
                sounds = when (tab) {
                    2 -> list.sortedBy { it.title.lowercase() }
                    3 -> list.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
                    else -> list
                }
                loading = false
            }
            .addOnFailureListener { sounds = emptyList(); loading = false }
    }

    val shown = remember(sounds, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) sounds else sounds.filter {
            it.title.lowercase().contains(q) || it.authorUsername.lowercase().contains(q)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ShortsTopBar(
            title = if (pickMode) "Выбрать звук" else "Звуки",
            onBack = { previewer.stop(); onBack() }
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { picker.launch("audio/*") }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(SolarIcons.get("upload"), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Загрузить", color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Поиск звука или автора") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            leadingIcon = { Icon(SolarIcons.get("search"), null, modifier = Modifier.size(20.dp)) }
        )

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoundTabs.forEachIndexed { i, name ->
                val selected = tab == i
                Text(
                    name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { tab = i }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                shown.isEmpty() -> Text(
                    text = when (tab) {
                        2 -> "Добавляйте звуки в избранное звездой, и они появятся здесь"
                        3 -> "Вы ещё не загружали звуки. Загруженный звук можно удалить удержанием"
                        else -> "Звуков пока нет. Загрузите первый!"
                    },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = bottomInset + 12.dp)) {
                    items(shown, key = { it.id }) { sound ->
                        SoundRow(
                            sound = sound,
                            me = me,
                            playing = previewer.playingId == sound.id,
                            pickMode = pickMode,
                            onPreview = { previewer.toggle(sound) },
                            onFavorite = { fav ->
                                vm.toggleFavoriteSound(sound, me, fav)
                                sounds = sounds.map {
                                    if (it.id == sound.id) it.copy(
                                        favoritedBy = if (fav) it.favoritedBy + me else it.favoritedBy - me
                                    ) else it
                                }
                            },
                            // Удержание на своём звуке открывает удаление
                            onLongClick = if (sound.authorUsername == me) ({ deleteSoundTarget = sound }) else null,
                            onClick = {
                                previewer.stop()
                                if (pickMode) onPick(sound) else onOpenSound(sound.id)
                            }
                        )
                    }
                }
            }
        }
    }

    deleteSoundTarget?.let { target ->
        DeleteSoundDialog(
            sound = target,
            onConfirm = {
                previewer.stop()
                vm.deleteSound(target)
                sounds = sounds.filter { it.id != target.id }
                deleteSoundTarget = null
                Toast.makeText(context, "Звук удалён", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { deleteSoundTarget = null }
        )
    }

    if (pendingUri != null) {
        AlertDialog(
            onDismissRequest = { if (!uploading) pendingUri = null },
            title = { Text("Новый звук") },
            text = {
                Column {
                    OutlinedTextField(
                        value = uploadTitle,
                        onValueChange = { uploadTitle = it },
                        label = { Text("Название") },
                        singleLine = true,
                        enabled = !uploading
                    )
                    if (uploading) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { uploadProgress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "После загрузки звук появится в общей библиотеке, и под него смогут снимать все.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !uploading,
                    onClick = {
                        val uri = pendingUri ?: return@Button
                        uploading = true
                        uploadProgress = 0f
                        ShortsPublisher.uploadSound(
                            context = context,
                            audioUri = uri,
                            title = uploadTitle,
                            onDone = {
                                uploading = false
                                pendingUri = null
                                tab = 3
                                refreshKey++
                                Toast.makeText(context, "Звук добавлен в библиотеку", Toast.LENGTH_SHORT).show()
                            },
                            onError = { msg ->
                                uploading = false
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            },
                            onProgress = { uploadProgress = it }
                        )
                    }
                ) { Text("Загрузить") }
            },
            dismissButton = {
                TextButton(enabled = !uploading, onClick = { pendingUri = null }) { Text("Отмена") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoundRow(
    sound: ShortSound,
    me: String,
    playing: Boolean,
    pickMode: Boolean,
    onPreview: () -> Unit,
    onFavorite: (Boolean) -> Unit,
    onLongClick: (() -> Unit)?,
    onClick: () -> Unit
) {
    val favorite = me in sound.favoritedBy
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { handler ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        handler()
                    }
                }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            SoundDisc(52.dp)
            Icon(
                SolarIcons.get(if (playing) "pause" else "play", bold = true),
                contentDescription = if (playing) "Остановить" else "Прослушать",
                tint = Color.White,
                modifier = Modifier.size(52.dp).clip(CircleShape).clickable { onPreview() }.padding(14.dp)
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(sound.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "@${sound.authorUsername} · ${formatDuration(sound.durationMs)} · ${compactCount(sound.uses)} видео",
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Icon(
            SolarIcons.get("star", bold = favorite),
            contentDescription = "В избранное",
            tint = if (favorite) Color(0xFFFFC83D) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onFavorite(!favorite) }.padding(10.dp)
        )
        if (pickMode) {
            Text(
                "Выбрать",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onClick() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

/** Подтверждение удаления звука с предупреждением, если под него уже снимали. */
@Composable
private fun DeleteSoundDialog(sound: ShortSound, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить звук?") },
        text = {
            Text(
                buildString {
                    append("«${sound.title}» исчезнет из библиотеки и избранного у всех.")
                    if (sound.uses > 0) {
                        append(" Под него снято видео: ${sound.uses}. У видео со звуком из библиотеки будет играть исходная дорожка.")
                    }
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ======================================================================
//  СТРАНИЦА ЗВУКА: видео под этот звук и кнопка «Снять видео»
// ======================================================================

@Composable
fun SoundDetailScreen(
    soundId: String,
    vm: ShortsViewModel,
    me: String,
    bottomInset: Dp,
    onBack: () -> Unit,
    onUse: (ShortSound) -> Unit,
    onOpenViewer: (List<ShortVideo>, Int) -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val previewer = rememberSoundPreviewer()
    var sound by remember(soundId) { mutableStateOf<ShortSound?>(null) }
    var videos by remember(soundId) { mutableStateOf(listOf<ShortVideo>()) }
    var loading by remember(soundId) { mutableStateOf(true) }
    var notFound by remember(soundId) { mutableStateOf(false) }
    var showDelete by remember(soundId) { mutableStateOf(false) }
    val context = LocalContext.current

    DisposableEffect(soundId) {
        val db = FirebaseFirestore.getInstance()
        val reg = db.collection("sounds").document(soundId).addSnapshotListener { d, _ ->
            if (d != null && d.exists()) sound = d.toObject(ShortSound::class.java)?.copy(id = d.id)
            else if (d != null) notFound = true
        }
        db.collection("shorts").whereEqualTo("soundId", soundId).get()
            .addOnSuccessListener { snap ->
                videos = snap.documents
                    .mapNotNull { d -> d.toObject(ShortVideo::class.java)?.copy(id = d.id) }
                    .sortedByDescending { it.likes }
                loading = false
            }
            .addOnFailureListener { loading = false }
        onDispose { reg.remove(); previewer.stop() }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val s = sound
        ShortsTopBar(title = "Звук", onBack = { previewer.stop(); onBack() }) {
            // Автор может удалить свой звук
            if (s != null && s.authorUsername == me) {
                Icon(
                    SolarIcons.get("trash"), "Удалить звук", tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable { showDelete = true }.padding(10.dp)
                )
            }
        }

        if (s == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (notFound) Text("Этот звук удалён", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                else CircularProgressIndicator()
            }
            return@Column
        }
        if (showDelete) {
            DeleteSoundDialog(
                sound = s,
                onConfirm = {
                    previewer.stop()
                    vm.deleteSound(s)
                    showDelete = false
                    Toast.makeText(context, "Звук удалён", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                onDismiss = { showDelete = false }
            )
        }
        val favorite = me in s.favoritedBy

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = bottomInset + 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SoundDisc(96.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(s.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "@${s.authorUsername}",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOpenProfile(s.authorUsername) }.padding(4.dp)
                    )
                    Text(
                        "${formatDuration(s.durationMs)} · ${compactCount(s.uses)} видео",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            SolarIcons.get(if (previewer.playingId == s.id) "pause" else "play", bold = true),
                            contentDescription = "Прослушать",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { previewer.toggle(s) }
                                .padding(13.dp)
                        )
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { previewer.stop(); onUse(s) }
                                .padding(horizontal = 22.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(SolarIcons.get("plus"), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Снять видео", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                        Icon(
                            SolarIcons.get("star", bold = favorite),
                            contentDescription = "В избранное",
                            tint = if (favorite) Color(0xFFFFC83D) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    val now = !favorite
                                    vm.toggleFavoriteSound(s, me, now)
                                    sound = s.copy(favoritedBy = if (now) s.favoritedBy + me else s.favoritedBy - me)
                                }
                                .padding(12.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else if (videos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Под этот звук ещё никто не снимал. Будьте первым!",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                items(videos, key = { it.id }) { v ->
                    ShortThumb(vm.resolve(v)) { onOpenViewer(videos, videos.indexOf(v)) }
                }
            }
        }
    }
}

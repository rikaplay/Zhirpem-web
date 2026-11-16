package com.RIKAPLAY.zhirpem_app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Профиль в Shorts. Отличается от профиля в обычной ленте: баннер переходит в аватарку,
 * под ней крупная статистика (подписки, подписчики, лайки), а ниже видео человека.
 * У своего профиля есть ещё вкладки «Понравившиеся» и «Избранные звуки».
 */
@Composable
fun ShortsProfileScreen(
    username: String,
    vm: ShortsViewModel,
    me: String,
    bottomInset: Dp,
    onBack: (() -> Unit)?,
    onOpenViewer: (List<ShortVideo>, Int) -> Unit,
    onOpenSound: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onCreate: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val isOwn = username == me

    // ---- данные пользователя ----
    var name by remember(username) { mutableStateOf(username) }
    var status by remember(username) { mutableStateOf("") }
    var bannerColorHex by remember(username) { mutableStateOf("#3A3A3A") }
    var bannerUrl by remember(username) { mutableStateOf<String?>(null) }
    var equippedBanner by remember(username) { mutableStateOf<String?>(null) }
    var equippedFrame by remember(username) { mutableStateOf<String?>(null) }
    val avatar = rememberUserAvatar(username)

    var followers by remember(username) { mutableIntStateOf(0) }
    var followingCount by remember(username) { mutableIntStateOf(0) }
    var isFollowing by remember(username) { mutableStateOf(false) }

    var videos by remember(username) { mutableStateOf(listOf<ShortVideo>()) }
    var videosLoading by remember(username) { mutableStateOf(true) }
    var liked by remember(username) { mutableStateOf<List<ShortVideo>?>(null) }
    var favSounds by remember(username) { mutableStateOf<List<ShortSound>?>(null) }
    var tab by remember(username) { mutableIntStateOf(0) }

    DisposableEffect(username) {
        val reg = db.collection("users").document(username).addSnapshotListener { d, _ ->
            if (d != null && d.exists()) {
                name = d.getString("name") ?: username
                status = d.getString("status") ?: ""
                bannerColorHex = d.getString("bannerColor") ?: "#3A3A3A"
                bannerUrl = d.getString("bannerUrl")
                equippedBanner = d.getString("equippedBanner")?.takeIf { it.isNotEmpty() }
                equippedFrame = d.getString("equippedFrame")?.takeIf { it.isNotEmpty() }
            }
        }
        onDispose { reg.remove() }
    }

    LaunchedEffect(username) {
        db.collection("follows").whereEqualTo("following", username).count().get(AggregateSource.SERVER)
            .addOnSuccessListener { followers = it.count.toInt() }
        db.collection("follows").whereEqualTo("follower", username).count().get(AggregateSource.SERVER)
            .addOnSuccessListener { followingCount = it.count.toInt() }
        if (!isOwn && me.isNotEmpty()) {
            db.collection("follows").whereEqualTo("follower", me).whereEqualTo("following", username)
                .limit(1).get().addOnSuccessListener { isFollowing = !it.isEmpty }
        }
        db.collection("shorts").whereEqualTo("authorUsername", username).get()
            .addOnSuccessListener { snap ->
                videos = snap.documents
                    .mapNotNull { d -> d.toObject(ShortVideo::class.java)?.copy(id = d.id) }
                    .sortedByDescending { it.timestamp?.toDate()?.time ?: Long.MAX_VALUE }
                videosLoading = false
            }
            .addOnFailureListener { videosLoading = false }
    }

    // Понравившиеся и звуки грузим, когда открыли вкладку
    LaunchedEffect(tab, isOwn) {
        if (!isOwn) return@LaunchedEffect
        if (tab == 1 && liked == null) {
            db.collection("shorts").whereArrayContains("likedBy", me).get()
                .addOnSuccessListener { snap ->
                    liked = snap.documents
                        .mapNotNull { d -> d.toObject(ShortVideo::class.java)?.copy(id = d.id) }
                        .sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
                }
                .addOnFailureListener { liked = emptyList() }
        }
        if (tab == 2 && favSounds == null) {
            db.collection("sounds").whereArrayContains("favoritedBy", me).get()
                .addOnSuccessListener { snap ->
                    favSounds = snap.documents
                        .mapNotNull { d -> d.toObject(ShortSound::class.java)?.copy(id = d.id) }
                        .sortedBy { it.title.lowercase() }
                }
                .addOnFailureListener { favSounds = emptyList() }
        }
    }

    val bannerColor = remember(bannerColorHex) {
        try { Color(android.graphics.Color.parseColor(bannerColorHex)) } catch (e: Exception) { Color(0xFF3A3A3A) }
    }
    // Удалённые видео пропадают сразу, без перезагрузки
    val visibleVideos = videos.filter { !vm.isDeleted(it.id) }
    val visibleLiked = liked?.filter { !vm.isDeleted(it.id) }
    var deleteTarget by remember(username) { mutableStateOf<ShortVideo?>(null) }
    val totalLikes = visibleVideos.sumOf { vm.resolve(it).likes }
    val bg = MaterialTheme.colorScheme.background

    fun toggleFollow() {
        if (me.isEmpty() || isOwn) return
        if (isFollowing) {
            isFollowing = false
            followers = (followers - 1).coerceAtLeast(0)
            db.collection("follows").whereEqualTo("follower", me).whereEqualTo("following", username).get()
                .addOnSuccessListener { s -> s.documents.forEach { it.reference.delete() } }
        } else {
            isFollowing = true
            followers += 1
            db.collection("follows").add(
                hashMapOf("follower" to me, "following" to username, "timestamp" to FieldValue.serverTimestamp())
            )
            notifyFollowed(context, db, me, username)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().background(bg),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = bottomInset + 12.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // ===================== ШАПКА: баннер + аватарка =====================
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().height(204.dp + 54.dp)) {
                Box(Modifier.fillMaxWidth().height(204.dp)) {
                    when {
                        equippedBanner != null -> {
                            Box(Modifier.fillMaxSize().background(bannerColor))
                            ShopBannerArt(bannerId = equippedBanner)
                        }
                        !bannerUrl.isNullOrEmpty() -> AsyncImage(
                            model = bannerUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        else -> Box(
                            Modifier.fillMaxSize().background(
                                Brush.linearGradient(listOf(bannerColor, bannerColor.copy(alpha = 0.55f)))
                            )
                        )
                    }
                    // Баннер плавно уходит в фон
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(0.45f to Color.Transparent, 1f to bg)
                        )
                    )
                }
                if (onBack != null) {
                    Icon(
                        SolarIcons.get("back"), "Назад", tint = Color.White,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(8.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable { onBack() }
                            .padding(10.dp)
                    )
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).size(108.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(3.dp, bg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatar != null) {
                            AsyncImage(
                                model = CloudinaryUrls.avatar(avatar, 320),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(name.take(1).uppercase(), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    AvatarFrameOverlay(frameId = equippedFrame, bannerColor = bannerColor)
                }
            }
        }

        // ===================== ИМЯ, СТАТИСТИКА, КНОПКИ =====================
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("@$username", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                if (status.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(status, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ProfileStat(compactCount(followingCount), "Подписки")
                    ProfileStat(compactCount(followers), "Подписчики")
                    ProfileStat(compactCount(totalLikes), "Лайки")
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isOwn) {
                        ProfileButton("Снять видео", primary = true, modifier = Modifier.weight(1f)) { onCreate() }
                        ProfileButton("Звуки", primary = false, modifier = Modifier.weight(1f)) { onOpenLibrary() }
                    } else {
                        ProfileButton(
                            if (isFollowing) "Вы подписаны" else "Подписаться",
                            primary = !isFollowing,
                            modifier = Modifier.weight(1f)
                        ) { toggleFollow() }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        // ===================== ВКЛАДКИ =====================
        item(span = { GridItemSpan(maxLineSpan) }) {
            val tabs = if (isOwn) listOf("grid", "heart", "music") else listOf("grid")
            Row(Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { i, icon ->
                    val selected = tab == i
                    Column(
                        Modifier.weight(1f).clickable { tab = i }.padding(top = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            SolarIcons.get(icon, bold = selected),
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.fillMaxWidth().height(2.dp)
                                .background(if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                        )
                    }
                }
            }
        }

        // ===================== СОДЕРЖИМОЕ ВКЛАДКИ =====================
        when (tab) {
            // Удержание на своём видео открывает удаление
            0 -> gridOfVideos(
                visibleVideos, videosLoading, "Здесь будут видео", vm, onOpenViewer,
                onLongClick = if (isOwn) ({ v: ShortVideo -> deleteTarget = v }) else null
            )
            1 -> gridOfVideos(
                visibleLiked ?: emptyList(), liked == null, "Видео, которые вы лайкнули, появятся здесь", vm, onOpenViewer,
                onLongClick = { v -> if (v.authorUsername == me) deleteTarget = v }
            )
            else -> {
                val sounds = favSounds
                if (sounds == null) {
                    item(span = { GridItemSpan(maxLineSpan) }) { LoadingBox() }
                } else if (sounds.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyBox("Добавляйте звуки в избранное звездой, чтобы потом снимать под них видео")
                    }
                } else {
                    items(sounds, key = { "s_" + it.id }, span = { GridItemSpan(maxLineSpan) }) { s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenSound(s.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SoundDisc(48.dp)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "@${s.authorUsername} · ${compactCount(s.uses)} видео",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Icon(SolarIcons.get("star", bold = true), null, tint = Color(0xFFFFC83D), modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить видео?") },
            text = { Text("Видео и все комментарии к нему будут удалены. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteShort(target)
                    deleteTarget = null
                    android.widget.Toast.makeText(context, "Видео удалено", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } }
        )
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.gridOfVideos(
    list: List<ShortVideo>,
    loading: Boolean,
    emptyText: String,
    vm: ShortsViewModel,
    onOpenViewer: (List<ShortVideo>, Int) -> Unit,
    onLongClick: ((ShortVideo) -> Unit)? = null
) {
    if (loading) {
        item(span = { GridItemSpan(maxLineSpan) }) { LoadingBox() }
    } else if (list.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) { EmptyBox(emptyText) }
    } else {
        items(list, key = { it.id }) { v ->
            ShortThumb(
                video = vm.resolve(v),
                onLongClick = onLongClick?.let { handler -> { handler(v) } }
            ) { onOpenViewer(list, list.indexOf(v)) }
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun EmptyBox(text: String) {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun ProfileButton(text: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

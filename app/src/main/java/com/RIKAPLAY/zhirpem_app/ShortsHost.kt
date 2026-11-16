package com.RIKAPLAY.zhirpem_app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore

/** Экраны, которые открываются поверх ленты внутри вкладки Shorts. */
sealed class ShortsRoute {
    data class Profile(val username: String) : ShortsRoute()
    data class Sound(val id: String) : ShortsRoute()
    data class Viewer(val videos: List<ShortVideo>, val index: Int) : ShortsRoute()
    data class Create(val sound: ShortSound?) : ShortsRoute()
    object Library : ShortsRoute()
}

/**
 * Вкладка ZHIRPEM Shorts. Нижняя панель приложения здесь всегда на экране: видео идёт
 * под ней, поэтому у всех экранов есть нижний отступ [bottomInset]. Прячется панель
 * только на съёмке (см. [ShortsNav.fullscreen]).
 */
@Composable
fun ShortsHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: ShortsViewModel = viewModel()
    val me = remember {
        context.getSharedPreferences("user_session", Context.MODE_PRIVATE).getString("username", "") ?: ""
    }
    val stack = remember { mutableStateListOf<ShortsRoute>() }
    var feedTab by remember { mutableIntStateOf(0) } // 0 — рекомендации, 1 — подписки

    // Видео идёт под панелью навигации: оставляем место над ней
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp

    val top = stack.lastOrNull()
    SideEffect { ShortsNav.fullscreen = top is ShortsRoute.Create }
    DisposableEffect(Unit) { onDispose { ShortsNav.fullscreen = false } }

    fun push(route: ShortsRoute) { stack.add(route) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    // Загрузка лент
    LaunchedEffect(me) { vm.loadForYou(me, refresh = true) }
    LaunchedEffect(feedTab) { if (feedTab == 1) vm.loadFollowing(me) }

    // Нажали «+» на вкладке
    LaunchedEffect(ShortsNav.createRequested) {
        if (ShortsNav.createRequested) {
            ShortsNav.createRequested = false
            if (stack.lastOrNull() !is ShortsRoute.Create) push(ShortsRoute.Create(null))
        }
    }

    // Открыли ссылку на видео (например, из личных сообщений)
    LaunchedEffect(PendingShortLink.shortId) {
        val id = PendingShortLink.shortId ?: return@LaunchedEffect
        PendingShortLink.shortId = null
        FirebaseFirestore.getInstance().collection("shorts").document(id).get()
            .addOnSuccessListener { d ->
                d.toObject(ShortVideo::class.java)?.copy(id = d.id)?.let { push(ShortsRoute.Viewer(listOf(it), 0)) }
            }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        // ===================== ЛЕНТА =====================
        val list = if (feedTab == 0) vm.forYou else vm.following
        ShortsPager(
            videos = list,
            startIndex = 0,
            vm = vm,
            me = me,
            screenActive = stack.isEmpty(),
            bottomInset = bottomInset,
            onOpenProfile = { push(ShortsRoute.Profile(it)) },
            onOpenSound = { push(ShortsRoute.Sound(it)) },
            onLoadMore = { if (feedTab == 0) vm.loadForYou(me, refresh = false) },
            emptyText = if (feedTab == 0) "Видео пока нет. Нажмите «+» и снимите первое!"
            else "Здесь появятся видео авторов, на которых вы подписаны",
            isLoading = if (feedTab == 0) vm.isLoadingForYou else vm.isLoadingFollowing,
            modifier = Modifier
        )

        // Верхняя строка: звуки · Рекомендации | Подписки · мой профиль
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                SolarIcons.get("music"), "Звуки", tint = Color.White,
                modifier = Modifier.size(42.dp).clip(CircleShape).clickable { push(ShortsRoute.Library) }.padding(9.dp)
            )
            Spacer(Modifier.weight(1f))
            FeedTab("Рекомендации", feedTab == 0) { feedTab = 0 }
            Spacer(Modifier.width(18.dp))
            FeedTab("Подписки", feedTab == 1) { feedTab = 1 }
            Spacer(Modifier.weight(1f))
            MyProfileButton(me) { push(ShortsRoute.Profile(me)) }
        }

        // ===================== ЭКРАНЫ ПОВЕРХ ЛЕНТЫ =====================
        val route = stack.lastOrNull()
        if (route != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    // Перехватываем касания, чтобы лента под экраном не реагировала
                    .pointerInput(Unit) { detectTapGestures { } }
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (route) {
                    is ShortsRoute.Profile -> ShortsProfileScreen(
                        username = route.username,
                        vm = vm,
                        me = me,
                        bottomInset = bottomInset,
                        onBack = { pop() },
                        onOpenViewer = { v, i -> push(ShortsRoute.Viewer(v, i)) },
                        onOpenSound = { push(ShortsRoute.Sound(it)) },
                        onOpenLibrary = { push(ShortsRoute.Library) },
                        onCreate = { push(ShortsRoute.Create(null)) }
                    )

                    is ShortsRoute.Sound -> SoundDetailScreen(
                        soundId = route.id,
                        vm = vm,
                        me = me,
                        bottomInset = bottomInset,
                        onBack = { pop() },
                        onUse = { push(ShortsRoute.Create(it)) },
                        onOpenViewer = { v, i -> push(ShortsRoute.Viewer(v, i)) },
                        onOpenProfile = { push(ShortsRoute.Profile(it)) }
                    )

                    is ShortsRoute.Viewer -> Box(Modifier.fillMaxSize().background(Color.Black)) {
                        ShortsPager(
                            videos = route.videos,
                            startIndex = route.index,
                            vm = vm,
                            me = me,
                            screenActive = true,
                            bottomInset = bottomInset,
                            onOpenProfile = { push(ShortsRoute.Profile(it)) },
                            onOpenSound = { push(ShortsRoute.Sound(it)) }
                        )
                        Icon(
                            SolarIcons.get("back"), "Назад", tint = Color.White,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(10.dp)
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable { pop() }
                                .padding(10.dp)
                        )
                    }

                    is ShortsRoute.Create -> ShortsCreateScreen(
                        vm = vm,
                        me = me,
                        initialSound = route.sound,
                        onClose = { pop() },
                        onPublished = {
                            pop()
                            // Освежаем ленту, чтобы новое видео попало в рекомендации и профиль
                            vm.loadForYou(me, refresh = true)
                        }
                    )

                    ShortsRoute.Library -> SoundLibraryScreen(
                        vm = vm,
                        me = me,
                        pickMode = false,
                        bottomInset = bottomInset,
                        onPick = {},
                        onOpenSound = { push(ShortsRoute.Sound(it)) },
                        onBack = { pop() }
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedTab(title: String, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(
        if (selected) Color.White else Color.White.copy(alpha = 0.6f), label = "tabColor"
    )
    Column(
        Modifier.clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = color, fontSize = 17.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.width(24.dp).height(3.dp).clip(CircleShape)
                .background(if (selected) Color.White else Color.Transparent)
        )
    }
}

/** Значок профиля справа сверху: мини-аватарка, по нажатию — мой профиль в Shorts. */
@Composable
private fun MyProfileButton(me: String, onClick: () -> Unit) {
    val avatar = rememberUserAvatar(me)
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (avatar != null) {
            AsyncImage(
                model = CloudinaryUrls.avatar(avatar, 112),
                contentDescription = "Мой профиль",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(SolarIcons.get("user"), "Мой профиль", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

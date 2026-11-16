@file:OptIn(ExperimentalMaterial3Api::class)

package com.RIKAPLAY.zhirpem_app

import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

import android.media.MediaRecorder
import android.net.Uri
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.QualitySelector
import androidx.camera.video.Quality
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.Recording
import androidx.camera.core.CameraSelector
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import android.os.Build
import androidx.lifecycle.viewmodel.compose.viewModel
import com.RIKAPLAY.zhirpem_app.webrtc.GlobalCallViewModel
import com.RIKAPLAY.zhirpem_app.webrtc.CallScreen
import com.RIKAPLAY.zhirpem_app.webrtc.IncomingCallOverlay
import com.RIKAPLAY.zhirpem_app.webrtc.CallService
import com.RIKAPLAY.zhirpem_app.webrtc.peerOfChat
import com.RIKAPLAY.zhirpem_app.webrtc.CallViewModel
import com.RIKAPLAY.zhirpem_app.webrtc.FirestoreSignalingClient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    chatId: String, 
    onBack: () -> Unit, 
    onNavigateToPost: (String) -> Unit, 
    onNavigateToProfile: (String) -> Unit,
    onOpenCamera: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sharedPrefs = remember { context.getSharedPreferences("user_session", Context.MODE_PRIVATE) }
    val myUsername = sharedPrefs.getString("username", "user_${System.currentTimeMillis()}") ?: "user_${System.currentTimeMillis()}"
    // Собеседник: для чатов «userA_userB» берётся из названия, для старых чатов со случайным ID уточняется по участникам
    var peerId by remember(chatId) { mutableStateOf(ChatPeerCache.peerOf(chatId, myUsername)) }

    var messages by remember { mutableStateOf(listOf<Message>()) }
    var chatData by remember { mutableStateOf<Chat?>(null) }
    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var selectedMessageForMenu by remember { mutableStateOf<Message?>(null) }
    var messageToForward by remember { mutableStateOf<Message?>(null) }
    var isForwardingDialogOpen by remember { mutableStateOf(false) }
    var isProfileOverlayOpen by remember { mutableStateOf(false) }
    var isSettingsDialogOpen by remember { mutableStateOf(false) }
    var showCallTypeDialog by remember { mutableStateOf(false) }

    var userWarningCount by remember { mutableIntStateOf(0) }
    var activeWarningBannerText by remember { mutableStateOf<String?>(null) }
    var isBannerDismissed by remember { mutableStateOf(false) }
    var isUserRestricted by remember { mutableStateOf(false) }

    var myUser by remember { mutableStateOf<User?>(null) }
    var peerUser by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(myUsername) {
        db.collection("users").document(myUsername).get().addOnSuccessListener {
            myUser = it.toObject(User::class.java)
        }
    }
    
    LaunchedEffect(peerId) {
        if (peerId.isNotEmpty()) {
            db.collection("users").document(peerId).get().addOnSuccessListener {
                peerUser = it.toObject(User::class.java)
            }
        }
    }

    val warnBag = rememberListenerBag()
    LaunchedEffect(myUsername) {
        warnBag.clear()
        warnBag.listen(db.collection("users").document(myUsername)) { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val count = (snapshot.getLong("warningsCount") ?: 0L).toInt()
                val weekStart = snapshot.getLong("warningsWeekStart") ?: System.currentTimeMillis()
                val bannedUntil = snapshot.getLong("bannedUntil")

                val status = CommunityRulesManager.calculateWeeklyStatus(count, weekStart, bannedUntil)
                userWarningCount = status.count
                isUserRestricted = status.isAccessRestricted

                if (status.count > 0) {
                    activeWarningBannerText = CommunityRulesManager.getWarningBannerText(status.count)
                } else {
                    activeWarningBannerText = null
                }
            }
        }
    }

    val hasCommunicatedBefore = remember(messages, myUsername) {
        messages.any { it.senderId == myUsername }
    }

    fun canSendAction(): Boolean {
        if (isUserRestricted || userWarningCount >= 3) {
            Toast.makeText(context, "Вам ограничен доступ в Жирпем из-за нарушений правил сообщества.", Toast.LENGTH_LONG).show()
            return false
        }
        if (userWarningCount >= 2 && !hasCommunicatedBefore) {
            Toast.makeText(context, "Вам ограничена возможность писать людям, с которыми вы еще не общались (2 предупреждения за неделю)", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    val globalCallViewModel: GlobalCallViewModel = viewModel()
    val isCallActive by globalCallViewModel.isCallActive.collectAsState()

    var peerName by remember { mutableStateOf("Чат") }
    var peerAvatarUrl by remember { mutableStateOf<String?>(null) }
    
    var isMenuExpanded by remember { mutableStateOf(value = false) }
    var isSendingMedia by remember { mutableStateOf(value = false) }
    var isPeerTyping by remember { mutableStateOf(value = false) }
    
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val rtdb = FirebaseDatabase.getInstance()

    var messageLimit by remember { mutableLongStateOf(10L) }

    DisposableEffect(chatId) {
        val chatRegistration = db.collection("chats").document(chatId).addSnapshotListener { snapshot, _ ->
            chatData = snapshot?.toObject(Chat::class.java)
            chatData?.participants?.let { participants ->
                ChatPeerCache.remember(chatId, myUsername, participants)
                peerId = ChatPeerCache.peerOf(chatId, myUsername)
            }
        }

        val typingRef = if (peerId.isNotEmpty()) rtdb.getReference("chats/$chatId/typing/$peerId") else null
        val typingListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                isPeerTyping = snapshot.getValue(Boolean::class.java) ?: false
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
            }
        }
        typingRef?.addValueEventListener(typingListener)

        onDispose {
            chatRegistration.remove()
            typingRef?.removeEventListener(typingListener)
        }
    }

    LaunchedEffect(messages) {
        val unreadMessages = messages.filter { it.senderId != myUsername && !it.isRead }
        if (unreadMessages.isNotEmpty()) {
            unreadMessages.forEach { msg ->
                db.collection("chats").document(chatId).collection("messages").document(msg.id)
                    .update("isRead", true)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    // Звонок можно начинать только после выдачи микрофона (иначе сервис звонка падал на Android 14+)
    var pendingCallType by remember { mutableStateOf<String?>(null) }
    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val type = pendingCallType
        pendingCallType = null
        if (type != null) {
            if (hasMicPermission(context)) {
                CallService.start(context, video = type == "VIDEO")
                globalCallViewModel.startOutgoingCall(chatId, type)
            } else {
                android.widget.Toast.makeText(context, "Для звонка нужен доступ к микрофону", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    val startCall: (String) -> Unit = { type ->
        if (hasAllCallPermissions(context, type)) {
            CallService.start(context, video = type == "VIDEO")
            globalCallViewModel.startOutgoingCall(chatId, type)
        } else {
            pendingCallType = type
            callPermissionLauncher.launch(callPermissionsFor(type))
        }
    }

    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }

    // Поиск по сообщениям
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchIndex by remember { mutableIntStateOf(0) }
    val searchMatches = remember(messages, searchQuery) {
        val q = searchQuery.trim()
        if (q.length < 2) emptyList() else messages.filter { it.text.contains(q, ignoreCase = true) }.map { it.id }
    }
    val searchMatchSet = remember(searchMatches) { searchMatches.toHashSet() }
    val currentMatchId = searchMatches.getOrNull(searchIndex)
    fun closeSearch() {
        isSearchOpen = false
        searchQuery = ""
        searchIndex = 0
    }
    androidx.activity.compose.BackHandler(enabled = isSearchOpen) { closeSearch() }
    // Чтобы искать глубже, при открытии поиска подгружаем больше истории
    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen && messageLimit < 500L) messageLimit = 500L
    }
    // Новый запрос начинается с самого свежего совпадения
    LaunchedEffect(searchQuery) { searchIndex = (searchMatches.size - 1).coerceAtLeast(0) }
    // Прокрутка к текущему совпадению
    LaunchedEffect(currentMatchId) {
        currentMatchId?.let { id ->
            val position = messages.indexOfFirst { it.id == id }
            if (position >= 0) listState.animateScrollToItem(position)
        }
    }

    // Исходящие медиа: пока отправляются, они видны в чате с кольцом прогресса,
    // а если отправка не удалась, остаются с кнопкой «повторить»
    val outbox = remember(chatId) { mutableStateListOf<PendingMedia>() }

    fun startPendingUpload(item: PendingMedia) {
        item.state = PendingState.UPLOADING
        item.progress = 0f
        item.errorMessage = null
        ChatRepository.uploadMediaToCloudinary(
            context = context,
            fileUri = item.uri,
            messageType = item.kind,
            chatId = chatId,
            currentUserId = myUsername,
            senderName = sharedPrefs.getString("name", "Аноним") ?: "Аноним",
            senderAvatar = sharedPrefs.getString("avatarUrl", "") ?: "",
            durationMs = item.durationMs,
            audioPeaks = item.peaks,
            onRequestId = { item.requestId = it },
            onProgress = { item.progress = it },
            onFailure = { reason -> item.errorMessage = reason },
            onComplete = { ok ->
                if (ok) {
                    outbox.remove(item)
                    MessageSounds.play(context, MessageSounds.Sound.SEND)
                } else if (item in outbox) {
                    item.state = PendingState.FAILED
                }
            }
        )
    }

    fun startMediaUpload(fileUri: Uri, messageType: String, durationMs: Long = 0L, peaks: List<Int> = emptyList()) {
        val item = PendingMedia(
            localId = java.util.UUID.randomUUID().toString(),
            kind = messageType,
            uri = fileUri,
            durationMs = durationMs,
            peaks = peaks
        )
        outbox.add(item)
        startPendingUpload(item)
    }

    fun cancelPending(item: PendingMedia) {
        item.requestId?.let { ChatRepository.cancelUpload(it) }
        outbox.remove(item)
    }

    // Какие сообщения уже были в чате: анимируем появление только новых
    val knownMessageIds = remember(chatId) { mutableSetOf<String>() }
    val newestKnownTimestamp = remember(chatId) { longArrayOf(0L) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var videoFile by remember { mutableStateOf<File?>(null) }
    var isRecordingPaused by remember { mutableStateOf(false) }
    var videoRecordStartedAt by remember { mutableLongStateOf(0L) }
    val videoSendFlag = remember { booleanArrayOf(false) }
    val videoDurationMs = remember { longArrayOf(0L) }
    var currentCameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }
    val previewView = remember { androidx.camera.view.PreviewView(context) }

    LaunchedEffect(Unit) {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.LOWEST))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaRecorder?.release()
            activeRecording?.stop()
        }
    }

    val mediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            if (!canSendAction()) return@let

            val isPeerVerifiedOnly = peerUser?.isOnlyVerifiedMessages ?: false
            val isMeVerified = (myUser?.blueBadge == true) || (myUser?.yellowBadge == true)
            if (isPeerVerifiedOnly && !isMeVerified) {
                Toast.makeText(context, "Пользователь ограничил получение сообщений", Toast.LENGTH_SHORT).show()
                return@let
            }

            isSendingMedia = true
            val isVideo = it.toString().contains("video")
            startMediaUpload(it, if (isVideo) "video_square" else "image")
            isSendingMedia = false 
        }
    }

    val gifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            if (!canSendAction()) return@let

            val isPeerVerifiedOnly = peerUser?.isOnlyVerifiedMessages ?: false
            val isMeVerified = (myUser?.blueBadge == true) || (myUser?.yellowBadge == true)
            if (isPeerVerifiedOnly && !isMeVerified) {
                Toast.makeText(context, "Пользователь ограничил получение сообщений", Toast.LENGTH_SHORT).show()
                return@let
            }

            isSendingMedia = true
            startMediaUpload(it, "image")
            isSendingMedia = false
        }
    }

    val messagesBag = rememberListenerBag()
    // Новые входящие сообщения: звук. Затем запоминаем, какие сообщения уже показаны
    LaunchedEffect(messages) {
        // Первая порция сообщений при открытии чата — это история, а не новые сообщения
        if (knownMessageIds.isNotEmpty()) {
            val fresh = messages.filter {
                it.id !in knownMessageIds && it.senderId != myUsername && it.timestamp > newestKnownTimestamp[0]
            }
            if (fresh.isNotEmpty()) MessageSounds.play(context, MessageSounds.Sound.RECEIVE)
        }
        messages.forEach { knownMessageIds.add(it.id) }
        newestKnownTimestamp[0] = maxOf(newestKnownTimestamp[0], messages.maxOfOrNull { it.timestamp } ?: 0L)
    }

    // Новое исходящее медиа: прокручиваем чат вниз к нему
    LaunchedEffect(outbox.size) {
        if (outbox.isNotEmpty()) {
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) listState.animateScrollToItem(total - 1)
        }
    }

    LaunchedEffect(chatId, messageLimit) {
        // При смене лимита старая подписка отключается (раньше они копились и по очереди
        // подменяли список, из-за чего чат дёргался и не давал листать)
        messagesBag.clear()
        val peerId = ChatPeerCache.peerOf(chatId, myUsername)
        if (peerId.isNotEmpty()) {
            db.collection("users").document(peerId).get().addOnSuccessListener {
                peerName = it.getString("name") ?: peerId
                peerAvatarUrl = it.getString("avatarUrl")
            }
        }

        messagesBag.listen(db.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp")
            .limitToLast(messageLimit)) { snapshot, _ ->
                snapshot?.let {
                    val now = System.currentTimeMillis()
                    messages = it.documents.mapNotNull { doc ->
                        val msg = doc.toObject(Message::class.java)?.copy(id = doc.id)
                        if (msg?.expiresAt != null && msg.expiresAt < now) {
                            null
                        } else msg
                    }
                }
            }
    }

    val animationsEnabled = LocalAnimationsEnabled.current
    // Прокручиваем вниз только при открытии чата и при новом сообщении (если вы внизу или оно ваше).
    // Подгрузка старых сообщений и обновления существующих больше не сбрасывают прокрутку.
    var initialScrollDone by remember(chatId) { mutableStateOf(false) }
    LaunchedEffect(messages.lastOrNull()?.id) {
        val last = messages.lastOrNull() ?: return@LaunchedEffect
        val lastItemIndex = messages.size // нулевой элемент списка — кнопка «Загрузить предыдущие»
        if (!initialScrollDone) {
            listState.scrollToItem(lastItemIndex)
            initialScrollDone = true
            return@LaunchedEffect
        }
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val nearBottom = lastVisible >= lastItemIndex - 3
        if (last.senderId == myUsername || nearBottom) {
            if (animationsEnabled) listState.animateScrollToItem(lastItemIndex)
            else listState.scrollToItem(lastItemIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = isSearchOpen,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 }) togetherWith
                                (fadeOut(tween(120)) + slideOutVertically(tween(200)) { -it / 3 })
                        },
                        label = "ChatTitleSwitch"
                    ) { searching ->
                    if (searching) {
                        ChatSearchField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            resultText = when {
                                searchQuery.trim().length < 2 -> ""
                                searchMatches.isEmpty() -> "нет"
                                else -> "${searchIndex + 1} из ${searchMatches.size}"
                            }
                        )
                    } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isProfileOverlayOpen = true }
                    ) {
                        Box {
                            if (!peerAvatarUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = peerAvatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(peerName.take(1).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            PresenceIndicator(
                                username = peerId,
                                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp),
                                size = 10.dp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(peerName, fontWeight = FontWeight.Bold)
                            if (isPeerTyping) {
                                Text(
                                    "печатает...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                PresenceText(username = peerId)
                            }
                        }
                    }
                    }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (isSearchOpen) closeSearch() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (isSearchOpen) {
                        // Предыдущее (выше по истории) и следующее совпадение
                        IconButton(
                            onClick = { if (searchMatches.isNotEmpty()) searchIndex = (searchIndex - 1 + searchMatches.size) % searchMatches.size },
                            enabled = searchMatches.size > 1
                        ) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Выше") }
                        IconButton(
                            onClick = { if (searchMatches.isNotEmpty()) searchIndex = (searchIndex + 1) % searchMatches.size },
                            enabled = searchMatches.size > 1
                        ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Ниже") }
                    } else {
                        IconButton(onClick = { isSearchOpen = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Поиск по сообщениям")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Обои чата на самый нижний слой
            if (!chatData?.wallpaperUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = chatData?.wallpaperUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            FallingLeavesBackground()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if ((selectedMessageForMenu != null || isProfileOverlayOpen) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(15.dp)
                        } else Modifier
                    )
            ) {
                if (activeWarningBannerText != null && !isBannerDismissed) {
                    CommunityWarningBanner(
                        text = activeWarningBannerText!!,
                        onDismiss = { isBannerDismissed = true }
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    item {
                        if (messages.size.toLong() >= messageLimit) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                TextButton(onClick = { messageLimit += 10 }) {
                                    Text("Загрузить предыдущие сообщения", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
                      Column(
                        modifier = Modifier
                            .animateItem()
                            .then(
                                if (searchQuery.trim().length >= 2 && message.id in searchMatchSet) {
                                    Modifier.background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = if (message.id == currentMatchId) 0.22f else 0.08f),
                                        RoundedCornerShape(14.dp)
                                    )
                                } else Modifier
                            )
                      ) {
                        val isMyMessage = message.senderId == myUsername
                        // Новое сообщение появляется с анимацией (при открытии чата старые не анимируются)
                        val shouldAnimate = remember(message.id) {
                            knownMessageIds.isNotEmpty() && message.id !in knownMessageIds && message.timestamp > newestKnownTimestamp[0]
                        }
                        
                        if (index == 0) {
                            DateHeader(timestamp = message.timestamp)
                        } else {
                            val prevMessage = messages[index - 1]
                            val cal1 = Calendar.getInstance().apply { timeInMillis = prevMessage.timestamp }
                            val cal2 = Calendar.getInstance().apply { timeInMillis = message.timestamp }
                            val isDifferentDay = cal1[Calendar.YEAR] != cal2[Calendar.YEAR] || 
                                               cal1[Calendar.DAY_OF_YEAR] != cal2[Calendar.DAY_OF_YEAR]
                            if (isDifferentDay) {
                                DateHeader(timestamp = message.timestamp)
                            }
                        }
                        
                        AnimatedMessageEntry(isMine = isMyMessage, animate = shouldAnimate) {
                            MessageBubble(
                                chatId = chatId,
                                message = message,
                                isMyMessage = isMyMessage,
                                theme = chatData?.theme ?: "DEFAULT",
                                onNavigateToPost = onNavigateToPost,
                                onReply = { replyingToMessage = it },
                                onReplyClick = { replyId ->
                                    val targetIndex = messages.indexOfFirst { it.id == replyId }
                                    if (targetIndex != -1) {
                                        scope.launch {
                                            listState.animateScrollToItem(targetIndex + 1)
                                        }
                                    }
                                },
                                onLongClick = { selectedMessageForMenu = it },
                                onToggleReaction = { m, emoji -> toggleReaction(db, chatId, m, myUsername, emoji) }
                            )
                        }
                      }
                    }

                    // Сообщения, которые ещё отправляются или не отправились
                    items(outbox, key = { "pending_" + it.localId }) { item ->
                        Box(modifier = Modifier.animateItem()) {
                            AnimatedMessageEntry(isMine = true, animate = true) {
                                PendingMediaBubble(
                                    item = item,
                                    onRetry = { startPendingUpload(item) },
                                    onCancel = { cancelPending(item) }
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    if (replyingToMessage != null) {
                        ReplyPanel(
                            message = replyingToMessage!!,
                            onCancel = { replyingToMessage = null }
                        )
                    }

                    Box {
                        ChatInputBar(
                            onSendText = { rawText ->
                                val text = rawText.trim()
                                if (text.isEmpty()) return@ChatInputBar

                                if (!canSendAction()) return@ChatInputBar

                                val isPeerVerifiedOnly = peerUser?.isOnlyVerifiedMessages ?: false
                                val isMeVerified = (myUser?.blueBadge == true) || (myUser?.yellowBadge == true)

                                if (isPeerVerifiedOnly && !isMeVerified) {
                                    Toast.makeText(context, "Пользователь ограничил получение сообщений", Toast.LENGTH_SHORT).show()
                                    return@ChatInputBar
                                }

                                val hasViolation = CommunityRulesManager.containsProhibitedContent(text)
                                val finalText = if (hasViolation) CommunityRulesManager.censorText(text) else text

                                if (hasViolation) {
                                    CommunityRulesManager.processViolation(
                                        db = db,
                                        userId = myUsername,
                                        chatId = chatId,
                                        messageText = text,
                                        onResult = { newCount, warningText ->
                                            userWarningCount = newCount
                                            activeWarningBannerText = warningText
                                            isBannerDismissed = false
                                            if (newCount >= 3) {
                                                isUserRestricted = true
                                            }
                                        }
                                    )
                                }

                                sendMessage(
                                    db = db,
                                    chatId = chatId,
                                    senderId = myUsername,
                                    text = finalText,
                                    replyToId = replyingToMessage?.id,
                                    replyToText = replyingToMessage?.let {
                                        it.text.ifEmpty { "Медиафайл" }
                                    },
                                    senderName = sharedPrefs.getString("name", "Аноним") ?: "Аноним",
                                    senderAvatar = sharedPrefs.getString("avatarUrl", "") ?: "",
                                    expiresAt = chatData?.disappearingDuration?.let { if (it > 0) System.currentTimeMillis() + it else null }
                                )
                                replyingToMessage = null
                                MessageSounds.play(context, MessageSounds.Sound.SEND)
                            },
                            onTyping = { isTyping ->
                                rtdb.getReference("chats/$chatId/typing/$myUsername").setValue(isTyping)
                            },
                            audioAmplitude = {
                                try { mediaRecorder?.maxAmplitude ?: 0 } catch (e: Exception) { 0 }
                            },
                            onStartAudioRecord = start@{
                                if (!canSendAction()) return@start false

                                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    try {
                                        val file = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
                                        audioFile = file
                                        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            MediaRecorder(context)
                                        } else {
                                            @Suppress("DEPRECATION")
                                            MediaRecorder()
                                        }.apply {
                                            setAudioSource(MediaRecorder.AudioSource.MIC)
                                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                            setOutputFile(file.absolutePath)
                                            prepare()
                                            start()
                                        }
                                        true
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        try { mediaRecorder?.release() } catch (_: Exception) {}
                                        mediaRecorder = null
                                        false
                                    }
                                } else {
                                    permissionLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
                                    false
                                }
                            },
                            onStopAudioRecord = { shouldSend, peaks, durationMs ->
                                val file = audioFile
                                var recordedOk = true
                                try {
                                    mediaRecorder?.stop()
                                } catch (e: RuntimeException) {
                                    recordedOk = false // слишком короткая запись
                                }
                                try { mediaRecorder?.release() } catch (_: Exception) {}
                                mediaRecorder = null
                                audioFile = null

                                if (shouldSend && recordedOk && file != null && file.exists()) {
                                    val isPeerVerifiedOnly = peerUser?.isOnlyVerifiedMessages ?: false
                                    val isMeVerified = (myUser?.blueBadge == true) || (myUser?.yellowBadge == true)
                                    if (isPeerVerifiedOnly && !isMeVerified) {
                                        Toast.makeText(context, "Пользователь ограничил получение сообщений", Toast.LENGTH_SHORT).show()
                                        file.delete()
                                    } else {
                                        startMediaUpload(Uri.fromFile(file), "voice", durationMs, peaks)
                                    }
                                } else {
                                    file?.delete()
                                }
                            },
                            onStartVideoRecord = start@{
                                if (!canSendAction()) return@start false

                                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {

                                    try {
                                        val file = File(context.cacheDir, "video_${System.currentTimeMillis()}.mp4")
                                        videoFile = file
                                        videoSendFlag[0] = false
                                        videoDurationMs[0] = 0L
                                        val outputOptions = FileOutputOptions.Builder(file).build()

                                        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                                        cameraProvider.unbindAll()

                                        val preview = androidx.camera.core.Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }

                                        cameraProvider.bindToLifecycle(lifecycleOwner, currentCameraSelector, preview, videoCapture)

                                        activeRecording = videoCapture?.output
                                            ?.prepareRecording(context, outputOptions)
                                            ?.withAudioEnabled()
                                            ?.start(ContextCompat.getMainExecutor(context)) { event ->
                                                if (event is VideoRecordEvent.Finalize) {
                                                    // Запись завершена: освобождаем камеру и только теперь отправляем готовый файл
                                                    try { ProcessCameraProvider.getInstance(context).get().unbindAll() } catch (_: Exception) {}
                                                    if (videoSendFlag[0] && !event.hasError() && file.exists() && file.length() > 0) {
                                                        val isPeerVerifiedOnly = peerUser?.isOnlyVerifiedMessages ?: false
                                                        val isMeVerified = (myUser?.blueBadge == true) || (myUser?.yellowBadge == true)
                                                        if (isPeerVerifiedOnly && !isMeVerified) {
                                                            Toast.makeText(context, "Пользователь ограничил получение сообщений", Toast.LENGTH_SHORT).show()
                                                            file.delete()
                                                        } else {
                                                            startMediaUpload(Uri.fromFile(file), "video_square", videoDurationMs[0])
                                                        }
                                                    } else {
                                                        file.delete()
                                                    }
                                                }
                                            }
                                        videoRecordStartedAt = System.currentTimeMillis()
                                        activeRecording != null
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        false
                                    }
                                } else {
                                    permissionLauncher.launch(arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO))
                                    false
                                }
                            },
                            onStopVideoRecord = { shouldSend, durationMs ->
                                videoSendFlag[0] = shouldSend
                                videoDurationMs[0] = durationMs
                                activeRecording?.stop()
                                activeRecording = null
                                isRecordingPaused = false
                                currentCameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                            },
                            onMoreClick = { isMenuExpanded = true }
                        )

                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Камера")
                                    }
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    if (canSendAction()) {
                                        onOpenCamera()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Фото или Видео")
                                    }
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    if (canSendAction()) {
                                        mediaLauncher.launch("*/*")
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Gif, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("GIF-анимация")
                                    }
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    if (canSendAction()) {
                                        gifLauncher.launch("image/gif")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (selectedMessageForMenu != null) {
                MessageContextMenuOverlay(
                    message = selectedMessageForMenu!!,
                    isMyMessage = selectedMessageForMenu!!.senderId == myUsername,
                    myReaction = selectedMessageForMenu!!.reactions[myUsername],
                    onDismiss = { selectedMessageForMenu = null },
                    onReaction = { emoji ->
                        toggleReaction(db, chatId, selectedMessageForMenu!!, myUsername, emoji)
                        selectedMessageForMenu = null
                    },
                    onReply = {
                        replyingToMessage = selectedMessageForMenu
                        selectedMessageForMenu = null
                    },
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = android.content.ClipData.newPlainText("сообщение", selectedMessageForMenu!!.text)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                        selectedMessageForMenu = null
                    },
                    onPin = {
                        Toast.makeText(context, "Закреплено", Toast.LENGTH_SHORT).show()
                        selectedMessageForMenu = null
                    },
                    onForward = {
                        messageToForward = selectedMessageForMenu
                        isForwardingDialogOpen = true
                        selectedMessageForMenu = null
                    },
                    onReport = {
                        val msg = selectedMessageForMenu
                        if (msg != null) {
                            val now = System.currentTimeMillis()
                            val reportId = "rep_${msg.senderId}_${now}"

                            db.collection("chats").document(chatId).collection("messages")
                                .limit(20)
                                .get()
                                .addOnSuccessListener { msgSnap ->
                                    val chatHistory = msgSnap.documents.mapNotNull { m ->
                                        val sender = m.getString("senderId") ?: ""
                                        val txt = m.getString("text") ?: ""
                                        if (txt.isNotEmpty()) "$sender: $txt" else null
                                    }

                                    val reportData = hashMapOf(
                                        "id" to reportId,
                                        "userId" to msg.senderId,
                                        "chatId" to chatId,
                                        "triggerMessage" to msg.text.ifEmpty { "Медиафайл" },
                                        "timestamp" to now,
                                        "warningsCount" to 1,
                                        "status" to "PENDING",
                                        "chatHistory" to chatHistory
                                    )
                                    db.collection("moderation_reports").document(reportId).set(reportData)
                                }
                                .addOnFailureListener {
                                    val reportData = hashMapOf(
                                        "id" to reportId,
                                        "userId" to msg.senderId,
                                        "chatId" to chatId,
                                        "triggerMessage" to msg.text.ifEmpty { "Медиафайл" },
                                        "timestamp" to now,
                                        "status" to "PENDING"
                                    )
                                    db.collection("moderation_reports").document(reportId).set(reportData)
                                }

                            Toast.makeText(context, "Жалоба отправлена на рассмотрение модераторам", Toast.LENGTH_SHORT).show()
                        }
                        selectedMessageForMenu = null
                    },
                    onDelete = {
                        db.collection("chats").document(chatId).collection("messages").document(selectedMessageForMenu!!.id).delete()
                        selectedMessageForMenu = null
                    },
                    onSelect = {
                        Toast.makeText(context, "Выбрано", Toast.LENGTH_SHORT).show()
                        selectedMessageForMenu = null
                    }
                )
            }

            if (isForwardingDialogOpen && messageToForward != null) {
                ForwardMessageDialog(
                    message = messageToForward!!,
                    onDismiss = {
                        isForwardingDialogOpen = false
                        messageToForward = null
                    },
                    onForward = { targetChatId, hideSender ->
                        if (canSendAction()) {
                            forwardMessage(
                                db = db,
                                targetChatId = targetChatId,
                                message = messageToForward!!,
                                senderId = myUsername,
                                senderName = sharedPrefs.getString("name", "Аноним") ?: "Аноним",
                                senderAvatar = sharedPrefs.getString("avatarUrl", "") ?: "",
                                hideSender = hideSender
                            )
                            isForwardingDialogOpen = false
                            messageToForward = null
                            Toast.makeText(context, "Сообщение переслано", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            if (isProfileOverlayOpen) {
                Dialog(
                    onDismissRequest = { isProfileOverlayOpen = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable { isProfileOverlayOpen = false },
                        contentAlignment = Alignment.Center
                    ) {
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { visible = true }

                        AnimatedVisibility(
                            visible = visible,
                            enter = scaleIn(initialScale = 0.8f) + fadeIn(),
                            exit = scaleOut(targetScale = 0.8f) + fadeOut()
                        ) {
                            ChatProfileOverlay(
                                peerId = peerId,
                                peerName = peerName,
                                peerAvatarUrl = peerAvatarUrl,
                                onDismiss = { isProfileOverlayOpen = false },
                                onNavigateToProfile = { 
                                    onNavigateToProfile(peerId)
                                    isProfileOverlayOpen = false
                                },
                                onOpenSettings = {
                                    isSettingsDialogOpen = true
                                },
                                onStartCall = {
                                    isProfileOverlayOpen = false
                                    showCallTypeDialog = true
                                }
                            )
                        }
                    }
                }
            }

            if (showCallTypeDialog) {
                Dialog(
                    onDismissRequest = { showCallTypeDialog = false }
                ) {
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { visible = true }

                    AnimatedVisibility(
                        visible = visible,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        Card(
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Тип звонка", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Text("Как вы хотите позвонить $peerName?", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                
                                Spacer(Modifier.height(24.dp))
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = {
                                            showCallTypeDialog = false
                                            startCall("AUDIO")
                                        },
                                        modifier = Modifier.weight(1f).height(100.dp).bounceClick(),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.Call, null, modifier = Modifier.size(32.dp))
                                            Spacer(Modifier.height(8.dp))
                                            Text("Аудио")
                                        }
                                    }
                                    
                                    Button(
                                        onClick = {
                                            showCallTypeDialog = false
                                            startCall("VIDEO")
                                        },
                                        modifier = Modifier.weight(1f).height(100.dp).bounceClick(),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.Videocam, null, modifier = Modifier.size(32.dp))
                                            Spacer(Modifier.height(8.dp))
                                            Text("Видео")
                                        }
                                    }
                                }
                                
                                TextButton(onClick = { showCallTypeDialog = false }, modifier = Modifier.padding(top = 12.dp).bounceClick()) {
                                    Text("Отмена")
                                }
                            }
                        }
                    }
                }
            }

            if (isSettingsDialogOpen) {
                ChatSettingsDialog(
                    chatId = chatId,
                    onDismiss = { isSettingsDialogOpen = false }
                )
            }

            if (activeRecording != null && videoCapture != null) {
                VideoRecordOverlay(
                    previewView = previewView,
                    startedAtMs = videoRecordStartedAt,
                    isPaused = isRecordingPaused,
                    onPauseToggle = {
                        if (isRecordingPaused) {
                            activeRecording?.resume()
                        } else {
                            activeRecording?.pause()
                        }
                        isRecordingPaused = !isRecordingPaused
                    },
                    onFlipCamera = {
                        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        } else {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        }
                        
                        try {
                            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                            cameraProvider.unbindAll()
                            val preview = androidx.camera.core.Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            cameraProvider.bindToLifecycle(lifecycleOwner, currentCameraSelector, preview, videoCapture)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CommunityWarningBanner(
    text: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFD32F2F),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Report,
                        contentDescription = "Предупреждение",
                        tint = Color.Yellow,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Нарушение правил сообщества Жирпем",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = Color.White.copy(alpha = 0.95f)
            )
        }
    }
}

@Composable
fun MessageContextMenuOverlay(
    message: Message,
    isMyMessage: Boolean,
    myReaction: String? = null,
    onDismiss: () -> Unit,
    onReaction: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onPin: () -> Unit,
    onForward: () -> Unit,
    onReport: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit
) {
    val emojis = listOf("❤️", "👍", "👎", "🔥", "🥰", "👏", "😁", "😂", "😮", "😢", "🙏")
    
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .padding(16.dp),
                horizontalAlignment = if (isMyMessage) Alignment.End else Alignment.Start
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        emojis.forEachIndexed { index, emoji ->
                            // Смайлики по очереди «выпрыгивают»; выбранная реакция подсвечена, повторное нажатие снимает её
                            val pop = remember { Animatable(0f) }
                            LaunchedEffect(Unit) {
                                delay(index * 30L)
                                pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
                            }
                            Text(
                                text = emoji,
                                modifier = Modifier
                                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f) }
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (emoji == myReaction) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                        else Color.Transparent
                                    )
                                    .clickable { onReaction(emoji) }
                                    .padding(4.dp),
                                fontSize = 24.sp
                            )
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    modifier = Modifier.width(220.dp)
                ) {
                    Column {
                        ContextMenuItem("Ответить", Icons.AutoMirrored.Filled.Reply, onReply)
                        ContextMenuItem("Скопировать", Icons.Default.ContentCopy, onCopy)
                        ContextMenuItem("Закрепить", Icons.Default.PushPin, onPin)
                        ContextMenuItem("Переслать", Icons.AutoMirrored.Filled.Forward, onForward)
                        ContextMenuItem("Пожаловаться", Icons.Default.Report, onReport, isDestructive = true)
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        ContextMenuItem("Удалить", Icons.Default.Delete, onDelete, isDestructive = true)
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        ContextMenuItem("Выбрать", Icons.Default.CheckCircleOutline, onSelect)
                    }
                }
            }
        }
    }
}

@Composable
fun ContextMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp
        )
    }
}

@Composable
fun ChatProfileOverlay(
    peerId: String,
    peerName: String,
    peerAvatarUrl: String?,
    onDismiss: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartCall: () -> Unit
) {
    var peerReputation by remember { mutableIntStateOf(0) }
    val peerReputationBag = rememberListenerBag()
    LaunchedEffect(peerId) {
        peerReputationBag.clear()
        if (peerId.isNotEmpty()) {
            peerReputationBag.listen(com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(peerId)) { doc, _ ->
                    if (doc != null && doc.exists()) {
                        peerReputation = (doc.getLong("reputation") ?: 0L).toInt().coerceIn(-10, 10)
                    }
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                .bounceClick()
                .clickable { onNavigateToProfile() },
            contentAlignment = Alignment.Center
        ) {
                    if (!peerAvatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = peerAvatarUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = peerName.take(1).uppercase(),
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = peerName,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clickable { onNavigateToProfile() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    UserRatingBadge(reputation = peerReputation)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CircleActionButton(
                        icon = Icons.Default.Person,
                        label = "Профиль",
                        onClick = onNavigateToProfile
                    )
                    CircleActionButton(
                        icon = Icons.Default.Call,
                        label = "Звонок",
                        onClick = onStartCall
                    )
                    CircleActionButton(
                        icon = Icons.Default.Settings,
                        label = "Настройки",
                        onClick = onOpenSettings
                    )
                }
            }
}

@Composable
fun CircleActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .bounceClick()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun ChatSettingsDialog(chatId: String, onDismiss: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    var chatData by remember { mutableStateOf<Chat?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    LaunchedEffect(chatId) {
        db.collection("chats").document(chatId).get().addOnSuccessListener {
            chatData = it.toObject(Chat::class.java)
        }
    }

    val wallpaperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            isUploading = true
            uploadImageToCloudinary(
                context = context,
                imageUri = it,
                mediaType = MediaType.IMAGE,
                cloudName = "dcwp4nm3e",
                uploadPreset = "ProfilePIC",
                onSuccess = { url ->
                    db.collection("chats").document(chatId).update("wallpaperUrl", url)
                    isUploading = false
                },
                onError = { isUploading = false }
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки чата") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("Цветовая тема", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val themes = listOf("DEFAULT", "BLUE", "GREEN", "PURPLE", "ORANGE")
                    themes.forEach { theme ->
                        val color = when(theme) {
                            "BLUE" -> Color(0xFF2196F3)
                            "GREEN" -> Color(0xFF4CAF50)
                            "PURPLE" -> Color(0xFF9C27B0)
                            "ORANGE" -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (chatData?.theme == theme) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable {
                                    db.collection("chats").document(chatId).update("theme", theme)
                                    chatData = chatData?.copy(theme = theme)
                                }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SettingsItem(
                    title = "Обои чата",
                    icon = Icons.Default.Image,
                    onClick = { wallpaperLauncher.launch("image/*") }
                )
                if (isUploading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text("Исчезающие сообщения", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                val durations = listOf(
                    0L to "Выкл",
                    3600000L to "1 час",
                    86400000L to "1 день",
                    604800000L to "1 неделя"
                )
                
                durations.forEach { (duration, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                db.collection("chats").document(chatId).update("disappearingDuration", duration)
                                chatData = chatData?.copy(disappearingDuration = duration)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = chatData?.disappearingDuration == duration, onClick = {
                            db.collection("chats").document(chatId).update("disappearingDuration", duration)
                            chatData = chatData?.copy(disappearingDuration = duration)
                        })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово") }
        }
    )
}

@Composable
fun SettingsItem(title: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ForwardMessageDialog(
    message: Message,
    onDismiss: () -> Unit,
    onForward: (String, Boolean) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val myUsername = context.getSharedPreferences("user_session", Context.MODE_PRIVATE).getString("username", "") ?: ""
    
    var chats by remember { mutableStateOf(listOf<Chat>()) }
    var hideSenderName by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        db.collection("chats")
            .whereArrayContains("participants", myUsername)
            .get()
            .addOnSuccessListener { snap ->
                chats = snap.documents.mapNotNull { it.toObject(Chat::class.java) }
                    .sortedByDescending { it.lastMessageTimestamp }
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переслать сообщение") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Скрыть имя отправителя", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = hideSenderName, onCheckedChange = { hideSenderName = it })
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск чата...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    val filteredChats = chats.filter { chat ->
                        val peerId = chat.participants.firstOrNull { it != myUsername } ?: ""
                        peerId.contains(searchQuery, ignoreCase = true)
                    }

                    items(filteredChats) { chat ->
                        val peerId = chat.participants.firstOrNull { it != myUsername } ?: ""
                        var peerName by remember { mutableStateOf(peerId) }

                        LaunchedEffect(peerId) {
                            db.collection("users").document(peerId).get().addOnSuccessListener { doc ->
                                peerName = doc.getString("name") ?: peerId
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onForward(chat.id, hideSenderName) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(peerName.take(1).uppercase(), fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(peerName, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

fun forwardMessage(
    db: FirebaseFirestore,
    targetChatId: String,
    message: Message,
    senderId: String,
    senderName: String,
    senderAvatar: String,
    hideSender: Boolean
) {
    val chatRef = db.collection("chats").document(targetChatId)
    val messageRef = chatRef.collection("messages").document()

    val forwardedMessage = hashMapOf(
        "senderId" to senderId,
        "text" to message.text,
        "timestamp" to System.currentTimeMillis(),
        "mediaUrl" to message.mediaUrl,
        "mediaType" to message.mediaType.name,
        "forwardedPostId" to message.forwardedPostId,
        "forwardedFrom" to if (hideSender) null else message.senderId,
        "replyToId" to null,
        "replyToText" to null,
        "isRead" to false
    )

    db.runTransaction { transaction ->
        transaction[messageRef] = forwardedMessage
        val lastMsgText = if (message.mediaUrl.isNotEmpty()) "📎 Переслано: Медиафайл" else "📎 Переслано: ${message.text}"
        transaction.update(chatRef, "lastMessage", lastMsgText)
        transaction.update(chatRef, "lastMessageTimestamp", System.currentTimeMillis())
    }
}

fun addReactionToMessage(db: FirebaseFirestore, chatId: String, messageId: String, userId: String, emoji: String) {
    db.collection("chats").document(chatId).collection("messages").document(messageId)
        .update("reactions.$userId", emoji)
}

fun sendMessage(
    db: FirebaseFirestore, 
    chatId: String, 
    senderId: String, 
    text: String, 
    mediaUrl: String = "", 
    mediaType: MediaType = MediaType.NONE,
    replyToId: String? = null,
    replyToText: String? = null,
    senderName: String = "",
    senderAvatar: String = "",
    expiresAt: Long? = null
) {
    val chatRef = db.collection("chats").document(chatId)
    val messageRef = chatRef.collection("messages").document()

    val message = hashMapOf(
        "senderId" to senderId,
        "text" to text,
        "timestamp" to System.currentTimeMillis(),
        "mediaUrl" to mediaUrl,
        "mediaType" to mediaType.name,
        "forwardedPostId" to null,
        "replyToId" to replyToId,
        "replyToText" to replyToText,
        "isRead" to false,
        "expiresAt" to expiresAt
    )

    db.runTransaction { transaction ->
        transaction[messageRef] = message
        val lastMsgText = if (mediaUrl.isNotEmpty()) "📎 Медиафайл" else text
        transaction.update(chatRef, "lastMessage", lastMsgText)
        transaction.update(chatRef, "lastMessageTimestamp", System.currentTimeMillis())
    }

    val peerId = ChatPeerCache.peerOf(chatId, senderId)
    if (peerId.isNotEmpty()) {
        sendNotification(
            db = db,
            senderId = senderId,
            senderName = senderName,
            senderAvatar = senderAvatar,
            receiverId = peerId,
            type = "MESSAGE",
            text = text.ifEmpty { "📎 Медиафайл" }
        )
    }
}

@Composable
fun MessageBubble(
    chatId: String,
    message: Message,
    isMyMessage: Boolean,
    theme: String,
    onNavigateToPost: (String) -> Unit,
    onReply: (Message) -> Unit,
    onReplyClick: (String) -> Unit,
    onLongClick: (Message) -> Unit,
    onToggleReaction: (Message, String) -> Unit = { _, _ -> }
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("user_session", Context.MODE_PRIVATE) }
    val isVibrationEnabled = remember(sharedPrefs) { sharedPrefs.getBoolean("vibration_enabled", true) }
    val fontSizeMultiplier = LocalFontSize.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var offsetX by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 50.dp.toPx() }
    var isHapticDone by remember { mutableStateOf(value = false) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf(message.text) }

    val db = FirebaseFirestore.getInstance()

    val bubbleColor = if (isMyMessage) {
        when (theme) {
            "BLUE" -> Color(0xFF2196F3)
            "GREEN" -> Color(0xFF4CAF50)
            "PURPLE" -> Color(0xFF9C27B0)
            "ORANGE" -> Color(0xFFFF9800)
            else -> MaterialTheme.colorScheme.primary
        }
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Редактировать сообщение") },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (message.id.isNotEmpty()) {
                        db.collection("chats").document(chatId).collection("messages").document(message.id)
                            .update("text", editedText)
                    }
                    showEditDialog = false
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить сообщение?") },
            text = { Text("Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        db.collection("chats").document(chatId).collection("messages").document(message.id).delete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
            }
        )
    }

    val animatedOffsetX by animateFloatAsState(targetValue = offsetX)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX >= swipeThreshold) {
                            onReply(message)
                        }
                        offsetX = 0f
                        isHapticDone = false
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val newOffset = (offsetX + dragAmount).coerceIn(0f, swipeThreshold * 1.5f)
                        offsetX = newOffset
                        
                        if (offsetX >= swipeThreshold && !isHapticDone) {
                            if (isVibrationEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isHapticDone = true
                        } else if (offsetX < swipeThreshold) {
                            isHapticDone = false
                        }
                    }
                )
            }
    ) {
        if (offsetX > 0) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = (offsetX / swipeThreshold).coerceIn(0f, 1f)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(24.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = animatedOffsetX },
            horizontalAlignment = if (isMyMessage) Alignment.End else Alignment.Start
        ) {
            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMyMessage) 16.dp else 4.dp,
                    bottomEnd = if (isMyMessage) 4.dp else 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = bubbleColor,
                    contentColor = if (isMyMessage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(
                        onClick = { /* Клик */ },
                        onDoubleClick = { onToggleReaction(message, "❤️") }, // двойное нажатие: быстрое «сердечко»
                        onLongClick = { onLongClick(message) }
                    )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    if (message.replyToId != null && message.replyToText != null) {
                        Row(
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Gray.copy(alpha = 0.1f))
                                .clickable { onReplyClick(message.replyToId) }
                                .height(IntrinsicSize.Min)
                                .fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Ответ",
                                    fontSize = 11.sp * fontSizeMultiplier,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = message.replyToText,
                                    fontSize = 13.sp * fontSizeMultiplier,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isMyMessage) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    if (!message.forwardedFrom.isNullOrEmpty()) {
                        var forwardedName by remember { mutableStateOf(message.forwardedFrom) }
                        LaunchedEffect(message.forwardedFrom) {
                            db.collection("users").document(message.forwardedFrom).get().addOnSuccessListener { doc ->
                                doc.getString("name")?.let { forwardedName = it }
                            }
                        }
                        Text(
                            text = "Переслано от $forwardedName",
                            fontSize = 11.sp * fontSizeMultiplier,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = if (isMyMessage) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    if (!message.forwardedPostId.isNullOrEmpty()) {
                        Surface(
                            color = if (isMyMessage) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .fillMaxWidth()
                                .clickable {
                                    val fid = message.forwardedPostId.orEmpty()
                                    // Видео из Shorts открываем во вкладке Shorts, обычные посты — как раньше
                                    if (fid.startsWith("short:")) PendingShortLink.shortId = fid.removePrefix("short:")
                                    else onNavigateToPost(fid)
                                }
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (message.forwardedPostId.orEmpty().startsWith("short:")) "🎬 Видео ZHIRPEM Shorts" else "🔗 Пересланный пост",
                                    fontSize = 12.sp * fontSizeMultiplier,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMyMessage) Color.White else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (message.mediaUrl.isNotEmpty()) {
                        val mediaModifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .padding(bottom = if (message.text.isNotEmpty()) 8.dp else 0.dp)

                        when (message.mediaType) {
                            MediaType.IMAGE -> {
                                AsyncImage(
                                    model = message.mediaUrl,
                                    contentDescription = "Изображение",
                                    modifier = mediaModifier
                                        .heightIn(max = 400.dp)
                                        .adaptiveImageSize(isLandscape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            MediaType.GIF -> {
                                GifPlayer(gifUrl = message.mediaUrl, modifier = mediaModifier.height(240.dp).adaptiveImageSize(isLandscape))
                            }
                            MediaType.VIDEO -> {
                                if (message.kind == "voice" || message.replyToId == "voice" || message.mediaUrl.contains("audio_") || message.mediaUrl.contains(".m4a")) {
                                    VoiceMessageBubble(
                                        audioUrl = message.mediaUrl,
                                        isMyMessage = isMyMessage,
                                        peaks = message.audioPeaks,
                                        durationMs = message.durationMs
                                    )
                                } else {
                                    VideoMessageBubble(
                                        rawVideoUrl = message.mediaUrl,
                                        isMyMessage = isMyMessage,
                                        durationMs = message.durationMs
                                    )
                                }
                            }
                            else -> {}
                        }
                    }

                    if (message.text.isNotEmpty()) {
                        Text(
                            text = message.text,
                            fontSize = 15.sp * fontSizeMultiplier,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    ReactionChips(
                        reactions = message.reactions,
                        myUserId = (sharedPrefs.getString("username", "") ?: "").replace("@", "").trim(),
                        isMyMessage = isMyMessage,
                        onToggle = { emoji -> onToggleReaction(message, emoji) }
                    )

                    if (isMyMessage) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                            Text(
                                text = timeFormat.format(Date(message.timestamp)),
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Icon(
                                imageVector = if (message.isRead) Icons.Filled.DoneAll else Icons.Filled.Done,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        Text(
                            text = timeFormat.format(Date(message.timestamp)),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.End).padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReplyPanel(message: Message, onCancel: () -> Unit) {
    val fontSizeMultiplier = LocalFontSize.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Ответ на сообщение",
                fontSize = 12.sp * fontSizeMultiplier,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = message.text.ifEmpty { "Медиафайл" },
                fontSize = 14.sp * fontSizeMultiplier,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Отмена", modifier = Modifier.size(16.dp))
        }
    }
}

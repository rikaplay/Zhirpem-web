package com.RIKAPLAY.zhirpem_app

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.RIKAPLAY.zhirpem_app.platform.*
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.Direction
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: String, onBack: () -> Unit, onNavigateToPost: (String) -> Unit, onOpenCamera: () -> Unit) {
    val db = Firebase.firestore
    val sessionManager = remember { SessionManager() }
    val myUsername = sessionManager.username ?: ""

    var messages by remember { mutableStateOf(listOf<Message>()) }
    var peerName by remember { mutableStateOf("Чат") }
    var peerAvatarUrl by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(chatId) {
        val peerId = chatId.split("_").firstOrNull { it != myUsername } ?: ""
        if (peerId.isNotEmpty()) {
            try {
                val doc = db.collection("users").document(peerId).get()
                peerName = doc.get<String>("name") ?: peerId
                peerAvatarUrl = doc.get<String>("avatarUrl")
            } catch (e: Exception) {}
        }

        db.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp")
            .snapshots.collect { snapshot ->
                messages = snapshot.documents.map { it.data<Message>().copy(id = it.id) }
            }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = peerAvatarUrl ?: "https://placehold.co/100x100.png",
                            contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(peerName, fontWeight = FontWeight.Bold)
                            PresenceIndicator(chatId.split("_").firstOrNull { it != myUsername } ?: "")
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message, message.senderId == myUsername)
                }
            }
            
            ChatInputBar(
                onSendText = { text ->
                    scope.launch {
                        val timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                        val msg = mapOf(
                            "senderId" to myUsername,
                            "text" to text,
                            "timestamp" to timestamp,
                            "isRead" to false
                        )
                        db.collection("chats").document(chatId).collection("messages").add(msg)
                    }
                }
            )
        }
    }
}

@Composable
fun MessageBubble(message: Message, isMyMessage: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMyMessage) Alignment.End else Alignment.Start
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isMyMessage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (message.text.isNotEmpty()) {
                    Text(message.text)
                }
                if (message.mediaUrl.isNotEmpty()) {
                    when (message.mediaType) {
                        MediaType.IMAGE -> AsyncImage(model = message.mediaUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)))
                        MediaType.VIDEO -> VideoMessageBubble(message.mediaUrl, isMyMessage)
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
expect fun ChatInputBar(onSendText: (String) -> Unit)

@Composable
expect fun VideoMessageBubble(videoUrl: String, isMyMessage: Boolean)

@Composable
expect fun VoiceMessageBubble(audioUrl: String, isMyMessage: Boolean)

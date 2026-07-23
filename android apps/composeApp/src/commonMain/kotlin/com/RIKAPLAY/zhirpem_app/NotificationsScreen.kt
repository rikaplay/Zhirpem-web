@file:OptIn(ExperimentalMaterial3Api::class)

package com.RIKAPLAY.zhirpem_app

import androidx.compose.material.icons.filled.Delete
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.RIKAPLAY.zhirpem_app.platform.HtmlText
import com.RIKAPLAY.zhirpem_app.platform.showToast
import zhirpem_app.composeapp.generated.resources.Res
import zhirpem_app.composeapp.generated.resources.jirpem_logo

// ==========================================
// 1. МОДЕЛЬ ДАННЫХ И ТИПЫ
// ==========================================

enum class NotificationType {
    LIKE, COMMENT, FOLLOW, MESSAGE, ADMIN
}

data class NotificationModel(
    val id: String = "",
    val senderId: String = "",
    val username: String = "",
    val userAvatarUrl: String = "",
    val type: NotificationType = NotificationType.LIKE,
    val targetText: String = "",
    val userComment: String = "",
    val timestamp: Timestamp? = null,
    val receiverId: String = "",
    val postId: String? = null,
    val bigPictureUrl: String = ""
)

// ==========================================
// 2. ГЛАВНЫЙ ЭКРАН УВЕДОМЛЕНИЙ
// ==========================================

@Composable
fun NotificationsScreen() {
    val sessionManager = remember { SessionManager() }
    val currentUserId = sessionManager.username ?: ""
    
    var notificationsList by remember { mutableStateOf(listOf<NotificationModel>()) }
    var isLoading by remember { mutableStateOf(true) }
    val db = Firebase.firestore
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            db.collection("notifications")
                .whereIn("receiverId", listOf(currentUserId, "ALL"))
                .orderBy("timestamp", Direction.DESCENDING)
                .snapshots
                .collect { snapshot ->
                    notificationsList = snapshot.documents.map { doc ->
                        val typeStr = doc.get<String>("type") ?: "LIKE"
                        val type = try { NotificationType.valueOf(typeStr) } catch (e: Exception) { NotificationType.LIKE }
                        
                        NotificationModel(
                            id = doc.id,
                            senderId = doc.get<String>("senderId") ?: "",
                            username = doc.get<String>("senderName") ?: "Пользователь",
                            userAvatarUrl = doc.get<String>("senderAvatarUrl") ?: "",
                            type = type,
                            targetText = doc.get<String>("title") ?: doc.get<String>("targetText") ?: "",
                            userComment = doc.get<String>("htmlBody") ?: doc.get<String>("userComment") ?: doc.get<String>("text") ?: "",
                            timestamp = doc.get<Timestamp>("timestamp"),
                            receiverId = doc.get<String>("receiverId") ?: "",
                            postId = doc.get<String>("postId"),
                            bigPictureUrl = doc.get<String>("bigPictureUrl") ?: ""
                        )
                    }
                    isLoading = false
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Уведомления", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (notificationsList.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Здесь пока пусто", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(notificationsList, key = { it.id }) { item ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.StartToEnd) {
                                    scope.launch {
                                        db.collection("notifications").document(item.id).delete()
                                        showToast("Удалено")
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromEndToStart = false,
                            backgroundContent = {
                                val color by animateColorAsState(
                                    when (dismissState.targetValue) {
                                        SwipeToDismissBoxValue.StartToEnd -> Color.Red.copy(alpha = 0.8f)
                                        else -> Color.Transparent
                                    }, label = "dismiss_bg"
                                )
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(color)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Удалить",
                                        tint = Color.White
                                    )
                                }
                            }
                        ) {
                            Column {
                                NotificationItem(
                                    notification = item,
                                    onFollowClick = { 
                                        if (currentUserId.isNotEmpty() && item.senderId.isNotEmpty()) {
                                            scope.launch {
                                                val followData = mapOf(
                                                    "follower" to currentUserId,
                                                    "following" to item.senderId,
                                                    "timestamp" to kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                                                )
                                                db.collection("follows").add(followData)
                                            }
                                        }
                                    }
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItem(
    notification: NotificationModel,
    onFollowClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp)) {
            AsyncImage(
                model = if (notification.type == NotificationType.ADMIN) Res.drawable.jirpem_logo else notification.userAvatarUrl.ifEmpty { "https://placehold.co/100x100.png" },
                contentDescription = "Аватарка",
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )

            val (badgeIcon, badgeColor) = when (notification.type) {
                NotificationType.LIKE -> Icons.Default.Favorite to Color(0xFFEF5350)
                NotificationType.COMMENT -> Icons.Default.Comment to MaterialTheme.colorScheme.primary
                NotificationType.FOLLOW -> Icons.Default.PersonAdd to Color(0xFF4CAF50)
                NotificationType.MESSAGE -> Icons.Default.Email to Color(0xFF2196F3)
                NotificationType.ADMIN -> Icons.Default.Notifications to Color(0xFFFF9800)
            }

            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            val annotatedText = buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)) {
                    append(if (notification.type == NotificationType.ADMIN) "Zhirpem" else notification.username)
                }
                append(" ")
                val actionText = when (notification.type) {
                    NotificationType.LIKE -> "оценил(а) вашу запись"
                    NotificationType.COMMENT -> "прокомментировал(а) вашу запись"
                    NotificationType.FOLLOW -> "подписался(-ась) на вас"
                    NotificationType.MESSAGE -> "отправил(а) вам сообщение"
                    NotificationType.ADMIN -> ""
                }
                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 14.sp)) {
                    append(actionText)
                }
            }

            Text(text = annotatedText)

            if (notification.type == NotificationType.ADMIN && notification.targetText.isNotEmpty()) {
                Text(
                    text = notification.targetText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (notification.type == NotificationType.ADMIN) {
                HtmlText(
                    html = notification.userComment,
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                if (notification.bigPictureUrl.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = notification.bigPictureUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            } else if (notification.type == NotificationType.COMMENT && notification.userComment.isNotEmpty()) {
                Text(
                    text = notification.userComment,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else if (notification.targetText.isNotEmpty()) {
                Text(
                    text = notification.targetText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = formatRelativeTime(notification.timestamp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }

        if (notification.type == NotificationType.FOLLOW) {
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onFollowClick,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(text = "Подписаться", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

fun formatRelativeTime(timestamp: Timestamp?): String {
    if (timestamp == null) return "только что"
    val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    val time = timestamp.seconds * 1000
    val diff = now - time
    
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        seconds < 60 -> "только что"
        minutes < 60 -> "${minutes}м"
        hours < 24 -> "${hours}ч"
        days < 30 -> "${days}д"
        else -> "недавно"
    }
}

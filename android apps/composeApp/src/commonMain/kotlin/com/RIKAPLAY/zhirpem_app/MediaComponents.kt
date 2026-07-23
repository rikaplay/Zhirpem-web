package com.RIKAPLAY.zhirpem_app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.gitlive.firebase.database.database
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun PresenceIndicator(username: String, modifier: Modifier = Modifier) {
    val cleanUsername = username.replace("@", "")
    var isOnline by remember { mutableStateOf(false) }

    LaunchedEffect(cleanUsername) {
        val database = Firebase.database
        val presenceRef = database.reference("status/$cleanUsername/state")
        try {
            presenceRef.snapshots.collect { snapshot ->
                isOnline = snapshot.value<String>() == "online"
            }
        } catch (e: Exception) {}
    }

    Box(
        modifier = modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(if (isOnline) Color(0xFF4CAF50) else Color.Gray)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
    )
}

@Composable
fun VerifiedBadge(isBlue: Boolean, isYellow: Boolean) {
    if (isBlue) {
        Icon(
            imageVector = Icons.Default.Verified,
            contentDescription = "Verified",
            tint = Color(0xFF2196F3),
            modifier = Modifier.size(16.dp).padding(start = 4.dp)
        )
    } else if (isYellow) {
        Icon(
            imageVector = Icons.Default.Verified,
            contentDescription = "Official",
            tint = Color(0xFFFFC107),
            modifier = Modifier.size(16.dp).padding(start = 4.dp)
        )
    }
}

@Composable
fun ShimmerPostItem() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(46.dp).clip(CircleShape).background(Color.Gray.copy(alpha = alpha)))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(width = 120.dp, height = 16.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = alpha)))
                Box(modifier = Modifier.size(width = 80.dp, height = 12.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = alpha)))
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(16.dp)).background(Color.Gray.copy(alpha = alpha)))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostItem(
    post: Post,
    onUserClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit = {}
) {
    val db = Firebase.firestore
    val sessionManager = remember { SessionManager() }
    val myUsername = sessionManager.username ?: ""
    val fontSizeMultiplier = LocalFontSize.current
    val scope = rememberCoroutineScope()

    val isLiked = post.likedBy.contains(myUsername)
    val isMyPost = post.handle.replace("@", "") == myUsername
    var isExpanded by remember { mutableStateOf(false) }
    var isCommentsExpanded by rememberSaveable(post.id) { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf(post.text) }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Редактировать") },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        db.collection("zhirpem_posts").document(post.id).update("text" to editedText)
                        showEditDialog = false
                    }
                }) { Text("Сохранить") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить пост?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        db.collection("zhirpem_posts").document(post.id).delete()
                        showDeleteDialog = false
                    }
                }) { Text("Удалить", color = Color.Red) }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .combinedClickable(
                onClick = { isExpanded = !isExpanded },
                onLongClick = { if (isMyPost) showDeleteDialog = true } // Simplified for now
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            .clickable { onUserClick(post.handle.replace("@", "")) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!post.authorAvatarUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = post.authorAvatarUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(post.author.take(1).uppercase(), fontWeight = FontWeight.Bold)
                        }
                    }
                    PresenceIndicator(post.handle, Modifier.align(Alignment.BottomEnd))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val nameColor = if (!post.authorNameColor.isNullOrEmpty()) parseColor(post.authorNameColor) else MaterialTheme.colorScheme.onSurface
                        Text(post.author, color = nameColor, fontWeight = FontWeight.Bold, fontSize = 16.sp * fontSizeMultiplier)
                        VerifiedBadge(post.blueBadge, post.yellowBadge)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(post.handle, color = Color.Gray, fontSize = 14.sp * fontSizeMultiplier)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            if (post.text.isNotEmpty()) {
                Text(post.text, fontSize = 16.sp * fontSizeMultiplier)
            }

            if (post.imageUrl != null) {
                Spacer(modifier = Modifier.height(12.dp))
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.FillWidth
                )
            } else if (post.mediaUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                when (post.mediaType) {
                    MediaType.GIF -> GifPlayer(post.mediaUrl, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)))
                    MediaType.VIDEO -> VideoPlayer(post.mediaUrl, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)))
                    else -> {}
                }
            }
            
            if (post.poll != null) {
                PollView(poll = post.poll, onVote = {}, currentUserId = myUsername)
            }
        }
    }
}

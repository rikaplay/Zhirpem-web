package com.RIKAPLAY.zhirpem_app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.FieldValue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(
    postId: String,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val sessionManager = remember { SessionManager() }
    val myUsername = sessionManager.username ?: ""
    val viewModel = remember { CommentViewModel() }

    val comments by viewModel.comments.collectAsState()
    var replyingTo by remember { mutableStateOf<Comment?>(null) }

    LaunchedEffect(postId) {
        viewModel.listenToComments(postId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Комментарии", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (comments.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Пока нет ответов. Станьте первым!", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(comments, key = { it.id }) { comment ->
                        CommentItemRow(
                            comment = comment,
                            currentUserId = myUsername,
                            onLikeClick = {
                                viewModel.toggleLikeComment(comment.id, myUsername)
                            },
                            onUserClick = onUserClick,
                            onReply = { replyingTo = it }
                        )
                    }
                }
            }
            
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                CommentInput(
                    postId = postId,
                    replyTo = replyingTo,
                    onCancelReply = { replyingTo = null }
                )
            }
        }
    }
}

@Composable
fun CommentItemRow(
    comment: Comment,
    currentUserId: String,
    onLikeClick: () -> Unit,
    onUserClick: (String) -> Unit,
    onReply: (Comment) -> Unit
) {
    val isLikedByMe = comment.likedBy.contains(currentUserId)
    val isReply = !comment.replyToCommentId.isNullOrEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 24.dp else 0.dp)
            .padding(vertical = 8.dp)
            .clickable { onReply(comment) },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                .clickable { onUserClick(comment.authorUsername.replace("@", "")) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = comment.author.take(1).uppercase(),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.clickable { onUserClick(comment.authorUsername.replace("@", "")) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(comment.author, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    comment.authorUsername,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(comment.text, fontSize = 14.sp)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onLikeClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isLikedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (comment.likesCount > 0) {
                Text(
                    text = comment.likesCount.toString(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CommentInput(
    postId: String,
    replyTo: Comment? = null,
    onCancelReply: () -> Unit = {}
) {
    val sessionManager = remember { SessionManager() }
    val db = Firebase.firestore
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }

    LaunchedEffect(replyTo) {
        if (replyTo != null) {
            val tag = "${replyTo.authorUsername} "
            if (!text.contains(tag)) {
                text = tag + text.replace(Regex("^@[a-zA-Z0-9_]+\\s"), "")
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (replyTo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Ответ ${replyTo.author}", fontSize = 12.sp, maxLines = 1)
                IconButton(onClick = onCancelReply, modifier = Modifier.size(16.dp)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ваш комментарий...") },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        scope.launch {
                            val newComment = mapOf(
                                "postId" to postId,
                                "author" to (sessionManager.name ?: "Аноним"),
                                "authorUsername" to "@${sessionManager.username ?: "user"}",
                                "text" to text.trim(),
                                "timestamp" to kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                                "likesCount" to 0,
                                "likedBy" to emptyList<String>(),
                                "replyToCommentId" to replyTo?.id,
                                "replyToUsername" to replyTo?.authorUsername
                            )
                            db.collection("comments").add(newComment)
                            db.collection("zhirpem_posts").document(postId).update("commentsCount" to FieldValue.increment(1))
                            text = ""
                            onCancelReply()
                        }
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White)
            }
        }
    }
}

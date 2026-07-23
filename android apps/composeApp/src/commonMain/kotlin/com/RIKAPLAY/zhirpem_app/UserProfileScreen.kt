package com.RIKAPLAY.zhirpem_app

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.RIKAPLAY.zhirpem_app.platform.*
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    username: String, 
    onBack: () -> Unit, 
    onNavigateToChat: (String) -> Unit = {},
    onHashtagClick: (String) -> Unit = {}
) {
    val db = Firebase.firestore
    val sessionManager = remember { SessionManager() }
    val myUsername = sessionManager.username ?: ""
    val fontSizeMultiplier = LocalFontSize.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("Загрузка...") }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var blueBadge by remember { mutableStateOf(false) }
    var yellowBadge by remember { mutableStateOf(false) }
    var bannerColor by remember { mutableStateOf("#808080") }
    var bio by remember { mutableStateOf("") }
    var joinedCommunityId by remember { mutableStateOf<String?>(null) }
    var joinedCommunityAvatar by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var bannerUrl by remember { mutableStateOf<String?>(null) }
    var isEditingBio by remember { mutableStateOf(false) }
    var isEditingStatus by remember { mutableStateOf(false) }
    var isMoreMenuExpanded by remember { mutableStateOf(false) }
    
    var posts by remember { mutableStateOf(listOf<Post>()) }
    var repostedPosts by remember { mutableStateOf(listOf<Post>()) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isUploading by remember { mutableStateOf(false) }

    var isFollowing by remember { mutableStateOf(false) }

    val bannerPicker = rememberImagePicker { path ->
        scope.launch {
            isUploading = true
            val url = getMediaUploader().upload(path, "image")
            if (url != null) {
                db.collection("users").document(username).update("bannerUrl" to url)
                bannerUrl = url
            }
            isUploading = false
        }
    }

    val avatarPicker = rememberImagePicker { path ->
        scope.launch {
            isUploading = true
            val url = getMediaUploader().upload(path, "image")
            if (url != null) {
                db.collection("users").document(username).update("avatarUrl" to url)
                avatarUrl = url
            }
            isUploading = false
        }
    }

    LaunchedEffect(username) {
        db.collection("users").document(username).snapshots.collect { doc ->
            if (doc.exists) {
                name = doc.get<String>("name") ?: "Без имени"
                avatarUrl = doc.get<String>("avatarUrl")
                blueBadge = doc.get<Boolean>("blueBadge") ?: false
                yellowBadge = doc.get<Boolean>("yellowBadge") ?: false
                bannerColor = doc.get<String>("bannerColor") ?: "#808080"
                bio = doc.get<String>("bio") ?: ""
                joinedCommunityId = doc.get<String>("joinedCommunityId")
                joinedCommunityAvatar = doc.get<String>("joinedCommunityAvatar")
                status = doc.get<String>("status") ?: ""
                bannerUrl = doc.get<String>("bannerUrl")
            }
        }
    }
    
    LaunchedEffect(username) {
        db.collection("zhirpem_posts")
            .whereEqualTo("handle", "@$username")
            .snapshots.collect { snap ->
                posts = snap.documents.map { it.data<Post>().copy(id = it.id) }.sortedByDescending { it.timestamp }
            }
    }

    LaunchedEffect(username) {
        db.collection("zhirpem_posts")
            .whereArrayContains("repostedBy", username)
            .snapshots.collect { snap ->
                repostedPosts = snap.documents.map { it.data<Post>().copy(id = it.id) }.sortedByDescending { it.timestamp }
                isLoading = false
            }
    }

    LaunchedEffect(myUsername, username) {
        if (myUsername.isNotEmpty() && myUsername != username) {
            db.collection("follows")
                .whereEqualTo("follower", myUsername)
                .whereEqualTo("following", username)
                .snapshots.collect { snapshot ->
                    isFollowing = snapshot.documents.isNotEmpty()
                }
        }
    }

    if (isEditingStatus) {
        var tempStatus by remember { mutableStateOf(status) }
        AlertDialog(
            onDismissRequest = { isEditingStatus = false },
            title = { Text("Обновить статус") },
            text = {
                OutlinedTextField(
                    value = tempStatus,
                    onValueChange = { tempStatus = it },
                    placeholder = { Text("Ваш статус...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        db.collection("users").document(username).update("status" to tempStatus)
                        isEditingStatus = false
                    }
                }) { Text("Сохранить") }
            }
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${posts.size} записей", fontSize = 12.sp, color = Color.Gray)
                }
                
                if (myUsername == username) {
                    IconButton(onClick = { /* Color Picker dialog could go here */ }) {
                        Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            val parsedColor = parseColor(bannerColor)
            Box(modifier = Modifier.fillMaxWidth().height(140.dp).background(parsedColor)) {
                if (!bannerUrl.isNullOrEmpty()) {
                    AsyncImage(model = bannerUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                if (myUsername == username) {
                    IconButton(
                        onClick = { bannerPicker() },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(0.5f), CircleShape)
                    ) { Icon(Icons.Default.Add, null, tint = Color.White) }
                }
                
                Box(modifier = Modifier.padding(start = 20.dp).size(100.dp).align(Alignment.BottomStart).offset(y = 50.dp)) {
                    AsyncImage(
                        model = avatarUrl, 
                        contentDescription = null, 
                        modifier = Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surface).border(4.dp, MaterialTheme.colorScheme.background, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    PresenceIndicator(username, Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 4.dp))
                    
                    if (myUsername == username) {
                        IconButton(
                            onClick = { avatarPicker() },
                            modifier = Modifier.align(Alignment.BottomEnd).size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape).border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                        ) { Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(54.dp))

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                            VerifiedBadge(blueBadge, yellowBadge)
                        }
                        Text("@$username", color = Color.Gray)
                        Text(
                            text = if (status.isEmpty()) "Установить статус..." else status,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(enabled = myUsername == username) { isEditingStatus = true }
                        )
                    }
                    
                    if (myUsername != username) {
                        Button(onClick = {
                            scope.launch {
                                openOrCreateChat(myUsername, username) { onNavigateToChat(it) }
                            }
                        }) { Text("Написать") }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                if (isEditingBio) {
                    var tempBio by remember { mutableStateOf(bio) }
                    Column {
                        OutlinedTextField(value = tempBio, onValueChange = { tempBio = it }, modifier = Modifier.fillMaxWidth(), label = { Text("О себе") })
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { isEditingBio = false }) { Text("Отмена") }
                            TextButton(onClick = { scope.launch { db.collection("users").document(username).update("bio" to tempBio); isEditingBio = false } }) { Text("Сохранить") }
                        }
                    }
                } else {
                    Text(
                        text = if (bio.isEmpty()) "Добавить описание..." else bio,
                        modifier = Modifier.fillMaxWidth().clickable { if (myUsername == username) isEditingBio = true },
                        color = if (bio.isEmpty()) Color.Gray else Color.Unspecified
                    )
                }
            }
            
            // TABS
            TabRow(selectedTabIndex = selectedTabIndex, containerColor = Color.Transparent) {
                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("Записи") })
                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Репосты") })
            }

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val currentList = if (selectedTabIndex == 0) posts else repostedPosts
                if (isLoading) {
                    repeat(3) { ShimmerPostItem() }
                } else if (currentList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { Text("Пусто") }
                } else {
                    currentList.forEach { post ->
                        PostItem(post = post, onUserClick = { if (it != username) onBack() }, onHashtagClick = onHashtagClick)
                    }
                }
            }
        }
    }
}

suspend fun openOrCreateChat(currentUserId: String, targetUserId: String, onChatReady: (String) -> Unit) {
    val db = Firebase.firestore
    try {
        val snapshot = db.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .get()
        
        val existingChat = snapshot.documents.find { doc ->
            val participants = doc.get<List<String>>("participants")
            participants.contains(targetUserId) && (participants.size == 2 || currentUserId == targetUserId)
        }

        if (existingChat != null) {
            onChatReady(existingChat.id)
        } else {
            val newChatRef = db.collection("chats").document()
            val newChat = mapOf(
                "id" to newChatRef.id,
                "participants" to listOf(currentUserId, targetUserId),
                "lastMessage" to "",
                "lastMessageTimestamp" to kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            )
            newChatRef.set(newChat)
            onChatReady(newChatRef.id)
        }
    } catch (e: Exception) {}
}

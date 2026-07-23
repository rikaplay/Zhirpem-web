package com.RIKAPLAY.zhirpem_app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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

    var user by remember { mutableStateOf<User?>(null) }
    var posts by remember { mutableStateOf(listOf<Post>()) }
    var repostedPosts by remember { mutableStateOf(listOf<Post>()) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isUploading by remember { mutableStateOf(false) }
    var isFollowing by remember { mutableStateOf(false) }
    var isEditingBio by remember { mutableStateOf(false) }
    var isEditingStatus by remember { mutableStateOf(false) }

    val avatarPicker = rememberImagePicker { path ->
        scope.launch {
            isUploading = true
            val url = getMediaUploader().upload(path, "image")
            if (url != null) {
                db.collection("users").document(username).update("avatarUrl" to url)
            }
            isUploading = false
        }
    }

    val bannerPicker = rememberImagePicker { path ->
        scope.launch {
            isUploading = true
            val url = getMediaUploader().upload(path, "image")
            if (url != null) {
                db.collection("users").document(username).update("bannerUrl" to url)
            }
            isUploading = false
        }
    }

    LaunchedEffect(username) {
        db.collection("users").document(username).snapshots.collect { doc ->
            if (doc.exists) {
                user = doc.data()
            }
        }
    }

    LaunchedEffect(username) {
        db.collection("zhirpem_posts")
            .whereEqualTo("handle", "@$username")
            .snapshots.collect { snap ->
                posts = snap.documents.map { it.data<Post>().copy(id = it.id) }
                    .sortedByDescending { it.timestamp }
            }
    }

    LaunchedEffect(username) {
        db.collection("zhirpem_posts")
            .whereArrayContains("repostedBy", username)
            .snapshots.collect { snap ->
                repostedPosts = snap.documents.map { it.data<Post>().copy(id = it.id) }
                    .sortedByDescending { it.timestamp }
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

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Column(modifier = Modifier.weight(1f)) {
                    Text(user?.name ?: "Профиль", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("${posts.size} записей", fontSize = 13.sp, color = Color.Gray)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            // Banner & Avatar
            val bannerColor = user?.bannerColor ?: "#808080"
            Box(modifier = Modifier.fillMaxWidth().height(140.dp).background(parseColor(bannerColor))) {
                user?.bannerUrl?.let {
                    AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                
                if (myUsername == username) {
                    IconButton(
                        onClick = bannerPicker,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(0.5f), CircleShape).size(32.dp)
                    ) { Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                }

                Box(modifier = Modifier.padding(start = 20.dp).size(100.dp).align(Alignment.BottomStart).offset(y = 50.dp)) {
                    AsyncImage(
                        model = user?.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surface).border(4.dp, MaterialTheme.colorScheme.background, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    PresenceIndicator(username, Modifier.align(Alignment.TopEnd))
                    
                    if (myUsername == username) {
                        IconButton(
                            onClick = avatarPicker,
                            modifier = Modifier.align(Alignment.BottomEnd).size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape).border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                        ) { Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(60.dp))

            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(user?.name ?: "", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("@$username", color = Color.Gray)
                        Text(
                            text = if (user?.status?.isEmpty() == true) "Установить статус..." else user?.status ?: "",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(enabled = myUsername == username) { isEditingStatus = true }
                        )
                    }
                    
                    if (myUsername != username && myUsername.isNotEmpty()) {
                        Button(onClick = { onNavigateToChat(username) }) { Text("Написать") }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(user?.bio?.ifEmpty { "О себе еще ничего не написано..." } ?: "", modifier = Modifier.clickable(enabled = myUsername == username) { isEditingBio = true })
                
                Spacer(modifier = Modifier.height(16.dp))
                // Stats
                Row {
                    Text("0 читает", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("0 читателей", fontWeight = FontWeight.Bold)
                }
            }

            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("Записи") })
                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Репосты") })
            }

            val currentPosts = if (selectedTabIndex == 0) posts else repostedPosts
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                currentPosts.forEach { post ->
                    PostItem(post = post, onUserClick = { if (it != username) onBack() }, onHashtagClick = onHashtagClick)
                }
            }
        }
    }
    
    if (isEditingStatus) {
        var tempStatus by remember { mutableStateOf(user?.status ?: "") }
        AlertDialog(
            onDismissRequest = { isEditingStatus = false },
            title = { Text("Статус") },
            text = { OutlinedTextField(value = tempStatus, onValueChange = { tempStatus = it }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { scope.launch { db.collection("users").document(username).update("status" to tempStatus); isEditingStatus = false } }) { Text("OK") } }
        )
    }

    if (isEditingBio) {
        var tempBio by remember { mutableStateOf(user?.bio ?: "") }
        AlertDialog(
            onDismissRequest = { isEditingBio = false },
            title = { Text("О себе") },
            text = { OutlinedTextField(value = tempBio, onValueChange = { tempBio = it }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { scope.launch { db.collection("users").document(username).update("bio" to tempBio); isEditingBio = false } }) { Text("OK") } }
        )
    }
}

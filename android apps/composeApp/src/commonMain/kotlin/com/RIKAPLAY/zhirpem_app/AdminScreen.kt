package com.RIKAPLAY.zhirpem_app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.RIKAPLAY.zhirpem_app.platform.*
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(onBack: () -> Unit) {
    val db = Firebase.firestore
    var currentTab by remember { mutableStateOf("Users") }
    
    var users by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var posts by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentTab) {
        if (currentTab == "Notifications") return@LaunchedEffect
        
        isLoading = true
        scope.launch {
            try {
                if (currentTab == "Users") {
                    val result = db.collection("users").get()
                    users = result.documents.map { it.data<Map<String, Any>>() + ("id" to it.id) }
                } else if (currentTab == "Posts") {
                    val result = db.collection("zhirpem_posts").get()
                    posts = result.documents.map { it.data<Map<String, Any>>() + ("id" to it.id) }
                }
            } catch (e: Exception) {}
            isLoading = false
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                isGlassEnabled = LocalGlassEnabled.current,
                title = { Text("Админ-панель 🛠️") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                AdminTabButton("Юзеры", currentTab == "Users") { currentTab = "Users" }
                Spacer(modifier = Modifier.width(4.dp))
                AdminTabButton("Посты", currentTab == "Posts") { currentTab = "Posts" }
                Spacer(modifier = Modifier.width(4.dp))
                AdminTabButton("Пуши", currentTab == "Notifications") { currentTab = "Notifications" }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (currentTab) {
                    "Users" -> {
                        FastUserAssigner(db)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(users) { user ->
                                UserAdminItem(user, db) { 
                                    scope.launch {
                                        val result = db.collection("users").get()
                                        users = result.documents.map { it.data<Map<String, Any>>() + ("id" to it.id) }
                                    }
                                }
                            }
                        }
                    }
                    "Posts" -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(posts) { post ->
                                PostAdminItem(post, db) { 
                                    scope.launch {
                                        val result = db.collection("zhirpem_posts").get()
                                        posts = result.documents.map { it.data<Map<String, Any>>() + ("id" to it.id) }
                                    }
                                }
                            }
                        }
                    }
                    "Notifications" -> {
                        NotificationAdminTab(db)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminTabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, 
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) { Text(text, fontSize = 12.sp) }
}

@Composable
fun FastUserAssigner(db: FirebaseFirestore) {
    var searchQuery by remember { mutableStateOf("") }
    var targetUser by remember { mutableStateOf<Map<String, Any>?>(null) }
    var statusMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Быстрый поиск", fontWeight = FontWeight.Bold)

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it.lowercase().trim() },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("username") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (searchQuery.isNotEmpty()) {
                            scope.launch {
                                try {
                                    val doc = db.collection("users").document(searchQuery).get()
                                    if (doc.exists) {
                                        targetUser = doc.data<Map<String, Any>>() + ("id" to doc.id)
                                        statusMessage = "Пользователь найден!"
                                    } else {
                                        targetUser = null
                                        statusMessage = "Пользователь не найден"
                                    }
                                } catch (e: Exception) {
                                    statusMessage = "Ошибка поиска"
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Найти")
                }
            }

            targetUser?.let { user ->
                Spacer(modifier = Modifier.height(16.dp))
                Text("Управление: ${user["name"]} (@${user["id"]})")
                
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = { scope.launch { updateBadge(db, user["id"] as String, true, false); statusMessage = "Синяя выдана!" } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)) { Text("Синяя", fontSize = 12.sp) }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = { scope.launch { updateBadge(db, user["id"] as String, false, true); statusMessage = "Желтая выдана!" } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color.Black)) { Text("Желтая", fontSize = 12.sp) }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = { scope.launch { updateBadge(db, user["id"] as String, false, false); statusMessage = "Сняты!" } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Снять", fontSize = 12.sp) }
                }
            }

            if (statusMessage.isNotEmpty()) {
                Text(statusMessage, modifier = Modifier.padding(top = 8.dp), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun UserAdminItem(user: Map<String, Any>, db: FirebaseFirestore, onAction: () -> Unit) {
    val userId = user["id"] as String
    var newColor by remember { mutableStateOf(user["nameColor"] as? String ?: "#000000") }
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("${user["name"] ?: "Noname"}", fontWeight = FontWeight.Bold)
                    Text("@${userId}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            val isBanned = user["isBanned"] as? Boolean ?: false
            Button(
                onClick = { 
                    scope.launch {
                        db.collection("users").document(userId).update("isBanned" to !isBanned)
                        onAction()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = if (isBanned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            ) {
                Text(if (isBanned) "Разбанить" else "Забанить")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = newColor, onValueChange = { newColor = it }, label = { Text("HEX") }, modifier = Modifier.weight(1f), singleLine = true)
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { 
                    if (newColor.startsWith("#")) {
                        scope.launch {
                            db.collection("users").document(userId).update("nameColor" to newColor)
                            onAction()
                        }
                    }
                }) { Text("Цвет") }
            }
        }
    }
}

@Composable
fun PostAdminItem(post: Map<String, Any>, db: FirebaseFirestore, onAction: () -> Unit) {
    val postId = post["id"] as String
    val scope = rememberCoroutineScope()
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("От: ${post["author"]}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            Text(post["text"] as? String ?: "", maxLines = 3)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { scope.launch { db.collection("zhirpem_posts").document(postId).delete(); onAction() } },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                Text(" Удалить")
            }
        }
    }
}

@Composable
fun NotificationAdminTab(db: FirebaseFirestore) {
    var title by remember { mutableStateOf("") }
    var htmlBody by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Создание уведомления", style = MaterialTheme.typography.titleLarge)
        
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Заголовок") }, modifier = Modifier.fillMaxWidth())
        
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            EditorButton("B") { htmlBody += "<b></b>" }
            EditorButton("I") { htmlBody += "<i></i>" }
            EditorButton("Link") { htmlBody += "<a href='URL'>Текст</a>" }
            // Media picker not integrated here yet for commonMain
        }

        OutlinedTextField(value = htmlBody, onValueChange = { htmlBody = it }, label = { Text("Текст (HTML)") }, modifier = Modifier.fillMaxWidth().height(150.dp))
        OutlinedTextField(value = imageUrl, onValueChange = { imageUrl = it }, label = { Text("URL картинки") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))
        Text("Предпросмотр:")
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                HtmlText(html = htmlBody)
            }
        }

        Button(
            onClick = {
                if (title.isNotEmpty() && htmlBody.isNotEmpty()) {
                    isSending = true
                    scope.launch {
                        val notificationData = mapOf(
                            "title" to title,
                            "htmlBody" to htmlBody,
                            "bigPictureUrl" to imageUrl,
                            "timestamp" to dev.gitlive.firebase.firestore.FieldValue.serverTimestamp,
                            "type" to "ADMIN",
                            "receiverId" to "ALL",
                            "senderName" to "Zhirpem"
                        )
                        db.collection("notifications").add(notificationData)
                        val success = NotificationSender().sendGlobalPush(title, htmlBody, imageUrl)
                        isSending = false
                        if (success) {
                            showToast("Отправлено!")
                            title = ""; htmlBody = ""; imageUrl = ""
                        } else {
                            showToast("Ошибка пуша")
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSending
        ) {
            if (isSending) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            else Text("Отправить всем")
        }
    }
}

suspend fun updateBadge(db: FirebaseFirestore, userId: String, blue: Boolean, yellow: Boolean) {
    db.collection("users").document(userId).update(
        "blueBadge" to blue,
        "yellowBadge" to yellow
    )
}

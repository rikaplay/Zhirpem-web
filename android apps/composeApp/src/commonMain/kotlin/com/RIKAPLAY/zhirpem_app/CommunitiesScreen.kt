package com.RIKAPLAY.zhirpem_app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
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
import dev.gitlive.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunitiesScreen(
    onBack: () -> Unit,
    onCommunityClick: (Community) -> Unit
) {
    val db = Firebase.firestore
    val sessionManager = remember { SessionManager() }
    val currentUserId = sessionManager.username ?: ""
    val scope = rememberCoroutineScope()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var allCommunities by remember { mutableStateOf(listOf<Community>()) }

    LaunchedEffect(Unit) {
        db.collection("communities").snapshots.collect { snapshot ->
            allCommunities = snapshot.documents.map { it.data<Community>().copy(id = it.id) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сообщества", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) { Icon(Icons.Default.Edit, null) }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(allCommunities) { community ->
                val isMember = community.members.contains(currentUserId)
                Card(modifier = Modifier.fillMaxWidth().clickable { onCommunityClick(community) }) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = community.avatarUrl, contentDescription = null, modifier = Modifier.size(48.dp).clip(CircleShape))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(community.name, fontWeight = FontWeight.Bold)
                            Text(community.description, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                        }
                        if (!isMember) {
                            Button(onClick = { scope.launch { db.collection("communities").document(community.id).update("members" to FieldValue.arrayUnion(currentUserId)) } }) { Text("Вступить") }
                        }
                    }
                }
            }
        }
    }
}

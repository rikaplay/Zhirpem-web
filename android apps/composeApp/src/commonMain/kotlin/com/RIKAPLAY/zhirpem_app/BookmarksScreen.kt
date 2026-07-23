package com.RIKAPLAY.zhirpem_app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(onBack: () -> Unit, onUserClick: (String) -> Unit) {
    val sessionManager = remember { SessionManager() }
    val myUsername = sessionManager.username ?: ""
    val db = Firebase.firestore

    var bookmarkedPosts by remember { mutableStateOf(listOf<Post>()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(myUsername) {
        if (myUsername.isNotEmpty()) {
            db.collection("zhirpem_posts")
                .where("bookmarkedBy", arrayContains = myUsername)
                .snapshots.collect { snapshot ->
                    bookmarkedPosts = snapshot.documents.map { it.data<Post>().copy(id = it.id) }
                        .sortedByDescending { it.timestamp?.seconds ?: 0L }
                    isLoading = false
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Закладки 🔖", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (bookmarkedPosts.isEmpty()) {
                Text("У вас пока нет закладок", modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(bookmarkedPosts, key = { it.id }) { post ->
                        PostItem(post = post, onUserClick = onUserClick)
                    }
                }
            }
        }
    }
}

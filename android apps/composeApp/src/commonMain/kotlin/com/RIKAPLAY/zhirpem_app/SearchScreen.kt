package com.RIKAPLAY.zhirpem_app

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.DynamicFeed
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
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.launch

enum class SearchTab { POSTS, USERS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialQuery: String? = null,
    onUserClick: (String) -> Unit,
    onPostClick: (String) -> Unit
) {
    var query by remember { mutableStateOf(initialQuery ?: "") }
    var selectedTab by remember { mutableStateOf(SearchTab.POSTS) }
    
    var allPosts by remember { mutableStateOf(listOf<Post>()) }
    var allUsers by remember { mutableStateOf(listOf<User>()) }
    
    var filteredPosts by remember { mutableStateOf(listOf<Post>()) }
    var filteredUsers by remember { mutableStateOf(listOf<User>()) }
    
    var isLoading by remember { mutableStateOf(true) }
    val db = Firebase.firestore
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            // Load all data for local searching as per original logic
            val postsSnapshot = db.collection("zhirpem_posts").get()
            allPosts = postsSnapshot.documents.map { it.data<Post>().copy(id = it.id) }
            
            val usersSnapshot = db.collection("users").get()
            allUsers = usersSnapshot.documents.map { it.data<User>().copy(id = it.id) }
            
            isLoading = false
        } catch (e: Exception) {
            isLoading = false
        }
    }

    LaunchedEffect(query, allPosts, allUsers) {
        filteredPosts = searchPosts(query, allPosts)
        filteredUsers = searchUsers(query, allUsers)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Поисковая строка
        SearchBar(
            query = query,
            onQueryChange = { query = it },
            onClear = { query = "" }
        )

        // Табы
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedTab == SearchTab.POSTS,
                onClick = { selectedTab = SearchTab.POSTS },
                text = { Text("Посты") },
                icon = { Icon(Icons.Default.DynamicFeed, null) }
            )
            Tab(
                selected = selectedTab == SearchTab.USERS,
                onClick = { selectedTab = SearchTab.USERS },
                text = { Text("Люди") },
                icon = { Icon(Icons.Default.Person, null) }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                when (selectedTab) {
                    SearchTab.POSTS -> {
                        if (filteredPosts.isEmpty()) {
                            EmptySearchState("Посты не найдены")
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filteredPosts) { post ->
                                    SearchPostItem(post, onClick = { onPostClick(post.id) })
                                }
                            }
                        }
                    }
                    SearchTab.USERS -> {
                        if (filteredUsers.isEmpty()) {
                            EmptySearchState("Пользователи не найдены")
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filteredUsers) { user ->
                                    SearchUserItem(user, onClick = { onUserClick(user.username) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp)),
        placeholder = { Text("Поиск по Жирпему...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        singleLine = true
    )
}

@Composable
fun EmptySearchState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = Color.Gray)
    }
}

@Composable
fun SearchUserItem(user: User, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = user.avatarUrl ?: "https://placehold.co/100x100.png",
            contentDescription = null,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(user.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("@${user.username}", color = Color.Gray, fontSize = 14.sp)
        }
    }
}

@Composable
fun SearchPostItem(post: Post, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = post.authorAvatarUrl ?: "https://placehold.co/100x100.png",
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(post.author, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(post.text, maxLines = 3, fontSize = 15.sp)
        }
    }
}

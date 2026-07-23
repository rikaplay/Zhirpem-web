package com.RIKAPLAY.zhirpem_app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.RIKAPLAY.zhirpem_app.platform.platformBlur
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.Direction
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFeedScreen(
    onUserClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit,
    onMenuClick: () -> Unit,
    onAdminAccess: () -> Unit,
    onShowWhatsNew: () -> Unit,
    currentAvatarUrl: String?,
    currentName: String,
    showBackupWarning: Boolean,
    onNavigateToSecurity: () -> Unit,
    onDismissBackupWarning: () -> Unit
) {
    val sessionManager = remember { SessionManager() }
    val db = Firebase.firestore
    
    var topNewsVersion by remember { mutableStateOf("") }
    // Skipping lastReadVersion logic for now as it needs a cross-platform data store
    val isNewsUnread = topNewsVersion.isNotEmpty()

    LaunchedEffect(Unit) {
        try {
            val snap = db.collection("update_news")
                .orderBy("timestamp", Direction.DESCENDING)
                .limit(1)
                .get()
            if (snap.documents.isNotEmpty()) {
                topNewsVersion = snap.documents.first().get<String>("version") ?: ""
            }
        } catch (e: Exception) {}
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val viewModel = remember { FeedViewModel() }
    val postsList by viewModel.postsList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    val tabs = listOf("Для вас", "Вы читаете", "Популярное", "Медиа")
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = tabs[selectedTabIndex]

    val listState = rememberLazyListState()

    var followingList by remember { mutableStateOf(setOf<String>()) }
    val myUsername = sessionManager.username ?: ""

    LaunchedEffect(myUsername) {
        if (myUsername.isNotEmpty()) {
            db.collection("follows")
                .whereEqualTo("follower", myUsername)
                .snapshots
                .collect { snapshot ->
                    followingList = snapshot.documents.map { it.get<String>("following") ?: "" }.toSet()
                }
        }
    }

    val filteredPosts by remember(selectedTab, postsList, followingList) {
        derivedStateOf {
            when (selectedTab) {
                "Медиа" -> postsList.filter { (it.isMedia || !it.imageUrl.isNullOrEmpty()) && !it.isAuthorBanned && it.communityId == null }
                "Вы читаете" -> postsList.filter { followingList.contains(it.handle.replace("@", "")) && !it.isAuthorBanned && it.communityId == null }
                "Популярное" -> postsList.filter { !it.isAuthorBanned && it.communityId == null }.sortedByDescending { it.likes }
                else -> postsList.filter { !it.isAuthorBanned && it.communityId == null }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { ZhirpemLogo(onAdminAccess = onAdminAccess) },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onMenuClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!currentAvatarUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = currentAvatarUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(currentName.take(1).uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                },
                actions = {
                    JumpingUpdateIcon(onClick = onShowWhatsNew, isJumping = isNewsUnread)
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (showBackupWarning) {
                BackupCodeWarningBanner(onCreateCode = onNavigateToSecurity, onDismiss = onDismissBackupWarning)
            }

            // Simplified Tabs for now
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                edgePadding = 16.dp,
                divider = {}
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(tab, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading && postsList.isEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(5) { ShimmerPostItem() }
                    }
                } else if (errorMessage != null) {
                    Text(errorMessage!!, color = Color.Red, modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(filteredPosts, key = { it.id }) { post ->
                            PostItem(post = post, onUserClick = onUserClick, onHashtagClick = onHashtagClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JumpingUpdateIcon(onClick: () -> Unit, isJumping: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "jumpingIcon")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2000
                if (isJumping) {
                    (-6f) at 300
                    (0f) at 600
                    (-3f) at 800
                    (0f) at 1000
                }
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "offsetY"
    )

    Box(
        modifier = Modifier
            .offset(y = offsetY.dp)
            .padding(end = 12.dp)
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Cached, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

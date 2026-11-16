package com.RIKAPLAY.zhirpem_app

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

private data class LeaderEntry(
    val username: String,
    val name: String,
    val avatarUrl: String?,
    val points: Int,
    val frame: String?,
    val bannerColor: String
)

private fun pointsOf(doc: com.google.firebase.firestore.DocumentSnapshot): Int =
    (doc.getLong("reputationPoints")
        ?: ((doc.getLong("reputation") ?: 0L) * ReputationManager.POINTS_PER_LEVEL)).toInt()

private fun levelOf(points: Int): Int = (points / ReputationManager.POINTS_PER_LEVEL).coerceIn(-10, 10)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(onBack: () -> Unit, onUserClick: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("user_session", Context.MODE_PRIVATE) }
    val me = (prefs.getString("username", "") ?: "").replace("@", "").trim()
    val db = remember { FirebaseFirestore.getInstance() }

    var entries by remember { mutableStateOf<List<LeaderEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var myPoints by remember { mutableStateOf<Int?>(null) }
    var myRank by remember { mutableStateOf<Int?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        isLoading = true
        loadError = false
        myRank = null
        myPoints = null

        db.collection("users")
            .orderBy("reputationPoints", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.map { doc ->
                    LeaderEntry(
                        username = doc.id,
                        name = doc.getString("name") ?: doc.id,
                        avatarUrl = doc.getString("avatarUrl"),
                        points = pointsOf(doc),
                        frame = doc.getString("equippedFrame")?.takeIf { it.isNotEmpty() },
                        bannerColor = doc.getString("bannerColor") ?: "#808080"
                    )
                }
                entries = list
                isLoading = false

                // Моё место: в топ-50 или отдельным запросом-счётчиком
                val idx = list.indexOfFirst { it.username == me }
                if (idx >= 0) {
                    myRank = idx + 1
                    myPoints = list[idx].points
                } else if (me.isNotEmpty()) {
                    db.collection("users").document(me).get().addOnSuccessListener { mine ->
                        val p = pointsOf(mine)
                        myPoints = p
                        db.collection("users").whereGreaterThan("reputationPoints", p)
                            .count().get(AggregateSource.SERVER)
                            .addOnSuccessListener { myRank = it.count.toInt() + 1 }
                    }
                }
            }
            .addOnFailureListener {
                loadError = true
                isLoading = false
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Рейтинг 🏆", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { reloadKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            loadError -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Не удалось загрузить рейтинг")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { reloadKey++ }) { Text("Повторить") }
                }
            }

            entries.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Пока никого нет в рейтинге")
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Моё место
                if (myRank != null && myPoints != null) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Ваше место", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Text("#$myRank", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("$myPoints очков", fontWeight = FontWeight.Bold)
                                    Text("Уровень ${levelOf(myPoints ?: 0)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }

                itemsIndexed(entries, key = { _, e -> e.username }) { index, entry ->
                    val rank = index + 1
                    val medal = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> null }
                    val rankColor = when (rank) {
                        1 -> Color(0xFFFFD700)
                        2 -> Color(0xFFC0C0C0)
                        3 -> Color(0xFFCD7F32)
                        else -> null
                    }
                    val isMe = entry.username == me
                    val banner = try {
                        Color(android.graphics.Color.parseColor(entry.bannerColor))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.primary
                    }

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        border = when {
                            rankColor != null -> BorderStroke(1.5.dp, rankColor.copy(alpha = 0.8f))
                            isMe -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                            else -> null
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onUserClick(entry.username) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                                if (medal != null) Text(medal, fontSize = 26.sp)
                                else Text("#$rank", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }

                            Spacer(Modifier.width(8.dp))

                            // Аватарка вместе с купленной рамкой
                            Box(modifier = Modifier.size(56.dp)) {
                                if (!entry.avatarUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = entry.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().padding(4.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(4.dp).clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(entry.name.take(1).uppercase(), fontWeight = FontWeight.Bold)
                                    }
                                }
                                AvatarFrameOverlay(frameId = entry.frame, bannerColor = banner)
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("@${entry.username}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("${entry.points}", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                                Text("Ур. ${levelOf(entry.points)}", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                            }
                        }
                    }
                }
            }
        }
    }
}

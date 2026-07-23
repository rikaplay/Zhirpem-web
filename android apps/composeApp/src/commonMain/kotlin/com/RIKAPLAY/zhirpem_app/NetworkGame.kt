package com.RIKAPLAY.zhirpem_app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameMillis
import com.RIKAPLAY.zhirpem_app.platform.NetworkMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

data class Obstacle(
    var x: Float,
    val width: Float = 40f,
    val height: Float = 60f
)

@Composable
fun PixelDinoGame(
    isHardcore: Boolean,
    onGameOver: (Int) -> Unit,
    highScore: Int
) {
    val smartphonePainter = rememberVectorPainter(Icons.Default.Smartphone)
    val serverPainter = rememberVectorPainter(Icons.Default.Storage)
    
    var playerY by remember { mutableStateOf(0f) }
    var playerVelocity by remember { mutableStateOf(0f) }
    var obstacles by remember { mutableStateOf(listOf<Obstacle>()) }
    var score by remember { mutableIntStateOf(0) }
    var gameSpeed by remember { mutableStateOf(5f) }
    var isJumping by remember { mutableStateOf(false) }
    var frames by remember { mutableIntStateOf(0) }

    val gravity = 0.8f
    val jumpStrength = -15f
    val speedIncrement = if (isHardcore) 0.005f * 2.5f else 0.005f

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis {
                frames++
                playerVelocity += gravity
                playerY += playerVelocity
                if (playerY > 0) {
                    playerY = 0f
                    playerVelocity = 0f
                    isJumping = false
                }

                obstacles = obstacles.map { it.copy(x = it.x - gameSpeed) }.filter { it.x > -100 }

                if (frames % 100 == 0 || (obstacles.isEmpty() && frames > 50)) {
                    if (Random.nextFloat() > 0.7f || obstacles.isEmpty()) {
                        obstacles = obstacles + Obstacle(x = 1000f)
                    }
                }

                val playerRect = GameRect(50f, 200f + playerY - 40f, 50f + 40f, 200f + playerY)
                obstacles.forEach { obs ->
                    val obsRect = GameRect(obs.x, 200f - obs.height, obs.x + obs.width, 200f)
                    if (playerRect.overlaps(obsRect)) {
                        onGameOver(score)
                    }
                }

                score++
                gameSpeed += speedIncrement
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown()
                        if (!isJumping) {
                            playerVelocity = jumpStrength
                            isJumping = true
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val groundY = 200.dp.toPx()
            val playerX = 50.dp.toPx()
            val playerSize = 40.dp.toPx()
            
            drawLine(color = Color.Gray, start = Offset(0f, groundY), end = Offset(size.width, groundY), strokeWidth = 2.dp.toPx())

            translate(left = playerX, top = groundY + playerY.dp.toPx() - playerSize) {
                val pSize = Size(playerSize, playerSize)
                with(smartphonePainter) { draw(size = pSize) }
            }

            obstacles.forEach { obs ->
                translate(left = obs.x.dp.toPx(), top = groundY - obs.height.dp.toPx()) {
                    val oSize = Size(obs.width.dp.toPx(), obs.height.dp.toPx())
                    with(serverPainter) { draw(size = oSize) }
                }
            }
        }
        
        Column(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
            Text("Счет: $score", fontWeight = FontWeight.Bold, color = Color.Gray)
            Text("Лучший: $highScore", fontSize = 12.sp, color = Color.Gray.copy(alpha = 0.7f))
        }
    }
}

private data class GameRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun overlaps(other: GameRect): Boolean = left < other.right && right > other.left && top < other.bottom && bottom > other.top
}

@Composable
fun NetworkGameScreen(onConnected: () -> Unit, onDismiss: () -> Unit) {
    val settingsManager = remember { SettingsManager() }
    val networkMonitor = remember { NetworkMonitor() }
    
    var gameState by remember { mutableStateOf("MENU") }
    var highScore by remember { mutableIntStateOf(settingsManager.gameHighScore) }
    var isHardcore by remember { mutableStateOf(settingsManager.isGameHardcore) }
    var lastScore by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (isActive) {
            if (networkMonitor.isActuallyConnected()) onConnected()
            delay(3000L)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("Ожидание сети", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 32.dp))

            when (gameState) {
                "MENU" -> {
                    GameButton("Играть", Icons.Default.PlayArrow) { gameState = "PLAYING" }
                    Spacer(modifier = Modifier.height(12.dp))
                    GameButton("Настройки", Icons.Default.Settings) { gameState = "SETTINGS" }
                    Spacer(modifier = Modifier.height(24.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Вернуться в приложение (offline)", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                }
                "PLAYING" -> {
                    PixelDinoGame(isHardcore = isHardcore, highScore = highScore, onGameOver = { score ->
                        lastScore = score
                        if (score > highScore) {
                            highScore = score
                            settingsManager.gameHighScore = score
                        }
                        gameState = "GAMEOVER"
                    })
                }
                "GAMEOVER" -> {
                    Text("ИГРА ОКОНЧЕНА", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                    Text("Ваш счет: $lastScore", fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    GameButton("Попробовать снова", Icons.Default.PlayArrow) { gameState = "PLAYING" }
                    GameButton("В меню", null) { gameState = "MENU" }
                }
                "SETTINGS" -> {
                    Text("Настройки", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                        Text("Hardcore режим")
                        Switch(checked = isHardcore, onCheckedChange = { isHardcore = it; settingsManager.isGameHardcore = it })
                    }
                    GameButton("Назад", null) { gameState = "MENU" }
                }
            }
        }
    }
}

@Composable
fun GameButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(0.7f).height(56.dp), shape = RoundedCornerShape(16.dp)) {
        if (icon != null) {
            Icon(icon, null)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun NetworkStabilityWrapper(content: @Composable () -> Unit) {
    val networkMonitor = remember { NetworkMonitor() }
    var isConnected by remember { mutableStateOf(true) }
    var showGame by remember { mutableStateOf(false) }
    var isManuallyDismissed by remember { mutableStateOf(false) }
    var offlineTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val currentStatus = networkMonitor.isActuallyConnected()
            if (currentStatus) {
                isConnected = true; showGame = false; isManuallyDismissed = false; offlineTime = 0L
            } else {
                if (isConnected) {
                    isConnected = false; offlineTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                } else {
                    if (!showGame && !isManuallyDismissed && offlineTime > 0 && kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - offlineTime > 10000) {
                        showGame = true
                    }
                }
            }
            delay(1000L)
        }
    }

    if (showGame && !isManuallyDismissed) {
        NetworkGameScreen(onConnected = { isConnected = true; showGame = false; isManuallyDismissed = false }, onDismiss = { isManuallyDismissed = true })
    } else {
        content()
    }
}

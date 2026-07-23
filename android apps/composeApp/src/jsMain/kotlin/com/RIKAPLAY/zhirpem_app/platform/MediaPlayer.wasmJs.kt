package com.RIKAPLAY.zhirpem_app.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import coil3.compose.AsyncImage

@Composable
actual fun GifPlayer(
    gifUrl: String,
    modifier: Modifier
) {
    // Coil 3 automatically handles Gifs on Web if configured
    AsyncImage(
        model = gifUrl,
        contentDescription = "GIF",
        modifier = modifier
    )
}

@Composable
actual fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier
) {
    // For WasmJs, we can eventually use HTML Interop to show a <video> tag
    // For now, a placeholder
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        Text("Видео не поддерживается в этой версии веба", color = Color.White)
    }
}

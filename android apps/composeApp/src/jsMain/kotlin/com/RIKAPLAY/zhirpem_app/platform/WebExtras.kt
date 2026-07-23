package com.RIKAPLAY.zhirpem_app.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun CameraView(
    modifier: Modifier,
    onMediaCaptured: (String, Boolean) -> Unit,
    onClose: () -> Unit
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text("Камера не поддерживается в браузере", color = Color.White)
    }
}

@Composable
actual fun rememberImagePicker(onImagePicked: (String) -> Unit): () -> Unit {
    return {
        // Here you would normally trigger a hidden <input type="file">
    }
}

actual object SoundPlayer {
    actual fun playSplashSound() {}
    actual fun release() {}
}

actual class NotificationSender {
    actual suspend fun sendGlobalPush(title: String, body: String, imageUrl: String): Boolean {
        return false
    }
}

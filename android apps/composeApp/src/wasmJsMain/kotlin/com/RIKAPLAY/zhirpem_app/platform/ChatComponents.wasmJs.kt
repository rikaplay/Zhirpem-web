package com.RIKAPLAY.zhirpem_app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
actual fun ChatInputBar(onSendText: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Сообщение (Web)...") },
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onSendText(text)
                    text = ""
                }
            },
            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White)
        }
    }
}

@Composable
actual fun VideoMessageBubble(videoUrl: String, isMyMessage: Boolean) {
    Box(
        modifier = Modifier
            .size(150.dp)
            .background(Color.DarkGray, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("Видео (Web)", color = Color.White)
    }
}

@Composable
actual fun VoiceMessageBubble(audioUrl: String, isMyMessage: Boolean) {
    Box(
        modifier = Modifier
            .width(200.dp)
            .height(40.dp)
            .background(Color.Gray, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("Голосовое (Web)", color = Color.White)
    }
}

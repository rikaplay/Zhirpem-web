package com.RIKAPLAY.zhirpem_app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Реакция на сообщение. Повторное нажатие на ту же реакцию снимает её,
 * другая реакция заменяет прежнюю (у каждого человека не больше одной реакции на сообщение).
 */
fun toggleReaction(db: FirebaseFirestore, chatId: String, message: Message, userId: String, emoji: String) {
    if (userId.isBlank()) return
    val ref = db.collection("chats").document(chatId).collection("messages").document(message.id)
    if (message.reactions[userId] == emoji) {
        ref.update("reactions.$userId", FieldValue.delete())
    } else {
        ref.update("reactions.$userId", emoji)
    }
}

/** Реакции под сообщением: смайлик и число, моя подсвечена, нажатие ставит или снимает реакцию. */
@Composable
fun ReactionChips(
    reactions: Map<String, String>,
    myUserId: String,
    isMyMessage: Boolean,
    onToggle: (String) -> Unit
) {
    if (reactions.isEmpty()) return
    val groups = remember(reactions) {
        reactions.values.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6)
    }
    val myReaction = reactions[myUserId]

    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        groups.forEach { (emoji, count) ->
            ReactionChip(
                emoji = emoji,
                count = count,
                mine = myReaction == emoji,
                onDarkBubble = isMyMessage,
                onClick = { onToggle(emoji) }
            )
        }
    }
}

@Composable
private fun ReactionChip(emoji: String, count: Int, mine: Boolean, onDarkBubble: Boolean, onClick: () -> Unit) {
    // При появлении и при изменении числа реакция «подпрыгивает»
    val scale = remember { Animatable(1f) }
    LaunchedEffect(count, mine) {
        scale.snapTo(0.7f)
        scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
    }
    val primary = MaterialTheme.colorScheme.primary
    val background = when {
        mine -> if (onDarkBubble) Color.White.copy(alpha = 0.32f) else primary.copy(alpha = 0.28f)
        onDarkBubble -> Color.White.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    }
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 13.sp)
        if (count > 1) {
            Spacer(Modifier.width(3.dp))
            Text(
                text = count.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (onDarkBubble) Color.White.copy(alpha = 0.9f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

/** Строка поиска в верхней панели чата. */
@Composable
fun ChatSearchField(query: String, onQueryChange: (String) -> Unit, resultText: String) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Поиск по сообщениям",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
        }
        if (resultText.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(resultText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = "Очистить",
                modifier = Modifier.size(18.dp).clickable { onQueryChange("") },
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

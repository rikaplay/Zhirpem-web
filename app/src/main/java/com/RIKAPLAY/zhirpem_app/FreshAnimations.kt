package com.RIKAPLAY.zhirpem_app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Мягкое появление элемента при первом показе: плавно проявляется и чуть поднимается снизу.
 * Для постов в ленте: карточки «вплывают» при прокрутке. Если анимации отключены в настройках, ничего не делает.
 */
@Composable
fun Modifier.appearOnce(): Modifier {
    val enabled = LocalAnimationsEnabled.current
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            progress.animateTo(1f, tween(durationMillis = 340, easing = FastOutSlowInEasing))
        }
    }
    return this.graphicsLayer {
        val p = progress.value
        if (p < 1f) {
            alpha = p
            translationY = (1f - p) * 32.dp.toPx()
        }
    }
}

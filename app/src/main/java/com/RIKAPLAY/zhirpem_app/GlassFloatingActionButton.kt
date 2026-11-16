package com.RIKAPLAY.zhirpem_app

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp

@Composable
fun GlassFloatingActionButton(
    onClick: () -> Unit,
    isGlassEnabled: Boolean,
    glassAlpha: Float,
    modifier: Modifier = Modifier,
    // Снимок экрана под кнопкой (для преломления) и положение его левого верхнего угла в окне
    backdrop: GraphicsLayer? = null,
    backdropOrigin: () -> Offset = { Offset.Zero }
) {
    val isDark = isSystemInDarkTheme()
    val panelShader = remember { if (Build.VERSION.SDK_INT >= 33) createPanelShader() else null }
    val refractionOn = isGlassEnabled && LocalAnimationsEnabled.current &&
        panelShader != null && backdrop != null
    var fabPos by remember { mutableStateOf(Offset.Zero) }

    // Динамическая логика для обеспечения видимости на светлой теме
    val dynamicAlpha = if (!isDark && isGlassEnabled) (glassAlpha + 0.15f).coerceAtMost(1f) else glassAlpha
    val dynamicBackground = if (isGlassEnabled) {
        if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = dynamicAlpha)
        else Color.White.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.primary
    }

    val dynamicBorderColor = if (isGlassEnabled) {
        if (isDark) Color.White.copy(alpha = 0.15f)
        else Color.Black.copy(alpha = 0.1f)
    } else {
        Color.Transparent
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .onGloballyPositioned { fabPos = it.positionInWindow() }
            .bounceClick { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // 1. Динамический фон
        if (refractionOn && panelShader != null && backdrop != null) {
            // Liquid Glass: под кнопкой рисуется снимок экрана, а шейдер преломляет его —
            // линза по всему кругу, скос у края с цветной каймой и бликом
            val tint = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = dynamicAlpha * 0.65f)
            else Color.White.copy(alpha = 0.35f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        shape = CircleShape
                        compositingStrategy = CompositingStrategy.Offscreen
                        if (Build.VERSION.SDK_INT >= 33) {
                            val r = size.width / 2f
                            renderEffect = buildPanelEffect(
                                shader = panelShader,
                                width = size.width,
                                height = size.height,
                                bevel = 14.dp.toPx(),
                                strength = 0.7f,
                                lensX = r,
                                lensGlow = 0.5f,
                                lensCy = size.height / 2f,
                                lensRadius = r * 0.95f,
                                lensAmt = 0.5f,
                                blurPx = 2.dp.toPx(),
                                bodyAmt = 0f,
                                cornerR = r
                            )
                        }
                    }
                    .drawBehind {
                        val o = fabPos - backdropOrigin()
                        translate(left = -o.x, top = -o.y) { drawLayer(backdrop) }
                    }
                    .background(tint)
            )
            // Обводка поверх, сама не искажается
            Box(Modifier.fillMaxSize().border(1.dp, dynamicBorderColor, CircleShape))
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .then(
                        if (isGlassEnabled) {
                            Modifier
                                .blur(12.dp)
                                .background(dynamicBackground)
                                .border(1.dp, dynamicBorderColor, CircleShape)
                        } else {
                            Modifier.background(MaterialTheme.colorScheme.primary)
                        }
                    )
            )
        }

        // 2. Иконка
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Создать",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(24.dp)
        )
    }
}

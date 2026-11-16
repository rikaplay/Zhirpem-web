package com.RIKAPLAY.zhirpem_app

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LiquidLens(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val glassEnabled = LocalGlassEnabled.current

    Box(
        modifier = modifier
            .clip(CircleShape)
            .then(
                if (animationsEnabled && glassEnabled) {
                    Modifier.graphicsLayer {
                        // Эффект размытия того, что под линзой (Android 12+)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                25f, 25f, android.graphics.Shader.TileMode.DECAL
                            ).asComposeRenderEffect()
                        }
                    }
                } else {
                    Modifier
                }
            )
            .drawWithContent {
                // Рисуем содержимое
                drawContent()
                
                // Эффект преломления: градиентная обводка имитирующая стекло
                val strokeWidth = 2.dp.toPx()
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.8f),
                            Color.White.copy(alpha = 0.1f),
                            Color.White.copy(alpha = 0.4f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    ),
                    style = Stroke(width = strokeWidth)
                )
                // мягкий свет по краю стекла (только с включённым эффектом стекла)
                if (glassEnabled) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.14f)),
                            center = center,
                            radius = size.minDimension / 2f
                        )
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun FluidSwipeBottomBar(
    isGlassEnabled: Boolean,
    glassAlpha: Float,
    items: List<Pair<String, String>>,
    selectedLabel: String,
    onTabSelected: (String) -> Unit,
    // Снимок экрана под панелью (для преломления) и положение его левого верхнего угла в окне
    backdrop: GraphicsLayer? = null,
    backdropOrigin: () -> Offset = { Offset.Zero }
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sharedPrefs = remember { context.getSharedPreferences("user_session", Context.MODE_PRIVATE) }
    val isVibrationEnabled = remember(sharedPrefs) { sharedPrefs.getBoolean("vibration_enabled", true) }

    var barWidth by remember { mutableFloatStateOf(0f) }
    var barPos by remember { mutableStateOf(Offset.Zero) }
    var dragX by remember { mutableFloatStateOf(-1f) }
    var isPressing by remember { mutableStateOf(false) }
    
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    val selectedIndex = items.indexOfFirst { it.second == selectedLabel }

    // 1. Динамическая логика прозрачности и цвета (для светлой темы делаем плотнее)
    val dynamicAlpha = if (!isDark && isGlassEnabled) (glassAlpha + 0.15f).coerceAtMost(1f) else glassAlpha
    val dynamicBackground = if (isGlassEnabled) {
        if (isDark) MaterialTheme.colorScheme.surface.copy(alpha = dynamicAlpha)
        else Color.White.copy(alpha = 0.7f) // Эффект матового стекла на светлой теме
    } else {
        MaterialTheme.colorScheme.surface
    }

    val dynamicBorderColor = if (isGlassEnabled) {
        if (isDark) Color.White.copy(alpha = 0.15f)
        else Color.Black.copy(alpha = 0.1f) // Темная обводка для видимости на светлом фоне
    } else {
        Color.Transparent
    }

    // Вычисляем текущий активный индекс на основе позиции пальца или выбора
    val segmentWidthPx = if (items.isNotEmpty()) barWidth / items.size else 0f
    val activeIndex = if (dragX != -1f && segmentWidthPx > 0) {
        (dragX / segmentWidthPx).toInt().coerceIn(0, items.size - 1)
    } else {
        selectedIndex
    }

    // Вибрация при смене активного индекса во время перетаскивания
    var lastVibratedIndex by remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(activeIndex, isPressing) {
        if (isPressing && activeIndex != lastVibratedIndex) {
            if (isVibrationEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastVibratedIndex = activeIndex
        }
    }

    // Анимация смещения и размера пузыря
    val targetOffsetPx = if (dragX != -1f) {
        dragX - segmentWidthPx / 2
    } else {
        segmentWidthPx * selectedIndex
    }

    val indicatorOffset by animateDpAsState(
        targetValue = with(density) { targetOffsetPx.coerceIn(0f, barWidth - segmentWidthPx).toDp() },
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "indicatorOffset"
    )

    val indicatorSize by animateDpAsState(
        targetValue = if (isPressing) 52.dp else 46.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "indicatorSize"
    )

    // Преломление под линзой (AGSL, Android 13+): иконки и стекло под пузырём увеличиваются
    // Два шейдера: линза под пузырём (иконки) и стеклянная кромка по всей панели
    val iconLensShader = remember { if (Build.VERSION.SDK_INT >= 33) createLensShader() else null }
    val panelShader = remember { if (Build.VERSION.SDK_INT >= 33) createPanelShader() else null }
    val refractionOn = isGlassEnabled && LocalAnimationsEnabled.current && iconLensShader != null && panelShader != null
    val backdropOn = refractionOn && backdrop != null
    // Когда под панелью настоящий экран, собственная заливка должна быть прозрачнее
    val glassTint = if (backdropOn) dynamicBackground.copy(alpha = dynamicBackground.alpha * 0.3f) else dynamicBackground
    val lensStrength by animateFloatAsState(
        targetValue = if (isPressing) 0.3f else 0.2f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "lensStrength"
    )
    val panelGlow by animateFloatAsState(
        targetValue = if (isPressing) 1f else 0.5f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "panelGlow"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(64.dp)
            .onGloballyPositioned {
                barWidth = it.size.width.toFloat()
                barPos = it.positionInWindow()
            }
            .pointerInput(items.size) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isPressing = true
                        dragX = offset.x
                    },
                    onDrag = { change, _ ->
                        dragX = change.position.x
                    },
                    onDragEnd = {
                        isPressing = false
                        if (dragX != -1f && segmentWidthPx > 0) {
                            val finalIndex = (dragX / segmentWidthPx).toInt().coerceIn(0, items.size - 1)
                            onTabSelected(items[finalIndex].second)
                        }
                        dragX = -1f
                    },
                    onDragCancel = { 
                        isPressing = false
                        dragX = -1f 
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Фон панели. С включённым стеклом внутри рисуется снимок экрана под панелью,
        // а шейдер его преломляет (Liquid Glass): скос по краю, линза под пузырём, лёгкая матовость.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (refractionOn) Modifier.graphicsLayer {
                    clip = true
                    shape = CircleShape
                    compositingStrategy = CompositingStrategy.Offscreen
                    val shader = panelShader
                    renderEffect = if (Build.VERSION.SDK_INT >= 33 && shader != null &&
                        refractionOn && segmentWidthPx > 0f && selectedIndex != -1
                    ) {
                        val lensCx = indicatorOffset.toPx() + segmentWidthPx / 2f
                        buildPanelEffect(
                            shader = shader,
                            width = size.width,
                            height = size.height,
                            bevel = if (backdropOn) 24.dp.toPx() else 12.dp.toPx(),
                            strength = if (backdropOn) 0.8f else 0.35f,
                            lensX = lensCx,
                            lensGlow = panelGlow,
                            lensCy = size.height / 2f,
                            lensRadius = indicatorSize.toPx() / 2f,
                            lensAmt = if (backdropOn) lensStrength * 1.3f else 0f,
                            blurPx = if (backdropOn) 3.dp.toPx() else 0f,
                            bodyAmt = if (backdropOn) 0.28f else 0f
                        )
                    } else null
                } else Modifier)
                .then(
                    if (backdropOn && backdrop != null) {
                        Modifier.drawBehind {
                            val o = barPos - backdropOrigin()
                            translate(left = -o.x, top = -o.y) { drawLayer(backdrop) }
                        }
                    } else Modifier
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .then(
                        when {
                            !isGlassEnabled -> Modifier.background(MaterialTheme.colorScheme.surface)
                            backdropOn -> Modifier.background(glassTint)
                            else -> Modifier.blur(20.dp).background(dynamicBackground)
                        }
                    )
            )
        }

        // Тонкая обводка панели (поверх фона, не преломляется)
        if (isGlassEnabled) {
            Box(Modifier.fillMaxSize().border(1.dp, dynamicBorderColor, CircleShape))
        }

        // Линза без эффекта стекла лежит под иконками, как в исходной версии
        if (!isGlassEnabled && activeIndex != -1) {
            val segmentWidthDp = with(density) { segmentWidthPx.toDp() }
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset + (segmentWidthDp - indicatorSize) / 2)
                    .size(indicatorSize)
                    .align(Alignment.CenterStart)
            ) {
                LiquidLens(modifier = Modifier.fillMaxSize()) {}
            }
        }

        // Внутренний слой: преломление иконок (только под самой линзой)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (refractionOn) Modifier.graphicsLayer {
                    clip = true
                    compositingStrategy = CompositingStrategy.Offscreen
                    val shader = iconLensShader
                    renderEffect = if (Build.VERSION.SDK_INT >= 33 && shader != null &&
                        refractionOn && segmentWidthPx > 0f && selectedIndex != -1
                    ) {
                        buildLensEffect(
                            shader,
                            indicatorOffset.toPx() + segmentWidthPx / 2f,
                            size.height / 2f,
                            indicatorSize.toPx() / 2f,
                            lensStrength,
                            0.18f
                        )
                    } else null
                } else Modifier)
        ) {
        // 3. Слой контента (Только иконки)
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, (icon, label) ->
                val isTargeted = index == activeIndex
                
                // Анимация масштаба иконки
                val iconScale by animateFloatAsState(
                    targetValue = if (isTargeted) (if (isGlassEnabled) 1.12f else 1.25f) else 1.0f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
                    label = "iconScale"
                )

                // Анимация цвета иконки
                val iconColor by animateColorAsState(
                    targetValue = if (isTargeted) MaterialTheme.colorScheme.primary 
                                  else Color.Gray.copy(alpha = 0.5f),
                    animationSpec = spring(),
                    label = "iconColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .bounceClick { onTabSelected(label) },
                    contentAlignment = Alignment.Center
                ) {
                    // icon — имя иконки Solar (home, search, bell, chat), у выбранной вкладки она «жирная»
                    androidx.compose.material3.Icon(
                        imageVector = SolarIcons.get(icon, bold = isTargeted),
                        contentDescription = label,
                        tint = iconColor,
                        modifier = Modifier
                            .size(26.dp)
                            .scale(iconScale)
                    )
                }
            }
        }
        }

        // 2. Слой "Жидкой линзы" (со стеклом — поверх иконок)
        if (isGlassEnabled && activeIndex != -1) {
            val segmentWidthDp = with(density) { segmentWidthPx.toDp() }
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset + (segmentWidthDp - indicatorSize) / 2)
                    .size(indicatorSize)
                    .align(Alignment.CenterStart)
            ) {
                LiquidLens(modifier = Modifier.fillMaxSize()) {}
            }
        }

    }
}

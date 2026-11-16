package com.RIKAPLAY.zhirpem_app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class LeafState(
    var xPercent: Float,
    var yPx: Float,
    var speed: Float,
    var rotation: Float,
    var rotationSpeed: Float,
    var swayPhase: Float,
    var swaySpeed: Float,
    var swayAmplitude: Float,
    var scale: Float,
    var alpha: Float,
    var leafType: Int,
    var color: Color,
    var veinColor: Color
)

@Composable
fun FallingLeavesBackground(
    modifier: Modifier = Modifier,
    leafCount: Int = 18
) {
    val isEnabled = LocalFallingLeavesEnabled.current
    // В режиме «облегчённых анимаций» листья не рисуем: это постоянная покадровая перерисовка
    val animationsOn = LocalAnimationsEnabled.current
    if (!isEnabled || !animationsOn) return

    var frameTrigger by remember { mutableLongStateOf(0L) }

    val maplePath = remember { createMapleLeafPath() }
    val mapleVeins = remember { createMapleVeinsPath() }
    val oakPath = remember { createOakLeafPath() }
    val oakVeins = remember { createOakVeinsPath() }
    val birchPath = remember { createBirchLeafPath() }
    val birchVeins = remember { createBirchVeinsPath() }

    val autumnColors = remember {
        listOf(
            Color(0xFFD32F2F), // Scarlet Red
            Color(0xFFE65100), // Dark Amber / Warm Orange
            Color(0xFFFF8F00), // Warm Gold
            Color(0xFFF57F17), // Autumn Yellow
            Color(0xFFA04000), // Chestnut Brown
            Color(0xFF827717), // Olive Gold
            Color(0xFFC0392B)  // Crimson
        )
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val leaves = remember {
        List(leafCount) {
            createRandomLeaf(autumnColors)
        }
    }

    // Initial scatter down the screen when screen opens
    LaunchedEffect(canvasSize) {
        if (canvasSize.height > 0) {
            val h = canvasSize.height.toFloat()
            leaves.forEachIndexed { index, leaf ->
                if (leaf.yPx <= 0f) {
                    leaf.yPx = (index.toFloat() / leafCount.toFloat()) * h - 50f
                }
            }
        }
    }

    // Animation frame loop
    LaunchedEffect(isEnabled) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.08f)
                    val h = if (canvasSize.height > 0) canvasSize.height.toFloat() else 2000f

                    for (leaf in leaves) {
                        leaf.yPx += leaf.speed * dt
                        leaf.rotation += leaf.rotationSpeed * dt
                        leaf.swayPhase += leaf.swaySpeed * dt

                        if (leaf.yPx > h + 60f) {
                            resetLeaf(leaf, autumnColors)
                        }
                    }
                }
                lastNanos = nanos
                frameTrigger = nanos
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
    ) {
        @Suppress("UNUSED_EXPRESSION")
        frameTrigger

        val widthPx = size.width
        if (widthPx <= 0f) return@Canvas

        for (leaf in leaves) {
            val swayX = sin(leaf.swayPhase) * leaf.swayAmplitude
            val posX = leaf.xPercent * widthPx + swayX
            val posY = leaf.yPx

            val tumbleX = (0.35f + 0.65f * abs(cos(leaf.swayPhase))).coerceIn(0.2f, 1f)

            // Без создания Pair на каждый лист в каждом кадре
            val path = when (leaf.leafType) {
                0 -> maplePath
                1 -> oakPath
                else -> birchPath
            }
            val veins = when (leaf.leafType) {
                0 -> mapleVeins
                1 -> oakVeins
                else -> birchVeins
            }

            withTransform({
                translate(left = posX, top = posY)
                rotate(degrees = leaf.rotation)
                scale(scaleX = leaf.scale * tumbleX, scaleY = leaf.scale)
            }) {
                drawPath(
                    path = path,
                    color = leaf.color,
                    alpha = leaf.alpha
                )
                drawPath(
                    path = veins,
                    color = leaf.veinColor,
                    alpha = leaf.alpha * 0.8f
                )
            }
        }
    }
}

private fun createRandomLeaf(autumnColors: List<Color>): LeafState {
    val leaf = LeafState(
        xPercent = Random.nextFloat(),
        yPx = -100f,
        speed = 0f,
        rotation = 0f,
        rotationSpeed = 0f,
        swayPhase = 0f,
        swaySpeed = 0f,
        swayAmplitude = 0f,
        scale = 1f,
        alpha = 0.8f,
        leafType = 0,
        color = Color.Unspecified,
        veinColor = Color.Unspecified
    )
    resetLeaf(leaf, autumnColors)
    return leaf
}

private fun resetLeaf(leaf: LeafState, autumnColors: List<Color>) {
    leaf.xPercent = Random.nextFloat()
    leaf.yPx = -60f - Random.nextFloat() * 120f
    leaf.speed = 90f + Random.nextFloat() * 110f
    leaf.rotation = Random.nextFloat() * 360f
    leaf.rotationSpeed = -50f + Random.nextFloat() * 100f
    leaf.swayPhase = Random.nextFloat() * 6.28f
    leaf.swaySpeed = 1.2f + Random.nextFloat() * 2.0f
    leaf.swayAmplitude = 25f + Random.nextFloat() * 40f
    leaf.scale = 0.75f + Random.nextFloat() * 0.65f
    leaf.alpha = 0.45f + Random.nextFloat() * 0.45f
    leaf.leafType = Random.nextInt(3)
    leaf.color = autumnColors.random()
    leaf.veinColor = Color.Black.copy(alpha = 0.25f)
}

// --- LEAF PATH BUILDERS ---

private fun createMapleLeafPath(): Path = Path().apply {
    moveTo(0f, 16f)
    lineTo(0f, 8f)
    cubicTo(-4f, 10f, -8f, 10f, -10f, 6f)
    cubicTo(-8f, 5f, -6f, 5f, -6f, 4f)
    cubicTo(-12f, 4f, -18f, 0f, -18f, -4f)
    cubicTo(-14f, -3f, -10f, -2f, -8f, -4f)
    cubicTo(-12f, -10f, -10f, -16f, -7f, -18f)
    cubicTo(-5f, -14f, -4f, -10f, -2f, -8f)
    cubicTo(-3f, -16f, 0f, -24f, 0f, -24f)
    cubicTo(0f, -24f, 3f, -16f, 2f, -8f)
    cubicTo(4f, -10f, 5f, -14f, 7f, -18f)
    cubicTo(10f, -16f, 12f, -10f, 8f, -4f)
    cubicTo(10f, -2f, 14f, -3f, 18f, -4f)
    cubicTo(18f, 0f, 12f, 4f, 6f, 4f)
    cubicTo(6f, 5f, 8f, 5f, 10f, 6f)
    cubicTo(8f, 10f, 4f, 10f, 0f, 8f)
    close()
}

private fun createMapleVeinsPath(): Path = Path().apply {
    moveTo(0f, 12f)
    lineTo(0f, -20f)
    moveTo(0f, 6f)
    lineTo(-14f, -3f)
    moveTo(0f, 6f)
    lineTo(14f, -3f)
    moveTo(0f, -2f)
    lineTo(-6f, -15f)
    moveTo(0f, -2f)
    lineTo(6f, -15f)
    moveTo(0f, 8f)
    lineTo(-8f, 5f)
    moveTo(0f, 8f)
    lineTo(8f, 5f)
}

private fun createOakLeafPath(): Path = Path().apply {
    moveTo(0f, 20f)
    lineTo(0f, 12f)
    cubicTo(-6f, 12f, -10f, 9f, -8f, 6f)
    cubicTo(-5f, 5f, -4f, 5f, -5f, 3f)
    cubicTo(-12f, 2f, -14f, -3f, -10f, -6f)
    cubicTo(-6f, -5f, -4f, -4f, -5f, -8f)
    cubicTo(-10f, -10f, -11f, -16f, -7f, -18f)
    cubicTo(-4f, -16f, -3f, -14f, -1f, -12f)
    lineTo(0f, -22f)
    lineTo(1f, -12f)
    cubicTo(3f, -14f, 4f, -16f, 7f, -18f)
    cubicTo(11f, -16f, 10f, -10f, 5f, -8f)
    cubicTo(4f, -4f, 6f, -5f, 10f, -6f)
    cubicTo(14f, -3f, 12f, 2f, 5f, 3f)
    cubicTo(4f, 5f, 5f, 5f, 8f, 6f)
    cubicTo(10f, 9f, 6f, 12f, 0f, 12f)
    close()
}

private fun createOakVeinsPath(): Path = Path().apply {
    moveTo(0f, 16f)
    lineTo(0f, -18f)
    moveTo(0f, 8f); lineTo(-6f, 5f)
    moveTo(0f, 8f); lineTo(6f, 5f)
    moveTo(0f, 1f); lineTo(-8f, -4f)
    moveTo(0f, 1f); lineTo(8f, -4f)
    moveTo(0f, -7f); lineTo(-6f, -14f)
    moveTo(0f, -7f); lineTo(6f, -14f)
}

private fun createBirchLeafPath(): Path = Path().apply {
    moveTo(0f, 18f)
    lineTo(0f, 10f)
    cubicTo(-12f, 8f, -15f, -2f, -12f, -10f)
    cubicTo(-9f, -16f, -4f, -20f, 0f, -24f)
    cubicTo(4f, -20f, 9f, -16f, 12f, -10f)
    cubicTo(15f, -2f, 12f, 8f, 0f, 10f)
    close()
}

private fun createBirchVeinsPath(): Path = Path().apply {
    moveTo(0f, 14f)
    lineTo(0f, -20f)
    for (i in -12..8 step 5) {
        val y = i.toFloat()
        moveTo(0f, y)
        lineTo(-8f, y - 5f)
        moveTo(0f, y)
        lineTo(8f, y - 5f)
    }
}

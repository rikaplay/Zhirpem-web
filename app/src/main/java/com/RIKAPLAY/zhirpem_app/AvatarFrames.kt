package com.RIKAPLAY.zhirpem_app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Редкость предмета. Цвет используется для плашки и подсветки в магазине. */
enum class FrameRarity(val title: String, val color: Color) {
    COMMON("Обычный", Color(0xFF9E9E9E)),       // серый
    UNCOMMON("Необычный", Color(0xFF2196F3)),   // синий
    RARE("Редкий", Color(0xFF4CAF50)),          // зелёный
    EPIC("Эпический", Color(0xFF9C27B0)),       // фиолетовый
    LEGENDARY("Легендарный", Color(0xFFFF9800)) // оранжевый
}

/** Как рисуется рамка. */
sealed class FrameStyle {
    /** Градиентное кольцо. [spin] — вращение, [glow] — свечение, [pulse] — «дыхание». */
    data class Gradient(
        val colors: List<Color>,
        val spin: Boolean = true,
        val glow: Boolean = true,
        val pulse: Boolean = false,
        val width: Float = 0.09f
    ) : FrameStyle()

    /** Вращающийся пунктир. */
    data class Dashed(
        val color: Color,
        val dashes: Int = 16,
        val glow: Boolean = false,
        val width: Float = 0.08f
    ) : FrameStyle()

    /** Два тонких кольца. */
    data class DoubleRing(
        val outer: Color,
        val inner: Color,
        val pulse: Boolean = false
    ) : FrameStyle()

    /** Пиксельный ободок. Если [palette] == null — берётся цвет баннера. */
    data class Pixel(val palette: List<Color>? = null) : FrameStyle()

    /** Золотое кольцо с бликом и окантовкой. */
    data object Gold : FrameStyle()
}

/** Точки, которые бегают по кольцу. [speed] — целое число оборотов за цикл (минус — в обратную сторону). */
data class FrameOrbit(val color: Color, val count: Int, val speed: Int = 1)

data class AvatarFrame(
    val id: String,
    val name: String,
    val description: String,
    val rarity: FrameRarity,
    val price: Int, // в очках репутации
    val style: FrameStyle,
    val orbit: FrameOrbit? = null
)

object AvatarFrames {
    const val RGB = "rgb"
    const val GOLD = "gold"
    const val PIXEL = "pixel"

    private val rainbow = listOf(
        Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
        Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF)
    )

    /** Все рамки, отсортированы по цене. */
    val all: List<AvatarFrame> = listOf(
        // ---------- Обычные ----------
        AvatarFrame(
            "plain", "Простое кольцо", "Тонкое белое кольцо. Скромно и со вкусом.",
            FrameRarity.COMMON, 100,
            FrameStyle.Gradient(listOf(Color.White, Color.White), spin = false, glow = false, width = 0.06f)
        ),
        AvatarFrame(
            "graphite", "Графит", "Два строгих серых кольца.",
            FrameRarity.COMMON, 150,
            FrameStyle.DoubleRing(Color(0xFF757575), Color(0xFFBDBDBD))
        ),
        AvatarFrame(
            "dotted", "Пунктир", "Белый пунктир медленно кружится вокруг аватарки.",
            FrameRarity.COMMON, 200,
            FrameStyle.Dashed(Color.White, dashes = 18)
        ),

        // ---------- Необычные ----------
        AvatarFrame(
            RGB, "RGB-ободок", "Переливается всеми цветами радуги, как игровая клавиатура.",
            FrameRarity.UNCOMMON, 400,
            FrameStyle.Gradient(rainbow)
        ),
        AvatarFrame(
            "ocean", "Океан", "Глубокая синева с бирюзовыми бликами.",
            FrameRarity.UNCOMMON, 450,
            FrameStyle.Gradient(listOf(Color(0xFF0D47A1), Color(0xFF00B0FF), Color(0xFF80D8FF), Color(0xFF00B0FF)))
        ),
        AvatarFrame(
            "sunset", "Закат", "Оранжевый, розовый и фиолетовый, как небо после дождя.",
            FrameRarity.UNCOMMON, 500,
            FrameStyle.Gradient(listOf(Color(0xFFFF9800), Color(0xFFFF4081), Color(0xFF7C4DFF), Color(0xFFFF4081)))
        ),
        AvatarFrame(
            "sakura", "Сакура", "Нежно-розовое кольцо, которое мягко пульсирует.",
            FrameRarity.UNCOMMON, 550,
            FrameStyle.Gradient(
                listOf(Color(0xFFFFB7C5), Color(0xFFFF80AB), Color(0xFFFFB7C5)),
                spin = false, pulse = true
            )
        ),
        AvatarFrame(
            "neon_dash", "Неоновый пунктир", "Светящийся бирюзовый пунктир.",
            FrameRarity.UNCOMMON, 600,
            FrameStyle.Dashed(Color(0xFF00E5FF), dashes = 14, glow = true)
        ),

        // ---------- Редкие ----------
        AvatarFrame(
            "toxic", "Токсик", "Ядовито-зелёное сияние.",
            FrameRarity.RARE, 1100,
            FrameStyle.Gradient(listOf(Color(0xFF76FF03), Color(0xFF00C853), Color(0xFF1B5E20), Color(0xFF00C853)))
        ),
        AvatarFrame(
            PIXEL, "Пиксельный ободок", "Собран из пикселей и подстраивается под цвет вашего баннера.",
            FrameRarity.RARE, 1200,
            FrameStyle.Pixel()
        ),
        AvatarFrame(
            "pixel_neon", "Пиксельный неон", "Яркие разноцветные пиксели в стиле аркады.",
            FrameRarity.RARE, 1400,
            FrameStyle.Pixel(
                listOf(Color(0xFFFF1744), Color(0xFFFFEA00), Color(0xFF00E676), Color(0xFF00B0FF), Color(0xFFD500F9))
            )
        ),
        AvatarFrame(
            "fire", "Огненное кольцо", "Пламя бежит по кругу.",
            FrameRarity.RARE, 1500,
            FrameStyle.Gradient(
                listOf(Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFF44336), Color(0xFFB71C1C), Color(0xFFF44336), Color(0xFFFF9800))
            )
        ),
        AvatarFrame(
            "ice", "Ледяное кольцо", "Холодное сияние и кружащиеся снежинки.",
            FrameRarity.RARE, 1600,
            FrameStyle.Gradient(listOf(Color.White, Color(0xFFB3E5FC), Color(0xFF4FC3F7), Color(0xFFB3E5FC))),
            FrameOrbit(Color.White, count = 6, speed = -1)
        ),

        // ---------- Эпические ----------
        AvatarFrame(
            "dark_matter", "Тёмная материя", "Фиолетовая аура вокруг тёмного кольца.",
            FrameRarity.EPIC, 2200,
            FrameStyle.DoubleRing(Color(0xFF4A148C), Color(0xFFB388FF), pulse = true)
        ),
        AvatarFrame(
            "galaxy", "Галактика", "Туманность со звёздами, которые вращаются вокруг тебя.",
            FrameRarity.EPIC, 2400,
            FrameStyle.Gradient(listOf(Color(0xFF311B92), Color(0xFF7C4DFF), Color(0xFFE040FB), Color(0xFF7C4DFF))),
            FrameOrbit(Color.White, count = 8, speed = 1)
        ),
        AvatarFrame(
            "electro", "Электро", "Пульсирующий разряд с летающими искрами.",
            FrameRarity.EPIC, 2600,
            FrameStyle.Gradient(listOf(Color(0xFF00E5FF), Color(0xFF2979FF), Color(0xFF00E5FF)), spin = false, pulse = true),
            FrameOrbit(Color(0xFFE0F7FA), count = 4, speed = 2)
        ),
        AvatarFrame(
            "rainbow_pulse", "Радужный пульс", "Радуга, которая бьётся как сердце.",
            FrameRarity.EPIC, 2800,
            FrameStyle.Gradient(rainbow, pulse = true)
        ),

        // ---------- Легендарные ----------
        AvatarFrame(
            GOLD, "Золотое кольцо", "Благородный золотой блеск для тех, кто на вершине.",
            FrameRarity.LEGENDARY, 3500,
            FrameStyle.Gold
        ),
        AvatarFrame(
            "diamond", "Алмазное кольцо", "Сверкающие грани и искры вокруг аватарки.",
            FrameRarity.LEGENDARY, 4500,
            FrameStyle.Gradient(
                listOf(Color.White, Color(0xFF80DEEA), Color(0xFFB2EBF2), Color.White, Color(0xFF4DD0E1)),
                pulse = true
            ),
            FrameOrbit(Color.White, count = 8, speed = 2)
        ),
        AvatarFrame(
            "royal", "Королевская аура", "Золото и пурпур, достойные короны.",
            FrameRarity.LEGENDARY, 6000,
            FrameStyle.Gradient(
                listOf(Color(0xFFFFD700), Color(0xFF7B1FA2), Color(0xFFFFD700), Color(0xFF7B1FA2)),
                pulse = true
            ),
            FrameOrbit(Color(0xFFFFD700), count = 6, speed = -1)
        )
    )

    fun byId(id: String?): AvatarFrame? = all.firstOrNull { it.id == id }
}

/**
 * Рисует рамку поверх аватарки. Кладётся в тот же Box, что и аватарка (fillMaxSize).
 * [bannerColor] нужен только для пиксельного ободка без собственной палитры.
 */
@Composable
fun AvatarFrameOverlay(
    frameId: String?,
    bannerColor: Color,
    modifier: Modifier = Modifier
) {
    val frame = AvatarFrames.byId(frameId) ?: return

    val transition = rememberInfiniteTransition(label = "avatarFrame")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "frameAngle"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "framePulse"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        when (val style = frame.style) {
            is FrameStyle.Gradient -> drawGradientRing(style, angle, pulse)
            is FrameStyle.Dashed -> drawDashedRing(style, angle)
            is FrameStyle.DoubleRing -> drawDoubleRing(style, pulse)
            is FrameStyle.Pixel -> drawPixelRing(style, bannerColor)
            FrameStyle.Gold -> drawGoldRing(angle)
        }
        frame.orbit?.let { drawOrbit(it, angle) }
    }
}

private fun DrawScope.drawGradientRing(style: FrameStyle.Gradient, angle: Float, pulse: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = size.minDimension / 2f
    val breathe = if (style.pulse) 1f + 0.4f * pulse else 1f
    val stroke = outerRadius * style.width * breathe
    val radius = outerRadius - stroke / 2f // внешний край остаётся на месте, кольцо «дышит» внутрь
    val brush = Brush.sweepGradient(style.colors + style.colors.first(), center)

    rotate(degrees = if (style.spin) angle else 0f, pivot = center) {
        if (style.glow) {
            drawCircle(
                brush = brush,
                radius = radius,
                center = center,
                alpha = 0.3f,
                style = Stroke(width = stroke * 1.8f)
            )
        }
        drawCircle(
            brush = brush,
            radius = radius,
            center = center,
            style = Stroke(width = stroke)
        )
    }
}

private fun DrawScope.drawDashedRing(style: FrameStyle.Dashed, angle: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = size.minDimension / 2f
    val stroke = outerRadius * style.width
    val radius = outerRadius - stroke / 2f
    val circumference = (2.0 * PI * radius).toFloat()
    val segment = circumference / (style.dashes * 2)

    if (style.glow) {
        drawCircle(
            color = style.color.copy(alpha = 0.25f),
            radius = radius,
            center = center,
            style = Stroke(width = stroke * 1.8f)
        )
    }
    rotate(degrees = angle, pivot = center) {
        drawCircle(
            color = style.color,
            radius = radius,
            center = center,
            style = Stroke(
                width = stroke,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(segment, segment), 0f)
            )
        )
    }
}

private fun DrawScope.drawDoubleRing(style: FrameStyle.DoubleRing, pulse: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = size.minDimension / 2f
    val outerStroke = outerRadius * 0.07f
    val innerStroke = outerRadius * 0.045f
    val gap = outerRadius * 0.035f

    drawCircle(
        color = style.outer,
        radius = outerRadius - outerStroke / 2f,
        center = center,
        style = Stroke(width = outerStroke)
    )
    val innerAlpha = if (style.pulse) 0.45f + 0.55f * pulse else 1f
    drawCircle(
        color = style.inner.copy(alpha = innerAlpha),
        radius = outerRadius - outerStroke - gap - innerStroke / 2f,
        center = center,
        style = Stroke(width = innerStroke)
    )
}

private fun DrawScope.drawPixelRing(style: FrameStyle.Pixel, bannerColor: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = size.minDimension / 2f
    val cell = outerRadius * 0.11f
    val bandRadius = outerRadius - cell
    val light = lerp(bannerColor, Color.White, 0.35f)
    val dark = lerp(bannerColor, Color.Black, 0.30f)
    val cells = ceil(size.minDimension / cell).toInt()
    // выравниваем сетку по центру, чтобы рамка была симметричной
    val originX = center.x - (cells * cell) / 2f
    val originY = center.y - (cells * cell) / 2f
    val palette = style.palette

    for (gy in 0 until cells) {
        for (gx in 0 until cells) {
            val cx = originX + gx * cell + cell / 2f
            val cy = originY + gy * cell + cell / 2f
            val dx = cx - center.x
            val dy = cy - center.y
            val dist = sqrt(dx * dx + dy * dy)
            if (abs(dist - bandRadius) <= cell * 0.7f) {
                val color = if (palette != null && palette.isNotEmpty()) {
                    palette[(gx * 7 + gy * 13) % palette.size]
                } else {
                    when ((gx * 7 + gy * 13) % 4) {
                        0 -> light
                        1 -> dark
                        else -> bannerColor
                    }
                }
                drawRect(
                    color = color,
                    topLeft = Offset(originX + gx * cell, originY + gy * cell),
                    size = Size(cell, cell)
                )
            }
        }
    }
}

private fun DrawScope.drawGoldRing(angle: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = size.minDimension / 2f
    val stroke = outerRadius * 0.10f
    val radius = outerRadius - stroke / 2f
    val gold = Color(0xFFFFD700)
    val light = Color(0xFFFFF6B0)
    val dark = Color(0xFFB8860B)
    val colors = listOf(gold, light, gold, dark, gold, light, gold, dark, gold)

    // тёплое свечение
    drawCircle(
        color = gold.copy(alpha = 0.25f),
        radius = radius,
        center = center,
        style = Stroke(width = stroke * 1.9f)
    )
    // само кольцо, блик медленно бежит по кругу
    rotate(degrees = angle, pivot = center) {
        drawCircle(
            brush = Brush.sweepGradient(colors, center),
            radius = radius,
            center = center,
            style = Stroke(width = stroke)
        )
    }
    // тонкая тёмная окантовка для объёма
    drawCircle(
        color = dark,
        radius = outerRadius - stroke * 0.12f,
        center = center,
        style = Stroke(width = stroke * 0.16f)
    )
    drawCircle(
        color = dark.copy(alpha = 0.8f),
        radius = outerRadius - stroke * 0.95f,
        center = center,
        style = Stroke(width = stroke * 0.14f)
    )
}

private fun DrawScope.drawOrbit(orbit: FrameOrbit, angle: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = size.minDimension / 2f
    val radius = outerRadius - outerRadius * 0.05f
    val dotRadius = outerRadius * 0.055f
    val step = 360f / orbit.count

    for (i in 0 until orbit.count) {
        val deg = angle * orbit.speed + i * step
        val rad = deg * PI.toFloat() / 180f
        val pos = Offset(center.x + radius * cos(rad), center.y + radius * sin(rad))
        val twinkle = 0.55f + 0.45f * abs(sin(rad * 3f))
        drawCircle(color = orbit.color.copy(alpha = 0.35f * twinkle), radius = dotRadius * 2f, center = pos)
        drawCircle(color = orbit.color.copy(alpha = twinkle), radius = dotRadius, center = pos)
    }
}

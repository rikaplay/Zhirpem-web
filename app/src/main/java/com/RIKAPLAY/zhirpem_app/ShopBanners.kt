package com.RIKAPLAY.zhirpem_app

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Баннер из магазина. Рисуется кодом, поэтому ничего не весит и не требует загрузки. */
data class ShopBanner(
    val id: String,
    val name: String,
    val description: String,
    val rarity: FrameRarity,
    val price: Int // в очках репутации
)

object ShopBanners {
    /** Все баннеры, отсортированы по цене. */
    val all: List<ShopBanner> = listOf(
        // ---------- Обычные ----------
        ShopBanner("fog", "Туман", "Спокойный серо-голубой градиент.", FrameRarity.COMMON, 150),
        ShopBanner("stripes", "Полоски", "Диагональные полосы цвета морской волны.", FrameRarity.COMMON, 200),
        // ---------- Необычные ----------
        ShopBanner("sunset_sea", "Закат над морем", "Солнце садится за горизонт.", FrameRarity.UNCOMMON, 600),
        ShopBanner("synth_grid", "Синтвейв", "Неоновая сетка уходит за горизонт.", FrameRarity.UNCOMMON, 700),
        ShopBanner("waves", "Волны", "Несколько слоёв плавно качающихся волн.", FrameRarity.UNCOMMON, 800),
        // ---------- Редкие ----------
        ShopBanner("aurora", "Северное сияние", "Зелёные и бирюзовые ленты на ночном небе.", FrameRarity.RARE, 1500),
        ShopBanner("stars", "Звёздное небо", "Мерцающие звёзды над тёмной синевой.", FrameRarity.RARE, 1700),
        // ---------- Эпические ----------
        ShopBanner("cyber_city", "Киберпанк-город", "Ночной город в розово-голубом свете.", FrameRarity.EPIC, 2800),
        ShopBanner("lava", "Лава", "Раскалённые потоки в темноте.", FrameRarity.EPIC, 3200),
        // ---------- Легендарные ----------
        ShopBanner("gold_empire", "Золотая империя", "Вращающиеся золотые лучи на тёмном фоне.", FrameRarity.LEGENDARY, 5000)
    )

    fun byId(id: String?): ShopBanner? = all.firstOrNull { it.id == id }
}

/** Рисует баннер на всю доступную область. */
@Composable
fun ShopBannerArt(bannerId: String?, modifier: Modifier = Modifier) {
    if (ShopBanners.byId(bannerId) == null) return

    val transition = rememberInfiniteTransition(label = "shopBanner")
    // 0..1, ровно один цикл за 12 секунд. Все анимации выражены через sin/cos, поэтому шва нет
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bannerTime"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        when (bannerId) {
            "fog" -> drawFog()
            "stripes" -> drawStripes()
            "sunset_sea" -> drawSunsetSea()
            "synth_grid" -> drawSynthGrid()
            "waves" -> drawWaves(t)
            "aurora" -> drawAurora(t)
            "stars" -> drawStars(t)
            "cyber_city" -> drawCyberCity()
            "lava" -> drawLava(t)
            "gold_empire" -> drawGoldEmpire(t)
        }
    }
}

private fun DrawScope.drawFog() {
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFFCFD8DC), Color(0xFF90A4AE), Color(0xFF546E7A)))
    )
    drawCircle(Color.White.copy(alpha = 0.18f), radius = size.height * 0.7f, center = Offset(size.width * 0.2f, size.height * 0.2f))
    drawCircle(Color.White.copy(alpha = 0.12f), radius = size.height * 0.5f, center = Offset(size.width * 0.8f, size.height * 0.6f))
}

private fun DrawScope.drawStripes() {
    drawRect(Color(0xFF004D50))
    val step = size.height * 0.28f
    val stripe = step * 0.5f
    var x = -size.height
    while (x < size.width + size.height) {
        val path = Path().apply {
            moveTo(x, size.height)
            lineTo(x + stripe, size.height)
            lineTo(x + stripe + size.height, 0f)
            lineTo(x + size.height, 0f)
            close()
        }
        drawPath(path, Color(0xFF26A69A).copy(alpha = 0.55f))
        x += step
    }
}

private fun DrawScope.drawSunsetSea() {
    val horizon = size.height * 0.62f
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFF3F1D6B), Color(0xFFD6336C), Color(0xFFFF9800), Color(0xFFFFD54F)),
            endY = horizon
        ),
        size = Size(size.width, horizon)
    )
    // солнце
    drawCircle(Color(0xFFFFF3B0), radius = size.height * 0.2f, center = Offset(size.width * 0.5f, horizon))
    // море
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFF1A237E), Color(0xFF0D1245)),
            startY = horizon, endY = size.height
        ),
        topLeft = Offset(0f, horizon),
        size = Size(size.width, size.height - horizon)
    )
    // дорожка света на воде
    for (i in 0 until 5) {
        val y = horizon + (i + 1) * (size.height - horizon) / 6f
        val w = size.width * (0.28f - i * 0.04f)
        drawRect(
            Color(0xFFFFCC80).copy(alpha = 0.55f - i * 0.08f),
            topLeft = Offset(size.width / 2f - w / 2f, y),
            size = Size(w, size.height * 0.012f)
        )
    }
}

private fun DrawScope.drawSynthGrid() {
    val horizon = size.height * 0.45f
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF1B0036), Color(0xFF6A1B9A), Color(0xFFFF4081)), endY = horizon),
        size = Size(size.width, horizon)
    )
    drawCircle(Color(0xFFFFD54F).copy(alpha = 0.9f), radius = size.height * 0.22f, center = Offset(size.width / 2f, horizon))
    drawRect(Color(0xFF12002B), topLeft = Offset(0f, horizon), size = Size(size.width, size.height - horizon))

    val line = Color(0xFF00E5FF).copy(alpha = 0.8f)
    val stroke = max(1f, size.height * 0.008f)
    // горизонтальные линии сгущаются к горизонту
    for (i in 1..6) {
        val k = i / 6f
        val y = horizon + (size.height - horizon) * k * k
        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
    }
    // лучи перспективы
    val cx = size.width / 2f
    for (i in -8..8) {
        drawLine(line, Offset(cx, horizon), Offset(cx + i * size.width * 0.14f, size.height), strokeWidth = stroke)
    }
}

private fun DrawScope.drawWaves(t: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF4FC3F7), Color(0xFF0277BD))))
    val phase = t * 2f * PI.toFloat()
    val layers = listOf(
        Triple(0.45f, Color(0xFF29B6F6).copy(alpha = 0.55f), 1f),
        Triple(0.58f, Color(0xFF0288D1).copy(alpha = 0.65f), -1f),
        Triple(0.72f, Color(0xFF01579B).copy(alpha = 0.8f), 1f),
        Triple(0.86f, Color(0xFF002F6C), -1f)
    )
    layers.forEachIndexed { index, (level, color, dir) ->
        val path = Path()
        val base = size.height * level
        val amp = size.height * (0.05f + index * 0.012f)
        path.moveTo(0f, size.height)
        path.lineTo(0f, base)
        var x = 0f
        val stepPx = max(4f, size.width / 80f)
        while (x <= size.width) {
            val y = base + sin(x / size.width * 2f * PI.toFloat() * (1.5f + index * 0.4f) + phase * dir) * amp
            path.lineTo(x, y)
            x += stepPx
        }
        path.lineTo(size.width, size.height)
        path.close()
        drawPath(path, color)
    }
}

private fun DrawScope.drawAurora(t: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF020B1A), Color(0xFF0B2A3B), Color(0xFF05121F))))
    val phase = t * 2f * PI.toFloat()
    val colors = listOf(Color(0xFF00E676), Color(0xFF1DE9B6), Color(0xFF64FFDA))
    colors.forEachIndexed { i, c ->
        val path = Path()
        val baseX = size.width * (0.15f + i * 0.3f)
        val sway = sin(phase + i * 1.7f) * size.width * 0.08f
        path.moveTo(baseX + sway - size.width * 0.12f, 0f)
        path.cubicTo(
            baseX + sway + size.width * 0.15f, size.height * 0.3f,
            baseX - sway - size.width * 0.1f, size.height * 0.6f,
            baseX + sway * 0.5f, size.height
        )
        path.lineTo(baseX + sway * 0.5f + size.width * 0.2f, size.height)
        path.cubicTo(
            baseX - sway + size.width * 0.12f, size.height * 0.6f,
            baseX + sway + size.width * 0.25f, size.height * 0.3f,
            baseX + sway - size.width * 0.12f + size.width * 0.18f, 0f
        )
        path.close()
        drawPath(
            path,
            Brush.verticalGradient(listOf(c.copy(alpha = 0.0f), c.copy(alpha = 0.55f), c.copy(alpha = 0.05f)))
        )
    }
}

private fun DrawScope.drawStars(t: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF050A24), Color(0xFF14246B), Color(0xFF283593))))
    val rnd = Random(42) // одинаковое небо при каждой отрисовке
    val phase = t * 2f * PI.toFloat()
    repeat(60) { i ->
        val x = rnd.nextFloat() * size.width
        val y = rnd.nextFloat() * size.height
        val r = (0.6f + rnd.nextFloat() * 1.8f) * (size.height / 170f)
        val twinkle = 0.55f + 0.45f * sin(phase * (1 + i % 3) + i)
        drawCircle(Color.White.copy(alpha = twinkle.coerceIn(0.15f, 1f)), radius = r, center = Offset(x, y))
    }
    // луна
    drawCircle(Color(0xFFFFF8E1), radius = size.height * 0.13f, center = Offset(size.width * 0.82f, size.height * 0.28f))
    drawCircle(Color(0xFF14246B), radius = size.height * 0.11f, center = Offset(size.width * 0.82f + size.height * 0.06f, size.height * 0.25f))
}

private fun DrawScope.drawCyberCity() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0033), Color(0xFFB0003A), Color(0xFFFF6E40)), endY = size.height * 0.75f))
    val rnd = Random(7)
    var x = 0f
    while (x < size.width) {
        val w = size.width * (0.04f + rnd.nextFloat() * 0.06f)
        val h = size.height * (0.25f + rnd.nextFloat() * 0.5f)
        drawRect(Color(0xFF0A0A1F), topLeft = Offset(x, size.height - h), size = Size(w, h))
        // светящиеся окна
        val rows = (h / (size.height * 0.07f)).toInt()
        for (r in 0 until rows) {
            if (rnd.nextFloat() > 0.45f) {
                val wx = x + w * (0.2f + rnd.nextFloat() * 0.5f)
                val wy = size.height - h + r * size.height * 0.07f + size.height * 0.02f
                val c = if (rnd.nextBoolean()) Color(0xFF00E5FF) else Color(0xFFFF4081)
                drawRect(c.copy(alpha = 0.85f), topLeft = Offset(wx, wy), size = Size(w * 0.14f, size.height * 0.025f))
            }
        }
        x += w + size.width * 0.004f
    }
    // неоновая полоса снизу
    drawRect(
        Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFFFF4081), Color(0xFF00E5FF))),
        topLeft = Offset(0f, size.height * 0.96f),
        size = Size(size.width, size.height * 0.04f)
    )
}

private fun DrawScope.drawLava(t: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0500), Color(0xFF4A0E00), Color(0xFF8B1A00))))
    val phase = t * 2f * PI.toFloat()
    val rnd = Random(99)
    repeat(7) { i ->
        val bx = size.width * (0.08f + rnd.nextFloat() * 0.84f)
        val by = size.height * (0.4f + rnd.nextFloat() * 0.5f)
        val r = size.height * (0.18f + rnd.nextFloat() * 0.22f)
        val dx = cos(phase + i) * size.width * 0.03f
        val dy = sin(phase * 1.0f + i * 1.3f) * size.height * 0.08f
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFFEB3B).copy(alpha = 0.9f), Color(0xFFFF6D00).copy(alpha = 0.65f), Color.Transparent),
                center = Offset(bx + dx, by + dy),
                radius = r
            ),
            radius = r,
            center = Offset(bx + dx, by + dy)
        )
    }
    // трещины
    val crack = Color(0xFFFF9100).copy(alpha = 0.55f)
    drawLine(crack, Offset(0f, size.height * 0.2f), Offset(size.width * 0.35f, size.height * 0.45f), strokeWidth = size.height * 0.012f)
    drawLine(crack, Offset(size.width * 0.6f, 0f), Offset(size.width * 0.8f, size.height * 0.35f), strokeWidth = size.height * 0.012f)
}

private fun DrawScope.drawGoldEmpire(t: Float) {
    drawRect(Brush.radialGradient(listOf(Color(0xFF3B2A00), Color(0xFF0D0900)), center = center, radius = size.width * 0.7f))
    val rayCount = 18
    val gold = Color(0xFFFFD700)
    val reach = size.width * 0.9f
    rotate(degrees = t * 360f / 3f, pivot = center) { // за 12 секунд поворот на 120 градусов, лучи совпадают без шва
        for (i in 0 until rayCount) {
            val a = (i * 360f / rayCount) * PI.toFloat() / 180f
            val half = (360f / rayCount / 4f) * PI.toFloat() / 180f
            val path = Path().apply {
                moveTo(center.x, center.y)
                lineTo(center.x + reach * cos(a - half), center.y + reach * sin(a - half))
                lineTo(center.x + reach * cos(a + half), center.y + reach * sin(a + half))
                close()
            }
            drawPath(path, gold.copy(alpha = 0.22f))
        }
    }
    // центральное свечение и кольца
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFF59D), gold.copy(alpha = 0.5f), Color.Transparent), center = center, radius = size.height * 0.5f),
        radius = size.height * 0.5f,
        center = center
    )
    drawCircle(gold, radius = size.height * 0.3f, center = center, style = Stroke(width = size.height * 0.015f))
    drawCircle(Color(0xFFB8860B), radius = size.height * 0.36f, center = center, style = Stroke(width = size.height * 0.008f))
}

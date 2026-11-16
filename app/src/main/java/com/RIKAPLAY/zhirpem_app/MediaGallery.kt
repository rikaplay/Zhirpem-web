package com.RIKAPLAY.zhirpem_app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** Максимум файлов в одном посте. */
const val MAX_POST_MEDIA = 10

/** Цвет радуги по углу оттенка (0..360). */
private fun hueColor(hue: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val x = h / 60f
    val i = x.toInt() % 6
    val f = x - x.toInt()
    val q = 1f - f
    return when (i) {
        0 -> Color(1f, f, 0f)
        1 -> Color(q, 1f, 0f)
        2 -> Color(0f, 1f, f)
        3 -> Color(0f, q, 1f)
        4 -> Color(f, 0f, 1f)
        else -> Color(1f, 0f, q)
    }
}

/**
 * Бегущая RGB-обводка для постов, которые наградил админ.
 * Цвета обновляются в фазе отрисовки, поэтому пост не перекомпонуется каждый кадр.
 */
@Composable
fun Modifier.rgbBorder(cornerRadius: Dp, width: Dp = 2.5.dp): Modifier {
    val transition = rememberInfiniteTransition(label = "rgbBorder")
    val hueShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 3000, easing = LinearEasing)),
        label = "rgbHue"
    )
    return this.drawWithContent {
        drawContent()
        val w = width.toPx()
        val r = cornerRadius.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val brush = Brush.sweepGradient(List(7) { hueColor(hueShift + it * 60f) }, center)

        // мягкое свечение
        drawRoundRect(
            brush = brush,
            cornerRadius = CornerRadius(r),
            alpha = 0.35f,
            style = Stroke(width = w * 2.5f)
        )
        // сама обводка
        drawRoundRect(
            brush = brush,
            topLeft = Offset(w / 2f, w / 2f),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius((r - w / 2f).coerceAtLeast(0f)),
            style = Stroke(width = w)
        )
    }
}

/** Галерея для поста с несколькими медиа: свайп по горизонтали, счётчик и точки. */
@Composable
fun PostMediaGallery(
    urls: List<String>,
    types: List<String>,
    modifier: Modifier = Modifier
) {
    if (urls.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { urls.size })
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    viewerIndex?.let { start ->
        GalleryViewerDialog(urls = urls, types = types, startIndex = start, onDismiss = { viewerIndex = null })
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val url = urls[page]
            val type = types.getOrNull(page)
                ?.let { name -> runCatching { MediaType.valueOf(name) }.getOrNull() }
                ?: MediaType.IMAGE

            when (type) {
                MediaType.VIDEO -> VideoPlayer(videoUrl = url, modifier = Modifier.fillMaxSize())
                MediaType.GIF -> GifPlayer(gifUrl = url, modifier = Modifier.fillMaxSize().clickable { viewerIndex = page })
                else -> AsyncImage(
                    model = url,
                    contentDescription = "Медиа ${page + 1}",
                    modifier = Modifier.fillMaxSize().clickable { viewerIndex = page },
                    contentScale = ContentScale.Crop
                )
            }
        }

        // «2/5» в углу
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.55f)
        ) {
            Text(
                text = "${pagerState.currentPage + 1}/${urls.size}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        // Точки внизу
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(urls.size) { index ->
                val selected = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .size(if (selected) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color.White else Color.White.copy(alpha = 0.5f))
                )
            }
        }
    }
}


/** Фото, которое можно приближать двумя пальцами. Пока оно не приближено, свайпы отдаются листалке страниц. */
@Composable
private fun ViewerImage(url: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val multiTouch = event.changes.size > 1
                        // Жесты забираем себе только при масштабировании или когда фото уже приближено
                        if (multiTouch || scale > 1f) {
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
        )
    }
}

/** Просмотр всех фото поста на весь экран: листание, приближение и полоса миниатюр. */
@Composable
fun GalleryViewerDialog(
    urls: List<String>,
    types: List<String>,
    startIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0)),
        pageCount = { urls.size }
    )
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.96f))) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val type = types.getOrNull(page)
                    ?.let { name -> runCatching { MediaType.valueOf(name) }.getOrNull() }
                    ?: MediaType.IMAGE
                when (type) {
                    MediaType.VIDEO -> VideoPlayer(videoUrl = urls[page], modifier = Modifier.fillMaxSize())
                    MediaType.GIF -> GifPlayer(gifUrl = urls[page], modifier = Modifier.fillMaxSize())
                    else -> ViewerImage(urls[page])
                }
            }

            // Закрыть
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 12.dp, top = 8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
            }

            // Счётчик
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} из ${urls.size}",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // Миниатюры внизу: по ним можно быстро перейти к нужному фото
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                urls.forEachIndexed { index, thumb ->
                    val selected = index == pagerState.currentPage
                    AsyncImage(
                        model = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                    )
                }
            }
        }
    }
}

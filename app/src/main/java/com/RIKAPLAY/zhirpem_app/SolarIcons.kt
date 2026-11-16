package com.RIKAPLAY.zhirpem_app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Минималистичные линейные иконки в стиле Solar: сетка 24x24, тонкая обводка 1,5 со скруглёнными концами.
 * Иконки нарисованы вручную в этом стиле и лежат прямо в коде, поэтому ничего не нужно подключать или скачивать.
 *
 * Использование: Icon(SolarIcons.get("home"), contentDescription = null, tint = цвет)
 * Для выбранного состояния есть «жирный» вариант: SolarIcons.get("home", bold = true), он чуть толще и с лёгкой заливкой.
 *
 * Доступные имена: home, search, shorts, bell, chat, user, settings, bookmark, group, stats, trophy.
 */
object SolarIcons {
    private class SolarSpec(val paths: List<String>, val fillable: Boolean)

    private val specs: Map<String, SolarSpec> = mapOf(
        "home" to SolarSpec(
            listOf(
            "M3 10.4 L11.1 3.5 C11.6 3.1 12.4 3.1 12.9 3.5 L21 10.4",
            "M5 8.9 V18.5 C5 19.9 6.1 21 7.5 21 H16.5 C17.9 21 19 19.9 19 18.5 V8.9",
            "M9.5 21 V16 C9.5 15.2 10.2 14.5 11 14.5 H13 C13.8 14.5 14.5 15.2 14.5 16 V21"
            ),
            fillable = true
        ),
        "search" to SolarSpec(
            listOf(
            "M18 11 A7 7 0 1 1 4 11 A7 7 0 1 1 18 11 Z",
            "M16.2 16.2 L20.5 20.5"
            ),
            fillable = false
        ),
        "heart" to SolarSpec(
            listOf(
            "M12 20.5 C12 20.5 3.5 15.2 3.5 9.2 C3.5 6.6 5.5 4.5 8 4.5 C9.7 4.5 11.2 5.4 12 6.8 C12.8 5.4 14.3 4.5 16 4.5 C18.5 4.5 20.5 6.6 20.5 9.2 C20.5 15.2 12 20.5 12 20.5 Z"
            ),
            fillable = true
        ),
        "repost" to SolarSpec(
            listOf(
            "M5 11 V9.5 C5 8.1 6.1 7 7.5 7 H18.5",
            "M15.5 4 L18.5 7 L15.5 10",
            "M19 13 V14.5 C19 15.9 17.9 17 16.5 17 H5.5",
            "M8.5 14 L5.5 17 L8.5 20"
            ),
            fillable = false
        ),
        "share" to SolarSpec(
            listOf(
            "M20.5 3.8 L3.5 10.8 L10 13.4 L12.6 20 Z",
            "M10 13.4 L20.5 3.8"
            ),
            fillable = true
        ),
        "music" to SolarSpec(
            listOf(
            "M9 18 V6 L19 4 V16",
            "M9 18 A2.5 2.5 0 1 1 4 18 A2.5 2.5 0 1 1 9 18 Z",
            "M19 16 A2.5 2.5 0 1 1 14 16 A2.5 2.5 0 1 1 19 16 Z"
            ),
            fillable = false
        ),
        "star" to SolarSpec(
            listOf(
            "M12 3.5 L14.6 9 L20.5 9.7 L16.1 13.7 L17.3 19.6 L12 16.7 L6.7 19.6 L7.9 13.7 L3.5 9.7 L9.4 9 Z"
            ),
            fillable = true
        ),
        "flip" to SolarSpec(
            listOf(
            "M20 12 A8 8 0 0 0 6.3 6.3 L4 8.5",
            "M4 4 V8.5 H8.5",
            "M4 12 A8 8 0 0 0 17.7 17.7 L20 15.5",
            "M20 20 V15.5 H15.5"
            ),
            fillable = false
        ),
        "bolt" to SolarSpec(
            listOf(
            "M13 2.5 L5 13.5 H11.5 L10.5 21.5 L19 10.5 H12.5 Z"
            ),
            fillable = true
        ),
        "play" to SolarSpec(
            listOf(
            "M8 5.5 V18.5 L19 12 Z"
            ),
            fillable = true
        ),
        "pause" to SolarSpec(
            listOf(
            "M8.5 5.5 V18.5",
            "M15.5 5.5 V18.5"
            ),
            fillable = false
        ),
        "close" to SolarSpec(
            listOf(
            "M6 6 L18 18",
            "M18 6 L6 18"
            ),
            fillable = false
        ),
        "back" to SolarSpec(
            listOf(
            "M15 5 L8 12 L15 19"
            ),
            fillable = false
        ),
        "plus" to SolarSpec(
            listOf(
            "M12 5 V19",
            "M5 12 H19"
            ),
            fillable = false
        ),
        "upload" to SolarSpec(
            listOf(
            "M12 16 V4",
            "M7 9 L12 4 L17 9",
            "M5 20 H19"
            ),
            fillable = false
        ),
        "gallery" to SolarSpec(
            listOf(
            "M4 5 H20 V19 H4 Z",
            "M4 15.5 L9 10.5 L14 15.5 L16.5 13 L20 16.5"
            ),
            fillable = false
        ),
        "grid" to SolarSpec(
            listOf(
            "M4 4 H10.5 V10.5 H4 Z",
            "M13.5 4 H20 V10.5 H13.5 Z",
            "M4 13.5 H10.5 V20 H4 Z",
            "M13.5 13.5 H20 V20 H13.5 Z"
            ),
            fillable = false
        ),
        "check" to SolarSpec(
            listOf(
            "M5 12.5 L10 17.5 L19 7"
            ),
            fillable = false
        ),
        "trash" to SolarSpec(
            listOf(
            "M5 7 H19",
            "M9 7 V5 H15 V7",
            "M7 7 L8 20 H16 L17 7"
            ),
            fillable = false
        ),
        "send" to SolarSpec(
            listOf(
            "M4 12 L20.5 4 L15 20.5 L11.5 13 Z"
            ),
            fillable = true
        ),
        "more" to SolarSpec(
            listOf(
            "M5 12 H5.01",
            "M12 12 H12.01",
            "M19 12 H19.01"
            ),
            fillable = false
        ),
        // Знак ZHIRPEM Shorts: «S» из двух смещённых капсул с мордой лисы внутри
        "shorts" to SolarSpec(
            listOf(
            "M7.36 11.95 L7.17 11.49 L7.06 11.01 L7.02 10.51 L7.07 9.94 L7.20 9.46 L7.44 8.93 L7.72 8.52 L8.13 8.11 L8.60 7.77 L16.40 3.27 L16.70 3.13 L17.09 2.99 L17.50 2.91 L17.91 2.89 L18.57 2.96 L19.20 3.16 L19.77 3.49 L20.26 3.93 L20.65 4.47 L20.92 5.07 L21.06 5.72 L21.07 6.05 L21.05 6.46 L20.92 7.03 L20.69 7.56 L20.37 8.04 L19.97 8.45 L19.56 8.74 L15.74 10.98 L16.22 11.43 L16.43 11.70 L16.64 12.05 L16.83 12.51 L16.94 12.99 L16.98 13.49 L16.93 14.06 L16.80 14.54 L16.56 15.07 L16.28 15.48 L15.87 15.89 L15.40 16.23 L7.60 20.73 L7.30 20.87 L6.91 21.01 L6.50 21.09 L6.09 21.11 L5.43 21.04 L4.80 20.84 L4.23 20.51 L3.74 20.07 L3.35 19.53 L3.08 18.93 L2.94 18.28 L2.93 17.95 L2.95 17.54 L3.08 16.97 L3.31 16.44 L3.63 15.96 L4.03 15.55 L4.44 15.26 L8.26 13.02 L7.78 12.57 L7.57 12.30 Z",
            "M9.77 9.77 L10.79 11.72 L12.46 11.72 L13.49 9.77 L13.49 12.93 L11.63 15.35 L9.77 12.93 Z",
            "M10.74 13.02 H10.75",
            "M12.51 13.02 H12.52"
            ),
            fillable = true
        ),
        "bell" to SolarSpec(
            listOf(
            "M6 10 C6 6.7 8.7 4 12 4 C15.3 4 18 6.7 18 10 V12.4 C18 13.3 18.3 14.1 18.9 14.8 L19.6 15.7 C20.2 16.4 19.7 17.5 18.8 17.5 H5.2 C4.3 17.5 3.8 16.4 4.4 15.7 L5.1 14.8 C5.7 14.1 6 13.3 6 12.4 Z",
            "M9.6 20.2 C10.1 20.7 10.9 21 12 21 C13.1 21 13.9 20.7 14.4 20.2"
            ),
            fillable = true
        ),
        "chat" to SolarSpec(
            listOf(
            "M12 3 C7 3 3 6.6 3 11 C3 13.2 4 15.2 5.6 16.6 L4.9 19.7 C4.8 20.3 5.3 20.8 5.9 20.5 L9.4 18.8 C10.2 19 11.1 19.1 12 19.1 C17 19.1 21 15.5 21 11.1 C21 6.7 17 3 12 3 Z",
            "M8.5 11.1 H8.51",
            "M12 11.1 H12.01",
            "M15.5 11.1 H15.51"
            ),
            fillable = true
        ),
        "user" to SolarSpec(
            listOf(
            "M15.8 8 A3.8 3.8 0 1 1 8.2 8 A3.8 3.8 0 1 1 15.8 8 Z",
            "M4.5 20 C4.5 16.8 7.9 14.6 12 14.6 C16.1 14.6 19.5 16.8 19.5 20"
            ),
            fillable = false
        ),
        "settings" to SolarSpec(
            listOf(
            "M4 7 H10.5",
            "M16.5 7 A2.5 2.5 0 1 1 11.5 7 A2.5 2.5 0 1 1 16.5 7 Z",
            "M16.5 7 H20",
            "M4 17 H7.5",
            "M13.0 17 A2.5 2.5 0 1 1 8.0 17 A2.5 2.5 0 1 1 13.0 17 Z",
            "M13 17 H20"
            ),
            fillable = false
        ),
        "bookmark" to SolarSpec(
            listOf(
            "M6 4.8 C6 3.8 6.8 3 7.8 3 H16.2 C17.2 3 18 3.8 18 4.8 V20.1 C18 20.8 17.2 21.2 16.7 20.8 L12.6 17.9 C12.2 17.6 11.8 17.6 11.4 17.9 L7.3 20.8 C6.8 21.2 6 20.8 6 20.1 Z"
            ),
            fillable = true
        ),
        "group" to SolarSpec(
            listOf(
            "M12.7 8.5 A3.2 3.2 0 1 1 6.3 8.5 A3.2 3.2 0 1 1 12.7 8.5 Z",
            "M3.5 19.5 C3.5 16.8 6.2 15 9.5 15 C12.8 15 15.5 16.8 15.5 19.5",
            "M19.599999999999998 9.4 A2.4 2.4 0 1 1 14.799999999999999 9.4 A2.4 2.4 0 1 1 19.599999999999998 9.4 Z",
            "M17.6 14.4 C19.9 14.7 21 16.2 21 18.3"
            ),
            fillable = false
        ),
        "stats" to SolarSpec(
            listOf(
            "M4.5 12.5 H8.5 V19.5 H4.5 Z",
            "M10 4.5 H14 V19.5 H10 Z",
            "M15.5 9 H19.5 V19.5 H15.5 Z"
            ),
            fillable = true
        ),
        "trophy" to SolarSpec(
            listOf(
            "M7.5 3.8 H16.5 V10 C16.5 12.5 14.5 14.5 12 14.5 C9.5 14.5 7.5 12.5 7.5 10 Z",
            "M7.5 5.5 H4.8 C4.8 8.4 5.8 9.8 7.8 10.3",
            "M16.5 5.5 H19.2 C19.2 8.4 18.2 9.8 16.2 10.3",
            "M12 14.5 V17.5",
            "M8.5 20.5 C8.5 18.8 9.8 17.5 11.4 17.5 H12.6 C14.2 17.5 15.5 18.8 15.5 20.5 Z"
            ),
            fillable = true
        )
    )

    private val cache = HashMap<String, ImageVector>()

    /** Иконка по имени. Неизвестное имя даёт иконку «user», чтобы приложение не падало. */
    fun get(name: String, bold: Boolean = false): ImageVector {
        val key = name + if (bold) ":bold" else ""
        return cache.getOrPut(key) {
            val spec = specs[name] ?: specs.getValue("user")
            ImageVector.Builder(
                name = "solar_$name",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                for (path in spec.paths) {
                    addPath(
                        pathData = addPathNodes(path),
                        fill = if (bold && spec.fillable) SolidColor(Color.Black.copy(alpha = 0.22f)) else null,
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = if (bold) 1.9f else 1.5f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round
                    )
                }
            }.build()
        }
    }
}

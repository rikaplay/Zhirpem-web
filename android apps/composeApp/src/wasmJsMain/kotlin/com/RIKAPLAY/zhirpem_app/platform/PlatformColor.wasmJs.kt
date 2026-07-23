package com.RIKAPLAY.zhirpem_app.platform

import androidx.compose.ui.graphics.Color

actual fun parseColor(colorString: String): Color {
    return try {
        val hex = colorString.removePrefix("#")
        val argb = hex.toLong(16)
        if (hex.length == 6) {
            Color(0xFF000000 or argb)
        } else {
            Color(argb)
        }
    } catch (e: Exception) {
        Color(0xFFE5DBFF)
    }
}

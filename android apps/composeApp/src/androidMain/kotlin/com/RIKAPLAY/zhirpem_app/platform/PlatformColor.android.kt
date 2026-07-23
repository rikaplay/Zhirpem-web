package com.RIKAPLAY.zhirpem_app.platform

import androidx.compose.ui.graphics.Color

actual fun parseColor(colorString: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(colorString))
    } catch (e: Exception) {
        Color(0xFFE5DBFF)
    }
}

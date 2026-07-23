package com.RIKAPLAY.zhirpem_app

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.RIKAPLAY.zhirpem_app.platform.parseColor

expect class ThemeManager() {
    var themeType: String
    var customColor: String
    
    companion object {
        val TYPE_DEFAULT: String
        val TYPE_MY_LIGHT: String
        val TYPE_MY_DARK: String
    }
}

fun ThemeManager.getCustomColorObj(): Color {
    return parseColor(customColor)
}

fun ThemeManager.generateColorScheme(baseColor: Color, isDark: Boolean) = if (isDark) {
    darkColorScheme(
        primary = baseColor,
        primaryContainer = baseColor.copy(alpha = 0.3f),
        onPrimaryContainer = baseColor
    )
} else {
    lightColorScheme(
        primary = baseColor,
        primaryContainer = baseColor.copy(alpha = 0.3f),
        onPrimaryContainer = baseColor,
        background = Color(0xFFF8F9FA),
        surface = Color(0xFFF8F9FA)
    )
}

package com.RIKAPLAY.zhirpem_app

import androidx.compose.runtime.compositionLocalOf

enum class AppThemeMode { LIGHT, DARK, AMOLED, SYSTEM, MATERIAL_YOU_LIGHT, MATERIAL_YOU_DARK }

// Глобальный указатель: включены ли полные анимации. По умолчанию — true (анимации работают)
val LocalAnimationsEnabled = compositionLocalOf { true }

// Глобальный коэффициент размера шрифта для доступности
val LocalFontSize = compositionLocalOf { 1.0f }

val LocalGlassEnabled = compositionLocalOf { true }
val LocalGlassAlpha = compositionLocalOf { 0.4f }
val LocalBackgroundBlurEnabled = compositionLocalOf { true }

expect class SettingsManager() {
    var isLowPerformanceMode: Boolean
    var fontSizeMultiplier: Float
    var isGlassEnabled: Boolean
    var glassAlpha: Float
    var isSplashScreenEnabled: Boolean
    var isSplashSoundEnabled: Boolean
    var isFirstLaunch: Boolean
    
    var gameHighScore: Int
    var isGameHardcore: Boolean
}

expect fun changeAppIcon(newAliasName: String)

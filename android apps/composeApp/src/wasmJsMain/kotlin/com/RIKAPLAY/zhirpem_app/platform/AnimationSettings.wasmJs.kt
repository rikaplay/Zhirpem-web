package com.RIKAPLAY.zhirpem_app

import kotlinx.browser.localStorage

actual class SettingsManager {
    actual var isLowPerformanceMode: Boolean
        get() = localStorage.getItem("low_perf_mode") == "true"
        set(value) { localStorage.setItem("low_perf_mode", value.toString()) }

    actual var fontSizeMultiplier: Float
        get() = localStorage.getItem("font_size_multiplier")?.toFloat() ?: 1.0f
        set(value) { localStorage.setItem("font_size_multiplier", value.toString()) }

    actual var isGlassEnabled: Boolean
        get() = localStorage.getItem("glass_enabled") != "false"
        set(value) { localStorage.setItem("glass_enabled", value.toString()) }

    actual var glassAlpha: Float
        get() = localStorage.getItem("glass_alpha")?.toFloat() ?: 0.4f
        set(value) { localStorage.setItem("glass_alpha", value.toString()) }

    actual var isSplashScreenEnabled: Boolean
        get() = localStorage.getItem("splash_screen_enabled") != "false"
        set(value) { localStorage.setItem("splash_screen_enabled", value.toString()) }

    actual var isSplashSoundEnabled: Boolean
        get() = localStorage.getItem("splash_sound_enabled") != "false"
        set(value) { localStorage.setItem("splash_sound_enabled", value.toString()) }

    actual var isFirstLaunch: Boolean
        get() = localStorage.getItem("is_first_launch") != "false"
        set(value) { localStorage.setItem("is_first_launch", value.toString()) }

    actual var gameHighScore: Int
        get() = localStorage.getItem("game_high_score")?.toInt() ?: 0
        set(value) { localStorage.setItem("game_high_score", value.toString()) }

    actual var isGameHardcore: Boolean
        get() = localStorage.getItem("game_hardcore") == "true"
        set(value) { localStorage.setItem("game_hardcore", value.toString()) }
}

actual fun changeAppIcon(newAliasName: String) {
    // Not supported on web
}

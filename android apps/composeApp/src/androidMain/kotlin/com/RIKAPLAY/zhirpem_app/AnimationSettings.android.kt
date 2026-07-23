package com.RIKAPLAY.zhirpem_app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

// For simplicity, we use a global context or you should use a DI framework
private var androidContext: Context? = null

fun initSettings(context: Context) {
    androidContext = context
}

actual class SettingsManager {
    private val prefs = androidContext?.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    actual var isLowPerformanceMode: Boolean
        get() = prefs?.getBoolean("low_perf_mode", false) ?: false
        set(value) { prefs?.edit()?.putBoolean("low_perf_mode", value)?.apply() }

    actual var fontSizeMultiplier: Float
        get() = prefs?.getFloat("font_size_multiplier", 1.0f) ?: 1.0f
        set(value) { prefs?.edit()?.putFloat("font_size_multiplier", value)?.apply() }

    actual var isGlassEnabled: Boolean
        get() = prefs?.getBoolean("glass_enabled", true) ?: true
        set(value) { prefs?.edit()?.putBoolean("glass_enabled", value)?.apply() }

    actual var glassAlpha: Float
        get() = prefs?.getFloat("glass_alpha", 0.4f) ?: 0.4f
        set(value) { prefs?.edit()?.putFloat("glass_alpha", value)?.apply() }

    actual var isSplashScreenEnabled: Boolean
        get() = prefs?.getBoolean("splash_screen_enabled", true) ?: true
        set(value) { prefs?.edit()?.putBoolean("splash_screen_enabled", value)?.apply() }

    actual var isSplashSoundEnabled: Boolean
        get() = prefs?.getBoolean("splash_sound_enabled", true) ?: true
        set(value) { prefs?.edit()?.putBoolean("splash_sound_enabled", value)?.apply() }

    actual var isFirstLaunch: Boolean
        get() = prefs?.getBoolean("is_first_launch", true) ?: true
        set(value) { prefs?.edit()?.putBoolean("is_first_launch", value)?.apply() }

    actual var gameHighScore: Int
        get() = prefs?.getInt("game_high_score", 0) ?: 0
        set(value) { prefs?.edit()?.putInt("game_high_score", value)?.apply() }

    actual var isGameHardcore: Boolean
        get() = prefs?.getBoolean("game_hardcore", false) ?: false
        set(value) { prefs?.edit()?.putBoolean("game_hardcore", value)?.apply() }
}

actual fun changeAppIcon(newAliasName: String) {
    val context = androidContext ?: return
    val packageManager = context.packageManager
    val packageName = context.packageName
    
    val components = listOf("MainActivity", "MainActivityAlias1", "MainActivityAlias2", "MainActivityAlias3")
    
    components.forEach { component ->
        val state = if (component == newAliasName) 
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED 
        else 
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            
        packageManager.setComponentEnabledSetting(
            ComponentName(packageName, "$packageName.$component"),
            state,
            PackageManager.DONT_KILL_APP
        )
    }
}

package com.RIKAPLAY.zhirpem_app

import android.content.Context

private var androidContext: Context? = null

fun initThemeManager(context: Context) {
    androidContext = context
}

actual class ThemeManager {
    actual companion object {
        actual val TYPE_DEFAULT = "DEFAULT"
        actual val TYPE_MY_LIGHT = "material_you_light"
        actual val TYPE_MY_DARK = "material_you_dark"
    }

    private val prefs = androidContext?.getSharedPreferences("app_theme_settings", Context.MODE_PRIVATE)

    actual var themeType: String
        get() = prefs?.getString("theme_type", TYPE_DEFAULT) ?: TYPE_DEFAULT
        set(value) { prefs?.edit()?.putString("theme_type", value)?.apply() }

    actual var customColor: String
        get() = prefs?.getString("custom_color", "#E5DBFF") ?: "#E5DBFF"
        set(value) { prefs?.edit()?.putString("custom_color", value)?.apply() }
}

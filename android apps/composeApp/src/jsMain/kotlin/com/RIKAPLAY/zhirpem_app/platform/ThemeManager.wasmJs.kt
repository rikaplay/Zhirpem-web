package com.RIKAPLAY.zhirpem_app

import kotlinx.browser.localStorage

actual class ThemeManager {
    actual companion object {
        actual val TYPE_DEFAULT = "DEFAULT"
        actual val TYPE_MY_LIGHT = "material_you_light"
        actual val TYPE_MY_DARK = "material_you_dark"
    }

    actual var themeType: String
        get() = localStorage.getItem("theme_type") ?: TYPE_DEFAULT
        set(value) { localStorage.setItem("theme_type", value) }

    actual var customColor: String
        get() = localStorage.getItem("custom_color") ?: "#E5DBFF"
        set(value) { localStorage.setItem("custom_color", value) }
}

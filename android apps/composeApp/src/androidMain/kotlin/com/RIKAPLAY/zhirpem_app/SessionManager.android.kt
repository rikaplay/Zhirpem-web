package com.RIKAPLAY.zhirpem_app

import android.content.Context

private var androidContext: Context? = null

fun initSessionManager(context: Context) {
    androidContext = context
}

actual class SessionManager {
    private val prefs = androidContext?.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    actual var isLoggedIn: Boolean
        get() = prefs?.getBoolean("is_logged_in", false) ?: false
        set(value) { prefs?.edit()?.putBoolean("is_logged_in", value)?.apply() }

    actual var username: String?
        get() = prefs?.getString("username", null)
        set(value) { prefs?.edit()?.putString("username", value)?.apply() }

    actual var name: String?
        get() = prefs?.getString("name", null)
        set(value) { prefs?.edit()?.putString("name", value)?.apply() }

    actual fun logout() {
        prefs?.edit()?.clear()?.apply()
    }
}

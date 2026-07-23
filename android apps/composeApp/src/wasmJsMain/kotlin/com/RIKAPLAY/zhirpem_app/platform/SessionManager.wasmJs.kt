package com.RIKAPLAY.zhirpem_app

import kotlinx.browser.localStorage

actual class SessionManager {
    actual var isLoggedIn: Boolean
        get() = localStorage.getItem("is_logged_in") == "true"
        set(value) { localStorage.setItem("is_logged_in", value.toString()) }

    actual var username: String?
        get() = localStorage.getItem("username")
        set(value) { value?.let { localStorage.setItem("username", it) } }

    actual var name: String?
        get() = localStorage.getItem("name")
        set(value) { value?.let { localStorage.setItem("name", it) } }

    actual fun logout() {
        localStorage.clear()
    }
}

package com.RIKAPLAY.zhirpem_app.platform

import kotlinx.browser.window

actual fun showToast(message: String) {
    window.alert(message)
}

actual fun getAppVersion(): String = "1.5.2-web"

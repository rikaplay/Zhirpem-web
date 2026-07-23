package com.RIKAPLAY.zhirpem_app.platform

import android.content.Context
import android.widget.Toast

private var androidContext: Context? = null

fun initPlatformUtils(context: Context) {
    androidContext = context
}

actual fun showToast(message: String) {
    androidContext?.let {
        Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
    }
}

actual fun getAppVersion(): String {
    return try {
        androidContext?.let {
            it.packageManager.getPackageInfo(it.packageName, 0).versionName
        } ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }
}

package com.RIKAPLAY.zhirpem_app.platform

import android.content.Context
import com.onesignal.OneSignal

private var androidContext: Context? = null

fun initPushManager(context: Context) {
    androidContext = context
}

actual class PushManager {
    actual fun initialize(appId: String) {
        androidContext?.let { OneSignal.initWithContext(it, appId) }
    }

    actual fun setExternalId(id: String) {
        OneSignal.User.addAlias("external_id", id)
    }

    actual fun requestPermission() {
        OneSignal.Notifications.requestPermission(true)
    }
}

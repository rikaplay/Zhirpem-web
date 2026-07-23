package com.RIKAPLAY.zhirpem_app.platform

expect class PushManager {
    fun initialize(appId: String)
    fun setExternalId(id: String)
    fun requestPermission()
}

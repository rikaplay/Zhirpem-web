package com.RIKAPLAY.zhirpem_app.platform

expect class AppUpdater() {
    suspend fun checkForUpdates(): String?
    fun downloadAndInstall(url: String)
}

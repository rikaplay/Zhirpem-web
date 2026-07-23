package com.RIKAPLAY.zhirpem_app.platform

expect class NetworkMonitor() {
    suspend fun isActuallyConnected(): Boolean
}

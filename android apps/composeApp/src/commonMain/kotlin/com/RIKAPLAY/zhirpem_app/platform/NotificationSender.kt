package com.RIKAPLAY.zhirpem_app.platform

expect class NotificationSender() {
    suspend fun sendGlobalPush(title: String, body: String, imageUrl: String): Boolean
}

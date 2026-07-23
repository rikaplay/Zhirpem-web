package com.RIKAPLAY.zhirpem_app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.cloudinary.android.MediaManager
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.RIKAPLAY.zhirpem_app.BuildConfig

class ZhirpemApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initSettings(this)
        initMediaUploader(this)
        initPushManager(this)
        initOptimizationManager(this)
        initThemeManager(this)
        initSoundPlayer(this)
        initSessionManager(this)
        initPlatformUtils(this)
        initAppUpdater(this)

        // 0. Инициализация Push
        PushManager().initialize(BuildConfig.ONESIGNAL_APP_ID)

        // 1. Инициализация Cloudinary
        val config = HashMap<String, String>()
        config["cloud_name"] = BuildConfig.CLOUDINARY_CLOUD_NAME
        config["api_key"] = BuildConfig.CLOUDINARY_API_KEY
        config["api_secret"] = BuildConfig.CLOUDINARY_API_SECRET
        
        try {
            MediaManager.init(this, config)
        } catch (e: Exception) {
            // Уже инициализировано
        }

        // 2. Создание канала уведомлений (для Android 8.0+)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Уведомления Жирпем"
            val descriptionText = "Получайте уведомления о лайках, комментариях и сообщениях"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("zhirpem_notifications", name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager: NotificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}

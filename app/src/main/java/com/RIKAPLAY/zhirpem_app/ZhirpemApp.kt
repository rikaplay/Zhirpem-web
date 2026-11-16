package com.RIKAPLAY.zhirpem_app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.cloudinary.android.MediaManager
import com.onesignal.OneSignal
import com.RIKAPLAY.zhirpem_app.BuildConfig

class ZhirpemApp : Application(), coil.ImageLoaderFactory {
    /** Единый загрузчик картинок: кеш в памяти (25% доступной) и на диске (256 МБ), плавное появление. */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .memoryCache {
            coil.memory.MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            coil.disk.DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false) // Cloudinary отдаёт неудобные заголовки кеша, а картинки там не меняются
        .crossfade(150)
        .build()

    override fun onCreate() {
        super.onCreate()

        try {
            packageManager.setComponentEnabledSetting(
                android.content.ComponentName(packageName, "$packageName.MainActivity"),
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {}

        // 0. Инициализация OneSignal
        OneSignal.initWithContext(this, BuildConfig.ONESIGNAL_APP_ID)

        // Инициализация динамических правил цензуры
        try {
            CommunityRulesManager.initDynamicRules(com.google.firebase.firestore.FirebaseFirestore.getInstance())
        } catch (_: Exception) {}

        // 1. Инициализация Cloudinary
        val config = HashMap<String, String>()
        config["cloud_name"] = BuildConfig.CLOUDINARY_CLOUD_NAME
        config["api_key"] = BuildConfig.CLOUDINARY_API_KEY
        config["api_secret"] = BuildConfig.CLOUDINARY_API_SECRET
        
        try {
            MediaManager.init(this, config)
        } catch (_: Exception) {
            // Уже инициализировано
        }

        // 2. Создание канала уведомлений (для Android 8.0+)
        createNotificationChannel()
        CallNotifications.ensureChannels(this)

        // 3. Нужно знать, открыто ли приложение: от этого зависит, показывать ли фоновые уведомления
        AppVisibility.register(this)
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

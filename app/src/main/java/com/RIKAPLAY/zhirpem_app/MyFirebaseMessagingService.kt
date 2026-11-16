package com.RIKAPLAY.zhirpem_app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        if (data.isEmpty()) return

        when (data["type"]) {
            // Входящий звонок: показываем уведомление с кнопками, если приложение закрыто
            "CALL" -> {
                val chatId = data["chatId"] ?: return
                if (AppVisibility.isForeground) return // в приложении звонок и так виден
                CallNotifications.showIncomingCall(
                    context = this,
                    chatId = chatId,
                    callType = data["callType"] ?: "VIDEO",
                    fromName = data["fromName"] ?: data["fromId"] ?: "Звонок",
                    fromAvatar = data["fromAvatar"]
                )
            }

            // Звонящий положил трубку (или звонок сбросили): убираем уведомление
            "CALL_CANCEL" -> {
                val chatId = data["chatId"] ?: return
                CallNotifications.cancelIncomingCall(this, chatId)
            }

            // Новое личное сообщение
            "MESSAGE" -> {
                val chatId = data["chatId"] ?: return
                if (AppVisibility.isForeground) return
                CallNotifications.showMessage(
                    context = this,
                    chatId = chatId,
                    fromName = data["fromName"] ?: data["fromId"] ?: "Сообщение",
                    fromAvatar = data["fromAvatar"],
                    text = data["text"] ?: ""
                )
            }

            // Остальные push с заголовком и текстом
            else -> {
                val title = data["title"] ?: "Zhirpem"
                val body = data["body"] ?: ""
                sendNotification(title, body)
            }
        }
    }

    private fun sendNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "zhirpem_notifications"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.jirpem_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Уведомления Жирпем",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Канал для уведомлений о новых постах и сообщениях"
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Токен обновился: сохраняем новый для текущего пользователя
        val username = getSharedPreferences("user_session", Context.MODE_PRIVATE).getString("username", "") ?: ""
        PushTokenManager.save(username, token)
    }
}

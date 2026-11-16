package com.RIKAPLAY.zhirpem_app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import java.net.HttpURLConnection
import java.net.URL

/**
 * Уведомления о входящих звонках и личных сообщениях.
 * Работают, когда приложение закрыто: их запускает push от сервера (см. functions/index.js).
 */
object CallNotifications {
    const val CHANNEL_CALLS = "zhirpem_calls"
    const val CHANNEL_MESSAGES = "zhirpem_messages"

    const val ACTION_DECLINE = "com.RIKAPLAY.zhirpem_app.CALL_DECLINE"
    const val EXTRA_CHAT_ID = "chatId"

    private const val CALL_TIMEOUT_MS = 45_000L

    private fun manager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun callId(chatId: String) = ("call_" + chatId).hashCode()
    private fun messageId(chatId: String) = ("msg_" + chatId).hashCode()

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = manager(context)

        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val calls = NotificationChannel(CHANNEL_CALLS, "Входящие звонки", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Звонки в Жирпем: контакт и кнопки «Ответить» и «Сбросить»"
            setSound(
                ringtone,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 500, 700, 500, 700)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val messages = NotificationChannel(CHANNEL_MESSAGES, "Личные сообщения", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Новые сообщения в личных чатах"
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        nm.createNotificationChannel(calls)
        nm.createNotificationChannel(messages)
    }

    /** Загружает аватар и делает его круглым. Вызывать не на главном потоке. */
    private fun loadAvatar(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
            }
            val src = connection.inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
            val side = minOf(src.width, src.height)
            val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val scaled = Bitmap.createScaledBitmap(square, 192, 192, true)
            val out = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawCircle(96f, 96f, 96f, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(scaled, 0f, 0f, paint)
            out
        } catch (e: Exception) {
            null
        }
    }

    private fun openAppIntent(context: Context, requestCode: Int, configure: Intent.() -> Unit): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            configure()
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Входящий звонок: имя и фото контакта, кнопки «Сбросить» и «Ответить». */
    fun showIncomingCall(context: Context, chatId: String, callType: String, fromName: String, fromAvatar: String?) {
        ensureChannels(context)
        val id = callId(chatId)

        val answerPi = openAppIntent(context, id + 1) {
            putExtra(PendingNotificationAction.EXTRA_ANSWER_CALL, true)
            putExtra(PendingNotificationAction.EXTRA_CALL_CHAT, chatId)
        }
        val openPi = openAppIntent(context, id + 2) {}
        val declinePi = PendingIntent.getBroadcast(
            context, id + 3,
            Intent(context, CallActionReceiver::class.java).apply {
                action = ACTION_DECLINE
                putExtra(EXTRA_CHAT_ID, chatId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val builder = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.jirpem_logo)
            .setContentTitle(fromName)
            .setContentText(if (callType == "VIDEO") "Входящий видеозвонок" else "Входящий аудиозвонок")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(CALL_TIMEOUT_MS)
            .setContentIntent(openPi)
            .addAction(0, "Сбросить", declinePi)
            .addAction(0, "Ответить", answerPi)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(ringtone).setVibrate(longArrayOf(0, 700, 500, 700, 500, 700))
        }
        loadAvatar(fromAvatar)?.let { builder.setLargeIcon(it) }

        val notification = builder.build()
        // Звонит, пока его не примут, не сбросят или не истечёт время
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        manager(context).notify(id, notification)
    }

    fun cancelIncomingCall(context: Context, chatId: String) {
        manager(context).cancel(callId(chatId))
    }

    const val ACTION_REPLY = "com.RIKAPLAY.zhirpem_app.MESSAGE_REPLY"
    const val ACTION_MARK_READ = "com.RIKAPLAY.zhirpem_app.MESSAGE_MARK_READ"
    const val KEY_REPLY_TEXT = "reply_text"

    /** Собирает и показывает уведомление о сообщении с кнопками «Ответить» (поле ввода прямо в шторке) и «Прочитано». */
    private fun postMessageNotification(
        context: Context,
        chatId: String,
        fromName: String,
        avatar: Bitmap?,
        style: NotificationCompat.MessagingStyle,
        lastText: String,
        onlyAlertOnce: Boolean
    ) {
        val id = messageId(chatId)

        val openPi = openAppIntent(context, id) {
            putExtra(PendingNotificationAction.EXTRA_OPEN_CHAT, chatId)
        }

        val remoteInput = androidx.core.app.RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel("Ответить…")
            .build()
        // Для RemoteInput система должна дописать введённый текст в Intent, поэтому PendingIntent изменяемый
        val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val replyPi = PendingIntent.getBroadcast(
            context, id + 4,
            Intent(context, MessageActionReceiver::class.java).apply {
                action = ACTION_REPLY
                putExtra(EXTRA_CHAT_ID, chatId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
        )
        val replyAction = NotificationCompat.Action.Builder(0, "Ответить", replyPi)
            .addRemoteInput(remoteInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .build()

        val markReadPi = PendingIntent.getBroadcast(
            context, id + 5,
            Intent(context, MessageActionReceiver::class.java).apply {
                action = ACTION_MARK_READ
                putExtra(EXTRA_CHAT_ID, chatId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val markReadAction = NotificationCompat.Action.Builder(0, "Прочитано", markReadPi)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.jirpem_logo)
            .setStyle(style)
            .setContentTitle(fromName)
            .setContentText(lastText)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(onlyAlertOnce)
            .setContentIntent(openPi)
            .addAction(replyAction)
            .addAction(markReadAction)
        avatar?.let { builder.setLargeIcon(it) }

        manager(context).notify(id, builder.build())
    }

    /** Новое личное сообщение. Сообщения из одного чата складываются в одно уведомление. */
    fun showMessage(context: Context, chatId: String, fromName: String, fromAvatar: String?, text: String) {
        ensureChannels(context)
        val nm = manager(context)

        val avatar = loadAvatar(fromAvatar)
        val sender = Person.Builder().setName(fromName).apply {
            avatar?.let { setIcon(IconCompat.createWithBitmap(it)) }
        }.build()

        val existingStyle = try {
            nm.activeNotifications.firstOrNull { it.id == messageId(chatId) }?.notification
                ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
        } catch (e: Exception) {
            null
        }
        val style = existingStyle ?: NotificationCompat.MessagingStyle(Person.Builder().setName("Вы").build())
        style.addMessage(text, System.currentTimeMillis(), sender)

        postMessageNotification(context, chatId, fromName, avatar, style, text, onlyAlertOnce = false)
    }

    /**
     * После ответа из шторки обновляет уведомление: добавляет в переписку ответ пользователя.
     * Без этого система продолжала бы показывать индикатор «отправка…». Если [text] равен null,
     * уведомление просто возвращается в обычный вид (ответ не отправился).
     */
    fun finishReply(context: Context, chatId: String, text: String?) {
        val nm = manager(context)
        val existing = try {
            nm.activeNotifications.firstOrNull { it.id == messageId(chatId) }?.notification
        } catch (e: Exception) {
            null
        } ?: return
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(existing) ?: return
        val title = existing.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Сообщение"

        if (text != null) {
            style.addMessage(NotificationCompat.MessagingStyle.Message(text, System.currentTimeMillis(), null as Person?))
        }
        postMessageNotification(context, chatId, title, null, style, text ?: "", onlyAlertOnce = true)
    }

    fun cancelMessage(context: Context, chatId: String) {
        manager(context).cancel(messageId(chatId))
    }
}

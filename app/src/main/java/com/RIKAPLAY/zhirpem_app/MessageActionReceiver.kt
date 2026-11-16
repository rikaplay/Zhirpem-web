package com.RIKAPLAY.zhirpem_app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Кнопки в уведомлении о сообщении, работают, даже когда приложение закрыто:
 *  - «Ответить»: ответ вводится прямо в шторке и уходит в чат;
 *  - «Прочитано»: убирает уведомление и отмечает сообщения прочитанными.
 */
class MessageActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra(CallNotifications.EXTRA_CHAT_ID) ?: return
        val prefs = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val me = (prefs.getString("username", "") ?: "").replace("@", "").trim()

        when (intent.action) {
            CallNotifications.ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(CallNotifications.KEY_REPLY_TEXT)?.toString()?.trim().orEmpty()

                if (text.isEmpty() || me.isEmpty()) {
                    CallNotifications.finishReply(context, chatId, null)
                    return
                }

                val pending = goAsync()
                ChatRepository.sendTextMessage(chatId, me, text) { ok ->
                    if (ok) {
                        CallNotifications.finishReply(context, chatId, text)
                    } else {
                        CallNotifications.finishReply(context, chatId, null)
                        Toast.makeText(context, "Не удалось отправить ответ", Toast.LENGTH_SHORT).show()
                    }
                    pending.finish()
                }
            }

            CallNotifications.ACTION_MARK_READ -> {
                CallNotifications.cancelMessage(context, chatId)
                if (me.isEmpty()) return

                val pending = goAsync()
                val db = FirebaseFirestore.getInstance()
                db.collection("chats").document(chatId).collection("messages")
                    .whereEqualTo("isRead", false)
                    .limit(100)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val batch = db.batch()
                        var count = 0
                        for (doc in snapshot.documents) {
                            if (doc.getString("senderId") != me) {
                                batch.update(doc.reference, "isRead", true)
                                count++
                            }
                        }
                        if (count > 0) {
                            batch.commit().addOnCompleteListener { pending.finish() }
                        } else {
                            pending.finish()
                        }
                    }
                    .addOnFailureListener { pending.finish() }
            }
        }
    }
}

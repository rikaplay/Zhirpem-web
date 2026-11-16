package com.RIKAPLAY.zhirpem_app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.firestore.FirebaseFirestore

/** Кнопка «Сбросить» в уведомлении о входящем звонке: работает, даже если приложение закрыто. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CallNotifications.ACTION_DECLINE) return
        val chatId = intent.getStringExtra(CallNotifications.EXTRA_CHAT_ID) ?: return

        CallNotifications.cancelIncomingCall(context, chatId)

        // Удаляем звонок: у звонящего он завершится как «сброшен»
        val pending = goAsync()
        FirebaseFirestore.getInstance().collection("calls").document(chatId).delete()
            .addOnCompleteListener { pending.finish() }
    }
}

package com.RIKAPLAY.zhirpem_app

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Хранит FCM-токен устройства в профиле пользователя. По нему сервер (Cloud Functions)
 * присылает звонки и сообщения, даже когда приложение закрыто. Постоянных соединений
 * в фоне нет, поэтому батарея не тратится.
 */
object PushTokenManager {
    private const val TAG = "PushToken"

    private fun userRef(username: String) =
        FirebaseFirestore.getInstance().collection("users").document(username.replace("@", "").trim())

    /** Запоминает токен текущего устройства для пользователя (вызывать после входа). */
    fun sync(username: String) {
        if (username.isBlank() || username == "anonymous") return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> save(username, token) }
            .addOnFailureListener { Log.e(TAG, "Не удалось получить токен: ${it.message}") }
    }

    fun save(username: String, token: String) {
        if (username.isBlank() || username == "anonymous" || token.isBlank()) return
        userRef(username).update(
            mapOf(
                "fcmToken" to token,
                "fcmTokens" to FieldValue.arrayUnion(token)
            )
        ).addOnFailureListener { Log.e(TAG, "Не удалось сохранить токен: ${it.message}") }
    }

    /** Убирает токен этого устройства при выходе из аккаунта, чтобы чужие звонки сюда не приходили. */
    fun remove(username: String) {
        if (username.isBlank() || username == "anonymous") return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            userRef(username).update("fcmTokens", FieldValue.arrayRemove(token))
        }
    }
}

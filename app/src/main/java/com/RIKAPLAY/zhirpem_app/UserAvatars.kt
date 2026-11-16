package com.RIKAPLAY.zhirpem_app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Общий кэш аватарок пользователей. Показывает актуальную аватарку там, где её нельзя
 * хранить внутри документа (комментарии, уведомления), и мгновенно обновляется,
 * когда пользователь меняет аватарку на этом устройстве.
 */
object UserAvatarCache {
    private const val TTL_MS = 10 * 60 * 1000L

    private data class Entry(val url: String, val fetchedAt: Long)

    private val entries = mutableStateMapOf<String, Entry>()
    private val loading = HashSet<String>()

    private fun clean(username: String) = username.replace("@", "").trim()

    /** Текущее значение из кэша: null — ещё не загружено, пустая строка — аватарки нет. */
    fun peek(username: String): String? = entries[clean(username)]?.url

    fun put(username: String, url: String?) {
        val u = clean(username)
        if (u.isEmpty()) return
        entries[u] = Entry(url.orEmpty(), System.currentTimeMillis())
    }

    /** Загружает аватарку один раз и обновляет её раз в 10 минут, если экран остаётся открытым. */
    fun request(username: String) {
        val u = clean(username)
        if (u.isEmpty()) return
        val cached = entries[u]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < TTL_MS) return
        if (!loading.add(u)) return
        FirebaseFirestore.getInstance().collection("users").document(u).get()
            .addOnSuccessListener { doc -> put(u, doc.getString("avatarUrl")) }
            .addOnCompleteListener { loading.remove(u) }
    }
}

/** Актуальная аватарка пользователя. Пока грузится — [fallback]; null, если аватарки нет. */
@Composable
fun rememberUserAvatar(username: String, fallback: String? = null): String? {
    LaunchedEffect(username) { UserAvatarCache.request(username) }
    val cached = UserAvatarCache.peek(username)
    return when {
        cached == null -> fallback?.takeIf { it.isNotBlank() }
        cached.isNotBlank() -> cached
        else -> null
    }
}

object AvatarSync {
    /**
     * Разовая синхронизация при входе: если аватарка в профиле отличается от той, что мы уже
     * разносили по постам, обновляем посты. Так чинятся и старые посты тех, кто менял
     * аватарку до появления этой функции.
     */
    fun syncOnce(context: Context, db: FirebaseFirestore, username: String, currentUrl: String?) {
        val u = username.replace("@", "").trim()
        if (u.isEmpty() || currentUrl.isNullOrBlank()) return
        val prefs = context.getSharedPreferences("avatar_sync", Context.MODE_PRIVATE)
        if (prefs.getString("synced_$u", null) == currentUrl) return
        onAvatarChanged(context, db, u, currentUrl)
        prefs.edit().putString("synced_$u", currentUrl).apply()
    }

    /**
     * Вызывать после смены аватарки: обновляет кэш, сохранённую сессию и аватарку
     * во всех старых постах автора (в постах она хранится в самом документе).
     */
    fun onAvatarChanged(context: Context, db: FirebaseFirestore, username: String, newUrl: String) {
        val u = username.replace("@", "").trim()
        if (u.isEmpty()) return

        UserAvatarCache.put(u, newUrl)
        context.getSharedPreferences("avatar_sync", Context.MODE_PRIVATE)
            .edit().putString("synced_$u", newUrl).apply()

        val prefs = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        if (prefs.getString("username", "") == u) {
            prefs.edit().putString("avatarUrl", newUrl).apply()
        }

        db.collection("zhirpem_posts").whereEqualTo("handle", "@$u").get()
            .addOnSuccessListener { snapshot ->
                // В одном пакете Firestore допускает до 500 операций
                snapshot.documents.chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { batch.update(it.reference, "authorAvatarUrl", newUrl) }
                    batch.commit().addOnFailureListener {
                        android.util.Log.e("AvatarSync", "Не удалось обновить аватарку в постах: ${it.message}")
                    }
                }
            }
            .addOnFailureListener {
                android.util.Log.e("AvatarSync", "Не удалось найти посты автора: ${it.message}")
            }
    }
}

// ----------------------------------------------------------------------
//  Уведомления о лайках и подписках (раньше они нигде не создавались)
// ----------------------------------------------------------------------

private fun sessionName(context: Context): String =
    context.getSharedPreferences("user_session", Context.MODE_PRIVATE).getString("name", "Аноним") ?: "Аноним"

private fun sessionAvatar(context: Context): String =
    context.getSharedPreferences("user_session", Context.MODE_PRIVATE).getString("avatarUrl", "") ?: ""

/** Уведомление автору поста о лайке. Повторный лайк того же поста уведомление не дублирует. */
fun notifyPostLiked(context: Context, db: FirebaseFirestore, post: Post, myUsername: String) {
    val receiver = post.handle.replace("@", "")
    if (receiver.isEmpty() || receiver == myUsername || post.id.isEmpty() || myUsername.isEmpty()) return

    fun send() = sendNotification(
        db = db,
        senderId = myUsername,
        senderName = sessionName(context),
        senderAvatar = sessionAvatar(context),
        receiverId = receiver,
        type = "LIKE",
        postId = post.id,
        targetText = post.text.take(80)
    )

    db.collection("notifications")
        .whereEqualTo("senderId", myUsername)
        .whereEqualTo("type", "LIKE")
        .whereEqualTo("postId", post.id)
        .limit(1)
        .get()
        .addOnSuccessListener { if (it.isEmpty) send() }
        .addOnFailureListener { send() }
}

/** Уведомление о новой подписке. Подписка, отмена и повторная подписка дублей не создают. */
fun notifyFollowed(context: Context, db: FirebaseFirestore, myUsername: String, targetUsername: String) {
    val receiver = targetUsername.replace("@", "")
    if (receiver.isEmpty() || receiver == myUsername || myUsername.isEmpty()) return

    fun send() = sendNotification(
        db = db,
        senderId = myUsername,
        senderName = sessionName(context),
        senderAvatar = sessionAvatar(context),
        receiverId = receiver,
        type = "FOLLOW"
    )

    db.collection("notifications")
        .whereEqualTo("senderId", myUsername)
        .whereEqualTo("type", "FOLLOW")
        .whereEqualTo("receiverId", receiver)
        .limit(1)
        .get()
        .addOnSuccessListener { if (it.isEmpty) send() }
        .addOnFailureListener { send() }
}

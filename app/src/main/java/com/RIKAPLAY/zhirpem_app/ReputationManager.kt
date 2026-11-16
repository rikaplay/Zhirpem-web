package com.RIKAPLAY.zhirpem_app

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Итог награждения поста админом. */
enum class AwardResult { OK, ALREADY_AWARDED, ERROR }

object ReputationManager {

    const val POINTS_PER_LEVEL = 1000
    const val MAX_POINTS = 10000
    const val MIN_POINTS = -10000

    /** Сколько очков получает автор поста, который наградил админ. */
    const val POST_AWARD_POINTS = 500

    private const val TAG = "Reputation"

    /**
     * Меняет очки репутации одной транзакцией. Раньше очки читались и записывались двумя
     * отдельными запросами: если два действия приходили почти одновременно (например, лайк и комментарий),
     * одно из них затиралось. Ошибки теперь пишутся в Logcat с тегом "Reputation".
     */
    private fun changePoints(db: FirebaseFirestore, userId: String, delta: Int) {
        if (userId.isBlank() || delta == 0) return
        val cleanUserId = userId.replace("@", "").trim()
        if (cleanUserId.isEmpty()) return

        val userRef = db.collection("users").document(cleanUserId)
        db.runTransaction { transaction ->
            val doc = transaction.get(userRef)
            if (doc.exists()) {
                val currentPoints = (doc.getLong("reputationPoints")
                    ?: ((doc.getLong("reputation") ?: 0L) * POINTS_PER_LEVEL)).toInt()
                val newPoints = (currentPoints + delta).coerceIn(MIN_POINTS, MAX_POINTS)
                val newLevel = (newPoints / POINTS_PER_LEVEL).coerceIn(-10, 10)
                transaction.update(
                    userRef,
                    mapOf(
                        "reputationPoints" to newPoints,
                        "reputation" to newLevel
                    )
                )
            }
            Unit
        }.addOnFailureListener { e ->
            Log.e(TAG, "Не удалось изменить репутацию @$cleanUserId на $delta: ${e.message}")
        }
    }

    /**
     * Повышает количество очков репутации пользователя.
     * 1000 очков = 1 уровень рейтинга (от -10 до +10).
     */
    fun addPoints(db: FirebaseFirestore, userId: String, pointsToAdd: Int) {
        if (pointsToAdd <= 0) return
        changePoints(db, userId, pointsToAdd)
    }

    /**
     * Понижает количество очков репутации пользователя при нарушениях.
     */
    fun reducePoints(db: FirebaseFirestore, userId: String, pointsToDeduct: Int) {
        if (pointsToDeduct <= 0) return
        changePoints(db, userId, -pointsToDeduct)
    }

    /**
     * Награда админа за пост: автору +500 репутации, у поста появляется RGB-обводка.
     * Один пост можно наградить только один раз (проверка внутри транзакции).
     */
    fun awardPost(
        db: FirebaseFirestore,
        postId: String,
        authorHandle: String,
        onResult: (AwardResult) -> Unit
    ) {
        if (postId.isBlank() || authorHandle.isBlank()) {
            onResult(AwardResult.ERROR)
            return
        }
        val postRef = db.collection("zhirpem_posts").document(postId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(postRef)
            if (snapshot.getBoolean("awarded") == true) {
                AwardResult.ALREADY_AWARDED
            } else {
                transaction.update(
                    postRef,
                    mapOf(
                        "awarded" to true,
                        "awardedBy" to "admin",
                        "awardedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                )
                AwardResult.OK
            }
        }.addOnSuccessListener { result ->
            if (result == AwardResult.OK) {
                addPoints(db, authorHandle, POST_AWARD_POINTS)
            }
            onResult(result)
        }.addOnFailureListener { e ->
            Log.e(TAG, "Не удалось наградить пост $postId: ${e.message}")
            onResult(AwardResult.ERROR)
        }
    }

    /**
     * Проверяет первый за день вход пользователя в приложение (+100 репутации)
     */
    fun checkDailyLogin(context: Context, db: FirebaseFirestore, username: String) {
        if (username.isBlank()) return
        val cleanUsername = username.replace("@", "").trim()
        if (cleanUsername.isEmpty()) return

        val prefs = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastLoginDate = prefs.getString("last_daily_reputation_date", "")

        if (lastLoginDate != todayStr) {
            prefs.edit().putString("last_daily_reputation_date", todayStr).apply()
            addPoints(db, cleanUsername, 100)
        }
    }
}

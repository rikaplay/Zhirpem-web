package com.RIKAPLAY.zhirpem_app

import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

object CommunityRulesManager {

    const val ONE_WEEK_MILLIS = 7 * 24 * 60 * 60 * 1000L

    const val WARNING_1_TEXT =
        "вы нарушили правило сообщества Жирпем:вам выдано 1 предупреждение. Вы можете продолжать пользоваться приложением так, как вы им и пользовались. Но при повторном нарушении на этой неделе, вы лишитесь возможности писать людям, с которыми вы еще не общались. При третьем нарушении на этой неделе вам будет ограничен доступ в Жирпем, и ваша переписка с тремя нарушениями отправится на рассмотрение модерации, где они решат: давать ли вам бан на срок от 7 до 30 дней, или отменить нарушение, если вы не нарушали правило и/или нарушение было ошибочным. Не нарушайте правила!"

    const val WARNING_2_TEXT =
        "вы нарушили правило сообщества Жирпем:вам выдано 2 предупреждение. Вы лишились возможности писать людям, с которыми вы еще не общались. При третьем нарушении на этой неделе вам будет ограничен доступ в Жирпем, и ваша переписка с тремя нарушениями отправится на рассмотрение модерации, где они решат: давать ли вам бан на срок от 7 до 30 дней, или отменить нарушение. Не нарушайте правила!"

    const val WARNING_3_TEXT =
        "вы нарушили правило сообщества Жирпем:вам выдано 3 предупреждение. Вам ограничен доступ в Жирпем, и ваша переписка с тремя нарушениями отправлена на рассмотрение модерации, где они решат: давать ли вам бан на срок от 7 до 30 дней, или отменить нарушение, если вы не нарушали правило и/или нарушение было ошибочным. Не нарушайте правила!"

    val DEFAULT_PROHIBITED_WORDS = listOf(
        "хуй", "пидор", "блядь", "сука", "ебать", "сучка", "заебал", "уебок",
        "пизда", "гандон", "презерватив", "долбоеб", "мудак", "шлюха", "пидорас",
        "блять", "ебаный", "нахуй", "похуй", "охуеть", "хуйня", "пиздец",
        "fuck", "bitch", "shit", "cunt", "nigger", "dick", "pussy", "asshole"
    )

    var currentProhibitedWords: List<String> = DEFAULT_PROHIBITED_WORDS

    private val PROHIBITED_PATTERNS = listOf(
        Regex("(?i)\\b[хx][уu][йjяеёиыюаоe]\\w*"),
        Regex("(?i)\\b[пp][иi][дd][оoаaеe][рr]\\w*"),
        Regex("(?i)\\b[бb][лl][яa]([тt][ьd]?)?\\w*"),
        Regex("(?i)\\b[сs][уu][кk][аaiоe]?\\w*"),
        Regex("(?i)\\b[еeё][бb][аaпpтtлlйj]\\w*"),
        Regex("(?i)\\b[мm][аa][тt][еeпp]?\\w*"),
        Regex("(?i)\\b[гg][оo][вv][нn][оoаae]?\\w*"),
        Regex("(?i)\\b[чc][лl][eе][нn]\\w*"),
        Regex("(?i)\\bfuck\\w*"),
        Regex("(?i)\\bbitch\\w*"),
        Regex("(?i)\\bshit\\w*"),
        Regex("(?i)\\basshole\\w*"),
        Regex("(?i)\\bcunt\\w*"),
        Regex("(?i)\\bdick\\w*"),
        Regex("(?i)\\bpussy\\w*")
    )

    fun initDynamicRules(db: FirebaseFirestore) {
        db.collection("app_config").document("censorship")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val wordsList = (snapshot.get("words") as? List<*>)?.mapNotNull { it?.toString()?.trim() }?.filter { it.isNotEmpty() }
                    if (wordsList != null) {
                        currentProhibitedWords = wordsList
                    }
                } else {
                    db.collection("app_config").document("censorship").set(mapOf("words" to DEFAULT_PROHIBITED_WORDS))
                }
            }
    }

    fun containsProhibitedContent(text: String): Boolean {
        if (text.isBlank()) return false
        val lowerText = text.lowercase(Locale.getDefault())

        for (word in currentProhibitedWords) {
            if (lowerText.contains(word.lowercase(Locale.getDefault()))) return true
        }

        for (pattern in PROHIBITED_PATTERNS) {
            if (pattern.containsMatchIn(text)) return true
        }

        return false
    }

    fun censorText(text: String): String {
        if (text.isBlank()) return text
        var censored = text

        for (word in currentProhibitedWords) {
            val lowerWord = word.lowercase(Locale.getDefault())
            val regex = Regex("(?i)\\b" + Regex.escape(word) + "\\b")
            censored = regex.replace(censored, "***")
            if (censored.lowercase(Locale.getDefault()).contains(lowerWord)) {
                val subRegex = Regex("(?i)" + Regex.escape(word))
                censored = subRegex.replace(censored, "***")
            }
        }

        for (pattern in PROHIBITED_PATTERNS) {
            censored = pattern.replace(censored) { match ->
                "*".repeat(match.value.length.coerceAtLeast(3))
            }
        }

        return censored
    }

    data class UserWarningStatus(
        val count: Int = 0,
        val weekStart: Long = 0L,
        val isRestrictedNewChats: Boolean = false,
        val isAccessRestricted: Boolean = false,
        val bannedUntil: Long? = null
    )

    fun calculateWeeklyStatus(
        count: Int,
        weekStart: Long,
        bannedUntil: Long? = null
    ): UserWarningStatus {
        val now = System.currentTimeMillis()
        val isWeekExpired = (now - weekStart) > ONE_WEEK_MILLIS

        val effectiveCount = if (isWeekExpired) 0 else count
        val effectiveWeekStart = if (isWeekExpired) now else weekStart

        val isBannedNow = bannedUntil != null && bannedUntil > now

        return UserWarningStatus(
            count = effectiveCount,
            weekStart = effectiveWeekStart,
            isRestrictedNewChats = effectiveCount >= 2,
            isAccessRestricted = effectiveCount >= 3 || isBannedNow,
            bannedUntil = bannedUntil
        )
    }

    fun getWarningBannerText(count: Int): String {
        return when (count) {
            1 -> WARNING_1_TEXT
            2 -> WARNING_2_TEXT
            else -> WARNING_3_TEXT
        }
    }

    fun processViolation(
        db: FirebaseFirestore,
        userId: String,
        chatId: String,
        messageText: String,
        onResult: (newCount: Int, warningText: String) -> Unit
    ) {
        val userRef = db.collection("users").document(userId)

        userRef.get().addOnSuccessListener { doc ->
            val now = System.currentTimeMillis()
            val rawCount = (doc.getLong("warningsCount") ?: 0L).toInt()
            val rawWeekStart = doc.getLong("warningsWeekStart") ?: now
            val bannedUntil = doc.getLong("bannedUntil")

            val currentStatus = calculateWeeklyStatus(rawCount, rawWeekStart, bannedUntil)

            val newCount = currentStatus.count + 1
            val newWeekStart = if (currentStatus.count == 0) now else currentStatus.weekStart

            val isRestrictedNewChats = newCount >= 2
            val isAccessRestricted = newCount >= 3

            val updates = hashMapOf<String, Any>(
                "warningsCount" to newCount,
                "warningsWeekStart" to newWeekStart,
                "lastWarningTimestamp" to now,
                "isRestrictedNewChats" to isRestrictedNewChats,
                "isAccessRestricted" to isAccessRestricted
            )

            if (isAccessRestricted) {
                updates["isBanned"] = true
            }

            // Автоматическое понижение рейтинга за нарушение правил (-75 за 1-е, -200 за 2-е, -1000 за 3-е)
            val penalty = when (newCount) {
                1 -> 75
                2 -> 200
                else -> 1000
            }
            ReputationManager.reducePoints(db, userId, penalty)

            userRef.update(updates)

            // Save violation record
            val violationData = hashMapOf(
                "userId" to userId,
                "chatId" to chatId,
                "messageText" to messageText,
                "timestamp" to now,
                "violationNumber" to newCount
            )
            userRef.collection("violations").add(violationData)

            // If 3rd violation on this week, send report to moderation with chat history
            if (newCount >= 3) {
                val reportId = "rep_${userId}_${now}"
                db.collection("chats").document(chatId).collection("messages")
                    .limit(20)
                    .get()
                    .addOnSuccessListener { msgSnap ->
                        val chatHistory = msgSnap.documents.mapNotNull { m ->
                            val sender = m.getString("senderId") ?: ""
                            val txt = m.getString("text") ?: ""
                            if (txt.isNotEmpty()) "$sender: $txt" else null
                        }

                        val reportData = hashMapOf(
                            "id" to reportId,
                            "userId" to userId,
                            "chatId" to chatId,
                            "triggerMessage" to messageText,
                            "timestamp" to now,
                            "warningsCount" to newCount,
                            "status" to "PENDING",
                            "chatHistory" to chatHistory
                        )
                        db.collection("moderation_reports").document(reportId).set(reportData)
                    }
                    .addOnFailureListener {
                        val reportData = hashMapOf(
                            "id" to reportId,
                            "userId" to userId,
                            "chatId" to chatId,
                            "triggerMessage" to messageText,
                            "timestamp" to now,
                            "warningsCount" to newCount,
                            "status" to "PENDING"
                        )
                        db.collection("moderation_reports").document(reportId).set(reportData)
                    }
            }

            val warningText = getWarningBannerText(newCount)
            onResult(newCount, warningText)
        }.addOnFailureListener {
            val defaultText = getWarningBannerText(1)
            onResult(1, defaultText)
        }
    }
}

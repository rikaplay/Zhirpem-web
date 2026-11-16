package com.RIKAPLAY.zhirpem_app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlin.math.max

// ======================================================================
//  МОДЕЛИ
// ======================================================================

/** Максимальная длина видео и звука в Shorts. */
const val SHORT_MAX_MS = 180_000L

/** Лимит размера файла при загрузке (бесплатный тариф Cloudinary принимает видео до 100 МБ). */
const val SHORT_MAX_UPLOAD_BYTES = 100L * 1024 * 1024

/** Коллекция Firestore: shorts. Все поля с значениями по умолчанию, чтобы работал toObject(). */
data class ShortVideo(
    val id: String = "",
    val authorUsername: String = "",
    val authorName: String = "",
    val authorAvatarUrl: String? = null,
    val videoUrl: String = "",
    val caption: String = "",
    val soundId: String? = null,
    val soundTitle: String = "",
    val soundAuthor: String = "",
    /** true — звук взят из библиотеки: исходная дорожка видео не играет, вместо неё играет звук. */
    val muteOriginal: Boolean = false,
    val tags: List<String> = emptyList(),
    val likes: Int = 0,
    val likedBy: List<String> = emptyList(),
    val commentsCount: Int = 0,
    val repostsCount: Int = 0,
    val repostedBy: List<String> = emptyList(),
    val views: Int = 0,
    val durationMs: Long = 0L,
    val timestamp: Timestamp? = null
)

/** Коллекция Firestore: sounds. Звуки загружают сами пользователи, они попадают в общую библиотеку. */
data class ShortSound(
    val id: String = "",
    val title: String = "",
    val authorUsername: String = "",
    val audioUrl: String = "",
    val durationMs: Long = 0L,
    val uses: Int = 0,
    val favoritedBy: List<String> = emptyList(),
    /** true — «оригинальный звук», созданный автоматически из видео. */
    val isOriginal: Boolean = false,
    val timestamp: Timestamp? = null
)

/** Коллекция Firestore: shorts_comments. */
data class ShortComment(
    val id: String = "",
    val shortId: String = "",
    val authorUsername: String = "",
    val author: String = "",
    val text: String = "",
    val timestamp: Timestamp? = null
)

// ======================================================================
//  ССЫЛКИ НА МЕДИА (Cloudinary умеет делать превью и аудио из видео)
// ======================================================================

fun shortThumbUrl(videoUrl: String, width: Int = 360): String {
    if (!videoUrl.contains("/upload/")) return videoUrl
    val withTransform = videoUrl.replace("/upload/", "/upload/so_0,w_$width,c_limit,q_auto/")
    return withTransform.substringBeforeLast('.') + ".jpg"
}

fun shortPlayUrl(videoUrl: String): String =
    if (videoUrl.contains("/upload/")) videoUrl.replace("/upload/", "/upload/q_auto:good/") else videoUrl

/** Звук, извлечённый из видео: Cloudinary отдаёт аудио, если сменить расширение файла на .mp3. */
fun soundUrlFromVideo(videoUrl: String): String? =
    if (videoUrl.contains("/upload/")) videoUrl.substringBeforeLast('.') + ".mp3" else null

fun compactCount(n: Int): String = when {
    n >= 10_000_000 -> "${n / 1_000_000}M"
    n >= 1_000_000 -> "${n / 1_000_000},${(n % 1_000_000) / 100_000}M".replace(",0M", "M")
    n >= 10_000 -> "${n / 1000}K"
    n >= 1_000 -> "${n / 1000},${(n % 1000) / 100}K".replace(",0K", "K")
    else -> n.toString()
}

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

// ======================================================================
//  ГЛОБАЛЬНАЯ НАВИГАЦИЯ ВКЛАДКИ
// ======================================================================

object ShortsNav {
    /** Нажали «+» на вкладке Shorts: нужно открыть экран съёмки. */
    var createRequested by mutableStateOf(false)

    /** Открыт полноэкранный режим (съёмка/выбор звука): нижняя панель и «+» прячутся. */
    var fullscreen by mutableStateOf(false)

    fun requestCreate() { createRequested = true }
}

/** Ссылка на конкретное видео (например, из личных сообщений). */
object PendingShortLink {
    var shortId by mutableStateOf<String?>(null)
}

// ======================================================================
//  VIEWMODEL: ленты, лайки, комментарии, звуки
// ======================================================================

class ShortsViewModel : ViewModel() {
    private val db get() = FirebaseFirestore.getInstance()

    var forYou by mutableStateOf(listOf<ShortVideo>())
        private set
    var following by mutableStateOf(listOf<ShortVideo>())
        private set
    var isLoadingForYou by mutableStateOf(false)
        private set
    var isLoadingFollowing by mutableStateOf(false)
        private set

    /** Локальные правки (лайки и т.п.): видны сразу во всех списках, где есть это видео. */
    private val patches = mutableStateMapOf<String, ShortVideo>()

    fun resolve(video: ShortVideo): ShortVideo = patches[video.id] ?: video

    /** Удалённые в этой сессии видео: пропадают из всех списков сразу. */
    private val deleted = mutableStateMapOf<String, Boolean>()

    fun isDeleted(id: String): Boolean = deleted[id] == true

    private val seen = HashSet<String>()
    private var lastRecent: DocumentSnapshot? = null
    private var forYouEnded = false
    private val viewed = HashSet<String>()

    private fun parse(doc: DocumentSnapshot): ShortVideo? =
        doc.toObject(ShortVideo::class.java)?.copy(id = doc.id)

    // ---------------- Рекомендации: тот же метод, что и в основной ленте ----------------
    // 1) берём топ интересов пользователя (user_interests.scores),
    // 2) ищем видео с этими тегами, ранжируем по сумме баллов,
    // 3) добавляем свежие видео как бесконечный запасной поток.

    fun loadForYou(me: String, refresh: Boolean) {
        if (isLoadingForYou) return
        if (!refresh && forYouEnded) return
        isLoadingForYou = true
        if (refresh) {
            seen.clear()
            lastRecent = null
            forYouEnded = false
        }

        fun finish(batch: List<ShortVideo>) {
            val fresh = batch.filter { seen.add(it.id) }
            if (fresh.isEmpty() && !refresh) forYouEnded = true
            forYou = if (refresh) fresh else (forYou + fresh).distinctBy { it.id }
            isLoadingForYou = false
        }

        fun withRecent(ranked: List<ShortVideo>) {
            var q: Query = db.collection("shorts").orderBy("timestamp", Query.Direction.DESCENDING).limit(15)
            lastRecent?.let { q = q.startAfter(it) }
            q.get().addOnSuccessListener { snap ->
                snap.documents.lastOrNull()?.let { lastRecent = it }
                val recent = snap.documents.mapNotNull { parse(it) }
                finish((ranked + recent).distinctBy { it.id })
            }.addOnFailureListener { finish(ranked) }
        }

        if (me.isBlank()) { withRecent(emptyList()); return }

        db.collection("user_interests").document(me).get()
            .addOnSuccessListener { doc ->
                @Suppress("UNCHECKED_CAST")
                val scores = (doc.get("scores") as? Map<String, Number>)
                    ?.mapValues { it.value.toLong() } ?: emptyMap()
                val top = scores.entries.sortedByDescending { it.value }.take(10).map { it.key }
                if (top.isEmpty() || !refresh && lastRecent != null && seen.size > 30) {
                    withRecent(emptyList())
                    return@addOnSuccessListener
                }
                db.collection("shorts").whereArrayContainsAny("tags", top).limit(20).get()
                    .addOnSuccessListener { snap ->
                        val ranked = snap.documents.mapNotNull { parse(it) }
                            .filter { it.id !in seen }
                            .sortedByDescending { v ->
                                v.tags.sumOf { scores[it] ?: 0L } + (0..3).random()
                            }
                        withRecent(ranked)
                    }
                    .addOnFailureListener { withRecent(emptyList()) }
            }
            .addOnFailureListener { withRecent(emptyList()) }
    }

    fun loadFollowing(me: String) {
        if (isLoadingFollowing || me.isBlank()) return
        isLoadingFollowing = true
        db.collection("follows").whereEqualTo("follower", me).get()
            .addOnSuccessListener { snap ->
                val authors = snap.documents.mapNotNull { it.getString("following") }.distinct()
                if (authors.isEmpty()) {
                    following = emptyList()
                    isLoadingFollowing = false
                    return@addOnSuccessListener
                }
                val chunks = authors.chunked(30)
                val collected = ArrayList<ShortVideo>()
                var remaining = chunks.size
                chunks.forEach { chunk ->
                    db.collection("shorts").whereIn("authorUsername", chunk).limit(40).get()
                        .addOnSuccessListener { s -> collected.addAll(s.documents.mapNotNull { parse(it) }) }
                        .addOnCompleteListener {
                            remaining--
                            if (remaining == 0) {
                                following = collected.distinctBy { it.id }
                                    .sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
                                isLoadingFollowing = false
                            }
                        }
                }
            }
            .addOnFailureListener { isLoadingFollowing = false }
    }

    // ---------------- Действия ----------------

    fun toggleLike(context: Context, video: ShortVideo, me: String) {
        if (me.isBlank()) return
        val cur = resolve(video)
        val liked = me in cur.likedBy
        patches[cur.id] = cur.copy(
            likes = max(0, cur.likes + if (liked) -1 else 1),
            likedBy = if (liked) cur.likedBy - me else cur.likedBy + me
        )
        db.collection("shorts").document(cur.id).update(
            mapOf(
                "likedBy" to if (liked) FieldValue.arrayRemove(me) else FieldValue.arrayUnion(me),
                "likes" to FieldValue.increment(if (liked) -1 else 1)
            )
        )
        if (!liked) {
            RecommendationManager.trackInteraction(me, cur.tags, 2)
            notifyAuthor(context, cur, me, "LIKE", "")
        }
    }

    fun toggleRepost(video: ShortVideo, me: String) {
        if (me.isBlank()) return
        val cur = resolve(video)
        val reposted = me in cur.repostedBy
        patches[cur.id] = cur.copy(
            repostsCount = max(0, cur.repostsCount + if (reposted) -1 else 1),
            repostedBy = if (reposted) cur.repostedBy - me else cur.repostedBy + me
        )
        db.collection("shorts").document(cur.id).update(
            mapOf(
                "repostedBy" to if (reposted) FieldValue.arrayRemove(me) else FieldValue.arrayUnion(me),
                "repostsCount" to FieldValue.increment(if (reposted) -1 else 1)
            )
        )
        if (!reposted) RecommendationManager.trackInteraction(me, cur.tags, 3)
    }

    /** Засчитывает просмотр один раз за сессию. */
    fun markViewed(video: ShortVideo, me: String) {
        if (!viewed.add(video.id)) return
        val cur = resolve(video)
        patches[cur.id] = cur.copy(views = cur.views + 1)
        db.collection("shorts").document(cur.id).update("views", FieldValue.increment(1))
        if (me.isNotBlank()) RecommendationManager.trackInteraction(me, cur.tags, 1)
    }

    fun addComment(context: Context, video: ShortVideo, me: String, text: String) {
        val clean = text.trim()
        if (me.isBlank() || clean.isEmpty()) return
        val prefs = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val cur = resolve(video)
        db.collection("shorts_comments").add(
            hashMapOf(
                "shortId" to cur.id,
                "authorUsername" to "@$me",
                "author" to (prefs.getString("name", me) ?: me),
                "text" to clean,
                "timestamp" to FieldValue.serverTimestamp()
            )
        )
        patches[cur.id] = cur.copy(commentsCount = cur.commentsCount + 1)
        db.collection("shorts").document(cur.id).update("commentsCount", FieldValue.increment(1))
        RecommendationManager.trackInteraction(me, cur.tags, 2)
        notifyAuthor(context, cur, me, "COMMENT", clean)
    }

    /** Удаляет видео вместе с комментариями. Файл на Cloudinary остаётся: клиент не может его стереть. */
    fun deleteShort(video: ShortVideo) {
        deleted[video.id] = true
        forYou = forYou.filter { it.id != video.id }
        following = following.filter { it.id != video.id }

        db.collection("shorts").document(video.id).delete()
        db.collection("shorts_comments").whereEqualTo("shortId", video.id).get()
            .addOnSuccessListener { snap ->
                snap.documents.chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit()
                }
            }
        if (!video.soundId.isNullOrEmpty()) {
            db.collection("sounds").document(video.soundId).update("uses", FieldValue.increment(-1))
        }
    }

    private fun notifyAuthor(context: Context, video: ShortVideo, me: String, type: String, text: String) {
        if (video.authorUsername == me) return
        val prefs = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        fun send() = sendNotification(
            db = db,
            senderId = me,
            senderName = prefs.getString("name", me) ?: me,
            senderAvatar = prefs.getString("avatarUrl", "") ?: "",
            receiverId = video.authorUsername,
            type = type,
            text = text,
            postId = "short:${video.id}",
            targetText = "🎬 " + video.caption.take(70)
        )
        if (type == "LIKE") {
            db.collection("notifications")
                .whereEqualTo("senderId", me).whereEqualTo("type", "LIKE")
                .whereEqualTo("postId", "short:${video.id}").limit(1).get()
                .addOnSuccessListener { if (it.isEmpty) send() }
                .addOnFailureListener { send() }
        } else send()
    }

    // ---------------- Звуки ----------------

    /** Удаляет звук из библиотеки (только свой). Файл на Cloudinary остаётся. */
    fun deleteSound(sound: ShortSound) {
        db.collection("sounds").document(sound.id).delete()
    }

    fun toggleFavoriteSound(sound: ShortSound, me: String, nowFavorite: Boolean) {
        if (me.isBlank()) return
        db.collection("sounds").document(sound.id).update(
            "favoritedBy", if (nowFavorite) FieldValue.arrayUnion(me) else FieldValue.arrayRemove(me)
        )
        if (nowFavorite) RecommendationManager.trackInteraction(me, listOf("sound_${sound.id}"), 3)
    }
}

// ======================================================================
//  ПУБЛИКАЦИЯ ВИДЕО И ЗАГРУЗКА ЗВУКОВ
// ======================================================================

object ShortsPublisher {
    private const val CLOUD = "dcwp4nm3e"
    private const val PRESET = "ProfilePIC"

    fun readDurationMs(context: Context, uri: Uri): Long = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(context, uri)
        val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        ms
    } catch (e: Exception) { 0L }

    /** Размер файла по uri; 0, если узнать не удалось. */
    fun readSizeBytes(context: Context, uri: Uri): Long = try {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        } ?: 0L
    } catch (e: Exception) { 0L }

    /** Превращает ответ Cloudinary в понятное сообщение. */
    private fun friendlyUploadError(detail: String?): String = when {
        detail == null -> "Не удалось загрузить файл. Проверьте интернет и попробуйте ещё раз"
        detail.contains("too large", ignoreCase = true) || detail.contains("size", ignoreCase = true) ->
            "Файл слишком большой для загрузки. Сделайте видео короче или с меньшим размером"
        else -> "Не удалось загрузить: $detail"
    }

    fun extractTags(caption: String, soundId: String?): List<String> {
        val tags = Regex("#([\\p{L}\\p{N}_]+)").findAll(caption)
            .map { it.groupValues[1].lowercase() }.distinct().toMutableList()
        if (!soundId.isNullOrEmpty()) tags.add("sound_$soundId")
        return tags.take(10)
    }

    /**
     * Загружает видео и создаёт документ в shorts.
     * Если звук из библиотеки не выбран, автоматически создаётся «оригинальный звук»
     * (аудиодорожка этого видео), под который потом смогут снимать другие.
     */
    fun publish(
        context: Context,
        videoUri: Uri,
        caption: String,
        sound: ShortSound?,
        durationMs: Long,
        onDone: (ShortVideo) -> Unit,
        onError: (String) -> Unit,
        onProgress: (Float) -> Unit = {}
    ) {
        val prefs = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val me = prefs.getString("username", "") ?: ""
        if (me.isEmpty()) { onError("Нужно войти в аккаунт"); return }
        val name = prefs.getString("name", me) ?: me
        val avatar = prefs.getString("avatarUrl", "") ?: ""
        val db = FirebaseFirestore.getInstance()

        if (readSizeBytes(context, videoUri) > SHORT_MAX_UPLOAD_BYTES) {
            onError("Видео больше 100 МБ. Сделайте его короче или выберите файл поменьше")
            return
        }

        var failureDetail: String? = null
        uploadImageToCloudinary(
            context = context,
            imageUri = videoUri,
            mediaType = MediaType.VIDEO,
            cloudName = CLOUD,
            uploadPreset = PRESET,
            onSuccess = { videoUrl ->
                val shortRef = db.collection("shorts").document()

                fun createShort(finalSound: ShortSound?, muteOriginal: Boolean) {
                    val data = ShortVideo(
                        id = shortRef.id,
                        authorUsername = me,
                        authorName = name,
                        authorAvatarUrl = avatar.ifEmpty { null },
                        videoUrl = videoUrl,
                        caption = caption.trim(),
                        soundId = finalSound?.id,
                        soundTitle = finalSound?.title.orEmpty(),
                        soundAuthor = finalSound?.authorUsername.orEmpty(),
                        muteOriginal = muteOriginal,
                        tags = extractTags(caption, finalSound?.id),
                        durationMs = durationMs
                    )
                    val map = hashMapOf<String, Any?>(
                        "authorUsername" to data.authorUsername,
                        "authorName" to data.authorName,
                        "authorAvatarUrl" to data.authorAvatarUrl,
                        "videoUrl" to data.videoUrl,
                        "caption" to data.caption,
                        "soundId" to data.soundId,
                        "soundTitle" to data.soundTitle,
                        "soundAuthor" to data.soundAuthor,
                        "muteOriginal" to data.muteOriginal,
                        "tags" to data.tags,
                        "likes" to 0,
                        "likedBy" to emptyList<String>(),
                        "commentsCount" to 0,
                        "repostsCount" to 0,
                        "repostedBy" to emptyList<String>(),
                        "views" to 0,
                        "durationMs" to data.durationMs,
                        "timestamp" to FieldValue.serverTimestamp()
                    )
                    shortRef.set(map)
                        .addOnSuccessListener {
                            finalSound?.let {
                                db.collection("sounds").document(it.id).update("uses", FieldValue.increment(1))
                            }
                            onDone(data)
                        }
                        .addOnFailureListener { onError(it.message ?: "Не удалось опубликовать") }
                }

                if (sound != null) {
                    createShort(sound, muteOriginal = true)
                } else {
                    val audio = soundUrlFromVideo(videoUrl)
                    if (audio == null) {
                        createShort(null, muteOriginal = false)
                    } else {
                        val soundRef = db.collection("sounds").document()
                        val original = ShortSound(
                            id = soundRef.id,
                            title = "Оригинальный звук — @$me",
                            authorUsername = me,
                            audioUrl = audio,
                            durationMs = durationMs,
                            uses = 0,
                            isOriginal = true
                        )
                        soundRef.set(
                            hashMapOf(
                                "title" to original.title,
                                "authorUsername" to me,
                                "audioUrl" to audio,
                                "durationMs" to durationMs,
                                "uses" to 0,
                                "favoritedBy" to emptyList<String>(),
                                "isOriginal" to true,
                                "timestamp" to FieldValue.serverTimestamp()
                            )
                        ).addOnCompleteListener {
                            // Своя дорожка остаётся в видео: она и есть «оригинальный звук»
                            createShort(if (it.isSuccessful) original else null, muteOriginal = false)
                        }
                    }
                }
            },
            onError = { onError(friendlyUploadError(failureDetail)) },
            onProgress = onProgress,
            onErrorDetail = { failureDetail = it }
        )
    }

    /** Загрузка собственного звука в общую библиотеку. */
    fun uploadSound(
        context: Context,
        audioUri: Uri,
        title: String,
        onDone: (ShortSound) -> Unit,
        onError: (String) -> Unit,
        onProgress: (Float) -> Unit = {}
    ) {
        val prefs = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val me = prefs.getString("username", "") ?: ""
        if (me.isEmpty()) { onError("Нужно войти в аккаунт"); return }
        val duration = readDurationMs(context, audioUri)
        if (duration > SHORT_MAX_MS) { onError("Звук должен быть не длиннее 3 минут"); return }
        val db = FirebaseFirestore.getInstance()

        var failureDetail: String? = null
        uploadImageToCloudinary(
            context = context,
            imageUri = audioUri,
            mediaType = MediaType.AUDIO,
            cloudName = CLOUD,
            uploadPreset = PRESET,
            onSuccess = { url ->
                val ref = db.collection("sounds").document()
                val clean = title.trim().ifEmpty { "Звук @$me" }
                ref.set(
                    hashMapOf(
                        "title" to clean,
                        "authorUsername" to me,
                        "audioUrl" to url,
                        "durationMs" to duration,
                        "uses" to 0,
                        "favoritedBy" to emptyList<String>(),
                        "isOriginal" to false,
                        "timestamp" to FieldValue.serverTimestamp()
                    )
                ).addOnSuccessListener {
                    onDone(ShortSound(id = ref.id, title = clean, authorUsername = me, audioUrl = url, durationMs = duration))
                }.addOnFailureListener { onError(it.message ?: "Не удалось сохранить звук") }
            },
            onError = { onError(friendlyUploadError(failureDetail)) },
            onProgress = onProgress,
            onErrorDetail = { failureDetail = it }
        )
    }
}

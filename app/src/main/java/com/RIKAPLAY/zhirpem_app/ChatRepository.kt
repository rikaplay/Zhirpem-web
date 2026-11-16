package com.RIKAPLAY.zhirpem_app

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ChatRepository {

    // Те же облако и пресет, что используются для аватарок, баннеров и постов (там загрузка работает)
    private const val CLOUD_NAME = "dcwp4nm3e"
    private const val UPLOAD_PRESET = "ProfilePIC"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Считает отправленные байты, чтобы кольцо в чате заполнялось по ходу отправки. */
    private class ProgressBody(
        private val delegate: RequestBody,
        private val onProgress: (Float) -> Unit
    ) : RequestBody() {
        override fun contentType() = delegate.contentType()
        override fun contentLength() = delegate.contentLength()

        override fun writeTo(sink: BufferedSink) {
            val total = contentLength()
            var written = 0L
            var lastPercent = -1
            val counting = object : ForwardingSink(sink) {
                override fun write(source: Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    written += byteCount
                    if (total > 0) {
                        val percent = (written * 100 / total).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            val buffered = counting.buffer()
            delegate.writeTo(buffered)
            buffered.flush()
        }
    }

    /**
     * Загрузка файла напрямую в Cloudinary по HTTP (как загружаются аватарки и посты):
     * без фоновой службы SDK. Все колбэки вызываются на главном потоке.
     */
    private fun uploadViaRest(
        context: Context,
        fileUri: Uri,
        messageType: String,
        token: String,
        onProgress: (Float) -> Unit,
        onUrl: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                val bytes = context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    mainHandler.post { onError("файл пустой или недоступен") }
                    return@Thread
                }

                val (resourceType, fileName, mime) = when (messageType) {
                    "voice" -> Triple("video", "voice_${System.currentTimeMillis()}.m4a", "audio/mp4")
                    "video_square" -> Triple("video", "video_${System.currentTimeMillis()}.mp4", "video/mp4")
                    else -> Triple("image", "image_${System.currentTimeMillis()}.jpg", "image/jpeg")
                }

                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", fileName, bytes.toRequestBody(mime.toMediaType()))
                    .addFormDataPart("upload_preset", UPLOAD_PRESET)
                    .build()

                val request = Request.Builder()
                    .url("https://api.cloudinary.com/v1_1/$CLOUD_NAME/$resourceType/upload")
                    .post(ProgressBody(multipart) { p -> mainHandler.post { onProgress(p) } })
                    .build()

                val call = httpClient.newCall(request)
                activeCalls[token] = call
                try {
                    call.execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            val url = JSONObject(body).optString("secure_url")
                            if (url.isNotBlank()) {
                                mainHandler.post { onUrl(url) }
                            } else {
                                mainHandler.post { onError("сервер не вернул ссылку на файл") }
                            }
                        } else {
                            val message = try {
                                JSONObject(body).optJSONObject("error")?.optString("message") ?: body.take(120)
                            } catch (e: Exception) {
                                body.take(120)
                            }
                            Log.e("ChatUpload", "Cloudinary HTTP ${response.code}: $message")
                            mainHandler.post { onError("HTTP ${response.code}: $message") }
                        }
                    }
                } finally {
                    activeCalls.remove(token)
                }
            } catch (e: Exception) {
                Log.e("ChatUpload", "Ошибка загрузки по HTTP", e)
                val cancelled = e.message?.contains("Canceled", ignoreCase = true) == true
                mainHandler.post { onError(if (cancelled) "CANCELLED" else (e.message ?: "ошибка сети")) }
            }
        }.start()
    }

    /** Запасной способ: загрузка через SDK Cloudinary (если прямая загрузка не удалась). */
    private fun uploadViaSdk(
        context: Context,
        fileUri: Uri,
        messageType: String,
        onRequestId: (String) -> Unit,
        onProgress: (Float) -> Unit,
        onUrl: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val resourceType = if (messageType == "image") "image" else "video"
        try {
            val requestId = MediaManager.get().upload(fileUri)
                .unsigned("mediapres")
                .option("resource_type", resourceType)
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {
                        onProgress(0f)
                    }

                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                        if (totalBytes > 0) onProgress((bytes.toFloat() / totalBytes).coerceIn(0f, 1f))
                    }

                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val secureUrl = resultData["secure_url"] as? String
                        if (secureUrl == null) onError("сервер не вернул ссылку на файл") else onUrl(secureUrl)
                    }

                    override fun onError(requestId: String, error: ErrorInfo) {
                        Log.e("ChatUpload", "Cloudinary SDK: ${error.code} ${error.description}")
                        onError("${error.code}: ${error.description}")
                    }

                    override fun onReschedule(requestId: String, error: ErrorInfo) {
                    }
                })
                .dispatch(context)
            onRequestId(requestId)
        } catch (e: Exception) {
            Log.e("ChatUpload", "SDK: не удалось начать загрузку", e)
            onError(e.message ?: "не удалось начать загрузку")
        }
    }

    fun uploadMediaToCloudinary(
        context: Context,
        fileUri: Uri,
        messageType: String, // "voice", "video_square" или "image"
        chatId: String,
        currentUserId: String,
        senderName: String = "",
        senderAvatar: String = "",
        durationMs: Long = 0L,
        audioPeaks: List<Int> = emptyList(),
        onRequestId: (String) -> Unit = {},
        onProgress: (Float) -> Unit = {},
        onFailure: (String) -> Unit = {},
        onComplete: (Boolean) -> Unit = {}
    ) {
        val appContext = context.applicationContext
        val token = "rest:" + UUID.randomUUID()
        onRequestId(token)

        fun publish(url: String) {
            onProgress(1f)
            sendMessageToFirestore(
                chatId = chatId,
                senderId = currentUserId,
                mediaUrl = url,
                type = messageType,
                senderName = senderName,
                senderAvatar = senderAvatar,
                durationMs = durationMs,
                audioPeaks = audioPeaks,
                onFailure = onFailure,
                onResult = onComplete
            )
        }

        uploadViaRest(
            context = appContext,
            fileUri = fileUri,
            messageType = messageType,
            token = token,
            onProgress = onProgress,
            onUrl = { url -> publish(url) },
            onError = { restReason ->
                if (restReason == "CANCELLED") {
                    onComplete(false)
                } else {
                    // Прямая загрузка не удалась: пробуем способ через SDK
                    Log.w("ChatUpload", "Прямая загрузка не удалась ($restReason), пробуем SDK")
                    onProgress(0f)
                    uploadViaSdk(
                        context = appContext,
                        fileUri = fileUri,
                        messageType = messageType,
                        onRequestId = onRequestId,
                        onProgress = onProgress,
                        onUrl = { url -> publish(url) },
                        onError = { sdkReason ->
                            onFailure("$restReason; запасной способ: $sdkReason")
                            onComplete(false)
                        }
                    )
                }
            }
        )
    }

    /**
     * Отправка текстового сообщения без экрана чата (например, ответ из шторки уведомлений).
     * Пакетная запись работает и без связи: сообщение уйдёт, когда она появится.
     */
    fun sendTextMessage(chatId: String, senderId: String, text: String, onResult: (Boolean) -> Unit = {}) {
        val db = FirebaseFirestore.getInstance()
        val now = System.currentTimeMillis()
        val chatRef = db.collection("chats").document(chatId)
        val messageRef = chatRef.collection("messages").document()

        val message = hashMapOf<String, Any?>(
            "senderId" to senderId,
            "text" to text,
            "timestamp" to now,
            "mediaUrl" to "",
            "mediaType" to MediaType.NONE.name,
            "forwardedPostId" to null,
            "replyToId" to null,
            "replyToText" to null,
            "isRead" to false,
            "expiresAt" to null
        )
        val chatUpdate = hashMapOf<String, Any?>(
            "id" to chatId,
            "lastMessage" to text,
            "lastMessageTimestamp" to now
        )
        val peer = ChatPeerCache.peerOf(chatId, senderId)
        if (peer.isNotEmpty() && chatId == listOf(senderId, peer).sorted().joinToString("_")) {
            chatUpdate["participants"] = FieldValue.arrayUnion(senderId, peer)
        }

        val batch = db.batch()
        batch.set(messageRef, message)
        batch.set(chatRef, chatUpdate, SetOptions.merge())
        batch.commit()
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener {
                Log.e("ChatSend", "Не удалось отправить сообщение: ${it.message}")
                onResult(false)
            }
    }

    /** Отменяет загрузку файла (кнопка × в сообщении, которое отправляется). */
    fun cancelUpload(requestId: String) {
        if (requestId.startsWith("rest:")) {
            activeCalls.remove(requestId)?.cancel()
            return
        }
        try {
            MediaManager.get().cancelRequest(requestId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendMessageToFirestore(
        chatId: String, 
        senderId: String, 
        mediaUrl: String, 
        type: String,
        senderName: String,
        senderAvatar: String,
        durationMs: Long = 0L,
        audioPeaks: List<Int> = emptyList(),
        onFailure: (String) -> Unit = {},
        onResult: (Boolean) -> Unit = {}
    ) {
        val db = FirebaseFirestore.getInstance()
        val chatRef = db.collection("chats").document(chatId)
        val messageRef = chatRef.collection("messages").document()

        val mediaType = when(type) {
            "image" -> MediaType.IMAGE
            "video_square", "voice" -> MediaType.VIDEO
            else -> MediaType.IMAGE
        }

        val messageMap = hashMapOf<String, Any?>(
            "senderId" to senderId,
            "text" to "",
            "mediaUrl" to mediaUrl,
            "mediaType" to mediaType.name,
            "timestamp" to System.currentTimeMillis(),
            "replyToId" to if (type == "voice") "voice" else null, // метка для старых версий приложения
            "kind" to type,
            "durationMs" to durationMs,
            "audioPeaks" to audioPeaks
        )

        // Раньше здесь была транзакция: она не работает без связи с сервером и падает, если документа чата ещё нет.
        // Пакетная запись с merge надёжнее: создаёт или обновляет чат и добавляет сообщение за один раз.
        val preview = when (type) {
            "voice" -> "🎤 Голосовое сообщение"
            "video_square" -> "🎥 Видеосообщение"
            else -> "📷 Фото"
        }
        val peer = ChatPeerCache.peerOf(chatId, senderId)
        val chatUpdate = hashMapOf<String, Any?>(
            "id" to chatId,
            "lastMessage" to preview,
            "lastMessageTimestamp" to System.currentTimeMillis()
        )
        // Участников дописываем только для обычных чатов «userA_userB», чтобы не испортить старые
        if (peer.isNotEmpty() && chatId == listOf(senderId, peer).sorted().joinToString("_")) {
            chatUpdate["participants"] = FieldValue.arrayUnion(senderId, peer)
        }

        val batch = db.batch()
        batch.set(messageRef, messageMap)
        batch.set(chatRef, chatUpdate, SetOptions.merge())
        batch.commit().addOnSuccessListener {
            onResult(true)

            // Уведомление собеседнику (только если сообщение действительно отправлено)
            val peerId = ChatPeerCache.peerOf(chatId, senderId)
            if (peerId.isNotEmpty()) {
                checkRecipientAndNotify(
                    db = db,
                    senderId = senderId,
                    senderName = senderName,
                    senderAvatar = senderAvatar,
                    receiverId = peerId,
                    chatId = chatId,
                    text = when (type) {
                        "voice" -> "🎤 Голосовое сообщение"
                        "video_square" -> "🎥 Видеосообщение"
                        else -> "📷 Фото"
                    }
                )
            }
        }.addOnFailureListener { e ->
            Log.e("ChatUpload", "Не удалось записать сообщение в Firestore", e)
            onFailure("запись сообщения: ${e.message}")
            onResult(false)
        }
    }

    private fun checkRecipientAndNotify(
        db: FirebaseFirestore,
        senderId: String,
        senderName: String,
        senderAvatar: String,
        receiverId: String,
        chatId: String,
        text: String
    ) {
        db.collection("users").document(receiverId).get().addOnSuccessListener { doc ->
            val isOnlyVerified = doc.getBoolean("isOnlyVerifiedMessages") ?: false
            
            fun proceedWithNotification() {
                val status = doc.getString("status") ?: "offline"
                val currentScreen = doc.getString("currentScreen") ?: ""
                val expectedScreen = "ChatScreen_$senderId" // Формат экрана чата

                // 1. Всегда отправляем Push (через существующую функцию)
                sendNotification(
                    db = db,
                    senderId = senderId,
                    senderName = senderName,
                    senderAvatar = senderAvatar,
                    receiverId = receiverId,
                    type = "CHAT_MESSAGE",
                    text = text
                )

                // 2. Если не в чате или оффлайн -> Запись в ленту уведомлений
                if (status == "offline" || currentScreen != expectedScreen) {
                    val feedNotify = hashMapOf(
                        "senderId" to senderId,
                        "senderName" to senderName,
                        "senderAvatarUrl" to senderAvatar,
                        "receiverId" to receiverId,
                        "type" to "CHAT_MESSAGE",
                        "text" to text,
                        "timestamp" to com.google.firebase.Timestamp.now(),
                        "chatId" to chatId
                    )
                    db.collection("notifications").add(feedNotify)
                }
            }

            if (isOnlyVerified) {
                db.collection("users").document(senderId).get().addOnSuccessListener { senderDoc ->
                    val isVerified = senderDoc.getBoolean("blueBadge") == true || senderDoc.getBoolean("yellowBadge") == true
                    if (isVerified) {
                        proceedWithNotification()
                    }
                }
            } else {
                proceedWithNotification()
            }
        }
    }
}

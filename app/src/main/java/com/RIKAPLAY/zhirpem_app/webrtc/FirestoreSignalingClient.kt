package com.RIKAPLAY.zhirpem_app.webrtc

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

/** Определяет собеседника по идентификатору чата вида "userA_userB" (в именах допустим "_"). */
fun peerOfChat(chatId: String, me: String): String {
    return when {
        chatId.startsWith("${me}_") -> chatId.removePrefix("${me}_")
        chatId.endsWith("_$me") -> chatId.removeSuffix("_$me")
        else -> chatId.split("_").firstOrNull { it != me } ?: ""
    }
}

/**
 * Сигнализация через Firestore.
 *
 * Раньше отсеивание «старых» сообщений шло по времени на часах телефонов (timestamp < старт сессии).
 * Если часы двух устройств расходились, ответ на звонок просто выбрасывался. Теперь каждый звонок
 * получает уникальный [sessionId], и принимаются только сообщения этой сессии.
 */
class FirestoreSignalingClient(
    private val callId: String,
    private val currentUserId: String,
    private val sessionId: String? = null,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : SignalingClient {

    override var onOfferReceived: ((SessionDescription) -> Unit)? = null
    override var onAnswerReceived: ((SessionDescription) -> Unit)? = null
    override var onIceCandidateReceived: ((IceCandidate) -> Unit)? = null
    override var onCallEnded: (() -> Unit)? = null

    private var hasBeenCreated = false
    private var answerHandled = false
    private var offerHandled = false

    private var callListener: ListenerRegistration? = null
    private var candidatesListener: ListenerRegistration? = null

    init { listenForSignaling() }

    private fun sameSession(data: Map<*, *>?, key: String): Boolean {
        if (sessionId == null) return true
        return data?.get(key) == sessionId
    }

    private fun listenForSignaling() {
        callListener = db.collection("calls").document(callId).addSnapshotListener { snap, e ->
            if (e != null) {
                android.util.Log.e("Signaling", "Error listening to call doc: ${e.message}")
                return@addSnapshotListener
            }

            val exists = snap?.exists() == true
            if (!exists) {
                // Документ был и пропал: собеседник положил трубку (в том числе до ответа)
                if (hasBeenCreated) onCallEnded?.invoke()
                return@addSnapshotListener
            }

            val data = snap?.data ?: return@addSnapshotListener

            // Документ нашей сессии, либо чужой (старый) от прошлого звонка
            if (sameSession(data, "sessionId")) hasBeenCreated = true else return@addSnapshotListener

            if (!offerHandled && data.containsKey("offer")) {
                val map = data["offer"] as? Map<*, *>
                val sdp = map?.get("sdp") as? String
                if (map != null && sdp != null && map["from"] != currentUserId) {
                    offerHandled = true
                    onOfferReceived?.invoke(SessionDescription(SessionDescription.Type.OFFER, sdp))
                }
            }
            if (!answerHandled && data.containsKey("answer")) {
                val map = data["answer"] as? Map<*, *>
                val sdp = map?.get("sdp") as? String
                if (map != null && sdp != null && map["from"] != currentUserId) {
                    answerHandled = true // ответ применяем один раз, иначе WebRTC ругается на состояние
                    onAnswerReceived?.invoke(SessionDescription(SessionDescription.Type.ANSWER, sdp))
                }
            }
        }

        candidatesListener = db.collection("calls").document(callId).collection("candidates")
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) return@addSnapshotListener
                snap.documentChanges.filter { it.type == DocumentChange.Type.ADDED }.forEach { ch ->
                    val data = ch.document.data
                    if (data["from"] != currentUserId && sameSession(data, "session")) {
                        val sdp = data["sdp"] as? String ?: return@forEach
                        onIceCandidateReceived?.invoke(
                            IceCandidate(
                                data["sdpMid"] as? String ?: "",
                                (data["sdpMLineIndex"] as? Number)?.toInt() ?: 0,
                                sdp
                            )
                        )
                    }
                }
            }
    }

    override fun sendOffer(sdp: SessionDescription, callType: String) {
        val recipientId = com.RIKAPLAY.zhirpem_app.ChatPeerCache.peerOf(callId, currentUserId)
        val offer = mapOf(
            "sdp" to sdp.description,
            "from" to currentUserId,
            "to" to recipientId
        )
        db.collection("calls").document(callId).set(
            mapOf(
                "offer" to offer,
                "sessionId" to sessionId,
                "timestamp" to System.currentTimeMillis(),
                "serverTimestamp" to FieldValue.serverTimestamp(),
                "recipientId" to recipientId,
                "callType" to callType
            )
        )
    }

    override fun sendAnswer(sdp: SessionDescription) {
        val answer = mapOf("sdp" to sdp.description, "from" to currentUserId)
        db.collection("calls").document(callId).update(mapOf("answer" to answer))
    }

    override fun sendIceCandidate(candidate: IceCandidate) {
        val data = mapOf(
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "sdp" to candidate.sdp,
            "from" to currentUserId,
            "session" to sessionId
        )
        db.collection("calls").document(callId).collection("candidates").add(data)
    }

    /** Удаляет звонок вместе с кандидатами (подколлекция сама не удаляется). */
    override fun clear() {
        val callRef = db.collection("calls").document(callId)
        callRef.collection("candidates").get()
            .addOnSuccessListener { snap ->
                val batch = db.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit().addOnCompleteListener { callRef.delete() }
            }
            .addOnFailureListener { callRef.delete() }
    }

    override fun dispose() {
        callListener?.remove()
        candidatesListener?.remove()
        callListener = null
        candidatesListener = null
    }
}

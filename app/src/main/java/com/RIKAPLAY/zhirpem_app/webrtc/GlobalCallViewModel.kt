package com.RIKAPLAY.zhirpem_app.webrtc

import com.RIKAPLAY.zhirpem_app.AppVisibility
import com.RIKAPLAY.zhirpem_app.CallNotifications
import com.RIKAPLAY.zhirpem_app.ChatPeerCache

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.*

class GlobalCallViewModel(
    private val application: Application
) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private var signalingClient: SignalingClient? = null
    private var webRtcManager: WebRtcManager? = null

    private val _isCallActive = MutableStateFlow(false)
    val isCallActive = _isCallActive.asStateFlow()

    private val _showIncomingOverlay = MutableStateFlow(false)
    val showIncomingOverlay = _showIncomingOverlay.asStateFlow()

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId = _currentChatId.asStateFlow()

    private val _peerName = MutableStateFlow("Чат")
    val peerName = _peerName.asStateFlow()

    private val _peerAvatarUrl = MutableStateFlow<String?>(null)
    val peerAvatarUrl = _peerAvatarUrl.asStateFlow()

    private val _callType = MutableStateFlow("VIDEO") // "VIDEO" or "AUDIO"
    val callType = _callType.asStateFlow()

    private val _isRemoteSpeaking = MutableStateFlow(false)
    val isRemoteSpeaking = _isRemoteSpeaking.asStateFlow()

    private val _isLocalSpeaking = MutableStateFlow(false)
    val isLocalSpeaking = _isLocalSpeaking.asStateFlow()

    private val _connectionState = MutableStateFlow(PeerConnection.IceConnectionState.NEW)
    val connectionState = _connectionState.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack = _remoteVideoTrack.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack = _localVideoTrack.asStateFlow()

    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled = _isVideoEnabled.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera = _isFrontCamera.asStateFlow()

    private val _isAudioEnabled = MutableStateFlow(true)
    val isAudioEnabled = _isAudioEnabled.asStateFlow()

    private val _isSpeakerphoneEnabled = MutableStateFlow(true)
    val isSpeakerphoneEnabled = _isSpeakerphoneEnabled.asStateFlow()

    private val _isMinimized = MutableStateFlow(false)
    val isMinimized = _isMinimized.asStateFlow()

    private val _ping = MutableStateFlow(0)
    val ping = _ping.asStateFlow()

    private val _callEndReason = MutableStateFlow<String?>(null)
    val callEndReason = _callEndReason.asStateFlow()

    private val _callDuration = MutableStateFlow(0L)
    val callDuration = _callDuration.asStateFlow()

    private var durationJob: kotlinx.coroutines.Job? = null
    private var statsJob: kotlinx.coroutines.Job? = null
    private var pendingRemoteSdp: SessionDescription? = null
    private var myUsername: String? = null
    
    private var ringtonePlayer: android.media.MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private fun getVibrator(): Vibrator {
        if (vibrator != null) return vibrator!!
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            application.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        return vibrator!!
    }

    private fun startIncomingVibration() {
        val v = getVibrator()
        val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, 0)
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
    }

    fun triggerClickVibration() {
        val v = getVibrator()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(50)
        }
    }

    private fun playRingtone() {
        if (ringtonePlayer != null) return
        val prefs = application.getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)
        val selectedIndex = prefs.getInt("outgoing_call_ringtone", 0)
        
        val resId = if (selectedIndex == 0) {
            com.RIKAPLAY.zhirpem_app.R.raw.zhirpem_tune_1
        } else {
            com.RIKAPLAY.zhirpem_app.R.raw.zhirpem_tune_2
        }

        try {
            ringtonePlayer = android.media.MediaPlayer.create(application, resId)
            ringtonePlayer?.isLooping = true
            ringtonePlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRingtone() {
        try {
            ringtonePlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        ringtonePlayer = null
    }

    private fun playBeep() {
        try {
            val player = android.media.MediaPlayer.create(application, com.RIKAPLAY.zhirpem_app.R.raw.beep)
            player.setOnCompletionListener { it.release() }
            player.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            state?.let { 
                _connectionState.value = it 
                if (it == PeerConnection.IceConnectionState.CONNECTED) {
                    _callEndReason.value = null
                    stopRingtone()
                    startDurationTimer()
                } else if (it == PeerConnection.IceConnectionState.DISCONNECTED) {
                    _callEndReason.value = "Disconnected"
                } else if (it == PeerConnection.IceConnectionState.FAILED) {
                    _callEndReason.value = "Failed"
                }
            }
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let { signalingClient?.sendIceCandidate(it) }
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {
            stream?.videoTracks?.firstOrNull()?.let { _remoteVideoTrack.value = it }
        }
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(channel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
            receiver?.track()?.let { if (it is VideoTrack) _remoteVideoTrack.value = it }
        }
    }

    fun init(username: String) {
        if (myUsername == username) return
        myUsername = username
        listenForIncomingCalls()
    }

    private fun listenForIncomingCalls() {
        val username = myUsername ?: return
        db.collection("calls")
            .whereEqualTo("recipientId", username)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                
                // Берём самый свежий звонок. Время берём серверное (если уже проставлено),
                // чтобы не зависеть от часов телефона звонящего.
                fun callTime(d: com.google.firebase.firestore.DocumentSnapshot): Long =
                    d.getTimestamp("serverTimestamp")?.toDate()?.time ?: (d.getLong("timestamp") ?: 0L)

                val doc = snapshot.documents.maxByOrNull { callTime(it) }
                if (doc != null && !_isCallActive.value) {
                    val timestamp = callTime(doc)
                    // Реагируем только на свежие звонки (звонок ещё может звенеть)
                    if (System.currentTimeMillis() - timestamp < 45000) {
                        val chatId = doc.id
                        val offerMap = doc.get("offer") as? Map<*, *>
                        val sdp = offerMap?.get("sdp") as? String
                        val sessionId = doc.getString("sessionId")

                        if (sdp != null && offerMap?.get("from") != myUsername) {
                            startIncomingCall(chatId, sdp, sessionId)
                        }
                    }
                }
            }
    }

    private fun startIncomingCall(chatId: String, sdp: String, sessionId: String?) {
        _currentChatId.value = chatId
        val peerId = ChatPeerCache.peerOf(chatId, myUsername ?: "")
        db.collection("users").document(peerId).get().addOnSuccessListener {
            _peerName.value = it.getString("name") ?: peerId
            _peerAvatarUrl.value = it.getString("avatarUrl")
        }

        db.collection("calls").document(chatId).get().addOnSuccessListener { doc ->
            // Звонящий записан в самом звонке: так собеседник определяется и в чатах со случайным ID
            val caller = (doc.get("offer") as? Map<*, *>)?.get("from") as? String
            if (!caller.isNullOrBlank() && caller != peerId) {
                ChatPeerCache.rememberPeer(chatId, caller)
                db.collection("users").document(caller).get().addOnSuccessListener {
                    _peerName.value = it.getString("name") ?: caller
                    _peerAvatarUrl.value = it.getString("avatarUrl")
                }
            }
            _callType.value = doc.getString("callType") ?: "VIDEO"
            if (_callType.value == "AUDIO") {
                _isVideoEnabled.value = false
            }
        }

        signalingClient = FirestoreSignalingClient(chatId, myUsername!!, sessionId)
        webRtcManager = WebRtcManager(application, signalingClient!!)
        
        pendingRemoteSdp = SessionDescription(SessionDescription.Type.OFFER, sdp)
        _isCallActive.value = true
        _showIncomingOverlay.value = true
        if (AppVisibility.isForeground) {
            playRingtone() // Приложение открыто: звоним сами
            startIncomingVibration()
        } else {
            // Приложение в фоне: звонит уведомление с кнопками «Ответить» и «Сбросить»
            // (если такое же уже пришло по push, оно просто обновится без повторного звонка)
            db.collection("users").document(peerId).get().addOnSuccessListener { userDoc ->
                db.collection("calls").document(chatId).get().addOnSuccessListener { callDoc ->
                    val type = callDoc.getString("callType") ?: "VIDEO"
                    val name = userDoc.getString("name") ?: peerId
                    val avatar = userDoc.getString("avatarUrl")
                    Thread {
                        CallNotifications.showIncomingCall(application, chatId, type, name, avatar)
                    }.start()
                }
            }
        }
        
        // Setup other signaling listeners (answer, candidates) via SignalingClient
        signalingClient?.onAnswerReceived = { webRtcManager?.onRemoteSessionDescription(it) }
        signalingClient?.onIceCandidateReceived = { webRtcManager?.addIceCandidate(it) }
        signalingClient?.onCallEnded = { hangup() }
    }

    fun startOutgoingCall(chatId: String, callType: String = "VIDEO") {
        _currentChatId.value = chatId
        _callType.value = callType
        _isVideoEnabled.value = callType == "VIDEO"
        
        val peerId = ChatPeerCache.peerOf(chatId, myUsername ?: "")
        db.collection("users").document(peerId).get().addOnSuccessListener {
            _peerName.value = it.getString("name") ?: peerId
            _peerAvatarUrl.value = it.getString("avatarUrl")
        }

        _isCallActive.value = true
        _showIncomingOverlay.value = false
        
        // У каждого звонка свой идентификатор сессии: по нему отсеиваются остатки прошлых звонков
        val sessionId = java.util.UUID.randomUUID().toString()
        signalingClient = FirestoreSignalingClient(chatId, myUsername!!, sessionId)
        webRtcManager = WebRtcManager(application, signalingClient!!)
        
        // Микрофон нужен и в аудиозвонке (раньше дорожка создавалась только для видео)
        webRtcManager?.startLocalCapture(withVideo = callType == "VIDEO")
        _localVideoTrack.value = webRtcManager?.getLocalVideoTrack()
        
        webRtcManager?.createPeerConnection(observer)
        
        setupAudioOutput()
        
        webRtcManager?.call(callType)

        signalingClient?.onAnswerReceived = { webRtcManager?.onRemoteSessionDescription(it) }
        signalingClient?.onIceCandidateReceived = { webRtcManager?.addIceCandidate(it) }
        signalingClient?.onCallEnded = { hangup() }
    }

    private fun startDurationTimer() {
        if (durationJob != null) return
        durationJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (true) {
                _callDuration.value = (System.currentTimeMillis() - startTime) / 1000
                
                // Симуляция индикации голоса (если микрофон включен)
                if (_isAudioEnabled.value) {
                    _isLocalSpeaking.value = (Math.random() > 0.7)
                } else {
                    _isLocalSpeaking.value = false
                }
                
                // Удаленный голос тоже симулируем для демо, 
                // в реальном приложении это берется из AudioTrack или статистики
                _isRemoteSpeaking.value = (Math.random() > 0.8)

                kotlinx.coroutines.delay(1000)
            }
        }
        startStatsPolling()
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            while (true) {
                webRtcManager?.getStats { reports ->
                    reports.statsMap.values.forEach { stats ->
                        if (stats.type == "candidate-pair" && stats.members["state"] == "succeeded") {
                            val rtt = stats.members["currentRoundTripTime"]
                            if (rtt is Double) {
                                _ping.value = (rtt * 1000).toInt()
                            }
                        }
                    }
                }
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    fun acceptCall() {
        _currentChatId.value?.let { CallNotifications.cancelIncomingCall(application, it) }
        triggerClickVibration()
        _showIncomingOverlay.value = false
        stopRingtone()
        stopVibration()
        
        // Авто-настройка звука при ответе
        setupAudioOutput()
        
        webRtcManager?.startLocalCapture(withVideo = _callType.value == "VIDEO")
        _localVideoTrack.value = webRtcManager?.getLocalVideoTrack()
        webRtcManager?.createPeerConnection(observer)
        pendingRemoteSdp?.let {
            webRtcManager?.onRemoteSessionDescription(it)
            webRtcManager?.answer()
        }
        pendingRemoteSdp = null
    }

    fun rejectCall() {
        triggerClickVibration()
        hangup()
    }

    fun toggleMinimize() {
        _isMinimized.value = !_isMinimized.value
    }

    fun hangup() {
        _currentChatId.value?.let { CallNotifications.cancelIncomingCall(application, it) }
        triggerClickVibration()
        if (_isCallActive.value) {
            _callEndReason.value = "Call ended"
            playBeep()
        }
        stopRingtone()
        stopVibration()
        _isCallActive.value = false
        _showIncomingOverlay.value = false
        _isMinimized.value = false
        durationJob?.cancel()
        durationJob = null
        statsJob?.cancel()
        statsJob = null
        _callDuration.value = 0
        _ping.value = 0
        webRtcManager?.dispose()
        signalingClient?.dispose()
        signalingClient?.clear()
        webRtcManager = null
        signalingClient = null
        _remoteVideoTrack.value = null
        _localVideoTrack.value = null
        _currentChatId.value = null
        _connectionState.value = PeerConnection.IceConnectionState.NEW

        // Возвращаем обычный режим звука (раньше после звонка оставался режим связи)
        try {
            val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = android.media.AudioManager.MODE_NORMAL
        } catch (_: Exception) {}

        // Clear reason after delay
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _callEndReason.value = null
        }
    }

    fun initLocalSurface(renderer: SurfaceViewRenderer) {
        webRtcManager?.initLocalSurface(renderer)
        webRtcManager?.startLocalCapture(withVideo = _callType.value == "VIDEO")
        _localVideoTrack.value = webRtcManager?.getLocalVideoTrack()
        webRtcManager?.addLocalSink(renderer)
    }

    fun initRemoteSurface(renderer: SurfaceViewRenderer) {
        webRtcManager?.initRemoteSurface(renderer)
        webRtcManager?.addRemoteSink(renderer)
    }

    fun toggleVideo() {
        _isVideoEnabled.value = !_isVideoEnabled.value
        webRtcManager?.toggleVideo(_isVideoEnabled.value)
    }

    fun flipCamera() {
        webRtcManager?.flipCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                _isFrontCamera.value = isFront
            }
            override fun onCameraSwitchError(p0: String?) {}
        })
    }

    fun toggleSpeakerphone() {
        val audioManager = application.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        
        // Проверяем наличие слухового динамика (earpiece)
        val hasEarpiece = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any { 
                it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE 
            }
        } else {
            application.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY)
        }

        if (!hasEarpiece) {
            // Если динамика нет (планшет), всегда включаем основной
            _isSpeakerphoneEnabled.value = true
            audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
            return
        }

        _isSpeakerphoneEnabled.value = !_isSpeakerphoneEnabled.value
        
        audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = _isSpeakerphoneEnabled.value
    }

    fun toggleAudio() {
        _isAudioEnabled.value = !_isAudioEnabled.value
        webRtcManager?.toggleAudio(_isAudioEnabled.value)
    }

    private fun setupAudioOutput() {
        val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val hasEarpiece = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any { 
            it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE 
        }
        
        if (!hasEarpiece) {
            _isSpeakerphoneEnabled.value = true
        }
        
        audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = _isSpeakerphoneEnabled.value
    }

    override fun onCleared() {
        super.onCleared()
        stopRingtone()
        signalingClient?.dispose()
        webRtcManager?.release()
    }
}

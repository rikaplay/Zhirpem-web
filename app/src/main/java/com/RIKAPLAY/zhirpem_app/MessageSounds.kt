package com.RIKAPLAY.zhirpem_app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

/**
 * Звуки сообщений. Файлы лежат в app/src/main/res/raw/:
 *  - msg_send.wav     отправка сообщения
 *  - msg_receive.wav  новое входящее сообщение в открытом чате
 *  - rec_start.wav    начало записи голосового или видео
 *
 * Сейчас это простые синтезированные «заглушки». Чтобы поставить свои звуки, замените файлы
 * с теми же именами (wav, mp3 или ogg, лучше короче секунды), код менять не нужно.
 * Отключить звуки можно значением false в SharedPreferences "user_session" по ключу "message_sounds_enabled".
 */
object MessageSounds {
    enum class Sound { SEND, RECEIVE, RECORD_START }

    private var pool: SoundPool? = null
    private val ids = mutableMapOf<Sound, Int>()
    private val loaded = mutableSetOf<Int>()

    private fun ensureLoaded(context: Context) {
        if (pool != null) return
        val soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded.add(sampleId)
        }
        val app = context.applicationContext
        ids[Sound.SEND] = soundPool.load(app, R.raw.msg_send, 1)
        ids[Sound.RECEIVE] = soundPool.load(app, R.raw.msg_receive, 1)
        ids[Sound.RECORD_START] = soundPool.load(app, R.raw.rec_start, 1)
        pool = soundPool
    }

    fun play(context: Context, sound: Sound) {
        try {
            val prefs = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("message_sounds_enabled", true)) return

            // В беззвучном режиме и режиме вибрации звуки не проигрываем
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

            ensureLoaded(context)
            val id = ids[sound] ?: return
            if (id in loaded) {
                pool?.play(id, 0.7f, 0.7f, 1, 0, 1f)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

package com.RIKAPLAY.zhirpem_app.platform

import android.content.Context
import android.media.MediaPlayer
import com.RIKAPLAY.zhirpem_app.R

private var androidContext: Context? = null

fun initSoundPlayer(context: Context) {
    androidContext = context
}

actual object SoundPlayer {
    private var mediaPlayer: MediaPlayer? = null

    actual fun playSplashSound() {
        val context = androidContext ?: return
        mediaPlayer?.stop()
        mediaPlayer?.release()
        
        try {
            mediaPlayer = MediaPlayer.create(context, R.raw.splash_sound)
            mediaPlayer?.setOnCompletionListener {
                release()
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual fun release() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

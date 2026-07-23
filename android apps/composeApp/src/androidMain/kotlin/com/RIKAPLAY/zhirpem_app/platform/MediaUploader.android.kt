package com.RIKAPLAY.zhirpem_app.platform

import android.content.Context
import android.net.Uri
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private var androidContext: Context? = null

fun initMediaUploader(context: Context) {
    androidContext = context
}

class AndroidMediaUploader : MediaUploader {
    override suspend fun upload(
        filePath: String,
        resourceType: String,
        onProgress: (Float) -> Unit
    ): String? = suspendCancellableCoroutine { continuation ->
        val uri = Uri.parse(filePath)
        
        MediaManager.get().upload(uri)
            .unsigned("mediapres")
            .option("resource_type", resourceType)
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {}
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                    onProgress(bytes.toFloat() / totalBytes)
                }
                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val secureUrl = resultData["secure_url"] as? String
                    continuation.resume(secureUrl)
                }
                override fun onError(requestId: String, error: ErrorInfo) {
                    continuation.resume(null)
                }
                override fun onReschedule(requestId: String, error: ErrorInfo) {}
            })
            .dispatch(androidContext!!)
    }
}

actual fun getMediaUploader(): MediaUploader = AndroidMediaUploader()

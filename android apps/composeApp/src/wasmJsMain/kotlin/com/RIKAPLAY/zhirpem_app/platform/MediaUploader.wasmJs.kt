package com.RIKAPLAY.zhirpem_app.platform

class WebMediaUploader : MediaUploader {
    override suspend fun upload(
        filePath: String,
        resourceType: String,
        onProgress: (Float) -> Unit
    ): String? {
        // Implementation for Web using fetch and Cloudinary API
        return null
    }
}

actual fun getMediaUploader(): MediaUploader = WebMediaUploader()

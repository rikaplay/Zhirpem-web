package com.RIKAPLAY.zhirpem_app.platform

interface MediaUploader {
    suspend fun upload(
        filePath: String,
        resourceType: String, // "image", "video"
        onProgress: (Float) -> Unit = {}
    ): String? // Returns secure URL
}

expect fun getMediaUploader(): MediaUploader

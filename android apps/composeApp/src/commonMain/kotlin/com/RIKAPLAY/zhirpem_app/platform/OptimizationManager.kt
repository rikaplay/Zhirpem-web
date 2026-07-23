package com.RIKAPLAY.zhirpem_app.platform

expect object OptimizationManager {
    fun getCacheSize(): Long
    fun getCacheSizeFormatted(): String
    fun clearAppCache(): String
    fun formatSize(size: Long): String
}

package com.RIKAPLAY.zhirpem_app.platform

import android.content.Context
import java.io.File

private var androidContext: Context? = null

fun initOptimizationManager(context: Context) {
    androidContext = context
}

actual object OptimizationManager {

    actual fun getCacheSize(): Long {
        return androidContext?.let { getFolderSize(it.cacheDir) } ?: 0L
    }

    actual fun getCacheSizeFormatted(): String {
        return formatSize(getCacheSize())
    }

    actual fun clearAppCache(): String {
        androidContext?.let { deleteRecursive(it.cacheDir) }
        return getCacheSizeFormatted()
    }

    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        if (file.isDirectory) {
            file.listFiles()?.forEach {
                size += getFolderSize(it)
            }
        } else {
            size = file.length()
        }
        return size
    }

    private fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach {
                deleteRecursive(it)
            }
        }
        if (fileOrDirectory.name != "cache") {
            fileOrDirectory.delete()
        }
    }

    actual fun formatSize(size: Long): String {
        if (size <= 0) return "0 Б"
        val units = listOf("Б", "КБ", "МБ", "ГБ")
        var s = size.toDouble()
        var unitIndex = 0
        while (s >= 1024 && unitIndex < units.size - 1) {
            s /= 1024
            unitIndex++
        }
        return "%.2f %s".format(s, units[unitIndex])
    }
}

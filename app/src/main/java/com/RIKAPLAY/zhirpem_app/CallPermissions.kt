package com.RIKAPLAY.zhirpem_app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Разрешения, нужные для звонка. Микрофон обязателен, камера только для видео. */
fun callPermissionsFor(callType: String): Array<String> =
    if (callType == "VIDEO") arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
    else arrayOf(Manifest.permission.RECORD_AUDIO)

/** Звонок можно начинать, когда выдан микрофон (без камеры видеозвонок идёт как аудио). */
fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** Выданы ли все разрешения для данного типа звонка (чтобы не показывать запрос зря). */
fun hasAllCallPermissions(context: Context, callType: String): Boolean =
    callPermissionsFor(callType).all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

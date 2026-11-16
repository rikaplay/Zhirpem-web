package com.RIKAPLAY.zhirpem_app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Идёт ли сейчас запись голосового или видеосообщения (или палец лежит на кнопке записи).
 * Пока это так, жест открытия боковой панели отключён: раньше он перехватывал касание,
 * и запись сбрасывалась.
 */
object RecordingState {
    var isActive by mutableStateOf(false)
}

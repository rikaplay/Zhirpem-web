package com.RIKAPLAY.zhirpem_app.platform

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp

actual fun Modifier.platformBlur(radius: Dp): Modifier = this.blur(radius)

package com.RIKAPLAY.zhirpem_app.platform

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

actual fun Modifier.platformBlur(radius: Dp): Modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    this.graphicsLayer {
        renderEffect = android.graphics.RenderEffect
            .createBlurEffect(radius.value * 2, radius.value * 2, android.graphics.Shader.TileMode.DECAL)
            .asComposeRenderEffect()
    }
} else {
    this.blur(radius)
}

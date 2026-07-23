package com.RIKAPLAY.zhirpem_app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.RIKAPLAY.zhirpem_app.platform.CameraView

@Composable
fun MediaPicker(
    onMediaSelected: (String, Boolean) -> Unit,
    onClose: () -> Unit
) {
    CameraView(
        modifier = Modifier,
        onMediaCaptured = onMediaSelected,
        onClose = onClose
    )
}

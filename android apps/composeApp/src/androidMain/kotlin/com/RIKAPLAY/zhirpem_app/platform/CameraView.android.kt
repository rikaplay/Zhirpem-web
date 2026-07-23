package com.RIKAPLAY.zhirpem_app.platform

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.RIKAPLAY.zhirpem_app.CameraPermissionWrapper
import com.RIKAPLAY.zhirpem_app.CameraScreen

@Composable
actual fun CameraView(
    modifier: Modifier,
    onMediaCaptured: (String, Boolean) -> Unit,
    onClose: () -> Unit
) {
    CameraPermissionWrapper(
        onPermissionGranted = {
            CameraScreen(
                onMediaSelected = { uri, isVideo ->
                    onMediaCaptured(uri.toString(), isVideo)
                },
                onClose = onClose
            )
        },
        onClose = onClose
    )
}

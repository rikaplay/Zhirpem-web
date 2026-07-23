package com.RIKAPLAY.zhirpem_app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun CameraView(
    modifier: Modifier,
    onMediaCaptured: (String, Boolean) -> Unit, // URL/Path, isVideo
    onClose: () -> Unit
)

package com.RIKAPLAY.zhirpem_app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun GifPlayer(
    gifUrl: String,
    modifier: Modifier = Modifier
)

@Composable
expect fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
)

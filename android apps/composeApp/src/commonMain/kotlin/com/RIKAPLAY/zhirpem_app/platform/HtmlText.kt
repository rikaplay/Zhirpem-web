package com.RIKAPLAY.zhirpem_app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun HtmlText(
    html: String,
    modifier: Modifier = Modifier
)

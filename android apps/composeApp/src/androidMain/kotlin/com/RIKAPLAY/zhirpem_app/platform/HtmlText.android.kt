package com.RIKAPLAY.zhirpem_app.platform

import android.text.Html
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun HtmlText(
    html: String,
    modifier: Modifier
) {
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                textSize = 14f
            }
        },
        update = { textView ->
            textView.text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
        },
        modifier = modifier
    )
}

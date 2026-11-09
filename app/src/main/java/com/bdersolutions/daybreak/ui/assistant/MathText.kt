package com.bdersolutions.daybreak.ui.assistant

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** True when a text holds a formula the model wrote in LaTeX. */
internal fun hasMath(text: String): Boolean =
    text.contains("$") || text.contains("\\(") || text.contains("\\[")

/**
 * An answer with formulas: the text is laid out as simple HTML and KaTeX (bundled in assets/katex, so it
 * works offline) draws the `$…$` and `$$…$$` parts. The page has no network access and gets only the
 * escaped answer text. Its height comes back from the page, so the bubble fits the content.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun MathText(text: String, color: Color, modifier: Modifier = Modifier) {
    val html = remember(text, color) { MathHtml.page(text, color.toArgb()) }
    var heightDp by remember(html) { mutableFloatStateOf(0f) }
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(if (heightDp > 0f) heightDp.dp else 24.dp),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                settings.blockNetworkLoads = true
                settings.allowContentAccess = false
                settings.allowFileAccess = false
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun height(cssPixels: Float) {
                            post { heightDp = cssPixels }
                        }
                    },
                    "Daybreak",
                )
            }
        },
        update = { view ->
            if (view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL("file:///android_asset/katex/", html, "text/html", "utf-8", null)
            }
        },
    )
}

/** Builds the page for [MathText]. Pure, so the escaping can be checked. */
internal object MathHtml {
    fun page(text: String, argb: Int): String {
        val color = String.format("#%06X", argb and 0xFFFFFF)
        return """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="katex.min.css">
<style>
html,body{margin:0;padding:0;background:transparent;color:$color;font:16px/1.45 sans-serif;word-wrap:break-word}
p{margin:0 0 6px 0}p:last-child{margin-bottom:0}
.katex-display{margin:6px 0;overflow-x:auto;overflow-y:hidden}
</style></head><body>${body(text)}
<script src="katex.min.js"></script><script src="auto-render.min.js"></script>
<script>
function report(){Daybreak.height(document.documentElement.scrollHeight)}
renderMathInElement(document.body,{delimiters:[{left:"$$",right:"$$",display:true},{left:"\\[",right:"\\]",display:true},{left:"$",right:"$",display:false},{left:"\\(",right:"\\)",display:false}],throwOnError:false});
document.querySelectorAll(".katex-display").forEach(function(d){var k=d.firstElementChild;if(k&&k.scrollWidth>d.clientWidth){k.style.fontSize=(d.clientWidth/k.scrollWidth*100).toFixed(1)+"%"}});
report();window.addEventListener("load",report);
if(window.ResizeObserver){new ResizeObserver(report).observe(document.body)}
</script></body></html>"""
    }

    /** Escaped text in paragraphs; **bold** becomes bold. Formulas stay as text for KaTeX to find. */
    fun body(text: String): String = text.trim().split(Regex("\n{2,}")).joinToString("") { paragraph ->
        val escaped = paragraph
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
            .replace("\n", "<br>")
        "<p>$escaped</p>"
    }
}

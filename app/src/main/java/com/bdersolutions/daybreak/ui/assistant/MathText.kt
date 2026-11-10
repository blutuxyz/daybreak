package com.bdersolutions.daybreak.ui.assistant

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.content.Intent
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bdersolutions.daybreak.data.assistant.AnswerMarkdown

/** The colours of the formula page, taken from the app's theme. */
internal data class MathPalette(val text: Color, val accent: Color, val muted: Color, val codeBackground: Color, val divider: Color)

/**
 * An answer with formulas: the same Markdown blocks the plain answer is drawn from are laid out as HTML,
 * and KaTeX (bundled in assets/katex, so it works offline) draws each formula the parser found. KaTeX is
 * handed the formulas one by one rather than searching the text for dollar signs, so what counts as a
 * formula is decided in one place. The page has no network access and gets only escaped text; a tapped
 * link opens in the browser. Its height comes back from the page, so the bubble fits the content.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun MathText(blocks: List<AnswerMarkdown.Block>, palette: MathPalette, modifier: Modifier = Modifier, placeholder: @Composable () -> Unit = {}) {
    val html = remember(blocks, palette) { MathHtml.page(blocks, palette) }
    // The page reports its height a moment after loading. Until then the plain text is shown in its place
    // (and gives the page its width), so the bubble does not collapse and then jump when an answer ends.
    // A height seen before is reused, so scrolling the bubble out and back in does not jump either.
    var heightDp by remember(html) { mutableFloatStateOf(MathHeights[html] ?: 0f) }
    var drawn by remember(html) { mutableStateOf(false) }
    // The page's bridge is made once with the view; it must reach the current text's state, not the first one.
    val onHeight by rememberUpdatedState { cssPixels: Float ->
        MathHeights[html] = cssPixels
        heightDp = cssPixels
        drawn = true
    }
    Box(modifier = modifier.fillMaxWidth()) {
        if (!drawn) placeholder()
        AndroidView(
            modifier = if (heightDp > 0f) Modifier.fillMaxWidth().height(heightDp.dp) else Modifier.matchParentSize(),
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    settings.javaScriptEnabled = true
                    settings.blockNetworkLoads = true
                    settings.allowContentAccess = false
                    settings.allowFileAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url
                            if (url.scheme == "https" || url.scheme == "http") {
                                runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                            return true
                        }
                    }
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun height(cssPixels: Float) {
                                post { if (cssPixels > 0f) onHeight(cssPixels) }
                            }
                        },
                        "Daybreak",
                    )
                }
            },
            update = { view ->
                view.alpha = if (drawn) 1f else 0f
                if (view.tag != html) {
                    view.tag = html
                    view.loadDataWithBaseURL("file:///android_asset/katex/", html, "text/html", "utf-8", null)
                }
            },
        )
    }
}

/** Heights of drawn answers by page, so a bubble that comes back into view has its size at once. */
private object MathHeights {
    private val heights = object : LinkedHashMap<String, Float>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?): Boolean = size > 64
    }

    @Synchronized
    operator fun get(html: String): Float? = heights[html]

    @Synchronized
    operator fun set(html: String, height: Float) {
        heights[html] = height
    }
}

/** Builds the page for [MathText]. Pure, so the escaping can be checked. */
internal object MathHtml {
    fun page(blocks: List<AnswerMarkdown.Block>, palette: MathPalette): String {
        fun css(color: Color) = String.format("#%06X", color.toArgb() and 0xFFFFFF)
        return """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="katex.min.css">
<style>
html,body{margin:0;padding:0;background:transparent;color:${css(palette.text)};font:16px/1.45 sans-serif;overflow-wrap:anywhere}
body>*{margin:0 0 8px 0}body>*:last-child{margin-bottom:0}
h1,h2,h3,h4,h5,h6{font-size:17px;font-weight:600;margin-top:4px}h3,h4,h5,h6{font-size:15px}
ul{list-style:none;padding:0}li{position:relative;margin:0 0 4px 0}
li .m{position:absolute;left:0;color:${css(palette.accent)};font-weight:600}
code,pre{font-family:monospace;font-size:13px;background:${css(palette.codeBackground)};border-radius:6px}
code{padding:0 3px}pre{padding:10px 12px;overflow-x:auto;white-space:pre}
blockquote{border-left:3px solid ${css(palette.accent)};padding-left:10px;color:${css(palette.muted)}}
a{color:${css(palette.accent)}}hr{border:0;border-top:1px solid ${css(palette.divider)}}
.t{overflow-x:auto}table{border-collapse:collapse;font-size:14px}th,td{padding:5px 10px;text-align:left;white-space:nowrap}
th{background:${css(palette.codeBackground)};font-weight:600}tr+tr td{border-top:1px solid ${css(palette.divider)}}
.d{overflow-x:auto;overflow-y:hidden}.katex-display{margin:4px 0}
</style></head><body>${body(blocks)}
<script src="katex.min.js"></script>
<script>
function report(){Daybreak.height(Math.ceil(document.body.getBoundingClientRect().height))}
document.querySelectorAll(".tex,.d").forEach(function(e){try{katex.render(e.textContent,e,{displayMode:e.classList.contains("d"),throwOnError:false})}catch(x){}});
document.querySelectorAll(".d").forEach(function(d){var k=d.querySelector(".katex-display>.katex");if(k&&k.scrollWidth>d.clientWidth){k.style.fontSize=(d.clientWidth/k.scrollWidth*100).toFixed(1)+"%"}});
report();window.addEventListener("load",report);
if(window.ResizeObserver){new ResizeObserver(report).observe(document.body)}
</script></body></html>"""
    }

    fun body(blocks: List<AnswerMarkdown.Block>): String = buildString {
        for (block in blocks) {
            when (block) {
                is AnswerMarkdown.Block.Paragraph -> append("<p>").append(inlines(block.text)).append("</p>")
                is AnswerMarkdown.Block.Heading -> append("<h${block.level}>").append(inlines(block.text)).append("</h${block.level}>")
                is AnswerMarkdown.Block.Items -> {
                    append("<ul>")
                    block.items.forEach { item ->
                        val marker = item.number?.let { "$it." } ?: if (item.depth == 0) "•" else "◦"
                        val indent = item.depth * 16
                        val gap = if (item.number != null) 24 else 16
                        append("<li style=\"padding-left:${indent + gap}px\"><span class=\"m\" style=\"left:${indent}px\">")
                            .append(marker).append("</span>").append(inlines(item.text)).append("</li>")
                    }
                    append("</ul>")
                }
                is AnswerMarkdown.Block.Quote -> append("<blockquote>").append(inlines(block.text)).append("</blockquote>")
                is AnswerMarkdown.Block.Code -> append("<pre>").append(escape(block.code)).append("</pre>")
                is AnswerMarkdown.Block.Math -> append("<div class=\"d\">").append(escape(block.tex)).append("</div>")
                is AnswerMarkdown.Block.Table -> {
                    append("<div class=\"t\"><table><tr>")
                    block.header.forEach { append("<th>").append(inlines(it)).append("</th>") }
                    append("</tr>")
                    block.rows.forEach { row ->
                        append("<tr>")
                        row.forEach { append("<td>").append(inlines(it)).append("</td>") }
                        append("</tr>")
                    }
                    append("</table></div>")
                }
                AnswerMarkdown.Block.Rule -> append("<hr>")
            }
        }
    }

    private fun inlines(parts: List<AnswerMarkdown.Inline>): String = buildString {
        for (part in parts) {
            when (part) {
                is AnswerMarkdown.Inline.Text -> {
                    var text = escape(part.text).replace("\n", "<br>")
                    if (part.italic) text = "<i>$text</i>"
                    if (part.bold) text = "<b>$text</b>"
                    append(text)
                }
                is AnswerMarkdown.Inline.Code -> append("<code>").append(escape(part.code)).append("</code>")
                is AnswerMarkdown.Inline.Math -> append("<span class=\"tex\">").append(escape(part.tex)).append("</span>")
                is AnswerMarkdown.Inline.Link -> append("<a href=\"").append(escape(part.url)).append("\">").append(escape(part.label)).append("</a>")
            }
        }
    }

    fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

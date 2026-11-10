package com.bdersolutions.daybreak.ui.assistant

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
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
import kotlin.math.roundToInt

/** The colours of the formula page, taken from the app's theme. */
internal data class MathPalette(val text: Color, val accent: Color, val muted: Color, val codeBackground: Color, val divider: Color)

/**
 * An answer with formulas. The Markdown blocks are laid out as HTML, and KaTeX (bundled in assets/katex, so it
 * works offline) draws each formula the parser found. The page is loaded once per colour scheme; every new text
 * (a streaming answer grows) is pushed into it, so the page is not reloaded and complete formulas show while the
 * rest of the answer is still arriving. A formula whose delimiters have not closed yet is plain text until they do.
 * The page has no network access and gets only escaped text; a tapped link opens in the browser. Its height comes
 * back from the page, so the answer fits its content.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun MathText(
    blocks: List<AnswerMarkdown.Block>,
    palette: MathPalette,
    modifier: Modifier = Modifier,
    fresh: FreshText? = null,
    placeholder: @Composable () -> Unit = {},
) {
    val html = remember(palette) { MathHtml.page(palette) }
    val plain = remember(blocks) { MathHtml.body(blocks) }
    // While an answer streams, its newest characters fade in the page too. The key is the fading runs at tenths of
    // opacity, so the page is only pushed again when a run visibly changes.
    val fadeKey = fresh?.segments?.joinToString { "${it.fromEnd}/${it.toEnd}/${(it.alpha * 10).roundToInt()}" }.orEmpty()
    val body = remember(plain, fadeKey) { if (fresh == null || fadeKey.isEmpty()) plain else MathHtml.fade(plain, fresh) }
    // Until the page reports its height the plain text is shown in its place (and gives the page its width), so the
    // answer does not collapse and then jump. The height is kept while the text grows; a height seen before for
    // this text is reused when the bubble comes back into view.
    var heightDp by remember { mutableFloatStateOf(MathHeights[body] ?: 0f) }
    var drawn by remember { mutableStateOf(heightDp > 0f) }
    // The page's bridge is made once with the view; it reads the current text through rememberUpdatedState.
    val onHeight by rememberUpdatedState { cssPixels: Float, text: String ->
        MathHeights[text] = cssPixels
        if (text == body) {
            heightDp = cssPixels
            drawn = true
        }
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
                    tag = PageState()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url
                            if (url.scheme == "https" || url.scheme == "http") {
                                runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                            return true
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            val state = view.tag as PageState
                            state.ready = true
                            state.pushed = null
                            pushBody(view, state)
                        }
                    }
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun height(cssPixels: Float, text: String) {
                                post { if (cssPixels > 0f) onHeight(cssPixels, text) }
                            }
                        },
                        "Daybreak",
                    )
                }
            },
            update = { view ->
                view.alpha = if (drawn) 1f else 0f
                val state = view.tag as PageState
                if (state.html != html) {
                    state.html = html
                    state.ready = false
                    state.pushed = null
                    view.loadDataWithBaseURL("file:///android_asset/katex/", html, "text/html", "utf-8", null)
                }
                state.body = body
                pushBody(view, state)
            },
        )
    }
}

/** What the page holds: the page itself, whether it has loaded, the text last pushed into it, and the text wanted. */
private class PageState {
    var html: String? = null
    var ready = false
    var pushed: String? = null
    var body: String = ""
}

/** Pushes the wanted text into a loaded page, once per change. */
private fun pushBody(view: WebView, state: PageState) {
    if (!state.ready || state.pushed == state.body) return
    state.pushed = state.body
    view.evaluateJavascript(MathHtml.setBodyCall(state.body), null)
}

/** Heights of drawn answers by text, so a bubble that comes back into view has its size at once. */
private object MathHeights {
    private val heights = object : LinkedHashMap<String, Float>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?): Boolean = size > 64
    }

    @Synchronized
    operator fun get(text: String): Float? = heights[text]

    @Synchronized
    operator fun set(text: String, height: Float) {
        heights[text] = height
    }
}

/** Builds the page for [MathText] and the calls that update it. Pure, so the escaping can be checked. */
internal object MathHtml {
    fun page(palette: MathPalette): String {
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
</style>
<script src="katex.min.js"></script>
<script>
var current="";
function report(){Daybreak.height(Math.ceil(document.body.getBoundingClientRect().height),current)}
function draw(){
document.querySelectorAll(".tex,.d").forEach(function(e){if(e.getAttribute("data-k"))return;try{katex.render(e.textContent,e,{displayMode:e.classList.contains("d"),throwOnError:false});e.setAttribute("data-k","1")}catch(x){}});
document.querySelectorAll(".d").forEach(function(d){var k=d.querySelector(".katex-display>.katex");if(k&&k.scrollWidth>d.clientWidth){k.style.fontSize=(d.clientWidth/k.scrollWidth*100).toFixed(1)+"%"}});
}
function setBody(html){
current=html;document.body.innerHTML=html;draw();report();
}
if(window.ResizeObserver){new ResizeObserver(function(){report()}).observe(document.body)}
</script></head><body></body></html>"""
    }

    /** The call that puts [body] into a loaded page. */
    fun setBodyCall(body: String): String = "setBody(${jsString(body)})"

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

    /**
     * Fades the newest characters of an answer inside the page, as the plain answer fades them: a visible character
     * whose distance from the end falls in a fading run gets that run's opacity. Tags are copied as they are, and
     * an entity counts as one character, as it does on the screen.
     */
    fun fade(html: String, fresh: FreshText): String {
        val tokens = ArrayList<Pair<String, Boolean>>()
        var i = 0
        while (i < html.length) {
            val c = html[i]
            if (c == '<') {
                val close = html.indexOf('>', i).let { if (it < 0) html.length - 1 else it }
                tokens += html.substring(i, close + 1) to false
                i = close + 1
            } else {
                val semicolon = if (c == '&') html.indexOf(';', i) else -1
                val stop = if (semicolon in (i + 2)..(i + 7)) semicolon + 1 else i + 1
                tokens += html.substring(i, stop) to true
                i = stop
            }
        }
        val total = tokens.count { it.second }
        val out = StringBuilder(html.length + 64)
        var seen = 0
        for ((text, visible) in tokens) {
            if (!visible) {
                out.append(text)
                continue
            }
            seen++
            val alpha = fresh.alphaAt(total - seen + 1)
            if (alpha < 1f) out.append("<span style=\"opacity:").append(alpha).append("\">").append(text).append("</span>")
            else out.append(text)
        }
        return out.toString()
    }

    fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** A JavaScript string literal for [text], safe inside a script call. */
    fun jsString(text: String): String = buildString {
        append('"')
        for (c in text) {
            when {
                c == '\\' -> append("\\\\")
                c == '"' -> append("\\\"")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c == '<' -> append("\\u003c")
                c == ' ' -> append("\\u2028")
                c == ' ' -> append("\\u2029")
                c < ' ' -> append(String.format("\\u%04x", c.code))
                else -> append(c)
            }
        }
        append('"')
    }
}

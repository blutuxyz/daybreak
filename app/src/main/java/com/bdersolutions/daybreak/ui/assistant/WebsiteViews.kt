package com.bdersolutions.daybreak.ui.assistant

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.assistant.CodeHighlighter
import com.bdersolutions.daybreak.data.assistant.PartialJson
import java.io.ByteArrayInputStream

/**
 * Where a website comes from when the student opens it: a finished one (its title and page), or the call that
 * makes it, which is live while the model writes it and a card once it is done.
 */
sealed interface WebsiteSource {
    data class Call(val callId: String) : WebsiteSource
    data class Static(val title: String, val html: String) : WebsiteSource
}

/** Opens a website's screen. Provided at the top of the assistant, so any code block or card can use it. */
val LocalOpenWebsite = compositionLocalOf<(WebsiteSource) -> Unit> { {} }

/** Whether the server allows live HTML previews. Off, HTML code blocks get no Preview button. */
val LocalLivePreviews = compositionLocalOf { true }

/** Copies [text] to the clipboard and says so. */
internal fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("Daybreak", text))
    Toast.makeText(context, R.string.assistant_copied, Toast.LENGTH_SHORT).show()
}

/** The card a website leaves in the chat: "Generating code" while it is written, its title once it is done. */
@Composable
internal fun WebsiteCardView(title: String, generating: Boolean, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).background(colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (generating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.onPrimaryContainer)
                } else {
                    Icon(Icons.Outlined.Language, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (generating) stringResource(R.string.assistant_website_generating) else title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.assistant_website_tap),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

/** The page's own screen: a Preview of the page and its Code. [generating] shows the code as it is written. */
@Composable
internal fun WebsiteDialog(title: String, html: String, generating: Boolean, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = colors.surface) {
            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 4.dp, end = 8.dp),
                    )
                }
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.assistant_website_preview)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.assistant_website_code)) })
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (tab == 0) {
                        if (generating) {
                            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.assistant_website_writing),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        } else {
                            WebPreview(html, Modifier.fillMaxSize())
                        }
                    } else {
                        CodeView(code = html, language = "html", generating = generating)
                    }
                }
            }
        }
    }
}

/** The code with its language and a copy button, scrolled to the newest line while it is being written. */
@Composable
private fun CodeView(code: String, language: String, generating: Boolean) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val vertical = rememberScrollState()
    LaunchedEffect(code.length) {
        if (generating) vertical.scrollTo(vertical.maxValue)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { copyToClipboard(context, code) }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.assistant_code_copy))
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(vertical)
                .padding(start = 16.dp, end = 8.dp, bottom = 16.dp),
        ) {
            HighlightedCode(
                code = code,
                language = language,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}

/** A code block in an answer: the language, a Preview for HTML, a copy button, and the highlighted code. */
@Composable
internal fun CodeBlock(language: String, code: String) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val open = LocalOpenWebsite.current
    val isHtml = language.trim().lowercase() in setOf("html", "htm", "xhtml")
    val defaultTitle = stringResource(R.string.assistant_website_default_title)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceContainerHighest,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language.ifBlank { "code" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (isHtml && LocalLivePreviews.current) {
                    TextButton(onClick = { open(WebsiteSource.Static(defaultTitle, code)) }) {
                        Text(stringResource(R.string.assistant_code_preview))
                    }
                }
                IconButton(onClick = { copyToClipboard(context, code) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.assistant_code_copy), modifier = Modifier.size(18.dp))
                }
            }
            HighlightedCode(
                code = code,
                language = language,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            )
        }
    }
}

/** Code with its keywords, strings, comments and so on in colour. Joined, the text is the code exactly. */
@Composable
internal fun HighlightedCode(code: String, language: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val palette = remember(colors) { codePalette(colors) }
    val text = remember(code, language, palette) { highlighted(code, language, palette) }
    androidx.compose.material3.Text(
        text = text,
        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
        color = colors.onSurface,
        softWrap = false,
        modifier = modifier,
    )
}

private fun codePalette(colors: ColorScheme): Map<CodeHighlighter.Kind, SpanStyle> {
    val dark = colors.surface.luminance() < 0.5f
    return mapOf(
        CodeHighlighter.Kind.KEYWORD to SpanStyle(color = if (dark) Color(0xFFC792EA) else Color(0xFF7B2CBF), fontWeight = FontWeight.SemiBold),
        CodeHighlighter.Kind.STRING to SpanStyle(color = if (dark) Color(0xFFC3E88D) else Color(0xFF2E7D32)),
        CodeHighlighter.Kind.COMMENT to SpanStyle(color = colors.onSurfaceVariant, fontStyle = FontStyle.Italic),
        CodeHighlighter.Kind.NUMBER to SpanStyle(color = if (dark) Color(0xFFF78C6C) else Color(0xFFB45309)),
        CodeHighlighter.Kind.TAG to SpanStyle(color = if (dark) Color(0xFF82AAFF) else Color(0xFF1D4ED8)),
        CodeHighlighter.Kind.ATTRIBUTE to SpanStyle(color = if (dark) Color(0xFFFFCB6B) else Color(0xFF0E7490)),
    )
}

private fun highlighted(code: String, language: String, palette: Map<CodeHighlighter.Kind, SpanStyle>): AnnotatedString =
    buildAnnotatedString {
        for (token in CodeHighlighter.tokenize(code, language)) {
            val style = palette[token.kind]
            if (style != null) withStyle(style) { append(token.text) } else append(token.text)
        }
    }

/**
 * The page in a WebView. Scripts and styles run inside the page; anything it tries to load from the web is
 * answered with nothing, and links do not leave the page, so the preview shows only what the page holds.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebPreview(html: String, modifier: Modifier = Modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val scheme = request.url?.scheme
                        return if (scheme == "http" || scheme == "https") {
                            WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        } else {
                            null
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                }
            }
        },
        update = { view ->
            if (view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
    )
}

/** The title of a website from its arguments while they are still arriving; the default until the title is there. */
internal fun liveTitle(arguments: String, default: String): String =
    PartialJson.string(arguments, "title")?.replace(Regex("\\s+"), " ")?.trim()?.ifBlank { null } ?: default

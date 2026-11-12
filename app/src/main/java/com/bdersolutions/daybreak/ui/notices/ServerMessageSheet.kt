package com.bdersolutions.daybreak.ui.notices

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.data.notices.InstallSource
import com.bdersolutions.daybreak.data.notices.MessageFilter
import com.bdersolutions.daybreak.data.notices.NoticeAudience
import com.bdersolutions.daybreak.data.notices.NoticeButton
import com.bdersolutions.daybreak.data.notices.NoticeButtonStyle
import com.bdersolutions.daybreak.data.notices.NoticeImage
import com.bdersolutions.daybreak.data.notices.NoticeImageShape
import com.bdersolutions.daybreak.data.notices.NoticeLevel
import com.bdersolutions.daybreak.data.notices.NoticeSheetStyle
import com.bdersolutions.daybreak.data.notices.ServerMessage
import com.bdersolutions.daybreak.data.notices.ServerNotices
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.ui.account.openUrl
import com.bdersolutions.daybreak.ui.components.Button
import com.bdersolutions.daybreak.ui.components.FilledTonalButton
import com.bdersolutions.daybreak.ui.components.TextButton
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import com.bdersolutions.daybreak.ui.settings.currentAppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The developer's messages, one at a time, as a sheet from the bottom. A message can bring a title, a picture
 * (built in, or uploaded on the admin page), a look, and up to three buttons with a note under each; one
 * without any of that is a plain sheet with its text and an OK button. Each message is shown once: closing it
 * remembers it. Messages meant for another platform, or for installs from the other source (Google Play or an
 * APK), are not shown.
 */
@Composable
fun ServerMessageSheet(notices: ServerNotices) {
    val status by notices.status.collectAsStateWithLifecycle()
    val dismissed by notices.dismissed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val audience = remember { NoticeAudience(platform = "android", installSource = installSourceOf(context)) }
    val pending = remember(status, dismissed) {
        MessageFilter.pending(status?.messages.orEmpty(), dismissed, System.currentTimeMillis(), audience)
    }
    val message = pending.firstOrNull() ?: return
    // A new message starts its own sheet (and its own entrance).
    key(message.id) { MessageSheetContent(message = message, notices = notices) }
}

/** Where this app was installed from: Google Play, or anything else (an APK, the in-app updater, adb). */
fun installSourceOf(context: Context): InstallSource {
    val packages = context.packageManager
    val installer = runCatching {
        if (Build.VERSION.SDK_INT >= 30) {
            packages.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packages.getInstallerPackageName(context.packageName)
        }
    }.getOrNull()
    return if (installer == PLAY_STORE_PACKAGE) InstallSource.PLAY else InstallSource.SIDELOADED
}

private const val PLAY_STORE_PACKAGE = "com.android.vending"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageSheetContent(message: ServerMessage, notices: ServerNotices) {
    val german = currentAppLanguage() == AppLanguage.GERMAN
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val sheet = message.sheet
    val dismissible = sheet?.dismissible ?: true
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // A sheet that must be answered cannot be swiped away.
        confirmValueChange = { dismissible || it != SheetValue.Hidden },
    )
    val title = sheet?.title(german)?.takeIf { it.isNotBlank() } ?: stringResource(message.level.titleRes())
    val body = message.textFor(german)
    val buttons = sheet?.buttons?.takeIf { it.isNotEmpty() }
        ?: listOf(NoticeButton(de = "OK", en = "OK", url = null, style = NoticeButtonStyle.FILLED, closes = true, infoDe = "", infoEn = ""))
    val glowColor = when (sheet?.style ?: levelStyle(message.level)) {
        NoticeSheetStyle.BRAND -> MaterialTheme.colorScheme.primary
        NoticeSheetStyle.WARNING -> MaterialTheme.colorScheme.tertiary
        NoticeSheetStyle.CRITICAL -> MaterialTheme.colorScheme.error
        NoticeSheetStyle.PLAIN -> MaterialTheme.colorScheme.primary
    }
    val glowStrength = when (sheet?.style ?: levelStyle(message.level)) {
        NoticeSheetStyle.PLAIN -> 0.10f
        else -> 0.30f
    }

    // One orchestrated entrance when the sheet has settled: the glow rises, the picture settles, the words and the
    // buttons follow. Nothing moves again afterwards.
    val glow = remember { Animatable(0f) }
    val picture = remember { Animatable(0f) }
    val words = remember { Animatable(0f) }
    val actions = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { glow.animateTo(1f, spring(dampingRatio = 1f, stiffness = 60f)) }
        launch { picture.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 260f)) }
        launch { delay(120); words.animateTo(1f, spring(dampingRatio = 1f, stiffness = 220f)) }
        launch { delay(230); actions.animateTo(1f, spring(dampingRatio = 1f, stiffness = 220f)) }
    }

    fun close(then: () -> Unit = {}) {
        scope.launch {
            then()
            sheetState.hide()
            notices.dismiss(message.id)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { notices.dismiss(message.id) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        // The handle is drawn inside the glow below, so the glow reaches the very top edge of the sheet.
        dragHandle = null,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissible),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(glowColor.copy(alpha = glowStrength * glow.value), Color.Transparent),
                            center = Offset(size.width / 2f, 0f),
                            radius = size.width * (0.35f + 0.5f * glow.value),
                        ),
                    )
                },
        ) {
            BottomSheetDefaults.DragHandle(modifier = Modifier.align(Alignment.TopCenter))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                val image = sheet?.image
                // Room for the handle, unless a banner picture runs across the top.
                if (image?.shape != NoticeImageShape.BANNER) Spacer(Modifier.height(30.dp))
                if (image?.shape == NoticeImageShape.BANNER) {
                    NoticePicture(
                        image = image,
                        notices = notices,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp)
                            .graphicsLayer { alpha = picture.value }
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                        banner = true,
                    )
                    Spacer(Modifier.height(18.dp))
                }
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    if (image != null && image.shape == NoticeImageShape.ICON) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp, bottom = 18.dp)
                                .size(76.dp)
                                .scale(0.7f + 0.3f * picture.value)
                                .alpha(picture.value.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            NoticePicture(image = image, notices = notices, modifier = Modifier.fillMaxWidth().height(76.dp), banner = false)
                        }
                    }
                    Column(
                        modifier = Modifier.graphicsLayer {
                            alpha = words.value.coerceIn(0f, 1f)
                            translationY = (1f - words.value) * 14.dp.toPx()
                        },
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (body.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = body,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Column(
                        modifier = Modifier.graphicsLayer {
                            alpha = actions.value.coerceIn(0f, 1f)
                            translationY = (1f - actions.value) * 18.dp.toPx()
                        },
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        buttons.forEach { button ->
                            NoticeButtonRow(
                                button = button,
                                german = german,
                                onClick = {
                                    haptics.perform(HapticEvent.TAP)
                                    button.url?.let { openUrl(context, it) }
                                    if (button.closes) close()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeButtonRow(button: NoticeButton, german: Boolean, onClick: () -> Unit) {
    val info = button.info(german)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val modifier = Modifier.fillMaxWidth().height(52.dp)
        when (button.style) {
            NoticeButtonStyle.FILLED -> Button(onClick = onClick, modifier = modifier) { Text(button.label(german)) }
            NoticeButtonStyle.TONAL -> FilledTonalButton(onClick = onClick, modifier = modifier) { Text(button.label(german)) }
            NoticeButtonStyle.TEXT -> TextButton(onClick = onClick, modifier = modifier) { Text(button.label(german)) }
        }
        if (info.isNotBlank()) {
            Text(
                text = info,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp),
            )
        }
    }
}

/** The picture of a sheet: a built-in one, or an uploaded one that fades in when it has been fetched. */
@Composable
private fun NoticePicture(image: NoticeImage, notices: ServerNotices, modifier: Modifier, banner: Boolean) {
    if (!image.isUpload) {
        if (image.value == "playstore") {
            Box(
                modifier = modifier.then(if (banner) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Image(painter = painterResource(R.drawable.ic_playstore), contentDescription = null, modifier = Modifier.size(if (banner) 72.dp else 44.dp))
            }
        }
        return
    }
    val bitmap by produceState<ImageBitmap?>(NoticeImageCache.get(image.value), image.value) {
        if (value == null) value = loadNoticeImage(notices, image.value)
    }
    val alpha by animateFloatAsState(if (bitmap != null) 1f else 0f, label = "noticePicture")
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().alpha(alpha),
            )
        }
    }
}

private fun levelStyle(level: NoticeLevel): NoticeSheetStyle = when (level) {
    NoticeLevel.INFO -> NoticeSheetStyle.PLAIN
    NoticeLevel.WARNING -> NoticeSheetStyle.WARNING
    NoticeLevel.CRITICAL -> NoticeSheetStyle.CRITICAL
}

private fun NoticeLevel.titleRes(): Int = when (this) {
    NoticeLevel.INFO -> R.string.notice_title_info
    NoticeLevel.WARNING -> R.string.notice_title_warning
    NoticeLevel.CRITICAL -> R.string.notice_title_critical
}

/** The few pictures of sheets that were fetched, so a sheet does not flash empty when it opens again. */
private object NoticeImageCache {
    private val cache = LruCache<String, ImageBitmap>(6)
    fun get(id: String): ImageBitmap? = cache.get(id)
    fun put(id: String, bitmap: ImageBitmap) {
        cache.put(id, bitmap)
    }
}

/** Fetches and decodes an uploaded picture, at most 1600 px on a side. Null when it cannot be had. */
private suspend fun loadNoticeImage(notices: ServerNotices, id: String): ImageBitmap? = withContext(Dispatchers.Default) {
    val bytes = notices.image(id) ?: return@withContext null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > MAX_PICTURE_SIDE || bounds.outHeight / sample > MAX_PICTURE_SIDE) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    decoded?.asImageBitmap()?.also { NoticeImageCache.put(id, it) }
}

private const val MAX_PICTURE_SIDE = 1600

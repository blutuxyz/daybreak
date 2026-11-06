package com.bdersolutions.daybreak.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.untis.UntisApi
import com.bdersolutions.daybreak.untis.UntisAuthException
import com.bdersolutions.daybreak.untis.UntisChild
import com.bdersolutions.daybreak.untis.UntisLoginResult
import com.bdersolutions.daybreak.untis.UntisNetworkException
import com.bdersolutions.daybreak.untis.UntisNoStudentException
import com.bdersolutions.daybreak.untis.UntisQrParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** The three ways to sign in, in the order of the Android app's tabs. */
private enum class LoginMode { QR, MANUAL, PASSWORD }

/** What went wrong, as the Android login shows it. The texts come from the same string resources. */
private enum class LoginFailure {
    INVALID_QR, INVALID_INPUT, AUTHENTICATION, SECOND_FACTOR, NETWORK, NO_STUDENT, STORAGE, UNKNOWN,
}

/** A login that succeeded for several students: the picker asks which one to use. */
private class PendingLogin(val result: UntisLoginResult)

/**
 * The sign-in page, the Android app's login: a language switch, the sun mark and the title, then
 * the QR-code sign-in (the primary way), with the manual key and the password behind "Andere
 * Anmeldung". A failure shakes the form. Signing in shows the spinner inside the button. When an
 * account has several students, a picker asks which one to open.
 */
@Composable
fun LoginScreen(
    container: DesktopContainer,
    /** Saves the login and returns whether it worked. The app moves on to the timetable when it did. */
    onLoggedIn: (UntisLoginResult, UntisChild) -> Boolean,
) {
    val language = LocalLanguage.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsState()

    var mode by remember { mutableStateOf(LoginMode.QR) }
    var showAlternatives by remember { mutableStateOf(false) }
    var qrPayload by remember { mutableStateOf("") }
    var showQrSecret by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf("") }
    var school by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var secondFactor by remember { mutableStateOf("") }
    var requiresSecondFactor by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<LoginFailure?>(null) }
    var failureDetail by remember { mutableStateOf<String?>(null) }
    // Counts every failure, so the same failure twice in a row still shakes the form.
    var failureSerial by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<PendingLogin?>(null) }
    var entered by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) { entered = true }
    LaunchedEffect(failureSerial) {
        if (failureSerial == 0) return@LaunchedEffect
        repeat(3) {
            shake.animateTo(10f, tween(45))
            shake.animateTo(-10f, tween(45))
        }
        shake.animateTo(0f, tween(45))
    }

    val alternativesVisible = showAlternatives || mode != LoginMode.QR
    val canSubmit = !loading && when (mode) {
        LoginMode.QR -> qrPayload.isNotBlank()
        LoginMode.MANUAL -> server.isNotBlank() && school.isNotBlank() && username.isNotBlank() && secret.isNotBlank()
        LoginMode.PASSWORD -> server.isNotBlank() && school.isNotBlank() && username.isNotBlank() &&
            password.isNotBlank() && (!requiresSecondFactor || secondFactor.isNotBlank())
    }
    val switchTo = if (settings.language == AppLanguage.GERMAN) AppLanguage.ENGLISH else AppLanguage.GERMAN

    fun fail(reason: LoginFailure, detail: String? = null) {
        failure = reason
        failureDetail = detail
        failureSerial++
    }

    fun clearError() {
        failure = null
        failureDetail = null
    }

    fun clearSensitive() {
        qrPayload = ""
        secret = ""
        password = ""
        secondFactor = ""
        requiresSecondFactor = false
    }

    fun saveAndOpen(result: UntisLoginResult, child: UntisChild) {
        val saved = onLoggedIn(result, child)
        if (saved) clearSensitive() else fail(LoginFailure.STORAGE)
    }

    fun finishLogin(result: UntisLoginResult) {
        when (result.children.size) {
            0 -> fail(LoginFailure.NO_STUDENT)
            1 -> saveAndOpen(result, result.children.first())
            else -> pending = PendingLogin(result)
        }
    }

    fun handleFailure(error: Throwable) {
        when (error) {
            is UntisAuthException -> if (error.requiresSecondFactor) {
                requiresSecondFactor = true
                fail(LoginFailure.SECOND_FACTOR)
            } else {
                fail(LoginFailure.AUTHENTICATION)
            }
            is UntisNetworkException -> fail(LoginFailure.NETWORK)
            is UntisNoStudentException -> fail(LoginFailure.NO_STUDENT)
            else -> fail(LoginFailure.UNKNOWN, error.message)
        }
    }

    fun launchLogin(request: suspend () -> UntisLoginResult) {
        if (loading) return
        loading = true
        clearError()
        scope.launch {
            try {
                finishLogin(request())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFailure(e)
            } finally {
                loading = false
            }
        }
    }

    fun submit() {
        if (loading) return
        when (mode) {
            LoginMode.QR -> {
                val payload = qrPayload.trim()
                if (payload.isBlank()) {
                    fail(LoginFailure.INVALID_QR)
                    return
                }
                val qr = runCatching { UntisQrParser.parse(payload) }.getOrElse {
                    fail(LoginFailure.INVALID_QR, it.message)
                    return
                }
                server = qr.server
                school = qr.school
                username = qr.username
                secret = qr.secret
                launchLogin { container.untisApi.loginWithSecret(qr.server, qr.school, qr.username, qr.secret) }
            }

            LoginMode.MANUAL -> {
                if (server.isBlank() || school.isBlank() || username.isBlank() || secret.isBlank()) {
                    fail(LoginFailure.INVALID_INPUT)
                    return
                }
                val (s, sc, u, k) = listOf(server.trim(), school.trim(), username.trim(), secret.trim())
                launchLogin { container.untisApi.loginWithSecret(s, sc, u, k) }
            }

            LoginMode.PASSWORD -> {
                if (server.isBlank() || school.isBlank() || username.isBlank() || password.isBlank()) {
                    fail(LoginFailure.INVALID_INPUT)
                    return
                }
                val (s, sc, u) = listOf(server.trim(), school.trim(), username.trim())
                val pw = password
                val code = secondFactor.trim()
                launchLogin { container.untisApi.loginWithPassword(s, sc, u, pw, code) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.widthIn(max = FormWidth).fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { container.settings.setLanguage(switchTo) },
                    enabled = !loading,
                    modifier = Modifier.handCursor(),
                ) {
                    Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (switchTo == AppLanguage.GERMAN) "Deutsch" else "English")
                }
            }

            Column(
                modifier = Modifier.widthIn(max = FormWidth).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Reveal(entered, index = 0) { SunMark() }
                Spacer(Modifier.height(16.dp))
                Reveal(entered, index = 1) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            tr("Daybreak", "Daybreak"),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr("Dein Stundenplan. Klar, ruhig und immer dabei.", "Your timetable. Clear, calm and always with you."),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(26.dp))
                Reveal(entered, index = 2) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            tr("Anmelden", "Sign in"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            tr("Verbinde Daybreak mit deinem WebUntis-Konto.", "Connect Daybreak to your WebUntis account."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))

                        AnimatedVisibility(
                            visible = alternativesVisible,
                            enter = expandVertically(DesktopMotion.spatial()) + fadeIn(DesktopMotion.fastEffects()),
                            exit = shrinkVertically(DesktopMotion.fastSpatial()) + fadeOut(DesktopMotion.fastEffects()),
                        ) {
                            Column {
                                LoginModeSwitcher(mode = mode, enabled = !loading, onChange = { next ->
                                    mode = next
                                    clearError()
                                })
                                Spacer(Modifier.height(18.dp))
                            }
                        }
                        if (!alternativesVisible) Spacer(Modifier.height(18.dp))

                        AnimatedContent(
                            targetState = mode,
                            modifier = Modifier.fillMaxWidth(),
                            transitionSpec = {
                                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                (
                                    slideInHorizontally(DesktopMotion.spatial<androidx.compose.ui.unit.IntOffset>()) { width -> direction * width / 6 } +
                                        fadeIn(DesktopMotion.fastEffects<Float>())
                                    ).togetherWith(
                                    slideOutHorizontally(DesktopMotion.spatial<androidx.compose.ui.unit.IntOffset>()) { width -> -direction * width / 6 } +
                                        fadeOut(DesktopMotion.fastEffects<Float>()),
                                ).using(SizeTransform(clip = false))
                            },
                            label = "loginMode",
                        ) { current ->
                            when (current) {
                                LoginMode.QR -> QrFields(
                                    payload = qrPayload,
                                    onPayload = { qrPayload = it; clearError() },
                                    showSecret = showQrSecret,
                                    onToggleSecret = { showQrSecret = !showQrSecret },
                                    enabled = !loading,
                                    onPaste = {
                                        clipboard.getText()?.text?.let { qrPayload = it.trim(); clearError() }
                                    },
                                )
                                LoginMode.MANUAL -> KeyFields(
                                    server = server, onServer = { server = it; clearError() },
                                    school = school, onSchool = { school = it; clearError() },
                                    username = username, onUsername = { username = it; clearError() },
                                    secret = secret, onSecret = { secret = it; clearError() },
                                    showSecret = showSecret, onToggleSecret = { showSecret = !showSecret },
                                    enabled = !loading,
                                    onDone = ::submit,
                                )
                                LoginMode.PASSWORD -> PasswordFields(
                                    server = server, onServer = { server = it; clearError() },
                                    school = school, onSchool = { school = it; clearError() },
                                    username = username, onUsername = { username = it; clearError() },
                                    password = password, onPassword = { password = it; clearError() },
                                    showPassword = showPassword, onTogglePassword = { showPassword = !showPassword },
                                    requiresSecondFactor = requiresSecondFactor,
                                    secondFactor = secondFactor, onSecondFactor = { secondFactor = it; clearError() },
                                    enabled = !loading,
                                    onDone = ::submit,
                                )
                            }
                        }
                    }
                }

                failure?.let { reason ->
                    Spacer(Modifier.height(14.dp))
                    Box(modifier = Modifier.graphicsLayer { translationX = shake.value }) {
                        LoginErrorCard(reason, failureDetail)
                    }
                }

                AnimatedVisibility(
                    visible = mode != LoginMode.QR || qrPayload.isNotBlank(),
                    enter = scaleIn(DesktopMotion.spatial(), initialScale = 0.9f) + fadeIn(DesktopMotion.fastEffects()),
                    exit = scaleOut(DesktopMotion.fastSpatial(), targetScale = 0.9f) + fadeOut(DesktopMotion.fastEffects()),
                ) {
                    Column {
                        Spacer(Modifier.height(18.dp))
                        SignInButton(
                            mode = mode,
                            loading = loading,
                            enabled = canSubmit,
                            onClick = ::submit,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                AlternativesToggle(
                    mode = mode,
                    alternativesVisible = alternativesVisible,
                    enabled = !loading,
                    onClick = {
                        when {
                            mode != LoginMode.QR -> {
                                mode = LoginMode.QR
                                showAlternatives = false
                                clearError()
                            }
                            alternativesVisible -> showAlternatives = false
                            else -> showAlternatives = true
                        }
                    },
                )

                Spacer(Modifier.height(22.dp))
                Reveal(entered, index = 3) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Spacer(Modifier.height(14.dp))
                        Text(
                            tr(
                                "Daybreak ist nicht mit Untis GmbH verbunden. Die inoffizielle mobile API kann sich ändern.",
                                "Daybreak is not affiliated with Untis GmbH. The unofficial mobile API may change.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    pending?.let { login ->
        StudentPicker(
            students = login.result.children,
            onSelected = { child ->
                pending = null
                saveAndOpen(login.result, child)
            },
            onCancelled = { pending = null },
        )
    }
}

/** The width of the sign-in column. On a desktop window the form stays a centred column of this width. */
private val FormWidth = 440.dp

/** Fades and rises a block in, one after another, like the Android login's entrance. */
@Composable
private fun Reveal(entered: Boolean, index: Int, content: @Composable () -> Unit) {
    val revealAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 420, delayMillis = index * 80, easing = FastOutSlowInEasing),
        label = "revealAlpha$index",
    )
    val revealRise by animateFloatAsState(
        targetValue = if (entered) 0f else 1f,
        animationSpec = tween(durationMillis = 420, delayMillis = index * 80, easing = FastOutSlowInEasing),
        label = "revealRise$index",
    )
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = revealAlpha
            translationY = revealRise * 22f
        },
    ) {
        content()
    }
}

/**
 * The sun of the Android login, drawn as a star that breathes between two points. The Android app
 * morphs real Material shapes; this is the same mark with a plain polygon.
 */
@Composable
private fun SunMark() {
    val transition = rememberInfiniteTransition(label = "sun")
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sunBreathe",
    )
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val dotColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(96.dp)) {
        val points = 12
        val outer = size.minDimension * 0.42f
        val inner = outer * lerp(0.80f, 0.90f, breathe)
        val path = Path()
        for (i in 0 until points * 2) {
            val radius = if (i % 2 == 0) outer else inner
            val angle = Math.PI * i / points - Math.PI / 2
            val x = center.x + radius * cos(angle).toFloat()
            val y = center.y + radius * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path = path, color = containerColor)
        drawCircle(color = dotColor, radius = size.minDimension * 0.22f, center = center)
    }
}

/** The three sign-in ways as equal pills; the chosen one is filled. */
@Composable
private fun LoginModeSwitcher(mode: LoginMode, enabled: Boolean, onChange: (LoginMode) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LoginMode.entries.forEach { item ->
            val selected = mode == item
            val container by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = DesktopMotion.effects(),
                label = "modeContainer",
            )
            val content by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = DesktopMotion.effects(),
                label = "modeContent",
            )
            Surface(
                onClick = { if (enabled) onChange(item) },
                enabled = enabled,
                shape = CircleShape,
                color = container,
                contentColor = content,
                modifier = Modifier.weight(1f).height(46.dp).handCursor(),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        when (item) {
                            LoginMode.QR -> tr("QR-Code", "QR code")
                            LoginMode.MANUAL -> tr("Manuell", "Manually")
                            LoginMode.PASSWORD -> tr("Passwort", "Password")
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun QrFields(
    payload: String,
    onPayload: (String) -> Unit,
    showSecret: Boolean,
    onToggleSecret: () -> Unit,
    enabled: Boolean,
    onPaste: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The Android app scans the code with the camera. A desktop has no camera here, so the code
        // is copied on the phone or from WebUntis and pasted in.
        Button(
            onClick = onPaste,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(60.dp).handCursor(),
            shape = RoundedCornerShape(30.dp),
        ) {
            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(tr("Aus Zwischenablage einfügen", "Paste from clipboard"), style = MaterialTheme.typography.titleMedium)
        }
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp).size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    tr(
                        "Scanne den QR-Code aus WebUntis unter Profil → Freigaben. Der Schlüssel wird verschlüsselt gespeichert.",
                        "Scan the QR code in WebUntis under Profile → Data access → Display. The key is stored encrypted.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        tr("Profil → Freigaben", "Profile → Data access → Display"),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        OrDivider(tr("oder Inhalt einfügen", "or paste content"))
        Spacer(Modifier.height(2.dp))
        LoginField(
            value = payload,
            onValueChange = onPayload,
            label = tr("QR-Code-Inhalt", "QR code content"),
            placeholder = "untis://setschool?…",
            enabled = enabled,
            singleLine = false,
            minLines = 2,
            maxLines = 4,
            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { VisibilityToggle(showSecret, onToggleSecret) },
            supportingText = tr(
                "Du kannst den QR-Code-Inhalt auch aus der Zwischenablage einfügen.",
                "You can also paste the QR code content from the clipboard.",
            ),
        )
    }
}

@Composable
private fun KeyFields(
    server: String, onServer: (String) -> Unit,
    school: String, onSchool: (String) -> Unit,
    username: String, onUsername: (String) -> Unit,
    secret: String, onSecret: (String) -> Unit,
    showSecret: Boolean, onToggleSecret: () -> Unit,
    enabled: Boolean,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ServerField(server, onServer, enabled)
        LoginField(
            value = school,
            onValueChange = onSchool,
            label = tr("Schule", "School"),
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        LoginField(
            value = username,
            onValueChange = onUsername,
            label = tr("Benutzername", "Username"),
            enabled = enabled,
            leadingIcon = Icons.Outlined.Person,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        LoginField(
            value = secret,
            onValueChange = onSecret,
            label = tr("Schlüssel", "Key"),
            enabled = enabled,
            leadingIcon = Icons.Outlined.Lock,
            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { VisibilityToggle(showSecret, onToggleSecret) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            supportingText = tr(
                "Du findest Server, Schule und Schlüssel im Untis-Mobile-QR-Code.",
                "You can find the server, school and key in the Untis Mobile QR code.",
            ),
        )
    }
}

@Composable
private fun PasswordFields(
    server: String, onServer: (String) -> Unit,
    school: String, onSchool: (String) -> Unit,
    username: String, onUsername: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    showPassword: Boolean, onTogglePassword: () -> Unit,
    requiresSecondFactor: Boolean,
    secondFactor: String, onSecondFactor: (String) -> Unit,
    enabled: Boolean,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ServerField(server, onServer, enabled)
        LoginField(
            value = school,
            onValueChange = onSchool,
            label = tr("Schule", "School"),
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        LoginField(
            value = username,
            onValueChange = onUsername,
            label = tr("Benutzername", "Username"),
            enabled = enabled,
            leadingIcon = Icons.Outlined.Person,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        LoginField(
            value = password,
            onValueChange = onPassword,
            label = tr("Passwort", "Password"),
            enabled = enabled,
            leadingIcon = Icons.Outlined.Lock,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { VisibilityToggle(showPassword, onTogglePassword) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (requiresSecondFactor) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )
        if (requiresSecondFactor) {
            LoginField(
                value = secondFactor,
                onValueChange = onSecondFactor,
                label = tr("Bestätigungscode", "Verification code"),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                supportingText = tr(
                    "Wird nur bei aktivierter Zwei-Faktor-Authentifizierung benötigt.",
                    "Only needed when two-factor authentication is enabled.",
                ),
            )
        }
        Text(
            tr(
                "Daybreak verwendet dein Passwort nur einmalig, um den Zugangsschlüssel abzurufen. Das Passwort wird nicht gespeichert.",
                "Daybreak uses your password once to derive the access key. The password itself is not stored.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ServerField(server: String, onServer: (String) -> Unit, enabled: Boolean) {
    LoginField(
        value = server,
        onValueChange = onServer,
        label = tr("Server", "Server"),
        placeholder = "school.webuntis.com",
        enabled = enabled,
        leadingIcon = Icons.Outlined.School,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
    )
}

/** The text field of the login, in the Android app's large rounded shape. */
@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        placeholder = placeholder?.let { hint -> { Text(hint) } },
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null) } },
        trailingIcon = trailingIcon,
        supportingText = supportingText?.let { text -> { Text(text) } },
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun VisibilityToggle(shown: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle, modifier = Modifier.handCursor()) {
        Icon(
            if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = tr("Sichtbarkeit umschalten", "Toggle visibility"),
        )
    }
}

@Composable
private fun OrDivider(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    }
}

/**
 * The sign-in button. While signing in the label is replaced by a small spinner, not a large
 * indicator, so the button keeps its height.
 */
@Composable
private fun SignInButton(mode: LoginMode, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp).handCursor(),
        shape = RoundedCornerShape(28.dp),
        colors = if (mode == LoginMode.QR) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = {
                (fadeIn(DesktopMotion.fastEffects<Float>()) + scaleIn(DesktopMotion.fastSpatial<Float>(), initialScale = 0.8f))
                    .togetherWith(fadeOut(DesktopMotion.fastEffects<Float>()) + scaleOut(DesktopMotion.fastSpatial<Float>(), targetScale = 0.8f))
            },
            label = "signInContent",
        ) { busy ->
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = spinnerColor(mode),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(tr("Anmeldung läuft…", "Signing in…"))
                }
            } else {
                Text(tr("Anmelden", "Sign in"), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** The spinner takes the colour of the button's text, as the Android indicator does. */
@Composable
private fun spinnerColor(mode: LoginMode): Color =
    if (mode == LoginMode.QR) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary

/** The link under the button: the other sign-in ways, or back to the QR code. */
@Composable
private fun AlternativesToggle(mode: LoginMode, alternativesVisible: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.handCursor()) {
            Icon(
                imageVector = when {
                    mode != LoginMode.QR -> Icons.Outlined.QrCodeScanner
                    alternativesVisible -> Icons.Outlined.ExpandLess
                    else -> Icons.Outlined.ExpandMore
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    mode != LoginMode.QR -> tr("Zurück zum QR-Code", "Back to QR code")
                    alternativesVisible -> tr("Weniger Optionen", "Fewer options")
                    else -> tr("Andere Anmeldung", "Other sign-in options")
                },
            )
        }
    }
}

/** The error card, in the error container colours. The detail is shown only for unknown failures. */
@Composable
private fun LoginErrorCard(reason: LoginFailure, detail: String?) {
    val text = when (reason) {
        LoginFailure.INVALID_QR -> tr("Der Inhalt ist kein gültiger Untis-Mobile-QR-Code.", "This is not a valid Untis Mobile QR code.")
        LoginFailure.INVALID_INPUT -> tr("Bitte fülle alle benötigten Felder aus.", "Please fill in all required fields.")
        LoginFailure.AUTHENTICATION -> tr(
            "Anmeldung fehlgeschlagen. Prüfe Server, Schule, Benutzername und Schlüssel.",
            "Sign-in failed. Check the server, school, username and key.",
        )
        LoginFailure.SECOND_FACTOR -> tr("Für dieses Konto ist ein zweiter Faktor erforderlich.", "This account requires a second factor.")
        LoginFailure.NETWORK -> tr(
            "Keine Verbindung zu WebUntis. Die zuletzt gespeicherten Daten bleiben verfügbar.",
            "Could not reach WebUntis. Your last synced data is still available.",
        )
        LoginFailure.NO_STUDENT -> tr("Mit diesem Konto ist keine Schülerin und kein Schüler verknüpft.", "No student is linked to this account.")
        LoginFailure.STORAGE -> tr(
            "Der Schlüssel konnte nicht sicher gespeichert werden. Bitte versuche es erneut.",
            "The key could not be stored securely. Please try again.",
        )
        LoginFailure.UNKNOWN -> tr("WebUntis hat eine unerwartete Antwort geliefert.", "WebUntis returned an unexpected response.")
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(text, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (!detail.isNullOrBlank() && reason == LoginFailure.UNKNOWN) {
                    Spacer(Modifier.height(3.dp))
                    Text(detail, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * Asks which student to sign in as. Each row picks that student straight away; closing the dialog
 * or pressing Cancel goes back to the sign-in form.
 */
@Composable
private fun StudentPicker(
    students: List<UntisChild>,
    onSelected: (UntisChild) -> Unit,
    onCancelled: () -> Unit,
) {
    DesktopDialog(onDismiss = onCancelled, maxWidth = 460.dp) { close ->
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                tr("Schülerin oder Schüler wählen", "Choose a student"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                tr(
                    "Für dieses Konto sind mehrere Personen hinterlegt. Wessen Stundenplan möchtest du sehen?",
                    "Several people are linked to this account. Whose timetable would you like to see?",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                students.forEach { student ->
                    ChoiceRow(label = student.displayName, selected = false) { onSelected(student) }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = close, modifier = Modifier.handCursor()) { Text(tr("Abbrechen", "Cancel")) }
            }
        }
    }
}

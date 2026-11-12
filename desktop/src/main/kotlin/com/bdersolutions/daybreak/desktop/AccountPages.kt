package com.bdersolutions.daybreak.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bdersolutions.daybreak.data.account.Access
import com.bdersolutions.daybreak.data.account.AccountError
import com.bdersolutions.daybreak.data.account.AccountParser
import com.bdersolutions.daybreak.data.account.ApiResult
import com.bdersolutions.daybreak.data.account.CreatedAccount
import com.bdersolutions.daybreak.data.account.Entitlement
import com.bdersolutions.daybreak.data.account.Plan
import com.bdersolutions.daybreak.desktop.update.LatestRelease
import com.bdersolutions.daybreak.untis.UntisSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.runtime.mutableStateOf as stateOf

/** What the plan allows right now. Provided once at the top of the signed-in app (see `SignedInScreen`). */
val LocalAccess = compositionLocalOf { Access.NONE }

/**
 * Opens the plans dialog for a locked feature (or without one). Any locked row calls [show] with its
 * [Entitlement]; the dialog then says which plan the feature is part of.
 */
fun interface PlansLauncher {
    fun show(feature: Entitlement?)
}

val LocalPlans = staticCompositionLocalOf { PlansLauncher { } }

/** Whether the person may use [this] right now: the one place the screens ask. */
@Composable
fun Entitlement.allowed(): Boolean = LocalAccess.current.allows(this)

/** Opens [url] in the default browser. Does nothing when the computer has none. */
fun openUrl(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}

@Composable
fun Plan.label(): String = when (this) {
    Plan.FREE -> tr("Free", "Free")
    Plan.PLUS -> "Plus"
    Plan.PRO -> "Pro"
}

@Composable
fun Entitlement.label(): String = when (this) {
    Entitlement.APP_NAME -> tr("App-Name", "App name")
    Entitlement.APP_ICON -> tr("App-Symbol", "App icon")
    Entitlement.CALENDAR_EXPORT -> tr("Kalenderexport", "Calendar export")
    Entitlement.WIDGET_CUSTOMISATION -> tr("Widget-Optionen", "Widget options")
    Entitlement.SHARE_PICTURE -> tr("Stundenplan als Bild teilen", "Share the timetable as a picture")
    Entitlement.SYNC -> tr("Abgleich auf allen Geräten", "Sync across devices")
    Entitlement.DAWN_REASONING -> tr("Denkstufe in Dawn", "Reasoning level in Dawn")
    Entitlement.DAWN_DRAFTS -> tr("Nachrichtenentwürfe in Dawn", "Message drafts in Dawn")
    Entitlement.HAPTICS_STUDIO -> tr("Haptik-Studio", "Haptics studio")
    Entitlement.ACCENT_PALETTES -> tr("Akzentfarben", "Accent colours")
    Entitlement.AMOLED -> tr("AMOLED-Schwarz", "AMOLED black")
}

@Composable
fun AccountError.text(): String = when (this) {
    AccountError.OFFLINE -> tr("Keine Verbindung zum Server. Prüfe dein Netz und versuche es noch einmal.", "No connection to the server. Check your network and try again.")
    AccountError.UNAUTHORIZED -> tr("Dieser Computer ist nicht mehr angemeldet. Melde dich mit deinem Wiederherstellungscode an.", "This computer is no longer signed in. Sign in with your restore code.")
    AccountError.UNKNOWN_CODE -> tr("Diesen Wiederherstellungscode kennen wir nicht. Prüfe ihn auf Tippfehler.", "We do not know this restore code. Check it for typing mistakes.")
    AccountError.LIMIT -> tr("Es sind schon drei WebUntis-Konten verbunden. Entferne eins, um ein neues zu verbinden.", "Three WebUntis accounts are connected already. Remove one to connect a new one.")
    AccountError.DUPLICATE -> tr("Dieses WebUntis-Konto ist schon mit deinem Daybreak-Konto verbunden.", "This WebUntis account is already connected to your Daybreak account.")
    AccountError.NOT_OFFERED -> tr("Dieses Angebot gibt es für dein Konto nicht (mehr).", "This offer is not available for your account (any more).")
    AccountError.NO_SUBSCRIPTION -> tr("Du hast noch kein Abo, das du verwalten könntest.", "You do not have a subscription to manage yet.")
    AccountError.RATE_LIMITED -> tr("Zu viele Versuche. Warte kurz und versuche es dann noch einmal.", "Too many tries. Wait a moment and try again.")
    AccountError.BAD_REQUEST, AccountError.UNAVAILABLE -> tr("Gerade nicht erreichbar. Versuche es später noch einmal.", "Not reachable right now. Please try again later.")
}

/**
 * The logic behind the migration dialog, the account page, the plans dialog and the trial offer. All calls go
 * through [com.bdersolutions.daybreak.data.account.AccountManager]; this class only holds what the screens show
 * while a call runs (busy, error, the code of a new account).
 */
class AccountController(private val container: DesktopContainer, private val scope: CoroutineScope) {
    private val manager = container.accountManager
    private val prefs = container.accountPrefs

    var busy by stateOf(false)
        private set
    var error by stateOf<AccountError?>(null)
    /** Set right after an account was made: the restore code is shown once, prominently. */
    var created by stateOf<CreatedAccount?>(null)
    /** A bound WebUntis account that another Daybreak account has bound as well. */
    var sharedWarning by stateOf(false)
    /** The slot id of the current WebUntis login, if it is bound from this computer. */
    var boundSlotId by stateOf<String?>(null)
    var trialStarted by stateOf(false)

    /** An install used before 2.0 with a Ko-fi payment that has not been carried over yet. */
    val legacySupporter: Boolean get() = manager.legacy().paid

    private fun call(block: suspend () -> AccountError?) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val failure = block()
            busy = false
            error = failure
        }
    }

    fun create() = call {
        when (val result = manager.create(prefs.installId())) {
            is ApiResult.Ok -> {
                created = result.value
                null
            }
            is ApiResult.Fail -> result.error
        }
    }

    fun restore(code: String) = call {
        when (val result = manager.restore(code)) {
            is ApiResult.Ok -> null
            is ApiResult.Fail -> result.error
        }
    }

    fun acknowledgeCreated() {
        created = null
    }

    fun loadBound(session: UntisSession) {
        boundSlotId = prefs.slotFor(keyOf(session))
    }

    fun bind(session: UntisSession, label: String) = call {
        val name = label.trim().ifBlank { session.school }
        // Named by what the school's own token says (school id and user id), not by the login name or the e-mail.
        val identity = try {
            container.untisApi.fetchIdentity(session)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: com.bdersolutions.daybreak.untis.UntisNetworkException) {
            return@call AccountError.OFFLINE
        } catch (_: Exception) {
            return@call AccountError.UNAVAILABLE
        }
        when (val result = manager.bind(session.server, identity.tenantId, identity.userId, name)) {
            is ApiResult.Ok -> {
                prefs.setSlot(keyOf(session), result.value.slot.id)
                boundSlotId = result.value.slot.id
                sharedWarning = result.value.sharedWithOthers
                null
            }
            is ApiResult.Fail -> result.error
        }
    }

    fun unbind(slotId: String, session: UntisSession) = call {
        when (val result = manager.unbind(slotId)) {
            is ApiResult.Ok -> {
                if (prefs.slotFor(keyOf(session)) == slotId) prefs.setSlot(keyOf(session), null)
                if (boundSlotId == slotId) boundSlotId = null
                sharedWarning = false
                null
            }
            is ApiResult.Fail -> result.error
        }
    }

    fun acceptTrial() = call {
        when (val result = manager.acceptTrial()) {
            is ApiResult.Ok -> {
                trialStarted = true
                null
            }
            is ApiResult.Fail -> result.error
        }
    }

    /** Opens Stripe Checkout for [plan] in the browser. */
    fun checkout(plan: Plan) = call {
        when (val result = manager.checkoutUrl(plan)) {
            is ApiResult.Ok -> {
                openUrl(result.value)
                null
            }
            is ApiResult.Fail -> result.error
        }
    }

    /** Opens the Stripe page where a subscriber changes the card or cancels. */
    fun manageSubscription() = call {
        when (val result = manager.portalUrl()) {
            is ApiResult.Ok -> {
                openUrl(result.value)
                null
            }
            is ApiResult.Fail -> result.error
        }
    }

    fun signOut() {
        scope.launch {
            manager.signOut()
            created = null
            error = null
            boundSlotId = null
            sharedWarning = false
        }
    }

    private fun keyOf(session: UntisSession) = "${session.server}|${session.school}|${session.studentId}"
}

private fun formatDate(iso: String?): String? =
    iso?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?.let { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(it) }

// ---- The account page ---------------------------------------------------------------------------

/** More → Daybreak account: create or restore, the plan, the restore code, up to three WebUntis accounts. */
@Composable
fun DaybreakAccountPage(container: DesktopContainer, session: UntisSession) {
    val scope = rememberCoroutineScope()
    val controller = remember { AccountController(container, scope) }
    val snapshot by container.accountManager.snapshot.collectAsState()
    val plans = LocalPlans.current
    LaunchedEffect(Unit) {
        controller.loadBound(session)
        if (container.accountAvailable) container.accountManager.refresh()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val made = controller.created
            when {
                !container.accountAvailable -> Text(
                    tr("Dieser Build hat keinen Update-Server: Konto und Tarife sind nicht verfügbar, alles ist freigeschaltet.", "This build has no update server: account and plans are not available, and everything is unlocked."),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                made != null -> RestoreCodeCreated(made, supporterThanks = snapshot.access.supporter, onContinue = controller::acknowledgeCreated)
                !snapshot.signedIn -> SignedOutAccount(controller)
                else -> SignedInAccount(container, session, controller, plans)
            }
        }
    }
}

@Composable
private fun SignedOutAccount(controller: AccountController) {
    var codeOpen by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    Text(
        tr(
            "Ein Konto bewahrt deinen Tarif und deine Daybreak-Einstellungen auf, auch wenn du den Computer wechselst. Kein Passwort, keine E-Mail.",
            "An account keeps your plan and your Daybreak settings safe, even when you change computers. No password, no email.",
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = controller::create,
        enabled = !controller.busy,
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth().height(52.dp).handCursor(),
    ) {
        BusyLabel(controller.busy && !codeOpen, tr("Konto erstellen", "Create account"))
    }
    OutlinedButton(
        onClick = { codeOpen = !codeOpen },
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth().height(48.dp).handCursor(),
    ) { Text(tr("Ich habe einen Wiederherstellungscode", "I have a restore code")) }
    AnimatedVisibility(
        visible = codeOpen,
        enter = fadeIn(DesktopMotion.effects()) + expandVertically(DesktopMotion.spatial()),
        exit = fadeOut(DesktopMotion.fastEffects()) + shrinkVertically(DesktopMotion.fastSpatial()),
    ) {
        RestoreCodeField(controller, code, onCode = { code = it })
    }
    ErrorText(controller.error)
}

@Composable
private fun RestoreCodeField(controller: AccountController, code: String, onCode: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = { onCode(it.take(32)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(tr("Wiederherstellungscode", "Restore code")) },
            placeholder = { Text("DA-XXXX-XXXX-XXXX") },
            singleLine = true,
            shape = CircleShape,
        )
        Button(
            onClick = { controller.restore(code) },
            enabled = !controller.busy && AccountParser.normalizeRestoreCode(code) != null,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(48.dp).handCursor(),
        ) { BusyLabel(controller.busy, tr("Anmelden", "Sign in")) }
    }
}

@Composable
private fun BusyLabel(busy: Boolean, text: String) {
    if (busy) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text)
}

@Composable
private fun ErrorText(error: AccountError?) {
    AnimatedVisibility(
        visible = error != null,
        enter = fadeIn(DesktopMotion.effects()) + expandVertically(DesktopMotion.spatial()),
        exit = fadeOut(DesktopMotion.fastEffects()) + shrinkVertically(DesktopMotion.fastSpatial()),
    ) {
        // Keep the last text while the line animates out.
        var last by remember { mutableStateOf(error) }
        if (error != null) last = error
        Text(last?.text().orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

/** The code, shown big, with a copy button; used right after an account was made and on the account page. */
@Composable
private fun CodeBox(code: String) {
    var copied by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                code,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { copyToClipboard(code); copied = true }, modifier = Modifier.handCursor()) {
                Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (copied) tr("Kopiert", "Copied") else tr("Code kopieren", "Copy code"))
            }
        }
    }
}

@Composable
private fun RestoreCodeCreated(made: CreatedAccount, supporterThanks: Boolean, onContinue: () -> Unit) {
    Text(tr("Dein Konto ist da", "Your account is ready"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
    Text(
        tr(
            "Das ist dein Wiederherstellungscode. Mit ihm meldest du dich auf einem neuen Computer oder nach einer Neuinstallation wieder an.",
            "This is your restore code. It signs you in again on a new computer or after a reinstall.",
        ),
        style = MaterialTheme.typography.bodyLarge,
    )
    CodeBox(made.restoreCode)
    Text(
        tr(
            "Bewahre ihn sicher auf, zum Beispiel in deinem Passwortmanager. Wer ihn kennt, kommt in dein Konto, und wir können ihn nicht zurücksetzen. Du findest ihn jederzeit unter Mehr → Daybreak-Konto.",
            "Keep it safe, for example in your password manager. Anyone who knows it can get into your account, and we cannot reset it. You can always find it under More → Daybreak account.",
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (supporterThanks) {
        Text(
            tr(
                "Deine Supporter-Extras (App-Name, Kalenderexport) sind für immer freigeschaltet.",
                "Your supporter extras (app name, calendar export) are unlocked for good.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Button(onClick = onContinue, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(52.dp).handCursor()) {
        Text(tr("Weiter", "Continue"))
    }
}

@Composable
private fun SignedInAccount(container: DesktopContainer, session: UntisSession, controller: AccountController, plans: PlansLauncher) {
    val snapshot by container.accountManager.snapshot.collectAsState()
    val state = snapshot.state
    val access = snapshot.access
    val plan = access.plan

    // The plan
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(tr("Tarif: ${plan.label()}", "Plan: ${plan.label()}"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            val until = formatDate(state?.planUntil)
            if (plan != Plan.FREE) {
                Text(
                    if (until != null) tr("Läuft bis $until", "Runs until $until") else tr("Aktiv", "Active"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    tr("Alles Wichtige ist kostenlos. Mehr gibt es mit Plus und Pro.", "Everything important is free. There is more with Plus and Pro."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (access.supporter) {
                Text(
                    tr("Supporter: App-Name und Kalenderexport bleiben für immer freigeschaltet.", "Supporter: app name and calendar export stay unlocked for good."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { plans.show(null) }, shape = CircleShape, modifier = Modifier.handCursor()) {
                    Text(tr("Tarife ansehen", "See plans"))
                }
                if (plan != Plan.FREE || state?.planUntil != null) {
                    OutlinedButton(onClick = controller::manageSubscription, enabled = !controller.busy, shape = CircleShape, modifier = Modifier.handCursor()) {
                        Text(tr("Abo verwalten", "Manage subscription"))
                    }
                }
            }
        }
    }

    // The restore code
    SectionTitle(tr("Wiederherstellungscode", "Restore code"))
    val code = snapshot.credentials?.restoreCode
    if (code != null) {
        CodeBox(code)
        Text(
            tr("Bewahre ihn sicher auf: Wer ihn kennt, kommt in dein Konto.", "Keep it safe: anyone who knows it can get into your account."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            tr("Dieser Computer kennt den Code nicht. Du findest ihn auf dem Gerät, auf dem du das Konto erstellt hast.", "This computer does not know the code. You find it on the device where you made the account."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // WebUntis accounts
    val slots = state?.webuntis.orEmpty()
    SectionTitle(tr("WebUntis-Konten (${slots.size} von 3)", "WebUntis accounts (${slots.size} of 3)"))
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            if (slots.isEmpty()) {
                Text(
                    tr("Noch kein WebUntis-Konto verbunden.", "No WebUntis account connected yet."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            slots.forEachIndexed { index, slot ->
                if (index > 0) HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(slot.label, style = MaterialTheme.typography.titleMedium)
                        if (slot.id == controller.boundSlotId) {
                            Text(
                                tr("Mit diesem Computer verbunden", "Connected from this computer"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    TextButton(onClick = { controller.unbind(slot.id, session) }, enabled = !controller.busy, modifier = Modifier.handCursor()) {
                        Text(tr("Verbindung lösen", "Disconnect"))
                    }
                }
            }
        }
    }
    if (controller.boundSlotId == null || controller.boundSlotId !in slots.map { it.id }) {
        BindSection(session, controller, full = slots.size >= 3)
    }
    AnimatedVisibility(visible = controller.sharedWarning) {
        Text(
            tr("Achtung: Dieses WebUntis-Konto ist auch mit einem anderen Daybreak-Konto verbunden.", "Careful: this WebUntis account is also connected to another Daybreak account."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    ErrorText(controller.error)

    // Sign out
    var confirmSignOut by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { confirmSignOut = true }, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(48.dp).handCursor()) {
        Text(tr("Von diesem Computer abmelden", "Sign out of this computer"))
    }
    if (confirmSignOut) {
        DesktopDialog(onDismiss = { confirmSignOut = false }, maxWidth = 480.dp) { close ->
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Abmelden?", "Sign out?"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(
                    tr(
                        "Danach gilt auf diesem Computer wieder Free, bis du dich mit deinem Wiederherstellungscode anmeldest. Hast du ihn noch?",
                        "After that this computer is back on Free until you sign in with your restore code. Do you still have it?",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = close, modifier = Modifier.handCursor()) { Text(tr("Abbrechen", "Cancel")) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { controller.signOut(); close() }, modifier = Modifier.handCursor()) { Text(tr("Abmelden", "Sign out")) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp),
    )
}

@Composable
private fun BindSection(session: UntisSession, controller: AccountController, full: Boolean) {
    var label by remember { mutableStateOf(session.school) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            tr(
                "Das Konto, mit dem du gerade angemeldet bist, wird deinem Daybreak-Konto zugeordnet. Gespeichert wird nur ein Hash und die Bezeichnung, die du hier wählst.",
                "The account you are signed in with now is assigned to your Daybreak account. Only a hash and the name you choose here are stored.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(40) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(tr("Bezeichnung", "Name")) },
            singleLine = true,
            shape = CircleShape,
            enabled = !full,
        )
        Button(
            onClick = { controller.bind(session, label) },
            enabled = !controller.busy && !full,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(48.dp).handCursor(),
        ) {
            Text(if (full) tr("Alle drei Plätze sind belegt", "All three places are taken") else tr("Dieses WebUntis-Konto verbinden", "Connect this WebUntis account"))
        }
    }
}

// ---- The plans dialog ----------------------------------------------------------------------------

/** Free, Plus and Pro side by side, with a button to get a plan (Stripe Checkout in the browser). */
@Composable
fun PlansDialog(
    container: DesktopContainer,
    feature: Entitlement?,
    onDismiss: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val controller = remember { AccountController(container, scope) }
    val snapshot by container.accountManager.snapshot.collectAsState()
    val current = snapshot.access.plan
    DesktopDialog(onDismiss = onDismiss, maxWidth = 760.dp) { close ->
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                if (feature != null) {
                    tr("${feature.label()} gibt es mit ${feature.minPlanLabel()}", "${feature.label()} comes with ${feature.minPlanLabel()}")
                } else {
                    tr("Daybreak Tarife", "Daybreak plans")
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                tr(
                    "Alles Wichtige bleibt kostenlos: Stundenplan, Fehlzeiten, Nachrichten, Prüfungen, Hausaufgaben, Suche und Benachrichtigungen. Die Tarife bringen mehr Komfort und mehr von Dawn.",
                    "Everything important stays free: timetable, absences, messages, exams, homework, search and notifications. Plans add comfort and more of Dawn.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                PlanCard(
                    modifier = Modifier.weight(1f),
                    plan = Plan.FREE,
                    current = current == Plan.FREE,
                    highlighted = false,
                    items = listOf(
                        tr("Stundenplan, Fehlzeiten, Prüfungen, Nachrichten", "Timetable, absences, exams, messages"),
                        tr("Dawn mit ein paar Fragen am Tag", "Dawn with a few questions a day"),
                    ),
                    action = null,
                )
                PlanCard(
                    modifier = Modifier.weight(1f),
                    plan = Plan.PLUS,
                    current = current == Plan.PLUS,
                    highlighted = feature?.minPlan == Plan.PLUS,
                    items = listOf(
                        tr("Mehr Fragen am Tag und mehr Modelle in Dawn", "More questions a day and more models in Dawn"),
                        tr("App-Name, Kalenderexport", "App name, calendar export"),
                        tr("Widget-Optionen und Stundenplan als Bild teilen", "Widget options and sharing the timetable as a picture"),
                        tr("Deine Einstellungen und Notizen auf allen Geräten", "Your settings and notes on all your devices"),
                    ),
                    action = if (current == Plan.FREE) PlanAction(tr("Plus wählen", "Get Plus")) { controller.checkout(Plan.PLUS) } else null,
                    busy = controller.busy,
                )
                PlanCard(
                    modifier = Modifier.weight(1f),
                    plan = Plan.PRO,
                    current = current == Plan.PRO,
                    highlighted = feature?.minPlan == Plan.PRO,
                    items = listOf(
                        tr("Alles aus Plus", "Everything in Plus"),
                        tr("Die meisten Fragen am Tag und alle Modelle in Dawn", "The most questions a day and every model in Dawn"),
                        tr("Denkstufe und Nachrichtenentwürfe in Dawn", "Reasoning level and message drafts in Dawn"),
                        tr("Akzentfarben und mehr", "Accent colours and more"),
                    ),
                    action = if (current != Plan.PRO) PlanAction(tr("Pro wählen", "Get Pro")) { controller.checkout(Plan.PRO) } else null,
                    busy = controller.busy,
                )
            }
            if (!snapshot.signedIn) {
                Text(
                    tr("Dafür brauchst du ein Daybreak-Konto. Es ist in einer Minute gemacht, ohne E-Mail und ohne Passwort.", "You need a Daybreak account for that. It takes a minute, with no email and no password."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { close(); onOpenAccount() }, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(48.dp).handCursor()) {
                    Text(tr("Konto erstellen oder anmelden", "Create an account or sign in"))
                }
            } else if (current != Plan.FREE) {
                OutlinedButton(onClick = controller::manageSubscription, enabled = !controller.busy, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(48.dp).handCursor()) {
                    Text(tr("Abo verwalten", "Manage subscription"))
                }
            }
            ErrorText(controller.error)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = close, modifier = Modifier.handCursor()) { Text(tr("Schließen", "Close")) }
            }
        }
    }
}

@Composable
private fun Entitlement.minPlanLabel(): String = minPlan.label()

private class PlanAction(val text: String, val onClick: () -> Unit)

@Composable
private fun PlanCard(
    modifier: Modifier,
    plan: Plan,
    current: Boolean,
    highlighted: Boolean,
    items: List<String>,
    action: PlanAction?,
    busy: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = if (highlighted || current) scheme.primaryContainer.copy(alpha = 0.35f) else scheme.surfaceContainer,
        border = BorderStroke(if (highlighted) 2.dp else 1.dp, if (highlighted) scheme.primary else scheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(plan.label(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (current) Text(tr("Dein Tarif", "Your plan"), style = MaterialTheme.typography.labelMedium, color = scheme.primary)
            items.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.weight(1f, fill = false).height(4.dp))
            if (action != null) {
                Button(onClick = action.onClick, enabled = !busy, shape = CircleShape, modifier = Modifier.fillMaxWidth().handCursor()) {
                    BusyLabel(busy, action.text)
                }
            }
        }
    }
}

// ---- Locked rows --------------------------------------------------------------------------------

/** A locked row is dimmed; the lock sits at the end of it. */
@Composable
fun Modifier.lockedAlpha(locked: Boolean): Modifier = this.alpha(if (locked) 0.6f else 1f)

@Composable
fun LockIcon() {
    Icon(Icons.Outlined.Lock, contentDescription = tr("Gesperrt", "Locked"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---- Moving an old install to an account ----------------------------------------------------------

/**
 * Shown once to someone who used Daybreak 1.x: make an account (or sign in with a restore code). "Not now"
 * holds it back for three days. A former Ko-fi supporter is thanked and told their extras stay.
 */
@Composable
fun MigrationDialog(container: DesktopContainer, onLater: () -> Unit) {
    val scope = rememberCoroutineScope()
    val controller = remember { AccountController(container, scope) }
    val snapshot by container.accountManager.snapshot.collectAsState()
    var codeOpen by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    val made = controller.created
    // Signed in by restoring a code: nothing more to ask.
    if (snapshot.signedIn && made == null) return
    DesktopDialog(onDismiss = { if (made == null) onLater() }, maxWidth = 560.dp) { close ->
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (made != null) {
                RestoreCodeCreated(made, supporterThanks = snapshot.access.supporter, onContinue = { controller.acknowledgeCreated(); close() })
            } else {
                Text(tr("Willkommen bei Daybreak 2", "Welcome to Daybreak 2"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(
                    tr(
                        "Mit einem Daybreak-Konto gehört dein Tarif dir und nicht diesem einen Computer. Du brauchst weder E-Mail noch Passwort: Du bekommst einen Wiederherstellungscode, mit dem du dich auf jedem Gerät anmeldest.",
                        "With a Daybreak account your plan belongs to you, not to this one computer. You need no email and no password: you get a restore code that signs you in on any device.",
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (controller.legacySupporter) {
                    Text(
                        tr(
                            "Danke, dass du Daybreak über Ko-fi unterstützt hast. Deine Extras bleiben dir erhalten, und für dich liegt noch etwas bereit.",
                            "Thank you for supporting Daybreak on Ko-fi. Your extras stay with you, and there is something waiting for you.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    tr(
                        "Dein WebUntis-Passwort und dein Stundenplan werden nie an dein Daybreak-Konto gesendet.",
                        "Your WebUntis password and your timetable are never sent to your Daybreak account.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = controller::create, enabled = !controller.busy, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(52.dp).handCursor()) {
                    BusyLabel(controller.busy && !codeOpen, tr("Konto erstellen", "Create account"))
                }
                OutlinedButton(onClick = { codeOpen = !codeOpen }, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(48.dp).handCursor()) {
                    Text(tr("Ich habe einen Wiederherstellungscode", "I have a restore code"))
                }
                AnimatedVisibility(
                    visible = codeOpen,
                    enter = fadeIn(DesktopMotion.effects()) + expandVertically(DesktopMotion.spatial()),
                    exit = fadeOut(DesktopMotion.fastEffects()) + shrinkVertically(DesktopMotion.fastSpatial()),
                ) { RestoreCodeField(controller, code, onCode = { code = it }) }
                ErrorText(controller.error)
                TextButton(onClick = { onLater(); close() }, modifier = Modifier.fillMaxWidth().handCursor()) {
                    Text(tr("Jetzt nicht", "Not now"))
                }
            }
        }
    }
}

// ---- The thank-you for former Ko-fi supporters ---------------------------------------------------

/** Three months of Pro for people who paid through Ko-fi before 2.0. */
@Composable
fun TrialOfferDialog(container: DesktopContainer, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val controller = remember { AccountController(container, scope) }
    DesktopDialog(onDismiss = onDismiss, maxWidth = 520.dp) { close ->
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (controller.trialStarted) {
                Text(tr("Pro ist aktiv", "Pro is active"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(tr("Viel Spaß mit Pro! Alles ist jetzt freigeschaltet.", "Enjoy Pro! Everything is unlocked now."), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = close, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(52.dp).handCursor()) { Text(tr("Fertig", "Done")) }
            } else {
                Text(tr("Danke für deine Unterstützung", "Thank you for your support"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(
                    tr(
                        "Du hast Daybreak früher über Ko-fi unterstützt. Dafür schenken wir dir 3 Monate Daybreak Pro.",
                        "You supported Daybreak on Ko-fi before. As a thank-you, here are 3 months of Daybreak Pro.",
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    tr(
                        "Pro bringt Dawn mit den meisten Fragen und allen Modellen, Akzentfarben und mehr. Danach geht es ohne Kosten auf Free zurück; deine Supporter-Extras behältst du.",
                        "Pro brings Dawn with the most questions and every model, accent colours and more. After that you go back to Free at no cost; you keep your supporter extras.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ErrorText(controller.error)
                Button(onClick = controller::acceptTrial, enabled = !controller.busy, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(52.dp).handCursor()) {
                    BusyLabel(controller.busy, tr("3 Monate Pro starten", "Start 3 months of Pro"))
                }
                TextButton(onClick = close, modifier = Modifier.fillMaxWidth().handCursor()) { Text(tr("Später", "Later")) }
            }
        }
    }
}

/** The reminder at the top of More, for two weeks after the popup was dismissed. */
@Composable
fun TrialBanner(container: DesktopContainer) {
    val scope = rememberCoroutineScope()
    val controller = remember { AccountController(container, scope) }
    var open by remember { mutableStateOf(false) }
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WorkspacePremium, contentDescription = null)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tr("3 Monate Pro für dich", "3 months of Pro for you"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    tr("Als Dank für deine Unterstützung. Das Angebot steht hier noch zwei Wochen.", "A thank-you for your support. The offer stays here for two more weeks."),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = { open = true }, shape = CircleShape, modifier = Modifier.handCursor()) { Text(tr("Ansehen", "View")) }
        }
    }
    if (open) TrialOfferDialog(container, onDismiss = { open = false })
}

// ---- A new version is available ------------------------------------------------------------------

/**
 * Covers the whole window when a newer version was found (on every start and every check): "Download now"
 * starts the download (the progress strip and the "Update ready" dialog follow), "Later" closes it until the
 * next check.
 */
@Composable
fun UpdateAvailableOverlay(release: LatestRelease?, onDownload: () -> Unit, onLater: () -> Unit) {
    AnimatedVisibility(
        visible = release != null,
        enter = fadeIn(DesktopMotion.effects()),
        exit = fadeOut(DesktopMotion.fastEffects()),
    ) {
        // Keep the last release while the page fades out.
        var last by remember { mutableStateOf(release) }
        if (release != null) last = release
        val shown = last ?: return@AnimatedVisibility
        val version = shown.versionName.ifBlank { shown.tag }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.widthIn(max = 560.dp).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(44.dp))
                    }
                    Text(
                        tr("Eine neue Version ist verfügbar", "A new version is available"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        tr("Daybreak $version steht bereit. Möchtest du sie herunterladen?", "Daybreak $version is ready. Do you want to download it?"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (shown.changelog.isNotBlank()) {
                        ChangelogView(shown.changelog, maxHeight = 220.dp)
                    }
                    Button(onClick = onDownload, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(52.dp).handCursor()) {
                        Text(tr("Jetzt herunterladen", "Download now"))
                    }
                    TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth().handCursor()) { Text(tr("Später", "Later")) }
                }
            }
        }
    }
}

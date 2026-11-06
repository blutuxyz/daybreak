package com.bdersolutions.daybreak.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.untis.ProbeResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** The state of the connection check on the debug page. */
private sealed interface ProbeState {
    data object Idle : ProbeState
    data object Running : ProbeState
    data object NoSession : ProbeState
    data class Done(val results: List<ProbeResult>) : ProbeState
    data class Failed(val message: String) : ProbeState
}

/**
 * The debug page of the Android app: the connection check and the last raw responses from WebUntis.
 * Hidden until the "System" theme option has been tapped ten times. Passwords and keys are never in it.
 */
@Composable
fun DebugPage(container: DesktopContainer) {
    val entries by container.rawLog.entries.collectAsState()
    val language = LocalLanguage.current
    val scope = rememberCoroutineScope()
    var probe by remember { mutableStateOf<ProbeState>(ProbeState.Idle) }
    var confirmingClear by remember { mutableStateOf(false) }

    fun runProbe() {
        if (probe == ProbeState.Running) return
        val session = container.sessionStore.timetableSession()
        if (session == null) {
            probe = ProbeState.NoSession
            return
        }
        probe = ProbeState.Running
        scope.launch {
            val today = LocalDate.now()
            val weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            val absenceStart = if (today.monthValue >= 8) LocalDate.of(today.year, 8, 1) else LocalDate.of(today.year - 1, 8, 1)
            probe = try {
                val results = withContext(Dispatchers.IO) {
                    container.untisApi.runDiagnostics(
                        session = session,
                        timetableStart = weekStart,
                        timetableEnd = weekStart.plusDays(6),
                        absenceStart = absenceStart,
                        absenceEnd = today,
                    )
                }
                ProbeState.Done(results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProbeState.Failed(e.message ?: e::class.java.simpleName)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(str("debug_subtitle"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ProbeCard(probe = probe, onStart = ::runProbe)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${entries.size} / 20",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (entries.isNotEmpty()) {
                    OutlinedButton(onClick = { confirmingClear = true }, modifier = Modifier.handCursor()) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(str("debug_clear"))
                    }
                }
            }

            if (entries.isEmpty()) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(str("debug_empty_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(str("debug_empty"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                entries.forEach { entry -> RawLogCard(entry) }
            }
        }
    }

    if (confirmingClear) {
        DesktopDialog(onDismiss = { confirmingClear = false }, maxWidth = 440.dp) { close ->
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(str("debug_clear_title"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(str("debug_clear_message"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = close, modifier = Modifier.handCursor()) { Text(tr("Abbrechen", "Cancel")) }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = {
                            container.rawLog.clear()
                            close()
                        },
                        modifier = Modifier.handCursor(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    ) {
                        Text(str("debug_clear"))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProbeCard(probe: ProbeState, onStart: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(str("probe_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(str("probe_body"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onStart, enabled = probe != ProbeState.Running, modifier = Modifier.handCursor()) {
                if (probe == ProbeState.Running) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.size(8.dp))
                    Text(str("probe_running"))
                } else {
                    Text(str("probe_start"))
                }
            }
            when (val current = probe) {
                ProbeState.NoSession -> Text(str("probe_no_session"), color = MaterialTheme.colorScheme.error)
                is ProbeState.Failed -> Text(current.message, color = MaterialTheme.colorScheme.error)
                is ProbeState.Done -> current.results.forEach { result ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${result.method}  ${result.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (result.ok) "OK" else result.message,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (result.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                ProbeState.Idle, ProbeState.Running -> Unit
            }
        }
    }
}

/** One raw response: when it came, which call, and the start of the body, which expands on demand. */
@Composable
private fun RawLogCard(entry: DesktopRawEntry) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember(entry.id) { mutableStateOf(false) }
    var copied by remember(entry.id) { mutableStateOf(false) }
    val time = remember(entry.receivedAt) {
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").format(Instant.ofEpochMilli(entry.receivedAt).atZone(ZoneId.systemDefault()))
    }
    val shown = if (expanded || entry.body.length <= PREVIEW_CHARS) entry.body else entry.body.take(PREVIEW_CHARS) + " …"

    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.method, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                shown,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (entry.body.length > PREVIEW_CHARS) {
                    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.handCursor()) {
                        Text(str(if (expanded) "debug_show_less" else "debug_show_more"))
                    }
                }
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(entry.body))
                        copied = true
                    },
                    modifier = Modifier.handCursor(),
                ) {
                    Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(str(if (copied) "debug_copied" else "debug_copy"))
                }
            }
        }
    }
}

private const val PREVIEW_CHARS = 400

package com.bdersolutions.daybreak.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.schedule.IcsExport
import com.bdersolutions.daybreak.ui.components.SelectionRow
import com.bdersolutions.daybreak.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/** How many weeks the calendar file covers, counted from this week. */
private enum class ExportRange(val weeks: Int) {
    THIS_WEEK(1),
    FOUR_WEEKS(4),
    EIGHT_WEEKS(8),
}

/**
 * Supporter extra: the timetable as an iCalendar (.ics) file, to open in the phone's calendar app or
 * to keep. The file is made on the phone from the timetable the app already loads; nothing is sent.
 */
@Composable
fun CalendarExportScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var range by rememberSaveable { mutableStateOf(ExportRange.FOUR_WEEKS) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun export(open: Boolean) {
        if (working) return
        working = true
        error = null
        scope.launch {
            try {
                val text = viewModel.buildCalendar(range.weeks, CalendarExporter.labels(context))
                val file = CalendarExporter.write(context, text)
                CalendarExporter.send(context, file, open)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: ActivityNotFoundException) {
                error = context.getString(R.string.calendar_export_no_app)
            } catch (failure: Exception) {
                error = failure.toUserMessage(context)
            } finally {
                working = false
            }
        }
    }

    SettingsPageScaffold(title = stringResource(R.string.settings_calendar_export), onBack = onBack) {
        item {
            Text(
                text = stringResource(R.string.calendar_export_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        item { SectionLabel(stringResource(R.string.calendar_export_range)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ExportRange.entries.forEach { option ->
                    SelectionRow(
                        label = stringResource(
                            when (option) {
                                ExportRange.THIS_WEEK -> R.string.calendar_export_this_week
                                ExportRange.FOUR_WEEKS -> R.string.calendar_export_four_weeks
                                ExportRange.EIGHT_WEEKS -> R.string.calendar_export_eight_weeks
                            },
                        ),
                        supporting = null,
                        selected = range == option,
                        onClick = { range = option },
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = { export(open = true) },
                    enabled = !working,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (working) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text(stringResource(R.string.calendar_export_open))
                    }
                }
                OutlinedButton(
                    onClick = { export(open = false) },
                    enabled = !working,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(R.string.calendar_export_share))
                }
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = stringResource(R.string.calendar_export_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Writes the calendar file into the cache and hands it to the calendar app or the share sheet. */
internal object CalendarExporter {
    fun labels(context: Context) = IcsExport.Labels(
        calendarName = context.getString(R.string.app_name),
        examPrefix = context.getString(R.string.calendar_export_exam),
        teacher = context.getString(R.string.calendar_export_teacher),
        className = context.getString(R.string.calendar_export_class),
        substituted = context.getString(R.string.calendar_export_substituted),
    )

    fun write(context: Context, text: String): File {
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        // One file, replaced each time, so old exports do not pile up in the cache.
        return File(directory, "daybreak-timetable.ics").apply { writeText(text, Charsets.UTF_8) }
    }

    /** [open] hands it to a calendar app to import; otherwise the share sheet offers apps and "save". */
    fun send(context: Context, file: File, open: Boolean) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = if (open) {
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "text/calendar") }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/calendar"
                putExtra(Intent.EXTRA_STREAM, uri)
            }
        }.apply {
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val launch = if (open) intent else Intent.createChooser(intent, context.getString(R.string.share_chooser))
        if (context !is android.app.Activity) launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    }
}

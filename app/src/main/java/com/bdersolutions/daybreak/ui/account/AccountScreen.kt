package com.bdersolutions.daybreak.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.bdersolutions.daybreak.untis.UntisAuthException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.session.DaybreakSession
import com.bdersolutions.daybreak.ui.settings.SettingsPageScaffold
import com.bdersolutions.daybreak.ui.toUserMessage
import com.bdersolutions.daybreak.ui.upcoming.EmptyText
import com.bdersolutions.daybreak.ui.upcoming.ErrorBlock
import com.bdersolutions.daybreak.ui.upcoming.ItemCard

/** Who is signed in, and the school's notices of today. */
@Composable
fun AccountScreen(
    session: DaybreakSession,
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val classServices by viewModel.classServices.collectAsStateWithLifecycle()
    val passwordSaved by viewModel.passwordSaved.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Each time the page opens, read the notices again, and the class services when the password
    // is stored. Without it the class services wait for the password to be typed.
    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.loadClassServices(null)
    }

    SettingsPageScaffold(
        title = stringResource(R.string.account_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        item(key = "profile") {
            ItemCard {
                Text(
                    text = session.studentName.ifBlank { session.displayName },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Fact(stringResource(R.string.account_school), session.schoolName.ifBlank { session.school })
                    Fact(stringResource(R.string.account_username), session.username)
                }
            }
        }

        item(key = "messages-title") {
            Text(
                text = stringResource(R.string.account_messages),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
            )
        }
        val error = state.error
        when {
            state.loading -> item(key = "loading") {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            error != null -> item(key = "error") {
                ErrorBlock(message = error.toUserMessage(context), onRetry = viewModel::load)
            }

            state.messages.isEmpty() -> item(key = "empty") { EmptyText(R.string.account_messages_empty) }

            else -> items(state.messages, key = { "msg-${it.id}" }) { message ->
                ItemCard {
                    if (message.subject.isNotBlank()) {
                        Text(
                            text = message.subject,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (message.text.isNotBlank()) {
                        if (message.subject.isNotBlank()) Spacer(Modifier.height(6.dp))
                        Text(message.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        classServicesSection(
            state = classServices,
            passwordSaved = passwordSaved,
            onSubmit = viewModel::loadClassServices,
            onForgetPassword = viewModel::forgetPassword,
        )
    }
}

private fun LazyListScope.classServicesSection(
    state: ClassServicesState,
    passwordSaved: Boolean,
    onSubmit: (String) -> Unit,
    onForgetPassword: () -> Unit,
) {
    item(key = "services-title") {
        Text(
            text = stringResource(R.string.account_class_services),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp),
        )
    }
    when (state) {
        ClassServicesState.Idle, is ClassServicesState.Failed -> item(key = "services-form") {
            ClassServicesForm(
                error = (state as? ClassServicesState.Failed)?.error,
                onSubmit = onSubmit,
            )
        }

        ClassServicesState.Loading -> item(key = "services-loading") {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is ClassServicesState.Loaded -> if (state.services.isEmpty()) {
            item(key = "services-empty") { EmptyText(R.string.class_services_empty) }
        } else {
            items(state.services, key = { "service-${it.id}" }) { service ->
                ItemCard {
                    Text(
                        text = service.service,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (service.student.isNotBlank()) {
                        Text(service.student, style = MaterialTheme.typography.bodyLarge)
                    }
                    val details = listOfNotNull(
                        service.className.ifBlank { null },
                        servicePeriod(service.start, service.end),
                    ).joinToString(" · ")
                    if (details.isNotBlank()) {
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (service.text.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(service.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    if (passwordSaved) {
        item(key = "forget-password") {
            TextButton(onClick = onForgetPassword, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.class_services_forget))
            }
        }
    }
}

private fun servicePeriod(start: LocalDate?, end: LocalDate?): String? {
    val format = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    return when {
        start != null && end != null -> "${format.format(start)} – ${format.format(end)}"
        start != null -> format.format(start)
        end != null -> format.format(end)
        else -> null
    }
}

/**
 * Asks for the password. It lives in plain state, not saved state, so it is not written into the
 * instance state, and it is emptied as soon as it has been handed over.
 */
@Composable
private fun ClassServicesForm(error: Throwable?, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        val value = password
        password = ""
        keyboard?.hide()
        onSubmit(value)
    }
    ItemCard {
        Text(
            text = stringResource(R.string.class_services_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = CircleShape,
            label = { Text(stringResource(R.string.class_services_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (password.isNotBlank()) submit() }),
            isError = error != null,
            supportingText = error?.let { failure ->
                {
                    Text(
                        if (failure is UntisAuthException) {
                            stringResource(R.string.class_services_wrong_password)
                        } else {
                            failure.toUserMessage(context)
                        },
                    )
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { submit() },
            enabled = password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(stringResource(R.string.class_services_show))
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

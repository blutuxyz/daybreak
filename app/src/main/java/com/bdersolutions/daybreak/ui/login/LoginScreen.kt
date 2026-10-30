package com.bdersolutions.daybreak.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.untis.UntisChild
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentLanguage = if (
        context.resources.configuration.locales[0].language.equals("de", ignoreCase = true)
    ) {
        AppLanguage.GERMAN
    } else {
        AppLanguage.ENGLISH
    }
    val switchLanguage = if (currentLanguage == AppLanguage.GERMAN) {
        AppLanguage.ENGLISH
    } else {
        AppLanguage.GERMAN
    }
    val canSubmit = when (state.mode) {
        LoginMode.QR -> state.qrPayload.isNotBlank()
        LoginMode.MANUAL -> state.server.isNotBlank() &&
            state.school.isNotBlank() &&
            state.username.isNotBlank() &&
            state.secret.isNotBlank()

        LoginMode.PASSWORD -> state.server.isNotBlank() &&
            state.school.isNotBlank() &&
            state.username.isNotBlank() &&
            state.password.isNotBlank() &&
            (!state.requiresSecondFactor || state.secondFactor.isNotBlank())
    } && !state.loading

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 28.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { viewModel.setLanguage(switchLanguage) }) {
                    Icon(
                        Icons.Outlined.Language,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = if (switchLanguage == AppLanguage.GERMAN) {
                            stringResource(R.string.language_german)
                        } else {
                            stringResource(R.string.language_english)
                        },
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SunMark()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.login_hero_title),
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.login_hero_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(26.dp))
            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            LoginModeSwitcher(
                mode = state.mode,
                onModeChange = viewModel::onModeChange,
            )

            Spacer(Modifier.height(18.dp))
            when (state.mode) {
                LoginMode.QR -> QrFields(state = state, viewModel = viewModel)
                LoginMode.MANUAL -> ManualFields(state = state, viewModel = viewModel)
                LoginMode.PASSWORD -> PasswordFields(state = state, viewModel = viewModel)
            }

            state.error?.let { failure ->
                Spacer(Modifier.height(14.dp))
                LoginErrorCard(failure = failure, detail = state.errorDetail)
            }

            if (state.mode != LoginMode.QR || state.qrPayload.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = viewModel::submit,
                    enabled = canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                    colors = if (state.mode == LoginMode.QR) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    if (state.loading) {
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                            color = LocalContentColor.current,
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(stringResource(R.string.login_loading))
                    } else {
                        Text(
                            text = stringResource(R.string.login_button),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.login_unofficial),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    state.pendingStudents?.let { students ->
        StudentPickerDialog(
            students = students,
            onSelected = viewModel::selectStudent,
            onCancelled = viewModel::cancelStudentSelection,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SunMark() {
    val shape = MaterialShapes.VerySunny.toShape()
    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

// See ViewModeSwitcher: use the expressive composable-child overload because
// the overflow-aware overload does not accept ToggleButton children directly.
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoginModeSwitcher(
    mode: LoginMode,
    onModeChange: (LoginMode) -> Unit,
) {
    ButtonGroup(modifier = Modifier.fillMaxWidth()) {
        LoginMode.entries.forEach { item ->
            ToggleButton(
                checked = mode == item,
                onCheckedChange = { if (it) onModeChange(item) },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
            ) {
                Text(
                    text = when (item) {
                        LoginMode.QR -> stringResource(R.string.login_tab_qr)
                        LoginMode.MANUAL -> stringResource(R.string.login_tab_manual)
                        LoginMode.PASSWORD -> stringResource(R.string.login_tab_password)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QrFields(
    state: LoginUiState,
    viewModel: LoginViewModel,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { startQrScanner(context, viewModel) },
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = CircleShape,
        ) {
            Icon(
                Icons.Outlined.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.login_scan_qr),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(16.dp),
            )
            Spacer(Modifier.size(8.dp))
            Column {
                Text(
                    text = stringResource(R.string.login_qr_help),
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
                        text = stringResource(R.string.login_webuntis_path),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        OrPasteDivider()
        Spacer(Modifier.height(2.dp))
        LoginField(
            value = state.qrPayload,
            onValueChange = viewModel::onQrPayloadChange,
            label = stringResource(R.string.login_qr_payload),
            placeholder = stringResource(R.string.login_qr_payload_hint),
            enabled = !state.loading,
            singleLine = false,
            minLines = 2,
            maxLines = 4,
            visualTransformation = if (state.showQrSecret) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = viewModel::toggleShowQrSecret) {
                    Icon(
                        if (state.showQrSecret) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = stringResource(R.string.login_toggle_visibility),
                    )
                }
            },
            supportingText = stringResource(R.string.login_qr_manual_hint),
        )
    }
}

@Composable
private fun OrPasteDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        )
        Text(
            text = stringResource(R.string.login_or_paste),
            modifier = Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun ManualFields(
    state: LoginUiState,
    viewModel: LoginViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LoginField(
            value = state.server,
            onValueChange = viewModel::onServerChange,
            label = stringResource(R.string.login_server),
            placeholder = stringResource(R.string.login_server_hint),
            enabled = !state.loading,
            leadingIcon = Icons.Outlined.School,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
        )
        LoginField(
            value = state.school,
            onValueChange = viewModel::onSchoolChange,
            label = stringResource(R.string.login_school),
            enabled = !state.loading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        LoginField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = stringResource(R.string.login_username),
            enabled = !state.loading,
            leadingIcon = Icons.Outlined.Person,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        LoginField(
            value = state.secret,
            onValueChange = viewModel::onSecretChange,
            label = stringResource(R.string.login_secret),
            enabled = !state.loading,
            leadingIcon = Icons.Outlined.Lock,
            visualTransformation = if (state.showSecret) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = viewModel::toggleShowSecret) {
                    Icon(
                        if (state.showSecret) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = stringResource(R.string.login_toggle_visibility),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            supportingText = stringResource(R.string.login_manual_hint),
        )
    }
}

@Composable
private fun PasswordFields(
    state: LoginUiState,
    viewModel: LoginViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LoginField(
            value = state.server,
            onValueChange = viewModel::onServerChange,
            label = stringResource(R.string.login_server),
            placeholder = stringResource(R.string.login_server_hint),
            enabled = !state.loading,
            leadingIcon = Icons.Outlined.School,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
        )
        LoginField(
            value = state.school,
            onValueChange = viewModel::onSchoolChange,
            label = stringResource(R.string.login_school),
            enabled = !state.loading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        LoginField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = stringResource(R.string.login_username),
            enabled = !state.loading,
            leadingIcon = Icons.Outlined.Person,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        LoginField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.login_password),
            enabled = !state.loading,
            leadingIcon = Icons.Outlined.Lock,
            visualTransformation = if (state.showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = viewModel::toggleShowPassword) {
                    Icon(
                        if (state.showPassword) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = stringResource(R.string.login_toggle_visibility),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (state.requiresSecondFactor) ImeAction.Next else ImeAction.Done,
            ),
        )
        if (state.requiresSecondFactor) {
            LoginField(
                value = state.secondFactor,
                onValueChange = viewModel::onSecondFactorChange,
                label = stringResource(R.string.login_second_factor),
                enabled = !state.loading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                supportingText = stringResource(R.string.login_second_factor_hint),
            )
        }
        Text(
            text = stringResource(R.string.login_password_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        placeholder = placeholder?.let { hint -> { Text(hint) } },
        leadingIcon = leadingIcon?.let { icon ->
            { Icon(imageVector = icon, contentDescription = null) }
        },
        trailingIcon = trailingIcon,
        supportingText = supportingText?.let { text -> { Text(text) } },
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun LoginErrorCard(failure: LoginFailure, detail: String?) {
    val text = when (failure) {
        LoginFailure.INVALID_QR -> stringResource(R.string.login_error_invalid_qr)
        LoginFailure.INVALID_INPUT -> stringResource(R.string.login_error_invalid_input)
        LoginFailure.AUTHENTICATION -> stringResource(R.string.error_auth)
        LoginFailure.SECOND_FACTOR -> stringResource(R.string.error_second_factor)
        LoginFailure.NETWORK -> stringResource(R.string.error_network)
        LoginFailure.NO_STUDENT -> stringResource(R.string.error_no_student)
        LoginFailure.STORAGE -> stringResource(R.string.login_error_storage)
        LoginFailure.SCANNER -> stringResource(R.string.login_error_scanner)
        LoginFailure.UNKNOWN -> stringResource(R.string.error_parse)
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(10.dp))
            Column {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (!detail.isNullOrBlank() && failure == LoginFailure.INVALID_QR) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = detail,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentPickerDialog(
    students: List<UntisChild>,
    onSelected: (UntisChild) -> Unit,
    onCancelled: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancelled,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
        title = { Text(stringResource(R.string.login_choose_student_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.login_choose_student_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                students.forEach { student ->
                    TextButton(
                        onClick = { onSelected(student) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = student.displayName,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancelled) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun startQrScanner(context: android.content.Context, viewModel: LoginViewModel) {
    try {
        val scanner: GmsBarcodeScanner = GmsBarcodeScanning.getClient(context)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue
                if (raw.isNullOrBlank()) {
                    viewModel.onScannerFailure(null)
                } else {
                    viewModel.onQrScanned(raw)
                }
            }
            .addOnFailureListener { error ->
                viewModel.onScannerFailure(error.message)
            }
    } catch (error: Exception) {
        viewModel.onScannerFailure(error.message)
    }
}

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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.DialogProperties
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.untis.UntisChild
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = viewModel::toggleLanguage) {
                    Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        if (state.language == AppLanguage.GERMAN) {
                            context.getString(R.string.language_english)
                        } else {
                            context.getString(R.string.language_german)
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(76.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.WbSunny,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = context.getString(R.string.login_hero_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = context.getString(R.string.login_hero_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = context.getString(R.string.login_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = context.getString(R.string.login_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))

                    PrimaryTabRow(
                        selectedTabIndex = state.mode.ordinal,
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier.clip(MaterialTheme.shapes.medium),
                    ) {
                        LoginMode.entries.forEach { mode ->
                            Tab(
                                selected = state.mode == mode,
                                onClick = { viewModel.onModeChange(mode) },
                                text = {
                                    Text(
                                        text = when (mode) {
                                            LoginMode.QR -> context.getString(R.string.login_tab_qr)
                                            LoginMode.MANUAL -> context.getString(R.string.login_tab_manual)
                                            LoginMode.PASSWORD -> context.getString(R.string.login_tab_password)
                                        },
                                    )
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))

                    when (state.mode) {
                        LoginMode.QR -> QrFields(state, viewModel)
                        LoginMode.MANUAL -> ManualFields(state, viewModel)
                        LoginMode.PASSWORD -> PasswordFields(state, viewModel)
                    }

                    state.error?.let { failure ->
                        Spacer(Modifier.height(14.dp))
                        LoginErrorCard(failure = failure, detail = state.errorDetail)
                    }

                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = viewModel::submit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = !state.loading,
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(context.getString(R.string.login_loading))
                        } else {
                            Text(context.getString(R.string.login_button))
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = context.getString(R.string.login_unofficial),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(24.dp))
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

@Composable
private fun QrFields(state: LoginUiState, viewModel: LoginViewModel) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = context.getString(R.string.login_qr_scan_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = context.getString(R.string.login_qr_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { startQrScanner(context, viewModel) },
                    enabled = !state.loading,
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(context.getString(R.string.login_scan_qr))
                }
            }
        }
        OutlinedTextField(
            value = state.qrPayload,
            onValueChange = viewModel::onQrPayloadChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
            label = { Text(context.getString(R.string.login_qr_payload)) },
            placeholder = { Text(context.getString(R.string.login_qr_payload_hint)) },
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
                        if (state.showQrSecret) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = context.getString(R.string.login_toggle_visibility),
                    )
                }
            },
        )
        Text(
            text = context.getString(R.string.login_qr_manual_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ManualFields(state: LoginUiState, viewModel: LoginViewModel) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.server,
            onValueChange = viewModel::onServerChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
            label = { Text(context.getString(R.string.login_server)) },
            placeholder = { Text(context.getString(R.string.login_server_hint)) },
            leadingIcon = { Icon(Icons.Outlined.School, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = state.school,
            onValueChange = viewModel::onSchoolChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
            label = { Text(context.getString(R.string.login_school)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
            label = { Text(context.getString(R.string.login_username)) },
            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = state.secret,
            onValueChange = viewModel::onSecretChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
            label = { Text(context.getString(R.string.login_secret)) },
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            singleLine = true,
            visualTransformation = if (state.showSecret) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = viewModel::toggleShowSecret) {
                    Icon(
                        if (state.showSecret) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = context.getString(R.string.login_toggle_visibility),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Text(
            text = context.getString(R.string.login_manual_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PasswordFields(state: LoginUiState, viewModel: LoginViewModel) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.server,
            onValueChange = viewModel::onServerChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
            label = { Text(context.getString(R.string.login_server)) },
            placeholder = { Text(context.getString(R.string.login_server_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = state.school,
            onValueChange = viewModel::onSchoolChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
            label = { Text(context.getString(R.string.login_school)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
            label = { Text(context.getString(R.string.login_username)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
            label = { Text(context.getString(R.string.login_password)) },
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            singleLine = true,
            visualTransformation = if (state.showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = viewModel::toggleShowPassword) {
                    Icon(
                        if (state.showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = context.getString(R.string.login_toggle_visibility),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (state.requiresSecondFactor) ImeAction.Next else ImeAction.Done,
            ),
        )
        if (state.requiresSecondFactor) {
            OutlinedTextField(
                value = state.secondFactor,
                onValueChange = viewModel::onSecondFactorChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading,
                label = { Text(context.getString(R.string.login_second_factor)) },
                supportingText = { Text(context.getString(R.string.login_second_factor_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
            )
        }
        Text(
            text = context.getString(R.string.login_password_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoginErrorCard(failure: LoginFailure, detail: String?) {
    val context = LocalContext.current
    val text = when (failure) {
        LoginFailure.INVALID_QR -> context.getString(R.string.login_error_invalid_qr)
        LoginFailure.INVALID_INPUT -> context.getString(R.string.login_error_invalid_input)
        LoginFailure.AUTHENTICATION -> context.getString(R.string.error_auth)
        LoginFailure.SECOND_FACTOR -> context.getString(R.string.error_second_factor)
        LoginFailure.NETWORK -> context.getString(R.string.error_network)
        LoginFailure.NO_STUDENT -> context.getString(R.string.error_no_student)
        LoginFailure.STORAGE -> context.getString(R.string.login_error_storage)
        LoginFailure.SCANNER -> context.getString(R.string.login_error_scanner)
        LoginFailure.UNKNOWN -> context.getString(R.string.error_parse)
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
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
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onCancelled,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
        title = { Text(context.getString(R.string.login_choose_student_title)) },
        text = {
            Column {
                Text(
                    text = context.getString(R.string.login_choose_student_body),
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
                Text(context.getString(R.string.action_cancel))
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

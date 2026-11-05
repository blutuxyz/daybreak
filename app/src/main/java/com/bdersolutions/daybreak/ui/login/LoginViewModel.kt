package com.bdersolutions.daybreak.ui.login

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdersolutions.daybreak.AppContainer
import com.bdersolutions.daybreak.data.session.SessionStore
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.data.settings.SettingsRepository
import com.bdersolutions.daybreak.untis.UntisApi
import com.bdersolutions.daybreak.untis.UntisAuthException
import com.bdersolutions.daybreak.untis.UntisChild
import com.bdersolutions.daybreak.untis.UntisLoginResult
import com.bdersolutions.daybreak.untis.UntisNetworkException
import com.bdersolutions.daybreak.untis.UntisNoStudentException
import com.bdersolutions.daybreak.untis.UntisQrParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class LoginMode { QR, MANUAL, PASSWORD }

enum class LoginFailure {
    INVALID_QR,
    INVALID_INPUT,
    AUTHENTICATION,
    SECOND_FACTOR,
    NETWORK,
    NO_STUDENT,
    STORAGE,
    SCANNER,
    UNKNOWN,
}

data class LoginUiState(
    val mode: LoginMode = LoginMode.QR,
    val qrPayload: String = "",
    val server: String = "",
    val school: String = "",
    val username: String = "",
    val secret: String = "",
    val password: String = "",
    val secondFactor: String = "",
    val showQrSecret: Boolean = false,
    val showSecret: Boolean = false,
    val showPassword: Boolean = false,
    val requiresSecondFactor: Boolean = false,
    val loading: Boolean = false,
    val error: LoginFailure? = null,
    val errorDetail: String? = null,
    val pendingStudents: List<UntisChild>? = null,
    val language: AppLanguage = AppLanguage.GERMAN,
)

class LoginViewModel(
    private val api: UntisApi,
    private val sessionStore: SessionStore,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState(language = settingsRepository.current().language))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var pendingLogin: UntisLoginResult? = null

    /** The password of a password login, held only until the login is saved, then stored encrypted. */
    private var pendingPassword: String? = null

    fun onModeChange(mode: LoginMode) {
        _state.value = _state.value.copy(mode = mode, error = null, errorDetail = null)
    }

    fun onQrPayloadChange(value: String) {
        _state.value = _state.value.copy(qrPayload = value, error = null, errorDetail = null)
    }

    fun onServerChange(value: String) {
        _state.value = _state.value.copy(server = value, error = null)
    }

    fun onSchoolChange(value: String) {
        _state.value = _state.value.copy(school = value, error = null)
    }

    fun onUsernameChange(value: String) {
        _state.value = _state.value.copy(username = value, error = null)
    }

    fun onSecretChange(value: String) {
        _state.value = _state.value.copy(secret = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    fun onSecondFactorChange(value: String) {
        _state.value = _state.value.copy(secondFactor = value, error = null)
    }

    fun toggleShowQrSecret() {
        _state.value = _state.value.copy(showQrSecret = !_state.value.showQrSecret)
    }

    fun toggleShowSecret() {
        _state.value = _state.value.copy(showSecret = !_state.value.showSecret)
    }

    fun toggleShowPassword() {
        _state.value = _state.value.copy(showPassword = !_state.value.showPassword)
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null, errorDetail = null)
    }

    fun onQrScanned(raw: String) {
        _state.value = _state.value.copy(qrPayload = raw, error = null, errorDetail = null)
        submit()
    }

    fun onScannerFailure(message: String? = null) {
        _state.value = _state.value.copy(
            error = LoginFailure.SCANNER,
            errorDetail = message,
        )
    }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        when (current.mode) {
            LoginMode.QR -> {
                val payload = current.qrPayload.trim()
                if (payload.isBlank()) {
                    showError(LoginFailure.INVALID_QR)
                    return
                }
                val qr = runCatching { UntisQrParser.parse(payload) }.getOrElse {
                    showError(LoginFailure.INVALID_QR, it.message)
                    return
                }
                _state.value = current.copy(
                    server = qr.server,
                    school = qr.school,
                    username = qr.username,
                    secret = qr.secret,
                )
                launchSecretLogin(qr.server, qr.school, qr.username, qr.secret)
            }

            LoginMode.MANUAL -> {
                if (current.server.isBlank() || current.school.isBlank() ||
                    current.username.isBlank() || current.secret.isBlank()
                ) {
                    showError(LoginFailure.INVALID_INPUT)
                    return
                }
                launchSecretLogin(
                    current.server.trim(),
                    current.school.trim(),
                    current.username.trim(),
                    current.secret.trim(),
                )
            }

            LoginMode.PASSWORD -> {
                if (current.server.isBlank() || current.school.isBlank() ||
                    current.username.isBlank() || current.password.isBlank()
                ) {
                    showError(LoginFailure.INVALID_INPUT)
                    return
                }
                launchPasswordLogin(
                    current.server.trim(),
                    current.school.trim(),
                    current.username.trim(),
                    current.password,
                    current.secondFactor.trim(),
                )
            }
        }
    }

    fun selectStudent(student: UntisChild) {
        val login = pendingLogin ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val saved = withContext(Dispatchers.IO) { sessionStore.saveLogin(login, student, pendingPassword) }
            if (saved) pendingPassword = null
            _state.value = if (saved) {
                clearSensitive(LoginUiState(language = _state.value.language))
            } else {
                _state.value.copy(loading = false, error = LoginFailure.STORAGE)
            }
        }
    }

    fun cancelStudentSelection() {
        pendingLogin = null
        pendingPassword = null
        _state.value = _state.value.copy(pendingStudents = null, loading = false)
    }

    fun toggleLanguage() {
        val next = when (settingsRepository.current().language) {
            AppLanguage.GERMAN -> AppLanguage.ENGLISH
            AppLanguage.ENGLISH -> AppLanguage.GERMAN
        }
        setLanguage(next)
    }

    fun setLanguage(language: AppLanguage) {
        settingsRepository.setLanguage(language)
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(Locale.forLanguageTag(language.tag).toLanguageTag()),
        )
        _state.value = _state.value.copy(language = language)
    }

    private fun launchSecretLogin(server: String, school: String, username: String, secret: String) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, errorDetail = null)
            try {
                pendingPassword = null
                val result = api.loginWithSecret(server, school, username, secret)
                handleLogin(result)
            } catch (error: Throwable) {
                handleError(error)
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    private fun launchPasswordLogin(
        server: String,
        school: String,
        username: String,
        password: String,
        secondFactor: String,
    ) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, errorDetail = null)
            try {
                val result = api.loginWithPassword(server, school, username, password, secondFactor)
                pendingPassword = password
                handleLogin(result)
            } catch (error: Throwable) {
                pendingPassword = null
                handleError(error)
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    private suspend fun handleLogin(result: UntisLoginResult) {
        when (result.children.size) {
            0 -> {
                _state.value = _state.value.copy(error = LoginFailure.NO_STUDENT)
            }

            1 -> {
                val student = result.children.first()
                val saved = withContext(Dispatchers.IO) { sessionStore.saveLogin(result, student, pendingPassword) }
                if (saved) {
                    pendingPassword = null
                    _state.value = clearSensitive(LoginUiState(language = _state.value.language))
                } else {
                    _state.value = _state.value.copy(error = LoginFailure.STORAGE)
                }
            }

            else -> {
                pendingLogin = result
                _state.value = _state.value.copy(
                    pendingStudents = result.children,
                    error = null,
                    errorDetail = null,
                    password = "",
                    secret = "",
                    qrPayload = "",
                )
            }
        }
    }

    private fun handleError(error: Throwable) {
        pendingLogin = null
        val failure = when (error) {
            is UntisAuthException -> {
                if (error.requiresSecondFactor) {
                    _state.value = _state.value.copy(requiresSecondFactor = true)
                    LoginFailure.SECOND_FACTOR
                } else {
                    LoginFailure.AUTHENTICATION
                }
            }
            is UntisNetworkException -> LoginFailure.NETWORK
            is UntisNoStudentException -> LoginFailure.NO_STUDENT
            else -> LoginFailure.UNKNOWN
        }
        _state.value = _state.value.copy(
            error = failure,
            errorDetail = error.message?.takeIf { failure == LoginFailure.UNKNOWN },
            pendingStudents = null,
        )
    }

    private fun showError(failure: LoginFailure, detail: String? = null) {
        _state.value = _state.value.copy(error = failure, errorDetail = detail)
    }

    private fun clearSensitive(base: LoginUiState): LoginUiState = base.copy(
        qrPayload = "",
        secret = "",
        password = "",
        secondFactor = "",
        loading = false,
        error = null,
        errorDetail = null,
        pendingStudents = null,
        requiresSecondFactor = false,
    )

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LoginViewModel::class.java))
            return LoginViewModel(
                api = container.untisApi,
                sessionStore = container.sessionStore,
                settingsRepository = container.settingsRepository,
            ) as T
        }
    }
}

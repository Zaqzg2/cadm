package com.cady.cadymarketing.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.auth.AuthErrors
import com.cady.cadymarketing.data.auth.AuthRepository
import com.cady.cadymarketing.data.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // Pre-fill the last email used, unless the person already started typing.
        viewModelScope.launch {
            val lastEmail = settingsStore.lastEmail.first()
            if (lastEmail.isNotEmpty()) {
                _uiState.update { state ->
                    if (state.email.isEmpty()) state.copy(email = lastEmail) else state
                }
            }
        }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun signIn() {
        val current = _uiState.value
        if (current.isLoading) return
        val email = current.email.trim()
        if (email.isEmpty() || current.password.isEmpty()) {
            _uiState.update { it.copy(error = "أدخل البريد الإلكتروني وكلمة المرور.") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.signIn(email, current.password)
                .onSuccess {
                    settingsStore.setLastEmail(email)
                    // The password never stays in memory after a successful sign-in.
                    _uiState.update { it.copy(isLoading = false, password = "") }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = AuthErrors.message(error)) }
                }
        }
    }
}

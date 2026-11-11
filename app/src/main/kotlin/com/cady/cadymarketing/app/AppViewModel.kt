package com.cady.cadymarketing.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.auth.AuthRepository
import com.cady.cadymarketing.domain.model.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading)

    private val _previewMode = MutableStateFlow(false)

    /** Only reachable while Firebase is not configured: lets the shell be checked without signing in. */
    val previewMode: StateFlow<Boolean> = _previewMode.asStateFlow()

    fun enterPreview() {
        _previewMode.value = true
    }

    fun signOut() {
        _previewMode.value = false
        authRepository.signOut()
    }
}

package com.cady.cadymarketing.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.auth.AuthRepository
import com.cady.cadymarketing.data.notify.BackgroundWork
import com.cady.cadymarketing.data.team.RoleRepository
import com.cady.cadymarketing.domain.model.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val roles: RoleRepository,
    private val background: BackgroundWork,
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
        // The next person to sign in is checked from scratch, and no reminder may reach a phone nobody is signed in on.
        viewModelScope.launch {
            roles.clear()
            background.cancelAll()
        }
    }
}

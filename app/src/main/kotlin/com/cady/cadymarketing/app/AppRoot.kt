package com.cady.cadymarketing.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.domain.model.AuthState
import com.cady.cadymarketing.feature.auth.FirebaseMissingScreen
import com.cady.cadymarketing.feature.auth.LoginScreen

/**
 * The whole app is a function of the auth state: sign in, or the shell. Signing out simply leaves
 * the shell, so nothing from the previous session stays on screen.
 */
@Composable
fun AppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val previewMode by viewModel.previewMode.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (val state = authState) {
            AuthState.Loading -> Unit
            AuthState.NotConfigured -> {
                if (previewMode) {
                    MainShell(email = null, onSignOut = viewModel::signOut)
                } else {
                    FirebaseMissingScreen(onPreview = viewModel::enterPreview)
                }
            }
            AuthState.SignedOut -> LoginScreen()
            is AuthState.SignedIn -> MainShell(email = state.email, onSignOut = viewModel::signOut)
        }
    }
}

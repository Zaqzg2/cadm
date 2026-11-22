package com.cady.cadymarketing.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.core.role.LocalRole
import com.cady.cadymarketing.data.team.RoleState
import com.cady.cadymarketing.domain.model.AuthState
import com.cady.cadymarketing.domain.model.MktRole
import com.cady.cadymarketing.feature.auth.FirebaseMissingScreen
import com.cady.cadymarketing.feature.auth.LoginScreen

/**
 * The whole app is a function of the auth state: sign in, or the shell. Signing out simply leaves
 * the shell, so nothing from the previous session stays on screen. A signed-in account first has its role found out
 * (a saved answer shows at once): the manager and the marketers get the shell, anybody else a clear refusal.
 *
 * [openContentId] is the post a reminder notification asked to open; the shell opens it and calls [onOpenContentHandled].
 */
@Composable
fun AppRoot(
    openContentId: String? = null,
    onOpenContentHandled: () -> Unit = {},
    viewModel: AppViewModel = hiltViewModel(),
) {
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
                    // Preview mode shows everything, as the manager would see it.
                    CompositionLocalProvider(LocalRole provides MktRole.MANAGER) {
                        MainShell(
                            email = null,
                            onSignOut = viewModel::signOut,
                            openContentId = openContentId,
                            onOpenContentHandled = onOpenContentHandled,
                        )
                    }
                } else {
                    FirebaseMissingScreen(onPreview = viewModel::enterPreview)
                }
            }
            AuthState.SignedOut -> LoginScreen()
            is AuthState.SignedIn -> SignedInRoot(
                uid = state.uid,
                email = state.email,
                onSignOut = viewModel::signOut,
                openContentId = openContentId,
                onOpenContentHandled = onOpenContentHandled,
            )
        }
    }
}

@Composable
private fun SignedInRoot(
    uid: String,
    email: String?,
    onSignOut: () -> Unit,
    openContentId: String?,
    onOpenContentHandled: () -> Unit,
    roleViewModel: RoleViewModel = hiltViewModel(),
) {
    LaunchedEffect(uid) { roleViewModel.load(uid) }
    val roleState by roleViewModel.state.collectAsStateWithLifecycle()
    when (val state = roleState) {
        RoleState.Loading -> RoleLoadingScreen()
        is RoleState.Failed -> RoleFailedScreen(message = state.message, onRetry = { roleViewModel.retry(uid) }, onSignOut = onSignOut)
        is RoleState.Ready -> {
            if (state.role == MktRole.NONE) {
                NoAccessScreen(email = email, onRetry = { roleViewModel.retry(uid) }, onSignOut = onSignOut)
            } else {
                CompositionLocalProvider(LocalRole provides state.role) {
                    MainShell(
                        email = email,
                        onSignOut = onSignOut,
                        openContentId = openContentId,
                        onOpenContentHandled = onOpenContentHandled,
                    )
                }
            }
        }
    }
}

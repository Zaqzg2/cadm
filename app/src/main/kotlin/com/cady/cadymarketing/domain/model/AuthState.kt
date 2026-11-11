package com.cady.cadymarketing.domain.model

/** Where the person stands with Firebase Auth. */
sealed interface AuthState {
    /** Waiting for the first answer from Firebase (a few milliseconds). */
    data object Loading : AuthState

    /** app/google-services.json was missing at build time, so Firebase was never initialised. */
    data object NotConfigured : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val uid: String, val email: String?) : AuthState
}

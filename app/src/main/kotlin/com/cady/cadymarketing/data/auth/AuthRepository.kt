package com.cady.cadymarketing.data.auth

import android.content.Context
import com.cady.cadymarketing.domain.model.AuthState
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/**
 * Sign-in with the same Firebase accounts as Kady. Firebase keeps the signed-in user on the device,
 * so after the first sign-in the app opens straight to the shell with no connection at all.
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** False when app/google-services.json was missing at build time. */
    val isConfigured: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    /** Emits at once with the cached user, then again on every sign-in and sign-out. */
    val authState: Flow<AuthState> =
        if (!isConfigured) {
            flowOf(AuthState.NotConfigured)
        } else {
            callbackFlow<AuthState> {
                val auth = FirebaseAuth.getInstance()
                val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                    val user = firebaseAuth.currentUser
                    trySend(
                        if (user == null) {
                            AuthState.SignedOut
                        } else {
                            AuthState.SignedIn(uid = user.uid, email = user.email)
                        },
                    )
                }
                auth.addAuthStateListener(listener)
                awaitClose { auth.removeAuthStateListener(listener) }
            }
        }

    suspend fun signIn(email: String, password: String): Result<Unit> =
        try {
            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    fun signOut() {
        if (isConfigured) FirebaseAuth.getInstance().signOut()
    }
}

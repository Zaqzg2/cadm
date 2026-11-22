package com.cady.cadymarketing.data.team

import com.cady.cadymarketing.data.auth.AuthRepository
import com.cady.cadymarketing.data.cloud.CloudErrors
import com.cady.cadymarketing.data.cloud.CloudTimeoutException
import com.cady.cadymarketing.data.cloud.FirestoreValues
import com.cady.cadymarketing.data.settings.SettingsStore
import com.cady.cadymarketing.domain.engine.RoleRules
import com.cady.cadymarketing.domain.engine.SyncSpecs
import com.cady.cadymarketing.domain.model.MktRole
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Where the check of the signed-in person's role stands. */
sealed interface RoleState {
    data object Loading : RoleState

    data class Ready(val role: MktRole) : RoleState

    /** The role could not be found out and none was saved from an earlier time (usually: no connection on the very first sign-in). */
    data class Failed(val message: String) : RoleState
}

/**
 * Who the signed-in account is for this app: the Kady manager, a marketer the manager listed, or nobody.
 * The answer is kept on the device so the app opens offline; it is re-checked whenever the app opens with a connection,
 * so a marketer the manager switched off loses access at her next opening.
 */
@Singleton
class RoleRepository @Inject constructor(
    private val auth: AuthRepository,
    private val settings: SettingsStore,
) {
    private val _state = MutableStateFlow<RoleState>(RoleState.Loading)
    val state: StateFlow<RoleState> = _state.asStateFlow()

    private val _diagnostics = MutableStateFlow("")

    /** The two documents the last check read, as text for the diagnostics dialog. */
    val diagnostics: StateFlow<String> = _diagnostics.asStateFlow()

    private var loadedFor: String? = null

    /** False in preview mode (no Firebase): nothing is synced and no background check runs. */
    val isFirebaseReady: Boolean get() = auth.isConfigured

    /** The role right now for code that cannot wait. Preview mode (no Firebase) shows everything; no answer yet means no access. */
    fun currentRole(): MktRole = when {
        !auth.isConfigured -> MktRole.MANAGER
        else -> (state.value as? RoleState.Ready)?.role ?: MktRole.NONE
    }

    /** Takes the saved role at once (if there is one for this account), then checks the cloud. */
    suspend fun load(uid: String, force: Boolean = false) {
        if (!force && loadedFor == uid && _state.value is RoleState.Ready) return
        loadedFor = uid
        val cached = parseCache(settings.cachedRole.first(), uid)
        _state.value = if (cached != null) RoleState.Ready(cached) else RoleState.Loading
        val fresh = try {
            Result.success(fetch(uid))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        fresh.onSuccess { role ->
            _state.value = RoleState.Ready(role)
            settings.setCachedRole(uid + SEP + role.code)
        }.onFailure { error ->
            if (cached == null) _state.value = RoleState.Failed(CloudErrors.message(error))
        }
    }

    /** The role for work that has no screen (the periodic check): the saved answer, or a fresh one. */
    suspend fun resolveNow(uid: String): MktRole {
        if (!auth.isConfigured) return MktRole.NONE
        if (loadedFor != uid || _state.value !is RoleState.Ready) load(uid)
        return currentRole()
    }

    /** Forgets the account (sign-out): the next person to sign in is checked from scratch. */
    suspend fun clear() {
        loadedFor = null
        _state.value = RoleState.Loading
        _diagnostics.value = ""
        settings.setCachedRole("")
    }

    private suspend fun fetch(uid: String): MktRole {
        if (!auth.isConfigured) return MktRole.MANAGER
        val db = FirebaseFirestore.getInstance()
        // A marketer has no Kady profile, and Kady's rules may refuse her the read of `users/{uid}`: that only means
        // she is not a Kady account with a role, so the team document decides. Any other failure (no connection...) is real.
        var userNote = ""
        val user = try {
            val userSnap = within { db.collection("users").document(uid).get().await() }
            if (userSnap.exists()) FirestoreValues.plain(userSnap.data) else null
        } catch (e: FirebaseFirestoreException) {
            if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
            userNote = "(القراءة مرفوضة: ليس لهذا الحساب ملف في كادي)"
            null
        }
        var team: Map<String, Any?>? = null
        if (RoleRules.resolve(user, null) != MktRole.MANAGER) {
            val teamSnap = within { db.collection(SyncSpecs.TEAM).document(uid).get().await() }
            team = if (teamSnap.exists()) FirestoreValues.plain(teamSnap.data) else null
        }
        _diagnostics.value = "users/$uid\n" + describe(user) + (if (userNote.isEmpty()) "" else "\n$userNote") +
            "\n\n" + SyncSpecs.TEAM + "/$uid\n" + describe(team)
        return RoleRules.resolve(user, team)
    }

    private fun describe(doc: Map<String, Any?>?): String =
        if (doc == null) "(لا يوجد مستند)" else doc.entries.joinToString("\n") { (key, value) -> "$key = ${value.toString().take(60)}" }

    private fun parseCache(saved: String, uid: String): MktRole? {
        if (!saved.startsWith(uid + SEP)) return null
        val role = MktRole.fromCode(saved.substring(uid.length + SEP.length))
        return role
    }

    private suspend fun <T : Any> within(block: suspend () -> T): T =
        withTimeoutOrNull(TIMEOUT_MS) { block() } ?: throw CloudTimeoutException()

    private companion object {
        const val SEP = "|"
        const val TIMEOUT_MS = 15_000L
    }
}

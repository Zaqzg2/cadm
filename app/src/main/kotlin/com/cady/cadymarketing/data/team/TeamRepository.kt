package com.cady.cadymarketing.data.team

import android.content.Context
import com.cady.cadymarketing.data.auth.AuthRepository
import com.cady.cadymarketing.data.cloud.CloudErrors
import com.cady.cadymarketing.data.cloud.CloudTimeoutException
import com.cady.cadymarketing.data.cloud.CloudUnavailableException
import com.cady.cadymarketing.data.cloud.FirestoreValues
import com.cady.cadymarketing.data.oplog.OpLogRepository
import com.cady.cadymarketing.domain.engine.SyncSpecs
import com.cady.cadymarketing.domain.engine.TeamRules
import com.cady.cadymarketing.domain.model.MktRole
import com.cady.cadymarketing.domain.model.OpAction
import com.cady.cadymarketing.domain.model.TeamMember
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** What the team screen needs to say to the manager when something fails. */
class TeamException(message: String) : Exception(message)

/**
 * The marketing team (`mkt_team/{uid}`), managed by the manager only. A marketer's account is made the way Kady makes a rep's:
 * on a second Firebase app instance, so creating it never signs the manager out. Passwords are never stored by this app:
 * Firebase Auth keeps them.
 */
@Singleton
class TeamRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: AuthRepository,
    private val opLog: OpLogRepository,
) {
    private fun db(): FirebaseFirestore {
        if (!auth.isConfigured) throw CloudUnavailableException("Firebase غير مربوط في هذا البناء")
        return FirebaseFirestore.getInstance()
    }

    suspend fun list(): List<TeamMember> {
        val snapshot = within { db().collection(SyncSpecs.TEAM).get().await() }
        return snapshot.documents
            .mapNotNull { doc -> TeamRules.member(doc.id, FirestoreValues.plain(doc.data)) }
            .sortedBy { it.name }
    }

    /** Makes the account (user name + password) and lists it as an active marketer. Throws [TeamException] with a sentence to show. */
    suspend fun addMarketer(name: String, username: String, password: String): TeamMember {
        TeamRules.nameProblem(name)?.let { throw TeamException(it) }
        TeamRules.usernameProblem(username)?.let { throw TeamException(it) }
        TeamRules.passwordProblem(password)?.let { throw TeamException(it) }
        val firestore = db()
        val email = TeamRules.emailFor(TeamRules.normalizeUsername(username))
        val secondaryAuth = FirebaseAuth.getInstance(secondaryApp())
        val uid = try {
            val created = try {
                within { secondaryAuth.createUserWithEmailAndPassword(email, password).await() }
            } catch (e: FirebaseAuthUserCollisionException) {
                // An earlier attempt made the account but stopped before listing it: reuse it when the password matches.
                within { secondaryAuth.signInWithEmailAndPassword(email, password).await() }
            }
            created.user?.uid ?: throw TeamException("تعذّر إنشاء الحساب.")
        } catch (e: TeamException) {
            throw e
        } catch (e: FirebaseAuthWeakPasswordException) {
            throw TeamException("كلمة المرور ضعيفة. اختر كلمة أطول.")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw TeamException("اسم المستخدم موجود بكلمة مرور أخرى. اختر اسمًا آخر.")
        } catch (e: FirebaseNetworkException) {
            throw TeamException("لا يوجد اتصال بالإنترنت.")
        } catch (e: CloudTimeoutException) {
            throw TeamException(CloudErrors.message(e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw TeamException(CloudErrors.message(e))
        } finally {
            secondaryAuth.signOut()
        }
        val now = System.currentTimeMillis()
        try {
            within {
                firestore.collection(SyncSpecs.TEAM).document(uid).set(TeamRules.memberDoc(name, username, true, now)).await()
                Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw TeamException("أُنشئ الحساب لكن تعذّر إدراجه في الفريق: " + CloudErrors.message(e) + " أعد المحاولة بالاسم نفسه وكلمة المرور نفسها.")
        }
        opLog.record(OpAction.TEAM_ADDED, name.trim(), TeamRules.normalizeUsername(username))
        return TeamMember(uid, name.trim(), TeamRules.normalizeUsername(username), MktRole.MARKETER, true, now)
    }

    /** Switches a marketer on or off. Off, she loses access at her next opening (and her uploads are refused at once). */
    suspend fun setActive(member: TeamMember, active: Boolean) {
        val now = System.currentTimeMillis()
        try {
            within {
                db().collection(SyncSpecs.TEAM).document(member.uid)
                    .set(mapOf("isActive" to active, "updatedAt" to now), SetOptions.merge()).await()
                Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw TeamException(CloudErrors.message(e))
        }
        opLog.record(if (active) OpAction.TEAM_ENABLED else OpAction.TEAM_DISABLED, member.name, member.username)
    }

    private fun secondaryApp(): FirebaseApp =
        try {
            FirebaseApp.getInstance(SECONDARY)
        } catch (e: IllegalStateException) {
            FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, SECONDARY)
        }

    private suspend fun <T : Any> within(block: suspend () -> T): T =
        withTimeoutOrNull(TIMEOUT_MS) { block() } ?: throw CloudTimeoutException()

    private companion object {
        const val SECONDARY = "team"
        const val TIMEOUT_MS = 25_000L
    }
}

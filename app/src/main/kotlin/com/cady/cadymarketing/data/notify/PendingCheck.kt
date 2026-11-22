package com.cady.cadymarketing.data.notify

import com.cady.cadymarketing.data.auth.AuthRepository
import com.cady.cadymarketing.data.content.ContentRepository
import com.cady.cadymarketing.data.settings.SettingsStore
import com.cady.cadymarketing.data.sync.SyncManager
import com.cady.cadymarketing.data.sync.SyncTrigger
import com.cady.cadymarketing.data.team.RoleRepository
import com.cady.cadymarketing.domain.engine.NotificationRules
import com.cady.cadymarketing.domain.model.MktRole
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The check the periodic work runs: sync, then tell the person about what is new for them (posts waiting for the manager,
 * or posts sent back to a marketer). A post is told about once, in the state it is in; if notifications are not allowed the
 * posts are not marked as told, so they are told as soon as the person allows them.
 */
@Singleton
class PendingCheck @Inject constructor(
    private val auth: AuthRepository,
    private val roles: RoleRepository,
    private val sync: SyncManager,
    private val content: ContentRepository,
    private val settings: SettingsStore,
    private val notifier: Notifier,
) {
    suspend fun run() {
        val uid = auth.currentUid() ?: return
        val role = roles.resolveNow(uid)
        if (role == MktRole.NONE) return
        if (!settings.reviewCheckOn.first()) return
        sync.syncNow(SyncTrigger.BACKGROUND)
        val told = settings.notifiedKeys.first()
        val notice = NotificationRules.forRole(role, content.observeAll().first(), told.toSet()) ?: return
        if (notifier.showReview(notice)) {
            settings.setNotifiedKeys(NotificationRules.remember(told, notice.keys))
        }
    }
}

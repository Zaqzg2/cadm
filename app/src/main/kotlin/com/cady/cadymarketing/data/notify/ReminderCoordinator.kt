package com.cady.cadymarketing.data.notify

import com.cady.cadymarketing.data.content.ContentRepository
import com.cady.cadymarketing.data.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the reminders in step with the posts: whenever a post is saved, approved, moved, deleted or arrives from the sync,
 * and whenever the person switches reminders on or off. One place covers every way a post can change.
 */
@Singleton
class ReminderCoordinator @Inject constructor(
    private val content: ContentRepository,
    private val work: BackgroundWork,
    private val settings: SettingsStore,
) {
    private var job: Job? = null

    /** Starts watching. Starting again begins afresh, which also hands the reminders over again after a sign-out and a new sign-in. */
    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            combine(content.observeAll(), settings.remindersOn) { posts, on -> posts to on }
                .collect { (posts, on) -> work.syncReminders(posts, on) }
        }
    }
}

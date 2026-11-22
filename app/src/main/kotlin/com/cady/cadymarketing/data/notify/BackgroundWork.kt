package com.cady.cadymarketing.data.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.cady.cadymarketing.data.settings.SettingsStore
import com.cady.cadymarketing.domain.engine.Reminder
import com.cady.cadymarketing.domain.engine.ReminderRules
import com.cady.cadymarketing.domain.model.SavedContent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * What the app hands to WorkManager: one reminder per approved post that is scheduled ahead, and one periodic check
 * (every 15 minutes, the least Android allows, and only with a connection). WorkManager is started on demand, the first
 * time it is needed, so opening the app stays as quick as before.
 */
@Singleton
class BackgroundWork @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
) {
    private fun manager(): WorkManager = WorkManager.getInstance(context)

    /** Starts the periodic check, or stops it. Starting it again changes nothing while it is already scheduled. */
    fun setReviewCheck(enabled: Boolean) {
        if (!enabled) {
            manager().cancelUniqueWork(CHECK_NAME)
            return
        }
        val request = PeriodicWorkRequest.Builder(PendingCheckWorker::class.java, CHECK_EVERY_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager().enqueueUniquePeriodicWork(CHECK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Makes the scheduled reminders match the posts: new or moved schedules are handed over, and reminders of posts that
     * are no longer approved or scheduled are cancelled. Only the changes are handed over, so it is cheap to call often.
     */
    suspend fun syncReminders(posts: List<SavedContent>, enabled: Boolean) {
        val now = System.currentTimeMillis()
        val desired = if (enabled) ReminderRules.desired(posts, now) else emptyList()
        val previous = settings.scheduledReminders.first()
        val byKey = desired.associateBy { key(it) }
        val work = manager()
        for ((key, reminder) in byKey) {
            if (key !in previous) work.enqueueUniqueWork(ReminderRules.workName(reminder.contentId), ExistingWorkPolicy.REPLACE, request(reminder, now))
        }
        val desiredIds = desired.map { it.contentId }.toSet()
        for (old in previous) {
            val id = old.substringBefore(SEP)
            if (id !in desiredIds) work.cancelUniqueWork(ReminderRules.workName(id))
        }
        if (byKey.keys != previous) settings.setScheduledReminders(byKey.keys)
    }

    /** Stops everything (sign-out): no notification must reach a phone nobody is signed in on. */
    suspend fun cancelAll() {
        val work = manager()
        work.cancelUniqueWork(CHECK_NAME)
        for (old in settings.scheduledReminders.first()) work.cancelUniqueWork(ReminderRules.workName(old.substringBefore(SEP)))
        settings.setScheduledReminders(emptySet())
    }

    private fun request(reminder: Reminder, now: Long): OneTimeWorkRequest =
        OneTimeWorkRequest.Builder(ReminderWorker::class.java)
            .setInitialDelay(ReminderRules.delayMillis(reminder, now), TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(ReminderWorker.KEY_ID, reminder.contentId)
                    .putString(ReminderWorker.KEY_AT, reminder.scheduledAt)
                    .build(),
            )
            .build()

    private fun key(reminder: Reminder): String = reminder.contentId + SEP + reminder.scheduledAt

    private companion object {
        const val CHECK_NAME = "review-check"
        const val CHECK_EVERY_MINUTES = 15L
        const val SEP = "|"
    }
}

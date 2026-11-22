package com.cady.cadymarketing.data.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cady.cadymarketing.domain.engine.ReminderRules
import dagger.hilt.android.EntryPointAccessors
import kotlin.coroutines.cancellation.CancellationException

/** Shows the reminder of one approved post, if the post still matches the schedule the reminder was made for. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        val scheduledAt = inputData.getString(KEY_AT) ?: return Result.success()
        return try {
            val deps = EntryPointAccessors.fromApplication(applicationContext, BackgroundEntryPoint::class.java)
            val post = deps.contentRepository().get(id)
            if (post != null && ReminderRules.stillDue(post, scheduledAt)) {
                deps.notifier().showReminder(id, "«${post.productName}» · ${post.channel.label}")
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.success()
        }
    }

    companion object {
        const val KEY_ID = "content_id"
        const val KEY_AT = "scheduled_at"
    }
}

/** The check every 15 minutes: sync, then tell the person about what is new for them. A failure waits for the next turn. */
class PendingCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            EntryPointAccessors.fromApplication(applicationContext, BackgroundEntryPoint::class.java).pendingCheck().run()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.success()
        }
    }
}

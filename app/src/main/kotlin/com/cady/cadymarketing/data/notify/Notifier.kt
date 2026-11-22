package com.cady.cadymarketing.data.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cady.cadymarketing.MainActivity
import com.cady.cadymarketing.R
import com.cady.cadymarketing.domain.engine.Notice
import com.cady.cadymarketing.domain.engine.ReminderRules
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Shows the app's notifications. Nothing is shown unless the person allowed notifications; a notice not shown is not lost (it is told later). */
@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** True when notifications may be shown (on Android 13 and later this needs the permission the person grants in Settings). */
    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** True when the system still asks for the permission (Android 13 and later, not granted yet). */
    fun needsPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** A post is due now. Tapping it opens the post in the Studio. */
    @SuppressLint("MissingPermission")
    fun showReminder(contentId: String, text: String): Boolean {
        if (!canNotify()) return false
        ensureChannels()
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(ReminderRules.TITLE)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openIntent(contentId))
            .build()
        NotificationManagerCompat.from(context).notify(contentId.hashCode(), notification)
        return true
    }

    /** Posts wait for the manager, or came back to the marketer. Tapping it opens the library. */
    @SuppressLint("MissingPermission")
    fun showReview(notice: Notice): Boolean {
        if (!canNotify()) return false
        ensureChannels()
        val notification = NotificationCompat.Builder(context, CHANNEL_REVIEW)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(notice.title)
            .setContentText(notice.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openIntent(null))
            .build()
        NotificationManagerCompat.from(context).notify(REVIEW_ID, notification)
        return true
    }

    private fun openIntent(contentId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (contentId != null) putExtra(MainActivity.EXTRA_OPEN_CONTENT, contentId)
        }
        val requestCode = contentId?.hashCode() ?: REVIEW_ID
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "مواعيد النشر", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تنبيه عند حلول موعد نشر منشور معتمد"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REVIEW, "الاعتماد والإرجاع", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "منشورات تنتظر اعتماد المدير، أو أُرجعت إلى المسوّق للتعديل"
            },
        )
    }

    private companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_REVIEW = "review"
        const val REVIEW_ID = 7001
    }
}

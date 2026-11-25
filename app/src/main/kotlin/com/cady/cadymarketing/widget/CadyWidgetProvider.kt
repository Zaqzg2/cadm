package com.cady.cadymarketing.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.cady.cadymarketing.MainActivity
import com.cady.cadymarketing.R
import com.cady.cadymarketing.domain.engine.DateUtil
import com.cady.cadymarketing.domain.engine.LaunchActions
import com.cady.cadymarketing.domain.engine.WidgetSummary
import com.cady.cadymarketing.domain.engine.WidgetSummaryEngine
import com.cady.cadymarketing.domain.model.MktRole
import dagger.hilt.android.EntryPointAccessors
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The home-screen widget: today's numbers (drafts, waiting for approval, scheduled this week), the next post that
 * has a time, and three buttons that open the Studio on a new post, the idea of the day, or the calendar.
 *
 * It is drawn with plain RemoteViews (no extra library), in the glass look: a translucent card whose colours follow
 * the phone's light / dark setting. It refreshes every half hour, whenever the app is left (see MainActivity), and the
 * buttons reach the app through the same actions the launcher shortcuts use.
 */
class CadyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // The data comes from the database, so the work is done off the main thread; goAsync keeps the receiver alive for it.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val views = WidgetRenderer.render(context.applicationContext)
                appWidgetManager.updateAppWidget(appWidgetIds, views)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep what is on screen: a widget that fails to refresh must never crash the app.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Asks every placed widget to redraw itself. Does nothing when no widget is placed. */
        fun refresh(context: Context) {
            try {
                val app = context.applicationContext
                val manager = AppWidgetManager.getInstance(app)
                val ids = manager.getAppWidgetIds(ComponentName(app, CadyWidgetProvider::class.java))
                if (ids.isEmpty()) return
                val intent = Intent(app, CadyWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                app.sendBroadcast(intent)
            } catch (e: Exception) {
                // Nothing to refresh is not an error.
            }
        }
    }
}

/** Builds the widget's RemoteViews from what is stored. */
internal object WidgetRenderer {

    suspend fun render(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        wireButtons(context, views)

        val deps = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val roles = deps.roleRepository()
        val cachedRole = deps.settingsStore().cachedRole.first()
        // Preview mode (no Firebase) shows everything, like the app; otherwise the account must have a usable saved role.
        val signedIn = !roles.isFirebaseReady || (cachedRole.isNotBlank() && !cachedRole.endsWith("|" + MktRole.NONE.code))
        if (!signedIn) {
            showSignedOut(context, views)
            return views
        }

        val content = deps.contentRepository().observeAll().first()
        val campaigns = deps.campaignRepository().observeAll().first()
        val summary = WidgetSummaryEngine.compute(content, campaigns, DateUtil.today(), DateUtil.nowMinute())
        show(context, views, summary)
        return views
    }

    private fun show(context: Context, views: RemoteViews, summary: WidgetSummary) {
        val next = summary.next
        val subtitle = when {
            next != null -> context.getString(R.string.widget_next, next.title, next.whenLabel)
            summary.late > 0 -> context.getString(R.string.widget_late, summary.late.toString())
            else -> summary.dateLabel
        }
        views.setTextViewText(R.id.widget_subtitle, subtitle)
        views.setTextViewText(R.id.widget_stat_a_value, summary.drafts.toString())
        views.setTextViewText(R.id.widget_stat_b_value, summary.pending.toString())
        views.setTextViewText(R.id.widget_stat_c_value, summary.scheduledNextWeek.toString())
    }

    private fun showSignedOut(context: Context, views: RemoteViews) {
        views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_sign_in))
        views.setTextViewText(R.id.widget_stat_a_value, DASH)
        views.setTextViewText(R.id.widget_stat_b_value, DASH)
        views.setTextViewText(R.id.widget_stat_c_value, DASH)
    }

    /** Every tap opens the app through one of the actions the launcher shortcuts use, so a request made before sign-in waits for it. */
    private fun wireButtons(context: Context, views: RemoteViews) {
        views.setOnClickPendingIntent(R.id.widget_root, open(context, LaunchActions.OPEN_HOME, REQUEST_HOME))
        views.setOnClickPendingIntent(R.id.widget_chip_a, open(context, LaunchActions.OPEN_LIBRARY, REQUEST_LIBRARY))
        views.setOnClickPendingIntent(R.id.widget_chip_b, open(context, LaunchActions.OPEN_LIBRARY, REQUEST_LIBRARY))
        views.setOnClickPendingIntent(R.id.widget_chip_c, open(context, LaunchActions.OPEN_CALENDAR, REQUEST_CALENDAR))
        views.setOnClickPendingIntent(R.id.widget_action_new, open(context, LaunchActions.NEW_POST, REQUEST_NEW_POST))
        views.setOnClickPendingIntent(R.id.widget_action_idea, open(context, LaunchActions.IDEA_OF_DAY, REQUEST_IDEA))
        views.setOnClickPendingIntent(R.id.widget_action_calendar, open(context, LaunchActions.OPEN_CALENDAR, REQUEST_CALENDAR))
    }

    private fun open(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private const val DASH = "—"
    private const val REQUEST_HOME = 7101
    private const val REQUEST_LIBRARY = 7102
    private const val REQUEST_CALENDAR = 7103
    private const val REQUEST_NEW_POST = 7104
    private const val REQUEST_IDEA = 7105
}

package com.hcwebhook.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home screen widget: last sync time and summary, a sync button, and a tap to open the app. */
class SyncStatusWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        /** Redraws every placed widget. Call after a sync saves its status. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SyncStatusWidget::class.java))
            if (ids.isEmpty()) return
            val views = views(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun views(context: Context): RemoteViews {
            val prefs = PreferencesManager(context)
            val views = RemoteViews(context.packageName, R.layout.widget_sync_status)
            views.setTextViewText(R.id.widget_status, SyncStatusText.lastSync(context))
            views.setTextViewText(R.id.widget_summary, prefs.getLastSyncSummary().orEmpty())

            val syncIntent = Intent(context, SyncTriggerReceiver::class.java).apply {
                action = SyncTrigger.ACTION_SYNC
                putExtra(SyncTrigger.EXTRA_SOURCE, "widget")
            }
            views.setOnClickPendingIntent(
                R.id.widget_sync,
                PendingIntent.getBroadcast(context, 0, syncIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (openIntent != null) {
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(context, 1, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
            }
            return views
        }
    }
}

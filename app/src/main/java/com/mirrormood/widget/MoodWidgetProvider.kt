package com.mirrormood.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mirrormood.MainActivity
import com.mirrormood.R
import com.mirrormood.data.db.MoodDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MoodWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_mood)

            // Launch app on tap
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

            // Load data async
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = MoodDatabase.getDatabase(context)
                    val latestEntry = db.moodDao().getLatestEntry().first()

                    val emoji = when (latestEntry?.mood) {
                        "Happy" -> "😊"
                        "Stressed" -> "😰"
                        "Tired" -> "😴"
                        "Focused" -> "🧠"
                        "Bored" -> "😒"
                        "Neutral" -> "😐"
                        else -> "🪞"
                    }
                    val label = latestEntry?.mood ?: "No data"

                    views.setTextViewText(R.id.tvWidgetEmoji, emoji)
                    views.setTextViewText(R.id.tvWidgetMood, label)
                    views.setTextViewText(R.id.tvWidgetLabel, "MirrorMood")

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    views.setTextViewText(R.id.tvWidgetEmoji, "🪞")
                    views.setTextViewText(R.id.tvWidgetMood, "Tap to open")
                    views.setTextViewText(R.id.tvWidgetLabel, "MirrorMood")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        /**
         * Convenience method to refresh all active MoodMood widget instances.
         * Call this after saving a new mood entry.
         */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, MoodWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (id in widgetIds) {
                updateWidget(context, appWidgetManager, id)
            }
        }
    }
}

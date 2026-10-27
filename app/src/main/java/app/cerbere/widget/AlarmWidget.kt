package app.cerbere.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.cerbere.CerbereApp
import app.cerbere.MainActivity
import app.cerbere.R
import app.cerbere.service.ActionReceiver
import app.cerbere.somfy.AlarmState
import app.cerbere.somfy.Level
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Home-screen widget: the alarm state on a background of its color, and the three states as
 * buttons. Redrawn by the repository on every change, so it follows the app, the keypad and
 * other phones within a second while the watch runs.
 */
class AlarmWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as CerbereApp
        update(context, app.alarm.state.value)
        // A placed widget also refreshes on Android's own schedule (30 min); a cached read is fine.
        val pending = goAsync()
        app.scope.launch {
            try {
                app.alarm.refresh()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private data class Look(val background: Int, val icon: Int, val title: String)

        private fun look(state: AlarmState): Look = when (state.pending ?: state.level) {
            Level.DISARMED -> Look(R.drawable.widget_bg_disarmed, R.drawable.ic_unlock, "Désactivée")
            Level.PARTIAL -> Look(R.drawable.widget_bg_night, R.drawable.ic_moon, "Mode nuit")
            Level.ARMED -> Look(R.drawable.widget_bg_armed, R.drawable.ic_shield, "Activée")
            null -> Look(R.drawable.widget_bg_unknown, R.drawable.ic_shield, if (state.loggedIn) "État inconnu" else "Connecte-toi")
        }

        private val BUTTONS = listOf(
            Triple(Level.DISARMED, R.id.btn_off, R.id.txt_off),
            Triple(Level.PARTIAL, R.id.btn_night, R.id.txt_night),
            Triple(Level.ARMED, R.id.btn_on, R.id.txt_on),
        )

        fun update(context: Context, state: AlarmState) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AlarmWidget::class.java))
            if (ids.isEmpty()) return
            val look = look(state)
            val views = RemoteViews(context.packageName, R.layout.widget_alarm)
            views.setInt(R.id.root, "setBackgroundResource", look.background)
            views.setImageViewResource(R.id.icon, look.icon)
            views.setTextViewText(R.id.title, look.title)
            views.setTextViewText(
                R.id.subtitle,
                when {
                    state.pending != null -> "Envoi à Somfy…"
                    state.error != null && state.loggedIn -> state.error
                    state.updatedAt > 0 -> "${state.siteName.ifBlank { "Cerbère" }} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.updatedAt))}"
                    else -> "Cerbère"
                },
            )
            views.setOnClickPendingIntent(
                R.id.header,
                PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            val current = state.pending ?: state.level
            BUTTONS.forEach { (level, button, text) ->
                val selected = level == current
                views.setInt(button, "setBackgroundResource", if (selected) R.drawable.widget_pill_on else R.drawable.widget_pill_off)
                views.setTextColor(text, context.getColor(if (selected) R.color.widget_on_pill else R.color.widget_text))
                if (state.loggedIn) views.setOnClickPendingIntent(button, ActionReceiver.intent(context, level))
            }
            manager.updateAppWidget(ids, views)
        }
    }
}

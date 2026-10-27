package app.cerbere.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
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
 * Home-screen widget, "les trois têtes": the logo's three dogs are the three states (Off, Nuit,
 * Totale) and the buttons; the active one lights up and the card takes the state's color. Redrawn by the repository on every
 * change, so it follows the app, the keypad and other phones within a second while the watch runs.
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
        private class Dog(val level: Level, val column: Int, val glow: Int, val on: Int, val off: Int, val label: Int)

        private val DOGS = listOf(
            Dog(Level.DISARMED, R.id.col_off, R.id.glow_off, R.id.head_on_off, R.id.head_off_off, R.id.label_off),
            Dog(Level.PARTIAL, R.id.col_night, R.id.glow_night, R.id.head_on_night, R.id.head_off_night, R.id.label_night),
            Dog(Level.ARMED, R.id.col_on, R.id.glow_on, R.id.head_on_on, R.id.head_off_on, R.id.label_on),
        )

        fun update(context: Context, state: AlarmState) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AlarmWidget::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.widget_alarm)
            val current = state.pending ?: state.level
            views.setInt(
                R.id.root, "setBackgroundResource",
                when (current.takeIf { state.loggedIn }) {
                    Level.DISARMED -> R.drawable.widget_bg_off
                    Level.PARTIAL -> R.drawable.widget_bg_night
                    Level.ARMED -> R.drawable.widget_bg_on
                    null -> R.drawable.widget_bg_unknown
                },
            )
            DOGS.forEach { dog ->
                val lit = state.loggedIn && dog.level == current
                views.setViewVisibility(dog.glow, if (lit) View.VISIBLE else View.GONE)
                views.setViewVisibility(dog.on, if (lit) View.VISIBLE else View.GONE)
                views.setViewVisibility(dog.off, if (lit) View.GONE else View.VISIBLE)
                views.setTextColor(dog.label, context.getColor(if (lit) R.color.widget_text else R.color.widget_dim))
                views.setTextViewTextSize(dog.label, TypedValue.COMPLEX_UNIT_SP, if (lit) 13f else 12f)
                if (state.loggedIn) views.setOnClickPendingIntent(dog.column, ActionReceiver.intent(context, dog.level))
            }
            views.setTextViewText(
                R.id.subtitle,
                when {
                    !state.loggedIn -> "Connecte-toi dans Cerbère"
                    state.pending != null -> "Envoi à Somfy…"
                    state.error != null -> state.error
                    state.updatedAt > 0 -> "${current?.label ?: "Cerbère"} · ${state.siteName.ifBlank { "Maison" }} · " +
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.updatedAt))
                    else -> "Cerbère"
                },
            )
            views.setOnClickPendingIntent(
                R.id.subtitle,
                PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            manager.updateAppWidget(ids, views)
        }
    }
}

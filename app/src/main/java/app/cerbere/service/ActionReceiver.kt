package app.cerbere.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.cerbere.CerbereApp
import app.cerbere.somfy.Level
import kotlinx.coroutines.launch

/** "Désactiver" in the notification, and the widget's three buttons: sends the level to Somfy. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val level = Level.of(intent.getStringExtra(EXTRA_LEVEL)) ?: return
        val app = context.applicationContext as CerbereApp
        if (app.alarm.state.value.level == level) return
        val pending = goAsync()
        app.scope.launch {
            try {
                app.alarm.set(level)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION = "app.cerbere.action.SET_LEVEL"
        private const val EXTRA_LEVEL = "level"

        fun intent(context: Context, level: Level): PendingIntent = PendingIntent.getBroadcast(
            context, level.ordinal,
            Intent(context, ActionReceiver::class.java).setAction(ACTION).putExtra(EXTRA_LEVEL, level.raw),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

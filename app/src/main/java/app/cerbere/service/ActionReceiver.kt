package app.cerbere.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.cerbere.CerbereApp
import app.cerbere.somfy.Level
import kotlinx.coroutines.launch

/**
 * "Désactiver" in the notification and the widget's buttons send the level to Somfy; a swiped-away
 * notification is put back while the alarm is on.
 */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REPOST) {
            // Swiped away while armed or in night mode: it comes straight back.
            Notifications.show(context, (context.applicationContext as CerbereApp).alarm.state.value)
            return
        }
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
        private const val ACTION_REPOST = "app.cerbere.action.REPOST"
        private const val EXTRA_LEVEL = "level"

        fun repost(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 10, Intent(context, ActionReceiver::class.java).setAction(ACTION_REPOST), PendingIntent.FLAG_IMMUTABLE,
        )

        fun intent(context: Context, level: Level): PendingIntent = PendingIntent.getBroadcast(
            context, level.ordinal,
            Intent(context, ActionReceiver::class.java).setAction(ACTION).putExtra(EXTRA_LEVEL, level.raw),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

package app.cerbere.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.cerbere.MainActivity
import app.cerbere.R
import app.cerbere.somfy.AlarmState
import app.cerbere.somfy.Level

/**
 * The guard service's notification, which follows the alarm: discreet while disarmed or fully armed,
 * prominent (its own channel, a "Désactiver" button) while night mode is on, so it is not forgotten
 * in the morning.
 */
object Notifications {
    const val ID = 1
    const val REMINDER_ID = 2
    private const val CH_GUARD = "guard"
    private const val CH_NIGHT = "night"
    const val CH_REMINDER = "reminder"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_GUARD, "Veille", NotificationManager.IMPORTANCE_MIN).apply {
                description = "État de l'alarme quand le mode nuit est coupé"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_NIGHT, "Mode nuit activé", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reste affichée tant que le mode nuit est activé"
                setSound(null, null)
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDER, "Rappel au déverrouillage", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Demande de désactiver l'alarme quand le téléphone est déverrouillé en mode nuit"
            }
        )
    }

    fun build(context: Context, state: AlarmState): Notification {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val night = state.level == Level.PARTIAL
        val builder = NotificationCompat.Builder(context, if (night) CH_NIGHT else CH_GUARD)
            .setSmallIcon(R.drawable.ic_stat_cerbere)
            .setColor(context.getColor(if (night) R.color.night else R.color.accent))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        when {
            state.pending != null -> builder.setContentTitle("${state.pending.label}…").setContentText("Envoi à Somfy")
            night -> builder.setContentTitle("Mode nuit activé")
                .setContentText("Pense à désactiver l'alarme avant de sortir.")
                .addAction(0, "Désactiver", ActionReceiver.intent(context, Level.DISARMED))
            else -> builder.setContentTitle("Alarme ${state.level?.label?.lowercase() ?: "inconnue"}")
                .setContentText(if (state.live) "Cerbère veille" else "Cerbère veille (connexion en cours)")
        }
        return builder.build()
    }

    fun show(context: Context, state: AlarmState) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!state.loggedIn) {
            nm.cancel(ID)
            nm.cancel(REMINDER_ID)
            return
        }
        if (state.level != Level.PARTIAL) nm.cancel(REMINDER_ID)
        runCatching { nm.notify(ID, build(context, state)) } // missing notification permission: nothing to show
    }
}

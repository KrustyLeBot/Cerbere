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
 * The guard service's notification, which follows the alarm:
 * - armed or night mode: on its own channel, with a "Désactiver" button, and put back at once if
 *   swiped away (Android 14+ lets the user dismiss any notification, even ongoing ones);
 * - disarmed: the minimum Android requires for the watch to run, on a silent channel without a
 *   status-bar icon, which can be hidden entirely in Android's settings ([OFF_CHANNEL]).
 */
object Notifications {
    /**
     * One id per kind: an update moved to the disarmed channel, when the user turned that channel off,
     * would be dropped and leave the "alarm on" notification showing. With two ids the old one is
     * cancelled for real.
     */
    const val ID_ACTIVE = 1
    const val REMINDER_ID = 2
    const val ID_OFF = 3
    const val OFF_CHANNEL = "guard"
    private const val CH_ACTIVE = "active"
    const val CH_REMINDER = "reminder"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("night") // replaced by CH_ACTIVE (armed too)
        nm.createNotificationChannel(
            NotificationChannel(OFF_CHANNEL, "Alarme désactivée", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Veille discrète quand l'alarme est désactivée. Peut être masquée sans arrêter Cerbère."
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ACTIVE, "Alarme activée ou mode nuit", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reste affichée tant que l'alarme est activée ou en mode nuit"
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

    /** Whether the user hid the disarmed-state notification in Android's settings. */
    fun offHidden(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(OFF_CHANNEL)?.importance ==
            NotificationManager.IMPORTANCE_NONE

    fun offChannelSettings(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, OFF_CHANNEL)

    fun build(context: Context, state: AlarmState): Notification {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val level = state.pending ?: state.level
        val active = isActive(state)
        val builder = NotificationCompat.Builder(context, if (active) CH_ACTIVE else OFF_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_cerbere)
            .setColor(context.getColor(if (level == Level.PARTIAL) R.color.night else R.color.accent))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        if (active) builder.setDeleteIntent(ActionReceiver.repost(context))
        when {
            state.pending != null -> builder.setContentTitle("${state.pending.label}…").setContentText("Envoi à Somfy")
            level == Level.PARTIAL -> builder.setContentTitle("Mode nuit activé")
                .setContentText("Pense à désactiver l'alarme avant de sortir.")
                .addAction(0, "Désactiver", ActionReceiver.intent(context, Level.DISARMED))
            level == Level.ARMED -> builder.setContentTitle("Alarme activée")
                .setContentText("La maison est sous surveillance.")
                .addAction(0, "Désactiver", ActionReceiver.intent(context, Level.DISARMED))
            else -> builder.setContentTitle("Alarme ${level?.label?.lowercase() ?: "inconnue"}")
                .setContentText(if (state.live) "Cerbère veille" else "Cerbère veille (connexion en cours)")
        }
        return builder.build()
    }

    fun isActive(state: AlarmState): Boolean = (state.pending ?: state.level).let { it == Level.PARTIAL || it == Level.ARMED }

    fun idFor(state: AlarmState) = if (isActive(state)) ID_ACTIVE else ID_OFF

    fun show(context: Context, state: AlarmState) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!state.loggedIn) {
            nm.cancel(ID_ACTIVE)
            nm.cancel(ID_OFF)
            nm.cancel(REMINDER_ID)
            return
        }
        if (state.level != Level.PARTIAL) nm.cancel(REMINDER_ID)
        // While the guard service runs it owns the notification (moving it to the right id); otherwise post it here.
        if (GuardService.showForeground(state)) return
        val id = idFor(state)
        runCatching { nm.notify(id, build(context, state)) } // missing notification permission: nothing to show
        nm.cancel(if (id == ID_ACTIVE) ID_OFF else ID_ACTIVE)
    }
}

package app.cerbere.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import app.cerbere.R
import app.cerbere.ui.ReminderActivity

/**
 * The full-screen "disarm?" prompt shown when the phone is unlocked in night mode. Opening a screen
 * from the background is only allowed with "display over other apps"; without it a heads-up
 * notification carries the same prompt (full screen on a locked phone when Android allows it).
 */
object Reminder {
    private const val PREFS = "reminder"
    private const val KEY_ENABLED = "enabled"

    fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()

    fun canShowFullScreen(context: Context) = Settings.canDrawOverlays(context)

    fun overlaySettings(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun show(context: Context) {
        val screen = Intent(context, ReminderActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        if (canShowFullScreen(context) && runCatching { context.startActivity(screen) }.isSuccess) return
        val tap = PendingIntent.getActivity(context, 1, screen, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, Notifications.CH_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_cerbere)
            .setColor(context.getColor(R.color.night))
            .setContentTitle("Mode nuit toujours activé")
            .setContentText("Désactive l'alarme avant d'ouvrir la porte.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tap)
            .setFullScreenIntent(tap, true)
            .setAutoCancel(true)
            .addAction(0, "Désactiver", ActionReceiver.intent(context, app.cerbere.somfy.Level.DISARMED))
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(Notifications.REMINDER_ID, notification) }
    }
}

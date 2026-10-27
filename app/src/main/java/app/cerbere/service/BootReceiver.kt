package app.cerbere.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.cerbere.CerbereApp

/** Starts the watch after a reboot or an update of the app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if ((context.applicationContext as CerbereApp).alarm.state.value.loggedIn) GuardService.start(context)
    }
}

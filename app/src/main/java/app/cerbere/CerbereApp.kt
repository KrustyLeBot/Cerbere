package app.cerbere

import android.app.Application
import app.cerbere.security.Vault
import app.cerbere.service.GuardService
import app.cerbere.service.Notifications
import app.cerbere.somfy.AlarmRepository
import app.cerbere.somfy.SomfyApi
import app.cerbere.suite.SuiteUpdater
import app.cerbere.widget.AlarmWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class CerbereApp : Application() {
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
    val api by lazy { SomfyApi(http, Vault(this)) }
    val alarm by lazy {
        AlarmRepository(this, api) { state ->
            AlarmWidget.update(this, state)
            Notifications.show(this, state)
        }
    }
    val updater by lazy { SuiteUpdater(this, "KrustyLeBot/Cerbere", "$packageName.fileprovider") }

    /** For work that outlives a screen: widget taps, notification actions, the reminder. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        if (alarm.state.value.loggedIn) GuardService.start(this)
    }
}

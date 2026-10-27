package app.cerbere.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.util.Log
import app.cerbere.CerbereApp
import app.cerbere.somfy.Level
import app.cerbere.somfy.LoginRequired
import app.cerbere.somfy.SomfyApi
import app.cerbere.somfy.SomfyJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Keeps watch while logged in, as a foreground service (its notification is the one that says
 * whether night mode is on):
 * - Somfy's live event websocket, the one its own app uses, brings every change in about a second;
 * - a read every [POLL_MS] is the safety net, in case an event was missed while the socket was down;
 * - each time the phone is unlocked in night mode, the reminder to disarm comes up.
 * Reconnects back off from 5 s to a minute: a failure that lasts is usually Somfy being unhappy, and
 * hammering it then is how an account gets banned.
 */
class GuardService : Service() {
    private val app get() = application as CerbereApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var idleJob: Job? = null
    private var attempts = 0

    private val unlocked = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (app.alarm.state.value.level == Level.PARTIAL && Reminder.enabled(context)) Reminder.show(context)
        }
    }

    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(n: Network) {
            // Back online: no need to wait for the backoff.
            scope.launch {
                if (socket == null) {
                    attempts = 0
                    reconnectJob?.cancel()
                    connect()
                }
                app.alarm.refresh()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val notification = Notifications.build(this, app.alarm.state.value)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifications.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.ID, notification)
        }
        registerReceiver(unlocked, IntentFilter(Intent.ACTION_USER_PRESENT))
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(network)
        scope.launch {
            while (isActive) {
                app.alarm.refresh()
                delay(POLL_MS)
            }
        }
        scope.launch { connect() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!app.alarm.state.value.loggedIn) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(unlocked)
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(network) }
        socket?.close(1000, null)
        socket = null
        app.alarm.setLive(false)
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun connect(forceRefresh: Boolean = false) {
        if (socket != null || !app.alarm.state.value.loggedIn) return
        val token = try {
            app.api.accessToken(forceRefresh)
        } catch (e: LoginRequired) {
            app.alarm.refresh() // shows the error; nothing to retry until the user logs in again
            return
        } catch (e: Exception) {
            scheduleReconnect()
            return
        }
        // The token rides in the URL, so the URL is a credential: never logged.
        val request = Request.Builder().url("${SomfyApi.WEBSOCKET}?token=${URLEncoder.encode(token, "UTF-8")}").build()
        socket = app.http.newBuilder().pingInterval(15, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()
            .newWebSocket(request, Listener())
    }

    private fun scheduleReconnect(forceRefresh: Boolean = false) {
        if (reconnectJob?.isActive == true) return
        val wait = (RECONNECT_MIN_MS shl attempts.coerceAtMost(4)).coerceAtMost(RECONNECT_MAX_MS)
        attempts++
        reconnectJob = scope.launch {
            delay(wait)
            connect(forceRefresh)
        }
    }

    /** The server can go silent instead of closing a stale connection; a quiet house is quiet too. */
    private fun armIdle(ws: WebSocket) {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_MS)
            ws.cancel()
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            scope.launch {
                attempts = 0
                app.alarm.setLive(true)
                armIdle(webSocket)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch {
                armIdle(webSocket)
                if (SomfyJson.isTokenError(text)) {
                    // Expired token: the server says so instead of closing. Refresh, then reconnect.
                    webSocket.cancel()
                    dropped(webSocket, forceRefresh = true)
                    return@launch
                }
                val (site, level) = SomfyJson.levelChange(text) ?: return@launch
                if (site == app.api.siteId) app.alarm.pushed(level)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            scope.launch { dropped(webSocket, forceRefresh = code == 3000) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "event stream failed: ${t.javaClass.simpleName}")
            scope.launch { dropped(webSocket, forceRefresh = response?.code == 401) }
        }
    }

    private fun dropped(ws: WebSocket, forceRefresh: Boolean) {
        if (socket !== ws) return
        socket = null
        idleJob?.cancel()
        app.alarm.setLive(false)
        scheduleReconnect(forceRefresh)
    }

    companion object {
        private const val TAG = "Cerbere"
        private const val POLL_MS = 15 * 60_000L
        private const val IDLE_MS = 30 * 60_000L
        private const val RECONNECT_MIN_MS = 5_000L
        private const val RECONNECT_MAX_MS = 60_000L

        fun start(context: Context) {
            // Not allowed from the background on recent Android (a widget tap waking the process, say):
            // the next app opening or reboot starts it then.
            runCatching { context.startForegroundService(Intent(context, GuardService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GuardService::class.java))
        }
    }
}

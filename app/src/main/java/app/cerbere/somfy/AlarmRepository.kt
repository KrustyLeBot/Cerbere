package app.cerbere.somfy

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * What the app, the widget, the notification and the reminder show. [level] is the last one known
 * (kept across restarts, so the widget never starts blank); [pending] is a change sent but not yet
 * confirmed.
 */
data class AlarmState(
    val level: Level? = null,
    val siteName: String = "",
    val updatedAt: Long = 0,
    val pending: Level? = null,
    val live: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

class AlarmRepository(context: Context, private val api: SomfyApi, private val onChange: (AlarmState) -> Unit) {
    private val prefs = context.getSharedPreferences("alarm", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        AlarmState(
            level = Level.of(prefs.getString(KEY_LEVEL, null)),
            siteName = api.siteName.orEmpty(),
            updatedAt = prefs.getLong(KEY_AT, 0),
            loggedIn = api.loggedIn && api.siteId != null,
        )
    )
    val state: StateFlow<AlarmState> = _state

    private fun publish(transform: (AlarmState) -> AlarmState) {
        _state.update(transform)
        val s = _state.value
        prefs.edit().putString(KEY_LEVEL, s.level?.raw).putLong(KEY_AT, s.updatedAt).apply()
        onChange(s)
    }

    /** Reads the level from Somfy ([force]: the user asked, a cached answer of a few seconds is fine). */
    suspend fun refresh(force: Boolean = false) {
        if (!_state.value.loggedIn) return
        try {
            val site = api.site(force)
            publish { it.copy(level = site.level ?: it.level, siteName = site.name, updatedAt = System.currentTimeMillis(), error = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LoginRequired) {
            publish { it.copy(error = e.message) }
        } catch (e: Exception) {
            publish { it.copy(error = describe(e)) }
        }
    }

    /** Sends a level; true once Somfy accepted it. */
    suspend fun set(level: Level): Boolean {
        if (!_state.value.loggedIn || _state.value.pending != null) return false
        publish { it.copy(pending = level, error = null) }
        return try {
            api.setLevel(level)
            publish { it.copy(level = level, pending = null, updatedAt = System.currentTimeMillis()) }
            true
        } catch (e: CancellationException) {
            publish { it.copy(pending = null) }
            throw e
        } catch (e: Exception) {
            publish { it.copy(pending = null, error = describe(e)) }
            false
        }
    }

    /** A change pushed by the websocket (made from here, the keypad, another phone…). */
    fun pushed(level: Level) {
        api.pushed(level)
        publish { it.copy(level = level, updatedAt = System.currentTimeMillis(), error = null) }
    }

    fun setLive(live: Boolean) {
        if (_state.value.live != live) publish { it.copy(live = live) }
    }

    fun loggedIn(site: Site) {
        api.chooseSite(site)
        publish { AlarmState(level = site.level, siteName = site.name, updatedAt = System.currentTimeMillis(), loggedIn = true) }
    }

    fun logout() {
        api.logout()
        prefs.edit().clear().apply()
        publish { AlarmState() }
    }

    private fun describe(e: Exception): String = when (e) {
        is SomfyException -> e.message ?: "Erreur Somfy"
        is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.net.ConnectException -> "Pas de connexion internet"
        else -> e.message ?: e.javaClass.simpleName
    }

    private companion object {
        const val KEY_LEVEL = "level"
        const val KEY_AT = "updated_at"
    }
}

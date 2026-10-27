package app.cerbere

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cerbere.service.GuardService
import app.cerbere.somfy.Level
import app.cerbere.somfy.Site
import app.cerbere.somfy.SomfyException
import app.cerbere.suite.SuiteUpdates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CerbereApp

    val alarm = app.alarm.state
    val updates = SuiteUpdates(app, app.updater, viewModelScope)

    var showSettings by mutableStateOf(false)

    // --- login ---
    var loggingIn by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set
    /** Several installations on the account: the user picks one. */
    var sitesToPick by mutableStateOf<List<Site>>(emptyList())
        private set

    val email: String get() = app.api.email.orEmpty()

    fun login(email: String, password: String) {
        if (loggingIn) return
        loggingIn = true
        loginError = null
        viewModelScope.launch {
            try {
                val sites = app.api.login(email.trim(), password)
                when (sites.size) {
                    0 -> loginError = "Aucune installation sur ce compte"
                    1 -> choose(sites.first())
                    else -> sitesToPick = sites
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SomfyException) {
                loginError = e.message
            } catch (e: Exception) {
                loginError = "Connexion impossible : vérifie internet (${e.javaClass.simpleName})"
            } finally {
                loggingIn = false
            }
        }
    }

    fun choose(site: Site) {
        sitesToPick = emptyList()
        app.alarm.loggedIn(site)
        GuardService.start(app)
    }

    fun logout() {
        GuardService.stop(app)
        app.alarm.logout()
        showSettings = false
    }

    // --- alarm ---
    /** Survives the screen: a change sent just before leaving the app still goes through. */
    fun set(level: Level) {
        app.scope.launch { app.alarm.set(level) }
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch { app.alarm.refresh(force) }
    }

    fun onOpen() {
        if (alarm.value.loggedIn) {
            GuardService.start(app)
            refresh()
        }
        updates.checkOnOpen()
    }
}

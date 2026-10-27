package app.cerbere

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.cerbere.ui.CerbereTheme
import app.cerbere.ui.HomeScreen
import app.cerbere.ui.LoginScreen
import app.cerbere.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CerbereTheme {
                val state by vm.alarm.collectAsState()
                BackHandler(enabled = vm.showSettings) { vm.showSettings = false }
                when {
                    !state.loggedIn -> LoginScreen(vm)
                    vm.showSettings -> SettingsScreen(vm, state)
                    else -> HomeScreen(vm, state)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onOpen()
    }
}

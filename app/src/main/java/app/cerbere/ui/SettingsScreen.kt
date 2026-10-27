package app.cerbere.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.cerbere.MainViewModel
import app.cerbere.service.Notifications
import app.cerbere.service.Reminder
import app.cerbere.somfy.AlarmState
import app.cerbere.suite.AboutSettings
import app.cerbere.suite.ConfirmDialog
import app.cerbere.suite.SectionTitle
import app.cerbere.suite.SettingsCard
import app.cerbere.suite.SettingsHint
import app.cerbere.suite.SuiteTopBar
import app.cerbere.suite.UpdateSettings

@Composable
fun SettingsScreen(vm: MainViewModel, state: AlarmState) {
    val context = LocalContext.current
    var confirmLogout by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    var reminder by remember { mutableStateOf(Reminder.enabled(context)) }
    val overlayOk = remember(tick) { Reminder.canShowFullScreen(context) }
    val offHidden = remember(tick) { Notifications.offHidden(context) }
    val batteryOk = remember(tick) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    Scaffold(topBar = { SuiteTopBar("Paramètres", onBack = { vm.showSettings = false }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle("Compte Somfy")
            SettingsCard {
                Text(vm.email.ifBlank { "Connecté" }, fontWeight = FontWeight.SemiBold)
                SettingsHint("Installation : ${state.siteName.ifBlank { "—" }}")
                SettingsHint(if (state.live) "Connexion temps réel active" else "Connexion temps réel en cours d'ouverture")
                OutlinedButton(onClick = { confirmLogout = true }) { Text("Se déconnecter") }
            }

            SectionTitle("Rappel du mode nuit")
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Au déverrouillage du téléphone", fontWeight = FontWeight.SemiBold)
                        SettingsHint("En mode nuit, propose de désactiver l'alarme chaque fois que le téléphone est déverrouillé.")
                    }
                    Switch(reminder, { reminder = it; Reminder.setEnabled(context, it) })
                }
                if (reminder) {
                    SettingsHint(
                        if (overlayOk) "Affichage plein écran autorisé ✓"
                        else "Sans « affichage par-dessus les autres applis », le rappel arrive en notification.",
                    )
                    if (!overlayOk) OutlinedButton(onClick = { context.startActivity(Reminder.overlaySettings(context)) }) { Text("Autoriser le plein écran") }
                }
                SettingsHint("Tant que le mode nuit est activé, une notification reste affichée avec un bouton « Désactiver ».")
            }

            SectionTitle("Notification")
            SettingsCard {
                Text("Alarme activée ou mode nuit", fontWeight = FontWeight.SemiBold)
                SettingsHint("Une notification reste affichée, avec un bouton « Désactiver ». Balayée, elle revient.")
                Text("Alarme désactivée", fontWeight = FontWeight.SemiBold)
                SettingsHint(
                    if (offHidden) "Aucune notification ✓"
                    else "Android impose une notification discrète pour que Cerbère veille (sans icône dans la barre d'état). Tu peux la masquer : Cerbère continue de veiller.",
                )
                if (!offHidden) OutlinedButton(onClick = { context.startActivity(Notifications.offChannelSettings(context)) }) { Text("Masquer quand l'alarme est désactivée") }
            }

            SectionTitle("Arrière-plan")
            SettingsCard {
                Text("Connexion permanente à l'alarme", fontWeight = FontWeight.SemiBold)
                SettingsHint(
                    "Cerbère garde la connexion temps réel de Somfy (celle de son app) et relit l'état toutes les 15 minutes au plus, " +
                        "pour ne jamais trop solliciter Somfy.",
                )
                if (batteryOk) SettingsHint("Exclu de l'optimisation de batterie ✓")
                else OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                }) { Text("Exclure de l'optimisation") }
            }

            UpdateSettings(vm.updates)
            AboutSettings(
                "Cerbère",
                "Contrôle de l'alarme Somfy Protect par l'API de l'app officielle (non documentée par Somfy : elle peut changer sans prévenir).",
            )
            Spacer(Modifier.height(32.dp))
        }
    }
    if (confirmLogout) {
        ConfirmDialog(
            "Se déconnecter ?", "Le widget et le rappel du mode nuit s'arrêtent jusqu'à la prochaine connexion.", "Se déconnecter",
            onConfirm = { vm.logout() }, onDismiss = { confirmLogout = false }, destructive = true,
        )
    }
}

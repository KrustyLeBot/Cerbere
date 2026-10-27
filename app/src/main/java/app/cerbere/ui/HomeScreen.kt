package app.cerbere.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.cerbere.MainViewModel
import app.cerbere.service.Reminder
import app.cerbere.somfy.AlarmState
import app.cerbere.somfy.Level
import app.cerbere.suite.SuiteBanner
import app.cerbere.suite.SuiteTopBar
import app.cerbere.suite.Tone
import app.cerbere.suite.UpdateBanner
import kotlinx.coroutines.delay

fun Level?.icon(): ImageVector = when (this) {
    Level.DISARMED -> Icons.Default.LockOpen
    Level.PARTIAL -> Icons.Default.Bedtime
    Level.ARMED, null -> Icons.Default.Shield
}

@Composable
fun HomeScreen(vm: MainViewModel, state: AlarmState) {
    Scaffold(
        topBar = {
            SuiteTopBar("Cerbère") {
                IconButton(onClick = { vm.refresh(force = true) }) { Icon(Icons.Default.Refresh, "Actualiser") }
                IconButton(onClick = { vm.showSettings = true }) { Icon(Icons.Default.Settings, "Paramètres") }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            UpdateBanner(vm.updates)
            state.error?.let { SuiteBanner(it, tone = Tone.Error, icon = Icons.Default.ErrorOutline) }
            PermissionBanners()
            Hero(state, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Text(
                "Changer l'état",
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Level.entries.forEach { level ->
                    StateButton(level, state, Modifier.weight(1f)) { vm.set(level) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Hero(state: AlarmState, modifier: Modifier = Modifier) {
    val shown = state.pending ?: state.level
    val (top, bottom) = levelColors(shown)
    val start by animateColorAsState(top, tween(500), label = "top")
    val end by animateColorAsState(bottom, tween(500), label = "bottom")
    Box(
        modifier.fillMaxWidth().height(240.dp).clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(start, end))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), Alignment.Center) {
                if (state.pending != null) {
                    CircularProgressIndicator(Modifier.size(56.dp), color = Color.White, strokeWidth = 3.dp)
                }
                Icon(shown.icon(), null, Modifier.size(48.dp), tint = Color.White)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                when {
                    state.pending != null -> "${state.pending.label}…"
                    shown != null -> "Alarme ${shown.label.lowercase()}"
                    else -> "État inconnu"
                },
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (state.live) Color(0xFF7CF5B3) else Color.White.copy(alpha = 0.5f)))
                Spacer(Modifier.width(6.dp))
                Text(subtitle(state), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun subtitle(state: AlarmState): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val site = state.siteName.ifBlank { "Maison" }
    if (state.pending != null) return "$site · envoi à Somfy"
    if (state.updatedAt == 0L) return site
    val minutes = (now - state.updatedAt) / 60_000
    val ago = when {
        state.live -> "en direct"
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a $minutes min"
        else -> "il y a ${minutes / 60} h"
    }
    return "$site · $ago"
}

@Composable
private fun StateButton(level: Level, state: AlarmState, modifier: Modifier, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val selected = (state.pending ?: state.level) == level
    val (accent, _) = levelColors(level)
    val container by animateColorAsState(if (selected) accent else MaterialTheme.colorScheme.surfaceContainerHigh, label = "button")
    val content = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    Surface(
        color = container, contentColor = content, shape = MaterialTheme.shapes.large,
        modifier = modifier.height(104.dp).clip(MaterialTheme.shapes.large).clickable(enabled = state.pending == null && !selected) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(level.icon(), null, Modifier.size(30.dp), tint = if (selected) Color.White else accent)
            Spacer(Modifier.height(8.dp))
            Text(
                when (level) {
                    Level.DISARMED -> "Désactiver"
                    Level.PARTIAL -> "Mode nuit"
                    Level.ARMED -> "Activer"
                },
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** What the reminder and the notification need, asked for once, in context. */
@Composable
private fun PermissionBanners() {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    // Re-read each time the screen comes back: the permissions are granted in Android's settings.
    val notificationsOk = remember(tick) {
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val overlayOk = remember(tick) { Reminder.canShowFullScreen(context) }
    val batteryOk = remember(tick) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    if (!notificationsOk) {
        SuiteBanner("Pour afficher le mode nuit en permanence et le rappel du matin.", tone = Tone.Accent, title = "Notifications", icon = Icons.Default.Notifications) {
            TextButton(onClick = { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Autoriser") }
        }
    }
    if (!overlayOk && Reminder.enabled(context)) {
        SuiteBanner("Autorise « affichage par-dessus » pour le rappel plein écran au déverrouillage.", tone = Tone.Accent, title = "Rappel du matin", icon = Icons.Default.Smartphone) {
            TextButton(onClick = { context.startActivity(Reminder.overlaySettings(context)) }) { Text("Autoriser") }
        }
    }
    if (!batteryOk) {
        SuiteBanner("Exclus Cerbère de l'optimisation de batterie pour rester connecté à l'alarme.", title = "Arrière-plan") {
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
            }) { Text("Exclure") }
        }
    }
}

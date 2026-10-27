package app.cerbere.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cerbere.CerbereApp
import app.cerbere.somfy.Level
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Full-screen prompt shown at unlock while night mode is on: disarm in one tap, or keep it. */
class ReminderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CerbereApp
        setContent {
            CerbereTheme {
                val state by app.alarm.state.collectAsState()
                var done by remember { mutableStateOf(false) }
                // Disarmed from somewhere else (keypad, other phone) while this was open: nothing left to ask.
                LaunchedEffect(state.level, state.pending) {
                    if (!done && state.pending == null && state.level != Level.PARTIAL) finish()
                }
                Reminder(
                    sending = state.pending != null,
                    done = done,
                    error = state.error.takeIf { state.pending == null && !done },
                    onDisarm = {
                        app.scope.launch {
                            if (app.alarm.set(Level.DISARMED)) {
                                done = true
                                delay(1200)
                                finish()
                            }
                        }
                    },
                    onKeep = ::finish,
                )
            }
        }
    }
}

@Composable
private fun Reminder(sending: Boolean, done: Boolean, error: String?, onDisarm: () -> Unit, onKeep: () -> Unit) {
    val (top, bottom) = levelColors(if (done) Level.DISARMED else Level.PARTIAL)
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        1f, 1.12f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "scale",
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom))).safeDrawingPadding().padding(28.dp)) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.size(140.dp).scale(if (done) 1f else pulse).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
                Alignment.Center,
            ) {
                Icon(if (done) Icons.Default.CheckCircle else Icons.Default.Bedtime, null, Modifier.size(72.dp), tint = Color.White)
            }
            Text(
                if (done) "Alarme désactivée" else "Mode nuit activé",
                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White,
            )
            Text(
                if (done) "Bonne journée !" else "Désactive l'alarme avant d'ouvrir une porte ou une fenêtre.",
                style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
            )
            error?.let { Text(it, color = Color(0xFFFFD6DB), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
        }
        if (!done) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onDisarm, enabled = !sending,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = bottom),
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) {
                    if (sending) CircularProgressIndicator(Modifier.size(24.dp), color = bottom, strokeWidth = 3.dp)
                    else Text("Désactiver l'alarme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onKeep) { Text("Garder le mode nuit", color = Color.White) }
            }
        }
    }
}

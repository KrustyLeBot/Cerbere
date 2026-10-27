package app.cerbere.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cerbere.MainViewModel
import app.cerbere.R
import app.cerbere.suite.SettingsCard
import app.cerbere.suite.SettingsHint
import app.cerbere.suite.UpdateBanner

@Composable
fun Logo(size: Int) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.28f).dp))
            .background(Brush.linearGradient(listOf(Color(0xFFF25C6E), Color(0xFF8E1A2E)))),
        Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size((size * 1.5f).dp))
    }
}

@Composable
fun LoginScreen(vm: MainViewModel) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            UpdateBanner(vm.updates, inset = false) // updates must work before any login
            Logo(96)
            Text("Cerbère", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                "Ton alarme Somfy Protect, en un geste.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                email, { email = it }, label = { Text("Email du compte Somfy") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                password, { password = it }, label = { Text("Mot de passe") }, singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (visible) "Masquer" else "Afficher")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            vm.loginError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(
                onClick = { vm.login(email, password) },
                enabled = !vm.loggingIn && email.contains("@") && password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                if (vm.loggingIn) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Se connecter")
            }
            SettingsCard {
                Text("Conseil : un compte invité", fontWeight = FontWeight.SemiBold)
                SettingsHint(
                    "Dans l'app Somfy Protect, ajoute un utilisateur invité dédié à Cerbère et connecte-toi avec lui. " +
                        "Un invité peut activer et désactiver l'alarme, mais pas modifier l'installation ni les utilisateurs.",
                )
                SettingsHint("Le mot de passe reste chiffré sur ce téléphone et ne sert qu'à Somfy.")
            }
        }
    }
    if (vm.sitesToPick.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Quelle installation ?") },
            text = {
                Column {
                    vm.sitesToPick.forEach { site ->
                        TextButton(onClick = { vm.choose(site) }, modifier = Modifier.fillMaxWidth()) {
                            Text(site.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

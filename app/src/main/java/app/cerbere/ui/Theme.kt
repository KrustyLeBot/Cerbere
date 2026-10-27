package app.cerbere.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.cerbere.somfy.Level

@Composable
fun CerbereTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) SuiteDarkColors else SuiteLightColors,
        shapes = SuiteShapes,
        content = content,
    )
}

/** The colors of each state, the same in the app, the widget and the reminder. */
fun levelColors(level: Level?): Pair<Color, Color> = when (level) {
    Level.DISARMED -> Color(0xFF2BB39A) to Color(0xFF16695E)
    Level.PARTIAL -> Color(0xFF5B5FE0) to Color(0xFF2A2C7C)
    Level.ARMED -> Color(0xFFE5485E) to Color(0xFF8E1A2E)
    null -> Color(0xFF5A5D66) to Color(0xFF2E3036)
}

// Generated: the same recipe in every app of the suite (Synchrotron, Griffonote, Ecoutify, Météorite).
package app.cerbere.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Fixed brand colors (never the wallpaper's), from the app accent #D93A4F: Material's tonal-spot
 * scheme for surfaces and containers, the accent at full strength for `primary`.
 */
internal val SuiteLightColors = lightColorScheme(
    primary = Color(0xFFB61E39),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFCACAE),
    onPrimaryContainer = Color(0xFF622C2F),
    inversePrimary = Color(0xFFFFB3B5),
    secondary = Color(0xFF775758),
    onSecondary = Color(0xFFFFF7F6),
    secondaryContainer = Color(0xFFFFDADA),
    onSecondaryContainer = Color(0xFF694A4B),
    tertiary = Color(0xFF7A5272),
    onTertiary = Color(0xFFFFF7F9),
    tertiaryContainer = Color(0xFFFECBF1),
    onTertiaryContainer = Color(0xFF66405F),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF3D2F2F),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF3D2F2F),
    surfaceVariant = Color(0xFFF5DDDD),
    onSurfaceVariant = Color(0xFF6C5B5B),
    surfaceTint = Color(0xFFB71E39),
    inverseSurface = Color(0xFF130C0D),
    inverseOnSurface = Color(0xFFA79A9A),
    error = Color(0xFFA73B21),
    onError = Color(0xFFFFF7F6),
    errorContainer = Color(0xFFFD795A),
    onErrorContainer = Color(0xFF6E1400),
    outline = Color(0xFF897676),
    outlineVariant = Color(0xFFC3ADAD),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F7),
    surfaceContainer = Color(0xFFFEE9E9),
    surfaceContainerHigh = Color(0xFFF9E3E3),
    surfaceContainerHighest = Color(0xFFF5DDDD),
    surfaceContainerLow = Color(0xFFFFF0F0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFECD5D4),
)

internal val SuiteDarkColors = darkColorScheme(
    primary = Color(0xFFFFB3B5),
    onPrimary = Color(0xFF680018),
    primaryContainer = Color(0xFF764547),
    onPrimaryContainer = Color(0xFFFFDBDB),
    inversePrimary = Color(0xFFB71E39),
    secondary = Color(0xFFE6BDBD),
    onSecondary = Color(0xFF553939),
    secondaryContainer = Color(0xFF503435),
    onSecondaryContainer = Color(0xFFDEB5B6),
    tertiary = Color(0xFFFFDFF5),
    onTertiary = Color(0xFF6F4868),
    tertiaryContainer = Color(0xFFFECBF1),
    onTertiaryContainer = Color(0xFF66405F),
    background = Color(0xFF130C0D),
    onBackground = Color(0xFFF8E0E0),
    surface = Color(0xFF130C0D),
    onSurface = Color(0xFFF8E0E0),
    surfaceVariant = Color(0xFF302223),
    onSurfaceVariant = Color(0xFFBCA6A6),
    surfaceTint = Color(0xFFFFB3B5),
    inverseSurface = Color(0xFFFFF8F7),
    inverseOnSurface = Color(0xFF5D5352),
    error = Color(0xFFF97758),
    onError = Color(0xFF450900),
    errorContainer = Color(0xFF85230A),
    onErrorContainer = Color(0xFFFF9B82),
    outline = Color(0xFF847171),
    outlineVariant = Color(0xFF544444),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF372929),
    surfaceContainer = Color(0xFF211717),
    surfaceContainerHigh = Color(0xFF281D1D),
    surfaceContainerHighest = Color(0xFF302223),
    surfaceContainerLow = Color(0xFF1A1111),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceDim = Color(0xFF130C0D),
)

/** Rounded shapes shared by the whole suite. */
internal val SuiteShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

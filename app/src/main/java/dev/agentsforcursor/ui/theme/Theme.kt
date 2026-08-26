package dev.agentsforcursor.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val Violet = Color(0xFF8B6CFF)
private val VioletDark = Color(0xFF5B3FD6)
private val Ink = Color(0xFF0B0B0F)
private val InkElevated = Color(0xFF14141C)
private val InkCard = Color(0xFF1A1A24)
private val Outline = Color(0xFF2E2E3C)
private val Mist = Color(0xFFF4F3F8)

val StatusRunning = Color(0xFF6FA8FF)
val StatusIdle = Color(0xFF8C8CA3)
val StatusSuccess = Color(0xFF4CC38A)
val StatusFailure = Color(0xFFFF6B6B)
val StatusArchived = Color(0xFF6B6B7B)

private val DarkColors = darkColorScheme(
    primary = Violet,
    onPrimary = Color(0xFF120C2B),
    primaryContainer = VioletDark,
    onPrimaryContainer = Mist,
    secondary = Color(0xFF9FA0B5),
    onSecondary = Ink,
    background = Ink,
    onBackground = Mist,
    surface = Ink,
    onSurface = Mist,
    surfaceVariant = InkCard,
    onSurfaceVariant = Color(0xFFB6B6C7),
    surfaceContainer = InkElevated,
    surfaceContainerHigh = InkCard,
    surfaceContainerHighest = Color(0xFF20202C),
    outline = Outline,
    outlineVariant = Color(0xFF24242F),
    error = StatusFailure,
    onError = Color(0xFF2B0B0B),
)

private val LightColors = lightColorScheme(
    primary = VioletDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E0FF),
    onPrimaryContainer = Color(0xFF1B1240),
    secondary = Color(0xFF5A5A72),
    background = Color(0xFFFBFAFF),
    onBackground = Color(0xFF15151C),
    surface = Color(0xFFFBFAFF),
    onSurface = Color(0xFF15151C),
    surfaceVariant = Color(0xFFEFEDF6),
    onSurfaceVariant = Color(0xFF4A4A5C),
    outline = Color(0xFFC9C7D6),
    error = Color(0xFFB3261E),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
    ),
)

/** Monospace for branch names, tool calls, and token counts. */
val MonoStyle: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 18.sp,
)

val CardCorner = 16.dp

@Composable
fun AgentsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalContext.current as? Activity

    if (view != null) {
        SideEffect {
            WindowCompat.getInsetsController(view.window, view.window.decorView)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}

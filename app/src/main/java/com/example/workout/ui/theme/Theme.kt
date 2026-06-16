package dev.wwade.workout.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = SlateBlue,
    secondary = Moss,
    tertiary = Clay,
    background = Sand,
    surface = ColorTokens.surface,
    onPrimary = ColorTokens.onPrimary,
    onSecondary = ColorTokens.onSecondary,
    onTertiary = ColorTokens.onTertiary,
    onBackground = Ink,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Mist,
    secondary = ColorTokens.darkSecondary,
    tertiary = Clay,
    background = ColorTokens.darkBackground,
    surface = ColorTokens.darkSurface,
    onPrimary = Ink,
    onSecondary = Ink,
    onTertiary = Ink,
    onBackground = Mist,
    onSurface = Mist,
)

@Composable
fun WorkoutTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content,
    )
}

private object ColorTokens {
    val surface = Mist
    val onPrimary = Sand
    val onSecondary = Sand
    val onTertiary = Sand
    val darkBackground = Ink
    val darkSurface = SlateBlue
    val darkSecondary = Moss
}

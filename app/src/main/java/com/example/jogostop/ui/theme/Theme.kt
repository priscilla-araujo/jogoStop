package com.example.jogostop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Blue,
    secondary = Mint,
    tertiary = BlueDark,
    background = BgLight,
    surface = SurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFF121318),
    onSurface = Color(0xFF121318),
)

private val DarkColors = darkColorScheme(
    primary = Blue,
    secondary = Mint,
    tertiary = BlueDark,
    background = BgDark,
    surface = SurfaceDark,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFFEAEAF0),
    onSurface = Color(0xFFEAEAF0),
)

@Composable
fun JogoStopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}

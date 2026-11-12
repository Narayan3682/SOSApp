package com.homepager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Calm dark slate with one loud emergency red, so the SOS button is the only thing that shouts. */
object Palette {
    val Sos = Color(0xFFE53935)
    val SosDeep = Color(0xFFB71C1C)
    val Ok = Color(0xFF2EBD85)
    val Warn = Color(0xFFF5A623)
    val Night = Color(0xFF0F1419)
    val NightCard = Color(0xFF1A2129)
    val NightLine = Color(0xFF2A333E)
    val Day = Color(0xFFF6F7F9)
    val DayCard = Color(0xFFFFFFFF)
}

private val Dark = darkColorScheme(
    primary = Palette.Sos, onPrimary = Color.White,
    secondary = Palette.Ok, onSecondary = Color.Black,
    tertiary = Palette.Warn,
    background = Palette.Night, onBackground = Color(0xFFE8ECF1),
    surface = Palette.NightCard, onSurface = Color(0xFFE8ECF1),
    surfaceVariant = Palette.NightLine, onSurfaceVariant = Color(0xFF9AA6B2),
    outline = Palette.NightLine, error = Palette.Sos,
)
private val Light = lightColorScheme(
    primary = Palette.SosDeep, onPrimary = Color.White,
    secondary = Palette.Ok, onSecondary = Color.White,
    tertiary = Palette.Warn,
    background = Palette.Day, onBackground = Color(0xFF14181D),
    surface = Palette.DayCard, onSurface = Color(0xFF14181D),
    surfaceVariant = Color(0xFFE6E9EE), onSurfaceVariant = Color(0xFF5B6673),
    outline = Color(0xFFD5DAE1), error = Palette.SosDeep,
)

@Composable
fun PagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

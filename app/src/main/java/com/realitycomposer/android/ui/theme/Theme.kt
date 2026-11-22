package com.realitycomposer.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object RcColors {
    val Background = Color(0xFF121214)
    val Surface = Color(0xFF1C1C1E)
    val SurfaceElevated = Color(0xFF2C2C2E)
    val SurfaceTranslucent = Color(0xE6222226)
    val Border = Color(0xFF3A3A3C)
    val AccentBlue = Color(0xFF0A84FF)
    val PlayGreen = Color(0xFF30D158)
    val WarningOrange = Color(0xFFFF9F0A)
    val DangerRed = Color(0xFFFF453A)
    val AxisX = Color(0xFFFF453A)
    val AxisY = Color(0xFF32D74B)
    val AxisZ = Color(0xFF0A84FF)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFAEAEB2)
    val TextTertiary = Color(0xFF6E6E73)
}

private val RealityDarkScheme = darkColorScheme(
    primary = RcColors.AccentBlue,
    onPrimary = Color.White,
    secondary = RcColors.PlayGreen,
    onSecondary = Color.Black,
    background = RcColors.Background,
    onBackground = RcColors.TextPrimary,
    surface = RcColors.Surface,
    onSurface = RcColors.TextPrimary,
    surfaceVariant = RcColors.SurfaceElevated,
    onSurfaceVariant = RcColors.TextSecondary,
    error = RcColors.DangerRed,
    onError = Color.White,
    outline = RcColors.Border
)

@Composable
fun RealityComposerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RealityDarkScheme,
        content = content
    )
}

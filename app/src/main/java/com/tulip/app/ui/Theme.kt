package com.tulip.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.tulip.app.data.Settings

object Pastel {
    val choices: List<Pair<String, Int>> = listOf(
        "Azul cielo" to 0xFF8EC5F2.toInt(),
        "Lavanda" to 0xFFB9A9E8.toInt(),
        "Verde salvia" to 0xFF9CC5A1.toInt(),
        "Rosa suave" to 0xFFF2A9C0.toInt(),
        "Crema" to 0xFFFAF3E0.toInt(),
        "Blanco" to 0xFFFFFFFF.toInt(),
    )
}

private val Ink = Color(0xFF1F2933)

private fun buildScheme(dark: Boolean, primary: Color, accent: Color, bg: Color): ColorScheme =
    if (!dark) lightColorScheme(
        primary = primary, onPrimary = Ink,
        primaryContainer = lerp(primary, Color.White, 0.6f), onPrimaryContainer = Ink,
        secondary = accent, onSecondary = Ink,
        secondaryContainer = lerp(accent, Color.White, 0.6f), onSecondaryContainer = Ink,
        background = bg, onBackground = Ink,
        surface = lerp(bg, Color.White, 0.6f), onSurface = Ink,
        surfaceVariant = lerp(bg, primary, 0.2f), onSurfaceVariant = Color(0xFF4A5560),
        surfaceContainer = lerp(bg, Color.White, 0.4f),
        surfaceContainerHigh = lerp(bg, Color.White, 0.55f),
        outline = Color(0xFF8A94A0),
    ) else darkColorScheme(
        primary = primary, onPrimary = Color(0xFF10151A),
        primaryContainer = lerp(primary, Color.Black, 0.6f), onPrimaryContainer = Color(0xFFE8EEF4),
        secondary = accent, onSecondary = Color(0xFF10151A),
        secondaryContainer = lerp(accent, Color.Black, 0.6f), onSecondaryContainer = Color(0xFFE8EEF4),
        background = lerp(Color(0xFF121418), bg, 0.06f), onBackground = Color(0xFFE6EAEE),
        surface = lerp(Color(0xFF181B20), bg, 0.05f), onSurface = Color(0xFFE6EAEE),
        surfaceVariant = lerp(Color(0xFF232830), primary, 0.12f), onSurfaceVariant = Color(0xFFB4BDC7),
        surfaceContainer = lerp(Color(0xFF1C2026), bg, 0.04f),
        surfaceContainerHigh = lerp(Color(0xFF232830), bg, 0.04f),
        outline = Color(0xFF7C8793),
    )

@Composable
fun TulipTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = buildScheme(dark, Color(settings.primary), Color(settings.accent), Color(settings.background)),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}

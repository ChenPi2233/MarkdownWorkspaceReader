package com.example.mdworkspace.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5D62),
    onPrimary = Color.White,
    secondary = Color(0xFF6D5D4B),
    onSecondary = Color.White,
    tertiary = Color(0xFF8B3A3A),
    background = Color.White,
    onBackground = Color(0xFF1F2421),
    surface = Color.White,
    onSurface = Color(0xFF1F2421),
    surfaceVariant = Color(0xFFE8ECE7),
    onSurfaceVariant = Color(0xFF454C48),
    outline = Color(0xFFB8C0BA)
)

@Composable
fun MarkdownWorkspaceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}

package org.betterlyrics.composer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFFF50032)

private val Dark = darkColorScheme(primary = Accent, background = Color(0xFF121212), surface = Color(0xFF1C1C1C))
private val Light = lightColorScheme(primary = Accent)

@Composable
fun ComposerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}

package ru.dks.obhod.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DeviationRed = Color(0xFFD32F2F)
val OkGreen = Color(0xFF2E7D32)

private val Light = lightColorScheme(
    primary = Color(0xFF1F4E79),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F5),
    onPrimaryContainer = Color(0xFF0B2740),
    secondary = Color(0xFFF5A623),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFFFE6B8),
    onSecondaryContainer = Color(0xFF3A2600),
    error = DeviationRed,
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF4F6F9),
    surface = Color(0xFFF4F6F9),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9CC7F0),
    onPrimary = Color(0xFF0B2740),
    primaryContainer = Color(0xFF1F4E79),
    onPrimaryContainer = Color(0xFFD3E4F5),
    secondary = Color(0xFFFFC660),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF5C4100),
    onSecondaryContainer = Color(0xFFFFE6B8),
    error = Color(0xFFFF6E66),
    errorContainer = Color(0xFF5C1512),
)

@Composable
fun DksTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}

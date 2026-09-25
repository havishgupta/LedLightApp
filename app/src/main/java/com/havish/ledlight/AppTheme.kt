package com.havish.ledlight

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7C5CFF),
    background = Color(0xFF0B0B12),
    surface = Color(0xFF161624),
    surfaceVariant = Color(0xFF1F1F33),
    onPrimary = Color.White,
    onBackground = Color(0xFFF2F2F8),
    onSurface = Color(0xFFF2F2F8),
    error = Color(0xFFFF5C7A)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF7C5CFF),
    background = Color(0xFFF2F2F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE5E5EA),
    onPrimary = Color.White,
    onBackground = Color(0xFF0B0B12),
    onSurface = Color(0xFF0B0B12),
    error = Color(0xFFFF5C7A)
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

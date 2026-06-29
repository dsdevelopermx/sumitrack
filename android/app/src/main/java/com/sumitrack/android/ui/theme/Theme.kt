package com.sumitrack.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary          = Primary,
    onPrimary        = OnPrimary,
    primaryContainer = PrimaryVariant,
    background       = Background,
    surface          = Surface,
    onSurface        = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    outline          = Outline,
    error            = Error,
)

@Composable
fun SumitrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography  = SumitrackTypography,
        shapes      = SumitrackShapes,
        content     = content,
    )
}

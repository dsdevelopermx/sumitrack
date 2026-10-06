package com.sumitrack.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary          = Primary,
    onPrimary        = OnPrimary,
    primaryContainer = PrimaryVariant,
    // Sin esto Material usa su onPrimaryContainer por defecto (#21005D, morado oscuro): sobre #3949AB da 2.2:1.
    // Blanco da 7.7:1 y es lo que ya pide DESIGN.md para el chip activo. Afecta a los FAB, la celda con cobros
    // de la Agenda y el badge "Variantes".
    onPrimaryContainer = OnPrimary,
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

package com.sumitrack.android.ui.theme

import androidx.compose.ui.graphics.Color

// Core
val Primary          = Color(0xFF1A237E)
val PrimaryVariant   = Color(0xFF3949AB)
val OnPrimary        = Color(0xFFFFFFFF)
val Background       = Color(0xFFF0F0F5)
val Surface          = Color(0xFFFFFFFF)
val OnSurface        = Color(0xFF1A1A2E)
val OnSurfaceVariant = Color(0xFF6B6B80)
val Outline          = Color(0xFFE8E8EE)
val Error            = Color(0xFFB00020)

// Estado de órdenes
val StatusPaid      = Color(0xFF2E7D32)
val StatusPending   = Color(0xFFF57F17)
val StatusOverdue   = Color(0xFFAD1457)
val StatusCancelled = Color(0xFF9E9E9E)

// Sync — EXCLUSIVO para indicadores de sincronización, no usar en otro contexto
val SyncOk      = Color(0xFF00BCD4)  // solo íconos ≥20dp sobre blanco
val SyncPending = Color(0xFFFF7043)  // solo íconos, nunca texto

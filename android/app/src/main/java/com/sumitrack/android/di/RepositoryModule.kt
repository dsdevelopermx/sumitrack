package com.sumitrack.android.di

// AuthRepository y SettingsRepository usan @Singleton + @Inject constructor.
// Hilt los provee automáticamente — no se requieren bindings manuales.
// Si en el futuro se usan interfaces (IAuthRepository), agregar @Binds aquí.

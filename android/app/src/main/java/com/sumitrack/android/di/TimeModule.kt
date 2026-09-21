package com.sumitrack.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    // Sin scope. La zona se lee EN VIVO (ZoneId.systemDefault() en cada llamada), no se captura al
    // crear el Clock como hace Clock.systemDefaultZone(): un cambio de zona horaria con la app abierta
    // se refleja en el siguiente cálculo, igual que formatDate (que ya usa systemDefault en vivo).
    // Los ViewModels que dependen de "hoy" (AgendaViewModel, Historia 5.3) lo reciben por constructor
    // para que los tests usen Clock.fixed(...) y no dependan de la fecha real.
    @Provides
    fun provideClock(): Clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneId.systemDefault()
        override fun withZone(zone: ZoneId): Clock = Clock.system(zone)
        override fun instant(): Instant = Instant.now()
    }
}

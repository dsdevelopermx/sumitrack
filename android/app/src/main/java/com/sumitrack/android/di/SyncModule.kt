package com.sumitrack.android.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    // Inyectar WorkManager directamente (en vez de Context + WorkManager.getInstance(context) en
    // cada punto de uso) mantiene testeable en JVM puro a cualquier clase que encole trabajo —
    // un fake/mock de WorkManager es trivial de construir, a diferencia de WorkManager.getInstance().
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}

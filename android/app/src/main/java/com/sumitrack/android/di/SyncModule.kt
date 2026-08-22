package com.sumitrack.android.di

import android.content.Context
import androidx.work.WorkManager
import com.sumitrack.android.sync.AndroidConnectivityObserver
import com.sumitrack.android.sync.ConnectivityObserver
import com.sumitrack.android.sync.PushSyncTrigger
import com.sumitrack.android.sync.SyncWorkObserver
import com.sumitrack.android.sync.TriggerPushSyncUseCase
import com.sumitrack.android.sync.WorkManagerSyncWorkObserver
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

    @Provides
    @Singleton
    fun provideSyncWorkObserver(workManager: WorkManager): SyncWorkObserver =
        WorkManagerSyncWorkObserver(workManager)

    @Provides
    @Singleton
    fun provideConnectivityObserver(@ApplicationContext context: Context): ConnectivityObserver =
        AndroidConnectivityObserver(context)

    @Provides
    @Singleton
    fun providePushSyncTrigger(useCase: TriggerPushSyncUseCase): PushSyncTrigger = useCase
}

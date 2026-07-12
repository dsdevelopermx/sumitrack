package com.sumitrack.android.di

import com.sumitrack.android.data.repositories.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Qualifier

// SessionManager usa @Singleton + @Inject constructor — Hilt lo provee automáticamente.

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TenantId

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {

    // Expone solo el Flow<String?> de tenantId (no todo SessionManager) para que
    // los ViewModels que solo necesitan el tenant activo (ej. ClientFormViewModel)
    // sean testeables en JVM puro sin un Context real.
    @Provides
    @TenantId
    fun provideTenantIdFlow(sessionManager: SessionManager): Flow<String?> = sessionManager.tenantId
}

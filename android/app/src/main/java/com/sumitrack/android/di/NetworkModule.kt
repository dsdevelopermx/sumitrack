package com.sumitrack.android.di

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sumitrack.android.BuildConfig
import com.sumitrack.android.data.remote.api.AuthApiService
import com.sumitrack.android.data.remote.api.SettingsApiService
import com.sumitrack.android.data.remote.api.SyncApiService
import com.sumitrack.android.data.repositories.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Interceptor de Authorization — resuelve el ítem diferido desde Historia 1.4 ("refactorizar
    // cuando haya ≥3 servicios autenticados"; SyncApiService es el 3ro). AuthApiService.login y
    // SettingsApiService.getSettings siguen pasando el header manual, pero comparten el mismo
    // OkHttpClient — por eso se usa .header() (reemplaza) en vez de .addHeader() (agrega), así
    // esas dos llamadas no terminan con dos headers Authorization duplicados. runBlocking dentro
    // del contrato síncrono de Interceptor es seguro aquí: OkHttp ejecuta los interceptores de
    // toda llamada asíncrona/suspend en sus propios hilos de despacho, nunca en el hilo principal.
    @Provides
    @Singleton
    fun provideAuthInterceptor(sessionManager: SessionManager): Interceptor = Interceptor { chain ->
        val token = runBlocking { sessionManager.token.first() }
        val request = if (token != null) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: Interceptor): OkHttpClient = OkHttpClient.Builder()
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                })
            }
        }
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService =
        retrofit.create(AuthApiService::class.java)

    @Provides
    @Singleton
    fun provideSettingsApiService(retrofit: Retrofit): SettingsApiService =
        retrofit.create(SettingsApiService::class.java)

    @Provides
    @Singleton
    fun provideSyncApiService(retrofit: Retrofit): SyncApiService =
        retrofit.create(SyncApiService::class.java)
}

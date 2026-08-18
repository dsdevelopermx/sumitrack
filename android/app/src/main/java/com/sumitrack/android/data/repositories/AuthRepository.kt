package com.sumitrack.android.data.repositories

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.sumitrack.android.data.remote.api.AuthApiService
import com.sumitrack.android.data.remote.dto.LoginRequestDto
import com.sumitrack.android.domain.exceptions.InvalidCredentialsException
import com.sumitrack.android.sync.workers.PullWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

@Singleton
class AuthRepository @Inject constructor(
    private val authApiService: AuthApiService,
    private val sessionManager: SessionManager,
    private val workManager: WorkManager,
) {
    suspend fun login(username: String, password: String): Result<Unit> {
        // Step 1: Authenticate — map 401 specifically to InvalidCredentialsException
        val response = runCatching {
            authApiService.login(LoginRequestDto(username, password))
        }.recoverCatching { e ->
            when {
                e is HttpException && e.code() == 401 -> throw InvalidCredentialsException()
                else -> throw e
            }
        }.getOrElse { return Result.failure(it) }

        // Step 2: Persist token
        sessionManager.saveToken(response.token)

        // Step 3: Encolar el pull inicial sin esperar su resultado — AC-1 de Historia 4.2 exige
        // que la navegación no se bloquee por la descarga. Ya no se revierte el login si esto
        // falla; PullWorker reintenta en background con su propio backoff. Un fallo al encolar
        // (p.ej. WorkManager mal configurado) tampoco debe hacer fallar un login por lo demás
        // exitoso — el token ya se guardó — así que se descarta silenciosamente aquí; el próximo
        // PushWorker/pull manual (Historia 5.1) puede retomar la sincronización más tarde.
        runCatching {
            val request = OneTimeWorkRequestBuilder<PullWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniqueWork("pull-sync", ExistingWorkPolicy.REPLACE, request)
        }

        return Result.success(Unit)
    }
}

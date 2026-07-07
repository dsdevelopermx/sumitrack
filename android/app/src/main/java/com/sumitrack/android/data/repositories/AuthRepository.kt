package com.sumitrack.android.data.repositories

import com.sumitrack.android.data.remote.api.AuthApiService
import com.sumitrack.android.data.remote.dto.LoginRequestDto
import com.sumitrack.android.domain.exceptions.InvalidCredentialsException
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApiService: AuthApiService,
    private val sessionManager: SessionManager,
    private val settingsRepository: SettingsRepository,
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

        // Step 3: Download settings — roll back token if this fails
        return runCatching {
            settingsRepository.downloadAndCacheSettings(response.token)
        }.onFailure {
            sessionManager.clearToken()
        }
    }
}

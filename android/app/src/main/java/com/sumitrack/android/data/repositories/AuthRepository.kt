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
    suspend fun login(username: String, password: String): Result<Unit> = runCatching {
        val response = authApiService.login(LoginRequestDto(username, password))
        sessionManager.saveToken(response.token)
        settingsRepository.downloadAndCacheSettings(response.token)
    }.recoverCatching { e ->
        when {
            e is HttpException && e.code() == 401 -> throw InvalidCredentialsException()
            else -> throw e
        }
    }
}

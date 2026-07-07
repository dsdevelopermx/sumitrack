package com.sumitrack.android.data.remote.api

import com.sumitrack.android.data.remote.dto.LoginRequestDto
import com.sumitrack.android.data.remote.dto.LoginResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): LoginResponseDto
}

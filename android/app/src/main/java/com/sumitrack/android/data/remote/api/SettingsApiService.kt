package com.sumitrack.android.data.remote.api

import com.sumitrack.android.data.remote.dto.SettingDto
import retrofit2.http.GET
import retrofit2.http.Header

interface SettingsApiService {

    @GET("api/v1/settings")
    suspend fun getSettings(@Header("Authorization") token: String): List<SettingDto>
}

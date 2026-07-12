package com.sumitrack.android.data.local

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

object JwtDecoder {

    @Serializable
    private data class JwtPayload(val tenant_id: String? = null)

    private val json = Json { ignoreUnknownKeys = true }

    fun decodeTenantId(token: String): String? {
        val parts = token.split(".")
        if (parts.size != 3) return null
        return runCatching {
            val payload = parts[1]
            val padded = payload.padEnd(((payload.length + 3) / 4) * 4, '=')
            val bytes = Base64.getUrlDecoder().decode(padded)
            json.decodeFromString<JwtPayload>(String(bytes, Charsets.UTF_8)).tenant_id?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}

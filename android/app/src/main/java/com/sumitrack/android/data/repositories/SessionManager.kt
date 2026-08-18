package com.sumitrack.android.data.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sumitrack.android.data.local.JwtDecoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : FolioBaselineStore {

    private val tokenKey = stringPreferencesKey("auth_token")
    private val tenantIdKey = stringPreferencesKey("tenant_id")

    // Piso del contador de folios confirmado por el servidor (Historia 4.2, AR-10) — poblado por
    // PullService.pullFolioCount() de forma rápida e independiente del pull completo de `ventas`.
    // No se limpia en clearToken() — mismo gap ya diferido de "nada purga datos locales al
    // cambiar de tenant en el mismo dispositivo" (deferred-work.md).
    private val folioBaselineKey = intPreferencesKey("folio_baseline")

    val token: Flow<String?> = context.dataStore.data.map { prefs -> prefs[tokenKey] }

    val tenantId: Flow<String?> = context.dataStore.data.map { prefs -> prefs[tenantIdKey] }

    val isLoggedIn: Flow<Boolean> = token.map { it?.isNotBlank() == true }

    override val folioBaseline: Flow<Int?> = context.dataStore.data.map { prefs -> prefs[folioBaselineKey] }

    override suspend fun saveFolioBaseline(count: Int) {
        context.dataStore.edit { prefs -> prefs[folioBaselineKey] = count }
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[tokenKey] = token
            val tenantId = JwtDecoder.decodeTenantId(token)
            if (tenantId != null) {
                prefs[tenantIdKey] = tenantId
            } else {
                // Un tenant_id nuevo (o ausente) siempre reemplaza al anterior — nunca dejar
                // el tenant_id de una sesión previa asociado a un token distinto.
                prefs.remove(tenantIdKey)
            }
        }
    }

    suspend fun clearToken() {
        context.dataStore.edit { prefs ->
            prefs.remove(tokenKey)
            prefs.remove(tenantIdKey)
        }
    }
}

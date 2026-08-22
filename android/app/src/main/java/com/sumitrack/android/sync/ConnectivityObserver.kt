package com.sumitrack.android.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

// Interfaz angosta sobre ConnectivityManager — mismo patrón que SyncWorkObserver, mantiene
// testeable en JVM puro cualquier clase que observe conectividad, sin necesitar Context real.
interface ConnectivityObserver {
    val isOnline: Flow<Boolean>
}

// Primer mecanismo de conectividad EN VIVO del proyecto — el único precedente existente
// (LoginViewModel.checkConnectivity()) es una sola lectura, nunca actualizada tras el arranque.
class AndroidConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context,
) : ConnectivityObserver {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        // NET_CAPABILITY_VALIDATED (no solo NET_CAPABILITY_INTERNET) — sin esto, un portal cautivo
        // o un router sin WAN funcional reporta "online" solo por declarar intención de dar
        // internet, aunque el sistema nunca haya confirmado que de verdad se puede alcanzar. Al
        // filtrar el request por VALIDATED, cualquier red que pierda esa validación dispara
        // onLost() automáticamente (deja de calzar con el request), sin necesitar interceptar
        // onCapabilitiesChanged() por separado.
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        val current = connectivityManager.activeNetwork
            ?.let { connectivityManager.getNetworkCapabilities(it) }
            ?.let {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
            ?: false
        trySend(current)

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
        // debounce — un handoff de red (ej. WiFi→celular) puede producir un onLost() seguido casi
        // inmediatamente de un onAvailable() de la red nueva; sin amortiguar, ese parpadeo
        // offline→online dispara innecesariamente el banner de AC-4 y su timer de colapso de 3s.
    }.debounce(800).distinctUntilChanged()
}

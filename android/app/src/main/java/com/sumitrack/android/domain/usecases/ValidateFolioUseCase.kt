package com.sumitrack.android.domain.usecases

import com.sumitrack.android.data.local.dao.SaleDao
import com.sumitrack.android.data.repositories.FolioBaselineStore
import com.sumitrack.android.data.repositories.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

// AR-10 (folio confirmado del servidor): el pull completo de `ventas` NO bloquea la navegación
// (AC-1 de Historia 4.2), así que no hay garantía de que `sales` ya esté poblado cuando el
// proveedor crea su primera venta tras el login. En su lugar, PullService.pullFolioCount() pide
// un conteo rápido y liviano (independiente del pull completo) apenas arranca el pull, y lo
// guarda como piso vía SessionManager.folioBaseline. El folio real es
// max(ventas locales, piso del servidor) + 1 — nunca colisiona con folios ya emitidos por el
// servidor aunque el pull completo de `ventas` siga corriendo en background.
class ValidateFolioUseCase @Inject constructor(
    private val saleDao: SaleDao,
    private val settingsRepository: SettingsRepository,
    private val folioBaselineStore: FolioBaselineStore,
) {
    suspend operator fun invoke(tenantId: String): String {
        val serie = settingsRepository.getValue("serie_folio")?.takeIf { it.isNotBlank() } ?: "A"
        val localCount = saleDao.countSalesForTenant(tenantId)
        val serverBaseline = folioBaselineStore.folioBaseline.first() ?: 0
        val count = maxOf(localCount, serverBaseline)
        return "$serie${count + 1}"
    }
}

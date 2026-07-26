package com.sumitrack.android.domain.usecases

import com.sumitrack.android.data.local.dao.SaleDao
import com.sumitrack.android.data.repositories.SettingsRepository
import javax.inject.Inject

// Sin componente de servidor: AR-10 describe un pull inicial de folio confirmado que depende de
// Epic 4 (sincronización), inexistente todavía. Contar las ventas locales del tenant es correcto
// mientras esta app sea la única fuente de folios (nadie más escribe en `sales` todavía).
class ValidateFolioUseCase @Inject constructor(
    private val saleDao: SaleDao,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(tenantId: String): String {
        val serie = settingsRepository.getValue("serie_folio")?.takeIf { it.isNotBlank() } ?: "A"
        val count = saleDao.countSalesForTenant(tenantId)
        return "$serie${count + 1}"
    }
}

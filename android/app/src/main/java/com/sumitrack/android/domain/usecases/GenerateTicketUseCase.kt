package com.sumitrack.android.domain.usecases

import com.sumitrack.android.data.repositories.ClientRepository
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.data.repositories.SettingsRepository
import com.sumitrack.android.domain.models.TicketData
import com.sumitrack.android.domain.models.TicketFiscalData
import com.sumitrack.android.domain.models.TicketLineItem
import com.sumitrack.android.domain.models.TicketPaymentCondition
import javax.inject.Inject

// Deliberadamente sin ninguna dependencia de android.graphics — solo ensambla datos, 100%
// testeable en JVM puro. El renderizado a Bitmap vive en ui/ (TicketBitmapRenderer.kt), separado
// para no requerir Robolectric (que este proyecto nunca ha adoptado).
class GenerateTicketUseCase @Inject constructor(
    private val saleRepository: SaleRepository,
    private val clientRepository: ClientRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(saleId: String, tenantId: String): TicketData? {
        val detail = saleRepository.getSaleDetail(saleId, tenantId) ?: return null
        val client = runCatching { clientRepository.getClientById(detail.sale.fkClient) }.getOrNull()

        val fiscal = TicketFiscalData(
            businessName = settingsRepository.getValue("negocio_nombre").orEmpty(),
            rfc = settingsRepository.getValue("negocio_rfc").orEmpty(),
            address = settingsRepository.getValue("negocio_direccion").orEmpty(),
            phone = settingsRepository.getValue("negocio_telefono").orEmpty(),
        )
        val paymentCondition = if (detail.installments.isEmpty()) {
            TicketPaymentCondition.SinglePayment(detail.payments.firstOrNull()?.paidAt ?: detail.sale.createdAt)
        } else {
            TicketPaymentCondition.InstallmentPlan(detail.installments.sortedBy { it.dueDate })
        }

        return TicketData(
            fiscal = fiscal,
            clientName = client?.name.orEmpty(),
            folio = detail.sale.folio,
            createdAt = detail.sale.createdAt,
            lineItems = detail.items.map {
                TicketLineItem(
                    description = if (it.variantName != null) "${it.productName} (${it.variantName})" else it.productName,
                    quantity = it.quantity,
                    unitPrice = it.unitPrice,
                    subtotal = it.subtotal,
                )
            },
            subtotal = detail.sale.subtotal,
            tax = detail.sale.tax,
            total = detail.sale.total,
            paymentCondition = paymentCondition,
        )
    }
}

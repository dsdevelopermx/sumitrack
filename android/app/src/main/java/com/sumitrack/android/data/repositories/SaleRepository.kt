package com.sumitrack.android.data.repositories

import com.sumitrack.android.data.local.SearchNormalizer
import com.sumitrack.android.data.local.TransactionRunner
import com.sumitrack.android.data.local.dao.InstallmentDao
import com.sumitrack.android.data.local.dao.OrderSummaryRow
import com.sumitrack.android.data.local.dao.PaymentDao
import com.sumitrack.android.data.local.dao.SaleDao
import com.sumitrack.android.data.local.dao.SaleItemDao
import com.sumitrack.android.data.local.entities.InstallmentEntity
import com.sumitrack.android.data.local.entities.PaymentEntity
import com.sumitrack.android.data.local.entities.SaleEntity
import com.sumitrack.android.data.local.entities.SaleItemEntity
import com.sumitrack.android.domain.models.Installment
import com.sumitrack.android.domain.models.InstallmentStatus
import com.sumitrack.android.domain.models.OrderDraftItem
import com.sumitrack.android.domain.models.OrderSummary
import com.sumitrack.android.domain.models.Payment
import com.sumitrack.android.domain.models.PaymentMethodType
import com.sumitrack.android.domain.models.Sale
import com.sumitrack.android.domain.models.SaleDetail
import com.sumitrack.android.domain.models.SaleItem
import com.sumitrack.android.domain.models.SaleStatus
import com.sumitrack.android.domain.models.SyncStatus
import com.sumitrack.android.domain.models.calculateOrderTotals
import com.sumitrack.android.domain.usecases.InstallmentSuggestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class PaymentConfig {
    data class Immediate(val payments: List<Pair<PaymentMethodType, BigDecimal>>) : PaymentConfig()
    data class Installments(val installments: List<InstallmentSuggestion>) : PaymentConfig()
}

@Singleton
class SaleRepository @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val saleDao: SaleDao,
    private val saleItemDao: SaleItemDao,
    private val installmentDao: InstallmentDao,
    private val paymentDao: PaymentDao,
) {

    suspend fun getOpenSalesForClient(clientId: String, tenantId: String): List<Sale> =
        saleDao.getOpenSalesForClient(clientId, tenantId).map { it.toDomain() }

    fun getOrdersForTenant(tenantId: String, statusFilter: SaleStatus?, searchQuery: String): Flow<List<OrderSummary>> =
        saleDao.getOrdersForTenantAsFlow(
            tenantId = tenantId,
            statusFilter = statusFilter?.name?.lowercase(Locale.ROOT),
            normalizedQuery = SearchNormalizer.toLikePattern(searchQuery.trim()),
        ).map { rows -> rows.map { it.toDomain() } }

    // Estatus "paid" para Pago inmediato: la UI (PaymentViewModel) solo permite confirmar cuando
    // "Restante por asignar" = $0.00, así que por construcción el total ya está cubierto.
    // Estatus "pending" para Parcialidades: ningún Payment se crea en esta historia para ese modo
    // (registrar cobros sobre parcialidades es Historia 3.6), la venta no tiene cobros todavía.
    // El saldo del cliente no requiere actualización explícita: CalculateClientBalanceUseCase ya
    // suma dinámicamente las ventas pending/partial en cada consulta.
    suspend fun createSale(
        tenantId: String,
        clientId: String,
        folio: String,
        items: List<OrderDraftItem>,
        paymentConfig: PaymentConfig,
    ): String {
        val now = Instant.now()
        val totals = calculateOrderTotals(items)
        val status = if (paymentConfig is PaymentConfig.Immediate) "paid" else "pending"

        val sale = SaleEntity(
            id = UUID.randomUUID().toString(),
            fkTenant = tenantId,
            fkClient = clientId,
            folio = folio,
            total = totals.total,
            subtotal = totals.subtotal,
            tax = totals.tax,
            status = status,
            createdAt = now,
            updatedAt = now,
            syncStatus = "pending",
        )
        val saleItems = items.map { item ->
            SaleItemEntity(
                id = UUID.randomUUID().toString(),
                fkTenant = tenantId,
                fkSale = sale.id,
                fkProduct = item.product.id,
                fkVariant = item.variant?.id,
                productName = item.product.name,
                variantName = item.variant?.name,
                quantity = item.quantity,
                unitPrice = item.product.price,
                taxRate = item.product.taxRate,
                createdAt = now,
                updatedAt = now,
                syncStatus = "pending",
            )
        }

        transactionRunner.run {
            saleDao.upsertAll(listOf(sale))
            saleItemDao.upsertAll(saleItems)
            when (paymentConfig) {
                is PaymentConfig.Immediate -> paymentDao.upsertAll(
                    paymentConfig.payments.map { (method, amount) ->
                        PaymentEntity(
                            id = UUID.randomUUID().toString(),
                            fkTenant = tenantId,
                            fkSale = sale.id,
                            fkInstallment = null,
                            method = method.name.lowercase(),
                            amount = amount,
                            paidAt = now,
                            createdAt = now,
                            updatedAt = now,
                            syncStatus = "pending",
                        )
                    }
                )
                is PaymentConfig.Installments -> installmentDao.upsertAll(
                    paymentConfig.installments.map { suggestion ->
                        InstallmentEntity(
                            id = UUID.randomUUID().toString(),
                            fkTenant = tenantId,
                            fkSale = sale.id,
                            amount = suggestion.amount,
                            dueDate = suggestion.dueDate,
                            status = "pending",
                            createdAt = now,
                            updatedAt = now,
                            syncStatus = "pending",
                        )
                    }
                )
            }
        }
        return sale.id
    }

    // Reutiliza saleItemDao/installmentDao/paymentDao (inyectados desde Historia 3.3 "por
    // paridad", sin uso hasta ahora) para ensamblar el detalle completo de una venta que
    // GenerateTicketUseCase (Historia 3.4) necesita.
    suspend fun getSaleDetail(saleId: String, tenantId: String): SaleDetail? {
        val sale = saleDao.getById(saleId, tenantId)?.toDomain() ?: return null
        return SaleDetail(
            sale = sale,
            items = saleItemDao.getForSale(saleId, tenantId).map { it.toDomain() },
            payments = paymentDao.getForSale(saleId, tenantId).map { it.toDomain() },
            installments = installmentDao.getForSale(saleId, tenantId).map { it.toDomain() },
        )
    }

    private fun SaleEntity.toDomain() = Sale(
        id = id,
        fkTenant = fkTenant,
        fkClient = fkClient,
        folio = folio,
        total = total,
        subtotal = subtotal,
        tax = tax,
        status = SaleStatus.fromString(status),
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = SyncStatus.fromString(syncStatus),
    )

    private fun SaleItemEntity.toDomain() = SaleItem(
        id = id,
        fkTenant = fkTenant,
        fkSale = fkSale,
        fkProduct = fkProduct,
        fkVariant = fkVariant,
        productName = productName,
        variantName = variantName,
        quantity = quantity,
        unitPrice = unitPrice,
        taxRate = taxRate,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = SyncStatus.fromString(syncStatus),
    )

    private fun PaymentEntity.toDomain() = Payment(
        id = id,
        fkTenant = fkTenant,
        fkSale = fkSale,
        fkInstallment = fkInstallment,
        method = PaymentMethodType.fromString(method),
        amount = amount,
        paidAt = paidAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = SyncStatus.fromString(syncStatus),
    )

    private fun InstallmentEntity.toDomain() = Installment(
        id = id,
        fkTenant = fkTenant,
        fkSale = fkSale,
        amount = amount,
        dueDate = dueDate,
        status = InstallmentStatus.fromString(status),
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = SyncStatus.fromString(syncStatus),
    )

    private fun OrderSummaryRow.toDomain() = OrderSummary(
        id = id,
        folio = folio,
        clientName = clientName,
        total = total,
        status = SaleStatus.fromString(status),
        createdAt = createdAt,
        syncStatus = SyncStatus.fromString(syncStatus),
    )
}

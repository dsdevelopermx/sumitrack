package com.sumitrack.android.data.repositories

import com.sumitrack.android.data.local.SearchNormalizer
import com.sumitrack.android.data.local.TransactionRunner
import com.sumitrack.android.data.local.dao.CreditBalanceDao
import com.sumitrack.android.data.local.dao.InstallmentDao
import com.sumitrack.android.data.local.dao.OrderSummaryRow
import com.sumitrack.android.data.local.dao.PaymentDao
import com.sumitrack.android.data.local.dao.SaleDao
import com.sumitrack.android.data.local.dao.SaleItemDao
import com.sumitrack.android.data.local.entities.CreditBalanceEntity
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
    private val creditBalanceDao: CreditBalanceDao,
) {

    suspend fun getOpenSalesForClient(clientId: String, tenantId: String): List<Sale> =
        saleDao.getOpenSalesForClient(clientId, tenantId).map { it.toDomain() }

    suspend fun getPaymentsForSale(saleId: String, tenantId: String): List<Payment> =
        paymentDao.getForSale(saleId, tenantId).map { it.toDomain() }

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
            // Historia 3.7 (AC-5/AC-6): un método CREDITO_A_FAVOR consume las filas de crédito del
            // cliente FIFO (más antiguas primero, `id` como desempate determinista si comparten
            // createdAt — Review Finding), reduciendo `amount` EN SITIO — nunca las borra ni las
            // marca "aplicadas" de forma binaria, porque `amount` ya representa el remanente actual
            // de esa fila (ver Dev Notes de la historia).
            if (paymentConfig is PaymentConfig.Immediate) {
                val creditUsed = paymentConfig.payments
                    .filter { it.first == PaymentMethodType.CREDITO_A_FAVOR }
                    .fold(BigDecimal.ZERO) { acc, (_, amount) -> acc + amount }
                if (creditUsed > BigDecimal.ZERO) {
                    val rows = creditBalanceDao.getForClient(clientId, tenantId).sortedWith(compareBy({ it.createdAt }, { it.id }))
                    val available = rows.fold(BigDecimal.ZERO) { acc, r -> acc + r.amount }
                    // Lanzar (no return@run) es intencional: una excepción SÍ revierte toda la
                    // transacción (venta/ítems/pagos ya escritos en este mismo bloque), evitando que
                    // una venta quede marcada "paid" con más crédito del que el cliente realmente
                    // tiene (Review Finding — reportado independientemente por los 3 revisores).
                    check(creditUsed <= available) { "Crédito a Favor insuficiente para cubrir el monto solicitado" }
                    var remaining = creditUsed
                    val updated = mutableListOf<CreditBalanceEntity>()
                    for (row in rows) {
                        if (remaining <= BigDecimal.ZERO) break
                        val consume = row.amount.min(remaining)
                        if (consume <= BigDecimal.ZERO) continue
                        updated += row.copy(amount = row.amount - consume, appliedAt = now, updatedAt = now, syncStatus = "pending")
                        remaining -= consume
                    }
                    if (updated.isNotEmpty()) creditBalanceDao.upsertAll(updated)
                }
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

    // Historia 3.6: registra un cobro contra una venta de pago único (installmentId = null, monto
    // = sale.total) o contra una parcialidad específica (monto = installment.amount). El monto
    // siempre lo resuelve RegisterPaymentUseCase antes de llamar aquí — nunca es libre.
    suspend fun registerPayment(
        tenantId: String,
        saleId: String,
        installmentId: String?,
        method: PaymentMethodType,
        amount: BigDecimal,
        paidAt: Instant,
    ) {
        transactionRunner.run {
            // La venta se busca ANTES de escribir nada (Review Finding) — un `return@run` normal
            // no revierte lo ya escrito en la transacción (Room solo hace rollback ante una
            // excepción), así que validar primero evita dejar un Payment/parcialidad huérfanos si
            // saleId/tenantId no corresponden a ninguna venta real.
            val sale = saleDao.getById(saleId, tenantId) ?: return@run
            paymentDao.upsertAll(
                listOf(
                    PaymentEntity(
                        id = UUID.randomUUID().toString(),
                        fkTenant = tenantId,
                        fkSale = saleId,
                        fkInstallment = installmentId,
                        method = method.name.lowercase(),
                        amount = amount,
                        paidAt = paidAt,
                        createdAt = paidAt,
                        updatedAt = paidAt,
                        syncStatus = "pending",
                    )
                )
            )
            val newSaleStatus = if (installmentId == null) {
                "paid"
            } else {
                val installments = installmentDao.getForSale(saleId, tenantId)
                val paidInstallment = installments.find { it.id == installmentId }
                    ?.copy(status = "paid", updatedAt = paidAt)
                if (paidInstallment != null) installmentDao.upsertAll(listOf(paidInstallment))
                val allPaid = installments.all { it.id == installmentId || it.status == "paid" }
                if (allPaid) "paid" else "partial"
            }
            // syncStatus = "pending" — mismo criterio que createSale: cualquier fila local
            // modificada que deba subirse en la siguiente sincronización se marca pending.
            saleDao.upsertAll(listOf(sale.copy(status = newSaleStatus, updatedAt = paidAt, syncStatus = "pending")))
        }
    }

    suspend fun getAvailableCredit(clientId: String, tenantId: String): BigDecimal =
        creditBalanceDao.getForClient(clientId, tenantId).fold(BigDecimal.ZERO) { acc, row -> acc + row.amount }

    // Historia 3.7 (AC-1, AC-2, AC-7): cancela la venta y todas sus parcialidades PENDING (las ya
    // PAID/CANCELLED no se tocan); nunca borra ni modifica payments (AC-7). Si `creditAmount` no es
    // null, otorga Crédito a Favor por ese monto en la MISMA transacción — evita el caso "venta
    // cancelada pero el crédito nunca se generó" ante un fallo a mitad de operación.
    suspend fun cancelSale(tenantId: String, saleId: String, creditAmount: BigDecimal?): Boolean {
        return transactionRunner.run {
            // La venta se busca ANTES de escribir nada, mismo criterio que registerPayment
            // (Historia 3.6, corregido en su code review): un `return@run` normal no revierte lo
            // ya escrito, así que validar primero evita escrituras huérfanas.
            val sale = saleDao.getById(saleId, tenantId) ?: return@run false
            val now = Instant.now()
            val installments = installmentDao.getForSale(saleId, tenantId)
            val toCancel = installments.filter { it.status == "pending" }
                .map { it.copy(status = "cancelled", updatedAt = now, syncStatus = "pending") }
            if (toCancel.isNotEmpty()) installmentDao.upsertAll(toCancel)
            saleDao.upsertAll(listOf(sale.copy(status = "cancelled", updatedAt = now, syncStatus = "pending")))
            if (creditAmount != null && creditAmount > BigDecimal.ZERO) {
                creditBalanceDao.upsertAll(
                    listOf(
                        CreditBalanceEntity(
                            id = UUID.randomUUID().toString(),
                            fkTenant = tenantId,
                            fkClient = sale.fkClient,
                            amount = creditAmount,
                            origin = "cancellation",
                            fkOriginSale = saleId,
                            appliedAt = null,
                            createdAt = now,
                            updatedAt = now,
                            syncStatus = "pending",
                        )
                    )
                )
            }
            true
        }
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

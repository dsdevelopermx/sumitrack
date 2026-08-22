package com.sumitrack.android.sync

import com.sumitrack.android.data.local.dao.ClientDao
import com.sumitrack.android.data.local.dao.CreditBalanceDao
import com.sumitrack.android.data.local.dao.InstallmentDao
import com.sumitrack.android.data.local.dao.PaymentDao
import com.sumitrack.android.data.local.dao.ProductDao
import com.sumitrack.android.data.local.dao.ProductVariantDao
import com.sumitrack.android.data.local.dao.SaleDao
import com.sumitrack.android.data.local.dao.SaleItemDao
import com.sumitrack.android.data.local.dao.SettingsDao
import com.sumitrack.android.data.local.entities.ClientEntity
import com.sumitrack.android.data.local.entities.CreditBalanceEntity
import com.sumitrack.android.data.local.entities.InstallmentEntity
import com.sumitrack.android.data.local.entities.PaymentEntity
import com.sumitrack.android.data.local.entities.ProductEntity
import com.sumitrack.android.data.local.entities.ProductVariantEntity
import com.sumitrack.android.data.local.entities.SaleEntity
import com.sumitrack.android.data.local.entities.SaleItemEntity
import com.sumitrack.android.data.local.entities.SettingsEntity
import com.sumitrack.android.data.remote.api.SyncApiService
import com.sumitrack.android.data.remote.dto.ClientSyncDto
import com.sumitrack.android.data.remote.dto.CreditBalanceSyncDto
import com.sumitrack.android.data.remote.dto.InstallmentSyncDto
import com.sumitrack.android.data.remote.dto.PaymentSyncDto
import com.sumitrack.android.data.remote.dto.ProductSyncDto
import com.sumitrack.android.data.remote.dto.ProductVariantSyncDto
import com.sumitrack.android.data.remote.dto.SaleItemSyncDto
import com.sumitrack.android.data.remote.dto.SaleSyncDto
import com.sumitrack.android.data.remote.dto.SettingSyncDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recolecta los registros `sync_status = pending` de las 9 entidades sincronizables y los
 * empuja al backend. Si la llamada de red de una entidad falla (excepción), [pushPending]
 * devuelve `false` de inmediato — [PushWorker] reintenta la corrida COMPLETA (no por entidad);
 * las entidades ya marcadas `synced` en la misma corrida no se reenvían en el reintento porque su
 * `sync_status` ya cambió. Si la llamada SÍ responde pero el backend rechaza registros
 * individuales (`success = false` por validación), esos registros quedan `pending` sin bloquear
 * al resto — un registro inválido nunca cambia de estado solo, así que forzar un reintento total
 * por su culpa dejaría sincronizando en el limbo a todas las demás entidades para siempre.
 */
@Singleton
class SyncManager @Inject constructor(
    private val clientDao: ClientDao,
    private val productDao: ProductDao,
    private val productVariantDao: ProductVariantDao,
    private val saleDao: SaleDao,
    private val saleItemDao: SaleItemDao,
    private val installmentDao: InstallmentDao,
    private val paymentDao: PaymentDao,
    private val creditBalanceDao: CreditBalanceDao,
    private val settingsDao: SettingsDao,
    private val syncApiService: SyncApiService,
) {
    // Mutex de proceso: push-sync (periódico) y push-sync-now (manual, Historia 4.3) son nombres
    // de trabajo de WorkManager DISTINTOS a propósito (para no colisionar/reemplazarse entre sí),
    // lo que significa que WorkManager no impide que ambos ejecuten pushPending concurrentemente —
    // sin este lock, el mismo lote pendiente podría enviarse duplicado al backend.
    private val pushMutex = Mutex()

    suspend fun pushPending(tenantId: String): Boolean = pushMutex.withLock {
        pushClientes(tenantId) &&
            pushProductos(tenantId) &&
            pushVariantes(tenantId) &&
            pushVentas(tenantId) &&
            pushItemsVenta(tenantId) &&
            pushParcialidades(tenantId) &&
            pushCobros(tenantId) &&
            pushCreditosAFavor(tenantId) &&
            pushSettings()
    }

    // Chequeo barato de si hay algo pendiente en cualquiera de las 9 entidades — usado por
    // PushWorker (Historia 4.3) para decidir si un push exitoso amerita notificar a la UI
    // (evita un Snackbar "Sincronizado correctamente" en cada corrida periódica silenciosa cuando
    // no había nada que sincronizar, que sería la mayoría de las veces en estado estable).
    suspend fun hasPendingWork(tenantId: String): Boolean {
        return clientDao.getPending(tenantId).isNotEmpty() ||
            productDao.getPending(tenantId).isNotEmpty() ||
            productVariantDao.getPending(tenantId).isNotEmpty() ||
            saleDao.getPending(tenantId).isNotEmpty() ||
            saleItemDao.getPending(tenantId).isNotEmpty() ||
            installmentDao.getPending(tenantId).isNotEmpty() ||
            paymentDao.getPending(tenantId).isNotEmpty() ||
            creditBalanceDao.getPending(tenantId).isNotEmpty() ||
            settingsDao.getPending().isNotEmpty()
    }

    // Ejecuta el push de un lote: llama a la API, marca `synced` (vía [markSynced], un UPDATE
    // acotado a la columna sync_status) SOLO los ids que el backend confirmó con success=true, y
    // deja el resto tal cual (pending). Nunca reescribe el resto de columnas de la entidad — evita
    // que un snapshot leído antes del request pise una edición local concurrente al mismo registro.
    // Relanza CancellationException para no romper la cancelación cooperativa de PushWorker.
    private suspend fun <TEntity, TResponse> pushBatch(
        pending: List<TEntity>,
        id: (TEntity) -> String,
        push: suspend (List<TEntity>) -> List<TResponse>,
        responseId: (TResponse) -> String,
        responseSuccess: (TResponse) -> Boolean,
        markSynced: suspend (List<String>) -> Unit,
    ): Boolean {
        if (pending.isEmpty()) return true
        return try {
            val response = push(pending)
            val successfulIds = response.filter(responseSuccess).map(responseId).toSet()
            if (successfulIds.isNotEmpty()) markSynced(successfulIds.toList())
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun pushClientes(tenantId: String): Boolean {
        val pending = try {
            clientDao.getPending(tenantId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        return pushBatch(
            pending = pending,
            id = { it.id },
            push = { syncApiService.pushClientes(it.map { entity -> entity.toSyncDto() }) },
            responseId = { it.id },
            responseSuccess = { it.success },
            markSynced = { clientDao.markSynced(it) },
        )
    }

    private suspend fun pushProductos(tenantId: String): Boolean {
        val pending = try {
            productDao.getPending(tenantId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        return pushBatch(
            pending = pending,
            id = { it.id },
            push = { syncApiService.pushProductos(it.map { entity -> entity.toSyncDto() }) },
            responseId = { it.id },
            responseSuccess = { it.success },
            markSynced = { productDao.markSynced(it) },
        )
    }

    private suspend fun pushVariantes(tenantId: String): Boolean {
        val pending = try {
            productVariantDao.getPending(tenantId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        return pushBatch(
            pending = pending,
            id = { it.id },
            push = { syncApiService.pushVariantes(it.map { entity -> entity.toSyncDto() }) },
            responseId = { it.id },
            responseSuccess = { it.success },
            markSynced = { productVariantDao.markSynced(it) },
        )
    }

    private suspend fun pushVentas(tenantId: String): Boolean {
        val pending = try {
            saleDao.getPending(tenantId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        return pushBatch(
            pending = pending,
            id = { it.id },
            push = { syncApiService.pushVentas(it.map { entity -> entity.toSyncDto() }) },
            responseId = { it.id },
            responseSuccess = { it.success },
            markSynced = { saleDao.markSynced(it) },
        )
    }

    private suspend fun pushItemsVenta(tenantId: String): Boolean {
        val pending = try {
            saleItemDao.getPending(tenantId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        return pushBatch(
            pending = pending,
            id = { it.id },
            push = { syncApiService.pushItemsVenta(it.map { entity -> entity.toSyncDto() }) },
            responseId = { it.id },
            responseSuccess = { it.success },
            markSynced = { saleItemDao.markSynced(it) },
        )
    }

    private suspend fun pushParcialidades(tenantId: String): Boolean {
        val pending = try {
            installmentDao.getPending(tenantId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        return pushBatch(
            pending = pending,
            id = { it.id },
            push = { syncApiService.pushParcialidades(it.map { entity -> entity.toSyncDto() }) },
            responseId = { it.id },
            responseSuccess = { it.success },
            markSynced = { installmentDao.markSynced(it) },
        )
    }

    private suspend fun pushCobros(tenantId: String): Boolean {
        val pending = try {
            paymentDao.getPending(tenantId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        return pushBatch(
            pending = pending,
            id = { it.id },
            push = { syncApiService.pushCobros(it.map { entity -> entity.toSyncDto() }) },
            responseId = { it.id },
            responseSuccess = { it.success },
            markSynced = { paymentDao.markSynced(it) },
        )
    }

    private suspend fun pushCreditosAFavor(tenantId: String): Boolean {
        val pending = try {
            creditBalanceDao.getPending(tenantId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        return pushBatch(
            pending = pending,
            id = { it.id },
            push = { syncApiService.pushCreditosAFavor(it.map { entity -> entity.toSyncDto() }) },
            responseId = { it.id },
            responseSuccess = { it.success },
            markSynced = { creditBalanceDao.markSynced(it) },
        )
    }

    private suspend fun pushSettings(): Boolean {
        val pending = try {
            settingsDao.getPending()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        return pushBatch(
            pending = pending,
            id = { it.key },
            push = { syncApiService.pushSettings(it.map { entity -> entity.toSyncDto() }) },
            responseId = { it.key },
            responseSuccess = { it.success },
            markSynced = { settingsDao.markSynced(it) },
        )
    }

    private fun ClientEntity.toSyncDto() = ClientSyncDto(
        id = id,
        fkTenant = fkTenant,
        name = name,
        phone = phone,
        rfc = rfc,
        address = address,
        notes = notes,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )

    private fun ProductEntity.toSyncDto() = ProductSyncDto(
        id = id,
        fkTenant = fkTenant,
        name = name,
        price = price.toPlainString(),
        taxRate = taxRate.toPlainString(),
        isActive = isActive,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )

    private fun ProductVariantEntity.toSyncDto() = ProductVariantSyncDto(
        id = id,
        fkTenant = fkTenant,
        fkProduct = fkProduct,
        name = name,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )

    private fun SaleEntity.toSyncDto() = SaleSyncDto(
        id = id,
        fkTenant = fkTenant,
        fkClient = fkClient,
        folio = folio,
        total = total.toPlainString(),
        subtotal = subtotal.toPlainString(),
        tax = tax.toPlainString(),
        status = status,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )

    private fun SaleItemEntity.toSyncDto() = SaleItemSyncDto(
        id = id,
        fkTenant = fkTenant,
        fkSale = fkSale,
        fkProduct = fkProduct,
        fkVariant = fkVariant,
        productName = productName,
        variantName = variantName,
        quantity = quantity,
        unitPrice = unitPrice.toPlainString(),
        taxRate = taxRate.toPlainString(),
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )

    private fun InstallmentEntity.toSyncDto() = InstallmentSyncDto(
        id = id,
        fkTenant = fkTenant,
        fkSale = fkSale,
        amount = amount.toPlainString(),
        dueDate = dueDate.toString(),
        status = status,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )

    private fun PaymentEntity.toSyncDto() = PaymentSyncDto(
        id = id,
        fkTenant = fkTenant,
        fkSale = fkSale,
        fkInstallment = fkInstallment,
        method = method,
        amount = amount.toPlainString(),
        paidAt = paidAt.toString(),
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )

    private fun CreditBalanceEntity.toSyncDto() = CreditBalanceSyncDto(
        id = id,
        fkTenant = fkTenant,
        fkClient = fkClient,
        amount = amount.toPlainString(),
        origin = origin,
        fkOriginSale = fkOriginSale,
        appliedAt = appliedAt?.toString(),
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )

    // SettingsEntity no tiene updatedAt (solo createdAt, ver Dev Notes de la historia sobre por
    // qué Settings no se rediseña por completo) — se reutiliza createdAt para ambos campos del DTO.
    private fun SettingsEntity.toSyncDto() = SettingSyncDto(
        key = key,
        value = value,
        createdAt = createdAt.toString(),
        updatedAt = createdAt.toString(),
    )
}

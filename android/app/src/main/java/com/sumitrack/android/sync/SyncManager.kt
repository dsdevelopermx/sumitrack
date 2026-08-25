package com.sumitrack.android.sync

import com.sumitrack.android.data.local.dao.ClientDao
import com.sumitrack.android.data.local.dao.ConflictLogDao
import com.sumitrack.android.data.local.dao.CreditBalanceDao
import com.sumitrack.android.data.local.dao.InstallmentDao
import com.sumitrack.android.data.local.dao.PaymentDao
import com.sumitrack.android.data.local.dao.ProductDao
import com.sumitrack.android.data.local.dao.ProductVariantDao
import com.sumitrack.android.data.local.dao.SaleDao
import com.sumitrack.android.data.local.dao.SaleItemDao
import com.sumitrack.android.data.local.dao.SettingsDao
import com.sumitrack.android.data.local.entities.ClientEntity
import com.sumitrack.android.data.local.entities.ConflictLogEntity
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
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    private val conflictLogDao: ConflictLogDao,
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
            pushSettings(tenantId)
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
    //
    // Un conflicto (Historia 4.4) es OTRO resultado por-registro, igual que un rechazo de
    // validación ya tolerado desde 4.1 — no afecta el resultado agregado (Boolean) de este batch,
    // solo marca `sync_status = conflict` (en vez de dejarlo `pending`) y persiste un
    // ConflictLogEntity con ambas versiones para que S-15/S-14 lo resuelvan más tarde.
    private suspend fun <TEntity, TResponse> pushBatch(
        pending: List<TEntity>,
        id: (TEntity) -> String,
        push: suspend (List<TEntity>) -> List<TResponse>,
        responseId: (TResponse) -> String,
        responseSuccess: (TResponse) -> Boolean,
        responseConflict: (TResponse) -> Boolean,
        responseServerSnapshot: (TResponse) -> String?,
        markSynced: suspend (List<String>) -> Unit,
        markConflict: suspend (List<String>) -> Unit,
        localSnapshot: (TEntity) -> String,
        entityType: String,
        tenantId: String,
    ): Boolean {
        if (pending.isEmpty()) return true
        return try {
            val response = push(pending)
            // Se excluyen explícitamente los ids en conflicto de successfulIds — si una respuesta
            // alguna vez trajera success=true Y conflict=true simultáneos, el conflicto manda.
            val conflicted = response.filter(responseConflict)
            val conflictedIds = conflicted.map(responseId).toSet()
            val successfulIds = response.filter { responseSuccess(it) && !responseConflict(it) }.map(responseId).toSet()
            if (successfulIds.isNotEmpty()) markSynced(successfulIds.toList())

            if (conflicted.isNotEmpty()) {
                markConflict(conflictedIds.toList())
                val pendingById = pending.associateBy(id)
                conflicted.forEach { item ->
                    val recordId = responseId(item)
                    val localEntity = pendingById[recordId] ?: return@forEach
                    // Dedup: si ya hay una entrada sin resolver para este registro (ej. se re-envió
                    // en un ciclo de push posterior y sigue en conflicto), se actualiza esa misma
                    // entrada en vez de insertar una segunda — evita acumular filas de log
                    // duplicadas para el mismo conflicto sin resolver.
                    val existingUnresolved = conflictLogDao.getLatestUnresolvedFor(entityType, recordId)
                    val snapshot = ConflictLogEntity(
                        id = existingUnresolved?.id ?: UUID.randomUUID().toString(),
                        fkTenant = tenantId,
                        entityType = entityType,
                        recordId = recordId,
                        localSnapshotJson = localSnapshot(localEntity),
                        serverSnapshotJson = responseServerSnapshot(item) ?: "{}",
                        detectedAt = Instant.now(),
                    )
                    if (existingUnresolved != null) conflictLogDao.update(snapshot) else conflictLogDao.insert(snapshot)
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun pushClientes(tenantId: String): Boolean {
        val pending = try {
            clientDao.getPending(tenantId) + clientDao.getConflicted(tenantId)
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
            responseConflict = { it.conflict },
            responseServerSnapshot = { it.serverSnapshot },
            markSynced = { clientDao.markSynced(it) },
            markConflict = { clientDao.markConflict(it) },
            localSnapshot = { Json.encodeToString(it.toSyncDto()) },
            entityType = "clientes",
            tenantId = tenantId,
        )
    }

    private suspend fun pushProductos(tenantId: String): Boolean {
        val pending = try {
            productDao.getPending(tenantId) + productDao.getConflicted(tenantId)
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
            responseConflict = { it.conflict },
            responseServerSnapshot = { it.serverSnapshot },
            markSynced = { productDao.markSynced(it) },
            markConflict = { productDao.markConflict(it) },
            localSnapshot = { Json.encodeToString(it.toSyncDto()) },
            entityType = "productos",
            tenantId = tenantId,
        )
    }

    private suspend fun pushVariantes(tenantId: String): Boolean {
        val pending = try {
            productVariantDao.getPending(tenantId) + productVariantDao.getConflicted(tenantId)
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
            responseConflict = { it.conflict },
            responseServerSnapshot = { it.serverSnapshot },
            markSynced = { productVariantDao.markSynced(it) },
            markConflict = { productVariantDao.markConflict(it) },
            localSnapshot = { Json.encodeToString(it.toSyncDto()) },
            entityType = "variantes",
            tenantId = tenantId,
        )
    }

    private suspend fun pushVentas(tenantId: String): Boolean {
        val pending = try {
            saleDao.getPending(tenantId) + saleDao.getConflicted(tenantId)
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
            responseConflict = { it.conflict },
            responseServerSnapshot = { it.serverSnapshot },
            markSynced = { saleDao.markSynced(it) },
            markConflict = { saleDao.markConflict(it) },
            localSnapshot = { Json.encodeToString(it.toSyncDto()) },
            entityType = "ventas",
            tenantId = tenantId,
        )
    }

    private suspend fun pushItemsVenta(tenantId: String): Boolean {
        val pending = try {
            saleItemDao.getPending(tenantId) + saleItemDao.getConflicted(tenantId)
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
            responseConflict = { it.conflict },
            responseServerSnapshot = { it.serverSnapshot },
            markSynced = { saleItemDao.markSynced(it) },
            markConflict = { saleItemDao.markConflict(it) },
            localSnapshot = { Json.encodeToString(it.toSyncDto()) },
            entityType = "items_venta",
            tenantId = tenantId,
        )
    }

    private suspend fun pushParcialidades(tenantId: String): Boolean {
        val pending = try {
            installmentDao.getPending(tenantId) + installmentDao.getConflicted(tenantId)
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
            responseConflict = { it.conflict },
            responseServerSnapshot = { it.serverSnapshot },
            markSynced = { installmentDao.markSynced(it) },
            markConflict = { installmentDao.markConflict(it) },
            localSnapshot = { Json.encodeToString(it.toSyncDto()) },
            entityType = "parcialidades",
            tenantId = tenantId,
        )
    }

    private suspend fun pushCobros(tenantId: String): Boolean {
        val pending = try {
            paymentDao.getPending(tenantId) + paymentDao.getConflicted(tenantId)
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
            responseConflict = { it.conflict },
            responseServerSnapshot = { it.serverSnapshot },
            markSynced = { paymentDao.markSynced(it) },
            markConflict = { paymentDao.markConflict(it) },
            localSnapshot = { Json.encodeToString(it.toSyncDto()) },
            entityType = "cobros",
            tenantId = tenantId,
        )
    }

    private suspend fun pushCreditosAFavor(tenantId: String): Boolean {
        val pending = try {
            creditBalanceDao.getPending(tenantId) + creditBalanceDao.getConflicted(tenantId)
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
            responseConflict = { it.conflict },
            responseServerSnapshot = { it.serverSnapshot },
            markSynced = { creditBalanceDao.markSynced(it) },
            markConflict = { creditBalanceDao.markConflict(it) },
            localSnapshot = { Json.encodeToString(it.toSyncDto()) },
            entityType = "creditos_a_favor",
            tenantId = tenantId,
        )
    }

    private suspend fun pushSettings(tenantId: String): Boolean {
        val pending = try {
            settingsDao.getPending() + settingsDao.getConflicted()
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
            responseConflict = { it.conflict },
            responseServerSnapshot = { it.serverSnapshot },
            markSynced = { settingsDao.markSynced(it) },
            markConflict = { settingsDao.markConflict(it) },
            localSnapshot = { Json.encodeToString(it.toSyncDto()) },
            entityType = "settings",
            tenantId = tenantId,
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

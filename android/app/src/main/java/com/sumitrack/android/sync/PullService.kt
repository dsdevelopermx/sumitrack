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
import com.sumitrack.android.data.local.dao.SyncMetadataDao
import com.sumitrack.android.data.local.entities.ClientEntity
import com.sumitrack.android.data.local.entities.CreditBalanceEntity
import com.sumitrack.android.data.local.entities.InstallmentEntity
import com.sumitrack.android.data.local.entities.PaymentEntity
import com.sumitrack.android.data.local.entities.ProductEntity
import com.sumitrack.android.data.local.entities.ProductVariantEntity
import com.sumitrack.android.data.local.entities.SaleEntity
import com.sumitrack.android.data.local.entities.SaleItemEntity
import com.sumitrack.android.data.local.entities.SettingsEntity
import com.sumitrack.android.data.local.entities.SyncMetadataEntity
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
import com.sumitrack.android.data.repositories.FolioBaselineStore
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Descarga el delta (o todo, en el primer pull) de las 9 entidades sincronizables y lo aplica
 * localmente, más un piso rápido del contador de folios (AR-10). Antes de escribir cada registro,
 * se descartan los ids que ya están `sync_status = pending` localmente (ver Dev Notes de la
 * historia 4.2 → "Por qué el pull salta los registros pending") — evita pisar una edición local
 * sin sincronizar todavía. `since` se captura ANTES de cada request (no al terminar) para no
 * perder registros escritos por el servidor durante la ventana del propio request; un pequeño
 * traslape en el próximo pull es inofensivo porque el upsert es idempotente.
 *
 * [pullAll] intenta las 10 llamadas (folio-count + 9 entidades) de forma independiente — una
 * entidad que falla NO detiene a las demás (hallazgo de code review: encadenar con `&&` dejaba
 * sin intentar a todo lo que viniera después de la primera falla, incluso si sus propios
 * endpoints estaban sanos). El resultado final es `true` solo si TODAS tuvieron éxito.
 *
 * Cada `pullXxx` solo avanza su `last_sync_at` cuando NINGÚN registro fue saltado por el guard de
 * `pending` — si algo se saltó, el watermark se queda donde estaba para que el servidor lo vuelva
 * a ofrecer en el próximo pull (hallazgo de code review: avanzar el watermark igual dejaba ese
 * registro inalcanzable para siempre en pulls futuros).
 */
@Singleton
class PullService @Inject constructor(
    private val clientDao: ClientDao,
    private val productDao: ProductDao,
    private val productVariantDao: ProductVariantDao,
    private val saleDao: SaleDao,
    private val saleItemDao: SaleItemDao,
    private val installmentDao: InstallmentDao,
    private val paymentDao: PaymentDao,
    private val creditBalanceDao: CreditBalanceDao,
    private val settingsDao: SettingsDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val syncApiService: SyncApiService,
    private val folioBaselineStore: FolioBaselineStore,
) {
    suspend fun pullAll(tenantId: String): Boolean {
        val folioOk = pullFolioCount()
        val clientesOk = pullClientes(tenantId)
        val productosOk = pullProductos(tenantId)
        val variantesOk = pullVariantes(tenantId)
        val ventasOk = pullVentas(tenantId)
        val itemsVentaOk = pullItemsVenta(tenantId)
        val parcialidadesOk = pullParcialidades(tenantId)
        val cobrosOk = pullCobros(tenantId)
        val creditosAFavorOk = pullCreditosAFavor(tenantId)
        val settingsOk = pullSettings()
        return folioOk && clientesOk && productosOk && variantesOk && ventasOk &&
            itemsVentaOk && parcialidadesOk && cobrosOk && creditosAFavorOk && settingsOk
    }

    // Piso rápido del contador de folios (AR-10) — una sola llamada liviana, independiente y
    // mucho más rápida que esperar el pull completo de `ventas`. Se intenta primero para que la
    // ventana de posible colisión de folio con AC-1 (pull no bloqueante) sea lo más corta posible.
    private suspend fun pullFolioCount(): Boolean {
        return try {
            val response = syncApiService.getFolioCount()
            folioBaselineStore.saveFolioBaseline(response.count)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun pullClientes(tenantId: String): Boolean {
        val since = syncMetadataDao.getLastSyncAt("clientes")
        val requestedAt = Instant.now()
        return try {
            val remote = syncApiService.pullClientes(since?.toString())
            val pendingIds = clientDao.getPending(tenantId).map { it.id }.toSet()
            val toUpsert = remote.filterNot { it.id in pendingIds }.map { it.toEntity() }
            if (toUpsert.isNotEmpty()) clientDao.upsertAll(toUpsert)
            if (toUpsert.size == remote.size) syncMetadataDao.setLastSyncAt(SyncMetadataEntity("clientes", requestedAt))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun pullProductos(tenantId: String): Boolean {
        val since = syncMetadataDao.getLastSyncAt("productos")
        val requestedAt = Instant.now()
        return try {
            val remote = syncApiService.pullProductos(since?.toString())
            val pendingIds = productDao.getPending(tenantId).map { it.id }.toSet()
            val toUpsert = remote.filterNot { it.id in pendingIds }.map { it.toEntity() }
            if (toUpsert.isNotEmpty()) productDao.upsertAll(toUpsert)
            if (toUpsert.size == remote.size) syncMetadataDao.setLastSyncAt(SyncMetadataEntity("productos", requestedAt))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun pullVariantes(tenantId: String): Boolean {
        val since = syncMetadataDao.getLastSyncAt("variantes")
        val requestedAt = Instant.now()
        return try {
            val remote = syncApiService.pullVariantes(since?.toString())
            val pendingIds = productVariantDao.getPending(tenantId).map { it.id }.toSet()
            val toUpsert = remote.filterNot { it.id in pendingIds }.map { it.toEntity() }
            if (toUpsert.isNotEmpty()) productVariantDao.upsertAll(toUpsert)
            if (toUpsert.size == remote.size) syncMetadataDao.setLastSyncAt(SyncMetadataEntity("variantes", requestedAt))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun pullVentas(tenantId: String): Boolean {
        val since = syncMetadataDao.getLastSyncAt("ventas")
        val requestedAt = Instant.now()
        return try {
            val remote = syncApiService.pullVentas(since?.toString())
            val pendingIds = saleDao.getPending(tenantId).map { it.id }.toSet()
            val toUpsert = remote.filterNot { it.id in pendingIds }.map { it.toEntity() }
            if (toUpsert.isNotEmpty()) saleDao.upsertAll(toUpsert)
            if (toUpsert.size == remote.size) syncMetadataDao.setLastSyncAt(SyncMetadataEntity("ventas", requestedAt))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun pullItemsVenta(tenantId: String): Boolean {
        val since = syncMetadataDao.getLastSyncAt("items_venta")
        val requestedAt = Instant.now()
        return try {
            val remote = syncApiService.pullItemsVenta(since?.toString())
            val pendingIds = saleItemDao.getPending(tenantId).map { it.id }.toSet()
            val toUpsert = remote.filterNot { it.id in pendingIds }.map { it.toEntity() }
            if (toUpsert.isNotEmpty()) saleItemDao.upsertAll(toUpsert)
            if (toUpsert.size == remote.size) syncMetadataDao.setLastSyncAt(SyncMetadataEntity("items_venta", requestedAt))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun pullParcialidades(tenantId: String): Boolean {
        val since = syncMetadataDao.getLastSyncAt("parcialidades")
        val requestedAt = Instant.now()
        return try {
            val remote = syncApiService.pullParcialidades(since?.toString())
            val pendingIds = installmentDao.getPending(tenantId).map { it.id }.toSet()
            val toUpsert = remote.filterNot { it.id in pendingIds }.map { it.toEntity() }
            if (toUpsert.isNotEmpty()) installmentDao.upsertAll(toUpsert)
            if (toUpsert.size == remote.size) syncMetadataDao.setLastSyncAt(SyncMetadataEntity("parcialidades", requestedAt))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun pullCobros(tenantId: String): Boolean {
        val since = syncMetadataDao.getLastSyncAt("cobros")
        val requestedAt = Instant.now()
        return try {
            val remote = syncApiService.pullCobros(since?.toString())
            val pendingIds = paymentDao.getPending(tenantId).map { it.id }.toSet()
            val toUpsert = remote.filterNot { it.id in pendingIds }.map { it.toEntity() }
            if (toUpsert.isNotEmpty()) paymentDao.upsertAll(toUpsert)
            if (toUpsert.size == remote.size) syncMetadataDao.setLastSyncAt(SyncMetadataEntity("cobros", requestedAt))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun pullCreditosAFavor(tenantId: String): Boolean {
        val since = syncMetadataDao.getLastSyncAt("creditos_a_favor")
        val requestedAt = Instant.now()
        return try {
            val remote = syncApiService.pullCreditosAFavor(since?.toString())
            val pendingIds = creditBalanceDao.getPending(tenantId).map { it.id }.toSet()
            val toUpsert = remote.filterNot { it.id in pendingIds }.map { it.toEntity() }
            if (toUpsert.isNotEmpty()) creditBalanceDao.upsertAll(toUpsert)
            if (toUpsert.size == remote.size) syncMetadataDao.setLastSyncAt(SyncMetadataEntity("creditos_a_favor", requestedAt))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun pullSettings(): Boolean {
        val since = syncMetadataDao.getLastSyncAt("settings")
        val requestedAt = Instant.now()
        return try {
            val remote = syncApiService.pullSettings(since?.toString())
            val pendingKeys = settingsDao.getPending().map { it.key }.toSet()
            val toUpsert = remote.filterNot { it.key in pendingKeys }.map { it.toEntity() }
            if (toUpsert.isNotEmpty()) settingsDao.upsertAll(toUpsert)
            if (toUpsert.size == remote.size) syncMetadataDao.setLastSyncAt(SyncMetadataEntity("settings", requestedAt))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private fun ClientSyncDto.toEntity() = ClientEntity(
        id = id,
        fkTenant = fkTenant,
        name = name,
        phone = phone,
        rfc = rfc,
        address = address,
        notes = notes,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        syncStatus = "synced",
    )

    private fun ProductSyncDto.toEntity() = ProductEntity(
        id = id,
        fkTenant = fkTenant,
        name = name,
        price = BigDecimal(price),
        taxRate = BigDecimal(taxRate),
        isActive = isActive,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        syncStatus = "synced",
    )

    private fun ProductVariantSyncDto.toEntity() = ProductVariantEntity(
        id = id,
        fkTenant = fkTenant,
        fkProduct = fkProduct,
        name = name,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        syncStatus = "synced",
    )

    private fun SaleSyncDto.toEntity() = SaleEntity(
        id = id,
        fkTenant = fkTenant,
        fkClient = fkClient,
        folio = folio,
        total = BigDecimal(total),
        subtotal = BigDecimal(subtotal),
        tax = BigDecimal(tax),
        status = status,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        syncStatus = "synced",
    )

    private fun SaleItemSyncDto.toEntity() = SaleItemEntity(
        id = id,
        fkTenant = fkTenant,
        fkSale = fkSale,
        fkProduct = fkProduct,
        fkVariant = fkVariant,
        productName = productName,
        variantName = variantName,
        quantity = quantity,
        unitPrice = BigDecimal(unitPrice),
        taxRate = BigDecimal(taxRate),
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        syncStatus = "synced",
    )

    private fun InstallmentSyncDto.toEntity() = InstallmentEntity(
        id = id,
        fkTenant = fkTenant,
        fkSale = fkSale,
        amount = BigDecimal(amount),
        dueDate = Instant.parse(dueDate),
        status = status,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        syncStatus = "synced",
    )

    private fun PaymentSyncDto.toEntity() = PaymentEntity(
        id = id,
        fkTenant = fkTenant,
        fkSale = fkSale,
        fkInstallment = fkInstallment,
        method = method,
        amount = BigDecimal(amount),
        paidAt = Instant.parse(paidAt),
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        syncStatus = "synced",
    )

    private fun CreditBalanceSyncDto.toEntity() = CreditBalanceEntity(
        id = id,
        fkTenant = fkTenant,
        fkClient = fkClient,
        amount = BigDecimal(amount),
        origin = origin,
        fkOriginSale = fkOriginSale,
        appliedAt = appliedAt?.let { Instant.parse(it) },
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        syncStatus = "synced",
    )

    // SettingsEntity no tiene updatedAt (ver Dev Notes de Historia 4.1) — solo createdAt se
    // persiste localmente; el `updatedAt` del DTO se usa nada más para el filtro delta del server.
    private fun SettingSyncDto.toEntity() = SettingsEntity(
        key = key,
        value = value,
        createdAt = Instant.parse(createdAt),
        syncStatus = "synced",
    )
}

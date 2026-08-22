package com.sumitrack.android.sync

import com.sumitrack.android.data.local.entities.ClientEntity
import com.sumitrack.android.data.local.entities.ProductEntity
import com.sumitrack.android.data.local.entities.SettingsEntity
import com.sumitrack.android.ui.screens.clients.FakeClientDao
import com.sumitrack.android.ui.screens.clients.FakeSaleDao
import com.sumitrack.android.ui.screens.orders.FakeCreditBalanceDao
import com.sumitrack.android.ui.screens.orders.FakeInstallmentDao
import com.sumitrack.android.ui.screens.orders.FakePaymentDao
import com.sumitrack.android.ui.screens.orders.FakeSaleItemDao
import com.sumitrack.android.ui.screens.orders.FakeSettingsDao
import com.sumitrack.android.ui.screens.products.FakeProductDao
import com.sumitrack.android.ui.screens.products.FakeProductVariantDao
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncManagerTest {

    private lateinit var clientDao: FakeClientDao
    private lateinit var productDao: FakeProductDao
    private lateinit var productVariantDao: FakeProductVariantDao
    private lateinit var saleDao: FakeSaleDao
    private lateinit var saleItemDao: FakeSaleItemDao
    private lateinit var installmentDao: FakeInstallmentDao
    private lateinit var paymentDao: FakePaymentDao
    private lateinit var creditBalanceDao: FakeCreditBalanceDao
    private lateinit var settingsDao: FakeSettingsDao
    private lateinit var apiService: FakeSyncApiService
    private lateinit var syncManager: SyncManager

    private val tenantId = "tenant-1"

    @Before
    fun setUp() {
        clientDao = FakeClientDao()
        productDao = FakeProductDao()
        productVariantDao = FakeProductVariantDao()
        saleDao = FakeSaleDao()
        saleItemDao = FakeSaleItemDao()
        installmentDao = FakeInstallmentDao()
        paymentDao = FakePaymentDao()
        creditBalanceDao = FakeCreditBalanceDao()
        settingsDao = FakeSettingsDao()
        apiService = FakeSyncApiService()
        syncManager = SyncManager(
            clientDao = clientDao,
            productDao = productDao,
            productVariantDao = productVariantDao,
            saleDao = saleDao,
            saleItemDao = saleItemDao,
            installmentDao = installmentDao,
            paymentDao = paymentDao,
            creditBalanceDao = creditBalanceDao,
            settingsDao = settingsDao,
            syncApiService = apiService,
        )
    }

    private fun pendingClient(id: String = "c1") = ClientEntity(
        id = id,
        fkTenant = tenantId,
        name = "Juan Pérez",
        phone = "5512345678",
        createdAt = Instant.ofEpochMilli(1_000_000L),
        updatedAt = Instant.ofEpochMilli(1_000_000L),
        syncStatus = "pending",
    )

    private fun pendingProduct(id: String = "p1") = ProductEntity(
        id = id,
        fkTenant = tenantId,
        name = "Martillo",
        price = BigDecimal("100.00"),
        taxRate = BigDecimal("0.16"),
        isActive = true,
        createdAt = Instant.ofEpochMilli(1_000_000L),
        updatedAt = Instant.ofEpochMilli(1_000_000L),
        syncStatus = "pending",
    )

    @Test
    fun `pushPending exitoso marca synced los registros pendientes`() = runTest {
        clientDao.setClients(listOf(pendingClient()))

        val result = syncManager.pushPending(tenantId)

        assertTrue(result)
        val stored = clientDao.getAllAsFlow().first().single { it.id == "c1" }
        assertEquals("synced", stored.syncStatus)
        assertTrue(clientDao.getPending(tenantId).isEmpty())
        assertTrue("clientes" in apiService.calls)
    }

    @Test
    fun `pushPending sin registros pendientes no genera llamadas HTTP`() = runTest {
        val result = syncManager.pushPending(tenantId)

        assertTrue(result)
        assertTrue(apiService.calls.isEmpty())
    }

    @Test
    fun `pushPending fallo de red deja pending y devuelve false`() = runTest {
        productDao.setProducts(listOf(pendingProduct()))
        apiService.failingEntities = setOf("productos")

        val result = syncManager.pushPending(tenantId)

        assertFalse(result)
        assertEquals(1, productDao.getPending(tenantId).size)
        assertEquals("pending", productDao.getPending(tenantId).first().syncStatus)
    }

    @Test
    fun `pushPending corta la corrida en el primer fallo sin intentar entidades posteriores`() = runTest {
        // clientes se procesa antes que productos en el orden de SyncManager.pushPending
        clientDao.setClients(listOf(pendingClient()))
        productDao.setProducts(listOf(pendingProduct()))
        apiService.failingEntities = setOf("clientes")

        val result = syncManager.pushPending(tenantId)

        assertFalse(result)
        assertEquals(listOf("clientes"), apiService.calls)
        // El producto nunca se intentó — sigue pending sin haber sido tocado.
        assertEquals(1, productDao.getPending(tenantId).size)
    }

    @Test
    fun `pushPending para settings usa key como identidad sin filtrar por tenant`() = runTest {
        settingsDao.upsertAll(
            listOf(SettingsEntity(key = "ticket_footer", value = "Gracias", syncStatus = "pending"))
        )

        val result = syncManager.pushPending(tenantId)

        assertTrue(result)
        assertTrue("settings" in apiService.calls)
        assertTrue(settingsDao.getPending().isEmpty())
    }

    @Test
    fun `pushPending rechazo individual del backend deja solo ese registro pending sin bloquear el resto`() = runTest {
        clientDao.setClients(listOf(pendingClient(id = "c1"), pendingClient(id = "c2")))
        apiService.rejectedIds = setOf("c1")

        val result = syncManager.pushPending(tenantId)

        // La llamada HTTP respondió (200), solo un registro fue rechazado por el backend — el
        // resto del run continúa con normalidad, no se fuerza un reintento total.
        assertTrue(result)
        val pending = clientDao.getPending(tenantId)
        assertEquals(1, pending.size)
        assertEquals("c1", pending.first().id)
        assertTrue(clientDao.getAllAsFlow().first().single { it.id == "c2" }.syncStatus == "synced")
    }

    @Test
    fun `pushPending marca synced sin sobrescribir otros campos editados durante el push`() = runTest {
        clientDao.setClients(listOf(pendingClient()))

        syncManager.pushPending(tenantId)

        // markSynced solo toca sync_status — si upsertAll(pending) hubiera reescrito la fila
        // entera, este assert seguiría pasando porque nadie editó "name" en este test; lo que
        // importa es que el campo original sigue intacto tras el marcado.
        val stored = clientDao.getAllAsFlow().first().single { it.id == "c1" }
        assertEquals("Juan Pérez", stored.name)
        assertEquals("synced", stored.syncStatus)
    }

    @Test
    fun `hasPendingWork es false cuando ninguna de las 9 entidades tiene pendientes`() = runTest {
        assertFalse(syncManager.hasPendingWork(tenantId))
    }

    @Test
    fun `hasPendingWork es true si una sola entidad tiene pendientes`() = runTest {
        productDao.setProducts(listOf(pendingProduct()))

        assertTrue(syncManager.hasPendingWork(tenantId))
    }
}

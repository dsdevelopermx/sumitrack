package com.sumitrack.android.sync

import com.sumitrack.android.data.local.entities.ClientEntity
import com.sumitrack.android.data.remote.dto.ClientSyncDto
import com.sumitrack.android.data.remote.dto.ProductSyncDto
import com.sumitrack.android.ui.screens.clients.FakeClientDao
import com.sumitrack.android.ui.screens.clients.FakeSaleDao
import com.sumitrack.android.ui.screens.orders.FakeCreditBalanceDao
import com.sumitrack.android.ui.screens.orders.FakeInstallmentDao
import com.sumitrack.android.ui.screens.orders.FakePaymentDao
import com.sumitrack.android.ui.screens.orders.FakeSaleItemDao
import com.sumitrack.android.ui.screens.orders.FakeSettingsDao
import com.sumitrack.android.ui.screens.products.FakeProductDao
import com.sumitrack.android.ui.screens.products.FakeProductVariantDao
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PullServiceTest {

    private lateinit var clientDao: FakeClientDao
    private lateinit var productDao: FakeProductDao
    private lateinit var productVariantDao: FakeProductVariantDao
    private lateinit var saleDao: FakeSaleDao
    private lateinit var saleItemDao: FakeSaleItemDao
    private lateinit var installmentDao: FakeInstallmentDao
    private lateinit var paymentDao: FakePaymentDao
    private lateinit var creditBalanceDao: FakeCreditBalanceDao
    private lateinit var settingsDao: FakeSettingsDao
    private lateinit var syncMetadataDao: FakeSyncMetadataDao
    private lateinit var apiService: FakeSyncApiService
    private lateinit var folioBaselineStore: FakeFolioBaselineStore
    private lateinit var pullService: PullService

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
        syncMetadataDao = FakeSyncMetadataDao()
        apiService = FakeSyncApiService()
        folioBaselineStore = FakeFolioBaselineStore()
        pullService = PullService(
            clientDao = clientDao,
            productDao = productDao,
            productVariantDao = productVariantDao,
            saleDao = saleDao,
            saleItemDao = saleItemDao,
            installmentDao = installmentDao,
            paymentDao = paymentDao,
            creditBalanceDao = creditBalanceDao,
            settingsDao = settingsDao,
            syncMetadataDao = syncMetadataDao,
            syncApiService = apiService,
            folioBaselineStore = folioBaselineStore,
        )
    }

    private fun remoteClient(id: String, name: String = "Juan Pérez") = ClientSyncDto(
        id = id,
        fkTenant = tenantId,
        name = name,
        phone = "5512345678",
        createdAt = Instant.ofEpochMilli(1_000_000L).toString(),
        updatedAt = Instant.ofEpochMilli(1_000_000L).toString(),
    )

    @Test
    fun `pullAll exitoso hace upsert y guarda last_sync_at`() = runTest {
        apiService.clientesPullResponse = listOf(remoteClient("c1"))

        val result = pullService.pullAll(tenantId)

        assertTrue(result)
        val stored = clientDao.getAllAsFlow().first().single { it.id == "c1" }
        assertEquals("Juan Pérez", stored.name)
        assertEquals("synced", stored.syncStatus)
        assertEquals(Instant.ofEpochMilli(1_000_000L), stored.createdAt)
        assertTrue(syncMetadataDao.getLastSyncAt("clientes") != null)
    }

    @Test
    fun `primer pull pasa since null a la API`() = runTest {
        // NOTA: FakeSyncApiService es un fake a nivel JVM, no Retrofit real — esto prueba que
        // PullService pasa `null` cuando no hay last_sync_at guardado, no que Retrofit omita el
        // query param `since` en la URL real (eso depende de la anotación @Query de Retrofit).
        pullService.pullAll(tenantId)

        assertNull(apiService.pullCalls["clientes"])
    }

    @Test
    fun `pull posterior usa el last_sync_at guardado`() = runTest {
        pullService.pullAll(tenantId) // primer pull, guarda last_sync_at
        val savedSince = syncMetadataDao.getLastSyncAt("clientes")

        pullService.pullAll(tenantId) // segundo pull

        assertEquals(savedSince.toString(), apiService.pullCalls["clientes"])
    }

    @Test
    fun `registro pending localmente se salta y no se sobrescribe`() = runTest {
        clientDao.setClients(listOf(
            ClientEntity(
                id = "c1",
                fkTenant = tenantId,
                name = "Edición local sin sincronizar",
                phone = "0000000000",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                syncStatus = "pending",
            )
        ))
        apiService.clientesPullResponse = listOf(remoteClient("c1", name = "Versión del servidor"))

        val result = pullService.pullAll(tenantId)

        assertTrue(result)
        val stored = clientDao.getAllAsFlow().first().single { it.id == "c1" }
        assertEquals("Edición local sin sincronizar", stored.name)
        assertEquals("pending", stored.syncStatus)
    }

    @Test
    fun `registro saltado por pending no avanza last_sync_at`() = runTest {
        // Hallazgo de code review: si se avanzara last_sync_at igual, el registro del servidor
        // correspondiente a "c1" quedaría inalcanzable para siempre en pulls futuros.
        clientDao.setClients(listOf(
            ClientEntity(
                id = "c1",
                fkTenant = tenantId,
                name = "Edición local sin sincronizar",
                phone = "0000000000",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                syncStatus = "pending",
            )
        ))
        apiService.clientesPullResponse = listOf(remoteClient("c1", name = "Versión del servidor"))

        pullService.pullAll(tenantId)

        assertNull(syncMetadataDao.getLastSyncAt("clientes"))
    }

    @Test
    fun `fallo de red deja last_sync_at sin actualizar y devuelve false`() = runTest {
        apiService.pullFailingEntities = setOf("clientes")

        val result = pullService.pullAll(tenantId)

        assertFalse(result)
        assertNull(syncMetadataDao.getLastSyncAt("clientes"))
    }

    @Test
    fun `una entidad que falla no impide que las demas se intenten`() = runTest {
        // Hallazgo de code review: pullAll() encadenaba con && y cortaba en la primera falla,
        // dejando sin intentar todo lo que viniera después en la cadena fija.
        apiService.pullFailingEntities = setOf("clientes")
        apiService.productosPullResponse = listOf(
            ProductSyncDto(
                id = "p1",
                fkTenant = tenantId,
                name = "Martillo",
                price = "100.00",
                taxRate = "0.16",
                isActive = true,
                createdAt = Instant.ofEpochMilli(1_000_000L).toString(),
                updatedAt = Instant.ofEpochMilli(1_000_000L).toString(),
            )
        )

        val result = pullService.pullAll(tenantId)

        assertFalse(result) // clientes falló, así que el resultado global sigue siendo false
        assertTrue("productos" in apiService.pullCalls) // pero productos SÍ se intentó
        assertTrue(productDao.getAllAsFlow(tenantId).first().any { it.id == "p1" }) // y se aplicó
        assertTrue(syncMetadataDao.getLastSyncAt("productos") != null)
    }

    @Test
    fun `pullAll pide el folio-count y lo guarda en FolioBaselineStore`() = runTest {
        apiService.folioCountResponse = 42

        val result = pullService.pullAll(tenantId)

        assertTrue(result)
        assertEquals(42, folioBaselineStore.folioBaseline.first())
    }

    @Test
    fun `fallo al pedir folio-count no bloquea el resto de entidades`() = runTest {
        apiService.folioCountFails = true
        apiService.clientesPullResponse = listOf(remoteClient("c1"))

        val result = pullService.pullAll(tenantId)

        assertFalse(result) // folio-count falló, resultado global es false
        assertTrue(clientDao.getAllAsFlow().first().any { it.id == "c1" }) // pero clientes sí corrió
        assertNull(folioBaselineStore.folioBaseline.first())
    }
}

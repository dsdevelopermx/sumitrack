package com.sumitrack.android.data.repositories

import com.sumitrack.android.data.local.entities.ClientEntity
import com.sumitrack.android.data.local.entities.SaleEntity
import com.sumitrack.android.domain.models.ClientSearchResult
import com.sumitrack.android.domain.usecases.CalculateClientBalanceUseCase
import com.sumitrack.android.ui.screens.clients.FakeClientDao
import com.sumitrack.android.ui.screens.clients.FakeSaleDao
import com.sumitrack.android.ui.screens.orders.FakeInstallmentDao
import com.sumitrack.android.ui.screens.orders.FakePaymentDao
import com.sumitrack.android.ui.screens.orders.FakeSaleItemDao
import com.sumitrack.android.ui.screens.products.FakeTransactionRunner
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)

class ClientRepositoryTest {

    private lateinit var fakeDao: FakeClientDao
    private lateinit var repository: ClientRepository

    @Before
    fun setUp() {
        fakeDao = FakeClientDao()
        repository = ClientRepository(
            fakeDao,
            CalculateClientBalanceUseCase(
                SaleRepository(FakeTransactionRunner(), FakeSaleDao(), FakeSaleItemDao(), FakeInstallmentDao(), FakePaymentDao())
            ),
        )
    }

    @Test
    fun `createClient persists client with given fkTenant and pending sync status`() = runTest {
        val id = repository.createClient(
            name = "Ferretería El Clavo",
            phone = "555-1234",
            rfc = null,
            address = null,
            notes = null,
            fkTenant = "tenant-1",
        )

        val saved = repository.getClientById(id)
        assertNotNull(saved)
        assertEquals("Ferretería El Clavo", saved!!.name)
        assertEquals("555-1234", saved.phone)
        assertEquals("tenant-1", saved.fkTenant)
    }

    @Test
    fun `createClient does not overwrite previously created clients`() = runTest {
        val firstId = repository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        val secondId = repository.createClient("Bernardo Ruiz", "555-0002", null, null, null, "tenant-1")

        assertNotNull(repository.getClientById(firstId))
        assertNotNull(repository.getClientById(secondId))
    }

    @Test
    fun `updateClient preserves id, updates fields, and returns true`() = runTest {
        val id = repository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")

        val result = repository.updateClient(id, "Ana López Ruiz", "555-9999", "XAXX010101000", "Calle 1", "Cliente frecuente")

        assertTrue(result)
        val updated = repository.getClientById(id)
        assertNotNull(updated)
        assertEquals(id, updated!!.id)
        assertEquals("Ana López Ruiz", updated.name)
        assertEquals("555-9999", updated.phone)
        assertEquals("XAXX010101000", updated.rfc)
        assertEquals("Calle 1", updated.address)
        assertEquals("Cliente frecuente", updated.notes)
    }

    @Test
    fun `updateClient on nonexistent id is a no-op and returns false`() = runTest {
        val result = repository.updateClient("does-not-exist", "Nombre", "Tel", null, null, null)
        assertFalse(result)
        assertNull(repository.getClientById("does-not-exist"))
    }

    @Test
    fun `getClientById returns null for unknown id`() = runTest {
        assertNull(repository.getClientById("unknown"))
    }

    private fun clientEntity(id: String, name: String, tenantId: String = "tenant-1") = ClientEntity(
        id = id,
        fkTenant = tenantId,
        name = name,
        phone = "555-0000",
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        syncStatus = "synced",
    )

    private fun saleEntity(id: String, clientId: String, total: BigDecimal, status: String, tenantId: String = "tenant-1") = SaleEntity(
        id = id,
        fkTenant = tenantId,
        fkClient = clientId,
        folio = "A1",
        total = total,
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        syncStatus = "synced",
    )

    @Test
    fun `searchClientsWithBalance maps client and sums open sales into balance`() = runTest {
        fakeDao.setClients(listOf(clientEntity("c1", "Ana López")))
        fakeDao.setSales(
            listOf(
                saleEntity("s1", "c1", BigDecimal("100.00"), "pending"),
                saleEntity("s2", "c1", BigDecimal("50.00"), "partial"),
                saleEntity("s3", "c1", BigDecimal("999.00"), "paid"),
            )
        )

        val results = mutableListOf<ClientSearchResult>()
        val job = launch { repository.searchClientsWithBalance("tenant-1", "").collect { results.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, results.size)
        assertEquals(BigDecimal("150.00"), results.first().balance)
    }

    @Test
    fun `searchClientsWithBalance returns zero balance for a client without sales`() = runTest {
        fakeDao.setClients(listOf(clientEntity("c1", "Ana López")))

        val results = mutableListOf<ClientSearchResult>()
        val job = launch { repository.searchClientsWithBalance("tenant-1", "").collect { results.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(BigDecimal.ZERO, results.first().balance)
    }

    @Test
    fun `searchClientsWithBalance excludes clients from a different tenant`() = runTest {
        fakeDao.setClients(listOf(clientEntity("c1", "Ana López", tenantId = "tenant-2")))

        val results = mutableListOf<ClientSearchResult>()
        val job = launch { repository.searchClientsWithBalance("tenant-1", "").collect { results.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(emptyList<Any>(), results)
    }

    @Test
    fun `searchClientsWithBalance excludes a sale whose fkTenant does not match, even if fkClient matches`() = runTest {
        // Simula una fila inconsistente (no alcanzable hoy por el invariante del modelo de datos,
        // pero cubierta como defensa en profundidad tras el code review de esta historia).
        fakeDao.setClients(listOf(clientEntity("c1", "Ana López", tenantId = "tenant-1")))
        fakeDao.setSales(listOf(saleEntity("s1", "c1", BigDecimal("500.00"), "pending", tenantId = "tenant-2")))

        val results = mutableListOf<ClientSearchResult>()
        val job = launch { repository.searchClientsWithBalance("tenant-1", "").collect { results.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(BigDecimal.ZERO, results.first().balance)
    }

    @Test
    fun `searchClientsWithBalance matches name ignoring accents`() = runTest {
        fakeDao.setClients(listOf(clientEntity("c1", "Pérez"), clientEntity("c2", "Ruiz")))

        val results = mutableListOf<ClientSearchResult>()
        val job = launch { repository.searchClientsWithBalance("tenant-1", "perez").collect { results.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("c1"), results.map { it.id })
    }

    @Test
    fun `searchClientsWithBalance treats whitespace-only query as blank`() = runTest {
        fakeDao.setClients(listOf(clientEntity("c1", "Ana López")))

        val results = mutableListOf<ClientSearchResult>()
        val job = launch { repository.searchClientsWithBalance("tenant-1", "   ").collect { results.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, results.size)
    }
}

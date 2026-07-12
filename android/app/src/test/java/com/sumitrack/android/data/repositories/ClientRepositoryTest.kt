package com.sumitrack.android.data.repositories

import com.sumitrack.android.domain.usecases.CalculateClientBalanceUseCase
import com.sumitrack.android.ui.screens.clients.FakeClientDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClientRepositoryTest {

    private lateinit var fakeDao: FakeClientDao
    private lateinit var repository: ClientRepository

    @Before
    fun setUp() {
        fakeDao = FakeClientDao()
        repository = ClientRepository(fakeDao, CalculateClientBalanceUseCase())
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
}

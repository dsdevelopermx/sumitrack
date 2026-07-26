package com.sumitrack.android.ui.screens.clients

import com.sumitrack.android.data.local.SearchNormalizer
import com.sumitrack.android.data.local.dao.ClientDao
import com.sumitrack.android.data.local.dao.ClientSearchRow
import com.sumitrack.android.data.local.entities.ClientEntity
import com.sumitrack.android.data.local.entities.SaleEntity
import com.sumitrack.android.data.repositories.ClientRepository
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.domain.models.Client
import com.sumitrack.android.domain.usecases.CalculateClientBalanceUseCase
import com.sumitrack.android.ui.screens.orders.FakeInstallmentDao
import com.sumitrack.android.ui.screens.orders.FakePaymentDao
import com.sumitrack.android.ui.screens.orders.FakeSaleItemDao
import com.sumitrack.android.ui.screens.products.FakeTransactionRunner
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ClientListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeClientDao
    private lateinit var viewModel: ClientListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeClientDao()
        val repo = ClientRepository(
            fakeDao,
            CalculateClientBalanceUseCase(
                SaleRepository(FakeTransactionRunner(), FakeSaleDao(), FakeSaleItemDao(), FakeInstallmentDao(), FakePaymentDao())
            ),
        )
        viewModel = ClientListViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial searchQuery is empty`() {
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun `onSearchQueryChange updates searchQuery`() {
        viewModel.onSearchQueryChange("Ferretería")
        assertEquals("Ferretería", viewModel.searchQuery.value)
    }

    @Test
    fun `onSearchClear resets searchQuery to empty`() {
        viewModel.onSearchQueryChange("algo")
        viewModel.onSearchClear()
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun `clients emits empty list when no clients exist`() = runTest {
        val job = launch { viewModel.clients.collect {} }
        advanceUntilIdle()
        assertEquals(emptyList<Client>(), viewModel.clients.value)
        job.cancel()
    }

    @Test
    fun `clients emits all clients when searchQuery is blank`() = runTest {
        val job = launch { viewModel.clients.collect {} }
        advanceUntilIdle()
        fakeDao.setClients(listOf(makeEntity("1", "Ana López"), makeEntity("2", "Bernardo Ruiz")))
        advanceUntilIdle()
        assertEquals(2, viewModel.clients.value.size)
        job.cancel()
    }

    @Test
    fun `clients filters by name when searchQuery is non-blank`() = runTest {
        val job = launch { viewModel.clients.collect {} }
        fakeDao.setClients(listOf(makeEntity("1", "Ana López"), makeEntity("2", "Bernardo Ruiz")))
        advanceUntilIdle()
        viewModel.onSearchQueryChange("Ana")
        advanceUntilIdle()
        val result = viewModel.clients.value
        assertEquals(1, result.size)
        assertEquals("Ana López", result.first().name)
        job.cancel()
    }

    @Test
    fun `clients returns empty list when no match found`() = runTest {
        val job = launch { viewModel.clients.collect {} }
        fakeDao.setClients(listOf(makeEntity("1", "Ana López")))
        advanceUntilIdle()
        viewModel.onSearchQueryChange("XYZ")
        advanceUntilIdle()
        assertEquals(0, viewModel.clients.value.size)
        job.cancel()
    }

    @Test
    fun `clients matches accented name when search has no accents`() = runTest {
        val job = launch { viewModel.clients.collect {} }
        fakeDao.setClients(listOf(makeEntity("1", "Ana López")))
        advanceUntilIdle()
        viewModel.onSearchQueryChange("lopez")
        advanceUntilIdle()
        val result = viewModel.clients.value
        assertEquals(1, result.size)
        assertEquals("Ana López", result.first().name)
        job.cancel()
    }

    private fun makeEntity(id: String, name: String) = ClientEntity(
        id = id,
        fkTenant = "tenant-1",
        name = name,
        phone = "555-0000",
        createdAt = Instant.ofEpochMilli(1_000_000L),
        updatedAt = Instant.ofEpochMilli(1_000_000L),
        syncStatus = "synced",
    )
}

class FakeClientDao : ClientDao {

    private val allFlow = MutableStateFlow<List<ClientEntity>>(emptyList())
    private val salesFlow = MutableStateFlow<List<SaleEntity>>(emptyList())

    fun setClients(clients: List<ClientEntity>) {
        allFlow.value = clients
    }

    fun setSales(sales: List<SaleEntity>) {
        salesFlow.value = sales
    }

    override fun getAllAsFlow(): Flow<List<ClientEntity>> = allFlow

    override fun searchByNameAsFlow(normalizedQuery: String): Flow<List<ClientEntity>> =
        allFlow.map { list ->
            list.filter { SearchNormalizer.normalize(it.name).contains(normalizedQuery) }
        }

    override fun searchWithBalanceAsFlow(tenantId: String, normalizedQuery: String): Flow<List<ClientSearchRow>> =
        combine(allFlow, salesFlow) { clients, sales -> clients to sales }
            .map { (clients, sales) ->
                clients
                    .filter { it.fkTenant == tenantId }
                    .filter { normalizedQuery.isBlank() || SearchNormalizer.normalize(it.name).contains(normalizedQuery) }
                    .map { client ->
                        val balance = sales
                            .filter { it.fkClient == client.id && it.fkTenant == tenantId && it.status in setOf("pending", "partial") }
                            .fold(BigDecimal.ZERO) { acc, sale -> acc + sale.total }
                        ClientSearchRow(id = client.id, name = client.name, phone = client.phone, balance = balance)
                    }
            }

    override suspend fun upsertAll(clients: List<ClientEntity>) {
        val byId = allFlow.value.associateBy { it.id }.toMutableMap()
        clients.forEach { byId[it.id] = it }
        allFlow.value = byId.values.toList()
    }

    override suspend fun getById(id: String): ClientEntity? =
        allFlow.value.find { it.id == id }
}

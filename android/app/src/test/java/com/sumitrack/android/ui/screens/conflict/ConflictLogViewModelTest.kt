package com.sumitrack.android.ui.screens.conflict

import com.sumitrack.android.data.local.entities.ConflictLogEntity
import com.sumitrack.android.sync.FakeConflictLogDao
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class ConflictLogViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var conflictLogDao: FakeConflictLogDao

    private val tenantId = "tenant-1"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        conflictLogDao = FakeConflictLogDao()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun entry(
        id: String,
        resolvedAt: Instant?,
        detectedAt: Instant = Instant.ofEpochMilli(1_000_000L),
        fkTenant: String = tenantId,
    ) = ConflictLogEntity(
        id = id,
        fkTenant = fkTenant,
        entityType = "clientes",
        recordId = "c1",
        localSnapshotJson = "{}",
        serverSnapshotJson = "{}",
        detectedAt = detectedAt,
        resolvedAt = resolvedAt,
        resolution = if (resolvedAt != null) "local_wins" else null,
    )

    private fun viewModel(tenantId: String? = "tenant-1") =
        ConflictLogViewModel(conflictLogDao, flowOf(tenantId))

    @Test
    fun `resolvedEntries excluye conflictos sin resolver`() = runTest {
        conflictLogDao.insert(entry("unresolved", resolvedAt = null))
        conflictLogDao.insert(entry("resolved", resolvedAt = Instant.ofEpochMilli(2_000_000L)))

        val vm = viewModel()
        val job = launch { vm.resolvedEntries.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("resolved"), vm.resolvedEntries.value.map { it.id })
        job.cancel()
    }

    @Test
    fun `resolvedEntries excluye conflictos de otro tenant`() = runTest {
        conflictLogDao.insert(entry("mismo-tenant", resolvedAt = Instant.ofEpochMilli(2_000_000L)))
        conflictLogDao.insert(entry("otro-tenant", resolvedAt = Instant.ofEpochMilli(2_000_000L), fkTenant = "tenant-2"))

        val vm = viewModel()
        val job = launch { vm.resolvedEntries.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("mismo-tenant"), vm.resolvedEntries.value.map { it.id })
        job.cancel()
    }

    @Test
    fun `resolvedEntries ordena por fecha de resolucion descendente`() = runTest {
        conflictLogDao.insert(entry("viejo", resolvedAt = Instant.ofEpochMilli(1_000_000L)))
        conflictLogDao.insert(entry("nuevo", resolvedAt = Instant.ofEpochMilli(3_000_000L)))

        val vm = viewModel()
        val job = launch { vm.resolvedEntries.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("nuevo", "viejo"), vm.resolvedEntries.value.map { it.id })
        job.cancel()
    }
}

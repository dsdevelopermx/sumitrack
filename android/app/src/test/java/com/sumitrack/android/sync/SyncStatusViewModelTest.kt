package com.sumitrack.android.sync

import androidx.work.WorkInfo
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var syncWorkObserver: FakeSyncWorkObserver
    private lateinit var connectivityObserver: FakeConnectivityObserver
    private lateinit var syncEventBus: SyncEventBus
    private lateinit var triggerCalls: MutableList<Unit>
    private lateinit var viewModel: SyncStatusViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        syncWorkObserver = FakeSyncWorkObserver()
        connectivityObserver = FakeConnectivityObserver(initiallyOnline = true)
        syncEventBus = SyncEventBus()
        triggerCalls = mutableListOf()
        viewModel = viewModel(tenantId = "tenant-1")
    }

    private fun viewModel(tenantId: String?) = SyncStatusViewModel(
        syncWorkObserver,
        connectivityObserver,
        syncEventBus,
        PushSyncTrigger { triggerCalls.add(Unit) },
        flowOf(tenantId),
    )

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `isSyncing es false cuando ninguna cola tiene trabajo en curso`() = runTest {
        val job = launch { viewModel.isSyncing.collect {} }
        advanceUntilIdle()
        assertFalse(viewModel.isSyncing.value)
        job.cancel()
    }

    @Test
    fun `isSyncing es true si push-sync-now esta RUNNING`() = runTest {
        val job = launch { viewModel.isSyncing.collect {} }
        syncWorkObserver.setState("push-sync-now", WorkInfo.State.RUNNING)
        advanceUntilIdle()
        assertTrue(viewModel.isSyncing.value)
        job.cancel()
    }

    @Test
    fun `isSyncing es true si pull-sync esta ENQUEUED`() = runTest {
        val job = launch { viewModel.isSyncing.collect {} }
        syncWorkObserver.setState("pull-sync", WorkInfo.State.ENQUEUED)
        advanceUntilIdle()
        assertTrue(viewModel.isSyncing.value)
        job.cancel()
    }

    @Test
    fun `isSyncing es false si push-sync (periodico) esta ENQUEUED`() = runTest {
        // ENQUEUED es el reposo normal de push-sync entre corridas de 15 min (resetPeriodic()),
        // no una ejecución inminente — a diferencia de pull-sync/push-sync-now (one-time).
        val job = launch { viewModel.isSyncing.collect {} }
        syncWorkObserver.setState("push-sync", WorkInfo.State.ENQUEUED)
        advanceUntilIdle()
        assertFalse(viewModel.isSyncing.value)
        job.cancel()
    }

    @Test
    fun `isSyncing es true si push-sync (periodico) esta RUNNING`() = runTest {
        val job = launch { viewModel.isSyncing.collect {} }
        syncWorkObserver.setState("push-sync", WorkInfo.State.RUNNING)
        advanceUntilIdle()
        assertTrue(viewModel.isSyncing.value)
        job.cancel()
    }

    @Test
    fun `isOffline refleja al ConnectivityObserver`() = runTest {
        val job = launch { viewModel.isOffline.collect {} }
        advanceUntilIdle()
        assertFalse(viewModel.isOffline.value)

        connectivityObserver.setOnline(false)
        advanceUntilIdle()
        assertTrue(viewModel.isOffline.value)
        job.cancel()
    }

    @Test
    fun `syncEvents refleja lo emitido al bus para el tenant activo`() = runTest {
        val received = mutableListOf<SyncEvent>()
        val job = launch { viewModel.syncEvents.collect { received.add(it) } }
        advanceUntilIdle()

        syncEventBus.emit(SyncEvent.Success(tenantId = "tenant-1"))
        advanceUntilIdle()

        assertEquals(listOf(SyncEvent.Success(tenantId = "tenant-1")), received)
        job.cancel()
    }

    @Test
    fun `syncEvents filtra eventos de un tenant distinto al activo`() = runTest {
        val received = mutableListOf<SyncEvent>()
        val job = launch { viewModel.syncEvents.collect { received.add(it) } }
        advanceUntilIdle()

        syncEventBus.emit(SyncEvent.Success(tenantId = "otro-tenant"))
        advanceUntilIdle()

        assertTrue(received.isEmpty())
        job.cancel()
    }

    @Test
    fun `retryPush delega al PushSyncTrigger inyectado`() {
        viewModel.retryPush()
        assertEquals(1, triggerCalls.size)
    }
}

package com.sumitrack.android.sync.workers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushWorkerTest {

    @Test
    fun `corrida periodica no notifica antes del tope de intentos`() {
        assertFalse(PushWorker.shouldNotifyFailure(0, isManualRetry = false))
        assertFalse(PushWorker.shouldNotifyFailure(1, isManualRetry = false))
        assertFalse(PushWorker.shouldNotifyFailure(PushWorker.MAX_ATTEMPTS - 1, isManualRetry = false))
    }

    @Test
    fun `corrida periodica notifica exactamente en el tope de intentos`() {
        assertTrue(PushWorker.shouldNotifyFailure(PushWorker.MAX_ATTEMPTS, isManualRetry = false))
    }

    @Test
    fun `corrida periodica no vuelve a notificar despues del tope de intentos`() {
        assertFalse(PushWorker.shouldNotifyFailure(PushWorker.MAX_ATTEMPTS + 1, isManualRetry = false))
    }

    @Test
    fun `reintento manual notifica en el primer intento fallido`() {
        assertTrue(PushWorker.shouldNotifyFailure(0, isManualRetry = true))
    }

    @Test
    fun `reintento manual no vuelve a notificar en intentos posteriores`() {
        assertFalse(PushWorker.shouldNotifyFailure(1, isManualRetry = true))
        assertFalse(PushWorker.shouldNotifyFailure(PushWorker.MAX_ATTEMPTS, isManualRetry = true))
    }
}

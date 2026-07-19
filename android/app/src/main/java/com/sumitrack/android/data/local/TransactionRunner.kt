package com.sumitrack.android.data.local

import androidx.room.withTransaction
import javax.inject.Inject

// Abstrae `SumitrackDatabase.withTransaction` para que los repositorios que necesitan escrituras
// atómicas multi-DAO (ej. ProductRepository) sigan siendo testeables en JVM puro sin Robolectric —
// mismo criterio que el qualifier TenantId en SessionModule.kt (exponer solo lo mínimo necesario,
// no la clase Room completa).
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactionRunner @Inject constructor(
    private val database: SumitrackDatabase,
) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction(block)
}

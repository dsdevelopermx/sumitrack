package com.sumitrack.android.data.repositories

import com.sumitrack.android.data.local.SearchNormalizer
import com.sumitrack.android.data.local.dao.ClientDao
import com.sumitrack.android.data.local.dao.ClientSearchRow
import com.sumitrack.android.data.local.entities.ClientEntity
import com.sumitrack.android.domain.models.Client
import com.sumitrack.android.domain.models.ClientSearchResult
import com.sumitrack.android.domain.models.SyncStatus
import com.sumitrack.android.domain.usecases.CalculateClientBalanceUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientRepository @Inject constructor(
    private val clientDao: ClientDao,
    private val calculateClientBalance: CalculateClientBalanceUseCase,
) {

    fun getAllClients(): Flow<List<Client>> =
        clientDao.getAllAsFlow().map { entities -> entities.map { it.toDomain() } }

    fun searchClients(query: String): Flow<List<Client>> =
        clientDao.searchByNameAsFlow(SearchNormalizer.toLikePattern(query))
            .map { entities -> entities.map { it.toDomain() } }

    fun searchClientsWithBalance(tenantId: String, query: String): Flow<List<ClientSearchResult>> =
        clientDao.searchWithBalanceAsFlow(tenantId, SearchNormalizer.toLikePattern(query.trim()))
            .map { rows -> rows.map { it.toDomain() } }

    suspend fun upsertAll(clients: List<ClientEntity>) = clientDao.upsertAll(clients)

    suspend fun createClient(
        name: String,
        phone: String,
        rfc: String?,
        address: String?,
        notes: String?,
        fkTenant: String,
    ): String {
        val now = Instant.now()
        val entity = ClientEntity(
            id = UUID.randomUUID().toString(),
            fkTenant = fkTenant,
            name = name,
            phone = phone,
            rfc = rfc,
            address = address,
            notes = notes,
            createdAt = now,
            updatedAt = now,
            syncStatus = "pending",
        )
        clientDao.upsertAll(listOf(entity))
        return entity.id
    }

    suspend fun updateClient(
        id: String,
        name: String,
        phone: String,
        rfc: String?,
        address: String?,
        notes: String?,
    ): Boolean {
        val existing = clientDao.getById(id) ?: return false
        clientDao.upsertAll(
            listOf(
                existing.copy(
                    name = name,
                    phone = phone,
                    rfc = rfc,
                    address = address,
                    notes = notes,
                    updatedAt = Instant.now(),
                    syncStatus = "pending",
                )
            )
        )
        return true
    }

    suspend fun getClientById(id: String): Client? = clientDao.getById(id)?.toDomainWithBalance()

    // Saldo real omitido a propósito: usada por getAllClients()/searchClients(), que se re-emiten
    // en cada cambio de Room. Calcular el saldo aquí dispararía una consulta a `sales` por cliente
    // en cada emisión (N+1). Ver toDomainWithBalance() para el perfil de cliente (S-12), que sí
    // necesita el saldo real y se carga una sola vez por navegación.
    private fun ClientEntity.toDomain() = Client(
        id = id,
        fkTenant = fkTenant,
        name = name,
        phone = phone,
        rfc = rfc,
        address = address,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = SyncStatus.fromString(syncStatus),
    )

    private suspend fun ClientEntity.toDomainWithBalance() =
        toDomain().copy(balance = calculateClientBalance(id, fkTenant))

    private fun ClientSearchRow.toDomain() = ClientSearchResult(id = id, name = name, phone = phone, balance = balance)
}

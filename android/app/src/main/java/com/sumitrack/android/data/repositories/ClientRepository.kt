package com.sumitrack.android.data.repositories

import com.sumitrack.android.data.local.SearchNormalizer
import com.sumitrack.android.data.local.dao.ClientDao
import com.sumitrack.android.data.local.entities.ClientEntity
import com.sumitrack.android.domain.models.Client
import com.sumitrack.android.domain.models.SyncStatus
import com.sumitrack.android.domain.usecases.CalculateClientBalanceUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    suspend fun upsertAll(clients: List<ClientEntity>) = clientDao.upsertAll(clients)

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
        balance = calculateClientBalance(id),
    )
}

package com.sumitrack.android.sync

import com.sumitrack.android.data.local.dao.ConflictLogDao
import com.sumitrack.android.data.local.entities.ConflictLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeConflictLogDao : ConflictLogDao {

    private val rowsFlow = MutableStateFlow<List<ConflictLogEntity>>(emptyList())
    val rows: List<ConflictLogEntity> get() = rowsFlow.value

    override suspend fun insert(entry: ConflictLogEntity) {
        rowsFlow.value = rowsFlow.value + entry
    }

    override suspend fun getById(id: String): ConflictLogEntity? =
        rowsFlow.value.find { it.id == id }

    override suspend fun getLatestUnresolvedFor(entityType: String, recordId: String): ConflictLogEntity? =
        rowsFlow.value
            .filter { it.entityType == entityType && it.recordId == recordId && it.resolvedAt == null }
            .maxByOrNull { it.detectedAt }

    override fun getUnresolvedAsFlow(tenantId: String): Flow<List<ConflictLogEntity>> =
        rowsFlow.map { rows -> rows.filter { it.fkTenant == tenantId && it.resolvedAt == null } }

    override fun getResolvedAsFlow(tenantId: String): Flow<List<ConflictLogEntity>> =
        rowsFlow.map { rows ->
            rows.filter { it.fkTenant == tenantId && it.resolvedAt != null }
                .sortedByDescending { it.resolvedAt }
        }

    override suspend fun update(entry: ConflictLogEntity) {
        rowsFlow.value = rowsFlow.value.map { if (it.id == entry.id) entry else it }
    }
}

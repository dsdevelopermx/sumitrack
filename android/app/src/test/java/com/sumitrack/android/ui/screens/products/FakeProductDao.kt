package com.sumitrack.android.ui.screens.products

import com.sumitrack.android.data.local.dao.ProductDao
import com.sumitrack.android.data.local.entities.ProductEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeProductDao : ProductDao {

    private val allFlow = MutableStateFlow<List<ProductEntity>>(emptyList())

    fun setProducts(products: List<ProductEntity>) {
        allFlow.value = products
    }

    override fun getAllAsFlow(tenantId: String): Flow<List<ProductEntity>> =
        allFlow.map { list -> list.filter { it.fkTenant == tenantId } }

    override fun getActiveAsFlow(tenantId: String): Flow<List<ProductEntity>> =
        allFlow.map { list -> list.filter { it.fkTenant == tenantId && it.isActive } }

    override suspend fun getById(id: String, tenantId: String): ProductEntity? =
        allFlow.value.find { it.id == id && it.fkTenant == tenantId }

    override suspend fun upsertAll(products: List<ProductEntity>) {
        val byId = allFlow.value.associateBy { it.id }.toMutableMap()
        products.forEach { byId[it.id] = it }
        allFlow.value = byId.values.toList()
    }
}

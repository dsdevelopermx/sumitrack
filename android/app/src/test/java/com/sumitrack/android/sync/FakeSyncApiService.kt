package com.sumitrack.android.sync

import com.sumitrack.android.data.remote.api.SyncApiService
import com.sumitrack.android.data.remote.dto.ClientSyncDto
import com.sumitrack.android.data.remote.dto.CreditBalanceSyncDto
import com.sumitrack.android.data.remote.dto.InstallmentSyncDto
import com.sumitrack.android.data.remote.dto.PaymentSyncDto
import com.sumitrack.android.data.remote.dto.ProductSyncDto
import com.sumitrack.android.data.remote.dto.ProductVariantSyncDto
import com.sumitrack.android.data.remote.dto.PushSyncResponseItemDto
import com.sumitrack.android.data.remote.dto.SaleItemSyncDto
import com.sumitrack.android.data.remote.dto.SaleSyncDto
import com.sumitrack.android.data.remote.dto.SettingSyncDto
import com.sumitrack.android.data.remote.dto.SettingSyncResponseItemDto
import java.io.IOException

class FakeSyncApiService : SyncApiService {

    // Nombres de segmento URL ("clientes", "productos", etc.) cuya próxima llamada debe fallar
    // simulando un error de red — usado para probar el corto-circuito de SyncManager.pushPending.
    var failingEntities: Set<String> = emptySet()

    // Ids de registros que el backend "rechaza" con success=false pese a responder 200 — usado
    // para probar que SyncManager solo marca synced los ids confirmados, no el lote completo.
    var rejectedIds: Set<String> = emptySet()

    val calls = mutableListOf<String>()

    private fun recordAndMaybeFail(entity: String) {
        calls += entity
        if (entity in failingEntities) throw IOException("simulated network failure")
    }

    override suspend fun pushClientes(body: List<ClientSyncDto>): List<PushSyncResponseItemDto> {
        recordAndMaybeFail("clientes")
        return body.map { PushSyncResponseItemDto(id = it.id, success = it.id !in rejectedIds) }
    }

    override suspend fun pushProductos(body: List<ProductSyncDto>): List<PushSyncResponseItemDto> {
        recordAndMaybeFail("productos")
        return body.map { PushSyncResponseItemDto(id = it.id, success = it.id !in rejectedIds) }
    }

    override suspend fun pushVariantes(body: List<ProductVariantSyncDto>): List<PushSyncResponseItemDto> {
        recordAndMaybeFail("variantes")
        return body.map { PushSyncResponseItemDto(id = it.id, success = it.id !in rejectedIds) }
    }

    override suspend fun pushVentas(body: List<SaleSyncDto>): List<PushSyncResponseItemDto> {
        recordAndMaybeFail("ventas")
        return body.map { PushSyncResponseItemDto(id = it.id, success = it.id !in rejectedIds) }
    }

    override suspend fun pushItemsVenta(body: List<SaleItemSyncDto>): List<PushSyncResponseItemDto> {
        recordAndMaybeFail("items_venta")
        return body.map { PushSyncResponseItemDto(id = it.id, success = it.id !in rejectedIds) }
    }

    override suspend fun pushParcialidades(body: List<InstallmentSyncDto>): List<PushSyncResponseItemDto> {
        recordAndMaybeFail("parcialidades")
        return body.map { PushSyncResponseItemDto(id = it.id, success = it.id !in rejectedIds) }
    }

    override suspend fun pushCobros(body: List<PaymentSyncDto>): List<PushSyncResponseItemDto> {
        recordAndMaybeFail("cobros")
        return body.map { PushSyncResponseItemDto(id = it.id, success = it.id !in rejectedIds) }
    }

    override suspend fun pushCreditosAFavor(body: List<CreditBalanceSyncDto>): List<PushSyncResponseItemDto> {
        recordAndMaybeFail("creditos_a_favor")
        return body.map { PushSyncResponseItemDto(id = it.id, success = it.id !in rejectedIds) }
    }

    override suspend fun pushSettings(body: List<SettingSyncDto>): List<SettingSyncResponseItemDto> {
        recordAndMaybeFail("settings")
        return body.map { SettingSyncResponseItemDto(key = it.key, success = it.key !in rejectedIds) }
    }
}

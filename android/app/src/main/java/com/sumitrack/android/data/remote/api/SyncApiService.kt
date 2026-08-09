package com.sumitrack.android.data.remote.api

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
import retrofit2.http.Body
import retrofit2.http.POST

interface SyncApiService {

    @POST("api/v1/sync/push/clientes")
    suspend fun pushClientes(@Body body: List<ClientSyncDto>): List<PushSyncResponseItemDto>

    @POST("api/v1/sync/push/productos")
    suspend fun pushProductos(@Body body: List<ProductSyncDto>): List<PushSyncResponseItemDto>

    @POST("api/v1/sync/push/variantes")
    suspend fun pushVariantes(@Body body: List<ProductVariantSyncDto>): List<PushSyncResponseItemDto>

    @POST("api/v1/sync/push/ventas")
    suspend fun pushVentas(@Body body: List<SaleSyncDto>): List<PushSyncResponseItemDto>

    @POST("api/v1/sync/push/items_venta")
    suspend fun pushItemsVenta(@Body body: List<SaleItemSyncDto>): List<PushSyncResponseItemDto>

    @POST("api/v1/sync/push/parcialidades")
    suspend fun pushParcialidades(@Body body: List<InstallmentSyncDto>): List<PushSyncResponseItemDto>

    @POST("api/v1/sync/push/cobros")
    suspend fun pushCobros(@Body body: List<PaymentSyncDto>): List<PushSyncResponseItemDto>

    @POST("api/v1/sync/push/creditos_a_favor")
    suspend fun pushCreditosAFavor(@Body body: List<CreditBalanceSyncDto>): List<PushSyncResponseItemDto>

    @POST("api/v1/sync/push/settings")
    suspend fun pushSettings(@Body body: List<SettingSyncDto>): List<SettingSyncResponseItemDto>
}

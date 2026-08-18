package com.sumitrack.android.data.remote.api

import com.sumitrack.android.data.remote.dto.ClientSyncDto
import com.sumitrack.android.data.remote.dto.CreditBalanceSyncDto
import com.sumitrack.android.data.remote.dto.FolioCountDto
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
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

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

    // Los 9 pull reutilizan los mismos DTOs que push — mismos campos, dirección de datos inversa.
    @GET("api/v1/sync/pull/clientes")
    suspend fun pullClientes(@Query("since") since: String?): List<ClientSyncDto>

    @GET("api/v1/sync/pull/productos")
    suspend fun pullProductos(@Query("since") since: String?): List<ProductSyncDto>

    @GET("api/v1/sync/pull/variantes")
    suspend fun pullVariantes(@Query("since") since: String?): List<ProductVariantSyncDto>

    @GET("api/v1/sync/pull/ventas")
    suspend fun pullVentas(@Query("since") since: String?): List<SaleSyncDto>

    @GET("api/v1/sync/pull/items_venta")
    suspend fun pullItemsVenta(@Query("since") since: String?): List<SaleItemSyncDto>

    @GET("api/v1/sync/pull/parcialidades")
    suspend fun pullParcialidades(@Query("since") since: String?): List<InstallmentSyncDto>

    @GET("api/v1/sync/pull/cobros")
    suspend fun pullCobros(@Query("since") since: String?): List<PaymentSyncDto>

    @GET("api/v1/sync/pull/creditos_a_favor")
    suspend fun pullCreditosAFavor(@Query("since") since: String?): List<CreditBalanceSyncDto>

    @GET("api/v1/sync/pull/settings")
    suspend fun pullSettings(@Query("since") since: String?): List<SettingSyncDto>

    // Piso rápido del contador de folios (AR-10) — independiente del pull completo de `ventas`.
    @GET("api/v1/sync/folio-count")
    suspend fun getFolioCount(): FolioCountDto
}

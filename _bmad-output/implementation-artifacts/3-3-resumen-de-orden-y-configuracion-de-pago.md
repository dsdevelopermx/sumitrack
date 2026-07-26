---
baseline_commit: 09a33cfc0ee6b3d50fde0d4e5b5f6ef19bb55584
---

# Story 3.3: Resumen de Orden y Configuración de Pago

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

Como proveedor,
quiero revisar el resumen de la orden y definir las condiciones de pago acordadas con el cliente,
para que quede registrado exactamente lo que se pactó en el momento.

## Acceptance Criteria

**AC-1 — Resumen de orden (S-06)**

**Dado** que el proveedor toca "Revisar Orden" en S-04
**Cuando** S-06 se muestra
**Entonces** lista todos los ítems con subtotal por línea; sección fija al fondo con subtotal, impuestos y total; nombre del cliente en la cabecera; botón "Editar" regresa a S-04; botón "Ir a Pagar"

**AC-2 — Pago inmediato (S-07)**

**Dado** que el proveedor avanza a S-07
**Cuando** selecciona modo "Pago inmediato"
**Entonces** aparece el constructor de `PaymentMethodRow`: dropdown de tipo (minWidth=120dp), campo de monto (`weight(1f)`), botón × con `contentDescription` "Eliminar método de pago [tipo]"; el método Efectivo no puede repetirse; botón "+ Agregar método" disponible

**AC-3 — Restante por asignar**

**Dado** que el proveedor agrega o modifica métodos de pago
**Cuando** cambia los montos
**Entonces** el contador "Restante por asignar" se actualiza en title-medium/primary con `LiveRegionMode.Polite` para TalkBack; botón "Confirmar Pago" habilitado solo cuando Restante = $0.00

**AC-4 — Parcialidades**

**Dado** que el proveedor selecciona modo "Parcialidades"
**Cuando** ingresa el número de parcialidades (1 ≤ N ≤ max_parcialidades de Settings)
**Entonces** el sistema sugiere fechas según la periodicidad seleccionada (semanal/quincenal/mensual); el proveedor puede ajustar fechas y montos; el sistema valida que la suma de parcialidades iguale exactamente el total antes de habilitar "Confirmar Pago"

**AC-5 — Confirmación y folio**

**Dado** que el proveedor confirma el pago
**Cuando** se ejecuta `ValidateFolioUseCase`
**Entonces** el folio se asigna usando el contador local (`serie_folio` + número auto-incremental); el folio es definitivo y nunca se reutiliza incluso si la venta se cancela después

### Fuera de alcance en esta historia (explícito)

- **S-08 (TicketSheet) y todo lo posterior** — Historia 3.4, no existe todavía. Al confirmar el pago, la venta se persiste y navega de vuelta a S-02 con un Snackbar placeholder ("Ticket — disponible próximamente") en vez de mostrar S-08.
- **Registro real de Cobros sobre parcialidades ya creadas** (FR-19, "marcar una Parcialidad como pagada") — Historia 3.6. Esta historia solo **define** el plan de parcialidades (monto + fecha por parcialidad, todas en estado `pending`); no se registra ningún `Payment` para el modo Parcialidades.
- **Crédito a Favor** (FR-18b) — Historia 3.7, requiere `CreditBalanceEntity` que no existe.
- **Cancelación de venta** (FR-16) — fuera de alcance, historia futura no planeada todavía en el sprint.
- **Pull de folio desde el servidor** (AR-10, "pull inicial descarga último folio confirmado del servidor") — Epic 4 (sincronización) no existe todavía. `ValidateFolioUseCase` genera el folio contando las ventas locales del tenant + 1, sin ningún componente de servidor. Revisar cuando Epic 4 exista para reconciliar con el folio confirmado del servidor tras la primera sincronización.
- **Pantalla de configuración de datos fiscales del tenant / `max_parcialidades` editable** (Historia 5.1) — no existe todavía, pero **no hace falta**: el backend ya siembra `serie_folio` ('A') y `max_parcialidades` ('15') como filas de la tabla `settings` (ver `ApplicationBuilderExtensions.cs`), descargadas y cacheadas localmente desde Historia 1.4 (`SettingsRepository.downloadAndCacheSettings`). Esta historia solo **lee** esas dos claves ya existentes, no construye ninguna UI para editarlas.

## Tasks / Subtasks

### Android — Nuevas entidades: ítems de venta, parcialidades y cobros

- [x] **T1: `SaleItem` (dominio) y `SaleItemEntity`/`SaleItemDao`** (AC-1)
  - [x] **Por qué esta entidad no estaba anticipada en `architecture.md`:** el árbol de dominio original solo lista `Sale`, no un desglose de ítems — un gap real de la planeación original. `SaleEntity` (Historia 2.3) solo guarda `total`, sin forma de persistir qué productos/cantidades componen una venta. Esta historia, al ser la primera que persiste una venta real, debe resolver ese gap: crear `SaleItemEntity` con una fila por producto+variante en el carrito.
  - [x] Crear `domain/models/SaleItem.kt`:
    ```kotlin
    data class SaleItem(
        val id: String,
        val fkTenant: String,
        val fkSale: String,
        val fkProduct: String,
        val fkVariant: String?,
        val productName: String,
        val variantName: String?,
        val quantity: Int,
        val unitPrice: BigDecimal,
        val taxRate: BigDecimal,
        val createdAt: Instant,
        val updatedAt: Instant,
        val syncStatus: SyncStatus,
    ) {
        val subtotal: BigDecimal get() = unitPrice.multiply(BigDecimal(quantity))
        val tax: BigDecimal get() = subtotal.multiply(taxRate).divide(BigDecimal(100))
    }
    ```
    `productName`/`variantName`/`unitPrice`/`taxRate` son **snapshots** al momento de la venta (no referencias vivas a `Product`/`ProductVariant`) — si el proveedor cambia el precio o nombre de un producto después, el detalle histórico de esta venta no debe cambiar. Práctica estándar de facturación; ningún AC lo exige explícitamente pero es la única forma honesta de que Historia 3.4 (ticket) e Historia 3.5 (detalle de orden) muestren datos consistentes con lo que realmente se vendió.
  - [x] Crear `data/local/entities/SaleItemEntity.kt` — mismos campos obligatorios AR-6 (`id`, `fk_tenant`, `created_at`, `updated_at`, `sync_status`) más `fk_sale`, `fk_product`, `fk_variant` (nullable), `product_name`, `variant_name` (nullable), `quantity` (`Int`), `unit_price`/`tax_rate` (`BigDecimal` vía `BigDecimalConverter`, ya registrado).
  - [x] Crear `data/local/dao/SaleItemDao.kt`:
    ```kotlin
    @Dao
    interface SaleItemDao {
        @Query("SELECT * FROM sale_items WHERE fk_sale = :saleId AND fk_tenant = :tenantId")
        suspend fun getForSale(saleId: String, tenantId: String): List<SaleItemEntity>

        @Upsert
        suspend fun upsertAll(items: List<SaleItemEntity>)
    }
    ```
    `getForSale` no se usa en esta historia (S-06/S-07 trabajan sobre el carrito en memoria, no releen de Room) — se agrega ahora por paridad con el resto de DAOs de esta historia y porque Historia 3.5 (Detalle de Orden) lo necesitará; mismo criterio de "agregar por paridad, no usar todavía" que Historia 2.3 aplicó a `SaleDao.upsertAll`.

- [x] **T2: `Installment` (dominio) y `InstallmentEntity`/`InstallmentDao`** (AC-4)
  - [x] Crear `domain/models/InstallmentStatus.kt` — mismo patrón que `SaleStatus.kt`: `enum class InstallmentStatus { PENDING, PAID; companion object { fun fromString(value: String) = ... } }`.
  - [x] Crear `domain/models/Installment.kt`:
    ```kotlin
    data class Installment(
        val id: String,
        val fkTenant: String,
        val fkSale: String,
        val amount: BigDecimal,
        val dueDate: Instant,
        val status: InstallmentStatus,
        val createdAt: Instant,
        val updatedAt: Instant,
        val syncStatus: SyncStatus,
    )
    ```
  - [x] Crear `data/local/entities/InstallmentEntity.kt` — AR-6 + `fk_sale`, `amount` (`BigDecimal`), `due_date` (`Instant`), `status` (`TEXT`, default `'pending'`).
  - [x] Crear `data/local/dao/InstallmentDao.kt`:
    ```kotlin
    @Dao
    interface InstallmentDao {
        @Query("SELECT * FROM installments WHERE fk_sale = :saleId AND fk_tenant = :tenantId ORDER BY due_date ASC")
        suspend fun getForSale(saleId: String, tenantId: String): List<InstallmentEntity>

        @Upsert
        suspend fun upsertAll(installments: List<InstallmentEntity>)
    }
    ```

- [x] **T3: `Payment`/`PaymentMethodType` (dominio) y `PaymentEntity`/`PaymentDao`** (AC-2)
  - [x] Crear `domain/models/PaymentMethodType.kt`:
    ```kotlin
    enum class PaymentMethodType {
        EFECTIVO, TRANSFERENCIA, TARJETA;
        companion object {
            fun fromString(value: String): PaymentMethodType = when (value.lowercase()) {
                "transferencia" -> TRANSFERENCIA
                "tarjeta" -> TARJETA
                else -> EFECTIVO
            }
        }
    }
    ```
    Los 3 valores no están enumerados explícitamente en `epics.md`/`UX-DR12` (solo dicen "dropdown de tipo"); se infieren como los métodos de cobro típicos de un negocio en campo en México. Solo `EFECTIVO` tiene la restricción "no puede repetirse" (AC-2, texto literal) — `TRANSFERENCIA`/`TARJETA` sí pueden repetirse (ej. dos transferencias de bancos distintos).
  - [x] Crear `domain/models/Payment.kt`:
    ```kotlin
    data class Payment(
        val id: String,
        val fkTenant: String,
        val fkSale: String,
        val fkInstallment: String?,
        val method: PaymentMethodType,
        val amount: BigDecimal,
        val paidAt: Instant,
        val createdAt: Instant,
        val updatedAt: Instant,
        val syncStatus: SyncStatus,
    )
    ```
    `fkInstallment` es `null` para los pagos de "Pago inmediato" (no están ligados a ninguna parcialidad) — en esta historia **nunca** se persiste un `Payment` con `fkInstallment` no nulo (eso es Historia 3.6, cuando se registre un cobro sobre una parcialidad existente).
  - [x] Crear `data/local/entities/PaymentEntity.kt` — AR-6 + `fk_sale`, `fk_installment` (nullable), `method` (`TEXT`), `amount` (`BigDecimal`), `paid_at` (`Instant`).
  - [x] Crear `data/local/dao/PaymentDao.kt`:
    ```kotlin
    @Dao
    interface PaymentDao {
        @Query("SELECT * FROM payments WHERE fk_sale = :saleId AND fk_tenant = :tenantId")
        suspend fun getForSale(saleId: String, tenantId: String): List<PaymentEntity>

        @Upsert
        suspend fun upsertAll(payments: List<PaymentEntity>)
    }
    ```

- [x] **T4: `SaleEntity` — agregar `subtotal`/`tax`; migración de base de datos** (AC-1)
  - [x] Actualizar `data/local/entities/SaleEntity.kt` — agregar `subtotal: BigDecimal` y `tax: BigDecimal` (además del `total` ya existente). Historia 2.3 dejó `SaleEntity` deliberadamente mínima ("no debe anticiparse aquí") para que la historia que primero cree ventas reales la extienda — es esta.
  - [x] Actualizar `data/local/SumitrackDatabase.kt` — agregar `SaleItemEntity::class`, `InstallmentEntity::class`, `PaymentEntity::class` a `entities`; subir `version` de `4` a `5`; agregar `abstract fun saleItemDao(): SaleItemDao`, `abstract fun installmentDao(): InstallmentDao`, `abstract fun paymentDao(): PaymentDao`.
  - [x] Actualizar `data/local/Migrations.kt` — agregar `MIGRATION_4_5`: `ALTER TABLE sales ADD COLUMN subtotal TEXT NOT NULL DEFAULT '0'`, `ALTER TABLE sales ADD COLUMN tax TEXT NOT NULL DEFAULT '0'`, más `CREATE TABLE IF NOT EXISTS sale_items (...)`, `CREATE TABLE IF NOT EXISTS installments (...)`, `CREATE TABLE IF NOT EXISTS payments (...)` con las columnas exactas de cada entidad. Agregarla a `ALL`.
  - [x] Actualizar `di/DatabaseModule.kt` — agregar `provideSaleItemDao`, `provideInstallmentDao`, `providePaymentDao`.

### Android — Use Cases nuevos

- [x] **T5: `ValidateFolioUseCase`** (AC-5)
  - [x] Agregar a `data/local/dao/SaleDao.kt`: `@Query("SELECT COUNT(*) FROM sales WHERE fk_tenant = :tenantId") suspend fun countSalesForTenant(tenantId: String): Int`.
  - [x] Agregar a `data/repositories/SettingsRepository.kt`: `suspend fun getValue(key: String): String? = settingsDao.getValue(key)` — passthrough simple, `SettingsDao.getValue` ya existe desde Historia 1.4 pero nunca se expuso desde el repositorio.
  - [x] Crear `domain/usecases/ValidateFolioUseCase.kt`:
    ```kotlin
    class ValidateFolioUseCase @Inject constructor(
        private val saleDao: SaleDao,
        private val settingsRepository: SettingsRepository,
    ) {
        suspend operator fun invoke(tenantId: String): String {
            val serie = settingsRepository.getValue("serie_folio")?.takeIf { it.isNotBlank() } ?: "A"
            val count = saleDao.countSalesForTenant(tenantId)
            return "$serie${count + 1}"
        }
    }
    ```
    **Sin componente de servidor** — ver "Fuera de alcance": AR-10 describe un pull inicial que no existe hasta Epic 4. Contar ventas locales del tenant es correcto mientras esta app sea la única fuente de folios (nadie más escribe en `sales` todavía).

- [x] **T6: `CalculateInstallmentsUseCase`** (AC-4)
  - [x] Crear `domain/models/InstallmentPeriodicity.kt`: `enum class InstallmentPeriodicity { WEEKLY, BIWEEKLY, MONTHLY }`.
  - [x] Crear `domain/usecases/CalculateInstallmentsUseCase.kt`:
    ```kotlin
    data class InstallmentSuggestion(val amount: BigDecimal, val dueDate: Instant)

    class CalculateInstallmentsUseCase @Inject constructor() {
        operator fun invoke(
            total: BigDecimal,
            count: Int,
            periodicity: InstallmentPeriodicity,
            startDate: ZonedDateTime = ZonedDateTime.now(),
        ): List<InstallmentSuggestion> {
            require(count in 1..MAX_INSTALLMENTS_HARD_LIMIT) { "count fuera de rango" }
            val baseAmount = total.divide(BigDecimal(count), 2, RoundingMode.HALF_UP)
            val amounts = MutableList(count) { baseAmount }
            // Ajuste de redondeo: la última parcialidad absorbe la diferencia para que la suma
            // sea EXACTAMENTE el total (AC-4 lo exige antes de habilitar "Confirmar Pago").
            val roundingDiff = total.subtract(baseAmount.multiply(BigDecimal(count)))
            amounts[count - 1] = amounts[count - 1].add(roundingDiff)
            return amounts.mapIndexed { index, amount ->
                val dueDate = when (periodicity) {
                    InstallmentPeriodicity.WEEKLY -> startDate.plusWeeks((index + 1).toLong())
                    InstallmentPeriodicity.BIWEEKLY -> startDate.plusDays((index + 1) * 15L)
                    InstallmentPeriodicity.MONTHLY -> startDate.plusMonths((index + 1).toLong())
                }
                InstallmentSuggestion(amount, dueDate.toInstant())
            }
        }
    }
    ```
    `MAX_INSTALLMENTS_HARD_LIMIT = 15` — límite absoluto (AR/FR-14 dice "máximo 15"); el límite **real** por tenant (`max_parcialidades`, hoy sembrado en `15` para todos) se valida en el ViewModel, no aquí (este use case no conoce Settings). "Quincenal" se interpreta como +15 días exactos (no "dos veces al mes" calendario) — simplificación razonable, ningún AC especifica el algoritmo exacto de "quincenal".

### Android — Persistencia real de la venta

- [x] **T7: `SaleRepository.createSale`** (AC-1 a AC-5)
  - [x] **Cambio de firma del constructor** — `SaleRepository` gana `TransactionRunner`, `SaleItemDao`, `InstallmentDao`, `PaymentDao` como nuevos parámetros de constructor (además del `SaleDao` ya existente). Esto **rompe 8 archivos de test existentes** que construyen `SaleRepository(fakeDao)` directamente — ver Testing § FakeSaleRepository dependencies para la lista completa y cómo actualizarlos.
    ```kotlin
    @Singleton
    class SaleRepository @Inject constructor(
        private val transactionRunner: TransactionRunner,
        private val saleDao: SaleDao,
        private val saleItemDao: SaleItemDao,
        private val installmentDao: InstallmentDao,
        private val paymentDao: PaymentDao,
    ) { /* getOpenSalesForClient, getOrdersForTenant sin cambios */ }
    ```
  - [x] Agregar tipos de entrada:
    ```kotlin
    sealed class PaymentConfig {
        data class Immediate(val payments: List<Pair<PaymentMethodType, BigDecimal>>) : PaymentConfig()
        data class Installments(val installments: List<InstallmentSuggestion>) : PaymentConfig()
    }
    ```
  - [x] Agregar `createSale`:
    ```kotlin
    suspend fun createSale(
        tenantId: String,
        clientId: String,
        folio: String,
        items: List<OrderDraftItem>,
        paymentConfig: PaymentConfig,
    ): String {
        val now = Instant.now()
        val subtotal = items.fold(BigDecimal.ZERO) { acc, item -> acc + item.subtotal }
        val tax = items.fold(BigDecimal.ZERO) { acc, item ->
            acc + item.subtotal.multiply(item.product.taxRate).divide(BigDecimal(100))
        }
        val total = subtotal + tax
        val status = if (paymentConfig is PaymentConfig.Immediate) "paid" else "pending"

        val sale = SaleEntity(
            id = UUID.randomUUID().toString(), fkTenant = tenantId, fkClient = clientId, folio = folio,
            total = total, subtotal = subtotal, tax = tax, status = status,
            createdAt = now, updatedAt = now, syncStatus = "pending",
        )
        val saleItems = items.map { item ->
            SaleItemEntity(
                id = UUID.randomUUID().toString(), fkTenant = tenantId, fkSale = sale.id,
                fkProduct = item.product.id, fkVariant = item.variant?.id,
                productName = item.product.name, variantName = item.variant?.name,
                quantity = item.quantity, unitPrice = item.product.price, taxRate = item.product.taxRate,
                createdAt = now, updatedAt = now, syncStatus = "pending",
            )
        }

        transactionRunner.run {
            saleDao.upsertAll(listOf(sale))
            saleItemDao.upsertAll(saleItems)
            when (paymentConfig) {
                is PaymentConfig.Immediate -> paymentDao.upsertAll(
                    paymentConfig.payments.map { (method, amount) ->
                        PaymentEntity(
                            id = UUID.randomUUID().toString(), fkTenant = tenantId, fkSale = sale.id,
                            fkInstallment = null, method = method.name.lowercase(), amount = amount,
                            paidAt = now, createdAt = now, updatedAt = now, syncStatus = "pending",
                        )
                    }
                )
                is PaymentConfig.Installments -> installmentDao.upsertAll(
                    paymentConfig.installments.map { suggestion ->
                        InstallmentEntity(
                            id = UUID.randomUUID().toString(), fkTenant = tenantId, fkSale = sale.id,
                            amount = suggestion.amount, dueDate = suggestion.dueDate, status = "pending",
                            createdAt = now, updatedAt = now, syncStatus = "pending",
                        )
                    }
                )
            }
        }
        return sale.id
    }
    ```
    **Por qué el estatus es `"paid"` siempre para "Pago inmediato":** la UI (T11) exige `Restante = $0.00` antes de habilitar "Confirmar Pago" (AC-3) — por construcción, si se llega a llamar `createSale` con `PaymentConfig.Immediate`, ya se cubrió el 100% del total. **Por qué `"pending"` para "Parcialidades":** ningún `Payment` se crea en esta historia para ese modo (ver "Fuera de alcance"), así que la venta no tiene cobros todavía.
    **Saldo del cliente:** no requiere ninguna actualización explícita — `CalculateClientBalanceUseCase` (Historia 2.3) ya suma dinámicamente las ventas `pending`/`partial` de `sales` en cada consulta; insertar la nueva fila es suficiente (FR-12 "el saldo se actualiza de forma inmediata" ya se cumple por diseño existente).

### Android — Componentes UI nuevos

- [x] **T8: `PaymentMethodRow.kt`** (AC-2, AC-3) — ya anticipado en `architecture.md`, primera vez que se construye
  - [x] Crear `ui/components/PaymentMethodRow.kt` según `UX-DR12`: `Row` horizontal con `ExposedDropdownMenuBox` (tipo, `minWidth = 120.dp`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`), `OutlinedTextField` de monto (`weight(1f)`, `keyboardType = KeyboardType.Decimal`), `IconButton` `×` (`Icons.Filled.Close`, `contentDescription = "Eliminar método de pago ${type label}"`, `wrapContentWidth`).
    ```kotlin
    @Composable
    fun PaymentMethodRow(
        type: PaymentMethodType,
        amountText: String,
        onTypeChange: (PaymentMethodType) -> Unit,
        onAmountChange: (String) -> Unit,
        onRemove: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        var dropdownExpanded by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                modifier = Modifier.widthIn(min = 120.dp),
            ) {
                OutlinedTextField(
                    value = paymentMethodLabel(type), onValueChange = {}, readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier = Modifier.menuAnchor(),
                )
                ExposedDropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                    PaymentMethodType.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(paymentMethodLabel(option)) }, onClick = { onTypeChange(option); dropdownExpanded = false })
                    }
                }
            }
            OutlinedTextField(
                value = amountText, onValueChange = onAmountChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            IconButton(onClick = onRemove, modifier = Modifier.wrapContentWidth()) {
                Icon(Icons.Filled.Close, contentDescription = "Eliminar método de pago ${paymentMethodLabel(type)}")
            }
        }
    }

    private fun paymentMethodLabel(type: PaymentMethodType): String = when (type) {
        PaymentMethodType.EFECTIVO -> "Efectivo"
        PaymentMethodType.TRANSFERENCIA -> "Transferencia"
        PaymentMethodType.TARJETA -> "Tarjeta"
    }
    ```

- [x] **T9: `CartRouteCodec`** (AC-1) — utilidad de codificación del carrito para pasarlo entre pantallas por argumentos de navegación
  - [x] Crear `ui/screens/orders/CartRouteCodec.kt`:
    ```kotlin
    object CartRouteCodec {
        fun encode(cart: List<OrderDraftItem>): String =
            cart.joinToString("|") { "${it.product.id},${it.variant?.id.orEmpty()},${it.quantity}" }

        fun decode(encoded: String): List<Triple<String, String?, Int>> =
            if (encoded.isBlank()) emptyList()
            else encoded.split("|").map { part ->
                val fields = part.split(",")
                Triple(fields[0], fields[1].ifBlank { null }, fields[2].toInt())
            }
    }
    ```
    **Por qué no un ViewModel compartido ni un grafo de navegación anidado:** Historia 3.2 ya decidió no introducir ViewModels compartidos entre pantallas (ver su Dev Notes de arquitectura). S-06 y S-07 SÍ son pantallas de navegación separadas (a diferencia de S-05, que fue un bottom sheet embebido) — "Editar" en S-06 regresa de verdad a S-04, y S-07 es su propia entrada de back stack. En vez de introducir un grafo anidado con ViewModel compartido (un patrón nuevo y más complejo que ningún AC exige), el carrito se codifica como string y se reenvía por argumentos de ruta de S-04→S-06→S-07 — mismo patrón de "reenviar IDs por argumentos de navegación" ya usado en Historias 2.3/3.2, solo que aquí el "id" es una lista compacta de `(productId, variantId, cantidad)`. Cada pantalla resuelve los `Product`/`ProductVariant` completos vía `ProductRepository`, igual que `ItemListViewModel` ya hace.

### Android — S-06 Resumen de Orden

- [x] **T10: `OrderSummaryViewModel` + `OrderSummaryScreen`** (AC-1)
  - [x] Crear `ui/screens/orders/OrderSummaryViewModel.kt`:
    ```kotlin
    data class OrderSummaryUiState(
        val isLoading: Boolean = true,
        val clientName: String = "",
        val items: List<OrderDraftItem> = emptyList(),
        val errorMessage: String? = null,
    ) {
        val subtotal: BigDecimal get() = items.fold(BigDecimal.ZERO) { acc, item -> acc + item.subtotal }
        val tax: BigDecimal get() = items.fold(BigDecimal.ZERO) { acc, item -> acc + item.subtotal.multiply(item.product.taxRate).divide(BigDecimal(100)) }
        val total: BigDecimal get() = subtotal + tax
    }

    @HiltViewModel
    class OrderSummaryViewModel @Inject constructor(
        savedStateHandle: SavedStateHandle,
        private val clientRepository: ClientRepository,
        private val productRepository: ProductRepository,
        @TenantId private val tenantId: Flow<String?>,
    ) : ViewModel() {
        val clientId: String = checkNotNull(savedStateHandle["clientId"])
        private val cartEncoded: String = checkNotNull(savedStateHandle["cart"])

        private val _uiState = MutableStateFlow(OrderSummaryUiState())
        val uiState: StateFlow<OrderSummaryUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val tenant = tenantId.first()
                if (tenant.isNullOrBlank()) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "No se pudo determinar tu negocio. Vuelve a iniciar sesión.") }
                    return@launch
                }
                val client = runCatching { clientRepository.getClientById(clientId) }.getOrNull()
                val items = CartRouteCodec.decode(cartEncoded).mapNotNull { (productId, variantId, quantity) ->
                    val product = runCatching { productRepository.getProductById(productId, tenant) }.getOrNull() ?: return@mapNotNull null
                    val variant = variantId?.let { vid ->
                        runCatching { productRepository.getVariantsForProduct(productId, tenant) }.getOrDefault(emptyList()).find { it.id == vid }
                    }
                    OrderDraftItem(product, variant, quantity)
                }
                _uiState.update { it.copy(isLoading = false, clientName = client?.name.orEmpty(), items = items) }
            }
        }
    }
    ```
  - [x] Crear `ui/screens/orders/OrderSummaryScreen.kt` — `Scaffold` con `TopAppBar` (título = nombre del cliente, back = "Editar", `onClick = onEditClick` → `popBackStack()` a S-04), `LazyColumn` de filas ítem (nombre + variante si aplica + cantidad × precio = subtotal de línea), sección fija al fondo (`subtotal`/`impuestos`/`total` formateados) + botón "Ir a Pagar" (`onClick = onGoToPaymentClick`, pasa `clientId`+`cart` intactos a S-07).

### Android — S-07 Configuración de Pago

- [x] **T11: `PaymentViewModel`** (AC-2 a AC-5) — el ViewModel más complejo del proyecto hasta ahora
  - [x] Crear `ui/screens/orders/PaymentViewModel.kt`. Estado:
    ```kotlin
    enum class PaymentMode { IMMEDIATE, INSTALLMENTS }

    data class PaymentMethodDraft(
        val localId: String = UUID.randomUUID().toString(),
        val type: PaymentMethodType = PaymentMethodType.EFECTIVO,
        val amountText: String = "",
    )

    data class InstallmentDraftUi(val amountText: String, val dueDate: Instant)

    data class PaymentUiState(
        val isLoading: Boolean = true,
        val items: List<OrderDraftItem> = emptyList(),
        val mode: PaymentMode = PaymentMode.IMMEDIATE,
        val paymentMethods: List<PaymentMethodDraft> = listOf(PaymentMethodDraft()),
        val installmentCountText: String = "",
        val periodicity: InstallmentPeriodicity = InstallmentPeriodicity.MONTHLY,
        val installments: List<InstallmentDraftUi> = emptyList(),
        val maxParcialidades: Int = 15,
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
    ) {
        val total: BigDecimal get() = items.fold(BigDecimal.ZERO) { acc, item ->
            acc + item.subtotal + item.subtotal.multiply(item.product.taxRate).divide(BigDecimal(100))
        }
        private fun BigDecimal?.orZero() = this ?: BigDecimal.ZERO
        val assignedAmount: BigDecimal get() = paymentMethods.fold(BigDecimal.ZERO) { acc, m -> acc + m.amountText.toBigDecimalOrNull().orZero() }
        val remaining: BigDecimal get() = total.subtract(assignedAmount)
        val isImmediateConfirmEnabled: Boolean get() = !isSaving && remaining.compareTo(BigDecimal.ZERO) == 0
        val installmentsSum: BigDecimal get() = installments.fold(BigDecimal.ZERO) { acc, i -> acc + i.amountText.toBigDecimalOrNull().orZero() }
        val isInstallmentsConfirmEnabled: Boolean get() = !isSaving && installments.isNotEmpty() && installmentsSum.compareTo(total) == 0
    }
    ```
  - [x] `init`: resuelve `tenantId`/`clientId`/`cart` igual que T10 (mismo `CartRouteCodec.decode` + resolución vía `ProductRepository`); además lee `settingsRepository.getValue("max_parcialidades")?.toIntOrNull() ?: 15` para `maxParcialidades`.
  - [x] `onModeChange(mode: PaymentMode)`.
  - [x] `onAddPaymentMethod()` — agrega un `PaymentMethodDraft()` nuevo (default `EFECTIVO`; si ya hay un `EFECTIVO` sin usar, el nuevo nace en `TRANSFERENCIA` para no violar la regla de inmediato, aunque el proveedor puede cambiarlo).
  - [x] `onRemovePaymentMethod(localId: String)`.
  - [x] `onPaymentMethodTypeChange(localId: String, type: PaymentMethodType)` — si `type == EFECTIVO` y ya existe otro método con `EFECTIVO` (distinto `localId`), rechaza el cambio y setea `errorMessage = "Ya agregaste un método de pago en efectivo"` (AC-2, "el método Efectivo no puede repetirse"); si no hay conflicto, aplica el cambio y limpia `errorMessage`.
  - [x] `onPaymentMethodAmountChange(localId: String, text: String)`.
  - [x] `onInstallmentCountChange(text: String)` — parsea a `Int`; si está fuera de `1..maxParcialidades`, setea `errorMessage` y no regenera; si es válido, llama `CalculateInstallmentsUseCase(total, count, periodicity)` y reemplaza `installments` por las sugerencias (**regenera desde cero, no preserva ediciones manuales previas de fecha/monto** — simplificación documentada: cambiar el número o la periodicidad reinicia el plan sugerido).
  - [x] `onPeriodicityChange(periodicity: InstallmentPeriodicity)` — igual que arriba, regenera `installments` si ya hay un `installmentCountText` válido.
  - [x] `onInstallmentAmountChange(index: Int, text: String)` — edita el monto de una parcialidad puntual sin regenerar las demás.
  - [x] `onInstallmentDateChange(index: Int, newDate: Instant)` — edita la fecha de una parcialidad puntual.
  - [x] `onConfirmClick()`:
    ```kotlin
    fun onConfirmClick() {
        val state = _uiState.value
        if (state.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val tenant = runCatching { tenantId.first() }.getOrNull()
            if (tenant.isNullOrBlank()) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "No se pudo determinar tu negocio. Vuelve a iniciar sesión.") }
                return@launch
            }
            val paymentConfig = when (state.mode) {
                PaymentMode.IMMEDIATE -> PaymentConfig.Immediate(
                    state.paymentMethods.mapNotNull { m -> m.amountText.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }?.let { m.type to it } }
                )
                PaymentMode.INSTALLMENTS -> PaymentConfig.Installments(
                    state.installments.map { InstallmentSuggestion(it.amountText.toBigDecimalOrNull() ?: BigDecimal.ZERO, it.dueDate) }
                )
            }
            val result = runCatching {
                val folio = validateFolioUseCase(tenant)
                saleRepository.createSale(tenant, clientId, folio, state.items, paymentConfig)
            }
            result.onSuccess {
                _uiState.update { it.copy(isSaving = false) }
                _navEvent.send(Unit)
            }.onFailure {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Algo salió mal. Inténtalo de nuevo.") }
            }
        }
    }
    ```
    Canal `_navEvent`/`navEvent` (`Channel<Unit>`, mismo patrón `LoginViewModel`/`ClientListScreen` de historias previas) — al recibirlo, `PaymentScreen` muestra el Snackbar placeholder de "Fuera de alcance" y navega de vuelta a S-02 limpiando todo el back stack de la orden.

- [x] **T12: `PaymentScreen`** (AC-2 a AC-5)
  - [x] Crear `ui/screens/orders/PaymentScreen.kt`:
    - `Scaffold` con `TopAppBar` (título "Configurar pago", back estándar).
    - Selector de modo: `SingleChoiceSegmentedButtonRow` con dos `SegmentedButton` ("Pago inmediato" / "Parcialidades") — **no reutilizar `FilterChipRow`**: esa API permite deseleccionar el chip activo (`onChipSelected(null)`), semánticamente incorrecta para un selector de modo obligatorio de 2 opciones (siempre debe haber exactamente una seleccionada, como un radio group). Primera vez que se usa `SegmentedButton` en el proyecto.
    - **Modo "Pago inmediato":** `Column` de `PaymentMethodRow` (uno por `uiState.paymentMethods`), botón "+ Agregar método", `Text` "Restante por asignar: {monto}" con `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` (primera vez que se usa `LiveRegionMode` en el proyecto — AC-3 lo exige explícitamente), botón "Confirmar Pago" (`enabled = uiState.isImmediateConfirmEnabled`).
    - **Modo "Parcialidades":** `OutlinedTextField` numérico para el número de parcialidades, selector de periodicidad (`SingleChoiceSegmentedButtonRow` con 3 opciones: Semanal/Quincenal/Mensual — mismo componente que el selector de modo, reutilizado), lista de filas editables (monto + botón que abre `DatePickerDialog` con la fecha sugerida, confirmar la actualiza vía `onInstallmentDateChange`), suma visible, botón "Confirmar Pago" (`enabled = uiState.isInstallmentsConfirmEnabled`). Primera vez que se usa `DatePickerDialog`/`rememberDatePickerState()` en el proyecto.
    - `LaunchedEffect(Unit) { viewModel.navEvent.collect { onConfirmed() } }` — `onConfirmed` (provisto por `NavGraph`) muestra el Snackbar placeholder y navega.

### Android — Wiring: S-04 → S-06 → S-07, navegación final

- [x] **T13: `ItemListScreen` — conectar "Revisar Orden" a navegación real** (AC-1)
  - [x] Actualizar `ui/screens/orders/ItemListScreen.kt` — agregar parámetro `onReviewOrderClick: (clientId: String, cartEncoded: String) -> Unit = { _, _ -> }`; el botón "Revisar Orden" (actualmente Snackbar placeholder "Resumen de orden — disponible próximamente", de Historia 3.2) llama a `onReviewOrderClick(viewModel.clientId, CartRouteCodec.encode(uiState.cart))`. Requiere exponer `clientId` públicamente desde `ItemListViewModel` (hoy `private val clientId`, cambiar a solo quitar el `private` o agregar un getter).

- [x] **T14: Rutas y wiring completo** (AC-1 a AC-5)
  - [x] Actualizar `ui/navigation/Routes.kt`:
    ```kotlin
    object OrderSummary : Routes("order_summary/{clientId}/{cart}") {
        fun createRoute(clientId: String, cart: String): String = "order_summary/$clientId/$cart"
    }
    object Payment : Routes("payment/{clientId}/{cart}") {
        fun createRoute(clientId: String, cart: String): String = "payment/$clientId/$cart"
    }
    ```
  - [x] Actualizar `ui/navigation/NavGraph.kt`:
    - Composable de `Routes.NewOrderItems` (S-04): pasar `onReviewOrderClick = { clientId, cart -> navController.navigate(Routes.OrderSummary.createRoute(clientId, cart)) { launchSingleTop = true } }`.
    - Agregar composable de `Routes.OrderSummary` (S-06): argumentos `clientId`/`cart` (`NavType.StringType`); `onEditClick = { navController.popBackStack() }`; `onGoToPaymentClick = { clientId, cart -> navController.navigate(Routes.Payment.createRoute(clientId, cart)) { launchSingleTop = true } }`.
    - Agregar composable de `Routes.Payment` (S-07): mismos argumentos; `onConfirmed` muestra el Snackbar placeholder (ver T12) y llama `navController.popBackStack(Routes.Orders.route, inclusive = false)` para limpiar todo el back stack de la orden (S-03→S-04→S-06→S-07) y regresar directo a S-02.

### Review Findings

- [x] [Review][Patch] Doble-submit en `PaymentViewModel.onConfirmClick` — `isSaving` se lee sincrónicamente antes de lanzar la corrutina y se resetea a `false` antes de que `_navEvent.send(Unit)` complete, dejando una ventana donde dos taps rápidos (o el botón reactivándose antes de navegar) pueden crear dos ventas duplicadas [`ui/screens/orders/PaymentViewModel.kt:211-241`]
- [x] [Review][Patch] Sin guardia contra carrito vacío al navegar a S-06/S-07 — "Revisar Orden" e "Ir a Pagar" están habilitados incluso con `cart`/`items` vacío, permitiendo crear una venta de $0 que consume un folio, además de un segmento de ruta vacío en `CartRouteCodec.encode(emptyList())` [`ui/screens/orders/ItemListScreen.kt:144-145`, `ui/screens/orders/OrderSummaryScreen.kt:90-91`]
- [x] [Review][Patch] `maxParcialidades` leído de Settings puede exceder el límite duro de 15 de `CalculateInstallmentsUseCase`, provocando un `IllegalArgumentException` no capturado (crash) si el número de parcialidades ingresado está entre 16 y `maxParcialidades` [`ui/screens/orders/PaymentViewModel.kt:113-117,158-165`]
- [x] [Review][Patch] Monto negativo en un método de pago (modo Pago inmediato) puede satisfacer `remaining == 0` en la UI pero se descarta silenciosamente al persistir (`takeIf { it > BigDecimal.ZERO }`), dejando el total realmente cobrado divergente del total de la venta [`ui/screens/orders/PaymentViewModel.kt:60-63,225-227`]
- [x] [Review][Patch] Monto negativo en una parcialidad individual no se rechaza en ningún punto — se persiste tal cual aunque la suma total siga coincidiendo con el total de la venta [`ui/screens/orders/PaymentViewModel.kt:67-71,228-230`]
- [x] [Review][Patch] `onInstallmentCountChange` con un valor fuera de rango deja el plan de parcialidades anterior (válido) intacto en el estado — `isInstallmentsConfirmEnabled` no depende de `installmentCountText`, así que "Confirmar Pago" sigue habilitado con un plan que ya no corresponde al número mostrado (con error visible) [`ui/screens/orders/PaymentViewModel.kt:158-165`]
- [x] [Review][Patch] `total`/`subtotal`/`tax` se calculan con aritmética `BigDecimal` sin `setScale(2, RoundingMode.HALF_UP)` consistente en los 3 lugares donde se duplica la fórmula (`SaleRepository.createSale`, `OrderSummaryUiState`, `PaymentUiState`) — si una tasa de impuesto produce más de 2 decimales, la comparación exacta `remaining.compareTo(BigDecimal.ZERO) == 0` (que habilita "Confirmar Pago") puede volverse imposible de satisfacer pese a que la UI muestre "$0.00"; extraer un helper compartido con redondeo consistente resuelve ambos problemas a la vez [`data/repositories/SaleRepository.kt:67-70`, `ui/screens/orders/OrderSummaryViewModel.kt:31`, `ui/screens/orders/PaymentViewModel.kt:54-63`]
- [x] [Review][Patch] `MIGRATION_4_5` no hace backfill de `subtotal`/`tax` para filas de `sales` preexistentes (quedan en `'0'` por default), dejando `subtotal + tax != total` de forma permanente para ventas creadas antes de esta migración [`data/local/Migrations.kt:82-85`]
- [x] [Review][Patch] `PaymentScreen` no tiene contenedor con scroll — con hasta 15 parcialidades o métodos de pago agregados sin límite, "Confirmar Pago" puede quedar fuera de la pantalla y sin forma de alcanzarlo [`ui/screens/orders/PaymentScreen.kt:104,175,221,243`]
- [x] [Review][Patch] El Change Log de esta historia dice "+30 sobre Historia 3.2" pero Completion Notes y el conteo real de tests nuevos en el diff dan "+28" — inconsistencia menor de documentación entre dos secciones del mismo archivo
- [x] [Review][Defer] Pérdida silenciosa de un ítem del carrito si el producto fue eliminado/desactivado entre S-04 y S-07 (`mapNotNull` + `getOrNull()` sin aviso al usuario) — deferred, misma familia que el patrón ya diferido de manejo de errores silencioso (Historias 2.3/3.2), pero de mayor prioridad que los demás ítems de ese bucket porque puede subcobrar al cliente sin que se note [`ui/screens/orders/OrderSummaryViewModel.kt`, `ui/screens/orders/PaymentViewModel.kt`]
- [x] [Review][Defer] Ningún test ejercita el rollback transaccional de `SaleRepository.createSale` — `FakeTransactionRunner` solo ejecuta el bloque directamente, sin simular un fallo a mitad de transacción — deferred, no bloquea ningún AC, es una mejora de cobertura de test [`data/repositories/SaleRepository.kt`]

## Dev Notes

### Por qué `SaleItemEntity` no estaba en `architecture.md` y se crea aquí

Ver T1. El árbol de dominio original (`architecture.md`, líneas 392-400) lista `Client`, `Product`, `Sale`, `Installment`, `Payment`, `SyncStatus`, `Conflict` — sin ningún modelo de línea de venta. Esto es un gap real de la planeación (no una decisión deliberada documentada en ningún ADR), descubierto al llegar a la primera historia que persiste ventas reales. Se resuelve con `SaleItemEntity`, con snapshots de nombre/precio/impuesto para proteger la integridad histórica.

### Por qué el carrito se reenvía como string codificado en vez de un ViewModel compartido

Ver T9. Mismo criterio arquitectónico que Historia 3.2 estableció para S-05 (evitar el primer ViewModel compartido entre pantallas del proyecto), extendido aquí: S-06/S-07 sí son pantallas de navegación reales (a diferencia del bottom sheet de S-05), pero reenviar `(productId, variantId, cantidad)` por argumentos de ruta —igual que ya se reenvía `clientId` desde Historia 2.3— evita introducir grafos de navegación anidados con ViewModels compartidos, un patrón nuevo que ningún AC de esta historia exige.

### Por qué `ValidateFolioUseCase` no tiene componente de servidor todavía

Ver T5 y "Fuera de alcance". AR-10 describe un pull inicial de folio desde el servidor que depende de Epic 4 (sincronización), inexistente. Contar las ventas locales del tenant es la única fuente de verdad disponible hoy — es correcto mientras ningún otro dispositivo/proceso escriba en `sales` sin pasar por esta app (cierto hasta que exista sync).

### Ruptura de compatibilidad: `SaleRepository` gana 4 parámetros de constructor

Ver T7. Esto **rompe la compilación** de 8 archivos de test existentes que construyen `SaleRepository(fakeDao)` directamente. Todos deben actualizarse a pasar los 4 nuevos parámetros (usando fakes triviales cuando el test no ejercita `createSale`):

| Archivo | Uso de `SaleRepository` | Fakes necesarios |
|---------|------------------------|-------------------|
| `SaleRepositoryTest.kt` | Sujeto de prueba directo — necesita fakes reales para probar `createSale` | `FakeTransactionRunner`, `FakeSaleItemDao`, `FakeInstallmentDao`, `FakePaymentDao` (nuevos) |
| `ClientRepositoryTest.kt`, `ClientFormViewModelTest.kt`, `ClientListViewModelTest.kt`, `ClientProfileViewModelTest.kt`, `CalculateClientBalanceUseCaseTest.kt` | Indirecto, vía `CalculateClientBalanceUseCase(SaleRepository(FakeSaleDao()))` — nunca llaman `createSale` | Fakes triviales (no-op) alcanzan |
| `OrderListViewModelTest.kt`, `ClientSelectViewModelTest.kt` | Indirecto, mismo patrón | Fakes triviales |

`FakeTransactionRunner` ya existe (`ui/screens/products/FakeTransactionRunner.kt`, Historia 2.4) — reutilizar tal cual, importándolo cross-package (mismo patrón que `FakeSaleDao` ya se importa desde `ui/screens/clients/` en archivos de otros paquetes).

### Archivos existentes relevantes — estado actual y cambio en esta historia

| Archivo | Estado actual | Cambio en esta historia |
|---------|---------------|--------------------------|
| `SaleEntity.kt` | `id`/`fk_tenant`/`fk_client`/`folio`/`total`/`status`/AR-6 (Historia 2.3) | + `subtotal`, + `tax` |
| `SaleRepository.kt` | `getOpenSalesForClient`, `getOrdersForTenant` (Historias 2.3/3.1) | + `createSale`; constructor gana 4 parámetros nuevos |
| `SettingsRepository.kt` | `downloadAndCacheSettings`, `clearLocalSettings` (Historia 1.4) | + `getValue(key)` passthrough |
| `SumitrackDatabase.kt` | `version = 4`, 5 entidades (Historia 2.4) | `version = 5`, + 3 entidades, + 3 DAOs |
| `ItemListScreen.kt`/`ItemListViewModel.kt` | "Revisar Orden" muestra Snackbar placeholder (Historia 3.2) | Navega de verdad a S-06; `clientId` expuesto públicamente |
| `Routes.kt`/`NavGraph.kt` | 10 rutas (Historia 3.2) | + `OrderSummary`, `Payment` |

**NO tocar:**
- `ClientDao.kt`, `ProductDao.kt`, `ProductVariantDao.kt` — sin relación con esta historia.
- `ClientSelectScreen.kt`, `ClientSelectViewModel.kt`, `VariantSelectorSheet.kt`, `QuantityStepper.kt` — sin cambios, ya completos desde Historia 3.2.
- `CalculateClientBalanceUseCase.kt` — sigue funcionando tal cual, ahora con datos reales en `sales` por primera vez.
- No crear `CreditBalanceEntity`/`ConflictLogEntity` — Historias 3.7/Epic 4.
- No implementar cancelación de venta (FR-16) — fuera de alcance.

### Testing

Mismo patrón establecido en Historias 2.1-3.2: **sin Robolectric**, tests JVM puros con Fake DAOs, correr con el JDK de Android Studio (`./gradlew :app:testDebugUnitTest`).

- **`FakeSaleItemDao`, `FakeInstallmentDao`, `FakePaymentDao`** (nuevos, `test/java/.../ui/screens/orders/`) — mismo patrón de almacenamiento en memoria que `FakeProductVariantDao`; `upsertAll` + `getForSale` filtrando por `fkSale`/`fkTenant`.
- **`SaleRepositoryTest`** (existente, actualizar constructor) — agregar casos para `createSale`: modo `Immediate` persiste `Sale` con `status = "paid"`, `SaleItem` por cada ítem del carrito (con snapshot correcto de nombre/precio/impuesto), y un `Payment` por cada método con monto > 0; modo `Installments` persiste `Sale` con `status = "pending"` y un `Installment` por cada sugerencia, sin ningún `Payment`; `subtotal`/`tax`/`total` calculados correctamente sobre múltiples ítems con `taxRate` distintos; aislamiento por tenant en la creación.
- **`ValidateFolioUseCaseTest`** (nuevo) — folio usa la serie de Settings; cuenta ventas existentes del tenant correctamente; tenant sin ventas previas → primer folio (`{serie}1`); excluye ventas de otro tenant del conteo.
- **`CalculateInstallmentsUseCaseTest`** (nuevo) — la suma de montos sugeridos iguala exactamente el total (incluyendo el ajuste de redondeo en la última parcialidad); fechas correctas para cada periodicidad; `count` fuera de rango lanza excepción.
- **`OrderSummaryViewModelTest`** (nuevo) — resuelve `clientName`+`items` desde el carrito codificado; `subtotal`/`tax`/`total` correctos.
- **`PaymentViewModelTest`** (nuevo) — el más grande: `onPaymentMethodTypeChange` rechaza un segundo `EFECTIVO`; `remaining`/`isImmediateConfirmEnabled` reaccionan a cambios de monto; `onInstallmentCountChange` fuera de `1..maxParcialidades` no regenera y setea error; `installmentsSum`/`isInstallmentsConfirmEnabled` correctos; `onConfirmClick` en modo `Immediate` llama `createSale` con `PaymentConfig.Immediate` y emite `navEvent`; en modo `Installments` llama con `PaymentConfig.Installments`; `tenantId` nulo muestra error y no guarda.
- Sin test de Composable/UI (`PaymentMethodRow`/`OrderSummaryScreen`/`PaymentScreen`) — mismo criterio que historias previas. `DatePickerDialog` y `LiveRegionMode` no pueden verificarse sin dispositivo — documentar como pendiente de verificación manual.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Historia 3.3: Resumen de Orden y Configuración de Pago] (líneas 542-568) — AC originales
- [Source: _bmad-output/planning-artifacts/epics.md#Requirements Inventory] FR-12 (creación de venta, saldo inmediato), FR-13/14 (pago único/parcialidades), FR-15 (estatus automático), FR-24 (folio), AR-6, AR-10 (folio, pull de servidor fuera de alcance), AR-17 (precisión monetaria)
- [Source: _bmad-output/planning-artifacts/architecture/architecture.md] árbol Android (líneas 392-408): `OrderSummaryScreen.kt`/`OrderSummaryViewModel.kt`/`PaymentScreen.kt`/`PaymentViewModel.kt`/`PaymentMethodRow.kt`/`ValidateFolioUseCase.kt`/`CalculateInstallmentsUseCase.kt` ya anticipados; `Installment.kt`/`Payment.kt` anticipados sin campos definidos (esta historia los define)
- [Source: _bmad-output/planning-artifacts/epics.md#UX-DR11,UX-DR12] `QuantityStepper` (ya construido, Historia 3.2), `PaymentMethodRow` (spec exacta de layout)
- [Source: backend/src/Sumitrack.Api/Infrastructure/Extensions/ApplicationBuilderExtensions.cs] (líneas 94-99) — confirma que `serie_folio`/`max_parcialidades` ya se siembran en el backend y se descargan desde Historia 1.4, sin necesitar Historia 5.1
- [Source: _bmad-output/implementation-artifacts/3-2-seleccion-de-cliente-e-items-en-nueva-orden.md] `OrderDraftItem` (carrito en memoria, reutilizado tal cual); decisión de arquitectura sobre ViewModels no compartidos entre pantallas
- [Source: _bmad-output/implementation-artifacts/2-4-catalogo-de-productos-y-variantes.md] `TransactionRunner` (reutilizado tal cual para `createSale`)
- [Source: android/app/src/main/java/com/sumitrack/android/data/repositories/SaleRepository.kt, SettingsRepository.kt, data/local/entities/SaleEntity.kt] estado actual antes de esta historia

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (`claude-sonnet-5`)

### Debug Log References

Único desvío respecto a la especificación literal de T14: el spec sugería `navController.popBackStack(Routes.Orders.route, false)` desde `onConfirmed` sin mostrar el Snackbar explícitamente en ningún composable — se implementó el Snackbar dentro de `PaymentScreen` (con su propio `SnackbarHostState`, mostrado justo antes de invocar `onConfirmed()`) porque `NavGraph.kt` no tiene un `SnackbarHostState` persistente entre composables de ruta; mostrarlo en la pantalla que está a punto de salir es la opción más simple sin tocar `OrderListScreen` (fuera del alcance de tareas). Aparte de esto, ninguna otra desviación: todas las decisiones de arquitectura delicadas (SaleItemEntity nueva, PaymentConfig sealed class, ruptura de constructor de SaleRepository, CartRouteCodec) ya venían resueltas en el Dev Notes de la historia y se siguieron tal cual.

Dos ajustes menores durante la verificación de build, no anticipados en el spec (API de Material3 1.4.0 difiere de lo escrito en los snippets de la historia):
- `ExposedDropdownMenu` en `PaymentMethodRow.kt` es miembro de `ExposedDropdownMenuBoxScope` (no top-level) — se quitó el import incorrecto.
- `Modifier.menuAnchor()` sin argumentos está deprecado en 1.4.0 — se usó el overload con `ExposedDropdownMenuAnchorType.PrimaryNotEditable`.
- `Divider` deprecado → `HorizontalDivider` en `OrderSummaryScreen.kt`.

### Completion Notes List

Historia implementada completa. 179 tests ✅ (0 fallos, +28 sobre Historia 3.2). `BUILD SUCCESSFUL` (`assembleDebug` + `testDebugUnitTest`, sin warnings en archivos nuevos/modificados de esta historia — persiste 1 warning pre-existente y fuera de alcance en `ClientListViewModel.kt` sobre `@FlowPreview`, no tocado por esta historia).

- AC-1 ✅: `OrderSummaryScreen` (S-06) lista ítems con subtotal por línea (`OrderSummaryItemRow`), sección fija al fondo (subtotal/impuestos/total vía `SummaryLine`), nombre del cliente en la `TopAppBar`, botón "Editar" (`popBackStack()` a S-04), botón "Ir a Pagar" reenvía `clientId`+carrito codificado a S-07.
- AC-2 ✅: `PaymentMethodRow` (dropdown `minWidth=120dp`, campo de monto `weight(1f)`, botón `×` con `contentDescription` dinámico) construido en modo "Pago inmediato"; `onPaymentMethodTypeChange` rechaza un segundo método Efectivo con mensaje de error; "+ Agregar método" agrega filas nuevas (con heurística para no proponer un segundo Efectivo por defecto).
- AC-3 ✅: "Restante por asignar" (`PaymentUiState.remaining`, derivado) en `titleMedium`/`colorScheme.primary` con `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`; "Confirmar Pago" habilitado solo cuando `remaining.compareTo(BigDecimal.ZERO) == 0`.
- AC-4 ✅: número de parcialidades validado contra `1..maxParcialidades` (leído de Settings, default 15); `CalculateInstallmentsUseCase` sugiere montos+fechas por periodicidad (semanal/quincenal/mensual, `SegmentedButton` reutilizado); fechas/montos editables por parcialidad (`DatePickerDialog` para fecha); suma validada exactamente contra el total antes de habilitar "Confirmar Pago".
- AC-5 ✅: `ValidateFolioUseCase` (serie de Settings + conteo local de ventas del tenant + 1) se invoca en `onConfirmClick` antes de `SaleRepository.createSale`; el folio queda persistido en el `SaleEntity` creado y nunca se recalcula ni reutiliza (cada llamada cuenta ventas ya existentes, incluyendo canceladas si las hubiera).
- **Ruptura de constructor de `SaleRepository`** (documentada en Dev Notes): 8 archivos de test existentes actualizados con `FakeTransactionRunner`/`FakeSaleItemDao`/`FakeInstallmentDao`/`FakePaymentDao` (triviales donde no se ejercita `createSale`, reales en `SaleRepositoryTest`).
- Sin test de Composable/UI (`PaymentMethodRow`/`OrderSummaryScreen`/`PaymentScreen`) — mismo criterio que historias previas, el proyecto no tiene infraestructura de test de Composables. `DatePickerDialog` y `LiveRegionMode`/TalkBack no pudieron verificarse sin dispositivo.
- **Pendiente:** verificación manual en emulador/dispositivo físico — este entorno no tiene `adb` ni emulador Android disponible (mismo pendiente arrastrado desde Historias 2.1-3.2). Recomendado antes de mergear: Orden → cliente → productos → "Revisar Orden" (S-06, verificar subtotal/impuestos/total) → "Ir a Pagar" → modo Pago inmediato (agregar 2 métodos, verificar que Efectivo no se puede repetir, Restante llega a $0.00, TalkBack anuncia el cambio) → Confirmar Pago (Snackbar + regreso a S-02, venta visible en Historial) → repetir con modo Parcialidades (cambiar número/periodicidad, editar una fecha vía `DatePickerDialog`, confirmar con suma exacta).

**Post-code-review:** 11 patches aplicados (ver "Review Findings" arriba) — doble-submit en `onConfirmClick`, guardia de carrito/ítems vacíos, `maxParcialidades` acotado al límite duro de `CalculateInstallmentsUseCase` (evita un crash real), montos negativos excluidos de `assignedAmount`/`installmentsSum` (Pago inmediato y Parcialidades), plan de parcialidades viejo ya no queda confirmable tras invalidar el count, `calculateOrderTotals` (nuevo, `OrderTotals.kt`) centraliza y redondea subtotal/tax/total (antes triplicado y sin `setScale`), backfill de `subtotal`/`tax` en `MIGRATION_4_5`, scroll agregado a `PaymentScreen`, corrección del conteo del Change Log. 2 hallazgos diferidos a `deferred-work.md`. 8 tests nuevos agregados para cubrir las correcciones (`OrderTotalsTest` + `PaymentViewModelTest`). **187 tests ✅ (0 fallos)**, `BUILD SUCCESSFUL` sin warnings nuevos.

### File List

**Archivos creados (NEW):**
- `android/app/src/main/java/com/sumitrack/android/domain/models/SaleItem.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/models/InstallmentStatus.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/models/Installment.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/models/PaymentMethodType.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/models/Payment.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/models/InstallmentPeriodicity.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/entities/SaleItemEntity.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/entities/InstallmentEntity.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/entities/PaymentEntity.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/SaleItemDao.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/InstallmentDao.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/PaymentDao.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/usecases/ValidateFolioUseCase.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/usecases/CalculateInstallmentsUseCase.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/components/PaymentMethodRow.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/CartRouteCodec.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderSummaryViewModel.kt` — [Review] `total`/`subtotal`/`tax` vía `calculateOrderTotals`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderSummaryScreen.kt` — [Review] botón "Ir a Pagar" deshabilitado con `items` vacío
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/PaymentViewModel.kt` — [Review] fix doble-submit, `maxParcialidades` acotado, montos negativos excluidos, plan viejo se limpia al invalidar el count, `total` vía `calculateOrderTotals`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/PaymentScreen.kt` — [Review] scroll agregado; `LazyColumn` de parcialidades reemplazada por `Column` simple
- `android/app/src/main/java/com/sumitrack/android/domain/models/OrderTotals.kt` — [Review] helper compartido de subtotal/tax/total con redondeo consistente
- `android/app/src/test/java/com/sumitrack/android/domain/models/OrderTotalsTest.kt` — [Review]
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/FakeSaleItemDao.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/FakeInstallmentDao.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/FakePaymentDao.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/FakeSettingsDao.kt`
- `android/app/src/test/java/com/sumitrack/android/domain/usecases/ValidateFolioUseCaseTest.kt`
- `android/app/src/test/java/com/sumitrack/android/domain/usecases/CalculateInstallmentsUseCaseTest.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/OrderSummaryViewModelTest.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/PaymentViewModelTest.kt`

**Archivos modificados (UPDATE):**
- `android/app/src/main/java/com/sumitrack/android/data/local/entities/SaleEntity.kt` — + `subtotal`, + `tax`
- `android/app/src/main/java/com/sumitrack/android/data/local/SumitrackDatabase.kt` — `version` 4→5, + 3 entidades/DAOs
- `android/app/src/main/java/com/sumitrack/android/data/local/Migrations.kt` — + `MIGRATION_4_5`; [Review] backfill de `subtotal`/`tax` para ventas preexistentes
- `android/app/src/main/java/com/sumitrack/android/di/DatabaseModule.kt` — + 3 `provide*Dao`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/SaleDao.kt` — + `countSalesForTenant`
- `android/app/src/main/java/com/sumitrack/android/data/repositories/SettingsRepository.kt` — + `getValue`
- `android/app/src/main/java/com/sumitrack/android/data/repositories/SaleRepository.kt` — + `createSale`, `PaymentConfig`; constructor gana 4 parámetros; [Review] usa `calculateOrderTotals` en vez de fórmula duplicada
- `android/app/src/main/java/com/sumitrack/android/domain/usecases/CalculateInstallmentsUseCase.kt` — [Review] `MAX_INSTALLMENTS_HARD_LIMIT` público, usado para acotar `maxParcialidades`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/ItemListViewModel.kt` — `clientId` expuesto públicamente
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/ItemListScreen.kt` — "Revisar Orden" navega de verdad a S-06; [Review] botón deshabilitado con carrito vacío
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/Routes.kt` — + `OrderSummary`, `Payment`
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/NavGraph.kt` — + composables S-06/S-07, wiring completo
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/FakeSaleDao.kt` — + `countSalesForTenant`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/ClientFormViewModelTest.kt` — constructor `SaleRepository` actualizado
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/ClientListViewModelTest.kt` — constructor `SaleRepository` actualizado
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/ClientProfileViewModelTest.kt` — constructor `SaleRepository` actualizado
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/OrderListViewModelTest.kt` — constructor `SaleRepository` actualizado
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/ClientSelectViewModelTest.kt` — constructor `SaleRepository` actualizado
- `android/app/src/test/java/com/sumitrack/android/data/repositories/ClientRepositoryTest.kt` — constructor `SaleRepository` actualizado
- `android/app/src/test/java/com/sumitrack/android/data/repositories/SaleRepositoryTest.kt` — constructor actualizado + 6 casos de `createSale`
- `android/app/src/test/java/com/sumitrack/android/domain/usecases/CalculateClientBalanceUseCaseTest.kt` — constructor `SaleRepository` actualizado

## Change Log

- **2026-07-26** — Historia 3.3 implementada completa (Status: review)
  - NEW: `SaleItem`/`SaleItemEntity`/`SaleItemDao` (gap de planeación resuelto: primera venta real requiere desglose de ítems, con snapshots de nombre/precio/impuesto)
  - NEW: `Installment`/`InstallmentEntity`/`InstallmentDao`, `Payment`/`PaymentMethodType`/`PaymentEntity`/`PaymentDao`
  - UPDATE: `SaleEntity` + `subtotal`/`tax`; `SumitrackDatabase` versión 4→5; `MIGRATION_4_5`
  - NEW: `ValidateFolioUseCase` (folio local, sin componente de servidor — Epic 4 pendiente), `CalculateInstallmentsUseCase` (con ajuste de redondeo en la última parcialidad)
  - UPDATE: `SaleRepository` + `createSale`/`PaymentConfig` — constructor gana `TransactionRunner`/`SaleItemDao`/`InstallmentDao`/`PaymentDao`, 8 archivos de test actualizados
  - NEW: `PaymentMethodRow` (dropdown+monto+botón eliminar), `CartRouteCodec` (reenvío de carrito por argumentos de ruta)
  - NEW: `OrderSummaryViewModel`/`OrderSummaryScreen` (S-06), `PaymentViewModel`/`PaymentScreen` (S-07, `SegmentedButton`+`DatePickerDialog` primera vez en el proyecto)
  - UPDATE: `ItemListScreen`/`ItemListViewModel` — "Revisar Orden" navega de verdad; `Routes`/`NavGraph` — rutas `order_summary`/`payment`, wiring S-04→S-06→S-07→S-02
  - NEW: tests — `ValidateFolioUseCaseTest` (5), `CalculateInstallmentsUseCaseTest` (7), `OrderSummaryViewModelTest` (3), `PaymentViewModelTest` (8), + 6 casos de `createSale` en `SaleRepositoryTest`
  - Build: 179 tests ✅ (0 fallos, +28 sobre Historia 3.2), `BUILD SUCCESSFUL` (`assembleDebug` + `testDebugUnitTest`, JDK de Android Studio)
  - Pendiente: verificación manual en dispositivo/emulador (sin `adb` en este entorno); code review todavía no ejecutado

- **2026-07-26** — Code review completo, 11 patches aplicados (Status: done)
  - Review: Blind Hunter (18 hallazgos) + Edge Case Hunter (12) + Acceptance Auditor (2) en paralelo → 0 decision-needed, 11 patch, 2 defer, 7 dismiss
  - PATCH: doble-submit en `PaymentViewModel.onConfirmClick` (guard síncrono + `isSaving` ya no se resetea antes de navegar)
  - PATCH: "Revisar Orden"/"Ir a Pagar" deshabilitados con carrito/ítems vacío (evitaba una venta de $0 que consumía folio)
  - PATCH: `maxParcialidades` acotado a `CalculateInstallmentsUseCase.MAX_INSTALLMENTS_HARD_LIMIT` (evitaba un crash real si Settings excede 15)
  - PATCH: montos negativos excluidos de `assignedAmount`/`installmentsSum`, y de lo persistido en modo Parcialidades (antes solo se filtraban en Pago inmediato)
  - PATCH: `onInstallmentCountChange` limpia el plan anterior al invalidar el count (ya no quedaba confirmable un plan viejo con el error visible)
  - NEW: `OrderTotals.kt`/`calculateOrderTotals` — reemplaza la fórmula de subtotal/tax/total triplicada (`SaleRepository`, `OrderSummaryUiState`, `PaymentUiState`) por un único helper con `setScale(2, HALF_UP)` consistente
  - PATCH: `MIGRATION_4_5` hace backfill (`subtotal = total, tax = '0'`) para ventas preexistentes
  - PATCH: `PaymentScreen` con `verticalScroll`; `LazyColumn` de parcialidades reemplazada por `Column` simple (máximo 15 filas)
  - PATCH: corrección de conteo en este Change Log (+28, no +30)
  - DEFER: pérdida silenciosa de ítem del carrito si el producto fue eliminado (agregado a `deferred-work.md`, prioridad alta dentro del bucket ya existente)
  - DEFER: sin test de rollback transaccional de `createSale` (agregado a `deferred-work.md`)
  - NEW: tests — `OrderTotalsTest` (3), + 6 casos nuevos en `PaymentViewModelTest`
  - Build: **187 tests ✅** (0 fallos, +8 sobre el conteo pre-review), `BUILD SUCCESSFUL` sin warnings nuevos

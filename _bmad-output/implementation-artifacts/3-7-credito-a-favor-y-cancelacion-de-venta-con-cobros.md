---
baseline_commit: 74eaa383be079a102fb12b3adae854b9c2d7b1b1
---

# Story 3.7: Crédito a Favor y Cancelación de Venta con Cobros

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

Como proveedor,
quiero cancelar una venta y gestionar correctamente los pagos ya recibidos,
para que mi contabilidad quede exacta y ningún dinero se pierda sin registro.

## Acceptance Criteria

**AC-1 — Cancelar venta sin cobros**

**Dado** que el proveedor cancela una venta sin cobros registrados
**Cuando** confirma en el `AlertDialog`
**Entonces** la venta pasa a `Cancelado`; las parcialidades pendientes se cancelan; el saldo del cliente se actualiza

**AC-2 — Segundo dialog para venta con cobros**

**Dado** que el proveedor cancela una venta con cobros (estado Parcial)
**Cuando** confirma en el dialog
**Entonces** aparece un segundo dialog con dos opciones: "Cancelar parcialidades" (Opción A — acuerdo manual, cobros quedan en historial) y "Generar Crédito a Favor" (Opción B — se crea `CreditBalanceEntity` por el monto total cobrado)

**AC-3 — Generar Crédito a Favor**

**Dado** que el proveedor elige Opción B (Crédito a Favor)
**Cuando** se ejecuta `ApplyCreditBalanceUseCase`
**Entonces** se crea `CreditBalanceEntity` con: UUID en cliente, `fk_client`, `fk_tenant`, monto en `BigDecimal`/`NUMERIC(18,6)`, `origin = CANCELLATION`, `fk_origin_sale`, `sync_status = pending`; el banner de Crédito a Favor aparece en S-12

**AC-4 — Chip de Crédito a Favor en S-07**

**Dado** que el cliente tiene Crédito a Favor y el proveedor crea una nueva venta
**Cuando** llega a S-07 (Pantalla de Pago)
**Entonces** aparece chip informativo con el monto de Crédito disponible; el proveedor puede aplicarlo como método de pago

**AC-5 — Integración del chip con el constructor de métodos de pago**

**Dado** que la `CreditBalanceEntity` ya existe (creada en esta historia) y la `PaymentScreen` (S-07) tiene el hook visual del chip
**Cuando** el chip de Crédito a Favor se integra en S-07
**Entonces** el chip muestra el monto exacto disponible para ese cliente; al seleccionarlo se suma como método de pago en el constructor de `PaymentMethodRow`

**AC-6 — Cobertura total con Crédito**

**Dado** que el Crédito a Favor cubre el total de la venta
**Cuando** se aplica
**Entonces** la venta pasa directamente a `Liquidado`; el Crédito se descuenta del saldo disponible

**AC-7 — Los cobros nunca se borran**

**Dado** que se cancela una venta con cobros (cualquier opción elegida)
**Cuando** finaliza el proceso
**Entonces** todos los `PaymentEntity` previos quedan en historial para auditoría; nunca se borran

### Fuera de alcance en esta historia (explícito)

- **Crédito que excede el total de una venta (vuelto/cambio) con notificación dedicada al usuario** — ninguna AC describe este caso; AC-6 solo describe "el Crédito cubre el total". Esta historia sí maneja correctamente el caso de sobra (ver Dev Notes → "Modelado de `CreditBalanceEntity`"): el monto de cada fila de crédito se reduce en sitio a medida que se consume (nunca se pierde dinero), pero no hay UI especial de "te sobró crédito" más allá de que el banner de S-12 seguirá mostrando el remanente en la siguiente consulta.
- **Sincronización real de `credit_balances` con el backend** — `sync_status = pending` queda listo en la entidad (AC-3 lo exige explícitamente), pero la lista de entidades sincronizables de Epic 4 (Historia 4.1) no incluye `credit_balances` todavía; agregarlo ahí es responsabilidad de Epic 4, no de esta historia.
- **Cambios en el backend .NET** (`CreditBalanceController`, endpoint `PATCH /api/v1/sales/{id}/cancel`) — consistente con el resto del Epic 2/3 (100% Android desde Historia 2.1), el backend no se toca en ninguna historia de este epic.
- **`origin = MANUAL`** — el enum de `CreditBalanceEntity.origin` soporta `CANCELLATION`/`MANUAL` per `architecture.md`, pero esta historia solo escribe `CANCELLATION` (ningún AC describe un flujo de otorgar crédito manualmente). El valor `MANUAL` queda reservado en el enum para una historia futura.
- **`OpenSaleRow.toUiStatus()` hardcodeado a `PARTIAL`** en `ClientProfileScreen.kt` — gap preexistente desde Historia 2.3, ortogonal a esta historia (una venta `CANCELLED` nunca aparece en `openSales`, que ya filtra por `pending`/`partial`, así que el hardcode nunca muestra un badge incorrecto para una venta cancelada). No se toca aquí.
- **Tenant-scoping de `ClientRepository.getClientById`** — gap heredado (ya van 8 historias mencionándolo); si esta historia necesita el tenant de un cliente, lo obtiene de `client.fkTenant` ya cargado (mismo patrón que `ClientProfileViewModel.load()` ya usa), sin tocar la firma del método.

## Tasks / Subtasks

### Dominio — nuevos valores y modelo

- [x] **T1: `PaymentMethodType.CREDITO_A_FAVOR`** (AC-5)
  - [x] Agregar el valor al enum en `domain/models/PaymentMethodType.kt` y el caso correspondiente en `fromString` (`"credito_a_favor" -> CREDITO_A_FAVOR`) — sin este caso, un `Payment` con `method = "credito_a_favor"` se reclasificaría silenciosamente como `EFECTIVO` al leerlo de vuelta (el `else -> EFECTIVO` actual es un catch-all, no un error explícito).
    ```kotlin
    enum class PaymentMethodType {
        EFECTIVO,
        TRANSFERENCIA,
        TARJETA,
        CREDITO_A_FAVOR;

        companion object {
            fun fromString(value: String): PaymentMethodType = when (value.lowercase()) {
                "transferencia" -> TRANSFERENCIA
                "tarjeta" -> TARJETA
                "credito_a_favor" -> CREDITO_A_FAVOR
                else -> EFECTIVO
            }
        }
    }
    ```
  - [x] Actualizar las 3 copias duplicadas de `paymentMethodLabel()`/`registerPaymentMethodLabel()` (`ui/components/PaymentMethodRow.kt`, `ui/screens/orders/OrderDetailScreen.kt`) para incluir `CREDITO_A_FAVOR -> "Crédito a Favor"` — sin esto, `PaymentMethodType.entries.forEach` en cualquier `when` no exhaustivo rompe la compilación (Kotlin exige exhaustividad en `when` de enum sin `else`); si alguna de las tres tiene `else`, no se detectará hasta runtime, así que revisar cada una explícitamente.

- [x] **T2: `InstallmentStatus.CANCELLED` + `InstallmentUiStatus.CANCELLED`** (AC-1)
  - [x] Agregar `CANCELLED` a `domain/models/InstallmentStatus.kt` y su caso en `fromString` (`"cancelled" -> CANCELLED`):
    ```kotlin
    enum class InstallmentStatus { PENDING, PAID, CANCELLED; ... }
    ```
  - [x] Agregar `CANCELLED` a `InstallmentUiStatus` (`ui/screens/orders/OrderDetailViewModel.kt`) y el caso correspondiente en `toUiStatus()`:
    ```kotlin
    enum class InstallmentUiStatus { PENDING, PAID, OVERDUE, CANCELLED }

    fun Installment.toUiStatus(now: Instant = Instant.now()): InstallmentUiStatus = when {
        status == InstallmentStatus.CANCELLED -> InstallmentUiStatus.CANCELLED
        status == InstallmentStatus.PAID -> InstallmentUiStatus.PAID
        dueDate.isBefore(now) -> InstallmentUiStatus.OVERDUE
        else -> InstallmentUiStatus.PENDING
    }
    ```
  - [x] Agregar el caso `CANCELLED -> "Cancelada" to StatusOverdue` (o un color neutro — decisión de implementación, no bloqueante) en `installmentStatusLabelAndColor()` de `OrderDetailScreen.kt` — sin este caso el `when` exhaustivo no compila.

- [x] **T3: `CreditBalance` (dominio) + `CreditOrigin`** (AC-3)
  - [x] Crear `domain/models/CreditBalance.kt`:
    ```kotlin
    enum class CreditOrigin { CANCELLATION, MANUAL }

    data class CreditBalance(
        val id: String,
        val fkTenant: String,
        val fkClient: String,
        val amount: BigDecimal,
        val origin: CreditOrigin,
        val fkOriginSale: String?,
        val appliedAt: Instant?,
        val createdAt: Instant,
        val updatedAt: Instant,
        val syncStatus: SyncStatus,
    )
    ```

### Base de datos — nueva entidad, DAO y migración

- [x] **T4: `CreditBalanceEntity` + `CreditBalanceDao`** (AC-3)
  - [x] Crear `data/local/entities/CreditBalanceEntity.kt` (mismo patrón que `PaymentEntity`/`InstallmentEntity`, `@ColumnInfo` snake_case):
    ```kotlin
    @Entity(tableName = "credit_balances")
    data class CreditBalanceEntity(
        @PrimaryKey @ColumnInfo(name = "id") val id: String,
        @ColumnInfo(name = "fk_tenant") val fkTenant: String,
        @ColumnInfo(name = "fk_client") val fkClient: String,
        @ColumnInfo(name = "amount") val amount: BigDecimal,
        @ColumnInfo(name = "origin") val origin: String,
        @ColumnInfo(name = "fk_origin_sale") val fkOriginSale: String?,
        @ColumnInfo(name = "applied_at") val appliedAt: Instant?,
        @ColumnInfo(name = "created_at") val createdAt: Instant,
        @ColumnInfo(name = "updated_at") val updatedAt: Instant,
        @ColumnInfo(name = "sync_status") val syncStatus: String = "pending",
    )
    ```
  - [x] Crear `data/local/dao/CreditBalanceDao.kt`, mismo patrón que `InstallmentDao`/`PaymentDao`:
    ```kotlin
    @Dao
    interface CreditBalanceDao {
        @Query("SELECT * FROM credit_balances WHERE fk_client = :clientId AND fk_tenant = :tenantId")
        suspend fun getForClient(clientId: String, tenantId: String): List<CreditBalanceEntity>

        @Upsert
        suspend fun upsertAll(rows: List<CreditBalanceEntity>)
    }
    ```

- [x] **T5: `MIGRATION_5_6` + registro en `SumitrackDatabase`/`DatabaseModule`** (AC-3)
  - [x] Agregar a `data/local/Migrations.kt`, mismo patrón exacto que las migraciones anteriores (`TEXT`/`INTEGER` vía los converters ya registrados, `sync_status TEXT NOT NULL DEFAULT 'pending'`):
    ```kotlin
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS credit_balances (
                    id TEXT NOT NULL PRIMARY KEY,
                    fk_tenant TEXT NOT NULL,
                    fk_client TEXT NOT NULL,
                    amount TEXT NOT NULL,
                    origin TEXT NOT NULL,
                    fk_origin_sale TEXT,
                    applied_at INTEGER,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    sync_status TEXT NOT NULL DEFAULT 'pending'
                )
                """.trimIndent()
            )
        }
    }
    ```
    Agregar `MIGRATION_5_6` al array `ALL`.
  - [x] `SumitrackDatabase.kt`: agregar `CreditBalanceEntity::class` a `entities = [...]`, subir `version = 6`, agregar `abstract fun creditBalanceDao(): CreditBalanceDao`.
  - [x] `DatabaseModule.kt`: agregar `@Provides @Singleton fun provideCreditBalanceDao(db: SumitrackDatabase): CreditBalanceDao = db.creditBalanceDao()`.
  - [x] **Verificar el schema exportado** (`android/app/schemas/`) se regenera correctamente al compilar (Room con `exportSchema = true` ya configurado) — no requiere acción manual, solo confirmar que no hay error de migración al correr los tests instrumentados/JVM que crean la BD en memoria.

### Repositorio y casos de uso

- [x] **T6: `SaleRepository.cancelSale(...)`** (AC-1, AC-2, AC-7)
  - [x] Agregar a `data/repositories/SaleRepository.kt`, inyectando `creditBalanceDao` como nueva dependencia del constructor (mismo patrón de acumulación que `installmentDao`/`paymentDao`):
    ```kotlin
    // Cancela la venta y todas sus parcialidades no pagadas (nunca toca `payments` — AC-7).
    // Si `creditAmount` no es null, además otorga Crédito a Favor por ese monto en la MISMA
    // transacción (evita el caso "venta cancelada pero el crédito no se generó" ante un fallo
    // a mitad de operación).
    suspend fun cancelSale(tenantId: String, saleId: String, creditAmount: BigDecimal?): Boolean {
        return transactionRunner.run {
            val sale = saleDao.getById(saleId, tenantId) ?: return@run false
            val installments = installmentDao.getForSale(saleId, tenantId)
            val toCancel = installments.filter { it.status == "pending" }
                .map { it.copy(status = "cancelled", updatedAt = Instant.now(), syncStatus = "pending") }
            if (toCancel.isNotEmpty()) installmentDao.upsertAll(toCancel)
            saleDao.upsertAll(listOf(sale.copy(status = "cancelled", updatedAt = Instant.now(), syncStatus = "pending")))
            if (creditAmount != null && creditAmount > BigDecimal.ZERO) {
                val now = Instant.now()
                creditBalanceDao.upsertAll(
                    listOf(
                        CreditBalanceEntity(
                            id = UUID.randomUUID().toString(),
                            fkTenant = tenantId,
                            fkClient = sale.fkClient,
                            amount = creditAmount,
                            origin = "cancellation",
                            fkOriginSale = saleId,
                            appliedAt = null,
                            createdAt = now,
                            updatedAt = now,
                            syncStatus = "pending",
                        )
                    )
                )
            }
            true
        }
    }
    ```
    Mismo criterio que `registerPayment` (Historia 3.6, corregido en su code review): la venta se busca **primero**, antes de escribir nada, para que un `return@run` temprano no deje escrituras huérfanas a medio camino.

  - [x] **`SaleRepository.getAvailableCredit(clientId, tenantId): BigDecimal`** (AC-4) — suma `amount` de todas las filas de `credit_balances` del cliente (ver Dev Notes → modelado: `amount` es el remanente actual de cada fila, no el monto histórico original, así que sumar todas las filas sin filtrar por `appliedAt` ya da el total disponible correcto):
    ```kotlin
    suspend fun getAvailableCredit(clientId: String, tenantId: String): BigDecimal =
        creditBalanceDao.getForClient(clientId, tenantId).fold(BigDecimal.ZERO) { acc, row -> acc + row.amount }
    ```

  - [x] **Consumo de crédito dentro de `createSale`** (AC-5, AC-6) — cuando `paymentConfig` es `Immediate` y contiene un método `CREDITO_A_FAVOR`, además de insertar el `PaymentEntity` normal (ya lo hace el código existente, sin cambios ahí), reducir en sitio las filas de `credit_balances` del cliente por ese monto, FIFO (filas más antiguas primero), dentro de la MISMA transacción de `createSale`:
    ```kotlin
    val creditUsed = paymentConfig.payments.filter { it.first == PaymentMethodType.CREDITO_A_FAVOR }.sumOf { it.second }
    if (creditUsed > BigDecimal.ZERO) {
        var remaining = creditUsed
        val rows = creditBalanceDao.getForClient(clientId, tenantId).sortedBy { it.createdAt }
        val updated = mutableListOf<CreditBalanceEntity>()
        for (row in rows) {
            if (remaining <= BigDecimal.ZERO) break
            val consume = row.amount.min(remaining)
            updated += row.copy(amount = row.amount - consume, appliedAt = now, updatedAt = now, syncStatus = "pending")
            remaining -= consume
        }
        if (updated.isNotEmpty()) creditBalanceDao.upsertAll(updated)
    }
    ```
    Insertar esta lógica dentro del bloque `transactionRunner.run { }` existente de `createSale`, después de `paymentDao.upsertAll(...)` del caso `Immediate`. **No valida** que `creditUsed` no exceda el crédito disponible del cliente — esa validación vive en `PaymentViewModel`/`ApplyCreditBalanceUseCase` (capa de Use Case, no en el repositorio, mismo criterio ya establecido en Historia 3.6 para `registerPayment`).

- [x] **T7: `ApplyCreditBalanceUseCase`** (AC-3)
  - [x] Crear `domain/usecases/ApplyCreditBalanceUseCase.kt` — **ojo con el nombre**: pese a llamarse "Apply", el AC-3 lo describe ejecutándose para *crear* el `CreditBalanceEntity` al cancelar (otorgar crédito), no para consumirlo en una venta nueva (eso lo hace `createSale`, ver T6). Es el nombre literal que usa `epics.md`, se respeta tal cual:
    ```kotlin
    class ApplyCreditBalanceUseCase @Inject constructor(
        private val saleRepository: SaleRepository,
    ) {
        suspend operator fun invoke(tenantId: String, saleId: String, amount: BigDecimal): Boolean =
            saleRepository.cancelSale(tenantId, saleId, creditAmount = amount)
    }
    ```
    (Delegación directa a `cancelSale` con `creditAmount` — ver T8 para por qué `CancelSaleUseCase` no llama a este use case por separado sino que ambos convergen en el mismo método de repositorio transaccional.)

- [x] **T8: `CancelSaleUseCase`** (AC-1, AC-2)
  - [x] Crear `domain/usecases/CancelSaleUseCase.kt` — no está pre-nombrado en `architecture.md` (igual que `RegisterPaymentUseCase` no lo estaba hasta que la Historia 3.6 lo necesitó); sigue el mismo patrón de validación-en-use-case ya establecido:
    ```kotlin
    class CancelSaleUseCase @Inject constructor(
        private val saleRepository: SaleRepository,
    ) {
        suspend operator fun invoke(tenantId: String, saleId: String, generateCredit: Boolean): Boolean {
            val detail = saleRepository.getSaleDetail(saleId, tenantId) ?: return false
            if (detail.sale.status == SaleStatus.CANCELLED) return false
            val totalCollected = detail.payments.fold(BigDecimal.ZERO) { acc, p -> acc + p.amount }
            val creditAmount = if (generateCredit && totalCollected > BigDecimal.ZERO) totalCollected else null
            return saleRepository.cancelSale(tenantId, saleId, creditAmount)
        }
    }
    ```
    **`generateCredit` se ignora silenciosamente si la venta no tiene cobros** (`totalCollected == 0`) — evita que un caller pase `generateCredit = true` por error en el camino de AC-1 (sin cobros) y termine creando una `CreditBalanceEntity` de $0.00 sin sentido.
    **Permite cancelar en cualquier estado no-`CANCELLED`** (incluyendo `PAID`) — FR-16 dice explícitamente "independientemente de su Estatus"; el AC-2 menciona "estado Parcial" como el caso ilustrativo pero el criterio real que determina si aparece el segundo dialog es "¿tiene cobros?", no el estado literal (una venta `PAID` también tiene cobros y debe pasar por el mismo segundo dialog — ver Dev Notes).

- [x] **T9: `CalculateAvailableCreditUseCase`** (AC-4)
  - [x] Crear `domain/usecases/CalculateAvailableCreditUseCase.kt`, mismo patrón que `CalculateClientBalanceUseCase` (deliberadamente un use case separado, no fusionado con el de saldo — "saldo" y "crédito a favor" son conceptos opuestos, mostrados como dos banners independientes en S-12, nunca como un solo número neto):
    ```kotlin
    class CalculateAvailableCreditUseCase @Inject constructor(
        private val saleRepository: SaleRepository,
    ) {
        suspend operator fun invoke(clientId: String, tenantId: String): BigDecimal =
            saleRepository.getAvailableCredit(clientId, tenantId)
    }
    ```

### UI — S-09: cancelación real

- [x] **T10: `OrderDetailViewModel` — reemplazar el placeholder por la cancelación real** (AC-1, AC-2, AC-7)
  - [x] Inyectar `CancelSaleUseCase` en el constructor.
  - [x] Eliminar `onCancelOrderConfirm()`/`cancelPlaceholderMessage` (placeholder de Historia 3.5) y reemplazar por:
    ```kotlin
    // showCreditChoiceDialog reemplaza cancelPlaceholderMessage — true cuando la venta tiene
    // cobros y se necesita que el proveedor elija Opción A/B antes de proceder.
    val showCreditChoiceDialog: Boolean = false,
    val isCancelling: Boolean = false,
    val cancelError: String? = null,
    val cancelSuccessMessage: String? = null,
    ```
    ```kotlin
    fun onCancelOrderConfirm() {
        if (uiState.value.paymentHistory.isEmpty()) {
            performCancel(generateCredit = false)
        } else {
            _uiState.update { it.copy(showCreditChoiceDialog = true) }
        }
    }

    fun onCreditChoiceDialogDismiss() {
        _uiState.update { it.copy(showCreditChoiceDialog = false) }
    }

    fun onCancelKeepingPayments() { // Opción A
        _uiState.update { it.copy(showCreditChoiceDialog = false) }
        performCancel(generateCredit = false)
    }

    fun onCancelWithCredit() { // Opción B
        _uiState.update { it.copy(showCreditChoiceDialog = false) }
        performCancel(generateCredit = true)
    }

    private fun performCancel(generateCredit: Boolean) {
        if (_uiState.value.isCancelling) return
        _uiState.update { it.copy(isCancelling = true, cancelError = null) }
        viewModelScope.launch {
            val tenant = tenantId.first()
            val success = tenant != null && runCatching {
                cancelSaleUseCase(tenant, saleId, generateCredit)
            }.getOrDefault(false)
            if (!success) {
                _uiState.update { it.copy(isCancelling = false, cancelError = "No se pudo cancelar la orden. Inténtalo de nuevo.") }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isCancelling = false,
                    cancelSuccessMessage = if (generateCredit) "Orden cancelada. Se generó Crédito a Favor para el cliente." else "Orden cancelada.",
                )
            }
            tenant?.let { loadOrder(it) }
        }
    }

    fun onCancelSuccessMessageShown() {
        _uiState.update { it.copy(cancelSuccessMessage = null) }
    }
    ```
    Mismo patrón exacto que `onConfirmRegisterPayment` (Historia 3.6): `runCatching` alrededor de la llamada al use case (Review Finding de esa historia — no repetir el gap), guard síncrono contra doble-tap, `loadOrder` para refrescar tras el éxito.

- [x] **T11: `OrderDetailScreen` — segundo dialog + fix del gap diferido de parcialidad tocable con venta cancelada** (AC-2)
  - [x] Reemplazar el `LaunchedEffect(uiState.cancelPlaceholderMessage)` por uno equivalente para `cancelSuccessMessage`/`cancelError` (Snackbar).
  - [x] Agregar el segundo `AlertDialog` (solo si `uiState.showCreditChoiceDialog`), copy descriptivo sin "Aceptar/Cancelar" genérico (convención de `EXPERIENCE.md` línea 247, ya seguida por el primer dialog de Historia 3.5):
    ```kotlin
    if (uiState.showCreditChoiceDialog) {
        AlertDialog(
            onDismissRequest = viewModel::onCreditChoiceDialogDismiss,
            title = { Text("Esta orden tiene cobros registrados") },
            text = { Text("¿Qué hacemos con los pagos ya recibidos?") },
            confirmButton = {
                TextButton(onClick = viewModel::onCancelWithCredit) { Text("Generar Crédito a Favor") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onCancelKeepingPayments) { Text("Cancelar parcialidades") }
            },
        )
    }
    ```
    (`dismissButton`/`confirmButton` aquí son solo los dos slots estándar de `AlertDialog` de M3 — **ninguno de los dos es un "cancelar la acción"**, ambos son opciones reales A/B; `onDismissRequest` — tocar fuera o back — es la única forma de no elegir nada, dejando la venta sin cancelar.)
  - [x] **Fix del gap diferido explícitamente bloqueado en esta historia** (`deferred-work.md`, entrada de Historia 3.5): `InstallmentRow`'s `onClick` debe dejar de ser tocable también cuando `uiState.status == SaleStatus.CANCELLED`, no solo cuando la parcialidad individual ya está pagada:
    ```kotlin
    onClick = if (installment.toUiStatus() != InstallmentUiStatus.PAID && uiState.status != SaleStatus.CANCELLED) {
        { viewModel.onRegisterPaymentClick(installment.id) }
    } else {
        null
    }
    ```

### UI — S-12: banner de Crédito a Favor

- [x] **T12: `ClientProfileViewModel`/`ClientProfileScreen` — wiring del banner ya construido** (AC-3)
  - [x] Inyectar `CalculateAvailableCreditUseCase` en `ClientProfileViewModel`.
  - [x] Agregar `creditBalance: BigDecimal? = null` a `ClientProfileUiState`; en `load()`, tras cargar `client` exitosamente, obtener `calculateAvailableCreditUseCase(clientId, client.fkTenant)` con el mismo patrón `try/catch(CancellationException) rethrow` ya usado ahí (no introducir un patrón nuevo).
  - [x] `ClientProfileScreen.kt`: cambiar `FinancialAlertBanner(overdueAmount = null, creditAmount = null)` → `FinancialAlertBanner(overdueAmount = null, creditAmount = uiState.creditBalance?.takeIf { it > BigDecimal.ZERO })` — el componente ya existe completo desde Historia 2.3 (ver Dev Notes), solo se le pasa el dato real; `overdueAmount` se queda en `null` (Epic 5, fuera de alcance).

### UI — S-07: chip de Crédito a Favor

- [x] **T13: `PaymentViewModel`/`PaymentScreen` — chip y aplicación como método de pago** (AC-4, AC-5, AC-6)
  - [x] Inyectar `CalculateAvailableCreditUseCase` en `PaymentViewModel`; en `init { }`, junto a la carga de `items`, obtener el crédito disponible del `clientId` de esta pantalla y guardarlo en un nuevo campo `availableCredit: BigDecimal = BigDecimal.ZERO` de `PaymentUiState`.
  - [x] Nuevo método `onApplyCreditClick()`: agrega un `PaymentMethodDraft` con `type = PaymentMethodType.CREDITO_A_FAVOR` y `amountText = min(availableCredit, remaining).toPlainString()`, **solo si no hay ya una fila de crédito agregada** (mismo criterio anti-duplicado que `onAddPaymentMethod` ya usa para `EFECTIVO`):
    ```kotlin
    fun onApplyCreditClick() {
        _uiState.update { state ->
            if (state.paymentMethods.any { it.type == PaymentMethodType.CREDITO_A_FAVOR }) return@update state
            val amount = state.availableCredit.min(state.remaining)
            if (amount <= BigDecimal.ZERO) return@update state
            val draft = PaymentMethodDraft(type = PaymentMethodType.CREDITO_A_FAVOR, amountText = amount.toPlainString())
            val methods = if (state.paymentMethods.size == 1 && state.paymentMethods.first().amountText.isBlank()) {
                listOf(draft) // reemplaza la fila vacía inicial en vez de sumarse a ella
            } else {
                state.paymentMethods + draft
            }
            state.copy(paymentMethods = methods)
        }
    }
    ```
  - [x] `PaymentScreen.kt`: chip/`Surface` visible cuando `uiState.availableCredit > BigDecimal.ZERO` y ninguna fila ya usa `CREDITO_A_FAVOR`, con el texto exacto ya establecido en `ClientProfileScreen.kt`/`EXPERIENCE.md`: `"Tiene ${formatAmount(uiState.availableCredit)} a su favor. Puedes aplicarlo al pago."`, botón "Aplicar" → `viewModel::onApplyCreditClick`.
  - [x] **AC-6 no requiere código nuevo en `onConfirmClick`**: `isImmediateConfirmEnabled` ya exige `remaining == 0` para cualquier combinación de métodos — con `CREDITO_A_FAVOR` como una fila más del constructor, cubrir el total completo con crédito ya deja la venta en `paid` (`Liquidado`) por el mismo camino que hoy usan `EFECTIVO`/`TRANSFERENCIA`/`TARJETA`. El descuento del crédito disponible ocurre en `SaleRepository.createSale` (T6), no aquí.

### Review Findings

Revisión adversarial en 3 capas paralelas (Blind Hunter, Edge Case Hunter, Acceptance Auditor) sobre el diff de esta historia (35 archivos, ~1817 líneas — la más grande del Epic 3). 0 `decision-needed`, 4 `patch` (ya aplicados), 1 `defer`, 9 hallazgos descartados como ruido, falsos positivos verificados, o patrones ya establecidos/aceptados en el proyecto.

- [x] [Review][Patch] **Sin validación de que el monto de `CREDITO_A_FAVOR` no excediera el crédito disponible del cliente** — reportado independientemente por los 3 revisores como el hallazgo de mayor severidad: `SaleRepository.createSale` consumía el FIFO hasta agotar las filas y simplemente se detenía sin error, dejando una venta marcada `paid` con más crédito del que el cliente realmente tenía [SaleRepository.kt] — corregido con `check(creditUsed <= available)` **antes** de mutar cualquier fila; al lanzar (no `return@run`), revierte toda la transacción (venta/ítems/pagos ya escritos en el mismo bloque).
- [x] [Review][Patch] **`PaymentMethodRow` permitía seleccionar `CREDITO_A_FAVOR` manualmente desde el dropdown genérico en cualquier fila**, con un monto libremente editable sin relación al crédito real — bypaseaba por completo el tope de `onApplyCreditClick` (`min(disponible, restante)`) y su protección contra duplicados [PaymentMethodRow.kt] — corregido excluyéndolo del dropdown (mismo criterio ya aplicado en `RegisterPaymentDialog` para "Registrar Cobro").
- [x] [Review][Patch] **`ApplyCreditBalanceUseCase` no tenía el guard contra venta ya cancelada que sí tiene `CancelSaleUseCase`** — una invocación repetida habría otorgado Crédito a Favor duplicado por el mismo monto [ApplyCreditBalanceUseCase.kt] — corregido con el mismo guard.
- [x] [Review][Patch] **Desempate no determinista en el consumo FIFO de crédito** cuando dos filas comparten `createdAt` exacto [SaleRepository.kt] — corregido agregando `id` como criterio de desempate secundario.
- [x] [Review][Defer] **`onCancelOrderConfirm` decide con `paymentHistory` cacheado en `uiState`, no con una lectura fresca** — un cobro registrado por otra vía entre la carga de la pantalla y el tap en "Cancelar Orden" podría cancelar la venta sin ofrecer Opción A/B. Mismo bucket que la condición de carrera ya diferida de `registerPayment` (Historia 3.6): inalcanzable hoy (app de un solo dispositivo/sesión, sin escritura concurrente real); revisar cuando Epic 4 introduzca sincronización multi-dispositivo.

**Descartados/verificados (9):** `CreditBalance`/`CreditOrigin` (dominio) confirmado código muerto sin ningún uso ni mapper en todo el codebase — **eliminado** como parte del mismo patch de limpieza (no solo descartado); `SaleRepository.cancelSale` sin guard propio contra doble-cancelación (ya cubierto por el guard agregado a `ApplyCreditBalanceUseCase` + el guard preexistente de `CancelSaleUseCase`, consistente con el patrón "validación en Use Cases, no en el repositorio" ya establecido en la revisión de 3.6); `registerPayment` sin guard contra venta cancelada (falso positivo — el guard ya existe desde Historia 3.6 en `RegisterPaymentUseCase`); `tenantId.first()` sin `runCatching` en `performCancel` (mismo patrón inconsistente ya diferido explícitamente en la revisión de 3.6); `InstallmentStatus.fromString` con default silencioso a `PENDING` (convención idéntica ya usada en `SaleStatus`/`PaymentMethodType`/`SyncStatus` en todo el proyecto); import de `BigDecimal` faltante en `ClientProfileScreen.kt` (falso positivo — ya estaba importado desde Historia 2.3); sin índice en `credit_balances` (patrón ya diferido consistentemente en todo el esquema desde Historia 2.1); sin `FOREIGN KEY`/`CHECK` en `credit_balances` (ningún `@ForeignKey` existe en ningún esquema del proyecto, decisión arquitectónica ya establecida); segundo dialog usando el slot `dismissButton` de M3 para "Opción A" (patrón defendible — ambos botones tienen copy descriptivo, nunca "Aceptar/Cancelar" genérico, `onDismissRequest` sigue siendo el verdadero no-op).

## Dev Notes

### Modelado de `CreditBalanceEntity` — por qué `amount` es el remanente, no el monto histórico

`architecture.md` (sección "Correcciones Post-Validación") especifica los campos de `CreditBalanceEntity` como `id, fk_client, fk_tenant, amount, origin, fk_origin_sale, applied_at, created_at, updated_at, sync_status` — sin ningún campo de "monto original" ni "monto restante" separados. Dado que FR-18b dice literalmente "el Crédito se descuenta del monto aplicado" (implica consumo parcial, no todo-o-nada) y AC-6 solo describe el caso donde el crédito cubre el total exacto (sin decir qué pasa si sobra), la decisión de modelado de esta historia es: **`amount` representa el saldo actual (remanente) de esa fila de crédito, no su monto histórico original.** Al otorgar crédito (`cancelSale` con `creditAmount`), se inserta una fila nueva con `amount = totalCobrado`. Al consumir crédito (`createSale` con un método `CREDITO_A_FAVOR`), las filas existentes del cliente se reducen en sitio (FIFO, más antiguas primero) hasta cubrir el monto usado — si una fila cubre más de lo necesario, su `amount` simplemente queda con el remanente (sigue disponible para la siguiente venta); si una fila se agota exactamente, su `amount` llega a `0` (deja de sumar en `getAvailableCredit`, sin necesidad de borrarla ni de un segundo estado "aplicada"). `applied_at` se actualiza en cada consumo (parcial o total) como timestamp informativo/de auditoría — **no** es el campo que determina disponibilidad (eso lo determina `amount > 0`), evitando así necesitar filtrar por él en `getAvailableCredit`.

Esta decisión evita: (a) inventar un campo "remaining" no listado en `architecture.md`, (b) inventar un valor de `origin` para "crédito parcialmente consumido" (el enum documentado solo tiene `CANCELLATION`/`MANUAL`), y (c) perder dinero del cliente cuando el crédito disponible excede el total de una venta puntual.

### Por qué el segundo dialog se dispara por "¿tiene cobros?" y no literalmente por `status == PARTIAL`

El AC-2 dice "cancela una venta con cobros (estado Parcial)" — pero FR-16 dice explícitamente que la cancelación aplica "independientemente de su Estatus", y una venta `Liquidado` (pagada por completo) también tiene cobros que hay que resolver de la misma manera (¿se quedan en historial sin más, o se convierten en Crédito a Favor?). Interpretar el AC-2 literalmente como "solo si `status == PARTIAL`" dejaría un hueco real: cancelar una venta ya pagada saltaría directo al camino de AC-1 (sin segundo dialog) pese a tener cobros reales que auditar — contradice AC-7 ("cualquier opción elegida", implicando que siempre hay una opción que elegir cuando hay cobros). Esta historia usa `detail.payments.isNotEmpty()` como el gate real (`CancelSaleUseCase`, T8), con `status == PARTIAL` como el caso más común pero no el único que lo dispara.

### Por qué `ApplyCreditBalanceUseCase` no es el que consume el crédito en una venta nueva

El nombre puede sugerir "aplicar crédito a una venta", pero el AC-3 lo ata explícitamente al momento de **cancelación** ("Cuando se ejecuta `ApplyCreditBalanceUseCase` / Entonces se crea `CreditBalanceEntity`..."). El consumo de crédito en una venta nueva (AC-5/AC-6) no tiene un nombre de use case asignado en `epics.md` ni en `architecture.md` — esta historia lo resuelve dentro de `SaleRepository.createSale` (T6) en vez de inventar un nombre no especificado, manteniendo el nombre `ApplyCreditBalanceUseCase` fiel a su AC literal.

### Reutilización total del banner de S-12 — ya construido desde Historia 2.3

`ClientProfileScreen.kt`'s `FinancialAlertBanner(overdueAmount, creditAmount)` fue implementado **completo** en Historia 2.3, pasando siempre `null` a ambos parámetros a propósito ("Fuera de alcance" de esa historia, con comentario explícito en el código citando que Épica 3 solo tendría que pasarle montos reales). Esta historia cumple exactamente esa promesa para `creditAmount` — cero cambios al componente visual, solo wiring de datos.

### Testing

- **100% JVM puro, sin Robolectric, sin tests de Compose UI** — mismo criterio de siempre.
- Nuevos fakes necesarios: `FakeCreditBalanceDao` (mismo patrón `mutableMapOf<String, CreditBalanceEntity>()` que `FakeInstallmentDao`/`FakePaymentDao`).
- `SaleRepositoryTest.kt` (+casos): `cancelSale` sin cobros cancela venta + parcialidades pendientes, no toca pagadas; `cancelSale` con `creditAmount` crea la fila de crédito en la misma transacción; `cancelSale` no re-escribe parcialidades ya `paid`; `getAvailableCredit` suma correctamente; `createSale` con un método `CREDITO_A_FAVOR` reduce las filas de crédito FIFO, deja remanente si sobra, agota una fila exactamente sin negativos.
- `CancelSaleUseCaseTest.kt` (nuevo): sin cobros + `generateCredit=true` → no crea crédito (monto sería $0); con cobros + Opción A → cancela sin crear crédito; con cobros + Opción B → cancela y crea crédito por el total cobrado; venta ya `CANCELLED` → `false`; venta no encontrada → `false`; venta `PAID` con cobros → mismo camino que `PARTIAL` (ver Dev Notes).
- `ApplyCreditBalanceUseCaseTest.kt` (nuevo): delega correctamente a `cancelSale` con el monto dado.
- `CalculateAvailableCreditUseCaseTest.kt` (nuevo): suma correcta, cero para cliente sin crédito, aísla por tenant.
- `OrderDetailViewModelTest.kt` (+casos): venta sin cobros → cancela directo, sin mostrar el segundo dialog; venta con cobros → `onCancelOrderConfirm` muestra `showCreditChoiceDialog`; Opción A/B llaman `cancelSaleUseCase` con `generateCredit` correcto; éxito recarga el detalle (parcialidades pendientes ahora `CANCELLED`); fallo (`runCatching`) no deja `isCancelling` atascado.
- `PaymentViewModelTest.kt` (+casos): `availableCredit` se carga en `init`; `onApplyCreditClick` agrega la fila de crédito una sola vez (no duplica en clicks repetidos); crédito que cubre el total exacto deja `isImmediateConfirmEnabled = true`.
- `ClientProfileViewModelTest.kt` (+casos): `creditBalance` se carga junto con `client`/`openSales`; banner no se muestra cuando el crédito es `0`.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Historia 3.7] — ACs verbatim, FR-16, FR-18b.
- [Source: _bmad-output/planning-artifacts/epics.md#Historia 2.3] — `FinancialAlertBanner`/S-12 ya construido con `creditAmount` como stub `null`.
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/EXPERIENCE.md#S-09, #S-07, #S-12] — specs de pantalla, convención de copy de dialogs (línea 247), microcopy de Crédito a Favor (línea 130).
- [Source: _bmad-output/planning-artifacts/architecture/architecture.md#Correcciones Post-Validación] — campos de `CreditBalanceEntity`, ubicación en el árbol de archivos.
- [Source: _bmad-output/planning-artifacts/architecture/architecture.md#Reglas Obligatorias] — UUID cliente-generado, `BigDecimal`/`NUMERIC(18,6)`, validación en Use Cases.
- [Source: android/.../data/repositories/SaleRepository.kt] — patrón `registerPayment` (Historia 3.6, ya corregido en su code review) replicado para `cancelSale`.
- [Source: android/.../ui/screens/orders/OrderDetailViewModel.kt, OrderDetailScreen.kt] — placeholder de Historia 3.5 a reemplazar.
- [Source: android/.../ui/screens/clients/ClientProfileScreen.kt, ClientProfileViewModel.kt] — banner ya construido en Historia 2.3.
- [Source: android/.../ui/screens/orders/PaymentViewModel.kt, PaymentScreen.kt] — constructor de métodos de pago (Historia 3.3) a extender.
- [Source: _bmad-output/implementation-artifacts/deferred-work.md#3-6] — gap bloqueado explícitamente en esta historia (`InstallmentRow` tocable con venta cancelada).

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- **Decisión de modelado (T3/T6):** `CreditBalanceEntity.amount` representa el saldo REMANENTE de esa fila de crédito (se reduce en sitio al consumirse), no el monto histórico original — decisión documentada en detalle en Dev Notes → "Modelado de CreditBalanceEntity". Evita perder dinero del cliente cuando el crédito disponible excede el total de una venta puntual, sin necesitar inventar un campo "remaining" ni un `origin` de "consumo parcial" no listados en `architecture.md`.
- **Decisión de alcance (T8):** `CancelSaleUseCase` dispara el segundo dialog (Opción A/B) basándose en "¿la venta tiene cobros?" (`detail.payments.isNotEmpty()`), no literalmente en `status == PARTIAL` — una venta `PAID` cancelada también tiene cobros que auditar. Documentado en Dev Notes con la cita de FR-16 ("independientemente de su Estatus").
- **Gap diferido de Historia 3.6 cerrado** (T11): `InstallmentRow` en `OrderDetailScreen.kt` ahora también verifica `uiState.status != SaleStatus.CANCELLED` antes de permitir tocar una parcialidad pendiente — cerrado explícitamente como parte del alcance de esta historia (ver `deferred-work.md`, entrada de 3.5/3.6).
- Migración de BD verificada: `MIGRATION_5_6` + versión 6 compilan y generan `android/app/schemas/.../6.json` correctamente (Room `exportSchema = true`); ningún test de escritura/lectura sobre `credit_balances` falló.

### Completion Notes List

- AC-1 (cancelar sin cobros): verificado — `CancelSaleUseCase` con `generateCredit` ignorado (sin cobros), venta y parcialidades pendientes pasan a `CANCELLED`, las ya pagadas quedan intactas.
- AC-2 (segundo dialog con cobros): verificado — `OrderDetailViewModel.onCancelOrderConfirm` muestra `showCreditChoiceDialog` cuando `paymentHistory` no está vacío; botones "Cancelar parcialidades"/"Generar Crédito a Favor" sin copy genérico.
- AC-3 (Generar Crédito a Favor): verificado — `ApplyCreditBalanceUseCase` crea `CreditBalanceEntity` con todos los campos exigidos (UUID cliente, `fk_client`, `fk_tenant`, `BigDecimal`, `origin = "cancellation"`, `fk_origin_sale`, `sync_status = "pending"`); banner de S-12 wireado con el dato real.
- AC-4/AC-5 (chip en S-07): verificado — chip visible solo con crédito disponible y sin fila `CREDITO_A_FAVOR` ya agregada; al aplicarlo se suma como fila más del constructor de métodos de pago existente (`PaymentMethodRow`), monto pre-llenado capado al total pendiente.
- AC-6 (cobertura total → Liquidado): verificado sin código nuevo en `onConfirmClick` — `isImmediateConfirmEnabled` ya exige `remaining == 0`, así que `CREDITO_A_FAVOR` cubriendo el total sigue el mismo camino que cualquier otro método. El descuento real ocurre en `SaleRepository.createSale` (consumo FIFO en sitio).
- AC-7 (cobros nunca se borran): verificado — ningún método (`cancelSale`, `registerPayment`, `createSale`) invoca un delete sobre `payments`; ni siquiera existe un método de borrado en `PaymentDao`.
- Pendiente: verificación manual en dispositivo/emulador (sin `adb` en este entorno); code review todavía no ejecutado.

### File List

**NUEVO:**
- `android/app/src/main/java/com/sumitrack/android/data/local/entities/CreditBalanceEntity.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/CreditBalanceDao.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/usecases/ApplyCreditBalanceUseCase.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/usecases/CancelSaleUseCase.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/usecases/CalculateAvailableCreditUseCase.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/FakeCreditBalanceDao.kt`
- `android/app/src/test/java/com/sumitrack/android/domain/usecases/ApplyCreditBalanceUseCaseTest.kt`
- `android/app/src/test/java/com/sumitrack/android/domain/usecases/CancelSaleUseCaseTest.kt`
- `android/app/src/test/java/com/sumitrack/android/domain/usecases/CalculateAvailableCreditUseCaseTest.kt`
- `android/app/schemas/com.sumitrack.android.data.local.SumitrackDatabase/6.json` (generado por Room)

**ELIMINADO (código review):**
- `android/app/src/main/java/com/sumitrack/android/domain/models/CreditBalance.kt` — confirmado sin ningún uso ni mapper en todo el codebase (`getAvailableCredit`/`CalculateAvailableCreditUseCase` operan directo sobre `BigDecimal`), eliminado como Review Finding.

**MODIFICADO:**
- `android/app/src/main/java/com/sumitrack/android/domain/models/PaymentMethodType.kt` — `CREDITO_A_FAVOR`
- `android/app/src/main/java/com/sumitrack/android/domain/models/InstallmentStatus.kt` — `CANCELLED`
- `android/app/src/main/java/com/sumitrack/android/data/local/Migrations.kt` — `MIGRATION_5_6`
- `android/app/src/main/java/com/sumitrack/android/data/local/SumitrackDatabase.kt` — entidad + DAO + versión 6
- `android/app/src/main/java/com/sumitrack/android/di/DatabaseModule.kt` — `provideCreditBalanceDao`
- `android/app/src/main/java/com/sumitrack/android/data/repositories/SaleRepository.kt` — `cancelSale`, `getAvailableCredit`, consumo de crédito en `createSale`
- `android/app/src/main/java/com/sumitrack/android/ui/components/PaymentMethodRow.kt` — label `CREDITO_A_FAVOR`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderDetailViewModel.kt` — `InstallmentUiStatus.CANCELLED`, cancelación real reemplaza placeholder
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderDetailScreen.kt` — segundo dialog, label `CREDITO_A_FAVOR` (excluido de "Registrar Cobro"), fix de gap diferido
- `android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientProfileViewModel.kt` — `creditBalance`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientProfileScreen.kt` — wiring del banner
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/PaymentViewModel.kt` — `availableCredit`, `onApplyCreditClick`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/PaymentScreen.kt` — chip de Crédito a Favor
- `android/app/src/test/java/com/sumitrack/android/data/repositories/SaleRepositoryTest.kt` — +8 casos
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/OrderDetailViewModelTest.kt` — +8 casos (cancelación real, reemplaza los 2 tests del placeholder)
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/ClientProfileViewModelTest.kt` — +2 casos
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/PaymentViewModelTest.kt` — +3 casos
- 13 archivos de test adicionales — nuevo parámetro `FakeCreditBalanceDao()` en el constructor de `SaleRepository`

## Change Log

- **2026-08-04** — Historia 3.7 implementada completa (Status: review) — cierra Epic 3
  - NEW: `CreditBalanceEntity`/`CreditBalanceDao` + `MIGRATION_5_6` (schema v5→v6) — primera entidad nueva desde Historia 1.3
  - NEW: `CancelSaleUseCase` — cancela venta + parcialidades pendientes (AC-1), dispara segundo dialog cuando hay cobros (AC-2) basándose en "¿tiene cobros?", no en el status literal
  - NEW: `ApplyCreditBalanceUseCase` — otorga Crédito a Favor al cancelar con Opción B (AC-3)
  - NEW: `CalculateAvailableCreditUseCase` — deliberadamente separado del cálculo de saldo (deuda vs. crédito son conceptos opuestos)
  - NEW: consumo de crédito FIFO en sitio dentro de `SaleRepository.createSale` cuando el método de pago es `CREDITO_A_FAVOR` (AC-5/AC-6) — decisión de modelado documentada en Dev Notes
  - UPDATE: `OrderDetailViewModel`/`OrderDetailScreen` (S-09) — cancelación real reemplaza el placeholder de Historia 3.5; segundo dialog Opción A/Opción B; fix del gap diferido de Historia 3.6 (parcialidad tocable con venta cancelada)
  - UPDATE: `ClientProfileScreen` (S-12) — banner de Crédito a Favor (construido en Historia 2.3) wireado con datos reales
  - UPDATE: `PaymentScreen`/`PaymentViewModel` (S-07) — chip de Crédito a Favor, se aplica como fila más del constructor de métodos de pago existente
  - NEW: tests — `ApplyCreditBalanceUseCaseTest` (1), `CancelSaleUseCaseTest` (6), `CalculateAvailableCreditUseCaseTest` (3), + casos en `SaleRepositoryTest`/`OrderDetailViewModelTest`/`ClientProfileViewModelTest`/`PaymentViewModelTest`
  - Build: 289 tests ✅ (0 fallos, +28 sobre Historia 3.6), `assembleDebug` BUILD SUCCESSFUL, schema v6 exportado correctamente
  - Pendiente: verificación manual en dispositivo/emulador (sin `adb` en este entorno); code review todavía no ejecutado

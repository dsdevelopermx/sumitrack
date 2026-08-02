---
baseline_commit: c8d7a29dea6f8910f1fabf055e8b956e26fb6a07
---

# Story 3.6: Registro de Cobros sobre Ventas y Parcialidades

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

Como proveedor,
quiero registrar los pagos que recibo contra una venta o parcialidad,
para que el saldo del cliente se actualice automáticamente sin cálculo manual.

## Acceptance Criteria

**AC-1 — Cobro de venta de pago único**

**Dado** que la orden es de pago único y el proveedor registra el cobro desde S-09
**Cuando** `RegisterPaymentUseCase` ejecuta con el monto completo
**Entonces** la venta pasa a estado `Liquidado`; el cobro queda en historial con fecha y monto; el saldo del cliente se actualiza de inmediato en SQLite

**AC-2 — Cobro de parcialidad**

**Dado** que la orden tiene parcialidades y el proveedor marca una como pagada
**Cuando** `RegisterPaymentUseCase` ejecuta
**Entonces** si quedan parcialidades pendientes: estado de la venta → `Parcial`; si todas están cubiertas: estado → `Liquidado`; el cobro queda en historial

**AC-3 — Persistencia y saldo**

**Dado** que se registra cualquier cobro
**Cuando** `RegisterPaymentUseCase` persiste el pago
**Entonces** el `PaymentEntity` se guarda en SQLite con UUID generado en cliente, `sync_status = pending`, fecha y monto en `BigDecimal`; el saldo en S-12 se recalcula automáticamente

### Fuera de alcance en esta historia (explícito)

- **Monto arbitrario/parcial contra una venta o parcialidad** — el texto literal de `epics.md` dice "con el monto completo" (AC-1) y "marca una [parcialidad] como pagada" (AC-2, binario). Ninguna de las dos ACs describe cobrar un monto distinto al total de la venta (pago único) o al monto fijo de la parcialidad. `RegisterPaymentUseCase` recibe el monto ya resuelto por el propio caso de uso (total de la venta o monto de la parcialidad elegida), no un monto que el usuario escriba libremente — la UI solo pide **qué** cobrar (venta completa o una parcialidad específica) y **cómo** (método de pago), nunca cuánto. Esto también evita reconstruir la UI de "Constructor de Métodos de Pago" (`PaymentMethodRow`, con campo de monto editable y soporte para dividir un cobro entre varios métodos) que Historia 3.3 construyó para **crear** una venta — reutilizarla aquí tal cual sugeriría (incorrectamente) que el monto es editable. Cobros divididos en múltiples métodos de pago sobre una venta/parcialidad ya existente no está en ningún AC.
- **Cancelación de venta y Crédito a Favor** — Historia 3.7 completa. Esta historia no toca `onCancelOrderConfirm`/`cancelPlaceholderMessage` (placeholder de Historia 3.5, sigue intacto) ni crea `CreditBalanceEntity`/`ApplyCreditBalanceUseCase`.
- **Pantalla dedicada de "Registrar Cobro"** — ni `epics.md` ni `EXPERIENCE.md` (`S-09` en `EXPERIENCE.md` línea 78, Flujo 2 líneas 313-326) describen una pantalla/ruta separada; el registro ocurre **inline en S-09** ("Roberto... Registra el cobro: efectivo, monto completo. Confirma." — tres pasos dentro de la misma pantalla de detalle, sin navegación). Se implementa como un `AlertDialog` simple lanzado desde S-09, no una nueva ruta en `NavGraph.kt`.
- **Repositorios `PaymentRepository`/`InstallmentRepository` dedicados** — `architecture.md` los lista en el árbol de archivos planeado (líneas 414-415), pero el patrón real del proyecto (`ValidateFolioUseCase` inyecta `SaleDao` directo; `SaleRepository` ya centraliza escrituras multi-DAO transaccionales vía `createSale`/`getSaleDetail`) hace innecesaria esa capa extra para el alcance de esta historia. Se añade `SaleRepository.registerPayment(...)` siguiendo el mismo patrón que `createSale`.
- **Recarga reactiva de S-09 tras el cobro** — no hay ningún `Flow` en `OrderDetailViewModel` (carga única en `init`); tras un cobro exitoso se re-ejecuta la misma lógica de carga (extraída a una función privada reutilizable), no se construye un mecanismo de observación reactiva nuevo.

## Tasks / Subtasks

### Android — Dominio: `RegisterPaymentUseCase` (testable en JVM puro)

- [x] **T1: `SaleRepository.registerPayment(...)`** (AC-1, AC-2, AC-3)
  - [x] Agregar a `data/repositories/SaleRepository.kt`, siguiendo el mismo patrón transaccional que `createSale` (un solo `transactionRunner.run { }` para las escrituras):
    ```kotlin
    suspend fun registerPayment(
        tenantId: String,
        saleId: String,
        installmentId: String?,
        method: PaymentMethodType,
        amount: BigDecimal,
        paidAt: Instant,
    ) {
        transactionRunner.run {
            paymentDao.upsertAll(
                listOf(
                    PaymentEntity(
                        id = UUID.randomUUID().toString(),
                        fkTenant = tenantId,
                        fkSale = saleId,
                        fkInstallment = installmentId,
                        method = method.name.lowercase(),
                        amount = amount,
                        paidAt = paidAt,
                        createdAt = paidAt,
                        updatedAt = paidAt,
                        syncStatus = "pending",
                    )
                )
            )
            val newSaleStatus = if (installmentId == null) {
                "paid"
            } else {
                val installments = installmentDao.getForSale(saleId, tenantId)
                val updated = installments.map { if (it.id == installmentId) it.copy(status = "paid", updatedAt = paidAt) else it }
                installmentDao.upsertAll(updated.filter { it.id == installmentId })
                if (updated.all { it.status == "paid" }) "paid" else "partial"
            }
            val sale = saleDao.getById(saleId, tenantId) ?: return@run
            saleDao.upsertAll(listOf(sale.copy(status = newSaleStatus, updatedAt = paidAt)))
        }
    }
    ```
    Confirmado en `data/local/TransactionRunner.kt`: `interface TransactionRunner { suspend fun <T> run(block: suspend () -> T): T }` — acepta cualquier número de llamadas `suspend` de distintos DAOs anidadas dentro del lambda (`createSale` ya lo hace con `saleDao`/`saleItemDao`/`paymentDao`/`installmentDao` en el mismo bloque); replicar exactamente esa estructura, sin necesidad de investigar la firma primero.
    Nota sobre `installmentDao.upsertAll(updated.filter { it.id == installmentId })`: solo se re-escribe la parcialidad que cambió (no las demás, que no cambiaron) — evita degradar `updated_at`/`sync_status` de filas no tocadas.
  - [x] **Guard de venta no encontrada:** si `saleDao.getById(saleId, tenantId)` devuelve `null` dentro de la transacción (venta eliminada/tenant incorrecto entre la validación del use case y la escritura — condición de carrera de baja probabilidad pero real dado que no hay lock), el pago ya se insertó pero el estado de venta no se actualiza. Evaluar si esto es aceptable (venta huérfana con pago sin reflejar en status) o si debe abortar toda la transacción — **decisión de implementación, no bloqueante para ningún AC** (la validación de `RegisterPaymentUseCase`, ver T2, ya verificó que la venta existe segundos antes; documentar la decisión tomada en Dev Agent Record).

- [x] **T2: `RegisterPaymentUseCase`** (AC-1, AC-2, AC-3)
  - [x] Crear `domain/usecases/RegisterPaymentUseCase.kt`, mismo patrón que `ValidateFolioUseCase`/`CalculateInstallmentsUseCase` (`@Inject constructor()`, `suspend operator fun invoke(...)`, validación con guards que devuelven `false`/`null` en vez de excepciones para casos esperables — evitar el patrón HALT/`require()` de `CalculateInstallmentsUseCase`, que es para errores de programador, no de negocio):
    ```kotlin
    class RegisterPaymentUseCase @Inject constructor(
        private val saleRepository: SaleRepository,
    ) {
        suspend operator fun invoke(
            tenantId: String,
            saleId: String,
            installmentId: String?,
            method: PaymentMethodType,
            now: Instant = Instant.now(),
        ): Boolean {
            val detail = saleRepository.getSaleDetail(saleId, tenantId) ?: return false
            if (detail.sale.status == SaleStatus.CANCELLED || detail.sale.status == SaleStatus.PAID) return false

            val amount = if (installmentId == null) {
                if (detail.installments.isNotEmpty()) return false // AC-1 es solo para ventas de pago único
                detail.sale.total
            } else {
                val installment = detail.installments.find { it.id == installmentId } ?: return false
                if (installment.status == InstallmentStatus.PAID) return false
                installment.amount
            }

            saleRepository.registerPayment(tenantId, saleId, installmentId, method, amount, now)
            return true
        }
    }
    ```
    **Por qué `Boolean` y no `Result<Unit>`/excepción:** todos los casos de `false` (venta no encontrada, ya cancelada/liquidada, parcialidad ya pagada, parcialidad no encontrada) son condiciones de negocio esperables ante el mismo tipo de condición de carrera que Historia 3.4 documentó para `TicketFileWriter` — no errores de programación. `GenerateTicketUseCase` (Historia 3.4) usa el mismo criterio (`TicketData?` nullable, no excepción) para su caso de fallo esperable.
    **Validación en el Use Case, no en el ViewModel** — regla explícita de `architecture.md` ("Validación: siempre en Use Cases... Nunca en ViewModels").

### Android — UI: registrar cobro desde S-09

- [x] **T3: Estado y acciones en `OrderDetailViewModel`** (AC-1, AC-2, AC-3)
  - [x] Extraer la lógica de `init { }` (carga de `SaleDetail` + cliente + `paymentCondition`) a una función privada `private suspend fun loadOrder(tenant: String)` reutilizable, invocada desde `init` y de nuevo tras un cobro exitoso — sin esto, S-09 mostraría datos obsoletos (parcialidad todavía "Pendiente", historial de cobros sin el nuevo registro) hasta salir y volver a entrar a la pantalla.
  - [x] Agregar a `OrderDetailUiState`:
    ```kotlin
    val showRegisterPaymentDialog: Boolean = false,
    val paymentTargetInstallmentId: String? = null, // null + showRegisterPaymentDialog=true → cobro de venta de pago único
    val isRegisteringPayment: Boolean = false,
    val registerPaymentError: String? = null,
    ```
    (`paymentTargetInstallmentId` solo tiene significado cuando `showRegisterPaymentDialog == true` — no usar su nulidad para decidir si el dialog está abierto, usar el booleano dedicado, mismo motivo por el que `OrderDetailScreen.kt` ya usa `showCancelDialog: Boolean` en vez de inferir del estado de otros campos.)
  - [x] Nuevos métodos:
    ```kotlin
    fun onRegisterPaymentClick(installmentId: String?) {
        _uiState.update { it.copy(showRegisterPaymentDialog = true, paymentTargetInstallmentId = installmentId, registerPaymentError = null) }
    }

    fun onRegisterPaymentDialogDismiss() {
        _uiState.update { it.copy(showRegisterPaymentDialog = false, paymentTargetInstallmentId = null) }
    }

    fun onConfirmRegisterPayment(method: PaymentMethodType) {
        if (_uiState.value.isRegisteringPayment) return
        _uiState.update { it.copy(isRegisteringPayment = true, registerPaymentError = null) }
        viewModelScope.launch {
            val tenant = tenantId.first()
            val success = tenant != null && registerPaymentUseCase(
                tenantId = tenant,
                saleId = saleId,
                installmentId = _uiState.value.paymentTargetInstallmentId,
                method = method,
            )
            if (!success) {
                _uiState.update { it.copy(isRegisteringPayment = false, registerPaymentError = "No se pudo registrar el cobro. Inténtalo de nuevo.") }
                return@launch
            }
            _uiState.update { it.copy(isRegisteringPayment = false, showRegisterPaymentDialog = false, paymentTargetInstallmentId = null) }
            tenant?.let { loadOrder(it) }
        }
    }
    ```
    Inyectar `RegisterPaymentUseCase` como nueva dependencia del constructor (junto a `GenerateTicketUseCase` existente).
  - [x] **Botón "Registrar Cobro"** en `OrderDetailScreen.kt`: visible cuando `uiState.paymentCondition is TicketPaymentCondition.SinglePayment` **y** `uiState.status != SaleStatus.PAID && != SaleStatus.CANCELLED` — llama `onRegisterPaymentClick(null)`. (En la práctica, para pago único el único estado alcanzable antes de cobrar es `PENDING`, ya que `createSale` marca `paid` inmediatamente cuando el modo es Inmediato — este botón cubre el caso, hoy inexistente en el código pero no imposible según el dominio, de una venta de pago único que quedó `PENDING`.)
  - [x] **Parcialidades pendientes/vencidas tocables:** en `InstallmentRow` (`OrderDetailScreen.kt`), agregar `Modifier.clickable { onRegisterPaymentClick(installment.id) }` **solo** cuando `installment.toUiStatus() != InstallmentUiStatus.PAID` (pendiente o vencida — ambas cobrables, "vencida" es solo "pendiente" + fecha pasada, ver Historia 3.5 Dev Notes). Filas ya pagadas no reaccionan al toque.
  - [x] **Dialog de confirmación** (`AlertDialog` simple, no reutiliza `PaymentMethodRow` — ver Fuera de alcance): selección de `PaymentMethodType` (Efectivo/Transferencia/Tarjeta, mismas 3 opciones y mismo texto que `paymentMethodLabel()` ya usa en `PaymentMethodRow.kt` — duplicar la función de etiquetado, no importar la privada de otro archivo) vía `RadioButton`/lista simple, monto mostrado como texto de solo lectura (total de la venta o monto de la parcialidad, ya conocido por el ViewModel — no requiere campo editable), botón "Confirmar" → `onConfirmRegisterPayment(selectedMethod)`, botón "Cancelar" → `onRegisterPaymentDialogDismiss()`. Mostrar `uiState.registerPaymentError` inline en el dialog si no es null (mismo criterio que `printError` en `TicketSheet`, Historia 3.4 — no un `Snackbar` que puede terminar oculto detrás de la ventana propia del dialog).
  - [x] Confirmación exitosa dispara feedback háptico leve (`HapticFeedbackType.Confirm` o equivalente vía `LocalHapticFeedback`) — convención de `EXPERIENCE.md` línea 235 ("Respuesta háptica leve de confirmación en acciones finalizadas (pago confirmado...)").

### Android — Corrección de gap descubierto: saldo no reflejaba cobros parciales

- [x] **T4: `CalculateClientBalanceUseCase` debe restar los cobros ya recibidos** (AC-3)
  - [x] **Gap real, no opcional:** `CalculateClientBalanceUseCase` (código actual, `domain/usecases/CalculateClientBalanceUseCase.kt`) suma `sale.total` de todas las ventas `pending`/`partial` sin restar los pagos ya recibidos contra esas ventas. Antes de esta historia era inalcanzable (ninguna venta con parcialidades tenía pagos parciales — permanecía en `pending` desde `createSale` hasta que 3.6 existiera). Ahora que `registerPayment` puede dejar una venta en `partial` con cobros parciales ya hechos, el AC-3 ("el saldo en S-12 se recalcula automáticamente") **no se cumple** si el cálculo sigue sumando el total completo de una venta parcialmente cobrada — el proveedor vería un saldo mayor al real inmediatamente después de registrar un cobro.
  - [x] Corregir sumando pagos por venta abierta:
    ```kotlin
    class CalculateClientBalanceUseCase @Inject constructor(
        private val saleRepository: SaleRepository,
    ) {
        suspend operator fun invoke(clientId: String, tenantId: String): BigDecimal =
            saleRepository.getOpenSalesForClient(clientId, tenantId).fold(BigDecimal.ZERO) { acc, sale ->
                val paid = saleRepository.getPaymentsForSale(sale.id, tenantId).fold(BigDecimal.ZERO) { sum, p -> sum + p.amount }
                acc + (sale.total - paid)
            }
    }
    ```
    Requiere exponer `SaleRepository.getPaymentsForSale(saleId, tenantId): List<Payment>` (delegado directo a `paymentDao.getForSale(...).map { it.toDomain() }` — el DAO ya existe, el repositorio solo necesita exponerlo fuera de `getSaleDetail`) **o** reutilizar `getSaleDetail(sale.id, tenantId)?.payments` por venta — evaluar cuál genera menos queries redundantes (N+1 ya existe hoy en el propio bucle de `getOpenSalesForClient`, no se empeora significativamente cualquiera de las dos opciones dado el volumen esperado de "órdenes abiertas por cliente" en este producto).
  - [x] Test nuevo en `CalculateClientBalanceUseCaseTest.kt` (si no existe, crear): venta `partial` con un pago parcial registrado → el saldo calculado excluye el monto ya cobrado, no solo el total de la venta.

### Review Findings

Revisión adversarial en 3 capas paralelas (Blind Hunter, Edge Case Hunter, Acceptance Auditor) sobre el diff de esta historia (9 archivos, ~933 líneas). 0 `decision-needed`, 7 `patch` (ya aplicados), 3 `defer`, 9 hallazgos descartados como ruido o patrones ya establecidos/aceptados en el proyecto.

- [x] [Review][Patch] `registerPayment` escribía `Payment`/parcialidad antes de validar que la venta existiera bajo ese tenant — un `return@run` normal no revierte lo ya escrito (Room solo hace rollback ante excepción), pudiendo dejar un `Payment` huérfano si `saleId`/`tenantId` no correspondían a ninguna venta [SaleRepository.kt:167] — corregido moviendo `saleDao.getById(...) ?: return@run` al inicio de la transacción, antes de cualquier escritura (también cierra el caso de tenant incorrecto).
- [x] [Review][Patch] El `Sale` actualizado por `registerPayment` no marcaba `syncStatus = "pending"` (inconsistente con `createSale`, que sí lo hace en toda fila nueva) [SaleRepository.kt:203] — corregido en el mismo `sale.copy(...)`.
- [x] [Review][Patch] `CalculateClientBalanceUseCase` podía restar de más si una venta llegara a estar sobrepagada (ej. por la condición de carrera diferida abajo), reduciendo el saldo agregado del cliente por debajo de lo real [CalculateClientBalanceUseCase.kt:15] — corregido con `.coerceAtLeast(BigDecimal.ZERO)` por venta.
- [x] [Review][Patch] `onConfirmRegisterPayment` no envolvía la llamada a `registerPaymentUseCase` en `runCatching` — una excepción (fallo de DB/transacción) dejaba `isRegisteringPayment` en `true` para siempre, con el dialog atascado sin forma de reintentar ni cancelar [OrderDetailViewModel.kt:158] — corregido.
- [x] [Review][Patch] El dialog de registro de cobro mostraba `$0.00` confirmable si `paymentTargetInstallmentId` no coincidía con ninguna parcialidad actual (estado obsoleto tras un reload concurrente) [OrderDetailScreen.kt:264] — corregido: si el monto no se puede resolver, el dialog se cierra en vez de mostrar un cobro fantasma.
- [x] [Review][Patch] Sin cobertura de test para aislamiento de tenant en `registerPayment`/`RegisterPaymentUseCase` — agregado un test en cada capa (repositorio y use case) verificando que un tenant incorrecto no escribe nada.
- [x] [Review][Patch] Las filas seleccionables de método de pago en el dialog no tenían `role = Role.RadioButton` — TalkBack no anunciaba correctamente el rol semántico [OrderDetailScreen.kt:403] — corregido.
- [x] [Review][Defer] Condición de carrera: dos llamadas concurrentes a `registerPayment` podrían ambas pasar la validación de "pendiente" antes de que cualquiera escriba, duplicando el cobro — deferred, baja probabilidad hoy (app de un solo dispositivo/sesión local, sin motor de sync/multi-dispositivo todavía); revisar cuando Epic 4 (sincronización) introduzca escritura concurrente real.
- [x] [Review][Defer] `InstallmentRow` es tocable aunque la venta padre esté `CANCELLED` (solo se valida el estado de la parcialidad, no el de la venta) — deferred, hoy inalcanzable porque ninguna venta puede llegar a `CANCELLED` todavía (la cancelación real es Historia 3.7); revisar al implementar esa historia.
- [x] [Review][Defer] `SaleRepository.registerPayment` no valida el monto recibido (confía en que `RegisterPaymentUseCase` ya lo resolvió correctamente) — deferred, consistente con el patrón ya establecido en `createSale` (tampoco valida sus parámetros); documentar si algún día se agrega un caller que no sea `RegisterPaymentUseCase`.

**Descartados (9):** strings de estado hardcodeados `"paid"`/`"partial"` en vez del enum `SaleStatus` (mismo patrón ya usado en `createSale`); patrón N+1 en `CalculateClientBalanceUseCase` (ya razonado y aceptado explícitamente en los Dev Notes de esta misma historia); parcialidades cobrables fuera de orden de vencimiento (ningún AC ni `epics.md` exige forzar el orden, decisión de producto fuera de alcance de esta revisión); feedback háptico optimista antes de confirmar éxito (ya documentado como simplificación intencional en el Dev Agent Record, confirmado no bloqueante por el Acceptance Auditor); sin test para un hipotético tercer valor de `InstallmentStatus` (el enum solo tiene `PENDING`/`PAID` hoy); orden de imports "roto" (el proyecto no tiene linter de imports configurado); `installmentId` sin coincidencia produciendo un "todas pagadas" vacuo (inalcanzable — ningún DAO del proyecto expone borrar una parcialidad, por lo que la lista nunca puede quedar sin la fila que el use case validó segundos antes).

### Por qué el monto nunca es editable por el usuario (ver también Fuera de alcance)

Las dos ACs describen escenarios binarios ("con el monto completo", "marca una... como pagada") — no hay ningún AC de esta historia que describa un cobro parcial arbitrario contra una venta o parcialidad. `RegisterPaymentUseCase` resuelve el monto internamente (`detail.sale.total` o `installment.amount`) precisamente para que sea estructuralmente imposible pasar un monto incorrecto — la UI solo puede elegir **qué** cobrar y **con qué método**, nunca cuánto. Esto es una simplificación real del alcance frente a lo que `PaymentMethodRow`/S-07 permiten al **crear** una venta (montos editables, múltiples métodos), y es intencional.

### Por qué se corrige `CalculateClientBalanceUseCase` en esta historia y no se difiere

El deferred de Historia 2.3 sobre este mismo archivo decía explícitamente: *"Sin piso en cero ni manejo de montos negativos... relevante cuando Epic 3 construya el flujo de creación de ventas con su propia validación."* — anticipaba revisar este archivo cuando Epic 3 mutara ventas, pero no anticipó específicamente el gap de "no resta pagos parciales" porque hasta esta historia ninguna venta parcial tenía pagos que restar. Diferirlo dejaría el AC-3 de esta misma historia técnicamente incumplido (el saldo mostrado sería incorrecto inmediatamente después de la funcionalidad que esta historia construye) — se corrige aquí, no se agrega a `deferred-work.md`.

### Gaps heredados que esta historia NO resuelve (quedan diferidos)

- El deferred de Historia 2.3 sobre `client.balance`/`openSales` como "dos lecturas no transaccionales de `sales`" (condición de carrera si el estado de una venta cambia entre ambas llamadas) se vuelve alcanzable por primera vez con esta historia (ya existe mutación real de `sales` fuera de `createSale`) — no se resuelve aquí, sigue en `deferred-work.md`, solo se anota que ya es alcanzable.
- El deferred de Historia 3.1 sobre `status IN ('pending','partial')` sensible a mayúsculas/minúsculas — `registerPayment` (T1) ya escribe `"paid"`/`"partial"` en minúsculas, consistente con `SaleStatus.fromString`, así que esta historia no introduce el problema pero tampoco lo resuelve estructuralmente (sigue dependiendo de la convención, no de un tipo forzado por Room).
- Ningún test ejercita el rollback transaccional real de `SaleRepository` (deferred desde Historia 3.3) — `registerPayment` hereda el mismo gap; `FakeTransactionRunner` sigue sin simular fallos a mitad de transacción.

### Testing

- **100% JVM puro, sin Robolectric, sin tests de Compose UI** — mismo criterio aceptado en todas las historias anteriores.
- `RegisterPaymentUseCaseTest.kt` (nuevo): pago único exitoso → venta pasa a `PAID`, `Payment` con `fkInstallment = null`; parcialidad exitosa con parcialidades restantes → venta `PARTIAL`; última parcialidad pendiente exitosa → venta `PAID`; venta no encontrada → `false`; venta ya `CANCELLED`/`PAID` → `false`; parcialidad no encontrada → `false`; parcialidad ya `PAID` → `false`; pago único contra una venta que sí tiene parcialidades (`installmentId = null` pero `detail.installments` no vacío) → `false`.
- `SaleRepositoryTest.kt` (+casos): `registerPayment` persiste el `PaymentEntity` correctamente (UUID generado, `sync_status = pending`, `fkInstallment` correcto); actualiza el estado de la venta correctamente en ambos escenarios (pago único, última parcialidad); no reescribe parcialidades no tocadas.
- `OrderDetailViewModelTest.kt` (+casos): `onRegisterPaymentClick`/`onConfirmRegisterPayment` — éxito recarga el detalle (parcialidad pagada, historial actualizado, dialog cerrado); fallo mantiene el dialog abierto con `registerPaymentError` seteado; doble-tap en `onConfirmRegisterPayment` protegido por `isRegisteringPayment` (mismo patrón `isSharing`/`isPrinting` ya establecido en esta clase desde Historia 3.5).
- `CalculateClientBalanceUseCaseTest.kt` (nuevo o +casos si ya existe uno mínimo): ver T4.
- Reutilizar `FakeInstallmentDao`/`FakePaymentDao`/`FakeSaleDao`/`FakeTransactionRunner` ya existentes en `ui/screens/orders/` y `ui/screens/clients/`/`ui/screens/products/` — no crear nuevos fakes, estos ya soportan `upsertAll`/`getForSale` con la semántica correcta (confirmado leyendo su código: `upsertAll` hace merge por `id`, igual que `@Upsert` de Room).

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Historia 3.6] — ACs verbatim.
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/EXPERIENCE.md#S-09] — spec de pantalla, sin afordance dedicada de cobro descrita explícitamente (se infiere del Flujo 2).
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/EXPERIENCE.md#Flujo 2] — narrativa "Roberto cobra una parcialidad", único lugar que confirma el flujo inline sin pantalla nueva.
- [Source: _bmad-output/planning-artifacts/architecture/architecture.md#Reglas Obligatorias] — validación en Use Cases, `BigDecimal`/`NUMERIC(18,6)`, UUID cliente-generado, `sync_status`.
- [Source: android/.../data/repositories/SaleRepository.kt] — patrón `createSale`/`getSaleDetail`, comentario líneas 60-65 que diferaba explícitamente esta historia.
- [Source: android/.../domain/usecases/CalculateClientBalanceUseCase.kt] — gap de saldo, corregido en T4.
- [Source: _bmad-output/implementation-artifacts/deferred-work.md#2-3] — deferred que anticipaba esta corrección.
- [Source: _bmad-output/implementation-artifacts/3-5-detalle-de-orden-e-historial-de-cobros.md] — `OrderDetailViewModel`/`OrderDetailScreen` actuales, punto de integración.

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- Confirmada la firma de `TransactionRunner.run` (`suspend fun <T> run(block: suspend () -> T): T`) antes de implementar T1 — soporta llamadas anidadas a múltiples DAOs `suspend` dentro del mismo bloque, igual que `createSale` ya lo hace.
- **Decisión T1 (guard de venta no encontrada):** si `saleDao.getById(saleId, tenantId)` devuelve `null` dentro de la transacción de `registerPayment` (condición de carrera de baja probabilidad tras la validación de `RegisterPaymentUseCase`), la transacción hace `return@run` sin lanzar — el `PaymentEntity` ya insertado en ese mismo bloque queda persistido pero el estado de la venta no se actualiza. Se documenta como comportamiento aceptado (no bloqueante para ningún AC) en vez de abortar toda la transacción, dado que revertir el pago ya escrito requeriría lógica adicional no exigida por ningún AC de esta historia; queda anotado como gap conocido en Dev Notes/deferred-work.md si se detecta en producción.
- Verificado con `grep` que `createImmediateSale`/`PaymentConfig.Immediate` siempre deja la venta en estado `paid` de inmediato (Historia 3.3) — por eso los tests de "Registrar Cobro" sobre venta de pago único siembran un `SaleEntity` `pending` directamente en el DAO fake en vez de usar `createSale`, igual que se hizo en Historia 3.3/3.4 para escenarios no alcanzables por el flujo normal de creación.

### Completion Notes List

- AC-1 (cobro de venta de pago único): verificado — `RegisterPaymentUseCase` con `installmentId = null` resuelve el monto como `sale.total`, `registerPayment` persiste el `Payment` y marca la venta `paid`. Botón "Registrar Cobro" en `OrderDetailScreen` visible solo cuando la condición de pago es `SinglePayment` y el estado no es `PAID`/`CANCELLED`.
- AC-2 (cobro de parcialidad): verificado — marcar una parcialidad paga deja la venta en `partial` si quedan pendientes, o `paid` si era la última. `InstallmentRow` es tocable solo para parcialidades no pagadas (pendiente o vencida).
- AC-3 (persistencia y saldo): verificado — `PaymentEntity` con UUID cliente-generado y `sync_status = pending`; gap real encontrado y corregido en `CalculateClientBalanceUseCase` (restaba solo `sale.total`, ahora resta también los pagos ya recibidos) para que el saldo de S-12 refleje cobros parciales inmediatamente, cumpliendo el AC de forma literal.
- Monto nunca editable por el usuario (ver Fuera de alcance) — el diálogo de registro solo pide método de pago; el monto se muestra de solo lectura, resuelto enteramente por `RegisterPaymentUseCase`.
- Feedback háptico (`HapticFeedbackType.Confirm`) disparado al tocar "Confirmar" en el diálogo — interpretación optimista de "confirmación exitosa" (EXPERIENCE.md línea 235) para evitar lógica frágil de diffing de estado async; no bloqueante para ningún AC.
- Pendiente: verificación manual en dispositivo/emulador (sin `adb` en este entorno); code review todavía no ejecutado.

### File List

**NUEVO:**
- `android/app/src/main/java/com/sumitrack/android/domain/usecases/RegisterPaymentUseCase.kt`
- `android/app/src/test/java/com/sumitrack/android/domain/usecases/RegisterPaymentUseCaseTest.kt`

**MODIFICADO:**
- `android/app/src/main/java/com/sumitrack/android/data/repositories/SaleRepository.kt` — `registerPayment(...)`, `getPaymentsForSale(...)`
- `android/app/src/main/java/com/sumitrack/android/domain/usecases/CalculateClientBalanceUseCase.kt` — resta pagos ya recibidos
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderDetailViewModel.kt` — `loadOrder` extraído, estado y acciones de registro de cobro
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderDetailScreen.kt` — botón "Registrar Cobro", `InstallmentRow` tocable, `RegisterPaymentDialog`
- `android/app/src/test/java/com/sumitrack/android/data/repositories/SaleRepositoryTest.kt` — +4 casos `registerPayment`
- `android/app/src/test/java/com/sumitrack/android/domain/usecases/CalculateClientBalanceUseCaseTest.kt` — +1 caso de resta de pagos
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/OrderDetailViewModelTest.kt` — +7 casos de registro de cobro

## Change Log

- **2026-08-02** — Historia 3.6 implementada completa (Status: review)
  - NEW: `RegisterPaymentUseCase` — resuelve el monto internamente (total de venta o monto de parcialidad, nunca editable por el usuario), valida venta/parcialidad antes de delegar a `SaleRepository.registerPayment`
  - NEW: `SaleRepository.registerPayment` — persiste el `Payment`, marca la parcialidad pagada (si aplica) y recalcula el estado de la venta (`partial`/`paid`), mismo patrón transaccional que `createSale`
  - UPDATE: `OrderDetailViewModel`/`OrderDetailScreen` (S-09) — botón "Registrar Cobro" para pago único, parcialidades pendientes/vencidas tocables, diálogo de confirmación con selección de método de pago (monto siempre de solo lectura)
  - FIX: `CalculateClientBalanceUseCase` restaba solo el total de la venta, no los cobros ya recibidos — gap real que dejaba el AC-3 incumplido en cuanto existiera el primer cobro parcial; corregido en esta misma historia (no diferido)
  - NEW: tests — `RegisterPaymentUseCaseTest` (9), `SaleRepositoryTest` (+4), `CalculateClientBalanceUseCaseTest` (+1), `OrderDetailViewModelTest` (+7)
  - Build: 259 tests ✅ (0 fallos, +21 sobre Historia 3.5), `BUILD SUCCESSFUL` (`assembleDebug` + `testDebugUnitTest`)
  - Pendiente: verificación manual en dispositivo/emulador (sin `adb` en este entorno); code review todavía no ejecutado

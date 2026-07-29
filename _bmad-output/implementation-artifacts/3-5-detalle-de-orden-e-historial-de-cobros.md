---
baseline_commit: 9da6695ca11e3537bf8cd7a2bb6d774a94a4ea13
---

# Story 3.5: Detalle de Orden e Historial de Cobros

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

Como proveedor,
quiero ver todos los detalles de una orden y su historial de pagos registrados,
para que tenga un registro completo que pueda consultar o mostrarle al cliente.

## Acceptance Criteria

**AC-1 — Contenido del detalle (S-09)**

**Dado** que el proveedor toca una `OrderCard` en S-02
**Cuando** S-09 se muestra
**Entonces** muestra: folio, fecha, nombre del cliente, `StatusBadge`, listado de ítems con cantidades y precios, condición de pago (fechas de parcialidades o fecha de pago único), historial de cobros con fecha y monto de cada pago registrado

**AC-2 — Estado de parcialidades**

**Dado** que la orden tiene parcialidades
**Cuando** S-09 se muestra
**Entonces** cada parcialidad muestra fecha, monto y estado (pagada/pendiente/vencida)

**AC-3 — Compartir Ticket**

**Dado** que el proveedor toca "Compartir Ticket"
**Cuando** se registra la acción
**Entonces** `GenerateTicketUseCase` genera el PNG en memoria y dispara el Android share intent

**AC-4 — Cancelar Orden (solo el disparador)**

**Dado** que el proveedor toca "Cancelar Orden" (para ventas que no estén ya canceladas)
**Cuando** se registra la acción
**Entonces** aparece `AlertDialog` de confirmación: "¿Cancelar esta orden? Esto no se puede deshacer." con botones "Sí, cancelar orden" / "No, mantenerla"

### Fuera de alcance en esta historia (explícito)

- **La cancelación real de la venta** — AC-4 (arriba, texto literal de `epics.md`) termina exactamente en "aparece `AlertDialog` de confirmación"; no dice qué pasa al tocar "Sí, cancelar orden". Historia 3.7 ("Crédito a Favor y Cancelación de Venta con Cobros") es la que define ese comportamiento completo, con dos casos: sin cobros (venta pasa directo a `Cancelado`) y con cobros/estado Parcial (segundo dialog con Opción A "Cancelar parcialidades" / Opción B "Generar Crédito a Favor", `ApplyCreditBalanceUseCase`, `CreditBalanceEntity`). Ningún `CancelSaleUseCase` está anticipado en `architecture.md` ni existe hoy en el código — inventar aquí una versión parcial (p. ej. solo el caso "sin cobros") dejaría a Historia 3.7 con que decidir si reescribe o extiende esa lógica a medias, el mismo tipo de deuda que el proyecto ha evitado consistentemente (ver Historia 3.2→3.3→3.4, cada una entrega solo hasta el límite exacto de su AC con un placeholder explícito para lo siguiente). Al tocar "Sí, cancelar orden" en esta historia: Snackbar "Cancelación de orden — disponible próximamente" y el dialog se cierra — mismo patrón exacto que "Revisar Orden" (3.2→3.3), "Ticket" (3.3→3.4) y "Detalle de orden" (3.1→3.5, este mismo placeholder que esta historia reemplaza) usaron antes.
- **Registro de cobros desde S-09** ("marcar una parcialidad como pagada", botón/flujo de `RegisterPaymentUseCase`) — Historia 3.6, no existe todavía. Esta historia solo **muestra** el historial de cobros ya existentes (que hoy, para ventas de Pago inmediato, son los `Payment` creados en `SaleRepository.createSale` desde Historia 3.3; para ventas con Parcialidades, la lista estará vacía hasta que 3.6 exista — comportamiento correcto dado el estado actual del sistema, no un bug).
- **Impresión vía Bluetooth desde S-09** — el AC-3 de esta historia solo pide "Compartir Ticket"; no se exige (ni se prohíbe) el botón de imprimir. Esta historia reutiliza `TicketSheet` tal cual (sin modificarlo — ver Dev Notes), que siempre muestra ambos botones ("Imprimir vía Bluetooth" y "Compartir"); no reimprimir un ticket ya generado es un caso de uso razonable en un historial, así que no se oculta el botón. Documentado como interpretación, no como AC explícito.
- **`CreditBalanceEntity`/chip de Crédito a Favor en S-07** — Historia 3.7, no construir aquí.
- **Estado "vencida" a nivel de `Sale`** (badge `SaleUiStatus.OVERDUE`/"Atraso" del historial S-02) — sigue sin alcanzarse (código muerto desde antes de esta historia, `OrderCard.toUiStatus()` nunca lo devuelve); esta historia NO lo activa, porque el AC-2 pide "vencida" a nivel de **parcialidad individual**, un concepto distinto (una parcialidad vencida no implica necesariamente que toda la venta deba mostrarse como "Atraso" en S-02 — esa es una decisión de producto que ningún AC de esta historia toma). Ver Dev Notes.

## Tasks / Subtasks

### Android — Derivación de estado de parcialidad (testable en JVM puro)

- [x] **T1: `InstallmentUiStatus` + derivación** (AC-2)
  - [x] **Por qué "vencida" no es un `InstallmentStatus` persistido:** `InstallmentStatus` (dominio, Historia 3.3) solo tiene `PENDING`/`PAID` — es correcto que así sea, porque "vencida" no es un hecho que se persista (no hay un job/cron que la marque), es una comparación de fecha en el momento de mostrarla: pendiente + `dueDate` ya pasado = vencida. Persistirlo requeriría reevaluar y reescribir filas constantemente sin ningún beneficio; derivarlo al leer es más simple y siempre correcto.
  - [x] Crear en `ui/screens/orders/OrderDetailViewModel.kt` (ver T2):
    ```kotlin
    enum class InstallmentUiStatus { PENDING, PAID, OVERDUE }

    fun Installment.toUiStatus(now: Instant = Instant.now()): InstallmentUiStatus = when {
        status == InstallmentStatus.PAID -> InstallmentUiStatus.PAID
        dueDate.isBefore(now) -> InstallmentUiStatus.OVERDUE
        else -> InstallmentUiStatus.PENDING
    }
    ```
    `now` con default `Instant.now()` pero parametrizable — permite tests deterministas sin mockear el reloj del sistema (mismo criterio que `CalculateInstallmentsUseCase` ya usa `startDate: ZonedDateTime = ZonedDateTime.now()` desde Historia 3.3).
    **No reutiliza `SaleUiStatus`/`StatusBadge`** (el badge de estado de *venta*, con 4 valores `PAID/PARTIAL/OVERDUE/CANCELLED`) — es un concepto distinto a nivel de *parcialidad individual*; forzar la reutilización mezclaría dos taxonomías no relacionadas. Se renderiza con un `Text`/chip simple reutilizando los tokens de color ya existentes `StatusPending`/`StatusPaid`/`StatusOverdue` de `ui/theme/` (mismos colores, sin el componente `StatusBadge` completo).

### Android — Pantalla de detalle

- [x] **T2: `OrderDetailViewModel`** (AC-1, AC-2, AC-3, AC-4)
  - [x] Crear `ui/screens/orders/OrderDetailViewModel.kt`:
    ```kotlin
    data class OrderDetailUiState(
        val isLoading: Boolean = true,
        val notFound: Boolean = false,
        val folio: String = "",
        val createdAt: Instant = Instant.EPOCH,
        val clientName: String = "",
        val status: SaleStatus = SaleStatus.PENDING,
        val items: List<SaleItem> = emptyList(),
        val subtotal: BigDecimal = BigDecimal.ZERO,
        val tax: BigDecimal = BigDecimal.ZERO,
        val total: BigDecimal = BigDecimal.ZERO,
        val paymentCondition: TicketPaymentCondition? = null,
        val installments: List<Installment> = emptyList(),
        val paymentHistory: List<Payment> = emptyList(),
        val ticketData: TicketData? = null,
        val isPrinting: Boolean = false,
        val isSharing: Boolean = false,
        val printError: String? = null,
        val cancelPlaceholderMessage: String? = null,
    )

    @HiltViewModel
    class OrderDetailViewModel @Inject constructor(
        savedStateHandle: SavedStateHandle,
        private val saleRepository: SaleRepository,
        private val clientRepository: ClientRepository,
        private val generateTicketUseCase: GenerateTicketUseCase,
        private val bluetoothTicketPrinter: BluetoothTicketPrinter,
        private val ticketFileWriter: TicketFileWriter,
        @TenantId private val tenantId: Flow<String?>,
    ) : ViewModel() {

        private val saleId: String = checkNotNull(savedStateHandle["saleId"])

        private val _uiState = MutableStateFlow(OrderDetailUiState())
        val uiState: StateFlow<OrderDetailUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val tenant = tenantId.first()
                if (tenant.isNullOrBlank()) {
                    _uiState.update { it.copy(isLoading = false, notFound = true) }
                    return@launch
                }
                val detail = runCatching { saleRepository.getSaleDetail(saleId, tenant) }.getOrNull()
                if (detail == null) {
                    _uiState.update { it.copy(isLoading = false, notFound = true) }
                    return@launch
                }
                val client = runCatching { clientRepository.getClientById(detail.sale.fkClient) }.getOrNull()
                val paymentCondition = if (detail.installments.isEmpty()) {
                    TicketPaymentCondition.SinglePayment(detail.payments.firstOrNull()?.paidAt ?: detail.sale.createdAt)
                } else {
                    TicketPaymentCondition.InstallmentPlan(detail.installments.sortedBy { it.dueDate })
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        folio = detail.sale.folio,
                        createdAt = detail.sale.createdAt,
                        clientName = client?.name.orEmpty(),
                        status = detail.sale.status,
                        items = detail.items,
                        subtotal = detail.sale.subtotal,
                        tax = detail.sale.tax,
                        total = detail.sale.total,
                        paymentCondition = paymentCondition,
                        installments = detail.installments.sortedBy { it.dueDate },
                        paymentHistory = detail.payments.sortedByDescending { it.paidAt },
                    )
                }
            }
        }

        fun onShareTicketClick() {
            viewModelScope.launch {
                val tenant = tenantId.first() ?: return@launch
                if (_uiState.value.ticketData == null) {
                    val ticket = runCatching { generateTicketUseCase(saleId, tenant) }.getOrNull() ?: return@launch
                    _uiState.update { it.copy(ticketData = ticket) }
                }
                onShareClick()
            }
        }

        // onPrintClick/onShareClick/onBluetoothPermissionDenied/onTicketDismiss: misma lógica que
        // PaymentViewModel (Historia 3.4) — duplicada deliberadamente, no extraída a un
        // delegate/use case compartido porque ningún AC de esta historia lo exige y evita tocar
        // PaymentViewModel.kt, ya estable y cubierto por su propia suite de tests (ver Dev Notes).
        fun onPrintClick() { /* idéntico a PaymentViewModel.onPrintClick, ver Historia 3.4 */ }
        fun onBluetoothPermissionDenied() { /* idéntico a PaymentViewModel.onBluetoothPermissionDenied */ }
        private fun onShareClick() { /* idéntico a PaymentViewModel.onShareClick, ver Historia 3.4 */ }
        fun onTicketDismiss() { _uiState.update { it.copy(ticketData = null, printError = null) } }

        fun onCancelOrderConfirm() {
            _uiState.update { it.copy(cancelPlaceholderMessage = "Cancelación de orden — disponible próximamente") }
        }

        fun onCancelPlaceholderShown() {
            _uiState.update { it.copy(cancelPlaceholderMessage = null) }
        }
    }
    ```
    `notFound` cubre tanto tenant nulo como venta inexistente/de otro tenant (`getSaleDetail` ya tenant-scoped desde Historia 3.4) — un solo estado de error simple, consistente con cómo `OrderSummaryViewModel`/`PaymentViewModel` (Historia 3.3) tratan "no se pudo determinar tu negocio" como un caso terminal de carga, no como una excepción.
    `onShareTicketClick()` es el punto de entrada real desde la UI: genera el `TicketData` primero (si no existe ya en el estado — evita regenerar el PNG en cada tap) y encadena a `onShareClick()`. `onPrintClick`/`onBluetoothPermissionDenied`/`onShareClick`/`onTicketDismiss` implementan exactamente la misma lógica ya escrita y probada en `PaymentViewModel` (Historia 3.4: `isPrinting`/`isSharing`/`printError`, guardas de doble-tap, mensaje exacto de AC-3 de la Historia 3.4 en fallo de impresión, `TicketFileWriter`/`BluetoothTicketPrinter` inyectados igual). Copiar el cuerpo completo de esos 4 métodos tal cual están en `PaymentViewModel.kt` (líneas ~296-349 al momento de esta historia).

- [x] **T3: `OrderDetailScreen`** (AC-1, AC-2, AC-3, AC-4)
  - [x] Crear `ui/screens/orders/OrderDetailScreen.kt`:
    - `Scaffold` + `TopAppBar` (título = folio, `navigationIcon` con `onBackClick`).
    - Si `uiState.notFound`: `EmptyState` (reutilizar componente ya existente) con mensaje "No pudimos cargar esta orden." y botón para regresar.
    - Encabezado: folio, fecha (`formatDate`, mismo patrón `DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-MX"))` que `OrderCard.kt`), nombre del cliente, `StatusBadge` (reutilizar tal cual, con el mismo mapeo `SaleStatus.toUiStatus()` ya usado en `OrderCard.kt` — duplicar la función privada, mismo criterio de duplicación deliberada ya validado dos veces en el proyecto para `formatAmount`/formateadores).
    - Lista de ítems: nombre (+variante si aplica), cantidad, precio unitario, subtotal de línea — mismo formato visual que `OrderSummaryItemRow` de Historia 3.3 (`OrderSummaryScreen.kt`), duplicar el patrón (no importar directamente, es `private` en ese archivo).
    - Sección de totales: subtotal/impuestos/total (mismo patrón `SummaryLine` de `OrderSummaryScreen.kt`, duplicado).
    - Condición de pago: si `paymentCondition is SinglePayment` → "Pago de contado" + fecha; si `is InstallmentPlan` → lista de parcialidades, cada una con fecha (`d MMM yyyy`), monto, y un chip de estado usando `installment.toUiStatus()` → "Pagada"/"Pendiente"/"Vencida" con los colores `StatusPaid`/`StatusPending`/`StatusOverdue` de `ui/theme/`.
    - Historial de cobros: lista de `uiState.paymentHistory`, cada fila con fecha (`paidAt`) y monto; si está vacía, un mensaje simple "Sin cobros registrados todavía" (no es un error — ver "Fuera de alcance").
    - Botón "Compartir Ticket" → `viewModel::onShareTicketClick`.
    - Botón "Cancelar Orden" — **oculto si `uiState.status == SaleStatus.CANCELLED`** (AC-4: "para ventas que no estén ya canceladas"); al tocarlo abre un `AlertDialog` local (`remember { mutableStateOf(false) }`, mismo patrón `showAbandonDialog` de `ItemListScreen.kt`) con el texto EXACTO de AC-4: título/mensaje "¿Cancelar esta orden? Esto no se puede deshacer.", botón "Sí, cancelar orden" → `viewModel::onCancelOrderConfirm` (cierra el dialog local Y dispara el Snackbar placeholder), botón "No, mantenerla" → solo cierra el dialog local.
    - `LaunchedEffect(uiState.cancelPlaceholderMessage)` → muestra el Snackbar y llama `viewModel::onCancelPlaceholderShown` para no repetirlo en recomposición.
    - Reutiliza `TicketSheet` (Historia 3.4) sin modificar — mismo wiring que `PaymentScreen.kt`: `LaunchedEffect` para `shareEvent`/permiso, `if (uiState.ticketData != null) TicketSheet(...)`.

- [x] **T4: Ruta y navegación** (AC-1)
  - [x] Agregar a `ui/navigation/Routes.kt`:
    ```kotlin
    object OrderDetail : Routes("order_detail/{saleId}") {
        fun createRoute(saleId: String): String = "order_detail/$saleId"
    }
    ```
  - [x] Actualizar `ui/screens/orders/OrderListScreen.kt` — agregar parámetro `onOrderClick: (saleId: String) -> Unit = {}`; el `onClick` de cada `OrderCard` (hoy Snackbar placeholder "Detalle de orden — disponible próximamente", de Historia 3.1) pasa a `onOrderClick(order.id)`.
  - [x] Actualizar `ui/navigation/NavGraph.kt` — composable de `Routes.Orders`: pasar `onOrderClick = { saleId -> navController.navigate(Routes.OrderDetail.createRoute(saleId)) { launchSingleTop = true } }`; agregar nuevo composable para `Routes.OrderDetail.route` (mismo patrón que `Routes.ClientProfile`: un solo argumento de ruta, `onBackClick = { navController.popBackStack() }`).

### Review Findings

Revisión adversarial en 3 capas paralelas (Blind Hunter, Edge Case Hunter, Acceptance Auditor) sobre el diff de esta historia (7 archivos, ~844/-15 líneas). 0 `decision-needed`, 3 `patch` (ya aplicados), 4 `defer`, 11 hallazgos descartados como ruido o patrones ya establecidos/aceptados en el proyecto.

- [x] [Review][Patch] Condición de carrera en `onShareTicketClick`: doble tap antes de resolver `tenantId.first()` generaba `generateTicketUseCase`/`shareEvent` duplicados [OrderDetailViewModel.kt:125] — corregido con guard síncrono de `isSharing` antes del `launch`.
- [x] [Review][Patch] `onShareTicketClick` no exponía feedback al usuario cuando `generateTicketUseCase` fallaba (silencioso, sin `printError`) [OrderDetailViewModel.kt:129] — corregido junto con el punto anterior.
- [x] [Review][Patch] `onPrintClick`/`onBluetoothPermissionDenied` (lógica duplicada de `PaymentViewModel`) se enviaron sin tests propios en `OrderDetailViewModelTest.kt` — agregados 4 tests nuevos (éxito/fallo de impresión, permiso denegado, fallo de generación de ticket).
- [x] [Review][Defer] `tenantId.first()` sin `runCatching` en `init` y `onShareTicketClick` [OrderDetailViewModel.kt:90,127] — deferred, pre-existing (mismo patrón inconsistente ya presente en `PaymentViewModel.kt:121`, historia 3.4).
- [x] [Review][Defer] `runCatching { }.getOrNull()` sin logging en toda la clase (sale detail, client, ticket) — deferred, pre-existing (gap transversal ya rastreado desde Historia 2.x).
- [x] [Review][Defer] `clientRepository.getClientById(detail.sale.fkClient)` sin scoping de tenant [OrderDetailViewModel.kt:100] — deferred, pre-existing (mismo gap de `ClientRepository`/`ClientDao` ya rastreado desde Historia 2.1/2.2, heredado vía `GenerateTicketUseCase`).
- [x] [Review][Defer] El test "generates the ticket once and reuses it" no verifica que `generateTicketUseCase` se invoque exactamente una vez (solo compara el `TicketData` resultante) — deferred, mejora de calidad de test, no bloqueante.

**Descartados (11):** folio ausente del bloque de encabezado del cuerpo (ya visible en el `TopAppBar`, AC-1 satisfecho); `EmptyState` del estado `notFound` sin botón propio de regreso (el `TopAppBar` con `onBackClick` ya es visible en ese estado); `runCatching` genérico alrededor de `startActivity` que rotula todo fallo como "no hay app instalada" (idéntico al patrón ya revisado en `PaymentScreen.kt`, Historia 3.4); `Channel`/`LaunchedEffect(Unit)` para `shareEvent` sin `repeatOnLifecycle` (idéntico al patrón ya revisado en `PaymentScreen.kt`); botón "Compartir Ticket" visible para órdenes `CANCELLED` (sin violación de AC, reimprimir el ticket de una venta cancelada es razonable para historial); formato de moneda artesanal sin separador de miles (patrón establecido en 5+ pantallas del proyecto); strings en español hardcodeados sin recursos (convención de todo el proyecto, cero `stringResource` en ninguna pantalla); `saleId` interpolado sin encoding en la ruta de navegación (mismo patrón que todas las demás rutas con ID, IDs son UUIDs internos); `checkNotNull(savedStateHandle["saleId"])` con excepción genérica (patrón establecido en todos los ViewModels con argumento de navegación obligatorio); sin tests de Compose UI para `OrderDetailScreen.kt` (limitación de proyecto documentada y aceptada explícitamente en cada historia — sin Robolectric); fecha de `SinglePayment` tomada con `firstOrNull()` sobre pagos divididos (falso positivo verificado — todos los pagos de una venta `Immediate` comparten el mismo `paidAt`, el orden no afecta el resultado).

## Dev Notes

### Por qué "Cancelar Orden" termina en un placeholder, no en una cancelación real

`epics.md` cita el AC-4 de esta historia textualmente hasta "aparece `AlertDialog` de confirmación" — no dice qué hace "Sí, cancelar orden". El primer AC de Historia 3.7 empieza exactamente ahí ("Dado que el proveedor cancela una venta sin cobros registrados... Cuando confirma en el `AlertDialog`... Entonces la venta pasa a `Cancelado`"), confirmando que la frontera entre historias es precisamente esa: 3.5 construye el disparador, 3.7 construye la acción. Ni siquiera el caso "más simple" (venta sin cobros) se implementa aquí a medias — ninguna historia de este proyecto ha dejado una función parcialmente implementada esperando que la siguiente la complete; siempre se corta en un límite explícito con un Snackbar placeholder (mismo patrón que "Revisar Orden" en 3.2, el ticket en 3.3, y esta misma pantalla de detalle en 3.1).

### Por qué `InstallmentUiStatus` no reutiliza `SaleUiStatus`

`SaleUiStatus` (con su valor `OVERDUE`/"Atraso") describe el estado de una **venta completa** en el contexto de S-02 (Historial) — hoy es código muerto porque `OrderCard.toUiStatus()` nunca lo produce (ninguna historia hasta ahora ha necesitado mostrarlo). "Vencida" en AC-2 de esta historia es un concepto distinto: el estado de **una parcialidad individual** dentro de una venta con estado `Parcial`. Mezclar ambos forzaría un enum de 4 valores a significar cosas diferentes según el contexto. Se crea un enum pequeño y local (`InstallmentUiStatus`, 3 valores) en vez de expandir `SaleUiStatus` a un quinto valor que no aplicaría a nivel de venta.

### Por qué se duplica la lógica de imprimir/compartir de `PaymentViewModel` en vez de extraerla

`TicketSheet.kt` (Historia 3.4) ya es un Composable sin estado propio, reutilizable tal cual — pero la orquestación (`onPrintClick`/`onShareClick`, manejo de `isPrinting`/`isSharing`/`printError`, guardas de doble-tap) vive completa dentro de `PaymentViewModel`, sin ninguna extracción a una clase compartida. Extraerla a un delegate/use case compartido sería la solución "correcta" a largo plazo, pero: (a) ningún AC de esta historia lo exige, (b) tocar `PaymentViewModel.kt` para refactorizarlo arriesga esa suite de tests ya estable y aprobada en el code review de 3.4, (c) el proyecto ya tiene precedente aceptado de duplicar código pequeño y estable en vez de crear abstracciones prematuras (formateadores de moneda, duplicados y validados dos veces en code reviews anteriores). Se documenta aquí como una oportunidad de refactor futura, no como deuda urgente.

### Gaps heredados al reutilizar `GenerateTicketUseCase` (ya diferidos, no de esta historia)

`GenerateTicketUseCase.getClientById(...)` no tiene scope de tenant (falla heredada de `ClientRepository.getClientById`, diferida desde Historias 2.1/2.2, van 6 historias mencionándola) y traga errores reales del cliente en silencio (`runCatching{}.getOrNull()` sin log, mismo patrón diferido desde Historia 2.3). Esta historia hereda ambos al reutilizar el use case para "Compartir Ticket" — no son gaps nuevos, no se resuelven aquí.

### Archivos existentes relevantes — estado actual y cambio en esta historia

| Archivo | Estado actual | Cambio en esta historia |
|---------|---------------|--------------------------|
| `domain/models/InstallmentStatus.kt` | `PENDING`/`PAID` (Historia 3.3), sin "vencida" | Sin cambios — "vencida" se deriva, no se persiste (ver T1) |
| `ui/components/StatusBadge.kt`/`SaleUiStatus` | 4 valores, `OVERDUE` sin usar en ningún lugar (código muerto) | Sin cambios — reutilizado tal cual para el badge de venta; el estado de parcialidad usa un enum nuevo y separado |
| `ui/screens/orders/OrderListScreen.kt` | `OrderCard.onClick` → Snackbar placeholder (Historia 3.1) | + `onOrderClick`, navega de verdad a S-09 |
| `ui/navigation/Routes.kt`/`NavGraph.kt` | 13 rutas (Historia 3.4) | + `OrderDetail` |
| `data/repositories/SaleRepository.kt` | `getSaleDetail` (Historia 3.4), sin métodos de escritura de estado | Sin cambios — esta historia es de solo lectura |
| `ui/screens/orders/TicketSheet.kt` | Composable sin estado propio (Historia 3.4) | Sin cambios — reutilizado tal cual desde `OrderDetailScreen` |

**NO tocar:**
- `PaymentViewModel.kt`/`PaymentScreen.kt` — ya estables, cubiertos por su propia suite; no se refactoriza para compartir código con esta historia (ver Dev Notes).
- `SaleRepository.kt` — sin agregar ningún método de escritura/cancelación aquí; eso es Historia 3.7.
- No crear `CancelSaleUseCase`/`CreditBalanceEntity`/`ApplyCreditBalanceUseCase` — Historia 3.7.
- No agregar UI de registro de cobros — Historia 3.6.
- `StatusBadge.kt`/`SaleUiStatus` — no expandir con un quinto valor; el estado de parcialidad es un tipo separado (T1).

### Testing

Mismo patrón establecido en Historias 2.1-3.4: **sin Robolectric**, tests JVM puros con Fake DAOs/interfaces, `./gradlew :app:testDebugUnitTest`.

- **`InstallmentUiStatus`/`toUiStatus()`** (en `OrderDetailViewModelTest` o archivo propio) — parcialidad pagada → `PAID` sin importar la fecha; parcialidad pendiente con fecha futura → `PENDING`; parcialidad pendiente con fecha pasada → `OVERDUE`; parcialidad pendiente con fecha exactamente igual a `now` → `PENDING` (`isBefore` estricto, no `isBefore/equals`).
- **`OrderDetailViewModelTest`** (nuevo) — carga correcta de folio/fecha/cliente/status/ítems/totales desde `SaleDetail`; `paymentCondition` `SinglePayment` para venta sin parcialidades, `InstallmentPlan` ordenado por fecha para venta con parcialidades; `notFound=true` con `saleId` inexistente o de otro tenant; `notFound=true` con `tenantId` nulo; cliente eliminado no tumba la carga (nombre vacío, mismo criterio que `GenerateTicketUseCase`); `onShareTicketClick` genera el ticket una sola vez y lo reutiliza en llamadas subsecuentes; `onCancelOrderConfirm` setea el mensaje placeholder exacto; `onCancelPlaceholderShown` lo limpia.
- Sin test de Composable/UI (`OrderDetailScreen`) — mismo criterio que todas las historias previas.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Historia 3.5: Detalle de Orden e Historial de Cobros] (líneas 604-627) — AC originales
- [Source: _bmad-output/planning-artifacts/epics.md#Historia 3.7: Crédito a Favor y Cancelación de Venta con Cobros] (líneas 652-687) — confirma dónde retoma la cancelación real
- [Source: _bmad-output/planning-artifacts/epics.md#Requirements Inventory] FR-15 (línea 48, estatus automático), FR-16 (línea 50, cancelación — bundleada con 3.7), FR-20 (línea 60, historial de cobros)
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/EXPERIENCE.md] líneas 37, 77-78, 131, 143, 192-197 — spec visual de S-09; línea 195 nota "parcialidades vencidas muestran badge adicional" sin especificar copy exacto (se usa "Vencida", consistente con el AC)
- [Source: _bmad-output/planning-artifacts/architecture/architecture.md] líneas 360-361 — `OrderDetailScreen.kt`/`OrderDetailViewModel.kt` ya anticipados; confirma que no existe ningún `CancelSaleUseCase` anticipado
- [Source: _bmad-output/implementation-artifacts/3-4-generacion-y-distribucion-del-ticket.md] `GenerateTicketUseCase(saleId, tenantId)` diseñado deliberadamente reutilizable — esta historia es la primera en reutilizarlo desde una pantalla distinta a S-08; `TicketSheet` confirmado como Composable sin estado propio
- [Source: _bmad-output/implementation-artifacts/deferred-work.md] gaps heredados de `GenerateTicketUseCase`/`ClientRepository.getClientById` (tenant-scoping, manejo de errores silencioso)
- [Source: android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderListScreen.kt, PaymentViewModel.kt, PaymentScreen.kt, TicketSheet.kt, ui/components/OrderCard.kt, StatusBadge.kt, ui/navigation/Routes.kt, NavGraph.kt] estado actual antes de esta historia

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (`claude-sonnet-5`)

### Debug Log References

Sin desviaciones respecto a lo especificado en Tasks/Subtasks — todas las decisiones de arquitectura delicadas (frontera 3.5/3.7 en "Cancelar Orden", `InstallmentUiStatus` separado de `SaleUiStatus`, duplicación deliberada de la orquestación de imprimir/compartir en vez de extraerla de `PaymentViewModel`) ya venían resueltas en la historia y se siguieron tal cual. Build limpio sin warnings desde la primera compilación.

Un detalle de wiring no explícito en el código skeleton del spec: `TicketSheet`'s botón interno "Compartir" se conectó a `viewModel::onShareTicketClick` (el mismo entry point que el botón "Compartir Ticket" de la pantalla principal), no a un `onShareClick` separado — `onShareTicketClick` ya es idempotente respecto a la generación del ticket (`if (ticketData == null)` antes de llamar a `generateTicketUseCase`), así que reutilizarlo desde dentro del sheet re-comparte el mismo PNG ya generado sin duplicar código ni regenerar innecesariamente.

### Completion Notes List

Historia implementada completa. 234 tests ✅ (0 fallos, +14 sobre Historia 3.4). `BUILD SUCCESSFUL` (`assembleDebug` + `testDebugUnitTest`, sin warnings).

- AC-1 ✅: `OrderDetailScreen` (S-09) muestra folio (título de la `TopAppBar`), fecha, nombre del cliente, `StatusBadge` (mismo mapeo `SaleStatus.toUiStatus()` que `OrderCard.kt`, duplicado deliberadamente), ítems con cantidad/precio/subtotal, condición de pago, e historial de cobros — todo cargado desde `SaleRepository.getSaleDetail` (Historia 3.4, sin cambios necesarios, ya tenant-scoped).
- AC-2 ✅: cada parcialidad muestra fecha, monto y un chip de estado derivado vía `Installment.toUiStatus()` → "Pagada"/"Pendiente"/"Vencida", con los tokens de color `StatusPaid`/`StatusPending`/`StatusOverdue` ya existentes.
- AC-3 ✅: botón "Compartir Ticket" → `onShareTicketClick` genera el `TicketData` (si no existe ya) vía `GenerateTicketUseCase(saleId, tenantId)` — primera reutilización real desde una pantalla distinta a S-08, confirmando que quedó bien desacoplado en Historia 3.4 — y dispara el `Intent.ACTION_SEND` real (mismo wiring que `PaymentScreen.kt`).
- AC-4 ✅: botón "Cancelar Orden" oculto si `status == SaleStatus.CANCELLED`; `AlertDialog` con el texto EXACTO del AC ("¿Cancelar esta orden? Esto no se puede deshacer." / "Sí, cancelar orden" / "No, mantenerla"); "Sí, cancelar orden" dispara el Snackbar placeholder "Cancelación de orden — disponible próximamente" (Historia 3.7 la implementa de verdad) y cierra el dialog.
- Historial de cobros vacío para ventas con parcialidades (sin `RegisterPaymentUseCase` todavía, Historia 3.6) se muestra como "Sin cobros registrados todavía." — comportamiento correcto, no un error, verificado con test dedicado.
- Reutilización confirmada de `TicketSheet`/`GenerateTicketUseCase` (Historia 3.4) sin ninguna modificación a esos archivos.
- Sin test de Composable/UI (`OrderDetailScreen`) — mismo criterio que todas las historias previas.
- **Pendiente:** verificación manual en emulador/dispositivo físico — este entorno no tiene `adb` ni emulador Android disponible (mismo pendiente arrastrado desde Historias 2.1-3.4). Recomendado antes de mergear: Historial → tocar una `OrderCard` → verificar todos los campos de S-09 → "Compartir Ticket" (verificar que reutiliza el mismo flujo de compartir de S-08) → "Cancelar Orden" (verificar texto exacto del dialog y que el botón está oculto en una venta ya cancelada) → probar con una venta de Parcialidades (verificar chips de estado, incluyendo una parcialidad vencida si es posible simular la fecha).

### File List

**Archivos creados (NEW):**
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderDetailViewModel.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderDetailScreen.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/InstallmentUiStatusTest.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/OrderDetailViewModelTest.kt`

**Archivos modificados (UPDATE):**
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/Routes.kt` — + `OrderDetail`
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/NavGraph.kt` — + composable de `OrderDetail`, `Orders` pasa `onOrderClick`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderListScreen.kt` — `OrderCard.onClick` navega de verdad a S-09 (ya no Snackbar placeholder, Historia 3.1)

## Change Log

- **2026-07-26** — Historia 3.5 implementada completa (Status: review)
  - NEW: `InstallmentUiStatus` (`PENDING`/`PAID`/`OVERDUE`) + `Installment.toUiStatus()` — "vencida" derivada por comparación de fecha, no persistida
  - NEW: `OrderDetailViewModel`/`OrderDetailScreen` (S-09) — folio/fecha/cliente/`StatusBadge`/ítems/totales/condición de pago/historial de cobros, reutilizando `SaleRepository.getSaleDetail` (Historia 3.4) sin cambios
  - NEW: primera reutilización real de `GenerateTicketUseCase`/`TicketSheet` (Historia 3.4) desde una pantalla distinta a S-08, sin modificar ninguno de los dos
  - NEW: botón "Cancelar Orden" + `AlertDialog` de confirmación con el texto exacto del AC; "Sí, cancelar orden" es un Snackbar placeholder — la cancelación real es Historia 3.7
  - UPDATE: `Routes`/`NavGraph` — ruta `order_detail/{saleId}`; `OrderListScreen` — `OrderCard.onClick` navega de verdad (ya no Snackbar placeholder de Historia 3.1)
  - NEW: tests — `InstallmentUiStatusTest` (4), `OrderDetailViewModelTest` (10)
  - Build: 234 tests ✅ (0 fallos, +14 sobre Historia 3.4), `BUILD SUCCESSFUL` (`assembleDebug` + `testDebugUnitTest`, sin warnings)
  - Pendiente: verificación manual en dispositivo/emulador (sin `adb` en este entorno); code review todavía no ejecutado

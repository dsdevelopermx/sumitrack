---
baseline_commit: 12408158bd308544768a8438e3c8850857fa058b
---

# Story 5.3: Agenda y Calendario de Cobros

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

Como proveedor,
quiero ver todos mis cobros programados en un calendario,
para que pueda planificar mi ruta del día de un vistazo sin buscar en cada orden.

## Acceptance Criteria

**AC-1 — Acceso desde S-02 y mes actual**

**Dado** que el proveedor está en S-02 (lista de órdenes)
**Cuando** toca el ícono de calendario de la app bar (`contentDescription`: "Agenda de cobros")
**Entonces** se abre S-10 mostrando el mes actual en un calendario personalizado de Compose (cuadrícula de 7 columnas)

**AC-2 — Tile de un día con cobros**

**Dado** que un día tiene cobros programados
**Cuando** se renderiza su celda
**Entonces** el tile tiene un fondo distinto al de los días sin cobros Y muestra superpuesto el número de cobros (canal visual secundario, no solo color); si algún cobro de ese día está dentro del umbral de `dias_anticipacion_recordatorio`, la celda muestra además un ícono de campana

**AC-3 — Accesibilidad de cada celda**

**Dado** que cada celda de día se renderiza
**Cuando** TalkBack la anuncia
**Entonces** su `contentDescription` es "Lunes 29 de junio, 3 cobros pendientes" (singular: "1 cobro pendiente") o "Martes 30 de junio, sin cobros" (FR-23, UX-DR22); cada celda cumple el mínimo táctil de 48×48dp (UX-DR20)

**AC-4 — Lista de cobros de un día**

**Dado** que el proveedor toca un día con cobros
**Cuando** se registra el toque
**Entonces** se despliega la lista de cobros de ese día con: nombre del cliente, monto, folio y estado de vencimiento ("Pendiente" / "Vencida")

**AC-5 — Navegación a S-09**

**Dado** que el proveedor toca una fila de la lista del día
**Cuando** se registra el toque
**Entonces** navega a S-09 (Detalle de Orden) de la venta correspondiente

**AC-6 — Estado vacío**

**Dado** que no hay cobros programados en ningún día
**Cuando** S-10 se muestra
**Entonces** aparece el empty state: "No hay cobros programados. Crea una orden con parcialidades para verlos aquí."

**AC-7 — Offline**

**Dado** que el proveedor está offline
**Cuando** S-10 se muestra
**Entonces** el calendario usa solo datos de SQLite local y S-10 no agrega ningún banner propio de offline

## Fuera de alcance (decisiones de scope explícitas)

- **Cobros de ventas de pago único** — igual que en la 5.2: el modelo no tiene fecha de pago para ellas; la agenda opera solo sobre `InstallmentEntity` (parcialidades). Por eso el empty state pide "una orden con parcialidades".
- **Solo parcialidades `pending`** — no se muestran parcialidades `paid`/`cancelled` (el AC habla de "cobros pendientes"). Las vencidas y aún sin cobrar SÍ aparecen, en el día en que vencieron (navegando a meses pasados).
- **Navegación entre meses (anterior/siguiente)** — el AC solo exige "el mes actual", pero un calendario que no permite salir del mes actual dejaría invisibles las parcialidades de meses futuros (la mayoría de las de un plan). Se agregan dos botones "Mes anterior"/"Mes siguiente" como parte mínima necesaria; NO se agregan gestos de swipe, selector de mes/año ni botón "Hoy".
- **Banner de offline** — `MainScreen` ya muestra un `OfflineBanner` global sobre TODO el `NavGraph` (incluye S-10). "Sin banner adicional" se interpreta como: S-10 no agrega ninguno propio ni cambia el global; ocultarlo solo en S-10 exigiría lógica por ruta en `MainScreen` y no se hace.
- **Deep link desde las notificaciones de la 5.2 hacia S-10/S-09**, pull-to-refresh, filtros/búsqueda dentro de la agenda, marcar un cobro como cobrado desde la agenda (el cobro se registra en S-09).
- **Primer día de la semana configurable** — fijo por locale (`es-MX`).
- **Cambios de backend y de schema Room** — 100% Android, sin migración.

## Tasks / Subtasks

### Android — datos de la agenda

- [x] T1: En `InstallmentDao.kt` agregar `data class AgendaRow(val installmentId: String, val saleId: String, val folio: String, val clientName: String, val amount: BigDecimal, val dueDate: Instant)` y `fun observeAgendaForTenant(tenantId: String): Flow<List<AgendaRow>>` con `@Query` (mismo estilo de string concatenado que `SaleDao.getOrdersForTenantAsFlow`): `SELECT i.id AS installmentId, s.id AS saleId, s.folio AS folio, COALESCE(c.name, '(cliente eliminado)') AS clientName, i.amount AS amount, i.due_date AS dueDate FROM installments i JOIN sales s ON s.id = i.fk_sale LEFT JOIN clients c ON c.id = s.fk_client WHERE i.fk_tenant = :tenantId AND i.status = 'pending' AND s.status IN ('pending', 'partial') ORDER BY i.due_date ASC, i.id ASC`. Aquí SÍ se une con `sales` (se necesita el folio y el `saleId`), así que el filtro por estado de la venta es gratuito. `BigDecimal`/`Instant` en un POJO de Room funcionan igual que en `OrderSummaryRow` (los `TypeConverter` ya están registrados).
- [x] T2: Actualizar `FakeInstallmentDao` (`ui/screens/orders/FakeInstallmentDao.kt`): implementar `observeAgendaForTenant` mapeando sus parcialidades `pending` del tenant a `AgendaRow`, con folio y nombre de cliente tomados de un helper de test `fun setSaleContext(saleId: String, folio: String, clientName: String)` (mismo criterio que `FakeSaleDao.clientNames`; sin contexto → folio `"(sin folio)"` y cliente `"(cliente eliminado)"`). Limitación aceptada: el Fake no replica el filtro por estado de la venta ni el SQL del join — igual que `getOrdersForTenantAsFlow` hoy. **Regla recurrente (4.4, 5.1, 5.2): al agregar un método a una interfaz DAO, todos los Fakes dejan de compilar** — `grep -rn ": InstallmentDao" android/app/src/test`.
- [x] T3: Modelo `AgendaEntry(installmentId, saleId, folio, clientName, amount: BigDecimal, dueDate: Instant)` en `domain/models/` y `SaleRepository.getAgendaForTenant(tenantId: String): Flow<List<AgendaEntry>>` = `installmentDao.observeAgendaForTenant(tenantId).map { rows -> rows.map { it.toDomain() } }` (mismo patrón que `getOrdersForTenant`; `SaleRepository` ya recibe `installmentDao`, no cambia su constructor). Test de mapeo con `FakeInstallmentDao` en el archivo de tests de `SaleRepository` que corresponda (localizarlo con `grep -rln "SaleRepository(" android/app/src/test`).
- [x] T4: Proveer `java.time.Clock` por Hilt — nuevo `di/TimeModule.kt`: `@Module @InstallIn(SingletonComponent::class) object TimeModule { @Provides fun provideClock(): Clock = Clock.systemDefaultZone() }` (hoy no existe ningún `Clock` en el proyecto). El `AgendaViewModel` lo recibe por constructor para que los tests fijen "hoy" con `Clock.fixed(...)`; sin esto los tests dependerían de la fecha real y se romperían en cambio de mes. Dagger no soporta parámetros de constructor con valor por defecto, por eso no se usa `() -> Instant = ...` como en `ReminderReconciler.start` (que no es un `@Inject` constructor).

### Android — lógica pura del calendario

- [x] T5: Crear `ui/screens/agenda/AgendaCalculator.kt` (objeto puro, sin `android.*`, `zone`/`today`/`now` como parámetros — mismo criterio que `ReminderPlanner`):
  - `groupByDay(entries: List<AgendaEntry>, zone: ZoneId): Map<LocalDate, List<AgendaEntry>>` (día = `dueDate.atZone(zone).toLocalDate()`; dentro del día, orden por `dueDate` y luego `installmentId`).
  - `data class AgendaGridCell(val date: LocalDate, val count: Int, val hasAlert: Boolean, val isToday: Boolean)`.
  - `buildMonthGrid(month: YearMonth, byDay: Map<LocalDate, List<AgendaEntry>>, today: LocalDate, diasAnticipacion: Int, firstDayOfWeek: DayOfWeek): List<AgendaGridCell?>` — `null` = celdas de relleno antes del día 1 para alinear con `firstDayOfWeek`; la lista NO se rellena al final. `hasAlert = count > 0 && ChronoUnit.DAYS.between(today, date) in 0..diasAnticipacion` (una fecha pasada NO lleva campana: lo vencido se comunica con el estado "Vencida" en la lista).
  - `dayDescription(date: LocalDate, count: Int, hasAlert: Boolean): String` con `Locale.forLanguageTag("es-MX")`: `"<Día de la semana capitalizado> <d> de <mes>, <n> cobros pendientes"` / `"1 cobro pendiente"` / `"..., sin cobros"`. Ej.: "Lunes 29 de junio, 3 cobros pendientes". **Adición deliberada de accesibilidad**: si `hasAlert`, se agrega `", con cobros próximos a vencer"` al final — la campana desaparece del árbol de TalkBack porque la celda reemplaza sus semantics; sin esto un usuario de TalkBack no recibiría esa señal.
  - `fun defaultFirstDayOfWeek(): DayOfWeek = WeekFields.of(Locale.forLanguageTag("es-MX")).firstDayOfWeek` (el ViewModel lo usa; los tests pasan el valor explícito para no depender de los datos de locale del JDK).
- [x] T6: `AgendaCalculatorTest.kt` (JVM puro, fechas fijas): agrupación por día en la zona `America/Mexico_City` (una parcialidad a las 23:30 locales cae en su día local, no en el UTC); relleno inicial correcto para `firstDayOfWeek = MONDAY` y `SUNDAY`; mes de 28/30/31 días; `count`/`isToday`; `hasAlert` en los bordes (`0` y `diasAnticipacion` con campana, `diasAnticipacion + 1` y fecha pasada sin campana, día sin cobros sin campana); `dayDescription` con 0, 1 y 3 cobros, con y sin alerta. Si algún string exacto de mes/día difiere por los datos de locale del JDK de tests, ajustar SIN cambiar el formato de producción.

### Android — ViewModel de S-10

- [x] T7: Crear `ui/screens/agenda/AgendaViewModel.kt` (`@HiltViewModel`; `SaleRepository`, `SettingsRepository`, `@TenantId Flow<String?>`, `Clock`). Estado en `AgendaUiState(isLoading = true, hasAnyEntries = false, month: YearMonth, monthLabel: String, cells: List<AgendaGridCell?>, selectedDate: LocalDate? = null, selectedEntries: List<AgendaEntryUi> = emptyList())` con `data class AgendaEntryUi(val entry: AgendaEntry, val isOverdue: Boolean)`, donde `isOverdue = entry.dueDate.isBefore(Instant.now(clock))` (la MISMA regla que `Installment.toUiStatus`, ver Dev Notes). Fuentes: `tenantId.flatMapLatest { t -> if (t.isNullOrBlank()) flowOf(emptyList()) else saleRepository.getAgendaForTenant(t) }.catch { emit(emptyList()) }` (mismo manejo que `OrderListViewModel`) + `settingsRepository.observeDiasAnticipacion()` (la 5.2 ya lo expone) + `MutableStateFlow<YearMonth>` (inicial `YearMonth.now(clock)`) + `MutableStateFlow<LocalDate?>` — combinados con `stateIn(WhileSubscribed(5_000))`. `today = LocalDate.now(clock)`, `zone = clock.zone`. Acciones: `onPreviousMonth()`, `onNextMonth()` (al cambiar de mes se limpia `selectedDate`), `onDayClick(date)` (solo selecciona si ese día tiene cobros; volver a tocar el mismo día lo deselecciona; un día sin cobros no hace nada). `monthLabel` = `"septiembre 2026"` (`MMMM yyyy`, `es-MX`, primera letra en mayúscula).
- [x] T8: `AgendaViewModelTest.kt` (JVM puro; `FakeInstallmentDao` + `setSaleContext`, `FakeSettingsDao`, `MutableStateFlow<String?>` como tenant, `Clock.fixed(...)` en `America/Mexico_City`; recolectar `vm.uiState` con el patrón de `OrderListViewModelTest`: `Dispatchers.setMain(StandardTestDispatcher)` + `val job = launch { vm.uiState.collect {} }; advanceUntilIdle()` — ojo: `advanceUntilIdle()` NO ejecuta corrutinas de `backgroundScope` (lección de la 5.2), por eso el `launch` es del scope del test): mes inicial = el del reloj fijo; sin parcialidades → `hasAnyEntries = false`; conteo por día y `hasAlert` con el `dias_anticipacion_recordatorio` por defecto (3) y tras cambiarlo; `onNextMonth`/`onPreviousMonth` y su efecto sobre `selectedDate`; `onDayClick` selecciona/deselecciona y lista las parcialidades del día ordenadas, con `isOverdue` correcto respecto al reloj fijo (una que venció ayer → `true`, una de mañana → `false`); tocar un día sin cobros no selecciona; parcialidad `paid` o de otro tenant no aparece; tenant `null` → vacío.

### Android — pantalla S-10 y navegación

- [x] T9: Crear `ui/screens/agenda/AgendaScreen.kt` (`AgendaScreen(onBackClick, onEntryClick: (saleId: String) -> Unit, viewModel = hiltViewModel())`), `Scaffold` con `TopAppBar` (título "Agenda de cobros", `IconButton` de regreso con `contentDescription = "Volver"`, mismo patrón que `ClientFormScreen`/`ProductListScreen`). Contenido:
  - `isLoading` → `CircularProgressIndicator` centrado; `!hasAnyEntries` → `EmptyState(icon = Icons.Outlined.CalendarMonth, message = "No hay cobros programados. Crea una orden con parcialidades para verlos aquí.")` (componente existente `ui/components/EmptyState.kt`) y NO se dibuja el calendario.
  - Cabecera de mes: `IconButton` "Mes anterior" (`contentDescription`) · `monthLabel` · `IconButton` "Mes siguiente".
  - Fila de encabezados de día de la semana (L M M J V S D o D L M M J V S según `defaultFirstDayOfWeek()`), `clearAndSetSemantics {}` (decorativa; cada celda ya se describe sola).
  - Cuadrícula de 7 columnas armada con `Column` de `Row`s (máx. 6 filas; NO `LazyVerticalGrid` dentro de un contenedor que hace scroll). Padding horizontal de la pantalla de **8dp** (no 16dp): con 360dp de ancho cada celda mide (360−16)/7 ≈ 49dp ≥ 48dp (UX-DR20); alto de celda mínimo 48dp.
  - Celda con cobros: fondo `MaterialTheme.colorScheme.primaryContainer`, número del día, y el conteo superpuesto en la esquina superior derecha (`Text` pequeño); campana `Icons.Filled.Notifications` (~14dp, tinte `StatusPending`) si `hasAlert`; `hoy` con borde; seleccionado con borde más grueso/fondo `primary`. Celda sin cobros: sin fondo, `onSurfaceVariant`. Semantics de TODA celda: `clearAndSetSemantics { contentDescription = AgendaCalculator.dayDescription(...); if (count > 0) { role = Role.Button; onClick(...) } }` — solo las celdas con cobros son clicables.
  - Debajo: si hay `selectedDate`, encabezado "Cobros del <d de mes>" y una fila por `selectedEntries` (altura mínima 64dp, `semantics(mergeDescendants = true)`, `clickable` → `onEntryClick(entry.saleId)`): cliente, monto (`"$" + setScale(2)` como `OrderDetailScreen.formatAmount`), folio, y estado. Estado = "Vencida" (`StatusOverdue`) si `isOverdue`, si no "Pendiente" (`StatusPending`) — misma regla que `Installment.toUiStatus` (Historia 3.5) para que S-09 y S-10 coincidan. Reutilizar `installmentStatusLabelAndColor` de `OrderDetailScreen.kt` haciéndola `internal` (hoy es `private`) en lugar de duplicarla. Sin `selectedDate`: texto guía "Toca un día para ver sus cobros."
  - El estado "Vencida"/"Pendiente" de cada fila viene precalculado en `AgendaEntryUi.isOverdue` — no llamar `Instant.now()` dentro del composable.
  - Sin banner propio de offline (AC-7).
- [x] T10: Navegación: `Routes.Agenda : Routes("agenda")` en `Routes.kt`; en `NavGraph.kt` `composable(Routes.Agenda.route) { AgendaScreen(onBackClick = { navController.popBackStack() }, onEntryClick = { saleId -> navController.navigate(Routes.OrderDetail.createRoute(saleId)) { launchSingleTop = true } }) }`.
- [x] T11: S-02 (`OrderListScreen.kt`): hoy NO tiene app bar (solo `Scaffold` con FAB y `SearchBar`). Agregar `topBar = { TopAppBar(title = { Text("Órdenes") }, actions = { IconButton(onClick = onAgendaClick) { Icon(Icons.Outlined.CalendarMonth, contentDescription = "Agenda de cobros") } }) }` (`@OptIn(ExperimentalMaterial3Api::class)` ya está en el archivo) y un nuevo parámetro `onAgendaClick: () -> Unit = {}`; en `NavGraph.kt` pasar `onAgendaClick = { navController.navigate(Routes.Agenda.route) { launchSingleTop = true } }`. Los insets ya los absorbe el `Scaffold` externo de `MainScreen`; el `TopAppBar` anidado no debe duplicar el padding de la barra de estado (mismo caso que `ClientFormScreen`, que ya usa `TopAppBar` dentro del `NavGraph`). No tocar el resto de S-02 (búsqueda, filtros, FAB, pull-to-refresh, foco del FAB de la 3.4).
- [x] T12: Verificación: `./gradlew compileDebugKotlin compileDebugUnitTestKotlin testDebugUnitTest assembleDebug` (suite completa; estado previo: 383 tests, 0 fallos). La verificación visual en dispositivo (cuadrícula, tamaños táctiles a 360dp, TalkBack, campana, navegación a S-09) NO es posible en este entorno (sin `adb`/emulador) — anotarla como pendiente en Completion Notes, igual que el gap heredado desde 2.1.

### Review Findings

Revisión con `bmad-code-review` (Blind Hunter 13 · Edge Case Hunter 11 · Acceptance Auditor 9 hallazgos, deduplicados).

- [x] [Review][Decision→Resuelto] La descripción de TalkBack agrega ", con cobros próximos a vencer" cuando hay campana, pese a que AC-3/UX-DR22 fijan el texto literal — **Decisión del usuario: mantener el sufijo**. Desviación aceptada y documentada: el texto base es idéntico al literal del AC; el sufijo solo aparece en celdas con campana, para que los usuarios de TalkBack reciban la señal que la celda reemplaza al limpiar su árbol de semantics [AgendaCalculator.kt:dayDescription]
- [x] [Review][Patch] Los cobros vencidos (los más urgentes) viven en meses ANTERIORES y S-10 abre en el mes actual sin ninguna señal — **Decisión del usuario: aviso de vencidos**: mantener el mes actual (AC-1) y agregar bajo la cabecera del mes una línea "Tienes N cobros vencidos en meses anteriores" que al tocarla salta al mes del cobro vencido más antiguo [AgendaScreen.kt / AgendaViewModel.kt] — **Corregido**: `AgendaCalculator.overdueBeforeMonth` + `overdueNotice`; `AgendaUiState` expone `overdueBeforeMonthCount`/`earliestOverdueMonth`; S-10 muestra bajo la cabecera del mes un `TextButton` "Tienes N cobros vencidos en meses anteriores" (texto + color `StatusOverdue`) que llama `onOverdueNoticeClick()` y salta al mes del vencido más antiguo. Tests: 2 en la calculadora + 2 en el ViewModel.
- [x] [Review][Patch] "Hoy", "ahora", el estado Vencida/Pendiente y la campana se congelan en el momento de la emisión — con la pantalla abierta pasada la medianoche/la hora de vencimiento, o tras volver a la app otro día, quedan obsoletos hasta que emita algún flujo; además `Clock.systemDefaultZone()` captura la zona una sola vez [AgendaViewModel.kt, TimeModule.kt] — **Corregido**: `AgendaViewModel.onResume()` (llamado por `LifecycleEventEffect(ON_RESUME)` en la pantalla) refresca el cálculo y, si el DÍA cambió, vuelve al mes actual y limpia la selección; `TimeModule` ahora provee un `Clock` que lee `ZoneId.systemDefault()` en vivo. Tests con un `MutableClock`: recalcula Vencida al avanzar la hora, regresa al mes actual tras cambiar el día, y el mismo día no mueve el mes que el usuario está viendo.
- [x] [Review][Patch] Un día con cobros ya vencidos hoy (`dueDate` antes de "ahora", etiquetados "Vencida") aún muestra campana y "próximos a vencer" (`daysUntil == 0`), contradiciendo su propia fila [AgendaCalculator.kt:buildMonthGrid] — **Corregido**: `buildMonthGrid` recibe `now` y `hasAlert` exige al menos un cobro del día con `!dueDate.isBefore(now)`. Test nuevo (solo vencido hoy → sin campana; vigente o mezcla → con campana).
- [x] [Review][Patch] `selectedDate` sobrevive si sus cobros desaparecen (pago registrado, venta cancelada, pull) o si un toque llega tras cambiar de mes: queda "Cobros del X" con lista vacía y una fecha fuera de la cuadrícula [AgendaViewModel.kt] — **Corregido**: en el `combine`, la selección solo es válida si es del mes visible Y ese día aún tiene cobros. Test nuevo: al marcar `paid` la parcialidad del día seleccionado, la selección se limpia.
- [x] [Review][Patch] La celda no sigue T9 al pie de la letra: la acción/rol de clic depende de cómo se combine `clickable` con `clearAndSetSemantics` (sin verificar en dispositivo) en vez de declararse en el bloque de semantics; y el día seleccionado se distingue solo por color (sin canal secundario ni semántica `selected`) [AgendaScreen.kt:DayCell] — **Corregido**: rol `Button`, acción `onClick` y `selected` se declaran dentro de `clearAndSetSemantics` (sin depender del merge con `clickable`); el día seleccionado además lleva el número en negritas (canal no dependiente del color).
- [x] [Review][Patch] Desviaciones menores de T9: celdas sin cobros usan `onSurface` (T9: `onSurfaceVariant`) y la campana mide 12dp (T9: ~14dp) [AgendaScreen.kt:DayCell] — **Corregido**: celdas sin cobros usan `onSurfaceVariant`; la campana mide 14dp.
- [x] [Review][Patch] Cobertura de tests: `onPreviousMonth` no prueba que limpie `selectedDate`, y `dayDescription` con alerta solo se prueba con 3 cobros (T6 pide 0, 1 y 3 con y sin alerta) [AgendaViewModelTest.kt, AgendaCalculatorTest.kt] — **Corregido**: tests nuevos de `onPreviousMonth` limpiando la selección y de `dayDescription` con alerta en singular (y sin cobros ignorando la alerta).
- [x] [Review][Defer] `.catch { emit(emptyList()) }` convierte un error de Room en el empty state "No hay cobros programados" sin reintento — mismo patrón ya usado por `OrderListViewModel`; el proyecto no tiene UI de error para listas — deferred, pre-existing
- [x] [Review][Defer] A 200 % de escala de fuente, el número del día, el conteo superpuesto y la campana pueden solaparse dentro de una celda de ~49dp — no verificable sin dispositivo — deferred
- [x] [Review][Defer] El `TopAppBar` anidado (S-02 y S-10) podría duplicar el inset de la barra de estado: `MainScreen` no llama `consumeWindowInsets` (la frase de T11 "los insets ya los absorbe el Scaffold externo" no está verificada); no es una regresión (`ClientFormScreen` ya hace lo mismo) — deferred, verificar en dispositivo
- [x] [Review][Defer] El `JOIN sales` interno oculta parcialidades cuya venta aún no se sincronizó, y el `LEFT JOIN clients` rotula "(cliente eliminado)" a un cliente que existe pero aún no llegó — mismo criterio y texto que `OrderSummaryRow` — deferred, pre-existing
- [x] [Review][Defer] Los montos no llevan separador de miles ("$1250.00", UX-voice pide "$1,250.00") — es la 3.ª copia privada de `formatAmount`; el gap es de toda la app — deferred, pre-existing
- [Review][Dismissed] Room re-emite ante cualquier escritura a las tablas del join (sync) y se recalcula todo: `StateFlow` ya deduplica `AgendaUiState` iguales (data class), sin recomposición extra · "L M X J V S D" (`NARROW` de es-MX usa "X" para miércoles, correcto) · falta un test de año bisiesto/cambio de año · navegación de meses sin límite ni botón "Hoy" (explícitamente fuera de alcance) · `onDayClick` depende de un colector (documentado en el Debug Log) · regla de "vencida" reimplementada en vez de reutilizar `toUiStatus` (documentada; la comparación es idéntica)

## Dev Notes

### Reglas que ya cobraron caro en historias anteriores

- **`uiState` con `by` (delegado) no hace smart-cast**: usar `uiState.x?.let { ... }`, no `if (uiState.x != null) uiState.x` (compilador, historia 5.1).
- **Cambio de DAO ⇒ actualizar TODOS los Fakes** (4.4, 5.1, 5.2). Aquí solo `FakeInstallmentDao`.
- **Sin librería de mocking**: solo Fakes escritos a mano; no agregar Mockito/MockK ni `work-testing`/`compose-ui-test`.
- **No hay suite de UI/Compose tests** en el proyecto: `AgendaScreen` se verifica por compilación + revisión; toda la lógica con reglas vive en `AgendaCalculator` y `AgendaViewModel` (JVM puro).
- Gradle necesita `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

### Reutiliza la 5.2, no la reinventa

`SettingsRepository.observeDiasAnticipacion()` (default 3, acotado 1–30) es el umbral de la campana — el mismo valor que decide cuándo llega la notificación de la 5.2. `InstallmentDao.observeOpenForTenant` de la 5.2 NO sirve aquí (no trae folio ni cliente): la agenda necesita el join de T1.

### "Estado de vencimiento" = misma regla que S-09

`Installment.toUiStatus(now)` (OrderDetailViewModel, Historia 3.5): `dueDate.isBefore(now)` ⇒ VENCIDA, si no PENDIENTE — comparación de `Instant`, no de fecha. Una parcialidad que vence hoy a las 15:00 aparece "Vencida" en S-09 a las 16:00; la agenda debe decir lo mismo. La AGRUPACIÓN por día, en cambio, sí usa la fecha local (`atZone(zone).toLocalDate()`), que es la fecha que el proveedor ve en pantalla (`formatDate` usa `ZoneId.systemDefault()`).

### Por qué se une con `sales` en esta query (y no en la de la 5.2)

La 5.2 filtra solo por `installments.status = 'pending'` porque `cancelSale` cancela las parcialidades pendientes y `registerPayment` marca `paid` la cobrada. Aquí la unión con `sales` ya es obligatoria (folio + `saleId` para navegar a S-09), así que añadir `s.status IN ('pending','partial')` es defensa en profundidad sin costo.

### Accesibilidad — puntos fáciles de romper

1. **48dp**: con 16dp de padding a cada lado, a 360dp de ancho las celdas medirían ≈46.9dp (<48). De ahí el padding de 8dp.
2. **Color + forma + texto** (UX): fondo distinto + conteo numérico superpuesto + campana; el estado de la fila lleva texto además de color.
3. `clearAndSetSemantics` en la celda: reemplaza todo su árbol interno (número, conteo, campana) por UNA descripción; por eso la campana se refleja en el texto de `dayDescription`.

### `Clock` en Hilt

No existía. `TimeModule` lo provee sin scope (`Clock.systemDefaultZone()` es barato y sin estado). Los tests construyen el ViewModel con `Clock.fixed(Instant.parse("2026-09-21T15:00:00Z"), ZoneId.of("America/Mexico_City"))`.

### Lo que NO hay que tocar

`SaleRepository` (solo se agrega un método), `PullService`, `SyncManager`, `ReminderReconciler`/`ReminderWorker` (5.2), `SettingsViewModel`/`SettingsScreen` (5.1), backend. `OrderDetailScreen.kt` solo cambia la visibilidad de `installmentStatusLabelAndColor` (`private` → `internal`).

### Testing

JVM puro: `AgendaCalculatorTest`, `AgendaViewModelTest`, test de mapeo de `SaleRepository`. Las fechas de los tests se construyen con `ZonedDateTime.of(..., ZoneId.of("America/Mexico_City"))` como en `ReminderPlannerTest` (5.2), nunca con la fecha real del sistema.

### References

- `_bmad-output/planning-artifacts/epics.md` — Historia 5.3 (líneas 902-936), FR-23, UX-DR20, UX-DR22
- `_bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/EXPERIENCE.md` — S-10 (líneas 38, 80-83, 135, 205, 226-229, 254-255, 285, 344-362)
- `_bmad-output/implementation-artifacts/5-2-notificaciones-push-locales-para-recordatorios-de-cobro.md` — `observeDiasAnticipacion`, `ReminderPlanner` (patrón puro con `now`/`zone`), lección de `backgroundScope`
- `_bmad-output/implementation-artifacts/5-1-pantalla-de-configuracion-del-tenant.md` — smart-cast con `by`, patrón `TopAppBar` + estado

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- **`matchParentSize` no se importa**: es miembro de `BoxScope` (disponible dentro del `Box { }`); el import fallaba con `Unresolved reference`. Se quitó.
- **`dayLabel` agregado a `AgendaCalculator`** (no estaba en la lista de T5): el encabezado "Cobros del 21 de septiembre" se arma ahí, puro y testeado, en vez de formatear fechas dentro del composable. `monthLabel` también vive en la calculadora (el ViewModel solo lo llama).
- **`onDayClick` lee `uiState.value.cells`** para saber si el día tiene cobros; con `WhileSubscribed` eso solo es válido mientras hay un colector (siempre el caso en la pantalla; los tests lo recolectan con `launch` del scope del test, no `backgroundScope`).
- **`installmentStatusLabelAndColor` pasó de `private` a `internal`** en `OrderDetailScreen.kt` para reutilizarla en la fila de la agenda (sin cambio de comportamiento).
- **Semantics de la celda sin verificar en dispositivo**: `Modifier.clickable(role = Role.Button, ...)` seguido de `clearAndSetSemantics { contentDescription = ... }` en el mismo nodo — patrón habitual, pero que TalkBack anuncie exactamente "Lunes 29 de junio, 3 cobros pendientes" y siga ofreciendo la acción de clic solo puede confirmarse en un dispositivo real.
- **Sin cambios en `SaleRepository` más allá de un método**, `PullService`, `SyncManager`, 5.1 ni 5.2, ni backend.
- Comandos: `./gradlew testDebugUnitTest --tests SaleRepositoryTest` (tras T1-T4), `--tests "...agenda.*"` (tras T5-T8), `./gradlew compileDebugKotlin compileDebugUnitTestKotlin`, `./gradlew testDebugUnitTest assembleDebug` (suite completa, 405 tests, 0 fallos; build exitoso).

### Completion Notes List

- T1-T4: `InstallmentDao.observeAgendaForTenant` (join `installments`→`sales`→`clients`, solo `pending`, venta `pending`/`partial`) + `AgendaRow`; `FakeInstallmentDao.setSaleContext` para simular el join; `AgendaEntry` y `SaleRepository.getAgendaForTenant`; `TimeModule` provee `Clock` (no existía). +1 test en `SaleRepositoryTest`.
- T5-T6: `AgendaCalculator` puro (`groupByDay` por fecha LOCAL, `buildMonthGrid` con relleno inicial por primer día de semana y `hasAlert` en `[hoy, hoy+días]`, `dayDescription` con singular/plural y el aviso extra de accesibilidad cuando hay alerta, `dayLabel`, `monthLabel`). 11 tests, incluidos los strings exactos en es-MX ("Lunes 29 de junio, 3 cobros pendientes").
- T7-T8: `AgendaViewModel` (combina cobros del tenant + `observeDiasAnticipacion` + mes + día seleccionado; `Clock` inyectado; `isOverdue` con la misma regla que `Installment.toUiStatus`). 10 tests con `Clock.fixed` (lunes 21 de septiembre de 2026, 09:00 CDMX).
- T9: `AgendaScreen` (S-10): cabecera de mes con "Mes anterior"/"Mes siguiente", encabezados de día decorativos, cuadrícula de 7 columnas armada con `Column`/`Row`, celdas de ≥48dp (padding horizontal de pantalla 8dp), fondo distinto + conteo superpuesto + campana, lista del día con estado por texto y color, empty state exacto del AC-6, sin banner propio de offline.
- T10-T11: `Routes.Agenda`, `NavGraph` (S-02 → S-10 → S-09), y `OrderListScreen` gana su primer `TopAppBar` ("Órdenes") con el ícono "Agenda de cobros".
- Ronda de revisión (`bmad-code-review`, 3 capas): 2 decisiones (mantener el sufijo de TalkBack como desviación aceptada del AC-3; agregar el aviso de vencidos en meses anteriores), 7 patches aplicados, 5 diferidos, 6 descartados. Archivos tocados en la ronda: `AgendaCalculator`, `AgendaViewModel`, `AgendaScreen`, `TimeModule` y sus tests (+11 tests: total final **416, 0 fallos**, `assembleDebug` exitoso).
- T12: `./gradlew testDebugUnitTest assembleDebug` → 405 tests, 0 fallos, build exitoso (antes de la ronda de revisión). **PENDIENTE (no verificable aquí, sin `adb`/emulador)**: apariencia de la cuadrícula, tamaños táctiles a 360dp, anuncio de TalkBack por celda, que el `TopAppBar` nuevo de S-02 no duplique el padding de la barra de estado, y la navegación S-02 → S-10 → S-09. `AgendaScreen` no tiene tests (no hay suite de Compose en el proyecto); la lógica vive en `AgendaCalculator`/`AgendaViewModel`, cubiertos.

### File List

**Nuevos:**
- `android/app/src/main/java/com/sumitrack/android/domain/models/AgendaEntry.kt`
- `android/app/src/main/java/com/sumitrack/android/di/TimeModule.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/agenda/AgendaCalculator.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/agenda/AgendaViewModel.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/agenda/AgendaScreen.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/agenda/AgendaCalculatorTest.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/agenda/AgendaViewModelTest.kt`

**Modificados:**
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/InstallmentDao.kt` (`AgendaRow`, `observeAgendaForTenant`)
- `android/app/src/main/java/com/sumitrack/android/data/repositories/SaleRepository.kt` (`getAgendaForTenant`)
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/Routes.kt` (`Routes.Agenda`)
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/NavGraph.kt` (ruta de S-10, `onAgendaClick`)
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderListScreen.kt` (app bar con ícono de agenda)
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderDetailScreen.kt` (`installmentStatusLabelAndColor` → `internal`)
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/FakeInstallmentDao.kt` (`observeAgendaForTenant`, `setSaleContext`)
- `android/app/src/test/java/com/sumitrack/android/data/repositories/SaleRepositoryTest.kt` (+1 test)

## Change Log

| Fecha | Cambio |
|-------|--------|
| 2026-09-21 | Historia creada (`bmad-create-story`), status: ready-for-dev |
| 2026-09-21 | Implementación completa (T1-T12), status: review |
| 2026-09-21 | Code review (3 capas + 2 decisiones del usuario) — 7 patches aplicados, 5 diferidos, 6 descartados. Status: done |

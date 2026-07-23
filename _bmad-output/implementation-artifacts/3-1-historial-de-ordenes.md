---
baseline_commit: cab7e0dbde8185e3a39c742ab442b3f97bfc8c4d
---

# Story 3.1: Historial de Órdenes

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

Como proveedor,
quiero ver todas mis ventas en una lista con su estado actual,
para que pueda consultar en segundos el panorama completo de mis créditos activos.

## Acceptance Criteria

**AC-1 — Lista de órdenes (S-02)**

**Dado** que el proveedor navega al tab Órdenes
**Cuando** S-02 se muestra
**Entonces** la lista de `OrderCard` muestra las ventas en orden cronológico descendente; cada card incluye folio (body-small/primary-variant), fecha (body-small/on-surface-variant), nombre del cliente (body-large), monto total (title-medium/primary), `StatusBadge` e `SyncIcon`; hay `SearchBar` con `FilterChipRow` por estado; FAB "Nueva Orden" visible

**AC-2 — Filtro por estado**

**Dado** que el proveedor activa un chip de filtro
**Cuando** selecciona un estado
**Entonces** el chip activo muestra fondo primary-variant + texto blanco + leadingIcon checkmark; la lista se filtra mostrando solo ventas de ese estado

**AC-3 — Sin órdenes**

**Dado** que el proveedor no tiene ventas registradas
**Cuando** S-02 se muestra
**Entonces** aparece empty state: "Aún no hay órdenes. Toca + para empezar." con FAB visible

**AC-4 — Navegación a detalle**

**Dado** que el proveedor toca una `OrderCard`
**Cuando** se registra el toque
**Entonces** navega a S-09 (Detalle de Orden); la card implementa `semantics(mergeDescendants = true)` para TalkBack

### Fuera de alcance en esta historia (explícito)

- **S-09 (Detalle de Orden)** — Historia 3.5, no existe todavía. `OrderCard.onClick` muestra un Snackbar placeholder ("Detalle de orden — disponible próximamente"), mismo patrón que `ClientCard.onClick` en Historia 2.1 antes de que Historia 2.3 construyera S-12.
- **S-03 (Selección de Cliente e Ítems en Nueva Orden)** — Historia 3.2, no existe todavía. El FAB "Nueva Orden" muestra el mismo tipo de Snackbar placeholder ("Nueva orden — disponible próximamente"), mismo patrón que el FAB de `ClientListScreen` antes de Historia 2.2.
- **Filtro "Atraso"** (mencionado como ejemplo en el AC original de `epics.md`, `status-overdue`) — determinar si una venta está "vencida" requiere una fecha de vencimiento por parcialidad (FR-13/FR-14), que vive en `Installment` — **no existe todavía** (Historia 3.3+). Los 4 chips de `FilterChipRow` en esta historia son los 4 valores reales de `SaleStatus` (Pendiente, Parcial, Pagada, Cancelada) — no hay chip "Atraso". Mismo criterio que Historia 2.3 aplicó al banner de deuda vencida en S-12.
- **Filtro por fecha (date picker)** — mencionado en `EXPERIENCE.md` (`UX-DR10`: "el filtro por fecha abre un date picker") pero no forma parte del AC-2 de `epics.md` (que solo pide `FilterChipRow` "por estado"). No implementar.
- **Ícono de calendario en la app bar hacia S-10 (Agenda de Cobros)** — mencionado en `EXPERIENCE.md` como parte de la descripción de S-02, pero no está en ningún AC de esta historia; S-10 es Historia 5.3 (Epic 5), no existe todavía.
- **Colapso del FAB en scroll** (`UX-DR8`: "se colapsa a FAB circular en scroll down; reaparece en scroll up") — el AC-1 solo exige "FAB visible", no el comportamiento de colapso. Se usa el mismo `ExtendedFloatingActionButton` siempre visible que `ClientListScreen`/`ProductListScreen`, para mantener consistencia con el resto de la app en vez de introducir un patrón de interacción nuevo sin AC que lo exija.

## Tasks / Subtasks

### Android — Proyección Sale+Client para la lista

- [x] **T1: `OrderSummary` (dominio) y `OrderSummaryRow` (proyección Room)** (AC-1)
  - [x] Crear `domain/models/OrderSummary.kt`:
    ```kotlin
    data class OrderSummary(
        val id: String,
        val folio: String,
        val clientName: String,
        val total: BigDecimal,
        val status: SaleStatus,
        val createdAt: Instant,
        val syncStatus: SyncStatus,
    )
    ```
    Es un modelo de **proyección para la lista** (Sale + nombre de cliente resuelto), distinto del `Sale` de dominio existente (que no incluye `clientName`). No modificar `Sale.kt` — `OrderSummary` es exclusivamente para S-02.
  - [x] Agregar `OrderSummaryRow` (data class, no `@Entity`) en el mismo archivo `data/local/dao/SaleDao.kt` — mismo criterio de co-ubicación que `StatusBadge.kt` (que agrupa `StatusBadge` + `SaleUiStatus`):
    ```kotlin
    data class OrderSummaryRow(
        val id: String,
        val folio: String,
        val total: BigDecimal,
        val status: String,
        val clientName: String,
        val createdAt: Instant,
        val syncStatus: String,
    )
    ```

- [x] **T2: `SaleDao` — query con JOIN a `clients`** (AC-1, AC-2)
  - [x] Agregar a `data/local/dao/SaleDao.kt`:
    ```kotlin
    @Query(
        "SELECT s.id AS id, s.folio AS folio, s.total AS total, s.status AS status, " +
            "c.name AS clientName, s.created_at AS createdAt, s.sync_status AS syncStatus " +
            "FROM sales s INNER JOIN clients c ON c.id = s.fk_client " +
            "WHERE s.fk_tenant = :tenantId " +
            "AND (:statusFilter IS NULL OR s.status = :statusFilter) " +
            "AND (:normalizedQuery = '' OR LOWER(s.folio) LIKE '%' || :normalizedQuery || '%' ESCAPE '\\' " +
            "OR REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(c.name)," +
            "'á','a'),'Á','a'),'é','e'),'É','e'),'í','i'),'Í','i'),'ó','o'),'Ó','o'),'ú','u'),'Ú','u'),'ü','u'),'Ü','u'),'ñ','n'),'Ñ','n') " +
            "LIKE '%' || :normalizedQuery || '%' ESCAPE '\\') " +
            "ORDER BY s.created_at DESC"
    )
    fun getOrdersForTenantAsFlow(
        tenantId: String,
        statusFilter: String?,
        normalizedQuery: String,
    ): Flow<List<OrderSummaryRow>>
    ```
    **Primer JOIN cross-entidad del proyecto** — todos los DAOs existentes son de una sola tabla. Se resuelve así (en vez de componer `SaleRepository` + `ClientDao.getAllAsFlow()` en Kotlin) porque `ClientDao.getAllAsFlow()` **no filtra por tenant** (gap preexistente documentado en `deferred-work.md` desde Historia 2.1/2.2) — usarlo aquí filtrando después en memoria repetiría exactamente el tipo de fuga multi-tenant que el code review de Historia 2.4 corrigió. El `JOIN` con `WHERE s.fk_tenant = :tenantId` es seguro por construcción: una venta solo puede referenciar un cliente del mismo tenant (invariante del modelo de datos, AR-2), así que no hace falta repetir el filtro de tenant también sobre `c.name`.
    El folio se compara con `LOWER()` simple (sin acentos) porque su formato es `{serie}{número}` (AR-16, ej. "A1"), siempre ASCII. El nombre del cliente reutiliza el mismo *REPLACE chain* de plegado de acentos que `ClientDao.searchByNameAsFlow` — no crear una función SQL nueva, copiar el patrón exacto.
    `TypeConverters` (`BigDecimalConverter`/`InstantConverter`) ya están registrados a nivel `@Database` (`SumitrackDatabase.kt`), aplican automáticamente a proyecciones `@Query` igual que a entidades — no requiere anotación adicional en `OrderSummaryRow`.

- [x] **T3: `SaleRepository.getOrdersForTenant`** (AC-1, AC-2)
  - [x] Agregar a `data/repositories/SaleRepository.kt` (sin tocar `getOpenSalesForClient`, que sigue igual):
    ```kotlin
    fun getOrdersForTenant(tenantId: String, statusFilter: SaleStatus?, searchQuery: String): Flow<List<OrderSummary>> =
        saleDao.getOrdersForTenantAsFlow(
            tenantId = tenantId,
            statusFilter = statusFilter?.name?.lowercase(),
            normalizedQuery = SearchNormalizer.toLikePattern(searchQuery),
        ).map { rows -> rows.map { it.toDomain() } }

    private fun OrderSummaryRow.toDomain() = OrderSummary(
        id = id, folio = folio, clientName = clientName, total = total,
        status = SaleStatus.fromString(status), createdAt = createdAt,
        syncStatus = SyncStatus.fromString(syncStatus),
    )
    ```
    Reutilizar `SearchNormalizer.toLikePattern` (`data/local/SearchNormalizer.kt`, ya usado por `ClientRepository`) — no crear un normalizador nuevo. Cuando `searchQuery` está en blanco, `toLikePattern("")` produce `""`, que la condición `:normalizedQuery = ''` de T2 trata como "sin filtro de texto".

### Android — UI S-02

- [x] **T4: `OrderCard.kt`** (AC-1, AC-4) — nuevo componente, ya anticipado en `architecture.md` pero no creado en historias previas
  - [x] Crear `ui/components/OrderCard.kt` según `UX-DR5`: tres filas dentro de un `ElevatedCard` (mismo `elevation = 1.dp`, `shape = MaterialTheme.shapes.medium`, `semantics(mergeDescendants = true)` que `ClientCard`/`ProductCard`):
    1. `Row`: folio (`bodySmall`, color `PrimaryVariant` — **token directo, no `colorScheme.primaryContainer`**; ver Historia 2.3 code review donde ese mismo error se corrigió después, aplicarlo bien desde el inicio aquí) + fecha formateada (`bodySmall`, `colorScheme.onSurfaceVariant`) en extremos opuestos (`Arrangement.SpaceBetween`).
    2. Nombre del cliente (`bodyLarge`).
    3. `Row`: monto formateado (`titleMedium`, `colorScheme.primary`, misma fórmula `"$" + total.setScale(2, RoundingMode.HALF_UP).toPlainString()` que `ClientCard`/`ProductCard` — función local `formatTotal`, **no** reutilizar la de otro archivo, es `private`) + `StatusBadge` + `SyncIcon`, alineados a la derecha.
    - Formateo de fecha: `Instant` → texto local. No existe todavía ningún formateador de fechas en el proyecto (primera vez que se muestra una fecha en UI). Función local `private fun formatDate(instant: Instant): String` usando `DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "MX")).format(instant.atZone(ZoneId.systemDefault()))`.
    - Mapeo `SaleStatus` (dominio) → `SaleUiStatus` (presentación, de `StatusBadge.kt`): `PENDING`/`PARTIAL` → `SaleUiStatus.PARTIAL` (mismo criterio que Historia 2.3 T5 — sin datos de vencimiento no hay forma de distinguir "vencido" de "no vencido", así que ambos se muestran como "Parcialidades"); `PAID` → `SaleUiStatus.PAID`; `CANCELLED` → `SaleUiStatus.CANCELLED`.
    ```kotlin
    @Composable
    fun OrderCard(order: OrderSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
        ElevatedCard(
            onClick = onClick,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            shape = MaterialTheme.shapes.medium,
            modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(order.folio, style = MaterialTheme.typography.bodySmall, color = PrimaryVariant)
                    Text(formatDate(order.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(order.clientName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text(
                        formatTotal(order.total), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
                    )
                    StatusBadge(order.status.toUiStatus())
                    SyncIcon(isSynced = order.syncStatus == SyncStatus.SYNCED)
                }
            }
        }
    }
    ```

- [x] **T5: `OrderListViewModel`** (AC-1, AC-2, AC-3)
  - [x] Crear `ui/screens/orders/OrderListViewModel.kt` — mismo esqueleto que `ClientListViewModel` (`debounce(200)` + `flatMapLatest` + `catch { emit(emptyList()) }` + `stateIn(WhileSubscribed(5_000))`), extendido con `@TenantId` (aprendizaje del code review de Historia 2.4: toda query nueva debe nacer aislada por tenant, no agregarse después) y un filtro de estado adicional:
    ```kotlin
    @HiltViewModel
    class OrderListViewModel @Inject constructor(
        private val saleRepository: SaleRepository,
        @TenantId private val tenantId: Flow<String?>,
    ) : ViewModel() {

        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        private val _statusFilter = MutableStateFlow<SaleStatus?>(null)
        val statusFilter: StateFlow<SaleStatus?> = _statusFilter.asStateFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        val orders: StateFlow<List<OrderSummary>> = combine(
            tenantId, _searchQuery.debounce(200), _statusFilter,
        ) { tenant, query, filter -> Triple(tenant, query, filter) }
            .flatMapLatest { (tenant, query, filter) ->
                if (tenant.isNullOrBlank()) flowOf(emptyList())
                else saleRepository.getOrdersForTenant(tenant, filter, query)
            }
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

        fun onSearchQueryChange(query: String) { _searchQuery.value = query }
        fun onSearchClear() { _searchQuery.value = "" }
        fun onStatusFilterSelected(status: SaleStatus?) { _statusFilter.value = status }
    }
    ```
    `FilterChipRow.onChipSelected` ya tiene la semántica de toggle (`if (isSelected) null else chip.id`, ver `FilterChipRow.kt`), así que `onStatusFilterSelected` recibe `null` cuando el proveedor deselecciona el chip activo — no requiere lógica adicional de toggle en el ViewModel.

- [x] **T6: `OrderListScreen`** (AC-1, AC-2, AC-3, AC-4) — reemplaza el placeholder actual de Historia 1.4
  - [x] Reescribir `ui/screens/orders/OrderListScreen.kt` — mismo esqueleto que `ClientListScreen.kt` (`Scaffold` + `SearchBar` M3 con `SearchBarDefaults.InputField` + `FilterChipRow` + `PullToRefreshBox(isRefreshing = false, onRefresh = { /* TODO Historia 4.x: trigger sync */ })` + `LazyColumn`/`EmptyState` + FAB):
    - `SearchBar`: placeholder `"Buscar por folio o cliente..."` (a diferencia de `ClientListScreen` que solo busca por nombre — aquí el AC no especifica los campos de búsqueda, se resuelve por analogía cubriendo folio + nombre de cliente, los dos campos visibles en `OrderCard`).
    - `FilterChipRow`: **primera activación real del componente en el proyecto** — hasta ahora `ClientListScreen` lo renderiza con `chips = emptyList()` (placeholder inerte desde Historia 2.1). Aquí: `chips = listOf(FilterChipData(SaleStatus.PENDING, "Pendiente"), FilterChipData(SaleStatus.PARTIAL, "Parcial"), FilterChipData(SaleStatus.PAID, "Pagada"), FilterChipData(SaleStatus.CANCELLED, "Cancelada"))`, `selectedChip = statusFilter`, `onChipSelected = viewModel::onStatusFilterSelected`.
    - Empty state: si `orders.isEmpty()` y `searchQuery`/`statusFilter` están ambos inactivos → mensaje exacto del AC-3 ("Aún no hay órdenes. Toca + para empezar."); si hay búsqueda/filtro activo y no hay resultados → "No se encontraron órdenes con esos criterios." (mismo patrón de mensaje dual que `ClientListScreen`). Ícono `Icons.Outlined.ReceiptLong`.
    - `LazyColumn` de `OrderCard(order, onClick = { snackbarHostState... })` — el `onClick` muestra el Snackbar placeholder de "Fuera de alcance" (S-09 no existe).
    - FAB `ExtendedFloatingActionButton("+", contentDescription = "Nueva Orden")` — `onClick` muestra el Snackbar placeholder de "Fuera de alcance" (S-03 no existe). Texto exacto del `contentDescription` tomado de `UX-DR8`.

### Android — Wiring

- [x] **T7: Confirmar wiring en `NavGraph.kt`** (AC-1)
  - [x] `composable(Routes.Orders.route) { OrderListScreen() }` ya existe (Historia 1.4) — no requiere cambios, `OrderListScreen` ahora tiene contenido real en vez del placeholder de texto. No agregar rutas nuevas (S-03/S-09 no se navegan todavía, solo Snackbars).

### Review Findings

- [x] [Review][Patch] `INNER JOIN` en `getOrdersForTenantAsFlow` oculta silenciosamente cualquier venta cuyo `fk_client` no tenga fila correspondiente en `clients` — contradice el "todas mis ventas" de AC-1 en el caso de un registro huérfano. **Resuelto:** `LEFT JOIN` + `COALESCE(c.name, '(cliente eliminado)')`; `FakeSaleDao` actualizado para el mismo fallback; test `falls back to a placeholder name for an orphaned client` agregado. [SaleDao.kt, FakeSaleDao.kt, SaleRepositoryTest.kt]
- [x] [Review][Patch] Búsqueda con solo espacios en blanco no se trata igual en la UI que en SQL. **Resuelto:** `SaleRepository.getOrdersForTenant` recorta (`trim()`) el `searchQuery` antes de normalizarlo. [SaleRepository.kt]
- [x] [Review][Patch] `ORDER BY s.created_at DESC` sin desempate. **Resuelto:** agregado `, s.id DESC` como criterio secundario; mismo ajuste reflejado en `FakeSaleDao` (`sortedWith(compareByDescending { createdAt }.thenByDescending { id })`). [SaleDao.kt, FakeSaleDao.kt]
- [x] [Review][Patch] Los dos tests de búsqueda en `SaleRepositoryTest` solo probaban que una venta coincidente aparecía, no que una no-coincidente fuera excluida; faltaba un caso combinando `statusFilter`+`searchQuery`. **Resuelto:** ambos tests reescritos para sembrar una venta coincidente y una no-coincidente y asertar exclusión; agregado `combines statusFilter and searchQuery`. [SaleRepositoryTest.kt]
- [x] [Review][Patch] `statusFilter?.name?.lowercase()` sensible a locale. **Resuelto:** `lowercase(Locale.ROOT)`. [SaleRepository.kt]
- [x] [Review][Defer] `.catch { emit(emptyList()) }` en `OrderListViewModel` traga cualquier error silenciosamente y, por semántica de `Flow.catch` + `stateIn(WhileSubscribed)`, un error real puede dejar el `StateFlow` sin volver a actualizarse hasta que todos los colectores se desconecten y reconecten. Mismo patrón exacto ya usado en `ClientListViewModel` (Historia 2.1) y `ProductListViewModel` (Historia 2.4) — no introducido por esta historia, requiere una pasada dedicada por las 3 ViewModels. [OrderListViewModel.kt, ClientListViewModel.kt, ProductListViewModel.kt] — deferred, pre-existing
- [x] [Review][Defer] `BackHandler` al colapsar la búsqueda no llama `onSearchClear()` (dejando un filtro de búsqueda invisible activo), y el contenido expandido del `SearchBar` es una lambda vacía `{ }` — ambos son copia exacta del patrón ya existente en `ClientListScreen` desde Historia 2.1, no introducidos por esta historia. Verificar en dispositivo real si el overlay expandido realmente oculta la lista (sospecha del Blind Hunter, no confirmable sin infraestructura de test de Composables). [OrderListScreen.kt, ClientListScreen.kt] — deferred, pre-existing
- [x] [Review][Defer] `getOrdersForTenantAsFlow` no tiene `LIMIT`/paginación — se re-ejecuta en cada tecleo (tras debounce) con un `JOIN` + cadena de 14 `REPLACE()` anidados y un `LIKE` con comodín inicial (no indexable). Sin costo real hoy por volumen de datos bajo; mismo criterio que los índices ya diferidos de `sales`/`clients`/`products` en historias previas. [SaleDao.kt] — deferred, pre-existing
- [x] [Review][Defer] `FakeSaleDao.getOrdersForTenantAsFlow` compara el `normalizedQuery` ya escapado (con `\` insertado antes de `%`/`_`) contra nombres sin desescapar — diverge del comportamiento real de `LIKE ... ESCAPE '\'` si un nombre de cliente contuviera literalmente `%` o `_`. Gap de fidelidad del fake, sin impacto en producción (la query SQL real sí es correcta). [FakeSaleDao.kt] — deferred, pre-existing

## Dev Notes

### Por qué esta historia hace el primer JOIN cross-entidad del proyecto (y por qué no un `ClientDao` sin scope de tenant)

Ver T2 para el razonamiento completo. Resumen: `ClientDao.getAllAsFlow()` no filtra por tenant (deferred desde Historia 2.1/2.2, documentado en `deferred-work.md`). Componer `SaleRepository` con `ClientDao` directamente para resolver `clientName` habría requerido usar esa query sin scope, repitiendo la fuga multi-tenant que el code review de Historia 2.4 ya corrigió para `Product*`. El `INNER JOIN` en `SaleDao` con `WHERE s.fk_tenant = :tenantId` es tenant-seguro por construcción sin tocar `ClientDao` (fuera de alcance de esta historia arreglar ese deferred).

### Aprendizajes aplicados desde el code review de Historia 2.4

- **Aislamiento por tenant desde el día uno** — `OrderListViewModel` recibe `@TenantId` igual que `ProductListViewModel`, no se agrega después de un hallazgo de review.
- **Color de folio con token directo** (`PrimaryVariant`, no `colorScheme.primaryContainer`) — mismo patch que se aplicó en Historia 2.3 después del review, aplicado bien desde el inicio en `OrderCard`.

### Primera activación real de `FilterChipRow`

El componente existe desde Historia 2.1 pero `ClientListScreen` lo renderiza con `chips = emptyList()` — nunca tuvo datos reales. Esta historia es la primera vez que se conecta con contenido real (los 4 `SaleStatus`). Si `FilterChipRow.kt` necesita ajustes de comportamiento no anticipados al usarlo con datos reales, documentarlo aquí como desviación — no se anticipa ninguno por lectura de su código actual (single-select con toggle ya implementado).

### Archivos existentes relevantes — estado actual y cambio en esta historia

| Archivo | Estado actual | Cambio en esta historia |
|---------|---------------|--------------------------|
| `OrderListScreen.kt` | Placeholder `Text("Órdenes — Historia 1.4")` (Historia 1.4) | Reemplazado por la pantalla real S-02 |
| `SaleDao.kt` | `getOpenSalesForClient`, `upsertAll` (Historia 2.3) | + `getOrdersForTenantAsFlow`, + `OrderSummaryRow` |
| `SaleRepository.kt` | `getOpenSalesForClient` (Historia 2.3) | + `getOrdersForTenant` |
| `FilterChipRow.kt` | Existe desde Historia 2.1, sin uso real | Sin cambios de código — primera vez con datos reales |
| `StatusBadge.kt` | 4 estados completos desde Historia 2.3 | Sin cambios — se reutiliza tal cual |

**NO tocar:**
- `ClientDao.kt` — el deferred de scope de tenant sigue abierto, fuera de alcance de esta historia (ver razonamiento arriba).
- `Sale.kt`, `SaleStatus.kt`, `CalculateClientBalanceUseCase.kt`, `ClientProfileViewModel.kt` — sin relación con esta historia.
- `ClientCard.kt`, `ProductCard.kt` — solo se usan como referencia de patrón para `OrderCard`, no se modifican.
- No crear `Installment.kt`/`Payment.kt` ni ningún campo de fecha de vencimiento — eso es Historia 3.3+.

### Testing

Mismo patrón establecido en Historias 2.1-2.4: **sin Robolectric**, tests JVM puros con Fake DAOs, correr con el JDK de Android Studio (`./gradlew :app:testDebugUnitTest`).

- **`FakeSaleDao`** (existente, `test/java/.../ui/screens/clients/FakeSaleDao.kt`) — extender para implementar `getOrdersForTenantAsFlow`. Cambiar el almacenamiento interno de `mutableMapOf<String, SaleEntity>` a `MutableStateFlow<List<SaleEntity>>` (mismo patrón que `FakeProductDao`) — cambio de implementación interna sin alterar el comportamiento observable de `getOpenSalesForClient`/`upsertAll`/`setSales`/`throwFromCallNumber` (siguen funcionando igual para los tests existentes de Historias 2.2-2.4 que ya usan este fake). Agregar `private val clientNames = mutableMapOf<String, String>()` + `fun setClientNames(names: Map<String, String>)`, usados solo por la nueva implementación de `getOrdersForTenantAsFlow` (filtra por `fkTenant`, `status`, y hace match de `folio`/nombre normalizado contra `searchQuery`, ordena por `createdAt` descendente).
- **`SaleRepositoryTest`** (existente) — agregar casos para `getOrdersForTenant`: mapea correctamente incluyendo `clientName`; filtra por `statusFilter` cuando no es null; excluye ventas de otro tenant; búsqueda por folio y por nombre de cliente (con y sin acentos, ej. "Perez" encuentra "Pérez"); lista vacía cuando no hay ventas.
- **`OrderListViewModelTest`** (nuevo, `test/java/.../ui/screens/orders/`) — con `FakeSaleDao`: `orders` emite lista vacía inicialmente; refleja las ventas del tenant activo (excluye otro tenant); `onStatusFilterSelected` filtra correctamente; `onSearchQueryChange` filtra por folio y por nombre (con debounce, usar `advanceUntilIdle()` tras `advanceTimeBy(200)` o `advanceUntilIdle()` directo como en `ClientListViewModelTest`); `tenantId` nulo → lista vacía.
- Sin test de Composable/UI (`OrderCard`/`OrderListScreen`) — mismo criterio que historias previas, el proyecto no tiene infraestructura de test de Composables.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Historia 3.1: Historial de Órdenes] (líneas 479-501) — AC originales
- [Source: _bmad-output/planning-artifacts/epics.md#UX Design Requirements] UX-DR5 (`OrderCard`), UX-DR7 (`StatusBadge`, ya completo), UX-DR8 (FAB, colapso fuera de alcance), UX-DR9 (`NavigationBar`, sin cambios), UX-DR10 (`SearchBar`/`FilterChipRow`, filtro por fecha fuera de alcance), UX-DR16 (empty states), UX-DR21 (pull-to-refresh)
- [Source: _bmad-output/planning-artifacts/architecture/architecture.md] árbol Android (líneas 348, 379): `OrderListScreen.kt` (S-02), `OrderCard.kt` ya anticipados en la estructura
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/EXPERIENCE.md#S-02 — Historial de Órdenes] (líneas 50, 203) microcopia empty state
- [Source: _bmad-output/implementation-artifacts/2-4-catalogo-de-productos-y-variantes.md] aprendizajes del code review: aislamiento por tenant desde el diseño inicial, no post-hoc
- [Source: _bmad-output/implementation-artifacts/2-3-perfil-de-cliente-con-saldo-y-ordenes-abiertas.md] patrón de mapeo `SaleStatus`→`SaleUiStatus` (Pendiente/Parcial ambos como "Parcialidades" sin datos de vencimiento); patch de `PrimaryVariant` directo
- [Source: android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientListScreen.kt, ClientListViewModel.kt] patrón de `SearchBar`+`FilterChipRow`+`PullToRefreshBox`+empty state dual a replicar
- [Source: android/app/src/main/java/com/sumitrack/android/data/local/dao/ClientDao.kt] patrón exacto del *REPLACE chain* de plegado de acentos a reutilizar en la nueva query de `SaleDao`
- [Source: android/app/src/main/java/com/sumitrack/android/data/local/SearchNormalizer.kt] normalizador reutilizado sin cambios

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (`claude-sonnet-5`)

### Debug Log References

Sin desviaciones respecto a lo especificado en Tasks/Subtasks — el diseño del JOIN cross-entidad, el mapeo `SaleStatus`→`SaleUiStatus`, y los placeholders Snackbar ya estaban resueltos por adelantado en la historia. Único ajuste no anticipado: el compilador marcó 3 warnings de deprecación (`Locale(String, String)`, `Icons.Outlined.ReceiptLong`, `debounce` sin `@OptIn(FlowPreview::class)`) — se corrigieron con las alternativas no deprecadas (`Locale.forLanguageTag("es-MX")`, `Icons.AutoMirrored.Outlined.ReceiptLong`, agregar `FlowPreview::class` al `@OptIn` existente) para dejar la compilación sin warnings, consistente con el resto del proyecto.

### Completion Notes List

Historia implementada completa + code review con 5 patches aplicados (0 decisiones necesarias). 126 tests ✅ (0 fallos, +17 sobre Historia 2.4). `BUILD SUCCESSFUL` (`assembleDebug` + `testDebugUnitTest`, sin warnings, JDK de Android Studio).

- AC-1 ✅: `OrderListScreen` (S-02) lista `OrderCard` en orden cronológico descendente (`ORDER BY s.created_at DESC, s.id DESC` tras el review), cada card con folio (`PrimaryVariant`), fecha formateada, nombre de cliente, monto, `StatusBadge` y `SyncIcon`. `SearchBar` (folio o cliente) + `FilterChipRow` (primera activación real del componente, hasta ahora inerte desde Historia 2.1) + FAB "Nueva Orden".
- AC-2 ✅: `FilterChipRow` con los 4 `SaleStatus` reales (Pendiente/Parcial/Pagada/Cancelada); selección filtra la lista vía `OrderListViewModel.onStatusFilterSelected` → query de Room con `statusFilter`. Toggle (deseleccionar) ya venía resuelto por `FilterChipRow.onChipSelected`.
- AC-3 ✅: empty state distingue "Aún no hay órdenes. Toca + para empezar." (sin búsqueda/filtro activo) de "No se encontraron órdenes con esos criterios." (con búsqueda/filtro activo y cero resultados) — mismo patrón dual que `ClientListScreen`.
- AC-4 ✅: `OrderCard` implementa `semantics(mergeDescendants = true)`; `onClick` muestra Snackbar placeholder ("Detalle de orden — disponible próximamente") ya que S-09 no existe (Historia 3.5, fuera de alcance documentado).
- **Primer JOIN cross-entidad del proyecto** (`SaleDao.getOrdersForTenantAsFlow`, `sales LEFT JOIN clients` tras el review) — resuelto así en vez de componer `SaleRepository`+`ClientDao` en Kotlin, precisamente para no heredar el gap de `ClientDao.getAllAsFlow()` sin scope de tenant (deferred desde Historia 2.1/2.2). Aislamiento por tenant aplicado desde el diseño inicial, aprendizaje directo del code review de Historia 2.4. `LEFT JOIN` + `COALESCE` (en vez del `INNER JOIN` original) blinda contra un `fk_client` huérfano, aunque hoy inalcanzable.
- `OrderCard` usa el token `PrimaryVariant` directo para el folio (no `colorScheme.primaryContainer`) — mismo patch que Historia 2.3 tuvo que corregir después de su code review, aplicado bien desde el inicio aquí.
- **Code review** (2026-07-19): 3 agentes en paralelo (Blind Hunter, Edge Case Hunter, Acceptance Auditor) → 0 decisiones, 5 patches aplicados (`LEFT JOIN`+`COALESCE` para clientes huérfanos, `trim()` de búsqueda antes de normalizar, desempate `s.id DESC` en el orden, tests de búsqueda reforzados para probar exclusión real, `Locale.ROOT` en `lowercase()`). El Acceptance Auditor no encontró violaciones de AC. 8 hallazgos descartados por coincidir con patrones ya existentes desde Historia 2.1 sin cambios (FAB "+", formato de moneda, `SyncIcon`, `semantics` vacío, etc.). 4 hallazgos diferidos a `deferred-work.md`, el más relevante: el patrón `.catch { emit(emptyList()) }` ahora se repite en 3 ViewModels (`ClientListViewModel`/`ProductListViewModel`/`OrderListViewModel`) y requiere una pasada dedicada.
- Sin test de Composable/UI (`OrderCard`/`OrderListScreen`) — mismo criterio que historias previas, el proyecto no tiene infraestructura de test de Composables.
- **Pendiente:** verificación manual en emulador/dispositivo físico — este entorno no tiene `adb` ni emulador Android disponible (mismo pendiente que en Historias 2.1-2.4). Recomendado antes de mergear: tab Órdenes → confirmar lista/orden cronológico con datos de prueba → activar un chip de filtro → confirmar filtrado → buscar por folio y por nombre → confirmar empty states (sin datos, y con filtro/búsqueda sin resultados) → tocar FAB y una card → confirmar Snackbars placeholder → **verificar si el overlay expandido del `SearchBar` oculta la lista** (sospecha del code review, ver deferred-work.md).

### File List

**Archivos creados (NEW):**
- `android/app/src/main/java/com/sumitrack/android/domain/models/OrderSummary.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/components/OrderCard.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderListViewModel.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/OrderListViewModelTest.kt`

**Archivos modificados (UPDATE):**
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/SaleDao.kt` — + `OrderSummaryRow`, + `getOrdersForTenantAsFlow` (`LEFT JOIN` + `COALESCE`, orden con desempate)
- `android/app/src/main/java/com/sumitrack/android/data/repositories/SaleRepository.kt` — + `getOrdersForTenant` (`trim()`, `Locale.ROOT`)
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderListScreen.kt` — reemplaza el placeholder de Historia 1.4 por la pantalla real S-02
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/FakeSaleDao.kt` — almacenamiento interno migrado a `MutableStateFlow`, + `getOrdersForTenantAsFlow` (con fallback de cliente huérfano y desempate), + `setClientNames`
- `android/app/src/test/java/com/sumitrack/android/data/repositories/SaleRepositoryTest.kt` — + 8 casos de `getOrdersForTenant` (incluye reforzados post-review)
- `_bmad-output/implementation-artifacts/deferred-work.md` — 4 hallazgos diferidos agregados

## Change Log

- **2026-07-19** — Historia 3.1 implementada completa (Status: review)
  - NEW: `OrderSummary` (dominio), `OrderSummaryRow` + `getOrdersForTenantAsFlow` (primer JOIN cross-entidad del proyecto, `SaleDao`), `SaleRepository.getOrdersForTenant`
  - NEW: `OrderCard`, `OrderListViewModel`, `OrderListScreen` (reemplaza el placeholder de Historia 1.4) — primera activación real de `FilterChipRow`
  - UPDATE: `FakeSaleDao` extendido (`MutableStateFlow` interno, `getOrdersForTenantAsFlow`, `setClientNames`); `SaleRepositoryTest` +6 casos
  - NEW: `OrderListViewModelTest` (9 casos)
  - Build: 124 tests ✅ (0 fallos, +15 sobre Historia 2.4), `BUILD SUCCESSFUL` sin warnings (`assembleDebug` + `testDebugUnitTest`, JDK de Android Studio)
  - Pendiente: verificación manual en dispositivo/emulador (sin `adb` en este entorno); code review todavía no ejecutado

- **2026-07-19** — Code review: 0 decisiones + 5 patches aplicados (Status: review, sigue en review pendiente de próximos pasos)
  - PATCH: `SaleDao.getOrdersForTenantAsFlow` — `INNER JOIN` → `LEFT JOIN` + `COALESCE(c.name, '(cliente eliminado)')` para no ocultar ventas con `fk_client` huérfano
  - PATCH: `SaleRepository.getOrdersForTenant` — `searchQuery.trim()` antes de normalizar (evita que un query de solo espacios aplique un filtro SQL real mientras la UI lo trata como "sin búsqueda")
  - PATCH: `ORDER BY s.created_at DESC, s.id DESC` — desempate estable para timestamps idénticos; reflejado también en `FakeSaleDao`
  - PATCH: `lowercase(Locale.ROOT)` en `statusFilter?.name?.lowercase()` — evita sensibilidad a locale
  - PATCH: `SaleRepositoryTest` — los 2 tests de búsqueda reforzados para probar exclusión real (antes solo probaban que una venta coincidente aparecía); +2 casos nuevos (`combines statusFilter and searchQuery`, `falls back to a placeholder name for an orphaned client`)
  - Descartados (8): FAB con texto "+", formato de moneda sin separador de miles, locale es-MX fijo para fechas, `SyncIcon` colapsando estados, `debounce` sin test de timing preciso, pull-to-refresh inerte, `semantics` con lambda vacía, posible carrera en `FakeSaleDao.upsertAll` — todos coinciden con patrones ya existentes desde Historia 2.1/2.4, sin cambios
  - Deferred (4, ver `deferred-work.md`): `.catch { emit(emptyList()) }` ahora en 3 ViewModels (requiere pasada dedicada), `BackHandler`/`SearchBar` sin limpiar búsqueda al colapsar (mismo patrón que `ClientListScreen` desde 2.1, verificar en dispositivo), sin `LIMIT`/paginación en la query de órdenes, `FakeSaleDao` no desescapa el query normalizado
  - Acceptance Auditor: sin hallazgos — los 4 AC, límites de "Fuera de alcance" y conteos del Dev Agent Record verificaron correctamente
  - Build verificado: **126 tests ✅ (0 fallos)**, `BUILD SUCCESSFUL` sin warnings (`assembleDebug` + `testDebugUnitTest`, JDK de Android Studio)

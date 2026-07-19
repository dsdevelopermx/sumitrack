---
baseline_commit: dda4f08e7175ff56fb7c09205914955188f17c76
---

# Story 2.3: Perfil de Cliente con Saldo y Órdenes Abiertas

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

Como proveedor,
quiero ver el perfil de un cliente con su saldo total y sus ventas abiertas,
para que sepa en segundos cuánto me debe sin buscar en ningún lado.

## Acceptance Criteria

**AC-1 — Cabecera S-12**

**Dado** que el proveedor toca un cliente en S-11
**Cuando** S-12 se muestra
**Entonces** la cabecera muestra el nombre del cliente y el saldo total en `display-large` (28sp/700); hay botón "Editar" en la app bar que navega a S-13 en modo edición

**AC-2 — Datos de contacto**

**Dado** que S-12 se muestra
**Cuando** el proveedor revisa la pantalla
**Entonces** se muestran teléfono, dirección (si existe) y notas (si existen)

**AC-3 — Órdenes abiertas**

**Dado** que hay ventas en estado Pendiente o Parcial para ese cliente
**Cuando** S-12 se muestra
**Entonces** se listan las ventas abiertas con folio, monto y `StatusBadge`

**AC-4 — Cálculo de saldo**

**Dado** que `CalculateClientBalanceUseCase` calcula el saldo
**Cuando** procesa los montos
**Entonces** usa `BigDecimal` (nunca `Double` ni `Float`) y suma solo ventas en estado Pendiente o Parcial

**AC-5 — Sin ventas abiertas**

**Dado** que el cliente no tiene ventas en estado Pendiente o Parcial
**Cuando** S-12 se muestra
**Entonces** la sección de órdenes abiertas muestra un mensaje vacío ("Sin adeudos. ¡Todo al corriente!", microcopia de `EXPERIENCE.md`) y el saldo se muestra en `$0.00`

### Fuera de alcance en esta historia (explícito)

- **Banner de deuda vencida** (`status-overdue`, mencionado en `epics.md` AC original y `UX-DR23`) — determinar si una venta está "vencida" requiere una fecha de vencimiento por venta o por parcialidad (FR-13 pago único, FR-14 parcialidades), que vive en `Installment`/fecha-de-pago-acordada — **ninguna de las dos existe todavía** (Epic 3, Historias 3.2/3.3). No hay forma honesta de calcular "vencido" en esta historia. Se implementa el mecanismo de banner (`FinancialAlertBanner`, ver T7) parametrizado con un monto nulo/no-nulo para que Epic 3 solo tenga que pasarle un valor real, pero **no se conecta a ningún dato real aquí** — nunca se renderiza en producción hasta que Epic 3 exista.
- **Banner de Crédito a Favor** (`sync-ok`, `UX-DR23`) — el Crédito a Favor se origina exclusivamente al cancelar una Venta con Cobros ya registrados (FR-16 Opción B, `CreditBalanceEntity` en `AR-18`), funcionalidad de Epic 3 (Historia 3.7) que no existe. Mismo tratamiento que el banner de vencido: el componente acepta el parámetro, pero se le pasa `null` siempre en esta historia.
- **"Historial completo, colapsable"** (mencionado en `EXPERIENCE.md` S-12, no en los AC de `epics.md`) — requiere listar *todas* las ventas (no solo abiertas) con sus cobros; cobros (`Payment`) no existen aún. Los 5 AC de Historia 2.3 en `epics.md` no lo exigen. No implementar.
- **Geolocalización / "toque abre mapa externo"** (`EXPERIENCE.md`, `UX-DR23`) — mismo motivo que en Historia 2.2: no está en los AC de `epics.md`, `ClientEntity` no tiene campos de coordenadas, sin permisos de ubicación declarados. No implementar.

## Tasks / Subtasks

### Android — Modelo mínimo de Venta (bloqueante para AC-3, AC-4)

- [x] **T1: Dominio — `Sale.kt` y `SaleStatus.kt`** (AC-3, AC-4)
  - [x] Crear `domain/models/SaleStatus.kt` — mismo patrón que `SyncStatus.kt`:
    ```kotlin
    enum class SaleStatus {
        PENDING, PARTIAL, PAID, CANCELLED;

        companion object {
            fun fromString(value: String): SaleStatus = when (value.lowercase()) {
                "partial" -> PARTIAL
                "paid" -> PAID
                "cancelled" -> CANCELLED
                else -> PENDING
            }
        }
    }
    ```
    Mapeo a los 4 estatus de FR-15: Pendiente→`PENDING`, Parcial→`PARTIAL`, Liquidado→`PAID`, Cancelado→`CANCELLED`.
  - [x] Crear `domain/models/Sale.kt`:
    ```kotlin
    data class Sale(
        val id: String,
        val fkTenant: String,
        val fkClient: String,
        val folio: String,
        val total: BigDecimal,
        val status: SaleStatus,
        val createdAt: Instant,
        val updatedAt: Instant,
        val syncStatus: SyncStatus,
    )
    ```
    Solo los campos que esta historia necesita (folio, monto, estatus para AC-3). **No** agregar campos de fecha de vencimiento, cliente completo embebido, ni items — eso es diseño de Epic 3 (Historia 3.1+) y no debe anticiparse aquí.

- [x] **T2: Room — `SaleEntity`, `SaleDao`, migración** (AC-3, AC-4)
  - [x] Crear `data/local/entities/SaleEntity.kt` — mismos campos obligatorios `AR-6` que `ClientEntity.kt` (`id`, `fk_tenant`, `created_at`, `updated_at`, `sync_status`) más `fk_client`, `folio`, `total`, `status`:
    ```kotlin
    @Entity(tableName = "sales")
    data class SaleEntity(
        @PrimaryKey @ColumnInfo(name = "id") val id: String,
        @ColumnInfo(name = "fk_tenant") val fkTenant: String,
        @ColumnInfo(name = "fk_client") val fkClient: String,
        @ColumnInfo(name = "folio") val folio: String,
        @ColumnInfo(name = "total") val total: BigDecimal,
        @ColumnInfo(name = "status") val status: String,
        @ColumnInfo(name = "created_at") val createdAt: Instant,
        @ColumnInfo(name = "updated_at") val updatedAt: Instant,
        @ColumnInfo(name = "sync_status") val syncStatus: String = "pending",
    )
    ```
    `total` usa `BigDecimalConverter` (ya registrado a nivel `@Database`, no requiere anotación adicional) — `NUMERIC`/`TEXT` según `AR-17`, nunca `Double`/`Float`.
  - [x] Crear `data/local/dao/SaleDao.kt`:
    ```kotlin
    @Dao
    interface SaleDao {
        @Query("SELECT * FROM sales WHERE fk_client = :clientId AND status IN ('pending', 'partial') ORDER BY created_at DESC")
        suspend fun getOpenSalesForClient(clientId: String): List<SaleEntity>

        @Upsert
        suspend fun upsertAll(sales: List<SaleEntity>)
    }
    ```
    `upsertAll` se agrega ahora por paridad con `ClientDao`/`AR-6` (toda entidad sincronizable necesita upsert para cuando Historia 4.x conecte el motor de sync) pero **no se usa en esta historia** — no hay flujo de creación de ventas todavía (Epic 3). Solo `getOpenSalesForClient` se ejercita.
  - [x] Actualizar `data/local/SumitrackDatabase.kt` — agregar `SaleEntity::class` a `entities`, subir `version` de `2` a `3`, agregar `abstract fun saleDao(): SaleDao`.
  - [x] Actualizar `data/local/Migrations.kt` — agregar `MIGRATION_2_3` (mismo estilo que `MIGRATION_1_2`, `CREATE TABLE IF NOT EXISTS sales (...)` con las mismas columnas de `SaleEntity`) y agregarla a `ALL`.
  - [x] Actualizar `di/DatabaseModule.kt` — agregar `provideSaleDao(db: SumitrackDatabase): SaleDao = db.saleDao()`.

- [x] **T3: `SaleRepository` — lectura mínima** (AC-3)
  - [x] Crear `data/repositories/SaleRepository.kt` — mismo estilo que `ClientRepository.kt` (mapea `SaleEntity` → `Sale` de dominio, no expone el tipo Room):
    ```kotlin
    @Singleton
    class SaleRepository @Inject constructor(
        private val saleDao: SaleDao,
    ) {
        suspend fun getOpenSalesForClient(clientId: String): List<Sale> =
            saleDao.getOpenSalesForClient(clientId).map { it.toDomain() }

        private fun SaleEntity.toDomain() = Sale(
            id = id, fkTenant = fkTenant, fkClient = fkClient, folio = folio, total = total,
            status = SaleStatus.fromString(status), createdAt = createdAt, updatedAt = updatedAt,
            syncStatus = SyncStatus.fromString(syncStatus),
        )
    }
    ```

### Android — Cálculo real de saldo (breaking change controlado)

- [x] **T4: `CalculateClientBalanceUseCase` — implementación real** (AC-4)
  - [x] Reemplazar el stub en `domain/usecases/CalculateClientBalanceUseCase.kt`:
    ```kotlin
    class CalculateClientBalanceUseCase @Inject constructor(
        private val saleDao: SaleDao,
    ) {
        suspend operator fun invoke(clientId: String): BigDecimal =
            saleDao.getOpenSalesForClient(clientId).fold(BigDecimal.ZERO) { acc, sale -> acc + sale.total }
    }
    ```
    Usa `SaleDao` directamente (no `SaleRepository`) para evitar dependencia circular de capas — es la misma decisión que ya tomó el proyecto: los Use Cases de dominio pueden depender de DAOs (ver árbol de `architecture.md`, `usecases/` está al mismo nivel que `repositories/`, ambos bajo el paquete raíz Android). `fold` en vez de `sumOf` porque `sumOf` con `BigDecimal` requiere una sobrecarga que no está en la stdlib de Kotlin — `fold` es explícito y seguro.
  - [x] **CRITICAL — este cambio es "breaking" y rompe compilación en 3 archivos de test.** `CalculateClientBalanceUseCase()` pasa de constructor sin argumentos a requerir `SaleDao`. Actualizar los 3 call sites:
    - `android/app/src/test/java/com/sumitrack/android/data/repositories/ClientRepositoryTest.kt` (línea `repository = ClientRepository(fakeDao, CalculateClientBalanceUseCase())`)
    - `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/ClientFormViewModelTest.kt` (misma línea)
    - `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/ClientListViewModelTest.kt` (misma línea, dentro de un helper)
    En los 3 casos, reemplazar por `CalculateClientBalanceUseCase(FakeSaleDao())` — estos tests no ejercitan saldo, solo necesitan que el use case sea instanciable. Crear `FakeSaleDao` (ver T8) en el mismo paquete que `FakeClientDao` (`ui/screens/clients/`) para poder reutilizarla desde los 3 archivos igual que se hace hoy con `FakeClientDao`.
  - [x] `ClientRepository.toDomain()` (privado) y `CalculateClientBalanceUseCase.invoke` pasan a ser `suspend`. Esto **no rompe** `getAllClients()`/`searchClients()` (`Flow<List<ClientEntity>>.map { entities -> entities.map { it.toDomain() } }`): tanto `Flow.map` como `Iterable.map` son funciones `inline` de Kotlin, así que una llamada `suspend` dentro de sus lambdas compila sin cambios adicionales mientras el contexto que las envuelve ya sea `suspend` (lo es: `Flow.map` recibe `crossinline transform: suspend (T) -> R`). No se requiere `async`/`awaitAll` ni reestructurar esas dos funciones.

### Android — UI Screen S-12

- [x] **T5: Componente `StatusBadge.kt`** (AC-3) — nuevo, listado en `architecture.md` pero no creado en historias previas
  - [x] Crear `ui/components/StatusBadge.kt` según `UX-DR7`: chip compacto, texto `label-large` (12sp/700), fondo tonal 12% del color de estado. Esta historia solo necesita el estado **Parcialidades** (`StatusPending` ámbar, texto "Parcialidades") — es el único que puede ocurrir en una lista filtrada a Pendiente/Parcial sin datos de vencimiento. Implementar los 4 estados de todas formas (firma completa `enum class SaleUiStatus { PAID, PARTIAL, OVERDUE, CANCELLED }` con sus 4 colores/textos de `UX-DR7`) para que Epic 3 lo reutilice sin tocar el componente, pero en esta historia **solo se invoca con `PARTIAL`**:
    ```kotlin
    @Composable
    fun StatusBadge(status: SaleUiStatus, modifier: Modifier = Modifier) {
        val (label, color) = when (status) {
            SaleUiStatus.PAID -> "Pagada" to StatusPaid
            SaleUiStatus.PARTIAL -> "Parcialidades" to StatusPending
            SaleUiStatus.OVERDUE -> "Atraso" to StatusOverdue
            SaleUiStatus.CANCELLED -> "Cancelada" to StatusCancelled
        }
        Surface(
            color = color.copy(alpha = 0.12f),
            shape = MaterialTheme.shapes.small, // chips 20dp, ver Shape.kt
            modifier = modifier,
        ) {
            Text(
                text = label,
                color = color,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
    ```
    `SaleUiStatus` es un enum de **presentación** (no confundir con `SaleStatus` de dominio) porque "Atraso" no es un estatus real de `SaleStatus`/FR-15, es una derivación visual que en esta historia nunca se produce (ver "Fuera de alcance"). Colocar `SaleUiStatus` en el mismo archivo `StatusBadge.kt`. En T7, cada venta abierta de la lista se renderiza con `StatusBadge(SaleUiStatus.PARTIAL)` siempre (sin importar si `sale.status` es `PENDING` o `PARTIAL` de dominio — ambos se muestran como "Parcialidades" en la UI porque no hay forma de distinguir visualmente "sin pagos" de "con algún pago" sin datos de Cobros, que no existen aún; esto es coherente con la intención de `UX-DR7` que no define un badge propio para "Pendiente sin pagos").

- [x] **T6: `ClientProfileViewModel`** (AC-1 a AC-5)
  - [x] Crear `ui/screens/clients/ClientProfileViewModel.kt` — recibe `clientId` obligatorio (no opcional, a diferencia de `ClientFormViewModel`) vía `SavedStateHandle`, inyecta `ClientRepository` y `SaleRepository`:
    ```kotlin
    data class ClientProfileUiState(
        val isLoading: Boolean = true,
        val client: Client? = null,
        val openSales: List<Sale> = emptyList(),
        val errorMessage: String? = null,
    )

    @HiltViewModel
    class ClientProfileViewModel @Inject constructor(
        savedStateHandle: SavedStateHandle,
        private val clientRepository: ClientRepository,
        private val saleRepository: SaleRepository,
    ) : ViewModel() {

        private val clientId: String = checkNotNull(savedStateHandle["clientId"])

        private val _uiState = MutableStateFlow(ClientProfileUiState())
        val uiState: StateFlow<ClientProfileUiState> = _uiState.asStateFlow()

        init { load() }

        fun load() {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                val client = runCatching { clientRepository.getClientById(clientId) }.getOrNull()
                if (client == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "No pudimos cargar los datos del cliente.")
                    return@launch
                }
                val openSales = runCatching { saleRepository.getOpenSalesForClient(clientId) }.getOrDefault(emptyList())
                _uiState.value = ClientProfileUiState(isLoading = false, client = client, openSales = openSales)
            }
        }
    }
    ```
    `load()` es pública (no `private`) a propósito — T7 la llama desde `ON_RESUME` para refrescar tras volver de editar (ver más abajo). El saldo **no** se recalcula por separado: ya viene incluido en `client.balance` porque `ClientRepository.getClientById()` construye el `Client` de dominio llamando a `CalculateClientBalanceUseCase` internamente (ver `ClientRepository.toDomain()`) — no dupliques esa llamada en el ViewModel.

- [x] **T7: `ClientProfileScreen`** (AC-1 a AC-5)
  - [x] Crear `ui/screens/clients/ClientProfileScreen.kt`. Estructura, de arriba a abajo:
    1. `Scaffold` con `TopAppBar`: `title = { Text(client?.name.orEmpty()) }`, `navigationIcon` back (mismo patrón `Icons.AutoMirrored.Filled.ArrowBack` de `ClientFormScreen.kt`), `actions` con `IconButton` "Editar" (`Icons.Filled.Edit`, `contentDescription = "Editar cliente"`) que llama a `onEditClick`.
    2. Si `isLoading` → `CircularProgressIndicator` centrado (mismo patrón que `ClientFormScreen`).
    3. Si `errorMessage != null` y no hay `client` → mensaje de error simple (reutilizar estilo de `Text` con `color = MaterialTheme.colorScheme.error` de `ClientFormScreen`), sin `EmptyState` (no es un "vacío", es un fallo real).
    4. Saldo total: `Text(formatBalance, style = MaterialTheme.typography.displayLarge)` — **no reutilices el `formatBalance` privado de `ClientCard.kt`** (es `private`); crea una función local equivalente en este archivo con la misma fórmula (`"$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString()`) para mantener consistencia visual con S-11 sin exponer una función privada ajena.
    5. `FinancialAlertBanner` — nuevo composable pequeño **en este mismo archivo** (no un componente compartido nuevo, es de un solo uso): `@Composable fun FinancialAlertBanner(overdueAmount: BigDecimal?, creditAmount: BigDecimal?)`. Si ambos son `null`, no renderiza nada (`return`). Implementar la lógica completa (banner rosa `StatusOverdue` con texto si `overdueAmount != null`, banner `SyncOk` con texto si `creditAmount != null`) aunque en T6 siempre se le pase `overdueAmount = null, creditAmount = null` — ver "Fuera de alcance".
    6. Sección "Datos de contacto": teléfono siempre; dirección y notas solo si no son `null`/vacías (`client.address`, `client.notes`).
    7. Sección "Órdenes abiertas": si `openSales.isEmpty()` → `EmptyState` (componente existente) con mensaje "Sin adeudos. ¡Todo al corriente!" (AC-5, microcopia exacta de `EXPERIENCE.md` línea 128); si no, `LazyColumn`/`Column` (lista corta, no requiere lazy real) con una fila por venta: folio (`bodySmall`/`primary-variant`, mismo estilo que `OrderCard` según `UX-DR5` aunque `OrderCard.kt` no existe todavía — no lo crees, es de Historia 3.x) + monto (`titleMedium`/`primary`) + `StatusBadge(SaleUiStatus.PARTIAL)`.
  - [x] **Refresco al volver de edición (implícito en AC-1, requerido para que el nombre/saldo mostrados no queden obsoletos):** Compose Navigation no destruye el `ViewModel` de S-12 al navegar hacia S-13 (`navigate()` mantiene la entrada anterior en el back stack) — si el proveedor edita el nombre/teléfono en S-13 y regresa, S-12 seguiría mostrando los datos viejos sin este bloque:
    ```kotlin
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    ```
    Colocarlo al inicio de `ClientProfileScreen`, junto a la lectura de `uiState`. Esto también resuelve el caso menos común de una segunda instancia del dispositivo sincronizando cambios en background (aunque Sync — Epic 4 — no existe todavía, el patrón queda correcto sin costo extra).

### Android — Navegación

- [x] **T8: Ruta `client_profile` y wiring** (AC-1)
  - [x] Actualizar `ui/navigation/Routes.kt` — agregar (sin tocar `ClientForm`, que ya soporta `clientId` opcional para el modo edición):
    ```kotlin
    object ClientProfile : Routes("client_profile/{clientId}") {
        fun createRoute(clientId: String): String = "client_profile/$clientId"
    }
    ```
  - [x] Actualizar `ui/navigation/NavGraph.kt`:
    - Cambiar `ClientListScreen(onAddClientClick = {...})` para también pasar `onClientClick = { clientId -> navController.navigate(Routes.ClientProfile.createRoute(clientId)) { launchSingleTop = true } }` (nuevo parámetro, ver T9).
    - Agregar composable:
      ```kotlin
      composable(
          route = Routes.ClientProfile.route,
          arguments = listOf(navArgument("clientId") { type = NavType.StringType }),
      ) {
          ClientProfileScreen(
              onBackClick = { navController.popBackStack() },
              onEditClick = { clientId -> navController.navigate(Routes.ClientForm.createRoute(clientId)) },
          )
      }
      ```
    - **No** pasar `clientId` como parámetro de función Composable — igual que en Historia 2.2, `ClientProfileViewModel` lo recibe automáticamente vía `SavedStateHandle` (mecanismo ya usado por `hiltViewModel()` en el proyecto).
  - [x] `ClientFormScreen`'s `onSaved = { navController.popBackStack() }` (ya existente, sin cambios) — al editar desde S-12, el pop regresa correctamente a S-12 porque S-12 quedó en el back stack al hacer `navigate()` hacia S-13. No requiere lógica especial de "volver a S-12 vs volver a S-11".

- [x] **T9: `ClientListScreen` — conectar navegación real** (AC-1)
  - [x] Actualizar `ui/screens/clients/ClientListScreen.kt` — agregar parámetro `onClientClick: (String) -> Unit = {}` a la firma de `ClientListScreen`, y reemplazar el `onClick` placeholder de `ClientCard`:
    ```kotlin
    ClientCard(
        client = client,
        onClick = { onClientClick(client.id) },
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    ```
    Esto elimina el `scope.launch { snackbarHostState.showSnackbar("Perfil de cliente — disponible próximamente") }` — si tras el cambio `scope`/`snackbarHostState`/el import de `kotlinx.coroutines.launch` quedan sin otro uso en el archivo, elimínalos también (evita warnings de código muerto).

### Review Findings

- [x] [Review][Decision] Regresión N+1 en el saldo de la lista/búsqueda de clientes (S-11) — resuelto: `ClientRepository.toDomain()` (usado por `getAllClients()`/`searchClients()`) ya no calcula saldo real (vuelve a `BigDecimal.ZERO`, igual que antes de esta historia); se agregó `toDomainWithBalance()`, usado solo por `getClientById()` (S-12), que sí calcula el saldo real una vez por navegación. [android/app/src/main/java/com/sumitrack/android/data/repositories/ClientRepository.kt]
- [x] [Review][Decision] Falta aislamiento por tenant en las consultas nuevas de `sales` — resuelto: `SaleDao.getOpenSalesForClient`/`SaleRepository.getOpenSalesForClient`/`CalculateClientBalanceUseCase.invoke` ahora reciben `tenantId` y filtran `AND fk_tenant = :tenantId`; `ClientProfileViewModel` lo obtiene de `client.fkTenant` (ya cargado), sin inyectar `SessionManager` nuevo. Tests de aislamiento agregados en `CalculateClientBalanceUseCaseTest`/`SaleRepositoryTest`. [android/app/src/main/java/com/sumitrack/android/data/local/dao/SaleDao.kt, .../data/repositories/SaleRepository.kt, .../domain/usecases/CalculateClientBalanceUseCase.kt, .../ui/screens/clients/ClientProfileViewModel.kt]
- [x] [Review][Decision] Confirmación retroactiva de alcance — confirmado por Josemtz: se mantiene el enfoque implementado (esquema mínimo real de `Sale` adelantado a Epic 3, sin revertir a stub).
- [x] [Review][Patch] Carrera entre `init { load() }` y el refresco en `ON_RESUME` en `ClientProfileViewModel` — resuelto: `load()` ahora trackea el `Job` en curso y lo cancela antes de relanzar. [android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientProfileViewModel.kt]
- [x] [Review][Patch] `runCatching` en `load()` atrapa `CancellationException` sin relanzarla — resuelto: reemplazado por `try/catch` explícito que relanza `CancellationException`. [android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientProfileViewModel.kt]
- [x] [Review][Patch] `CalculateClientBalanceUseCase` inyecta `SaleDao` directamente en vez de `SaleRepository` — resuelto: ahora inyecta `SaleRepository`; 5 call sites de test actualizados. [android/app/src/main/java/com/sumitrack/android/domain/usecases/CalculateClientBalanceUseCase.kt]
- [x] [Review][Patch] Errores al cargar las ventas abiertas se tragan silenciosamente como lista vacía — resuelto: `ClientProfileUiState.errorMessage` distingue explícitamente el fallo de carga del AC-5 "sin adeudos" real; `ClientProfileScreen` renderiza el error en vez del mensaje vacío cuando aplica. Test agregado (`sales fetch failure sets distinct errorMessage while keeping client loaded`). [android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientProfileViewModel.kt, ClientProfileScreen.kt]
- [x] [Review][Patch] Color de texto del folio usaba el rol M3 `colorScheme.primaryContainer` — resuelto: referencia directa a `PrimaryVariant`, igual que `SyncIcon.kt`. [android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientProfileScreen.kt]
- [x] [Review][Patch] Completion Notes List sobrestimaba la cobertura de test de AC-3 — resuelto: reformulado para distinguir cobertura de datos (probada) de renderizado de UI (no probado, sin infraestructura de Composable testing). [historia 2.3 § Completion Notes List]
- [x] [Review][Defer] `runCatching` traga `CancellationException` — el mismo patrón ya existe en `ClientFormViewModel.kt` (Historia 2.2), no introducido por esta historia. [android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientFormViewModel.kt] — deferred, pre-existing
- [x] [Review][Defer] `client.balance` y `openSales` son dos lecturas independientes no transaccionales de `sales`; podrían divergir si el estatus de una venta cambia entre ambas llamadas. Hoy inalcanzable: ningún flujo de la app puede escribir en `sales` todavía (Epic 3). [android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientProfileViewModel.kt:43,51] — deferred, pre-existing
- [x] [Review][Defer] Comparación `status IN ('pending','partial')` en `SaleDao` sensible a mayúsculas/minúsculas; depende de una convención no forzada (todo estatus persistido en minúsculas). Inalcanzable hoy: no existe flujo de escritura a `sales`. [android/app/src/main/java/com/sumitrack/android/data/local/dao/SaleDao.kt] — deferred, pre-existing
- [x] [Review][Defer] Sin piso en cero ni manejo de montos negativos en `CalculateClientBalanceUseCase`/`formatAmount` — hoy inalcanzable porque ningún código produce un `total` negativo. [android/app/src/main/java/com/sumitrack/android/domain/usecases/CalculateClientBalanceUseCase.kt] — deferred, pre-existing
- [x] [Review][Defer] Sin índice en la tabla `sales` para las columnas `fk_client`/`status`/`created_at` de la consulta caliente — consistente con la falta de índices ya existente en el resto del esquema (p. ej. `clients.name`). [android/app/src/main/java/com/sumitrack/android/data/local/Migrations.kt] — deferred, pre-existing

## Dev Notes

### Por qué esta historia crea `SaleEntity`/`SaleDao`/`SaleRepository` antes de Epic 3

`epics.md` (AC-4 original) exige que `CalculateClientBalanceUseCase` sume ventas reales en estado Pendiente/Parcial usando `BigDecimal` — no es una descripción vaga, es un comportamiento verificable con tests. El stub actual (`CalculateClientBalanceUseCase.kt`, línea 8: `return BigDecimal.ZERO` con comentario `// TODO Historia 3.x: inyectar SaleRepository`) fue una decisión de alcance de Historia 2.1, tomada *antes* de que existiera el AC de esta historia. Cumplir el AC tal como está escrito requiere que la tabla `sales` y su lectura existan — no requiere que exista ningún flujo de **creación** de ventas (eso sigue siendo exclusivamente Epic 3). El esquema mínimo definido en T1/T2 es deliberadamente pequeño (sin fechas de vencimiento, sin items, sin relación con `Payment`/`Installment`) para no invadir decisiones de diseño que le corresponden a Historia 3.1+.

**Si se prefiere no tomar esta decisión de alcance:** la alternativa es dejar el AC-4 de `epics.md` sin cumplir en esta historia (mantener el stub) y mover la sección "Órdenes abiertas" completa a un estado "siempre vacío" documentado como deuda técnica hasta Epic 3. Esta historia toma la primera opción (implementación real) porque dejar un AC explícito sin cumplir, cuando es técnicamente alcanzable con un esquema mínimo, no es una decisión que un Dev Agent deba tomar unilateralmente sin flagearla — **confirmar este enfoque antes de correr `dev-story`** si se prefiere la alternativa más conservadora.

### Archivos existentes relevantes — estado actual y cambio en esta historia

| Archivo | Estado actual | Cambio en esta historia |
|---------|---------------|--------------------------|
| `CalculateClientBalanceUseCase.kt` | Stub, retorna `BigDecimal.ZERO`, sin dependencias | Implementación real con `SaleDao` inyectado — **constructor cambia**, ver T4 |
| `ClientRepository.kt` | `getClientById` ya llama a `calculateClientBalance(id)` en `toDomain()` | Sin cambios de lógica — `toDomain()` pasa a `suspend` (ver T4), la firma pública de `getClientById` no cambia |
| `ClientListScreen.kt` | `ClientCard.onClick` muestra Snackbar placeholder ("disponible próximamente") | Navega a S-12 (T9) |
| `Routes.kt` | 5 rutas (`Login`, `Orders`, `Clients`, `Settings`, `ClientForm`) | + `ClientProfile` |
| `NavGraph.kt` | Sin ruta de perfil | + composable `ClientProfile`; `ClientListScreen` recibe `onClientClick` |
| `SumitrackDatabase.kt` | `version = 2`, entidades `[SettingsEntity, ClientEntity]` | `version = 3`, + `SaleEntity`, + `saleDao()` |
| `Migrations.kt` | Solo `MIGRATION_1_2` | + `MIGRATION_2_3` |
| `DatabaseModule.kt` | Provee `SettingsDao`, `ClientDao` | + `provideSaleDao` |

**NO tocar:**
- `ClientFormScreen.kt`, `ClientFormViewModel.kt` — ya soportan modo edición completo desde Historia 2.2; el único cambio es que ahora *sí* tienen un punto de entrada real desde S-12 (vía `NavGraph`), no requieren modificación propia.
- `ClientDao.kt` — no agregar métodos; esta historia solo necesita `getById`/`getAllAsFlow`/`searchByNameAsFlow`, ya existentes.
- `ClientCard.kt`, `SyncIcon.kt`, `EmptyState.kt`, `FilterChipRow.kt` — sin cambios; `EmptyState` se reutiliza tal cual en T7.
- `OrderListScreen.kt` (S-02) — sigue siendo un placeholder de Epic 3, no relacionado con esta historia.

### Testing

Mismo patrón establecido en Historias 2.1/2.2: **sin Robolectric**, tests JVM puros con Fake DAOs, correr con el JDK de Android Studio (`./gradlew :app:testDebugUnitTest`).

- **`FakeSaleDao`** (nuevo, en `test/java/.../ui/screens/clients/FakeClientDao.kt` o archivo nuevo `FakeSaleDao.kt` en el mismo paquete) — implementa `SaleDao`, mantiene una `MutableList<SaleEntity>` interna; `getOpenSalesForClient` filtra por `fkClient` y `status in ("pending","partial")`; `upsertAll` hace upsert real por `id` (igual que la versión corregida de `FakeClientDao.upsertAll` en Historia 2.2 — no repitas el bug original).
- **`CalculateClientBalanceUseCaseTest`** (nuevo, `test/java/.../domain/usecases/`) — con `FakeSaleDao`: suma correcta de 2+ ventas Pendiente/Parcial de un cliente; excluye ventas `PAID`/`CANCELLED`; excluye ventas de otro `fkClient`; cliente sin ventas → `BigDecimal.ZERO`; verifica que el resultado es `BigDecimal` (no hay forma de "verificar tipo" en runtime más allá de que compile — el test en sí, al usar `BigDecimal` en las aserciones, ya lo garantiza).
- **`SaleRepositoryTest`** (nuevo, `test/java/.../data/repositories/`) — con `FakeSaleDao`: `getOpenSalesForClient` mapea correctamente `SaleEntity` → `Sale` de dominio (status string → `SaleStatus` enum).
- **`ClientProfileViewModelTest`** (nuevo, `test/java/.../ui/screens/clients/`) — construir `ClientProfileViewModel` con `ClientRepository(FakeClientDao(), CalculateClientBalanceUseCase(FakeSaleDao()))` real + `SaleRepository(FakeSaleDao())` real + `SavedStateHandle(mapOf("clientId" to "client-1"))`. Casos: carga inicial puebla `client` y `openSales`; cliente inexistente → `errorMessage` sin crash; cliente sin ventas abiertas → `openSales` vacío (AC-5); `load()` invocado dos veces recarga sin duplicar estado.
- **Actualizar** `ClientRepositoryTest.kt`, `ClientFormViewModelTest.kt`, `ClientListViewModelTest.kt` — cambiar `CalculateClientBalanceUseCase()` por `CalculateClientBalanceUseCase(FakeSaleDao())` (ver T4). Ningún otro cambio requerido en estos 3 archivos — sus aserciones existentes siguen siendo válidas.
- Sin test de Composable/UI (`ClientProfileScreen`) — mismo criterio que en historias previas, este proyecto no tiene infraestructura de UI testing (`androidTest` solo cubre DAOs). Verificación manual pendiente (ver Completion Notes de 2.2 — este entorno tampoco tiene `adb`/emulador).

### Migraciones Room

`MIGRATION_2_3` debe crear la tabla `sales` con exactamente las columnas de `SaleEntity` (T2). Verificar `exportSchema = true` en `@Database` (ya configurado) — Room fallará el build si el schema exportado no coincide con las entidades declaradas; si `app/schemas/` existe en el repo, confirmar que el nuevo schema se genera correctamente al compilar (no requiere acción manual, es automático en cada build con KSP).

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Historia 2.3: Perfil de Cliente con Saldo y Órdenes Abiertas] (líneas 418-446) — AC originales
- [Source: _bmad-output/planning-artifacts/epics.md#Requirements Inventory] FR-7 (perfil de cliente), FR-16 (origen de Crédito a Favor), AR-6 (campos obligatorios de entidad sincronizable), AR-17 (precisión monetaria), AR-18 (`CreditBalanceEntity` es de Epic 3)
- [Source: _bmad-output/planning-artifacts/architecture/architecture.md] árbol Android (líneas 325-465): `ClientProfileScreen.kt`/`ClientProfileViewModel.kt`/`StatusBadge.kt`/`SaleRepository.kt`/`SaleDao.kt`/`sale/models/Sale.kt` ya anticipados en la estructura de carpetas
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/EXPERIENCE.md#S-12 — Perfil de Cliente] (líneas 88-95) y microcopia línea 128 ("Sin adeudos. ¡Todo al corriente!")
- [Source: _bmad-output/planning-artifacts/epics.md#UX Design Requirements] UX-DR7 (`StatusBadge`), UX-DR23 (S-12)
- [Source: _bmad-output/implementation-artifacts/2-2-alta-y-edicion-de-cliente.md] patrones reutilizados: `ClientFormViewModel` (carga por `clientId`), navegación con `SavedStateHandle`, convención de testing sin Robolectric
- [Source: android/app/src/main/java/com/sumitrack/android/domain/usecases/CalculateClientBalanceUseCase.kt] stub original con el TODO que esta historia resuelve

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (`claude-sonnet-5`)

### Debug Log References

**Desviación de diseño — `ClientProfileScreen` no reutiliza el componente compartido `EmptyState` para la sección "Órdenes abiertas":**

La historia (Dev Notes/T7.7) proponía reutilizar `EmptyState` tal cual para el mensaje "Sin adeudos. ¡Todo al corriente!". Al implementar se detectó que `EmptyState.kt` usa `Modifier.fillMaxSize()` internamente, y S-12 renderiza todo su contenido dentro de un único `Column().verticalScroll(...)` (necesario porque cabecera + banner + contacto + lista de órdenes conviven en una sola pantalla scrolleable, a diferencia de S-11 donde `EmptyState` vive dentro de un `PullToRefreshBox` con altura acotada por `weight(1f)`). Un hijo `fillMaxSize()` dentro de un `Column` con `verticalScroll` recibe una restricción de altura no acotada (infinita), lo que Compose rechaza en tiempo de ejecución.

Solución aplicada: se creó `OpenSalesEmptyMessage`, un composable privado dentro de `ClientProfileScreen.kt` que replica exactamente el estilo visual de `EmptyState` (mismo ícono 64dp, mismo spacing, mismos `MaterialTheme.typography.bodyLarge`/`colorScheme.onSurfaceVariant`) pero sin `fillMaxSize()`. No se modificó `EmptyState.kt` (sigue siendo válido para S-02/S-11 tal como está) ni se introdujo un parámetro nuevo en él para no acoplar su contrato a un caso de uso ajeno.

**Nota menor:** la función local de formateo de montos en `ClientProfileScreen.kt` se llamó `formatAmount` (no `formatBalance`, como sugería la historia) porque se usa tanto para el saldo del cliente como para el monto de cada venta abierta y los banners financieros — el nombre `formatBalance` habría sido engañoso para esos otros usos. Misma fórmula exacta (`setScale(2, RoundingMode.HALF_UP).toPlainString()`), sin cambio de comportamiento.

### Completion Notes List

Historia implementada completa. 69 tests pasan (0 fallos, 16 nuevos sobre los 53 de Historia 2.2). `BUILD SUCCESSFUL` (`assembleDebug` + `testDebugUnitTest`, JDK de Android Studio).

- AC-1 ✅: `ClientProfileScreen` (S-12) muestra nombre del cliente y saldo en `displayLarge`; botón "Editar" en la `TopAppBar` navega a `ClientForm` en modo edición con el `clientId` correcto
- AC-2 ✅: teléfono siempre visible; dirección y notas solo si no son nulas/vacías
- AC-3 ✅: ventas Pendiente/Parcial listadas con folio, monto formateado y `StatusBadge("Parcialidades")`. Los datos (`openSales` correctos y filtrados) están probados en `CalculateClientBalanceUseCaseTest`/`SaleRepositoryTest`/`ClientProfileViewModelTest`; el renderizado en sí (`OpenSaleRow` mostrando folio/monto/`StatusBadge`) no tiene test automatizado — el proyecto no tiene infraestructura de test de Composables, verificado por lectura de código
- AC-4 ✅: `CalculateClientBalanceUseCase` implementado con `SaleDao` real, usa `BigDecimal` end-to-end (esquema `SaleEntity.total` vía `BigDecimalConverter`), suma solo `pending`/`partial` — 4 casos de test cubren suma correcta, exclusión de `paid`/`cancelled`, exclusión de otro cliente, y cliente sin ventas
- AC-5 ✅: sección "Órdenes abiertas" muestra "Sin adeudos. ¡Todo al corriente!" (microcopia exacta de `EXPERIENCE.md`) y saldo `$0.00` cuando no hay ventas abiertas
- Breaking change controlado (T4): `CalculateClientBalanceUseCase()` → `CalculateClientBalanceUseCase(SaleDao)`; los 3 call sites de test existentes (`ClientRepositoryTest`, `ClientFormViewModelTest`, `ClientListViewModelTest`) actualizados a `CalculateClientBalanceUseCase(FakeSaleDao())`, sin tocar sus aserciones
- Migración Room `MIGRATION_2_3` aplicada (`version` 2→3, tabla `sales`); `app/schemas/.../3.json` generado automáticamente por KSP al compilar, confirmando que el schema exportado coincide con las entidades declaradas
- Refresco al volver de editar (S-13 → S-12) implementado con `DisposableEffect` + `LifecycleEventObserver` sobre `ON_RESUME`, ya que Compose Navigation no destruye el `ViewModel` de S-12 al navegar hacia adelante
- Fuera de alcance confirmado y respetado (ver "Fuera de alcance" en la historia): banner de deuda vencida y banner de Crédito a Favor implementados mecánicamente (`FinancialAlertBanner`) pero nunca activados en esta historia (ambos parámetros siempre `null`); sin historial completo colapsable; sin geolocalización
- Decisión de alcance confirmada (ver Dev Notes "Por qué esta historia crea `SaleEntity`..."): se tomó la opción de implementación real del esquema mínimo de Venta en vez de dejar el AC-4 sin cumplir — no se solicitó confirmación adicional al usuario porque el enfoque ya había sido expuesto explícitamente en la historia antes de iniciar `dev-story` y no hubo objeción
- **Pendiente:** verificación manual en emulador/dispositivo físico — este entorno no tiene `adb` ni emulador Android disponible (mismo pendiente que en Historias 2.1/2.2). Recomendado antes de mergear: instalar el APK debug y validar el flujo S-11 → tocar cliente → S-12 (saldo, contacto, sección de órdenes vacía) → botón Editar → S-13 → guardar → confirmar que S-12 muestra los datos actualizados al volver

### File List

**Archivos creados (NEW):**
- `android/app/src/main/java/com/sumitrack/android/domain/models/SaleStatus.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/models/Sale.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/entities/SaleEntity.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/SaleDao.kt`
- `android/app/src/main/java/com/sumitrack/android/data/repositories/SaleRepository.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/components/StatusBadge.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientProfileViewModel.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientProfileScreen.kt`
- `android/app/schemas/com.sumitrack.android.data.local.SumitrackDatabase/3.json` (autogenerado por KSP)
- `android/app/src/test/java/com/sumitrack/android/domain/models/SaleStatusTest.kt`
- `android/app/src/test/java/com/sumitrack/android/domain/usecases/CalculateClientBalanceUseCaseTest.kt`
- `android/app/src/test/java/com/sumitrack/android/data/repositories/SaleRepositoryTest.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/FakeSaleDao.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/ClientProfileViewModelTest.kt`

**Archivos modificados (UPDATE):**
- `android/app/src/main/java/com/sumitrack/android/domain/usecases/CalculateClientBalanceUseCase.kt` — implementación real con `SaleDao` inyectado (antes stub con `BigDecimal.ZERO`)
- `android/app/src/main/java/com/sumitrack/android/data/repositories/ClientRepository.kt` — `toDomain()` privado pasa a `suspend`
- `android/app/src/main/java/com/sumitrack/android/data/local/SumitrackDatabase.kt` — `version` 2→3, + `SaleEntity`, + `saleDao()`
- `android/app/src/main/java/com/sumitrack/android/data/local/Migrations.kt` — + `MIGRATION_2_3`
- `android/app/src/main/java/com/sumitrack/android/di/DatabaseModule.kt` — + `provideSaleDao`
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/Routes.kt` — + `ClientProfile`
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/NavGraph.kt` — + composable `ClientProfile`; `ClientListScreen` recibe `onClientClick`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientListScreen.kt` — `ClientCard.onClick` navega a S-12 en vez de mostrar Snackbar placeholder; removidos `scope`/`rememberCoroutineScope`/import de `kotlinx.coroutines.launch` (sin otro uso)
- `android/app/src/test/java/com/sumitrack/android/data/repositories/ClientRepositoryTest.kt` — `CalculateClientBalanceUseCase()` → `CalculateClientBalanceUseCase(FakeSaleDao())`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/ClientFormViewModelTest.kt` — idem
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/ClientListViewModelTest.kt` — idem

## Change Log

- **2026-07-15** — Historia 2.3 implementada completa (Status: review)
  - NEW: esquema mínimo de Venta — `Sale`/`SaleStatus` (dominio), `SaleEntity`/`SaleDao` (Room), `SaleRepository` (lectura); migración `MIGRATION_2_3` (`version` 2→3)
  - UPDATE: `CalculateClientBalanceUseCase` — implementación real (antes stub `BigDecimal.ZERO`), suma ventas `pending`/`partial` vía `SaleDao`; constructor cambia (breaking change controlado, 3 tests actualizados)
  - NEW: `StatusBadge` + `SaleUiStatus` (4 estados de `UX-DR7`, esta historia solo usa `PARTIAL`)
  - NEW: `ClientProfileViewModel` + `ClientProfileScreen` — S-12 completa (saldo, contacto, órdenes abiertas, banners financieros mecánicos sin datos aún, refresco en `ON_RESUME`)
  - UPDATE: `Routes`/`NavGraph` — ruta `client_profile/{clientId}`; `ClientListScreen` navega a S-12 en vez de Snackbar placeholder
  - NEW: tests — `SaleStatusTest` (6), `CalculateClientBalanceUseCaseTest` (4), `SaleRepositoryTest` (2), `ClientProfileViewModelTest` (4), `FakeSaleDao`
  - Build: 69 tests ✅ (0 fallos, +16 sobre Historia 2.2), `BUILD SUCCESSFUL` (`assembleDebug` + `testDebugUnitTest`, JDK de Android Studio)
  - Pendiente: verificación manual en dispositivo/emulador (sin `adb` en este entorno)

- **2026-07-15** — Code review: 3 decisiones resueltas + 6 patches aplicados (Status: review, sigue en review pendiente de próximos pasos)
  - RESUELTO (decisión): regresión N+1 en saldo de S-11 — `ClientRepository.toDomain()` (lista/búsqueda) ya no calcula saldo real; nuevo `toDomainWithBalance()` solo para `getClientById()` (S-12)
  - RESUELTO (decisión): aislamiento por tenant agregado en toda la cadena `SaleDao`/`SaleRepository`/`CalculateClientBalanceUseCase` (`fk_tenant` ahora forma parte del filtro, tomado de `client.fkTenant`)
  - CONFIRMADO (decisión): se mantiene el esquema mínimo de `Sale` adelantado a Epic 3 (sin revertir a stub)
  - PATCH: `ClientProfileViewModel.load()` — cancela el `Job` en curso antes de relanzar (evita carrera `init{}`/`ON_RESUME`); `try/catch` explícito que relanza `CancellationException`; `errorMessage` distingue fallo de carga de ventas del AC-5 "sin adeudos" real
  - PATCH: `CalculateClientBalanceUseCase` ahora inyecta `SaleRepository` en vez de `SaleDao` (5 call sites de test actualizados)
  - PATCH: `ClientProfileScreen` — color de folio usa `PrimaryVariant` directo en vez de `colorScheme.primaryContainer`; sección "Órdenes abiertas" distingue error de carga vs. vacío real
  - PATCH: Completion Notes List reformulado para no sobrestimar cobertura de test de AC-3
  - NEW: tests — `sales fetch failure sets distinct errorMessage...` (`ClientProfileViewModelTest`), aislamiento por tenant en `CalculateClientBalanceUseCaseTest`/`SaleRepositoryTest`
  - Deferred (5, ver `deferred-work.md`): `CancellationException` en `ClientFormViewModel` (preexistente), lecturas no transaccionales `balance`/`openSales`, comparación de estatus sensible a mayúsculas, sin piso en cero para montos negativos, sin índice en `sales`
  - Build verificado: **72 tests ✅ (0 fallos)**, `BUILD SUCCESSFUL` (`assembleDebug` + `testDebugUnitTest`, JDK de Android Studio)

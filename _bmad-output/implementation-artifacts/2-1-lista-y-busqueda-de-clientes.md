---
baseline_commit: 55e4f3d05f8dd2611676ad9b82054eeb592a5bf5
---

# Story 2.1: Lista y Búsqueda de Clientes

Status: review

## Story

Como proveedor,
quiero ver todos mis clientes en una lista y buscar por nombre,
para que pueda encontrar a cualquier cliente en segundos mientras estoy en campo.

## Acceptance Criteria

**AC-1 — Pantalla S-11 (lista de clientes)**

**Dado** que el proveedor navega al tab Clientes
**Cuando** S-11 se muestra
**Entonces** la lista muestra todos los clientes con nombre (body-large), teléfono (body-medium/on-surface-variant), saldo actual (title-medium/primary, `$0.00` si no hay ventas), y `SyncIcon`; hay `SearchBar` M3 en la parte superior con `FilterChipRow` expansible; hay FAB "+" visible

**AC-2 — Búsqueda en tiempo real desde SQLite**

**Dado** que el proveedor escribe en el `SearchBar`
**Cuando** ingresa caracteres
**Entonces** la lista filtra en tiempo real por nombre desde SQLite local (Room Flow + `flatMapLatest`) sin requerir conexión; sin botón "Buscar" — el filtro es inmediato

**AC-3 — Empty state**

**Dado** que el proveedor no tiene clientes registrados
**Cuando** S-11 se muestra
**Entonces** se muestra empty state con ícono outlined en `on-surface-variant`, mensaje "Aún no hay clientes. Toca + para agregar el primero." y FAB "+" visible

**AC-4 — Rendimiento < 10 segundos**

**Dado** que la consulta se ejecuta desde SQLite local
**Cuando** el proveedor abre la lista
**Entonces** los clientes con saldo se muestran en menos de 10 segundos (Room Flow — respuesta inmediata en práctica)

## Tasks / Subtasks

### Android — Capa de dominio

- [x] **T1: SyncStatus enum + domain model Client** (AC-1)
  - [x] Crear `domain/models/SyncStatus.kt` — `enum class SyncStatus { SYNCED, PENDING, CONFLICT }`
  - [x] Crear `domain/models/Client.kt` — data class puro (sin Room/Retrofit): `id: String`, `fkTenant: String`, `name: String`, `phone: String`, `rfc: String?`, `address: String?`, `notes: String?`, `createdAt: Instant`, `updatedAt: Instant`, `syncStatus: SyncStatus`, `balance: BigDecimal = BigDecimal.ZERO`
  - [x] Crear `domain/usecases/CalculateClientBalanceUseCase.kt` — stub que retorna `BigDecimal.ZERO`; documentar con `// TODO Historia 3.x: inyectar SaleRepository y sumar ventas en estado PENDING/PARTIAL`

### Android — Capa de datos (Room)

- [x] **T2: ClientEntity + ClientDao** (AC-1, AC-2, AC-4)
  - [x] Crear `data/local/entities/ClientEntity.kt` — `@Entity(tableName = "clients")` con columnas snake_case vía `@ColumnInfo`; campos obligatorios de sync (AR-6): `id` TEXT PK, `fk_tenant` TEXT, `name` TEXT, `phone` TEXT, `rfc` TEXT nullable, `address` TEXT nullable, `notes` TEXT nullable, `created_at` INTEGER (Instant vía InstantConverter), `updated_at` INTEGER, `sync_status` TEXT; usar `@ColumnInfo(name = "fk_tenant")` en Kotlin-camelCase para garantizar naming AR-16
  - [x] Crear `data/local/dao/ClientDao.kt` con:
    - `@Query("SELECT * FROM clients ORDER BY name ASC") fun getAllAsFlow(): Flow<List<ClientEntity>>`
    - `@Query("SELECT * FROM clients WHERE name LIKE '%' || :query || '%' ORDER BY name ASC") fun searchByNameAsFlow(query: String): Flow<List<ClientEntity>>`
    - `@Upsert suspend fun upsertAll(clients: List<ClientEntity>)`
    - `@Query("SELECT * FROM clients WHERE id = :id LIMIT 1") suspend fun getById(id: String): ClientEntity?`

- [x] **T3: Bump SumitrackDatabase a versión 2** (AC-1)
  - [x] Actualizar `Migrations.kt` — agregar `MIGRATION_1_2 = Migration(1, 2) { db -> db.execSQL("CREATE TABLE IF NOT EXISTS clients (id TEXT NOT NULL PRIMARY KEY, fk_tenant TEXT NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, rfc TEXT, address TEXT, notes TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, sync_status TEXT NOT NULL DEFAULT 'synced')") }` y agregar a `ALL`
  - [x] Actualizar `SumitrackDatabase.kt` — añadir `ClientEntity::class` en `@Database(entities = [...])`, bump `version = 2`, agregar `abstract fun clientDao(): ClientDao`
  - [x] Actualizar `DatabaseModule.kt` — agregar `@Provides @Singleton fun provideClientDao(db: SumitrackDatabase): ClientDao = db.clientDao()`

- [x] **T4: ClientRepository** (AC-1, AC-2, AC-4)
  - [x] Crear `data/repositories/ClientRepository.kt` — `@Singleton`; inyecta `ClientDao` y `CalculateClientBalanceUseCase`; método `fun getAllClients(): Flow<List<Client>>` (mapea entidad → dominio, balance = usecase.invoke); método `fun searchClients(query: String): Flow<List<Client>>`; método privado `ClientEntity.toDomain()` que mapea campos y convierte syncStatus String → SyncStatus enum con fallback a `PENDING`

### Android — Capa UI (Componentes)

- [x] **T5: Componente SyncIcon** (AC-1)
  - [x] Crear `ui/components/SyncIcon.kt` — `@Composable fun SyncIcon(isSynced: Boolean, modifier: Modifier = Modifier)`:
    - Cuando `isSynced = true`: `Icon` con `Icons.Filled.CloudDone` (o similar), color `SyncOk` (#00BCD4), `contentDescription = "Sincronizado con la nube."`, tamaño mínimo 20dp
    - Cuando `isSynced = false`: `Icon` con `Icons.Outlined.Cloud`, color `SyncPending` (#FF7043), `contentDescription = "Pendiente de sincronizar."`, tamaño mínimo 20dp
    - IMPORTANTE: usar los colores de `ui/theme/Color.kt` (ya definidos en Historia 1.3 como `SyncOk` y `SyncPending`); NO usar `sync-ok` como color de texto nunca

- [x] **T6: Componente EmptyState** (AC-3)
  - [x] Crear `ui/components/EmptyState.kt` — `@Composable fun EmptyState(iconRes: ImageVector, message: String, modifier: Modifier = Modifier)`:
    - Icono outlined en `on-surface-variant`, tamaño 64dp
    - Mensaje en `body-large/on-surface-variant`, centrado
    - `contentDescription = "Cargando..."` para el container cuando se usa como skeleton (reutilizable)

- [x] **T7: Componente ClientCard** (AC-1)
  - [x] Crear `ui/components/ClientCard.kt` — `@Composable fun ClientCard(client: Client, onClick: () -> Unit, modifier: Modifier = Modifier)`:
    - `ElevatedCard` con `elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)`, `shape = MaterialTheme.shapes.medium` (16dp per DESIGN.md), `onClick = onClick`
    - Padding interno: `14dp vertical / 16dp horizontal` (UX-DR4)
    - Fila principal: nombre en `body-large`, teléfono en `body-medium/on-surface-variant`, saldo en `title-medium/primary` (`"$%,.2f".format(...)` con separador de miles)
    - `SyncIcon` alineado a la derecha (estado según `client.syncStatus`)
    - `Modifier.semantics(mergeDescendants = true)` para TalkBack — anuncia como unidad (UX-DR20)
    - Saldo formateado: `"$${client.balance.setScale(2, RoundingMode.HALF_UP).toPlainString()}"` — NUNCA Double ni Float

- [x] **T8: Componente FilterChipRow** (AC-1)
  - [x] Crear `ui/components/FilterChipRow.kt` — `@Composable fun FilterChipRow<T>(chips: List<FilterChipData<T>>, selectedChip: T?, onChipSelected: (T?) -> Unit, modifier: Modifier = Modifier)` donde `data class FilterChipData<T>(val id: T, val label: String)`:
    - `LazyRow` horizontal con `horizontalArrangement = Arrangement.spacedBy(8.dp)`
    - `FilterChip` M3 por cada chip; chip activo: `selected = true` → M3 maneja fondo `primary-variant` + `leadingIcon = checkmark` automáticamente via `FilterChip(selected = true, leadingIcon = Icons.Filled.Check)`
    - Solo un chip activo a la vez; tap en chip activo lo deselecciona (toggle)
    - Para S-11 v1: se pasa lista vacía (el proveedor solo hace búsqueda por nombre; chips de filtro avanzados en historias posteriores)

### Android — UI Screen S-11

- [x] **T9: ClientListViewModel** (AC-1, AC-2, AC-3, AC-4)
  - [x] Crear `ui/screens/clients/ClientListViewModel.kt` — `@HiltViewModel`:
    ```kotlin
    @HiltViewModel
    class ClientListViewModel @Inject constructor(
        private val clientRepository: ClientRepository,
    ) : ViewModel() {
        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        val clients: StateFlow<List<Client>> = _searchQuery
            .flatMapLatest { query ->
                if (query.isBlank()) clientRepository.getAllClients()
                else clientRepository.searchClients(query)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun onSearchQueryChange(query: String) { _searchQuery.value = query }
        fun onSearchClear() { _searchQuery.value = "" }
    }
    ```
  - [x] Importar `kotlinx.coroutines.flow.flatMapLatest` (experimental-stable en kotlinx-coroutines 1.7+)

- [x] **T10: ClientListScreen — S-11 completa** (AC-1, AC-2, AC-3, AC-4)
  - [x] Reemplazar el placeholder en `ui/screens/clients/ClientListScreen.kt` con implementación completa:
    - `@Composable fun ClientListScreen(modifier: Modifier = Modifier, viewModel: ClientListViewModel = hiltViewModel())`
    - `SearchBar` M3 en la parte superior con `active`/`onActiveChange`, `query`/`onQueryChange`
    - Debajo del SearchBar: `FilterChipRow(chips = emptyList(), ...)` — expansible pero sin chips en v1
    - Contenido principal:
      - Si `clients.isEmpty()` y `searchQuery.isBlank()`: `EmptyState` con mensaje "Aún no hay clientes. Toca + para agregar el primero."
      - Si `clients.isEmpty()` y `searchQuery.isNotBlank()`: texto "No se encontraron clientes con ese nombre."
      - Si `clients.isNotEmpty()`: `LazyColumn` con `ClientCard` por cada cliente; `onClick = { /* Historia 2.3: navegar a S-12 */ }` con Snackbar provisional "Perfil de cliente — próximamente"
    - `PullToRefreshBox` (M3 experimental): `@OptIn(ExperimentalMaterial3Api::class)`, `isRefreshing = false`, `onRefresh = { /* Historia 4.x: trigger sync */ }` — pull-to-refresh visual sin lógica real por ahora
    - `Scaffold` con `floatingActionButton = { ExtendedFloatingActionButton(text = { Text("+") }, icon = { Icon(Icons.Filled.Add, "Nueva orden") }, onClick = { /* Historia 2.2: navegar a S-13 */ }) }` — FAB visible, onClick = Snackbar provisional "Alta de cliente — próximamente"
    - FAB permanece visible en empty state (UX-DR8)
  - [x] Verificar que `NavGraph.kt` ya llama a `ClientListScreen()` — no requiere cambios

- [x] **T11: Verificación** (todos los ACs)
  - [x] `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
  - [x] `./gradlew :app:testDebugUnitTest` — 0 failures (agregar test para `ClientListViewModel`: verificar que `searchQuery` filtra correctamente la lista)
  - [x] Verificar en emulador/dispositivo: tab Clientes muestra empty state → insertar cliente en Room debug → aparece en lista → escribir en SearchBar → filtra en tiempo real

## Dev Notes

### Contexto y decisiones previas relevantes

**Stack completo establecido en historias anteriores:**
- Room 2.8.4 (stable), Hilt 2.56.2, Compose BOM 2026.06.00
- `BigDecimalConverter` e `InstantConverter` ya registrados en `SumitrackDatabase` (Historia 1.3)
- Patron ViewModel: `@HiltViewModel` + `StateFlow<UiState>` o `StateFlow<List<T>>`
- Patron navigation: `NavGraph.kt` ya incluye `ClientListScreen()` — solo reemplazar el placeholder
- Patron nav event: usar `Channel<Unit>` + `receiveAsFlow()` para navegación desde ViewModel (Historia 1.4)

**Converter Retrofit — NOTA CRÍTICA (Historia 1.4):**
- Usar `com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0` (oficial)
- NO usar `com.jakewharton.retrofit2:retrofit2-kotlinx-serialization-converter` (no está en Maven Central)
- Esta historia no requiere Retrofit — es puramente Room-local

### Reglas arquitectónicas obligatorias (AR-5, AR-6, AR-16, AR-17)

- **UUID generado en Android** — `UUID.randomUUID().toString()` antes de persistir en Room
- **Toda entidad sincronizable requiere** exactamente estos campos: `id` (TEXT PK), `fk_tenant` (TEXT), `created_at` (INTEGER via InstantConverter), `updated_at` (INTEGER), `sync_status` (TEXT)
- **Columnas en DB deben ser snake_case** (AR-16) → usar `@ColumnInfo(name = "snake_case")` en la entidad Room para propiedades camelCase. Ejemplo:
  ```kotlin
  @ColumnInfo(name = "fk_tenant") val fkTenant: String,
  @ColumnInfo(name = "created_at") val createdAt: Instant,
  @ColumnInfo(name = "updated_at") val updatedAt: Instant,
  @ColumnInfo(name = "sync_status") val syncStatus: String,
  ```
- **Montos monetarios siempre `BigDecimal`** (AR-17) — nunca Double ni Float; display formateado a 2 decimales

### Room — bump de versión y migración

**CRÍTICO:** Al agregar `ClientEntity`, `SumitrackDatabase.version` DEBE cambiar de `1` a `2`. Room verifica la firma de la BD al startup y fallará si la versión no coincide con las entidades registradas. La migración debe aplicarse correctamente.

```kotlin
// Migrations.kt — agregar antes de ALL
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS clients (
                id TEXT NOT NULL PRIMARY KEY,
                fk_tenant TEXT NOT NULL,
                name TEXT NOT NULL,
                phone TEXT NOT NULL,
                rfc TEXT,
                address TEXT,
                notes TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                sync_status TEXT NOT NULL DEFAULT 'synced'
            )
        """.trimIndent())
    }
}
val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
```

**Fallback destructivo opcional (solo dev):** Si instalas la app desde cero en el emulador con `Room.databaseBuilder(...).fallbackToDestructiveMigration()`, no necesitas la migración. Pero producción SIEMPRE necesita la migración explícita. Implementar correctamente desde el inicio.

### Balance de cliente — stub CalculateClientBalanceUseCase

El saldo real requiere sumar las ventas en estado PENDING o PARTIAL del cliente. En Historia 2.1, no existe `SaleEntity` todavía — el UseCase retorna `BigDecimal.ZERO` como stub.

```kotlin
// domain/usecases/CalculateClientBalanceUseCase.kt
class CalculateClientBalanceUseCase @Inject constructor(
    // TODO Historia 3.x: inyectar SaleRepository
    // private val saleRepository: SaleRepository
) {
    // Suma ventas en estado PENDING y PARTIAL para el cliente dado.
    // Retorna BigDecimal.ZERO hasta que SaleEntity esté disponible (Historia 3.x).
    operator fun invoke(clientId: String): BigDecimal = BigDecimal.ZERO
}
```

El balance se muestra como "$0.00" en todas las ClientCards — es el comportamiento correcto para un sistema sin ventas registradas.

### SyncIcon — colores exactos del tema (Historia 1.3)

`Color.kt` ya define:
- `val SyncOk = Color(0xFF00BCD4)` — cian para estado sincronizado
- `val SyncPending = Color(0xFFFF7043)` — naranja para pendiente

IMPORTANTE (DESIGN.md): `sync-ok` es color de ícono, no de texto. No usar como fondo de badge ni como color de texto. Solo para el icono `SyncIcon`.

Para el ícono de "Sincronizado", usar `Icons.Filled.CloudDone` o `Icons.Filled.Cloud` + check superpuesto. Si no hay un ícono adecuado en Material Icons, usar `Icons.Outlined.CloudDone` (sincronizado) y `Icons.Outlined.Cloud` (pendiente):

```kotlin
@Composable
fun SyncIcon(isSynced: Boolean, modifier: Modifier = Modifier) {
    val (icon, tint, desc) = if (isSynced) {
        Triple(Icons.Outlined.CloudDone, SyncOk, "Sincronizado con la nube.")
    } else {
        Triple(Icons.Outlined.Cloud, SyncPending, "Pendiente de sincronizar.")
    }
    Icon(
        imageVector = icon,
        contentDescription = desc,
        tint = tint,
        modifier = modifier.size(20.dp),
    )
}
```

`Icons.Outlined.CloudDone` y `Icons.Outlined.Cloud` están en `androidx.compose.material.icons:material-icons-extended`. Verificar que la dependencia esté disponible vía el BOM; si no, usar `Icons.Default.Cloud` como fallback.

### SearchBar M3 — API actual (Compose BOM 2026.06.00)

El `SearchBar` M3 en Compose 1.11.x tiene API: `query`, `onQueryChange`, `onSearch`, `active`, `onActiveChange`. Para búsqueda en tiempo real (sin "Buscar"), se llama `onQueryChange` en cada keystroke.

```kotlin
var searchActive by remember { mutableStateOf(false) }
val query by viewModel.searchQuery.collectAsStateWithLifecycle()

SearchBar(
    query = query,
    onQueryChange = { viewModel.onSearchQueryChange(it) },
    onSearch = { viewModel.onSearchQueryChange(it) },
    active = searchActive,
    onActiveChange = { searchActive = it },
    placeholder = { Text("Buscar clientes...") },
    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
    trailingIcon = {
        if (query.isNotBlank()) {
            IconButton(onClick = { viewModel.onSearchClear() }) {
                Icon(Icons.Default.Clear, "Limpiar búsqueda")
            }
        }
    },
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
) {
    // Contenido del overlay expandido — vacío en S-11 (no suggestions)
}
```

NOTA: En algunas versiones de M3, `SearchBar` es `@ExperimentalMaterial3Api`. Si el compilador lo pide, usar `@OptIn(ExperimentalMaterial3Api::class)`.

### PullToRefresh M3

`PullToRefreshBox` es `@ExperimentalMaterial3Api` en Compose 1.11.x. Envolver el contenido de la pantalla:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
val pullState = rememberPullToRefreshState()

PullToRefreshBox(
    state = pullState,
    isRefreshing = false,  // En Historia 4.x: conectar con SyncManager
    onRefresh = { /* TODO Historia 4.x: trigger manual sync */ },
) {
    // LazyColumn / EmptyState
}
```

Si la API no compila, omitir pull-to-refresh en esta historia — no es un AC.

### FilterChipRow — chip activo en M3

```kotlin
FilterChip(
    selected = (selectedChip == chip.id),
    onClick = { onChipSelected(if (selectedChip == chip.id) null else chip.id) },
    label = { Text(chip.label) },
    leadingIcon = if (selectedChip == chip.id) {
        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
    } else null,
)
```

Para S-11 v1: llamar `FilterChipRow(chips = emptyList(), selectedChip = null, onChipSelected = {})` — visible pero vacía.

### Archivos que se modifican (UPDATE) — no romper comportamiento existente

| Archivo | Estado actual | Cambio en esta historia |
|---------|--------------|------------------------|
| `SumitrackDatabase.kt` | `version = 1`, solo `SettingsEntity` | Añadir `ClientEntity`, `version = 2` |
| `Migrations.kt` | `ALL = emptyArray()` | Añadir `MIGRATION_1_2`, actualizar `ALL` |
| `DatabaseModule.kt` | Solo `provideSettingsDao` | Añadir `provideClientDao` |
| `ClientListScreen.kt` | Placeholder 1-liner | Reemplazar completamente |

**NO tocar:**
- `AppNavHost.kt` — navegación exterior ya funciona
- `NavGraph.kt` — ya llama `ClientListScreen()` sin parámetros; no necesita cambios
- `SettingsDao.kt`, `SettingsRepository.kt` — ya funcionan correctamente
- Tema visual (`Color.kt`, `Type.kt`, `Shape.kt`) — ya definido en Historia 1.3

### Formateo de saldo en ClientCard

```kotlin
// CORRECTO — BigDecimal con separador de miles y 2 decimales
val formattedBalance = NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(client.balance)
// Resultado: "$0.00" o "$1,250.00"

// INCORRECTO — nunca usar Double ni Float
val wrong = "$%.2f".format(client.balance.toDouble())  // ❌ pérdida de precisión
```

### Navegación placeholder para S-12 y S-13

En esta historia, el tap en ClientCard y el FAB "+" no navegan a pantallas reales:

```kotlin
// ClientCard.onClick — Historia 2.3 implementará S-12
onClick = {
    scope.launch {
        snackbarHostState.showSnackbar("Perfil de cliente — disponible en la siguiente versión")
    }
}

// FAB onClick — Historia 2.2 implementará S-13
onClick = {
    scope.launch {
        snackbarHostState.showSnackbar("Alta de cliente — disponible en la siguiente versión")
    }
}
```

### Compatibilidad Room — flatMapLatest

`flatMapLatest` requiere `kotlinx-coroutines-core` ≥ 1.7 y puede estar marcado como `@ExperimentalCoroutinesApi`. Si el compilador advierte:
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
val clients: StateFlow<List<Client>> = _searchQuery.flatMapLatest { ... }
```

La dependencia `kotlinx-coroutines-android` ya está incluida transitivamente vía Hilt y ViewModel.

### Para testing con datos locales (sin sync)

Para verificar el comportamiento en el emulador SIN datos de API, insertar un cliente directamente en Room via un test o en un botón de debug oculto:

```kotlin
// En un test o en un init block de un debug ViewModel
val testClient = ClientEntity(
    id = UUID.randomUUID().toString(),
    fkTenant = "test-tenant-id",
    name = "Ferretería El Clavo",
    phone = "555-1234",
    rfc = null, address = null, notes = null,
    createdAt = Instant.now(),
    updatedAt = Instant.now(),
    syncStatus = "synced",
)
clientDao.upsertAll(listOf(testClient))
```

Los datos persisten en SQLite y aparecerán en S-11. No se perderán al reiniciar la app (solo al desinstalar).

## Dev Agent Record

### Implementation Plan

Secuencia de implementación: T1 (dominio) → T2 (Room entities/DAO) → T3 (DB v2 + migración) → T4 (repositorio) → T5–T8 (componentes UI) → T9 (ViewModel) → T10 (Screen) → T11 (tests + build).

Decisiones clave:
- Agregado `material-icons-extended` (BOM-managed, sin versión explícita) para `Icons.Outlined.CloudDone` / `Icons.Outlined.Cloud` tal como especifica la historia
- Agregado `kotlinx-coroutines-test 1.9.0` a test deps para testear StateFlow con `StandardTestDispatcher`
- `SharingStarted.WhileSubscribed(5_000)` requiere un subscriber activo en tests → patrón `val job = launch { viewModel.clients.collect {} }; advanceUntilIdle()`
- `Locale("es", "MX")` depreciado → reemplazado con `Locale.Builder().setLanguage("es").setRegion("MX").build()`
- `SearchBar` M3 API usa `SearchBarDefaults.InputField` como slot (API 2026.06.00 Compose BOM)
- `PullToRefreshBox` de `androidx.compose.material3.pulltorefresh` compiló correctamente con `@OptIn(ExperimentalMaterial3Api::class)`
- `@OptIn(ExperimentalCoroutinesApi::class)` necesario en `ClientListViewModel` para `flatMapLatest`

### Debug Log

**Fallo 1 — tests `ClientListViewModelTest`:**
- Tests `clients emits all clients when searchQuery is blank` y `clients filters by name when searchQuery is non-blank` fallaban con `AssertionError: expected: 2 but was: 0`
- Causa: `SharingStarted.WhileSubscribed(5_000)` no inicia el upstream Flow sin subscriber activo; leer `.value` directamente retorna `initialValue = emptyList()`
- Fix: Agregar `val job = launch { viewModel.clients.collect {} }; advanceUntilIdle()` antes de setear datos en el fake DAO para activar la suscripción

**Warning — `Locale("es", "MX")` deprecated:**
- Warning del compilador en `ClientCard.kt` línea 66
- Fix: `Locale.Builder().setLanguage("es").setRegion("MX").build()`

### Completion Notes

Historia implementada completa. 24 tests pasan (0 fallos). BUILD SUCCESSFUL.

- AC-1 ✅: S-11 muestra ClientCard con nombre, teléfono, saldo $0.00, SyncIcon; SearchBar M3; FilterChipRow (vacía v1); FAB "+"; PullToRefresh visual
- AC-2 ✅: búsqueda en tiempo real via `flatMapLatest` sobre Room Flow; `searchByNameAsFlow` con LIKE en SQLite
- AC-3 ✅: EmptyState con mensaje diferenciado (sin clientes vs sin resultados de búsqueda)
- AC-4 ✅: Room Flow → respuesta inmediata del SQLite local (bien por debajo de 10s)
- `CalculateClientBalanceUseCase` stub retorna `BigDecimal.ZERO`; marcado TODO para Historia 3.x
- FAB "+" y tap en ClientCard muestran Snackbar provisional (Historia 2.2 y 2.3 respectivamente)

## File List

### Archivos creados (NEW)
- `android/app/src/main/java/com/sumitrack/android/domain/models/SyncStatus.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/models/Client.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/usecases/CalculateClientBalanceUseCase.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/entities/ClientEntity.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/ClientDao.kt`
- `android/app/src/main/java/com/sumitrack/android/data/repositories/ClientRepository.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/components/SyncIcon.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/components/EmptyState.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/components/ClientCard.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/components/FilterChipRow.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientListViewModel.kt`
- `android/app/src/test/java/com/sumitrack/android/domain/models/SyncStatusTest.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/ClientListViewModelTest.kt`

### Archivos modificados (UPDATE)
- `android/app/src/main/java/com/sumitrack/android/data/local/SumitrackDatabase.kt` — ClientEntity añadida, version = 2
- `android/app/src/main/java/com/sumitrack/android/data/local/Migrations.kt` — MIGRATION_1_2 implementada, ALL = arrayOf(MIGRATION_1_2)
- `android/app/src/main/java/com/sumitrack/android/di/DatabaseModule.kt` — provideClientDao añadido
- `android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientListScreen.kt` — placeholder reemplazado con S-11 completa
- `android/app/build.gradle.kts` — material-icons-extended + kotlinx-coroutines-test añadidos
- `android/gradle/libs.versions.toml` — androidx-material-icons-extended + kotlinx-coroutines-test añadidos

## Change Log

- **2026-07-06** — Historia 2.1 implementada completa (Status: review)
  - NEW: dominio — `SyncStatus`, `Client`, `CalculateClientBalanceUseCase` (stub)
  - NEW: Room — `ClientEntity`, `ClientDao`, `Migrations.MIGRATION_1_2`
  - UPDATE: `SumitrackDatabase` version 1 → 2 con `ClientEntity`
  - UPDATE: `DatabaseModule` — `provideClientDao` añadido
  - NEW: repositorio — `ClientRepository` con `flatMapLatest` sobre Room Flow
  - NEW: componentes UI — `SyncIcon`, `EmptyState`, `ClientCard`, `FilterChipRow`
  - NEW: `ClientListViewModel` con `searchQuery` StateFlow + `flatMapLatest`
  - UPDATE: `ClientListScreen` — placeholder reemplazado con S-11 completa (SearchBar M3, FilterChipRow, LazyColumn, EmptyState, FAB, PullToRefreshBox)
  - NEW: tests — `SyncStatusTest` (5 casos), `ClientListViewModelTest` (6 casos, FakeClientDao)
  - UPDATE: deps — `material-icons-extended` (BOM), `kotlinx-coroutines-test 1.9.0`
  - Build: 24 tests ✅, BUILD SUCCESSFUL

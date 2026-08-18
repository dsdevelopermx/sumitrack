---
baseline_commit: 1e65f4658fc047cf13672089fc5e123cf60d5942
---

# Story 4.2: Pull Inicial y Folio del Servidor al Hacer Login

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

Como proveedor,
quiero que al iniciar sesión todos mis datos se descarguen al teléfono,
para que pueda operar offline de inmediato con mi historial completo disponible.

## Acceptance Criteria

**AC-1 — Pull al hacer login**

**Dado** que el proveedor hace login exitoso (con conexión)
**Cuando** la sesión se establece
**Entonces** `PullService` descarga el delta de cada entidad vía `GET /api/v1/sync/pull/{entity}?since={last_sync_at}` (**9 entidades — ver Dev Notes**: clientes, productos, variantes, ventas, items_venta, parcialidades, cobros, creditos_a_favor, settings)
**Y** se muestra un indicador de progreso durante la descarga sin bloquear la navegación

**AC-2 — Primer login (pull completo)**

**Dado** que es el primer login (sin `last_sync_at` almacenado)
**Cuando** ejecuta el pull
**Entonces** descarga la totalidad de los datos del tenant desde el servidor

**AC-3 — Folio confirmado del servidor**

**Dado** que el pull inicial completa exitosamente
**Cuando** termina la descarga
**Entonces** el último folio confirmado del servidor se refleja en el contador local de folios
**Y** los folios generados offline desde ese momento siguen la secuencia `{serie_folio}{contador++}`

**AC-4 — Pull incremental (delta)**

**Dado** que `last_sync_at` está almacenado por entidad en SQLite
**Cuando** el proveedor ejecuta un pull posterior (el mismo motor, disparado de nuevo)
**Entonces** solo se descargan registros modificados en el servidor después de `last_sync_at` (pull incremental/delta)

**AC-5 — Persistencia y contrato del servidor**

**Dado** que el servidor recibe `GET /api/v1/sync/pull/{entity}?since={iso_timestamp}`
**Cuando** `SyncController` procesa la solicitud
**Entonces** devuelve solo los registros con `updated_at > since` del schema del tenant; respuesta directa como lista sin wrapper (sin `since` → devuelve todo, para el primer pull)

### Fuera de alcance en esta historia (explícito)

- **Botón manual "Sincronizar ahora" (S-14, Historia 5.1)** — esta historia construye el motor de pull reutilizable (AC-4 ya lo exige: "el mismo motor, disparado de nuevo"), pero la UI del botón en Configuración es 100% Historia 5.1. El mecanismo que 5.1 disparará es el mismo `PullWorker`/`PullService` de esta historia, sin cambios.
- **Detección y resolución real de conflictos** (`server.updated_at > client.updated_at`, `ConflictService`, `ConflictLogEntity`, S-15) — 100% Historia 4.4, mismo criterio que el push ciego de Historia 4.1. Esta historia agrega solo un guard interino y acotado (ver Dev Notes → "Por qué el pull salta los registros pending") — no es detección de conflictos real, solo evita el caso de pérdida de datos más obvio mientras 4.4 no existe.
- **Verificación de `expiresAt` del JWT** — `deferred-work.md` lo apuntaba a esta historia como sugerencia (no como AC de `epics.md`, que no lo menciona en ningún criterio de 4.2). Se re-difiere explícitamente para no inflar el alcance de una historia ya grande — queda anotado de nuevo en `deferred-work.md`.
- **Retirar `SettingsApiService.getSettings()` / `SettingsRepository.downloadAndCacheSettings()`** — esta historia deja de *llamarlos* desde `AuthRepository.login()` (reemplazados por el pull unificado de `settings`), pero no borra el endpoint/método — quedan sin uso, candidatos a limpieza en una historia futura una vez confirmado que el pull unificado los reemplaza sin regresión.
- **Multi-dispositivo real** — igual que toda la Epic 4 hasta ahora: v1 es de un solo dispositivo por tenant (`architecture.md`, sección de Folio: "Riesgo controlado en v1... no hay colisión posible").

## Tasks / Subtasks

### Backend — endpoint de pull (extiende `SyncService`/`SyncController` de Historia 4.1)

- [x] **T1: DTOs de respuesta del pull (9 tipos)** (AC-1, AC-5) — un DTO de respuesta por entidad en `Models/Responses/{Entity}PullResponseItem.cs`, mismos campos que su `{Entity}SyncRequest` de Historia 4.1 (sin `SyncStatus` — igual que en push, es un concepto local que no se transmite):
    ```csharp
    public class ClientPullResponseItem
    {
        public Guid Id { get; set; }
        public Guid FkTenant { get; set; }
        public string Name { get; set; } = string.Empty;
        public string Phone { get; set; } = string.Empty;
        public string? Rfc { get; set; }
        public string? Address { get; set; }
        public string? Notes { get; set; }
        public DateTime CreatedAt { get; set; }
        public DateTime UpdatedAt { get; set; }
    }
    // ... 7 más (Product, ProductVariant, Sale, SaleItem, Installment, Payment, CreditBalance)
    // más SettingPullResponseItem (Key, Value, CreatedAt, UpdatedAt — sin Id/FkTenant, mismo
    // patrón de excepción que Setting.cs desde Historia 4.1)
    ```

- [x] **T2: `ISyncService.PullAsync` + `SyncService` — 9 métodos `PullXxxAsync`** (AC-1, AC-2, AC-4, AC-5) — mismo patrón de dispatch por switch que `PushAsync`, pero de lectura en vez de escritura:
    ```csharp
    Task<object?> PullAsync(string entity, DateTime? since, CancellationToken cancellationToken = default);
    ```
    Cada `PullXxxAsync(DateTime? since, CancellationToken ct)`:
    ```csharp
    private async Task<List<ClientPullResponseItem>> PullClientesAsync(DateTime? since, CancellationToken ct)
    {
        var query = _ctx.Clients.AsQueryable();
        if (since.HasValue) query = query.Where(e => e.UpdatedAt > NormalizeUtc(since.Value));
        return await query.Select(e => new ClientPullResponseItem { Id = e.Id, FkTenant = e.FkTenant, Name = e.Name, Phone = e.Phone, Rfc = e.Rfc, Address = e.Address, Notes = e.Notes, CreatedAt = e.CreatedAt, UpdatedAt = e.UpdatedAt }).ToListAsync(ct);
    }
    ```
    **Sin filtro explícito de `fk_tenant`** — el aislamiento ya lo garantiza `TenantSchemaInterceptor` vía `search_path` (schema-por-tenant físico, no una tabla compartida con `WHERE`); mismo razonamiento ya aplicado a `Settings` desde Historia 4.1. `NormalizeUtc` (ya existe en `SyncService.cs` desde 4.1) se reutiliza tal cual para el parámetro `since`. `Settings` sigue el mismo patrón sin `FkTenant`.

- [x] **T3: `SyncController` — acción GET de pull** (AC-1, AC-5):
    ```csharp
    [HttpGet("pull/{entity}")]
    public async Task<IActionResult> Pull(string entity, [FromQuery] DateTime? since, CancellationToken cancellationToken)
    {
        var result = await _syncService.PullAsync(entity, since, cancellationToken);
        if (result is null) return NotFound(new ErrorResponse { Errors = [new ApiError { Code = "UNKNOWN_ENTITY", Message = $"Entidad '{entity}' no reconocida." }] });
        return Ok(result);
    }
    ```
    `[FromQuery] DateTime? since` — ASP.NET Core parsea el query string ISO-8601 automáticamente, sin parsing manual (a diferencia del `JsonElement` crudo que usa `Push`, porque aquí no hay body).

- [x] **T4: Tests backend (xUnit)** — al menos: pull sin `since` devuelve todos los registros; pull con `since` devuelve solo los `updated_at > since`; entidad desconocida devuelve 404 `UNKNOWN_ENTITY`; `settings` funciona pese a su llave distinta (`Key` en vez de `Id`); un registro con `updated_at` exactamente igual a `since` NO se incluye (`>` estricto, no `>=`, tal como dice AC-5 literal).

### Android — `PullService` + `PullWorker` (reutiliza infraestructura WorkManager/Hilt-Work de Historia 4.1)

- [x] **T5: `SyncMetadataEntity` + `SyncMetadataDao` — nueva tabla, `MIGRATION_7_8`** (AC-1, AC-4) — **no reutilizar `SettingsEntity`** para `last_sync_at` (ver Dev Notes → "Por qué `last_sync_at` no vive en `settings`"):
    ```kotlin
    @Entity(tableName = "sync_metadata")
    data class SyncMetadataEntity(
        @PrimaryKey val entity: String,
        @ColumnInfo(name = "last_sync_at") val lastSyncAt: Instant,
    )

    @Dao
    interface SyncMetadataDao {
        @Query("SELECT last_sync_at FROM sync_metadata WHERE entity = :entity LIMIT 1")
        suspend fun getLastSyncAt(entity: String): Instant?

        @Upsert
        suspend fun setLastSyncAt(row: SyncMetadataEntity)
    }
    ```
    `MIGRATION_7_8`: `CREATE TABLE IF NOT EXISTS sync_metadata (entity TEXT NOT NULL PRIMARY KEY, last_sync_at INTEGER NOT NULL)`. Bump `SumitrackDatabase` a `version = 8`, registrar en `Migrations.ALL`, agregar `SyncMetadataEntity::class` a `entities`, `syncMetadataDao()` abstracto, `provideSyncMetadataDao` en `DatabaseModule.kt`.

- [x] **T6: `SyncApiService` — 9 métodos `@GET pull/{entity}`** (AC-1, AC-5) — **reutiliza los mismos 9 DTOs de Android ya creados en Historia 4.1** (`ClientSyncDto`, `ProductSyncDto`, etc.) — sus campos ya son exactamente los que el pull necesita, sin crear DTOs nuevos del lado Android (a diferencia del backend, que sí necesita clases de Response C# separadas porque las `Request` ya tienen semántica de "esto sube"):
    ```kotlin
    @GET("api/v1/sync/pull/clientes") suspend fun pullClientes(@Query("since") since: String?): List<ClientSyncDto>
    // ... 8 métodos más, mismos 9 segmentos de URL que push
    ```

- [x] **T7: `PullService.kt`** (`sync/PullService.kt`, junto a `SyncManager.kt`) (AC-1, AC-2, AC-4) — recolecta y aplica el pull de las 9 entidades:
    ```kotlin
    @Singleton
    class PullService @Inject constructor(
        /* los 9 DAOs + SyncMetadataDao + SyncApiService */
    ) {
        suspend fun pullAll(tenantId: String): Boolean =
            pullClientes(tenantId) && pullProductos(tenantId) && /* ... */ && pullSettings()

        private suspend fun pullClientes(tenantId: String): Boolean {
            val since = syncMetadataDao.getLastSyncAt("clientes")
            val requestedAt = Instant.now() // capturado ANTES del request — ver Dev Notes
            return try {
                val remote = syncApiService.pullClientes(since?.toString())
                val pendingIds = clientDao.getPending(tenantId).map { it.id }.toSet()
                val toUpsert = remote.filterNot { it.id in pendingIds }.map { it.toEntity() }
                if (toUpsert.isNotEmpty()) clientDao.upsertAll(toUpsert)
                syncMetadataDao.setLastSyncAt(SyncMetadataEntity("clientes", requestedAt))
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        }
        // ... 8 métodos más, mismo patrón; pullSettings() usa settingsDao.getPending() (sin tenantId)
        // toEntity(): 9 extension functions Dto → Entity (inverso de los toSyncDto() de SyncManager.kt),
        // syncStatus = "synced" siempre (todo lo que el pull escribe viene confirmado por el servidor)
    }
    ```
    **Guard "salta los pending"** (decisión confirmada con el usuario): antes de aplicar `upsertAll`, se excluyen los ids que `getPending(tenantId)` (ya existe desde Historia 4.1 en los 9 DAOs) reporta como localmente pendientes — evita que un pull sobrescriba una edición local sin sincronizar. Ese registro simplemente no se toca en este pull; el push normal lo subirá en su siguiente corrida, y un pull *posterior* (una vez ya sincronizado) lo actualizará con normalidad.

- [x] **T8: `PullWorker.kt`** (`sync/workers/PullWorker.kt`) (AC-1) — `CoroutineWorker` de un solo disparo (no periódico, a diferencia de `PushWorker`), mismo patrón `@HiltWorker`/`@AssistedInject`:
    ```kotlin
    @HiltWorker
    class PullWorker @AssistedInject constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val pullService: PullService,
        @TenantId private val tenantId: Flow<String?>,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = try {
            val tenant = tenantId.first() ?: return Result.success()
            if (pullService.pullAll(tenant)) Result.success() else Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }
    ```

- [x] **T9: `AuthRepository.login()` — rediseño no-bloqueante** (AC-1) — implementado con un cambio adicional respecto al snippet original: `WorkManager` se inyecta directamente en el constructor (vía nuevo `di/SyncModule.kt`) en vez de `Context` + `WorkManager.getInstance(context)` inline — mantiene la clase teóricamente fake-able sin necesitar mockear `Context`. — quita el paso bloqueante de `settingsRepository.downloadAndCacheSettings(...)` (superado por el pull unificado de `settings`, ver "Fuera de alcance") y en su lugar encola `PullWorker` de forma fire-and-forget, sin esperar su resultado:
    ```kotlin
    class AuthRepository @Inject constructor(
        private val authApiService: AuthApiService,
        private val sessionManager: SessionManager,
        @ApplicationContext private val context: Context,
    ) {
        suspend fun login(username: String, password: String): Result<Unit> {
            val response = /* Step 1: autenticar — igual que hoy */
            sessionManager.saveToken(response.token) // Step 2

            // Step 3: encolar pull inicial, NO esperar su resultado — AC-1 exige que la
            // navegación no se bloquee por la descarga.
            val request = OneTimeWorkRequestBuilder<PullWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("pull-sync", ExistingWorkPolicy.REPLACE, request)

            return Result.success(Unit)
        }
    }
    ```
    **Cambio de comportamiento explícito** (ver Dev Notes → "Por qué se retira el bloqueo de settings del login"): hoy, si la descarga de settings falla, el login entero falla y el token se revierte. Con este cambio, el login se considera exitoso en cuanto el token se guarda — un pull inicial fallido se reintenta en background sin bloquear ni revertir la sesión. Este cambio lo exige la letra de AC-1 ("sin bloquear la navegación").
    `ExistingWorkPolicy.REPLACE` (no `KEEP`) — para que un pull manual futuro (Historia 5.1) pueda re-disparar el mismo `work` con prioridad, sin esperar a que uno anterior termine.

- [x] **T10: Indicador de progreso no bloqueante** (AC-1) — `PullSyncViewModel` nuevo (`ui/screens/PullSyncViewModel.kt`) expone `isPulling: StateFlow<Boolean>` derivado de `WorkManager.getWorkInfosForUniqueWorkFlow("pull-sync")`; `MainScreen.kt` lo consume y muestra un `LinearProgressIndicator` encima de `NavGraph`. — mínimo y acotado; observa `WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("pull-sync")`, mapeado a `Boolean` (`RUNNING`/`ENQUEUED` → true), expuesto vía un `ViewModel` liviano inyectado en `MainScreen.kt`; un `LinearProgressIndicator(Modifier.fillMaxWidth())` se muestra encima de `NavGraph(...)` dentro del `Scaffold` mientras esté activo. Ver Dev Notes → "Por qué el indicador es mínimo" — Historia 4.3 generalizará este patrón en el app bar.

- [x] **T11: `ValidateFolioUseCase.kt` — actualizar comentario (sin cambio de lógica)** (AC-3) — el comentario actual dice literalmente que AR-10 "depende de Epic 4 (sincronización), inexistente todavía"; eso ya no es cierto tras esta historia. **La fórmula (`saleDao.countSalesForTenant(tenantId) + 1`) no cambia** — ver Dev Notes → "Por qué AC-3 no necesita un contador nuevo" — se satisface automáticamente en cuanto `PullService` puebla `sales` localmente antes de que el proveedor pueda crear una venta nueva. Agregar un test que confirme que el folio considera ventas insertadas vía pull (no solo las creadas localmente).

### Tests

- [x] **T12: Tests unitarios Android (JVM)** — `PullServiceTest` (5 tests, mismo patrón que `SyncManagerTest` de 4.1: fakes de los 9 DAOs + `FakeSyncApiService` extendido con métodos `pullXxx` + `FakeSyncMetadataDao` nuevo): pull exitoso hace upsert y guarda `last_sync_at`; primer pull (`since = null`) llama al API sin el query param; pull posterior usa el `last_sync_at` guardado; **un registro con id `pending` localmente se salta y no se sobrescribe**; fallo de red deja `last_sync_at` sin actualizar y el método devuelve `false`. `ValidateFolioUseCaseTest` — nuevo caso con ventas "pulled" en la fuente de datos. **Sin test dedicado para `PullWorker` ni `AuthRepository`** — hallazgo durante la implementación: no existía ningún `AuthRepositoryTest` previo (ni `LoginViewModelTest`), y `AuthRepository` ahora depende de `WorkManager` (clase abstracta con decenas de miembros, no fake-able a mano sin una librería de mocking, que este proyecto no tiene instalada) además de `SessionManager` (envuelve `Context`/DataStore real). Mismo criterio ya aceptado para `PushWorker`/`PullWorker` desde Historia 4.1 — se documenta como límite de testeo consciente, no como omisión.

### Review Findings (2026-08-09)

- [x] [Review][Decision→Patch] Race de colisión de folio — resuelto: el usuario propuso un piso de folio dedicado y rápido en vez de depender del pull completo de `ventas`. Se agregó `GET /api/v1/sync/folio-count` (backend, `_ctx.Sales.CountAsync`) + `PullService.pullFolioCount()` (Android, primera llamada dentro de `pullAll`, independiente y mucho más rápida que las 9 entidades) que guarda el conteo del servidor en `SessionManager.folioBaseline` (vía la interfaz `FolioBaselineStore`, para mantener testeable sin `Context` real). `ValidateFolioUseCase` ahora usa `max(ventas locales, piso del servidor) + 1` — la ventana de colisión se acota a una sola llamada liviana en vez de depender de que el pull completo de `ventas` termine. Paginación completa de "últimas ventas" queda fuera de esta historia — es el mismo ítem ya diferido de "sin límite/paginación en el pull" (extendido, no una construcción nueva). [backend: SyncController.cs, SyncService.cs, FolioCountResponse.cs · android: PullService.kt, ValidateFolioUseCase.kt, SessionManager.kt, FolioBaselineStore.kt]
- [x] [Review][Patch] `pullAll()` usa `&&` (corto-circuito) — si una entidad falla, las siguientes en la cadena NUNCA se intentan en esa corrida, incluso si sus propios endpoints están sanos [android/.../sync/PullService.kt]
- [x] [Review][Patch] `last_sync_at` se avanza a "ahora" incondicionalmente aunque el guard de `pending` haya saltado registros — el registro del servidor correspondiente queda permanentemente inalcanzable en pulls futuros una vez que `since` ya lo superó [android/.../sync/PullService.kt]
- [x] [Review][Patch] `workManager.enqueueUniqueWork(...)` en `AuthRepository.login()` sin manejo de errores — si falla, la excepción se propaga tras haber guardado el token, dejando una sesión válida pero un `login()` que parece haber fallado [android/.../data/repositories/AuthRepository.kt]
- [x] [Review][Patch] Nombre de test engañoso — "primer pull (since null) llama al API sin query param" solo prueba que el fake recibió `null`, no que Retrofit realmente omite el query param en la URL real [android/.../sync/PullServiceTest.kt]
- [x] [Review][Patch] Sin test de aislamiento por tenant en los nuevos endpoints de pull — agregar uno que documente la misma limitación de EF InMemory ya establecida en `PushAsync_SeparateDbContexts_DoNotShareState` de Historia 4.1 [backend/tests/.../SyncServicePullTests.cs]
- [x] [Review][Defer] Sin paginación/límite superior en las queries de pull del backend (`ToListAsync()` sin acotar) [backend/src/Sumitrack.Api/Services/Sync/SyncService.cs] — deferred, extiende el mismo hardening ya diferido para push en Historia 4.1
- [x] [Review][Defer] Excepciones silenciadas sin logging/telemetría en `PullService`/`PullWorker` [android/.../sync/PullService.kt, PullWorker.kt] — deferred, misma clase ya diferida para el lado push en 4.1
- [x] [Review][Defer] Riesgo de integridad referencial cruzada: una venta saltada por el guard de `pending` mientras sus `sale_items` no lo son [android/.../sync/PullService.kt] — deferred, bajo riesgo práctico (ventas e items comparten transacción de creación)
- [x] [Review][Defer] `sync_metadata` sin `fk_tenant`, nada purga las tablas Room al hacer logout/cambio de tenant en el mismo dispositivo [android/.../data/local/entities/SyncMetadataEntity.kt] — deferred, extiende el gap ya diferido desde Historia 1.4/2.1 ("caché local sin purga en logout") a la nueva tabla
- [x] [Review][Defer] `getPending`+`upsertAll` no son atómicos en `PullService` — ventana estrecha de carrera con una edición local concurrente o con `PushWorker` corriendo en paralelo [android/.../sync/PullService.kt] — deferred, misma clase de riesgo ya aceptada para la carrera análoga del lado push en 4.1
- [x] [Review][Defer] Un registro individual malformado del servidor (timestamp inválido, etc.) falla el pull completo de esa entidad vía el catch-all de excepción, y se reintentará para siempre porque `since` nunca avanza más allá de él [android/.../sync/PullService.kt] — deferred, baja probabilidad práctica dado que la validación de campo agregada al push en la revisión de 4.1 ya evita que datos malformados lleguen a persistirse en el servidor
- [x] [Review][Defer] `last_sync_at` se graba con el reloj del dispositivo, no uno del servidor — un device con reloj desincronizado podría en teoría perder registros en un pull delta futuro [android/.../sync/PullService.kt] — deferred, riesgo bajo en v1 (Android sincroniza NTP por defecto)

## Dev Notes

### Las 9 entidades del pull — mismo alcance que el push de Historia 4.1

Confirmado con el usuario: aunque `epics.md`/`architecture.md` listan literalmente solo 6 entidades para el pull (clientes, productos, ventas, parcialidades, cobros, settings — el mismo gap ya corregido para el push en 4.1), esta historia pull-ea las 9: se agregan `variantes`, `items_venta`, `creditos_a_favor`. Sin ellas, un pull inicial dejaría ventas sin sus items y productos sin variantes — la app quedaría inutilizable offline tras una instalación nueva pese a que el AC-2 promete "la totalidad de los datos del tenant". Mismos 9 segmentos de URL que el push (`clientes`, `productos`, `variantes`, `ventas`, `items_venta`, `parcialidades`, `cobros`, `creditos_a_favor`, `settings`).

### Por qué el pull salta los registros `pending` (guard interino, no detección de conflictos real)

Confirmado con el usuario: un pull que hace `upsertAll` ciego sobre las mismas 9 tablas que usa el push sobrescribiría una edición local sin sincronizar (`sync_status = pending`) con la versión del servidor, marcándola `synced` — la edición se pierde sin aviso y nunca se reintenta el push, porque ya no aparece en `getPending()`. La detección real de conflictos (`server.updated_at > client.updated_at`, `ConflictService`, S-15) es 100% Historia 4.4. Mientras tanto, el guard mínimo es: si el id ya está `pending` localmente, el pull no lo toca — se deja que el push normal lo suba primero, y un pull posterior (ya sincronizado) lo actualiza con normalidad. Esto reutiliza `getPending(tenantId)` (ya existe en los 9 DAOs desde Historia 4.1), sin agregar ninguna query nueva.

### Por qué `last_sync_at` no vive en `settings`

`SettingsEntity`/`SettingsDao` ya existen y podrían parecer el lugar obvio para guardar `last_sync_at` por entidad (mismo patrón que `serie_folio`). **No se reutiliza** porque `settings` es una tabla de negocio que también se sincroniza: cualquier fila que se marque `pending` ahí se sube al servidor en la siguiente corrida de `PushWorker` (`SyncManager.pushSettings()`, Historia 4.1). Meter metadatos internos de sync (`last_sync_at_clientes`, etc.) en esa misma tabla los expondría a subirse al servidor y aparecer en el `settings` de *otros* dispositivos del mismo tenant — contaminación de datos de negocio con estado interno del motor de sync. Se crea `SyncMetadataEntity`/`sync_metadata` como tabla nueva, dedicada, que el push NUNCA toca (no tiene DAO `getPending`, no participa en `SyncManager`).

### Por qué AC-3 no necesita un contador nuevo

`architecture.md` (AR-10) describe "almacena el contador en SQLite y genera consecutivos localmente (`contador++`)", lo que sugiere un contador mutable separado. Pero `ValidateFolioUseCase` (desde antes de esta historia) ya usa `saleDao.countSalesForTenant(tenantId) + 1` — un conteo derivado de las filas reales de `sales`, no un entero separado que pueda desincronizarse. Ese patrón ya establecido se mantiene sin cambios: en cuanto `PullService` puebla `sales` localmente (parte del pull de las 9 entidades, AC-1/AC-2), el conteo ya incluye las ventas pulled y el folio siguiente es automáticamente correcto — sin necesitar un valor separado que rastrear, sin riesgo de drift entre un contador y las filas reales. El único cambio real es que el comentario del archivo (que decía "AR-10... inexistente todavía") queda obsoleto y se actualiza.

### Por qué se retira el bloqueo de settings del login

Hoy, `AuthRepository.login()` hace `Step 3: settingsRepository.downloadAndCacheSettings(token)` de forma bloqueante — si falla, el login completo falla y el token se revierte (`sessionManager.clearToken()`). AC-1 exige explícitamente que el pull "no bloquee la navegación", lo que es incompatible con mantener cualquier descarga bloqueante dentro de `login()`. Dado que el pull unificado de esta historia ya incluye `settings` como una de las 9 entidades, la descarga dedicada de settings (`SettingsApiService.getSettings`/`SettingsRepository.downloadAndCacheSettings`) queda redundante — se deja de invocar desde el login (no se borra el código, ver "Fuera de alcance"). El login pasa a considerarse exitoso en cuanto el token se guarda; el pull corre en background vía `PullWorker` con su propio retry/backoff, sin poder revertir la sesión si falla.

### Por qué el indicador de progreso es mínimo

AC-1 solo exige "se muestra un indicador de progreso... sin bloquear la navegación" — no especifica su forma. No existe ningún patrón de "operación en background, no bloqueante" en la app todavía (los `isLoading` existentes en `LoginViewModel`/`OrderSummaryViewModel`/etc. son todos bloqueantes, deshabilitan la interacción). Historia 4.3 (la siguiente) construye explícitamente el patrón "definitivo" (`LinearProgressIndicator` en la app bar + `Snackbar` al completar/fallar, ver `epics.md` líneas 774-776) — construir ese patrón completo aquí duplicaría trabajo que 4.3 hará de todas formas. Esta historia entrega lo mínimo que satisface la letra del AC: una barra observando `WorkInfo` del `PullWorker`, en `MainScreen.kt`. Historia 4.3 puede reemplazarla o generalizarla sin fricción.

### Backend — reutilización del patrón de `SyncService.cs` (Historia 4.1)

`SyncService.cs` ya tiene `ITenantContext _tenantContext`, `TenantDbContext _ctx`, y el helper `NormalizeUtc(DateTime)` — todos reutilizables tal cual para `PullAsync`. El tenant nunca se toma de un parámetro de query ni de ningún lado del request — se resuelve igual que en push, vía `ResolveTenantId()` (aunque en la práctica el pull ni siquiera necesita filtrar por tenant explícitamente, porque el aislamiento ya lo da el schema). El árbol de archivos *planeado* en `architecture.md` (líneas 492-498, 544-545) menciona `FolioService.cs`/`DeltaSyncHelper.cs`/una capa `Repositories/` que **nunca se construyeron** en la implementación real de 4.1 — esta historia sigue el árbol *real* (todo dentro de `SyncService.cs`), no el aspiracional.

### Testing

- **Backend**: xUnit + `Microsoft.EntityFrameworkCore.InMemory`, mismo patrón que `SyncServiceTests.cs` de Historia 4.1.
- **Android**: JVM puro, reutilizar `FakeSyncApiService`/los 9 Fake DAOs ya existentes desde 4.1, agregándoles lo necesario para simular respuestas de pull. `PullWorker` sin test dedicado por la misma razón ya documentada para `PushWorker` en 4.1 (`TestListenableWorkerBuilder` requiere `Context` real, inviable sin Robolectric).

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Historia 4.2, FR-32/33, AR-9/AR-10] — ACs, requisitos funcionales y reglas de arquitectura verbatim.
- [Source: _bmad-output/planning-artifacts/architecture/architecture.md#Sincronización Offline, #Folio de Venta, #Reglas Obligatorias] — diseño de pull/folio, convenciones JSON, endpoints.
- [Source: backend/src/Sumitrack.Api/Services/Sync/SyncService.cs, Controllers/SyncController.cs] — patrón de dispatch por entidad, `ITenantContext`, `NormalizeUtc`, ya construidos en Historia 4.1, reutilizados sin cambios.
- [Source: android/.../sync/SyncManager.kt, sync/workers/PushWorker.kt] — patrón `pushBatch`/`toSyncDto()`/`@HiltWorker` a espejar del lado pull.
- [Source: android/.../domain/usecases/ValidateFolioUseCase.kt] — comentario que nombra explícitamente esta historia como su dependencia pendiente.
- [Source: android/.../data/repositories/AuthRepository.kt, SessionManager.kt] — flujo de login actual a rediseñar.
- [Source: android/.../ui/screens/MainScreen.kt, ui/navigation/AppNavHost.kt] — punto de inserción del indicador de progreso.
- [Source: _bmad-output/implementation-artifacts/deferred-work.md] — `expiresAt` del JWT (re-diferido, no incluido), `upsertAll` sin `deleteAll` previo en Settings (Historia 1.4/4.1, sigue sin resolver).
- [Source: _bmad-output/implementation-artifacts/4-1-motor-de-sincronizacion-push-en-background.md] — historia predecesora directa; "Fuera de alcance" de 4.1 nombra esta historia explícitamente para pull/last_sync_at/folio/primer login.

## Dev Agent Record

### Agent Model Used

claude-sonnet-5

### Debug Log References

- Backend: `cd backend && dotnet build` — compilación correcta tras T1-T3.
- Backend: `cd backend && dotnet test --filter "FullyQualifiedName~SyncServicePullTests"` — 5/5 tras T4.
- Backend: `cd backend && dotnet test --filter "FullyQualifiedName!~LoginAsync_ValidCredentials_ReturnsTokenWithCorrectClaims"` — 20/20 (regresión final, excluye el fallo preexistente ya flagueado en Historia 4.1, no relacionado).
- Android: `JAVA_HOME=".../Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin` — verificado tras T5-T8 y tras T9-T11.
- Android: `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:testDebugUnitTest :app:assembleDebug` — build final completo, 304/304 tests, APK ensamblado.

### Completion Notes List

- **Las 12 tareas completadas end-to-end.** Todos los ACs (AC-1 a AC-5) satisfechos.
- **AC-3 (folio del servidor) resuelto sin contador nuevo** — confirmado durante la implementación: `ValidateFolioUseCase` ya derivaba el folio de `saleDao.countSalesForTenant(tenantId) + 1`; en cuanto `PullService` puebla `sales` localmente, el conteo ya es correcto. Solo se actualizó el comentario del archivo (ya no cierto que "depende de Epic 4, inexistente todavía") y se agregó un test que confirma el folio considera ventas insertadas vía pull.
- **`AuthRepository` rediseñado para inyectar `WorkManager` en vez de `Context`** (desviación menor respecto al snippet original de la historia, que mostraba `@ApplicationContext Context` + `WorkManager.getInstance(context)` inline) — se agregó `di/SyncModule.kt` con un `@Provides fun provideWorkManager(...)`. Motivo: mantener la clase, en principio, fake-able para tests sin depender de un `Context` real.
- **Hallazgo durante la implementación (T12): no existía ningún `AuthRepositoryTest` ni `LoginViewModelTest` previo** — la historia asumía implícitamente que había un test que "actualizar"; no era así. Dado que `AuthRepository` ahora depende de `WorkManager` (clase abstracta de androidx.work con decenas de miembros, no fake-able a mano) y de `SessionManager` (envuelve `Context`/DataStore real), y este proyecto no tiene ninguna librería de mocking instalada (Mockito/MockK), **no se agregó test dedicado para `AuthRepository`** — mismo criterio ya aceptado para `PushWorker`/`PullWorker` desde Historia 4.1 (límite de testeo consciente y documentado, no omisión). Introducir una librería de mocking sería una decisión de alcance mayor no autorizada por esta historia.
- **`PullWorker` sin test dedicado**, misma razón ya establecida en 4.1 para `PushWorker` (`TestListenableWorkerBuilder` requiere `Context` real, inviable sin Robolectric en este proyecto 100% JVM puro).
- **Indicador de progreso deliberadamente mínimo** (`PullSyncViewModel` + `LinearProgressIndicator` en `MainScreen.kt`) — ver Dev Notes → "Por qué el indicador es mínimo"; Historia 4.3 construye el patrón definitivo (app bar + Snackbar).

### File List

**Backend — nuevo:**
- `backend/src/Sumitrack.Api/Models/Responses/ClientPullResponseItem.cs`
- `backend/src/Sumitrack.Api/Models/Responses/ProductPullResponseItem.cs`
- `backend/src/Sumitrack.Api/Models/Responses/ProductVariantPullResponseItem.cs`
- `backend/src/Sumitrack.Api/Models/Responses/SalePullResponseItem.cs`
- `backend/src/Sumitrack.Api/Models/Responses/SaleItemPullResponseItem.cs`
- `backend/src/Sumitrack.Api/Models/Responses/InstallmentPullResponseItem.cs`
- `backend/src/Sumitrack.Api/Models/Responses/PaymentPullResponseItem.cs`
- `backend/src/Sumitrack.Api/Models/Responses/CreditBalancePullResponseItem.cs`
- `backend/src/Sumitrack.Api/Models/Responses/SettingPullResponseItem.cs`
- `backend/tests/Sumitrack.Api.Tests/Services/SyncServicePullTests.cs`

**Backend — modificado:**
- `backend/src/Sumitrack.Api/Services/Sync/ISyncService.cs`
- `backend/src/Sumitrack.Api/Services/Sync/SyncService.cs`
- `backend/src/Sumitrack.Api/Controllers/SyncController.cs`

**Android — nuevo:**
- `android/app/src/main/java/com/sumitrack/android/data/local/entities/SyncMetadataEntity.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/SyncMetadataDao.kt`
- `android/app/src/main/java/com/sumitrack/android/di/SyncModule.kt`
- `android/app/src/main/java/com/sumitrack/android/sync/PullService.kt`
- `android/app/src/main/java/com/sumitrack/android/sync/workers/PullWorker.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/PullSyncViewModel.kt`
- `android/app/src/test/java/com/sumitrack/android/sync/FakeSyncMetadataDao.kt`
- `android/app/src/test/java/com/sumitrack/android/sync/PullServiceTest.kt`
- `android/app/schemas/com.sumitrack.android.data.local.SumitrackDatabase/8.json` (autogenerado por Room)

**Android — modificado:**
- `android/app/src/main/java/com/sumitrack/android/data/local/Migrations.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/SumitrackDatabase.kt`
- `android/app/src/main/java/com/sumitrack/android/di/DatabaseModule.kt`
- `android/app/src/main/java/com/sumitrack/android/data/remote/api/SyncApiService.kt`
- `android/app/src/main/java/com/sumitrack/android/data/repositories/AuthRepository.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/usecases/ValidateFolioUseCase.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/MainScreen.kt`
- `android/app/src/test/java/com/sumitrack/android/domain/usecases/ValidateFolioUseCaseTest.kt`
- `android/app/src/test/java/com/sumitrack/android/sync/FakeSyncApiService.kt`

**Tocados adicionalmente por el code review (patches aplicados, incluida la resolución del decision-needed):**
- `backend/src/Sumitrack.Api/Models/Responses/FolioCountResponse.cs` — **nuevo**
- `backend/src/Sumitrack.Api/Services/Sync/ISyncService.cs`, `SyncService.cs` — `GetFolioCountAsync`
- `backend/src/Sumitrack.Api/Controllers/SyncController.cs` — `GET /api/v1/sync/folio-count`
- `backend/tests/Sumitrack.Api.Tests/Services/SyncServiceFolioCountTests.cs` — **nuevo**
- `backend/tests/Sumitrack.Api.Tests/Services/SyncServicePullTests.cs` — test de aislamiento por tenant agregado
- `android/app/src/main/java/com/sumitrack/android/data/repositories/FolioBaselineStore.kt` — **nuevo**
- `android/app/src/main/java/com/sumitrack/android/data/repositories/SessionManager.kt` — implementa `FolioBaselineStore`
- `android/app/src/main/java/com/sumitrack/android/data/remote/dto/FolioCountDto.kt` — **nuevo**
- `android/app/src/main/java/com/sumitrack/android/data/remote/api/SyncApiService.kt` — `getFolioCount()`
- `android/app/src/main/java/com/sumitrack/android/di/SessionModule.kt` — provider de `FolioBaselineStore`
- `android/app/src/main/java/com/sumitrack/android/sync/PullService.kt` — `pullFolioCount()`, `pullAll` sin corto-circuito, `last_sync_at` no avanza si se saltaron registros
- `android/app/src/main/java/com/sumitrack/android/data/repositories/AuthRepository.kt` — `enqueueUniqueWork` envuelto en `runCatching`
- `android/app/src/main/java/com/sumitrack/android/domain/usecases/ValidateFolioUseCase.kt` — usa `FolioBaselineStore` como piso
- `android/app/src/test/java/com/sumitrack/android/sync/FakeFolioBaselineStore.kt` — **nuevo**
- `android/app/src/test/java/com/sumitrack/android/sync/FakeSyncApiService.kt` — `getFolioCount` simulado
- `android/app/src/test/java/com/sumitrack/android/sync/PullServiceTest.kt` — test renombrado + 4 tests nuevos
- `android/app/src/test/java/com/sumitrack/android/domain/usecases/ValidateFolioUseCaseTest.kt` — 2 tests nuevos del piso de folio
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/PaymentViewModelTest.kt` — actualizado al nuevo constructor de `ValidateFolioUseCase`

### Change Log

| Fecha | Cambio |
|---|---|
| 2026-08-09 | Historia 4.2 implementada completa (T1-T12): pull inicial/incremental de las 9 entidades sincronizables + folio confirmado del servidor + login no bloqueante. 304 tests Android (298 previos + 6 nuevos) y 20/20 tests backend (excluyendo el fallo preexistente ya flagueado, no relacionado) en verde. `assembleDebug` y `dotnet build` correctos. |
| 2026-08-09 | Code review (1 pase, backend+Android juntos, bajo el umbral de chunking). 1 decision-needed (race de colisión de folio) resuelto con un endpoint dedicado de folio-count propuesto por el usuario, en vez de bloquear UI o aceptar el riesgo. 5 patches más aplicados (pullAll sin corto-circuito, last_sync_at no avanza en registros saltados, enqueueUniqueWork con manejo de errores, test renombrado, test de aislamiento por tenant agregado) + 7 items diferidos a deferred-work.md. 310 tests Android, 23 tests backend (excluyendo el fallo preexistente) — todos en verde. Status → `done`. |

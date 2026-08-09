---
baseline_commit: 0074765879022bd18e21e70feabc42d1de4e0017
---

# Story 4.1: Motor de Sincronización Push en Background

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

Como proveedor,
quiero que mis cambios se sincronicen automáticamente con la nube cuando tenga señal,
para que mis datos siempre estén respaldados sin que yo tenga que hacer nada.

## Acceptance Criteria

**AC-1 — Push exitoso**

**Dado** que la app tiene registros con `sync_status = pending` y hay conexión disponible
**Cuando** `PushWorker` ejecuta en background vía WorkManager
**Entonces** envía los registros pendientes a `POST /api/v1/sync/push/{entity}` por tipo de entidad (**9 entidades — ver Dev Notes**: clientes, productos, variantes, ventas, items_venta, parcialidades, cobros, creditos_a_favor, settings)
**Y** al recibir respuesta exitosa actualiza `sync_status = synced` en SQLite

**AC-2 — Retry ante fallo de red**

**Dado** que el push falla por error de red
**Cuando** WorkManager reintenta
**Entonces** aplica `ExponentialBackoffPolicy`; el registro permanece con `sync_status = pending`; no se pierde ningún dato

**AC-3 — Retoma automática al reconectar**

**Dado** que la app pierde conexión mientras hay registros pendientes
**Cuando** se recupera la señal
**Entonces** WorkManager retoma automáticamente la cola de pendientes sin intervención del usuario

**AC-4 — Sin bloqueo de UI**

**Dado** que el push ocurre en background
**Cuando** el proveedor opera la app simultáneamente
**Entonces** la UI no se bloquea ni degrada visiblemente (NFR-1)

**AC-5 — Persistencia en el servidor**

**Dado** que el servidor recibe `POST /api/v1/sync/push/{entity}`
**Cuando** el `SyncController` procesa el payload
**Entonces** persiste los registros en el schema del tenant correcto (vía `TenantSchemaInterceptor`, ya construido desde Historia 1.2); devuelve confirmación por registro; el UUID del cliente nunca se reasigna

### Fuera de alcance en esta historia (explícito)

- **Pull, `last_sync_at`, folio del servidor, primer login** — 100% Historia 4.2. Esta historia no descarga nada, solo sube.
- **Indicadores de UI** (`SyncIcon`, `LinearProgressIndicator`, banner de "Sin internet", `Snackbar` de éxito/reintentar) — 100% Historia 4.3. `PushWorker` no expone todavía ningún estado observable para la UI; 4.3 decidirá cómo consumir `WorkManager.getWorkInfosForUniqueWorkFlow` cuando le toque.
- **Detección y resolución de conflictos** — **decisión explícita de esta historia**: `SyncController` hace *push ciego* (siempre sobrescribe/upserta, sin comparar `server.updated_at` contra `client.updated_at`). `ConflictService`, `ConflictLogEntity`, S-15, y el guard real de `server.updated_at > client.updated_at` son 100% Historia 4.4. Riesgo aceptado explícitamente: v1 es de un solo dispositivo por tenant (`architecture.md`, sección de folio: "Riesgo controlado en v1... no hay colisión posible"), así que un push ciego no tiene con qué chocar todavía en la práctica.
- **Verificación de `expiresAt` del JWT** — asignado explícitamente a Historia 4.2 en `deferred-work.md`. Si el token expira mientras `PushWorker` corre, la llamada HTTP fallará con 401 y WorkManager la tratará como cualquier otro fallo de red (reintento con backoff) — sin manejo especial de "token expirado" en esta historia.
- **Triggers de sync oportunistas por cada escritura local** (crear venta, cobrar, editar cliente, etc.) — esta historia programa **un único trabajo periódico de WorkManager** (ver T13/Dev Notes) que cubre el AC-1/AC-3 literalmente ("sube mientras hay conexión... retoma la cola al reconectar") sin necesidad de tocar los ~10 puntos de escritura ya existentes en el proyecto. Agregar un `enqueue` inmediato tras cada escritura es una mejora de UX (sync más rápido), no un requisito del AC — candidato a historia futura si se necesita.
- **Las 3 condiciones de carrera diferidas desde Historia 3.6/3.7** (`registerPayment`, consumo FIFO de crédito, `onCancelOrderConfirm` con `paymentHistory` cacheado) — la retro del Epic 3 las asignó a "la historia que introduzca escritura concurrente real". Esta historia sigue siendo de **un solo dispositivo, un solo `PushWorker`** — no crea escritura concurrente multi-dispositivo genuina (eso requeriría un segundo dispositivo pusheando/pulleando el mismo tenant a la vez, que es más bien territorio de 4.2/4.4). Se re-diferencian explícitamente a Historia 4.4, que es la que trae `ConflictService` y el concepto real de "el servidor cambió bajo mis pies".

## Tasks / Subtasks

### Backend — modelo de datos (9 entidades, la primera vez que Epic 2/3 tocan el backend)

- [x] **T1: Entidades EF Core nuevas** (AC-5)
  - [ ] Crear 8 clases nuevas en `backend/src/Sumitrack.Api/Models/Entities/`: `Client.cs`, `Product.cs`, `ProductVariant.cs`, `Sale.cs`, `SaleItem.cs`, `Installment.cs`, `Payment.cs`, `CreditBalance.cs`. Patrón exacto de `Setting.cs`/`User.cs` (POCO simple, sin lógica), campos `Id`(Guid)/`FkTenant`(Guid)/`CreatedAt`(DateTime)/`UpdatedAt`(DateTime)/`SyncStatus`(string) en las 8 + los campos propios de cada dominio (ver tabla de mapeo en Dev Notes → "Las 9 entidades, campo por campo").
  - [ ] **Arreglar `Setting.cs`** (viola Reglas Obligatorias #2 hoy: sin `Id`, sin `FkTenant`, sin `CreatedAt`, sin `SyncStatus`) — agregar `CreatedAt`(DateTime) y `SyncStatus`(string) **sin** agregar `Id` ni `FkTenant` (ver Dev Notes → "Por qué Settings no se rediseña por completo").
    ```csharp
    public class Setting
    {
        public string Key { get; set; } = string.Empty;
        public string? Value { get; set; }
        public DateTime CreatedAt { get; set; }
        public DateTime UpdatedAt { get; set; }
        public string SyncStatus { get; set; } = "synced"; // servidor siempre confirma synced
    }
    ```

- [x] **T2: Extender el DDL crudo en `ApplicationBuilderExtensions.cs`** (AC-5) — este proyecto NO usa migraciones de EF Core para las tablas de schema-por-tenant, las crea vía un string SQL ejecutado en cada arranque (`CreateTenantSchemaSql`, ver Dev Notes → "Por qué el DDL es un string crudo, no una migración de EF Core"). Agregar 8 bloques `CREATE TABLE IF NOT EXISTS "{schema}".{tabla} (...)` nuevos a esa misma constante, más un `ALTER TABLE "{schema}".settings ADD COLUMN IF NOT EXISTS ...` para los 2 campos nuevos de Settings:
    ```sql
    CREATE TABLE IF NOT EXISTS "{schema}".clients (
        id UUID NOT NULL,
        fk_tenant UUID NOT NULL,
        name CHARACTER VARYING(200) NOT NULL,
        phone CHARACTER VARYING(20) NOT NULL,
        rfc CHARACTER VARYING(20),
        address TEXT,
        notes TEXT,
        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
        sync_status CHARACTER VARYING(20) NOT NULL DEFAULT 'synced',
        CONSTRAINT pk_clients PRIMARY KEY (id)
    );
    -- ... 7 bloques más, uno por entidad (products, product_variants, sales, sale_items,
    -- installments, payments, credit_balances) — mismo patrón: id UUID PK, fk_tenant UUID,
    -- created_at/updated_at TIMESTAMPTZ, sync_status, más las columnas propias de cada
    -- dominio (ver tabla de mapeo en Dev Notes). Montos en NUMERIC(18,6) (Regla Obligatoria #6).

    ALTER TABLE "{schema}".settings ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();
    ALTER TABLE "{schema}".settings ADD COLUMN IF NOT EXISTS sync_status CHARACTER VARYING(20) NOT NULL DEFAULT 'synced';
    ```
    Esta constante ya se ejecuta para cada tenant existente en cada arranque de la app (`EnsureTenantSchemaAsync`, idempotente vía `IF NOT EXISTS`/`ADD COLUMN IF NOT EXISTS`) — no requiere ningún paso manual adicional para tenants ya creados.

- [x] **T3: `TenantDbContext` — `DbSet` + fluent mapping para las 9 entidades** (AC-5) — mismo patrón exacto que `User`/`Setting` ya mapeados ahí (`entity.ToTable(...)`, `HasKey`, `HasColumnName`/`HasColumnType` por campo). Para las 8 entidades nuevas: `entity.Property(e => e.Total).HasColumnType("numeric(18,6)")` (o el campo monetario correspondiente) — primer uso de columnas monetarias en el backend de todo el proyecto, seguir Regla Obligatoria #6 al pie de la letra.

- [x] **T4: DTOs de request/response para push** (AC-1, AC-5) — un DTO de request por entidad (`Models/Requests/{Entity}SyncRequest.cs`, lista de objetos con los mismos campos que la entidad **excepto `SyncStatus`** — ese es un concepto local, no se sube) y un DTO de response compartido para las 9:
    ```csharp
    public class PushSyncResponseItem
    {
        public Guid Id { get; set; }
        public bool Success { get; set; }
        public string? Error { get; set; }
    }
    // Response: List<PushSyncResponseItem> — "confirmación por registro" (AC-5, literal)
    ```

- [x] **T5: `SyncController` + `SyncService`** (AC-1, AC-5) — mismo patrón que `SettingsController` (`[Authorize]`, inyecta `TenantDbContext` — el `TenantSchemaInterceptor` ya resuelve el schema correcto automáticamente, sin código nuevo de resolución de tenant):
    ```csharp
    [ApiController]
    [Route("api/v1/sync")]
    [Authorize]
    public class SyncController : ControllerBase
    {
        private readonly ISyncService _syncService;
        public SyncController(ISyncService syncService) => _syncService = syncService;

        [HttpPost("push/{entity}")]
        public async Task<IActionResult> Push(string entity, [FromBody] JsonElement payload, CancellationToken ct)
        {
            var result = await _syncService.PushAsync(entity, payload, ct);
            if (result == null) return NotFound(new { errors = new[] { new { code = "UNKNOWN_ENTITY", message = $"Entidad '{entity}' no reconocida." } } });
            return Ok(result);
        }
    }
    ```
    `SyncService.PushAsync(entity, payload, ct)` despacha por el segmento `{entity}` (switch/dictionary sobre los 9 nombres — ver tabla de mapeo) hacia un método privado por entidad que deserializa la lista, hace `Upsert` (mismo criterio "push ciego" — usar `context.Set<T>().ExecuteUpdate`/lectura-then-write simple, sin comparar timestamps) contra `TenantDbContext`, y arma la respuesta `PushSyncResponseItem` por fila. **No existe ningún patrón de bulk-upsert genérico en el backend todavía** (`SettingsController` solo hace `SELECT`) — este es el primer lugar que lo necesita; implementar con un `foreach` simple (buscar por `Id`, `Update` si existe / `Add` si no, `SaveChangesAsync` al final del lote) es suficiente para el volumen esperado, sin necesitar SQL crudo de upsert.

- [x] **T6: Tests backend (xUnit)** — al menos: push de un registro nuevo lo inserta; push de un registro existente (mismo `Id`) lo actualiza sin duplicar; entidad desconocida en la URL devuelve 404 con `UNKNOWN_ENTITY`; respuesta incluye un `PushSyncResponseItem` por cada elemento del payload; aislamiento por tenant (dos tenants, mismo `Id` de negocio no colisiona — en la práctica los `Id` son UUID generados en Android, así que la colisión real es casi imposible, pero verificar que el filtro de schema vía `TenantSchemaInterceptor` sigue aplicando).

### Android — infraestructura WorkManager + Hilt (primera vez en el proyecto)

- [x] **T7: Dependencias WorkManager + Hilt-Work** (AC-1) — agregar a `libs.versions.toml` (`work = "2.10.0"` o la última estable, `hiltWork = "1.2.0"`) y a `app/build.gradle.kts`: `androidx.work:work-runtime-ktx`, `androidx.hilt:hilt-work`, `androidx.hilt:hilt-compiler` (KSP, ya usado para Room/Hilt) — **no existe ninguna referencia a `work` en el proyecto hoy**, confirmado.

- [x] **T8: `SumitrackApp` como `Configuration.Provider` + `HiltWorkerFactory`** (AC-1) — hoy es solo `@HiltAndroidApp class SumitrackApp : Application()`, sin ninguna configuración de WorkManager:
    ```kotlin
    @HiltAndroidApp
    class SumitrackApp : Application(), Configuration.Provider {
        @Inject lateinit var workerFactory: HiltWorkerFactory
        override val workManagerConfiguration: Configuration
            get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
    }
    ```
    Deshabilitar el inicializador automático de WorkManager en el manifest (`<provider android:name="androidx.startup.InitializationProvider" ...><meta-data android:name="androidx.work.WorkManagerInitializer" tools:node="remove" /></provider>`) — requerido cuando se provee `Configuration` manualmente vía `Configuration.Provider`, de lo contrario WorkManager se inicializa dos veces y crashea.

- [x] **T9: Permisos en `AndroidManifest.xml`** (AC-1, AC-3) — `<uses-permission android:name="android.permission.INTERNET" />` y `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />`. **Gap preexistente confirmado**: ninguno de los dos está declarado hoy pese a que `AuthApiService`/`SettingsApiService` ya hacen llamadas HTTP reales desde Historia 1.4 — funcionaba porque ninguna build de release lo había necesitado forzar todavía; `WorkManager`'s `NetworkType.CONNECTED` constraint (T13) lo vuelve imposible de seguir ignorando.

- [x] **T10: `SettingsEntity` — agregar `syncStatus`/`createdAt`, migración v6→v7** (AC-1) — mismo arreglo que el backend (T1), mismo criterio de "no agregar `id` ni `fkTenant` sintéticos" (ver Dev Notes):
    ```kotlin
    @Entity(tableName = "settings")
    data class SettingsEntity(
        @PrimaryKey val key: String,
        val value: String?,
        @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
        @ColumnInfo(name = "sync_status") val syncStatus: String = "pending",
    )
    ```
    `MIGRATION_6_7`: `ALTER TABLE settings ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0` + `ALTER TABLE settings ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'pending'` (backfill con `UPDATE settings SET sync_status = 'synced', created_at = updated_at`-equivalente — ver Dev Notes sobre por qué no hay `updated_at` previo que copiar, mismo problema que la migración 4→5 de Historia 3.3 con `subtotal`/`tax`). Bump `SumitrackDatabase` a `version = 7`, registrar en `Migrations.ALL`.
    **Todo código existente que construye `SettingsEntity`** (`SettingsRepository.downloadAndCacheSettings`, cualquier test) debe actualizarse para el nuevo constructor.

- [x] **T11: Query de pendientes por tenant en cada uno de los 9 DAOs** (AC-1) — agregar `@Query("SELECT * FROM {tabla} WHERE fk_tenant = :tenantId AND sync_status = 'pending'") suspend fun getPending(tenantId: String): List<{Entity}>` a `ClientDao`, `ProductDao`, `ProductVariantDao`, `SaleDao`, `SaleItemDao`, `InstallmentDao`, `PaymentDao`, `CreditBalanceDao` (para `SettingsDao`, sin `fk_tenant` — ver Dev Notes — la query es `WHERE sync_status = 'pending'` a secas). Todos los DAOs ya tienen `upsertAll(...)` reutilizable para marcar `sync_status = "synced"` tras un push exitoso (`upsertAll(pending.map { it.copy(syncStatus = "synced") })`).

- [x] **T12: Interceptor OkHttp de `Authorization`** (AC-1) — resuelve el ítem diferido desde Historia 1.4 ("refactorizar cuando haya ≥3 servicios autenticados" — `SyncApiService`, T13, es el 3ro). Agregar a `NetworkModule.kt`:
    ```kotlin
    @Provides
    @Singleton
    fun provideAuthInterceptor(sessionManager: SessionManager): Interceptor = Interceptor { chain ->
        val token = runBlocking { sessionManager.token.first() }
        val request = if (token != null) {
            chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }
    ```
    Agregarlo a `OkHttpClient.Builder().addInterceptor(authInterceptor)`. **No tocar** `AuthApiService.login`/`SettingsApiService.getSettings` (siguen pasando el header manual — cambiar sus firmas para quitarlo es un refactor fuera de alcance de esta historia, ya que ninguna AC lo exige); el interceptor solo se usa por `SyncApiService` (T13), que se diseña sin parámetro de token desde el día uno.
    `runBlocking` dentro de un `Interceptor` (que es síncrono por contrato de OkHttp) es el patrón estándar y aceptado para este caso — el propio `PushWorker` ya corre en un hilo de background de WorkManager, no en el hilo principal, así que no hay riesgo de ANR.

- [x] **T13: `SyncApiService` + DTOs (9 tipos)** (AC-1, AC-5) — un método por entidad (`@POST("api/v1/sync/push/clientes")`, etc.), sin header manual (ya cubierto por el interceptor de T12):
    ```kotlin
    interface SyncApiService {
        @POST("api/v1/sync/push/clientes") suspend fun pushClientes(@Body body: List<ClientSyncDto>): List<PushSyncResponseItemDto>
        @POST("api/v1/sync/push/productos") suspend fun pushProductos(@Body body: List<ProductSyncDto>): List<PushSyncResponseItemDto>
        // ... 7 métodos más, mismo patrón — ver tabla de mapeo en Dev Notes para los 9 segmentos de URL
    }
    ```
    DTOs (`data/remote/dto/{Entity}SyncDto.kt`) — mismos campos que el dominio, `camelCase`, montos como `String` (BigDecimal serializado como texto, mismo patrón implícito que el resto del proyecto — `kotlinx.serialization` con `BigDecimal` requiere un serializer custom o pasar por `String`; usar `String` y convertir con `BigDecimal(it)` en el mapper, más simple que escribir un `KSerializer<BigDecimal>` para una sola historia).

- [x] **T14: `SyncManager` + `PushWorker`** (AC-1, AC-2, AC-3, AC-4) — `SyncManager.kt` (`data/sync/` o `sync/`, ver Dev Notes sobre la inconsistencia de `architecture.md` en esto) centraliza "recolectar pendientes de las 9 entidades y pushearlas"; `PushWorker` es el `CoroutineWorker` que WorkManager ejecuta:
    ```kotlin
    @HiltWorker
    class PushWorker @AssistedInject constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val syncManager: SyncManager,
        @TenantId private val tenantId: Flow<String?>,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val tenant = tenantId.first() ?: return Result.success() // sin sesión, nada que subir
            return if (syncManager.pushPending(tenant)) Result.success() else Result.retry()
        }
    }
    ```
    `SyncManager.pushPending(tenantId): Boolean` recorre las 9 entidades, para cada una: lee pendientes del DAO correspondiente, si la lista no está vacía llama al método de `SyncApiService` correspondiente dentro de un `runCatching`, si tiene éxito marca `synced` vía `upsertAll`; si CUALQUIER llamada falla (excepción de red), la función devuelve `false` de inmediato (WorkManager reintenta la corrida COMPLETA, no por entidad — más simple, y las entidades ya marcadas `synced` en la misma corrida no se reenvían en el reintento porque su `sync_status` ya cambió). Doc comment explícito sobre esta decisión en el código.

- [x] **T15: Registro del trabajo periódico en WorkManager** (AC-1, AC-3) — un único punto de encolado, disparado una vez por proceso de la app (candidato natural: dentro de `SumitrackApp.onCreate()`, ya que `Configuration.Provider` vive ahí):
    ```kotlin
    val request = PeriodicWorkRequestBuilder<PushWorker>(15, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, PeriodicWorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("push-sync", ExistingPeriodicWorkPolicy.KEEP, request)
    ```
    15 minutos es el intervalo mínimo que WorkManager permite para trabajo periódico (no es negociable a la baja) — cumple AC-1/AC-3 ("sube mientras hay conexión... retoma la cola al reconectar") sin necesitar triggers oportunistas por escritura (ver Fuera de alcance).

### Tests

- [x] **T16: Tests unitarios (JVM, `androidx.work:work-testing`)** — Se confirmó durante la implementación que `TestListenableWorkerBuilder` **no es viable** en este proyecto: requiere un `android.content.Context` real (vía `ApplicationProvider.getApplicationContext()`), que solo existe bajo Robolectric o instrumentación — este proyecto usa exclusivamente tests JVM puros (sin Robolectric, decisión de arquitectura ya establecida). Se aplicó el fallback documentado: toda la lógica de negocio vive en `SyncManager.pushPending(...)` (cero dependencia de `CoroutineWorker`/`Context`), 100% testeable con fakes; `PushWorker` queda como una envoltura de 3 líneas sin test dedicado (mismo criterio de "empujar lo no testeable a la capa más delgada posible" de Historia 3.4 con `Bitmap`/`Uri`). No se agregó la dependencia `androidx.work:work-testing` — hubiera quedado sin uso real.
  - [x] Casos cubiertos en `SyncManagerTest`: push exitoso marca `synced`; sin pendientes no genera llamada HTTP; fallo de red deja `pending` y devuelve `false` (mapeado a `Result.retry()` en `PushWorker`); corte de corriente en el primer fallo sin intentar entidades posteriores; `settings` usa `key` como identidad sin filtrar por tenant. (El caso "sin tenant activo" vive como un one-liner trivial en `PushWorker.doWork()`, no en `SyncManager` — cubierto por inspección, no por test dedicado, dado que no hay forma de testearlo en JVM puro sin Robolectric.)
  - [x] Fakes nuevos: `FakeSyncApiService` (paquete `sync`); se reutilizó el patrón de `Fake{Entity}Dao` ya existentes agregándoles `getPending` (8 con filtro `fk_tenant`, `FakeSettingsDao` sin él).
  - [x] `SettingsRepository.downloadAndCacheSettings` actualizado (marca `syncStatus = "synced"` en los settings descargados); los 5 call-sites de test que construyen `SettingsEntity` (named args con defaults) no requirieron cambios tras T10.

### Review Findings — Backend (2026-08-09)

- [x] [Review][Decision→Patch] Validación de campos antes de persistir en `SyncService` — resuelto: el usuario confirmó agregarla. Se agregó un método `Validate{Entity}` privado por entidad (`Guid.Empty` rechazado, strings requeridos no vacíos y acotados al mismo límite que su columna en `TenantDbContext`, montos/tasas negativos rechazados, `Quantity <= 0` rechazado); los `DateTime` se normalizan a UTC vía `NormalizeUtc` en vez de rechazarse (Android siempre manda `Instant.toString()` con sufijo `Z`, así que esto es normalización defensiva, no una regla de negocio). Se dejó fuera deliberadamente la consistencia `Total == Subtotal + Tax` y la existencia de FKs referenciadas (cubierta por el ítem de FK constraints ya diferido) — son reglas de negocio más amplias, no validación de campo. [backend/src/Sumitrack.Api/Services/Sync/SyncService.cs]
- [x] [Review][Patch] `SyncService` confía en `FkTenant` del payload del cliente en vez de usar el tenant autenticado — se agregó `ITenantContext` al constructor; `FkTenant` ahora se resuelve siempre desde `_tenantContext.TenantId` (poblado por `TenantResolverMiddleware` desde el claim JWT), nunca del payload [backend/src/Sumitrack.Api/Services/Sync/SyncService.cs]
- [x] [Review][Patch] `SyncService` no captura errores de deserialización/`SaveChangesAsync`; cada item se marcaba `Success = true` antes de que el guardado se confirmara, violando la "confirmación por registro" de AC-5 — se agregó try/catch alrededor de `Deserialize` (por lote) y `SaveChangesAsync` (por entidad, vía `SaveAndReconcileAsync`); si el guardado falla, los items previamente marcados exitosos se revierten a `Success = false` con el mensaje de la excepción [backend/src/Sumitrack.Api/Services/Sync/SyncService.cs]
- [x] [Review][Patch] `PushAsync_TwoTenantSchemas_DoNotCollideOnSameId` no puede probar lo que su nombre/intención dicen — EF InMemory nunca abre una conexión ADO.NET real, así que `TenantSchemaInterceptor` (un `DbConnectionInterceptor`) nunca se ejecuta bajo este test [backend/tests/Sumitrack.Api.Tests/Services/SyncServiceTests.cs]
- [x] [Review][Defer] Sin límite de tamaño de lote en el payload de push [backend/src/Sumitrack.Api/Services/Sync/SyncService.cs] — deferred, hardening
- [x] [Review][Defer] DDL crudo interpola `{schema}` sin parametrizar, ahora en 8 tablas más [backend/src/Sumitrack.Api/Infrastructure/Extensions/ApplicationBuilderExtensions.cs] — deferred, pre-existing desde Historia 1.4
- [x] [Review][Defer] Doble fuente de verdad del esquema (fluent `OnModelCreating` vs DDL crudo), nada las mantiene sincronizadas [backend/src/Sumitrack.Api/Infrastructure/Data/TenantDbContext.cs, ApplicationBuilderExtensions.cs] — deferred, pre-existing desde Historia 1.4
- [x] [Review][Defer] DDL no declara `FOREIGN KEY` en ninguna de las 8 tablas nuevas (solo `PRIMARY KEY`) — filas huérfanas posibles si el orden de sync se rompe [backend/src/Sumitrack.Api/Infrastructure/Extensions/ApplicationBuilderExtensions.cs] — deferred, hardening
- [x] [Review][Defer] Race de "mismo Id concurrente" en el servidor (`FindAsync` luego `Add`/update, sin upsert atómico) [backend/src/Sumitrack.Api/Services/Sync/SyncService.cs] — deferred, misma clase de riesgo ya aceptada para v1 (retro Epic 3)
- [x] [Review][Defer] `EnsureTenantSchemaAsync` aborta el aprovisionamiento de TODOS los tenants si uno solo falla en el arranque [backend/src/Sumitrack.Api/Infrastructure/Extensions/ApplicationBuilderExtensions.cs] — deferred, pre-existing, no específico de esta historia

### Review Findings — Android (2026-08-09)

- [x] [Review][Patch] `SyncManager` ignora la confirmación por registro del backend (`success`/`error`) y marca TODO el lote como `synced` con cualquier respuesta 200, incluso si el backend rechazó registros individuales (relevante ahora que `SyncService` valida campo por campo) — **y** el `upsertAll(pending.map { it.copy(syncStatus = "synced") })` sobreescribe la fila completa con el snapshot leído antes del request, revirtiendo silenciosamente una edición local concurrente al mismo registro. Fix aplicado: `markSynced(ids)` (UPDATE acotado a `sync_status`, sin tocar el resto de columnas) agregado a los 9 DAOs; `SyncManager.pushBatch` filtra la respuesta por `success = true` antes de llamar `markSynced` solo con esos ids — un rechazo individual ya no bloquea el resto del lote ni de la corrida. [android/.../sync/SyncManager.kt, los 9 DAOs]
- [x] [Review][Patch] Interceptor de `Authorization` usaba `addHeader` (agrega) en vez de `header` (reemplaza) sobre el `OkHttpClient` compartido — cambiado a `.header(...)`, así `AuthApiService.login`/`SettingsApiService.getSettings` (que siguen pasando el header manual) ya no terminan con 2 headers `Authorization` [android/.../di/NetworkModule.kt]
- [x] [Review][Patch] `runCatching { }` en `SyncManager` tragaba `CancellationException`, rompiendo la cancelación cooperativa si WorkManager cancela `PushWorker` a mitad de una corrida — reemplazado por try/catch explícito que relanza `CancellationException` en `pushBatch` y en cada lectura de `getPending` [android/.../sync/SyncManager.kt]
- [x] [Review][Patch] `getPending(...)` fuera del manejo de errores en cada método de `SyncManager`, y `tenantId.first()` sin manejo de error en `PushWorker.doWork()` — ambos ahora envueltos en try/catch que resuelve en `false`/`Result.retry()` respectivamente en vez de propagar la excepción sin capturar [android/.../sync/SyncManager.kt, android/.../sync/workers/PushWorker.kt]
- [x] [Review][Patch] `ExistingPeriodicWorkPolicy.KEEP` congelaba la configuración (intervalo/constraints/backoff) del trabajo periódico para instalaciones existentes ante cualquier cambio futuro — cambiado a `UPDATE` [android/.../SumitrackApp.kt]
- [x] [Review][Patch] Aserción tautológica en el primer test de `SyncManagerTest` — reemplazada por una lectura directa de `getAllAsFlow().first()` para verificar el `syncStatus` real de la entidad; se agregaron además 2 tests nuevos (rechazo individual del backend no bloquea el resto del lote; `markSynced` no sobreescribe otros campos) [android/.../sync/SyncManagerTest.kt]
- [x] [Review][Defer] Sin límite de tamaño de lote en el push del cliente Android (mismo hardening ya diferido del lado backend) + `writeTimeout` por defecto de OkHttp (10s) puede ser corto para lotes grandes tras un periodo offline extendido [android/.../sync/SyncManager.kt, android/.../di/NetworkModule.kt] — deferred, hardening
- [x] [Review][Defer] Excepciones silenciadas sin logging/telemetría en `SyncManager`/`PushWorker` [android/.../sync/SyncManager.kt, android/.../sync/workers/PushWorker.kt] — deferred, no hay framework de logging establecido en el proyecto todavía
- [x] [Review][Defer] `SettingsRepository.downloadAndCacheSettings` resetea `createdAt` a "ahora" en cada re-descarga — hoy inconsecuente (no existe todavía un flujo de edición local de Settings que lo vuelva `pending`), pero la semántica queda mal si se agrega uno [android/.../data/repositories/SettingsRepository.kt] — deferred, bajo impacto hoy

## Dev Notes

### Las 9 entidades, campo por campo (tabla de mapeo)

| # | Segmento URL | Entidad Android | Entidad Backend | Tabla | Campos propios del dominio (además de id/fk_tenant/created_at/updated_at/sync_status) |
|---|---|---|---|---|---|
| 1 | `clientes` | `ClientEntity` | `Client` | `clients` | name, phone, rfc?, address?, notes? |
| 2 | `productos` | `ProductEntity` | `Product` | `products` | name, price(NUMERIC 18,6), tax_rate(NUMERIC 18,6), is_active |
| 3 | `variantes` | `ProductVariantEntity` | `ProductVariant` | `product_variants` | fk_product, name |
| 4 | `ventas` | `SaleEntity` | `Sale` | `sales` | fk_client, folio, total/subtotal/tax(NUMERIC 18,6), status |
| 5 | `items_venta` | `SaleItemEntity` | `SaleItem` | `sale_items` | fk_sale, fk_product, fk_variant?, product_name, variant_name?, quantity, unit_price/tax_rate(NUMERIC 18,6) |
| 6 | `parcialidades` | `InstallmentEntity` | `Installment` | `installments` | fk_sale, amount(NUMERIC 18,6), due_date, status |
| 7 | `cobros` | `PaymentEntity` | `Payment` | `payments` | fk_sale, fk_installment?, method, amount(NUMERIC 18,6), paid_at |
| 8 | `creditos_a_favor` | `CreditBalanceEntity` | `CreditBalance` | `credit_balances` | fk_client, amount(NUMERIC 18,6), origin, fk_origin_sale?, applied_at? |
| 9 | `settings` | `SettingsEntity` | `Setting` | `settings` | key(PK, no `id`), value? — **sin `fk_tenant`**, ver nota abajo |

`variantes` y `items_venta` (#3, #5) son entidades **agregadas en esta historia** — ni `epics.md` ni `architecture.md` las mencionan en ninguna lista de sync pese a tener `sync_status` en Room desde su creación (Historia 2.4 y 3.3 respectivamente). Confirmado con Josemtz antes de escribir esta historia (junto con `creditos_a_favor`, ya exigido por la retro del Epic 3). Nombres de segmento de URL (`variantes`, `items_venta`) son decisión de nomenclatura de esta historia, no vienen de ningún documento previo — siguen el mismo estilo de sustantivo español en plural que el resto de la lista.

### Por qué el DDL es un string crudo, no una migración de EF Core

`ApplicationBuilderExtensions.CreateTenantSchemaSql` ejecuta `CREATE TABLE IF NOT EXISTS` para cada tenant en cada arranque de la app — **no existe ningún archivo de migración de EF Core para las tablas de schema-por-tenant** (`Infrastructure/Data/Migrations/Public/` solo tiene la migración de `public.tenants`). Este es un gap ya diferido explícitamente desde Historia 1.4 (`deferred-work.md`: *"DbSet<Setting> + raw DDL mezclados... Consolidar estrategia de migraciones en Epic 4"*) — Epic 4 llegó, pero **consolidar la estrategia completa (mover todo a migraciones de EF Core reales) es un refactor mayor fuera del alcance de esta historia particular**; se sigue el patrón ya establecido (extender el mismo string) en vez de mezclar dos estrategias de DDL a mitad de esta historia. Queda como un ítem para diferir de nuevo, ahora con más peso (9 tablas dependiendo del string en vez de 2).

### Por qué Settings no se rediseña por completo

`SettingsEntity`/`Setting` violan la Regla Obligatoria #2 (falta `id`, `fk_tenant`, y hasta ahora `created_at`/`sync_status`) porque su llave natural ya es `key` (string), no un UUID — todo el código existente (`SettingsRepository.getValue(key)`, la UI de Configuración) referencia por `key`, no por un `id` sintético. Rediseñar Settings con un `id` UUID + `fk_tenant` real sería más fiel a la letra de la Regla #2, pero:
- La tabla `settings` en Postgres ya está aislada por tenant vía el schema mismo (`SET search_path`), así que un `fk_tenant` explícito sería redundante para el propósito de aislamiento (a diferencia de `users`, que sí lo tiene por auditoría, no por aislamiento).
- Ningún código actual (Android ni backend) necesita filtrar Settings por `fk_tenant` — confirmado, `SettingsDao`/`SettingsRepository` no reciben `tenantId` en ninguna firma.

Se agregan solo los 2 campos genuinamente necesarios para que AC-1 funcione (`created_at` para consistencia, `sync_status` para el filtro de pendientes que el push necesita), usando `key` como identidad de sincronización en vez de un `id` UUID. Documentado aquí como una excepción deliberada y acotada a la Regla #2, no como un descuido.

### Por qué el push es "ciego" (sin guard de conflicto) en esta historia

`AR-9`/`architecture.md` describen la comparación `server.updated_at > client.updated_at` como parte del flujo de push, pero `ConflictService`/`ConflictLogEntity`/S-15 están explícitamente asignados a Historia 4.4 en el árbol de archivos de `architecture.md`. Confirmado con Josemtz: esta historia hace push ciego (siempre sobrescribe), dejando la detección real de conflicto completa para 4.4. Riesgo aceptado explícitamente porque v1 es de un solo dispositivo por tenant — no hay overwrite indebido posible todavía en la práctica, solo en teoría (dos sesiones del mismo usuario en dos teléfonos, escenario no soportado activamente hasta que 4.4 exista).

### Ubicación de `SyncManager.kt`/`PushWorker.kt` — inconsistencia menor en `architecture.md`

El árbol de archivos detallado (líneas 447-455) pone `sync/SyncManager.kt` y `sync/workers/PushWorker.kt` directamente bajo `sync/`; el resumen posterior (líneas 865-868) agrega un subnivel `sync/manager/SyncManager.kt`. Esta historia usa la versión del árbol detallado (más autoritativa, más temprana): `sync/SyncManager.kt`, `sync/workers/PushWorker.kt`.

### Testing

- **JVM puro donde sea posible** — `androidx.work:work-testing` (`TestListenableWorkerBuilder`) es la primera herramienta de test de este proyecto pensada específicamente para código que normalmente requeriría un dispositivo/emulador; confirmar en la implementación que efectivamente corre sin Robolectric antes de comprometerse a esa ruta — si no, `SyncManager.pushPending(...)` (sin ninguna dependencia de `CoroutineWorker`) es 100% testeable con los fakes ya existentes, y `PushWorker` queda como una envoltura casi sin lógica.
- **Backend**: xUnit + `Microsoft.EntityFrameworkCore.InMemory` (ya usado en el proyecto) para `SyncService`, sin necesitar una instancia real de Postgres para los tests de esta historia.
- Reutilizar `Fake{Entity}Dao` ya existentes (`FakeInstallmentDao`, `FakePaymentDao`, `FakeCreditBalanceDao`, etc.) agregándoles `getPending(tenantId)`.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Historia 4.1, FR-32..34, AR-2/6/7/8/9/19] — ACs, requisitos funcionales y reglas de arquitectura verbatim.
- [Source: _bmad-output/planning-artifacts/architecture/architecture.md#Sincronización Offline, #Reglas Obligatorias] — diseño de push/pull, entidades sincronizables, convenciones JSON.
- [Source: backend/src/Sumitrack.Api/Infrastructure/Extensions/ApplicationBuilderExtensions.cs] — patrón de DDL crudo a extender.
- [Source: backend/src/Sumitrack.Api/Infrastructure/Data/TenantDbContext.cs, TenantSchemaInterceptor.cs] — resolución de tenant ya construida desde Historia 1.2, reutilizada sin cambios.
- [Source: backend/src/Sumitrack.Api/Controllers/SettingsController.cs] — patrón de Controller a replicar para `SyncController`.
- [Source: android/.../di/NetworkModule.kt, data/remote/api/AuthApiService.kt, SettingsApiService.kt] — convenciones de Retrofit/DI existentes.
- [Source: _bmad-output/implementation-artifacts/epic-3-retro-2026-08-04.md] — mandato de agregar `credit_balances` a la lista de entidades sincronizables.
- [Source: _bmad-output/implementation-artifacts/deferred-work.md] — interceptor de Authorization (Historia 1.4), estrategia de migraciones (Historia 1.4), gap de tenant-scoping en `ClientRepository` (no tocado por esta historia).

## Dev Agent Record

### Agent Model Used

claude-sonnet-5

### Debug Log References

- Backend: `cd backend && dotnet build` — compilación correcta tras T1-T3, T4, T5-T6 (verificado 3 veces en distintos checkpoints).
- Backend: `cd backend && dotnet test` — 10/11 pasan; el único fallo (`AuthServiceTests.LoginAsync_ValidCredentials_ReturnsTokenWithCorrectClaims`) se confirmó **pre-existente en `main`** (reproduce igual en `git stash -u` sobre el commit base `0074765`, antes de cualquier cambio de esta historia) — no relacionado con sync. Diferido como tarea separada (ver Completion Notes).
- Android: `JAVA_HOME=".../Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin` — verificado tras T7-T9, T10-T13, T14-T15.
- Android: `./gradlew :app:assembleDebug :app:testDebugUnitTest` — build final completo, APK ensamblado, 296/296 tests pasan (291 preexistentes + 5 nuevos de `SyncManagerTest`).

### Completion Notes List

- **Las 16 tareas completadas end-to-end** (backend: entidades/DDL/DbContext/DTOs/Controller/Service/tests; Android: WorkManager+Hilt/Settings fix/DAOs/interceptor/SyncApiService+DTOs/SyncManager+PushWorker/registro periódico/tests). Todos los ACs (AC-1 a AC-5) satisfechos.
- **Push ciego confirmado y aplicado literalmente**: `SyncService` (backend) hace upsert por `Id` sin comparar `updated_at`; ningún guard de conflicto se implementó, según decisión explícita pre-historia.
- **`PushSyncResponseItem` vs `SettingSyncResponseItem`**: se resolvió el gap abierto en Dev Notes (T4) creando una respuesta paralela para `settings` (`key`/`success`/`error` en vez de `id`/`success`/`error`), ya que `Setting` no tiene `Id`. Documentado también en el DTO Android (`SettingSyncResponseItemDto`).
- **`TestListenableWorkerBuilder` confirmado NO viable** en este proyecto (requiere `Context` real, solo disponible bajo Robolectric/instrumentación — este proyecto es 100% JVM puro). Se aplicó el fallback ya documentado en Dev Notes: toda la lógica vive en `SyncManager.pushPending(...)` (sin dependencia de `CoroutineWorker`), testeada con fakes; `PushWorker` queda sin test dedicado por ser una envoltura de 3 líneas. No se agregó la dependencia `androidx.work:work-testing` (hubiera quedado sin uso real).
- **`SettingsEntity` no tiene `updatedAt`** (solo `createdAt`, verbatim del snippet de la historia) — el mapper `SyncManager` reusa `createdAt` para ambos campos del DTO de settings al pushear, documentado inline con comentario.
- **Hallazgo fuera de alcance, NO corregido en esta historia**: `AuthServiceTests.LoginAsync_ValidCredentials_ReturnsTokenWithCorrectClaims` falla de forma determinística en `main` (no introducido por esta historia, confirmado con `git stash -u` sobre el baseline). Flagueado como tarea de background separada (posible causa: `JwtSecurityTokenHandler.MapInboundClaims` reescribiendo/descartando el claim corto `tenant_id` en `ValidateToken`). No se tocó como parte de 4.1 para no mezclar alcance.
- **8 Fakes DAO existentes actualizados** con `getPending(tenantId)` (7) / `getPending()` sin tenant (Settings) para mantener la compilación de tests tras agregar el método a las 9 interfaces DAO — cambio mecánico, sin lógica nueva de negocio en los fakes más allá del filtro esperado.

### File List

**Backend — nuevo:**
- `backend/src/Sumitrack.Api/Models/Entities/Client.cs`
- `backend/src/Sumitrack.Api/Models/Entities/Product.cs`
- `backend/src/Sumitrack.Api/Models/Entities/ProductVariant.cs`
- `backend/src/Sumitrack.Api/Models/Entities/Sale.cs`
- `backend/src/Sumitrack.Api/Models/Entities/SaleItem.cs`
- `backend/src/Sumitrack.Api/Models/Entities/Installment.cs`
- `backend/src/Sumitrack.Api/Models/Entities/Payment.cs`
- `backend/src/Sumitrack.Api/Models/Entities/CreditBalance.cs`
- `backend/src/Sumitrack.Api/Models/Requests/ClientSyncRequest.cs`
- `backend/src/Sumitrack.Api/Models/Requests/ProductSyncRequest.cs`
- `backend/src/Sumitrack.Api/Models/Requests/ProductVariantSyncRequest.cs`
- `backend/src/Sumitrack.Api/Models/Requests/SaleSyncRequest.cs`
- `backend/src/Sumitrack.Api/Models/Requests/SaleItemSyncRequest.cs`
- `backend/src/Sumitrack.Api/Models/Requests/InstallmentSyncRequest.cs`
- `backend/src/Sumitrack.Api/Models/Requests/PaymentSyncRequest.cs`
- `backend/src/Sumitrack.Api/Models/Requests/CreditBalanceSyncRequest.cs`
- `backend/src/Sumitrack.Api/Models/Requests/SettingSyncRequest.cs`
- `backend/src/Sumitrack.Api/Models/Responses/PushSyncResponseItem.cs`
- `backend/src/Sumitrack.Api/Models/Responses/SettingSyncResponseItem.cs`
- `backend/src/Sumitrack.Api/Services/Sync/ISyncService.cs`
- `backend/src/Sumitrack.Api/Services/Sync/SyncService.cs`
- `backend/src/Sumitrack.Api/Controllers/SyncController.cs`
- `backend/tests/Sumitrack.Api.Tests/Services/SyncServiceTests.cs`

**Backend — modificado:**
- `backend/src/Sumitrack.Api/Models/Entities/Setting.cs`
- `backend/src/Sumitrack.Api/Infrastructure/Extensions/ApplicationBuilderExtensions.cs`
- `backend/src/Sumitrack.Api/Infrastructure/Data/TenantDbContext.cs`
- `backend/src/Sumitrack.Api/Infrastructure/Extensions/ServiceCollectionExtensions.cs`

**Android — nuevo:**
- `android/app/src/main/java/com/sumitrack/android/data/remote/dto/ClientSyncDto.kt`
- `android/app/src/main/java/com/sumitrack/android/data/remote/dto/ProductSyncDto.kt`
- `android/app/src/main/java/com/sumitrack/android/data/remote/dto/ProductVariantSyncDto.kt`
- `android/app/src/main/java/com/sumitrack/android/data/remote/dto/SaleSyncDto.kt`
- `android/app/src/main/java/com/sumitrack/android/data/remote/dto/SaleItemSyncDto.kt`
- `android/app/src/main/java/com/sumitrack/android/data/remote/dto/InstallmentSyncDto.kt`
- `android/app/src/main/java/com/sumitrack/android/data/remote/dto/PaymentSyncDto.kt`
- `android/app/src/main/java/com/sumitrack/android/data/remote/dto/CreditBalanceSyncDto.kt`
- `android/app/src/main/java/com/sumitrack/android/data/remote/dto/SettingSyncDto.kt`
- `android/app/src/main/java/com/sumitrack/android/data/remote/dto/PushSyncResponseItemDto.kt`
- `android/app/src/main/java/com/sumitrack/android/data/remote/dto/SettingSyncResponseItemDto.kt`
- `android/app/src/main/java/com/sumitrack/android/data/remote/api/SyncApiService.kt`
- `android/app/src/main/java/com/sumitrack/android/sync/SyncManager.kt`
- `android/app/src/main/java/com/sumitrack/android/sync/workers/PushWorker.kt`
- `android/app/src/test/java/com/sumitrack/android/sync/FakeSyncApiService.kt`
- `android/app/src/test/java/com/sumitrack/android/sync/SyncManagerTest.kt`
- `android/app/schemas/com.sumitrack.android.data.local.SumitrackDatabase/7.json` (autogenerado por Room)

**Android — modificado:**
- `android/gradle/libs.versions.toml`
- `android/app/build.gradle.kts`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/com/sumitrack/android/SumitrackApp.kt`
- `android/app/src/main/java/com/sumitrack/android/di/NetworkModule.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/entities/SettingsEntity.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/Migrations.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/SumitrackDatabase.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/ClientDao.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/ProductDao.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/ProductVariantDao.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/SaleDao.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/SaleItemDao.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/InstallmentDao.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/PaymentDao.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/CreditBalanceDao.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/SettingsDao.kt`
- `android/app/src/main/java/com/sumitrack/android/data/repositories/SettingsRepository.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/ClientListViewModelTest.kt` (`FakeClientDao.getPending`)
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/FakeSaleDao.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/products/FakeProductDao.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/products/FakeProductVariantDao.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/FakeSaleItemDao.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/FakeInstallmentDao.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/FakePaymentDao.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/FakeCreditBalanceDao.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/FakeSettingsDao.kt`

**Tocados adicionalmente por el code review (patches aplicados):**
- `backend/src/Sumitrack.Api/Services/Sync/SyncService.cs` — `ITenantContext` inyectado (FkTenant ya no viene del payload), validación de campo por entidad, try/catch alrededor de deserialización y `SaveChangesAsync`
- `backend/tests/Sumitrack.Api.Tests/Services/SyncServiceTests.cs` — helper `BuildTenantContext`, test renombrado (`PushAsync_SeparateDbContexts_DoNotShareState`), 3 tests nuevos de validación
- `android/app/src/main/java/com/sumitrack/android/sync/SyncManager.kt` — `pushBatch` genérico (filtra por `success` de la respuesta, relanza `CancellationException`, usa `markSynced` en vez de `upsertAll`)
- `android/app/src/main/java/com/sumitrack/android/sync/workers/PushWorker.kt` — `doWork()` envuelto en try/catch → `Result.retry()`
- `android/app/src/main/java/com/sumitrack/android/di/NetworkModule.kt` — `.header()` en vez de `.addHeader()`
- `android/app/src/main/java/com/sumitrack/android/SumitrackApp.kt` — `ExistingPeriodicWorkPolicy.UPDATE` en vez de `KEEP`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/{Client,Product,ProductVariant,Sale,SaleItem,Installment,Payment,CreditBalance,Settings}Dao.kt` — `markSynced` agregado a las 9
- `android/app/src/test/java/com/sumitrack/android/sync/FakeSyncApiService.kt` — soporte de `rejectedIds` para simular rechazo individual del backend
- `android/app/src/test/java/com/sumitrack/android/sync/SyncManagerTest.kt` — aserción tautológica corregida, 2 tests nuevos
- Los 9 Fake DAOs de test (`FakeClientDao`/`FakeSaleDao`/`FakeProductDao`/`FakeProductVariantDao`/`FakeSaleItemDao`/`FakeInstallmentDao`/`FakePaymentDao`/`FakeCreditBalanceDao`/`FakeSettingsDao`) — `markSynced` agregado a las 9

### Change Log

| Fecha | Cambio |
|---|---|
| 2026-08-09 | Historia 4.1 implementada completa (T1-T16): motor de sincronización push en background, backend + Android. 296 tests Android (291 previos + 5 nuevos) y 10/11 tests backend (1 fallo preexistente no relacionado, diferido) en verde. |
| 2026-08-09 | Code review (3 capas, por dominio: backend + Android). Backend: 4 patches (validación de campos, tenant desde `ITenantContext` en vez del payload, manejo de errores con confirmación real por registro, test renombrado) + 6 defers. Android: 6 patches (per-record ack respetado vía `markSynced`, header `Authorization` duplicado, `CancellationException` ya no se traga, errores locales resuelven en `Result.retry()`, `ExistingPeriodicWorkPolicy.UPDATE`, test tautológico corregido) + 3 defers. 298 tests Android, 15 tests backend (excluyendo el 1 fallo preexistente no relacionado) — todos en verde. Status → `done`. |

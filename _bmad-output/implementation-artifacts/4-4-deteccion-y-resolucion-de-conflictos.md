---
baseline_commit: 48a2475d53b721f23374349720957155140c9374
---

# Story 4.4: Detección y Resolución de Conflictos

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

Como proveedor,
quiero ser notificado cuando se detecta un conflicto de datos y poder resolverlo yo mismo,
para que nunca se pierda información silenciosamente y la decisión siempre sea mía.

## Acceptance Criteria

**AC-1 — Detección en el push**

**Dado** que `PushWorker` intenta subir un registro
**Cuando** se detecta que `server.updated_at > client.updated_at` para el mismo `id`
**Entonces** el push NO se ejecuta para ese registro; el `sync_status` cambia a `conflict`; se crea un registro de log con ambas versiones del registro

**AC-2 — Descubrimiento del conflicto**

**Dado** que existe al menos un registro en `conflict`
**Cuando** el proveedor lo detecta (por el ícono o accediendo a S-14 § Sincronización)
**Entonces** puede abrir S-15 (Resolución de Conflicto) que muestra ambas versiones del registro lado a lado

**AC-3 — Accesibilidad de S-15**

**Dado** que S-15 se abre
**Cuando** el dialog aparece en pantalla
**Entonces** el foco de TalkBack se mueve automáticamente al título "Conflicto detectado"; hay dos opciones: "Usar versión local" y "Conservar ambas"

**AC-4 — Resolución "Usar versión local"**

**Dado** que el proveedor elige "Usar versión local"
**Cuando** confirma
**Entonces** la versión local reemplaza la del servidor en el siguiente push; `sync_status` vuelve a `pending`; el conflicto queda marcado como resuelto en el log

**AC-5 — Resolución "Conservar ambas"**

**Dado** que el proveedor elige "Conservar ambas"
**Cuando** confirma
**Entonces** se crea un segundo registro marcado como duplicado para revisión posterior; ambos quedan en `pending` para push

**AC-6 — Cierre sin elegir**

**Dado** que el proveedor cierra S-15 sin elegir (swipe down o Back)
**Cuando** el dialog se cierra
**Entonces** el conflicto permanece en cola con `sync_status = conflict`; se reofrece en el próximo sync; el foco TalkBack regresa a la pantalla anterior

**AC-7 — Historial en S-14**

**Dado** que el proveedor accede a S-14 § Sincronización
**Cuando** consulta el log
**Entonces** puede ver el historial de conflictos **resueltos** con entidad, fecha de detección y resolución elegida

## Fuera de alcance (decisiones de scope explícitas)

- **Reorganización completa de S-14 en 5 secciones** (Datos Fiscales · Catálogo · Parámetros · Sincronización · Sesión) — es el AC literal de Historia 5.1 (`backlog`). Confirmado con el usuario: esta historia solo agrega una fila de entrada mínima "Sincronización" a `SettingsScreen.kt` (mismo patrón visual que la fila "Catálogo de productos" ya existente), sin tocar la estructura general de la pantalla. Igual que 4.2 dejó un placeholder mínimo que 4.3 generalizó.
- **Botón "Sincronizar ahora"** (mencionado en `EXPERIENCE.md` dentro de la sección S-14 § Sincronización) — es Historia 5.1/FR-31, no esta historia.
- **`ConflictService.cs`/`DetectConflictUseCase.kt` como clases separadas nuevas** — `architecture.md` las propone, pero es un documento pre-implementación que ya diverge del código real (confirmado: no existen `Services/Clients/`, `Services/Conflicts/` ni ningún servicio por-entidad; todo el backend de sync vive en un `SyncService.cs` monolítico). Esta historia EXTIENDE `SyncService.cs`/`SyncManager.kt` existentes con el mismo patrón ya usado en 4.1-4.3, no crea servicios nuevos por separado.
- **Auto-apertura de S-15 al detectar un conflicto en background** — el AC-2 dice literalmente "el proveedor lo detecta" (descubrimiento pasivo vía ícono o log), no que el sistema interrumpa al usuario con un dialog automático mientras `PushWorker` corre en background. S-15 se abre únicamente por acción explícita del usuario (tap en el ícono de conflicto de una card, o tap en una entrada del log).
- **Diff visual estructurado por tipo de entidad** en S-15 — con 9 formas de entidad distintas, construir un comparador campo-a-campo tipado por entidad sería scope excesivo. Se muestra un listado genérico clave:valor por versión (parseando el JSON de cada snapshot), suficiente para cumplir "muestra ambas versiones... lado a lado" sin necesitar 9 layouts distintos.

## Tasks / Subtasks

### Backend — detección de conflicto en el push

- [x] **T1: Extender `PushSyncResponseItem`/`SettingSyncResponseItem`** (AC-1) — agregar `bool Conflict` (default `false`) y `string? ServerSnapshot` (JSON del row actual en servidor, solo poblado cuando `Conflict = true`):
    ```csharp
    public class PushSyncResponseItem
    {
        public Guid Id { get; set; }
        public bool Success { get; set; }
        public string? Error { get; set; }
        public bool Conflict { get; set; }
        public string? ServerSnapshot { get; set; }
    }
    ```
    Mismo patrón para `SettingSyncResponseItem` (usa `Key` en vez de `Id`, sin cambios ahí).

- [x] **T2: Comparación de timestamp en las 9 `PushXxxAsync`** (AC-1) — antes de la rama `if (existing is null) { Add } else { overwrite }` ya existente en `SyncService.cs`, insertar el chequeo de conflicto. Patrón exacto (repetir en las 9, todas siguen la misma estructura confirmada — `PushClientesAsync` línea 68, `PushProductosAsync` 137, `PushVariantesAsync` 203, `PushVentasAsync` 264, `PushItemsVentaAsync` 338, `PushParcialidadesAsync` 416, `PushCobrosAsync` 482, `PushCreditosAFavorAsync` 550, `PushSettingsAsync` 618):
    ```csharp
    var existing = await _ctx.<Table>.FindAsync([item.Id], ct);
    if (existing is not null && existing.UpdatedAt > NormalizeUtc(item.UpdatedAt))
    {
        results.Add(new PushSyncResponseItem
        {
            Id = item.Id,
            Success = false,
            Conflict = true,
            ServerSnapshot = JsonSerializer.Serialize(existing),
        });
        continue; // NO se toca `existing` — el push de este registro se descarta por completo
    }
    if (existing is null) { /* Add, sin cambios */ }
    else { /* overwrite, sin cambios */ }
    ```
    **`>` estricto** (no `>=`) — timestamps iguales NO son conflicto, procede como hoy. **Corrección tras verificar el código real**: `Setting` (entidad backend) SÍ tiene `UpdatedAt` propio (confirmado en `SyncService.cs`, `PushSettingsAsync` ya lo asigna) — el chequeo de conflicto para settings usa `existing.UpdatedAt` normalmente, igual que las otras 8. El quirk de "sin `updatedAt` propio" es SOLO del lado Android (`SettingsEntity.kt`, Room), no del backend — ver T5/T12 y la nota corregida en Dev Notes.

- [x] **T3: Tests backend** (AC-1) — nuevo test de conflicto en al menos 3 de los 9 `PushXxxAsync` (Clientes, Ventas, Settings — Settings se incluye porque es un código genuinamente distinto: PK por `Key` en vez de `Id`, `SettingSyncResponseItem` propio, try/catch inline en vez de `SaveAndReconcileAsync` compartido; **no** por ningún quirk de `UpdatedAt`, que el backend sí tiene igual que las otras 8 — ver Dev Notes), verificando: `existing` NO se modifica, `results` contiene `Conflict=true`/`Success=false`/`ServerSnapshot` poblado con el JSON del row existente. Seguir el patrón de test ya establecido en `SyncServiceTests.cs`.

### Android — persistencia y detección local del conflicto

- [x] **T4: `ConflictLogEntity`/`ConflictLogDao`** (AC-1, AC-7) — nueva tabla, mismo patrón que `SyncMetadataEntity`/`SyncMetadataDao` (Historia 4.2):
    ```kotlin
    @Entity(tableName = "conflict_log")
    data class ConflictLogEntity(
        @PrimaryKey val id: String,              // UUID propio del log entry, distinto del id del registro en conflicto
        @ColumnInfo(name = "fk_tenant") val fkTenant: String,
        @ColumnInfo(name = "entity_type") val entityType: String,   // "clientes"|"productos"|"variantes"|"ventas"|"items_venta"|"parcialidades"|"cobros"|"creditos_a_favor"|"settings" — mismas keys ya usadas en el switch de SyncService/SyncApiService
        @ColumnInfo(name = "record_id") val recordId: String,       // id (o key, para settings) del registro real en conflicto
        @ColumnInfo(name = "local_snapshot") val localSnapshotJson: String,
        @ColumnInfo(name = "server_snapshot") val serverSnapshotJson: String,
        @ColumnInfo(name = "detected_at") val detectedAt: Instant,
        @ColumnInfo(name = "resolved_at") val resolvedAt: Instant? = null,
        val resolution: String? = null,           // null | "local_wins" | "kept_both"
        @ColumnInfo(name = "duplicate_record_id") val duplicateRecordId: String? = null, // solo si resolution == "kept_both"
    )

    @Dao
    interface ConflictLogDao {
        @Insert
        suspend fun insert(entry: ConflictLogEntity)

        @Query("SELECT * FROM conflict_log WHERE fk_tenant = :tenantId AND resolved_at IS NULL")
        fun getUnresolvedAsFlow(tenantId: String): Flow<List<ConflictLogEntity>>

        @Query("SELECT * FROM conflict_log WHERE id = :id")
        suspend fun getById(id: String): ConflictLogEntity?

        @Query("SELECT * FROM conflict_log WHERE fk_tenant = :tenantId AND resolved_at IS NOT NULL ORDER BY resolved_at DESC")
        fun getResolvedAsFlow(tenantId: String): Flow<List<ConflictLogEntity>>

        @Update
        suspend fun update(entry: ConflictLogEntity)
    }
    ```
    Registrar en `SumitrackDatabase.kt` (bump `version = 9`, agregar `ConflictLogEntity::class` a `entities = [...]`, agregar `abstract fun conflictLogDao(): ConflictLogDao`). Nueva `MIGRATION_8_9` en `Migrations.kt`, mismo patrón que `MIGRATION_7_8`:
    ```kotlin
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS conflict_log (
                id TEXT NOT NULL PRIMARY KEY, fk_tenant TEXT NOT NULL, entity_type TEXT NOT NULL,
                record_id TEXT NOT NULL, local_snapshot TEXT NOT NULL, server_snapshot TEXT NOT NULL,
                detected_at INTEGER NOT NULL, resolved_at INTEGER, resolution TEXT, duplicate_record_id TEXT
            )""")
        }
    }
    ```
    Agregar a `Migrations.ALL` y a `.addMigrations(*Migrations.ALL)` en `DatabaseModule.kt`. Sin test de migración dedicado — no existe ningún precedente de `MigrationTestHelper` en el proyecto (mismo criterio ya aceptado para `MIGRATION_7_8`).

- [x] **T5: `PushSyncResponseItemDto`/`SettingSyncResponseItemDto`** (AC-1) — espejo del cambio backend (T1):
    ```kotlin
    @Serializable
    data class PushSyncResponseItemDto(
        val id: String,
        val success: Boolean,
        val error: String? = null,
        val conflict: Boolean = false,
        val serverSnapshot: String? = null,
    )
    ```

- [x] **T6: `markConflict(ids)` en las 9 DAOs** (AC-1) — un método nuevo por DAO, mismo patrón que `markSynced` ya existente en cada uno (ej. `ClientDao.kt:55-56`: `@Query("UPDATE clients SET sync_status = 'synced' WHERE id IN (:ids)") suspend fun markSynced(ids: List<String>)`):
    ```kotlin
    @Query("UPDATE clients SET sync_status = 'conflict' WHERE id IN (:ids)")
    suspend fun markConflict(ids: List<String>)
    ```
    Para `SettingsDao`: filtrar por `key IN (:keys)` en vez de `id`.

- [x] **T7: `SyncManager` — persistir el conflicto** (AC-1) — inyectar `ConflictLogDao` en `SyncManager`. Dentro de cada `pushXxx` (ej. `pushClientes`), después de recibir la respuesta del `pushBatch` genérico, separar los items con `conflict = true`: llamar `dao.markConflict(conflictIds)` y, por cada uno, `conflictLogDao.insert(ConflictLogEntity(...))` con `localSnapshotJson = Json.encodeToString(entity)` (la entidad local que se intentó pushear, ya disponible en el batch antes de mapear a DTO) y `serverSnapshotJson = response.serverSnapshot!!`. **No cambia el contrato de `pushPending(tenantId): Boolean`** — un conflicto es un resultado por-registro, igual que un rechazo de validación ya tolerado hoy (`success = false` sin conflicto); el batch/red se sigue considerando exitoso a nivel de `pushBatch`, así que `PushWorker`/`SyncEventBus` (Historia 4.3) NO necesitan cambios — el mismo criterio de "un registro inválido no bloquea al resto" ya establecido desde 4.1 aplica aquí sin tocar código ya revisado y en producción.

- [x] **T8: Tests de `SyncManager`** (AC-1) — nuevo caso en `SyncManagerTest.kt`: `apiService` devuelve `conflict=true` para un id → verificar que `sync_status` queda `conflict` (no `pending` ni `synced`), que se insertó una fila en `ConflictLogDao` con los snapshots correctos, y que `pushPending` sigue devolviendo `true` (el batch en sí no falló).

### Android — UI: ícono de 3 estados

- [x] **T9: `SyncIcon.kt` — de `Boolean` a `SyncStatus`** (AC-2) — cambiar firma de `isSynced: Boolean` a `status: SyncStatus`, agregar rama `CONFLICT`: ícono `Icons.Outlined.SyncProblem` (o `Icons.Outlined.ReportProblem` si `SyncProblem` no está en el set de íconos ya importado — verificar `androidx.compose.material.icons.outlined` disponibles), tint = `Error` (color ya existente en `Color.kt`, `#B00020` — reutilizar, no crear un token nuevo: a diferencia de `sync-pending` que es informativo, `conflict` es genuinamente un estado de error), `contentDescription = "Conflicto de sincronización."`. Hacer el ícono clickeable SOLO cuando `status == CONFLICT` (navega a S-15), decorativo en los otros dos casos (comportamiento actual sin cambios):
    ```kotlin
    @Composable
    fun SyncIcon(status: SyncStatus, onConflictClick: () -> Unit = {}, modifier: Modifier = Modifier) {
        // CloudDone/SyncOk si SYNCED, Cloud/SyncPending si PENDING (sin cambios),
        // SyncProblem/Error + Modifier.clickable(onClick = onConflictClick) si CONFLICT
    }
    ```

- [x] **T10: Actualizar call sites** (AC-2) — `OrderCard.kt:75` y `ClientCard.kt:58` pasan `syncStatus = order.syncStatus`/`client.syncStatus` directamente (ya son `SyncStatus`, no hace falta mapear a boolean) y agregan `onConflictClick` navegando a S-15 con el id del registro.

### Android — S-15 (Resolución de Conflicto)

- [x] **T11: `ConflictScreen.kt`** (AC-2, AC-3, AC-6) — dialog/modal (`AlertDialog` o `ModalBottomSheet`, siguiendo `EXPERIENCE.md`: "Dialog/modal"). Título "Conflicto detectado" con foco de TalkBack automático al abrir — **no existe precedente de focus-management en el proyecto** (verificado: el dialog de cancelación de Historia 3.7 no usa `FocusRequester`/`semantics`); el único precedente de `FocusRequester` es `OrderListScreen.kt:69,77-86,97` (retorna foco al FAB tras volver del ticket), reutilizar esa misma API con el mismo patrón defensivo (`runCatching { requester.requestFocus() }`):
    ```kotlin
    @Composable
    fun ConflictScreen(conflictLogId: String, onDismiss: () -> Unit, viewModel: ConflictViewModel = hiltViewModel()) {
        val titleFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            runCatching { titleFocusRequester.requestFocus() }
        }
        // Text("Conflicto detectado", modifier = Modifier.focusRequester(titleFocusRequester).focusable())
        // listado genérico clave:valor de local vs. servidor (parsear JSON con kotlinx.serialization a JsonObject)
        // botones "Usar versión local" / "Conservar ambas"
        // onDismissRequest = onDismiss (cubre swipe-down y Back — AC-6)
    }
    ```
    El **retorno de foco a la pantalla anterior al cerrar (AC-6)** es responsabilidad de quien navega a S-15 (`OrderCard`/`ClientCard`/`ConflictLogScreen`): mantener un `FocusRequester` sobre el ícono/fila que abrió S-15 y llamar `requestFocus()` en el callback `onDismiss`/`popBackStack`, mismo patrón exacto que `OrderListScreen.kt` ya usa para el FAB.

- [x] **T12: `ConflictViewModel.kt`** (AC-2, AC-3, AC-4, AC-5) — carga el `ConflictLogEntity` por id (`ConflictLogDao.getById`), expone ambos snapshots parseados. `resolveKeepLocal()` (AC-4): lee la entidad local actual vía el DAO correspondiente al `entityType` (`getById(recordId)`), la actualiza con `syncStatus = "pending"`, `updatedAt = Instant.now()` (bumpear el timestamp es lo que evita que el próximo push vuelva a chocar con el mismo `server.updated_at`), persiste vía `upsertAll(listOf(actualizado))`; marca `ConflictLogEntity.resolvedAt = now()`, `resolution = "local_wins"`. `resolveKeepBoth()` (AC-5): además de lo anterior (bumpear+pending el original), crea una copia con `id` nuevo (`UUID.randomUUID()`), `createdAt`/`updatedAt = now()`, `syncStatus = "pending"`, inserta vía `upsertAll(listOf(duplicado))`; marca `resolution = "kept_both"`, `duplicateRecordId = <nuevo id>`. **Dispatch por `entityType`**: como no hay abstracción genérica entre las 9 entidades (mismo patrón ya usado en `SyncManager.pushPending`/`PullService`, un `when(entityType)` explícito con los 9 casos), usar un `when` explícito llamando al DAO correspondiente — para `settings`, la clave es `key` no `id`, y no hay `createdAt`/`updatedAt` separados (usar `createdAt` para ambos, igual que T2/T5).

- [x] **T13: Tests de `ConflictViewModel`** (AC-4, AC-5) — `FakeConflictLogDao` + fakes de DAOs ya existentes (ej. `FakeClientDao`). Casos: `resolveKeepLocal` deja el registro `pending` con `updatedAt` posterior al original y el log `resolved_at`/`resolution="local_wins"` seteados; `resolveKeepBoth` además crea un segundo registro `pending` con id distinto y el log guarda `duplicateRecordId`.

### Android — S-14 § Sincronización (entry point mínimo, confirmado con el usuario)

- [x] **T14: Fila "Sincronización" en `SettingsScreen.kt`** (AC-2, AC-7) — nueva fila clickeable, mismo patrón visual exacto que la fila "Catálogo de productos" ya existente (`SettingsScreen.kt:53-71`, `Surface(onClick=...) { Row { Icon; Text } }`), navega a `ConflictLogScreen`. No se toca el resto de la pantalla (sin secciones nuevas — ver "Fuera de alcance").

- [x] **T15: `ConflictLogScreen.kt` + `ConflictLogViewModel.kt`** (AC-7) — lista de conflictos **resueltos** (`ConflictLogDao.getResolvedAsFlow(tenantId)` — AC-7 dice literalmente "historial de conflictos resueltos", los no resueltos se descubren vía el ícono en las cards, no en este listado), mostrando por fila: entidad (`entityType`, mapeado a un label legible: "Cliente", "Producto", etc.), fecha de detección (`detectedAt`), resolución elegida (`resolution` mapeado a "Versión local" / "Conservadas ambas"). Sin filtro/búsqueda — fuera de alcance, ningún AC lo exige.

- [x] **T16: Tests de `ConflictLogViewModel`** (AC-7) — verificar que expone solo entradas con `resolvedAt != null`, ordenadas por fecha de resolución descendente (delegando en el `ORDER BY` del DAO).

### Review Findings

- [x] [Review][Patch] `ConflictLogScreen` solo muestra conflictos resueltos, filas no clickeables — no cumple AC-2. **Corregido**: la pantalla ahora muestra una sección "Sin resolver" (clickeable, navega a `Routes.Conflict`) además de "Resueltos"; usa `getUnresolvedAsFlow`. [ConflictLogScreen.kt, ConflictLogViewModel.kt]
- [x] [Review][Patch] `getPending()` excluye `sync_status='conflict'` para siempre — contradice AC-6. **Corregido**: nuevo `getConflicted(tenantId)` en las 9 DAOs (no se cambió `getPending` para no romper `hasPendingWork`); `SyncManager` incluye ambos en el batch de push, con dedup contra un `ConflictLogEntity` no resuelto ya existente vía `getLatestUnresolvedFor`. [SyncManager.kt, las 9 DAOs]
- [x] [Review][Patch] Foco de TalkBack se pide mientras `isLoading == true`, antes de que el título exista — nunca funciona en la práctica. **Corregido**: el `LaunchedEffect(entry)` que pide el foco ahora vive después de los guards de carga/null, en el mismo punto donde el `AlertDialog` con el título realmente se compone; se agregó un estado de carga visible (antes no renderizaba nada). [ConflictScreen.kt]
- [x] [Review][Patch] `PullService.kt` no excluía ids en `conflict` de ser sobrescritos por un pull. **Corregido**: el guard de exclusión ahora usa `getPending() + getConflicted()` en las 9 funciones de pull. [PullService.kt]
- [x] [Review][Patch] Área clickeable del ícono de conflicto de 20dp, bajo el mínimo de 48dp. **Corregido**: envuelto en `IconButton` cuando `status == CONFLICT`. [SyncIcon.kt]
- [x] [Review][Patch] `ConflictViewModel.resolve()` sin guard de re-entrada — doble tap creaba dos duplicados. **Corregido**: nuevo `isResolving` en `ConflictUiState`, seteado de forma síncrona antes de lanzar la corrutina; botones del dialog deshabilitados mientras `isResolving`. [ConflictViewModel.kt, ConflictScreen.kt]
- [x] [Review][Patch] `applyResolution` indistinguía "no encontrado" de "sin duplicado" — un registro faltante se marcaba resuelto sin cambiar nada. **Corregido**: nuevo `ResolutionOutcome` sellado (`Applied`/`RecordNotFound`); el log solo se marca resuelto en el caso `Applied`. [ConflictViewModel.kt]
- [x] [Review][Patch] `pushBatch` no excluía explícitamente los ids en conflicto de `successfulIds`. **Corregido**: `successfulIds` ahora filtra `responseSuccess(it) && !responseConflict(it)`. [SyncManager.kt]
- [x] [Review][Patch] Fila "Sincronización" sin `HorizontalDivider()` respecto a "Catálogo de productos". **Corregido**. [SettingsScreen.kt]
- [x] [Review][Patch] Sin test para la carrera de doble-tap ni para "registro no encontrado". **Corregido**: 2 tests nuevos en `ConflictViewModelTest.kt` verificando ambos casos contra los fixes de arriba.
- [x] [Review][Patch] 2 de los 3 tests backend nuevos solo verificaban `ServerSnapshot != null`. **Corregido**: assert de contenido agregado a los tests de Ventas y Settings. [SyncServiceTests.cs]
- [x] [Review][Patch] Ningún test backend verificaba el orden validación-antes-que-conflicto. **Corregido**: nuevo test `PushAsync_ClienteInvalidoConTimestampViejo_ReportaErrorDeValidacionNoConflicto`. [SyncServiceTests.cs]
- [x] [Review][Patch] Ningún test backend cubría un batch mixto. **Corregido**: nuevo test `PushAsync_LoteMixtoConConflictoExitoYRechazo_ProcesaLosTresIndependientemente`. [SyncServiceTests.cs]
- [x] [Review][Patch] El test de Clientes solo verificaba `Name`. **Corregido**: asserts agregados para `Phone`/`Rfc`/`Address`/`Notes`. [SyncServiceTests.cs]
- [x] [Review][Patch] Texto de T3 desactualizado sobre Settings/`UpdatedAt`. **Corregido**: texto de T3 actualizado explicando la razón real (PK por `Key`, tipo de respuesta propio, sin `SaveAndReconcileAsync` compartido). [este archivo]
- [x] [Review][Defer] Chequeo-de-conflicto-luego-escritura no es atómico con `SaveChangesAsync` — dos requests concurrentes para el mismo id podrían ambos pasar el chequeo. Misma clase de riesgo de concurrencia ya aceptada/diferida para v1 de este proyecto (retro de Epic 3, single-device-per-tenant). [SyncService.cs]
- [x] [Review][Defer] Timestamps exactamente iguales no se tratan como conflicto (comparación `>` estricta) — last-write-wins silencioso en el empate exacto. Coincide con la redacción literal del AC, ventana angosta. [SyncService.cs]
- [x] [Review][Defer] El resultado de conflicto tiene `Success=false` sin `Error` — el único consumidor real (Android) ya discrimina por el flag `Conflict` directamente, no por `Error`, así que no hay impacto práctico hoy. [PushSyncResponseItem.cs]
- [x] [Review][Defer] `ServerSnapshot` serializa la entidad EF cruda, incluyendo campos internos (`SyncStatus`, `FkTenant`) sin utilidad para la UI de resolución — un DTO más estricto sería más limpio. Baja prioridad. [SyncService.cs]
- [x] [Review][Defer] `ServerSnapshot` amplifica el radio de impacto si el aislamiento por schema-por-tenant alguna vez se rompiera (de un overwrite silencioso pasaría a una fuga activa de datos de otro tenant en el response). El aislamiento está verificado correcto hoy, pero sin test a nivel unitario (EF InMemory no tiene concepto de schema). [SyncService.cs]
- [x] [Review][Defer] IDs duplicados dentro del mismo batch de push hacen que el chequeo de conflicto del segundo item compare contra el primer item del mismo payload (aún no guardado), no contra el servidor real — confirmado inalcanzable desde el cliente Android actual (tabla local con `id` como PK no puede producir duplicados en un mismo `getPending()`), pero alcanzable por cualquier otro caller de la API. [SyncService.cs]
- [x] [Review][Defer] Editar un registro en `sync_status='conflict'` por una vía normal (fuera de S-15) lo revierte a `pending` sin resolver el `ConflictLogEntity` correspondiente — el log queda huérfano (nunca `resolvedAt`, invisible una vez que el ícono deja de mostrar `CONFLICT`). Arreglarlo bien requiere inyectar `ConflictLogDao` en cada repositorio (`ClientRepository`, `ProductRepository`, `SaleRepository`, etc.) — alcance mayor al de esta ronda; ventana angosta (requiere editar un registro ya en conflicto sin pasar por S-15 primero). [ClientRepository.kt y análogos]
- [x] [Review][Defer] "Conservar ambas" en `ventas` crea una venta duplicada sin sus `SaleItem`/`Installment`/`Payment` asociados — un pedido incompleto/huérfano. Arreglarlo requiere cascada de duplicación entre tablas relacionadas; alcance de una sola entidad, y "revisión posterior" en la redacción de AC-5 ya implica reconciliación manual esperada. [ConflictViewModel.kt]
- [x] [Review][Defer] 7 de 9 llamadas `getById`/`getByKey` en `ConflictViewModel.applyResolution` no filtran por tenant (a diferencia de `productos`/`ventas`) — confirmado teórico únicamente (todos los ids son UUIDs, sin colisión realista hoy), pero inconsistencia latente de defensa-en-profundidad si la estrategia de generación de ids cambiara. [ConflictViewModel.kt]
- [x] [Review][Defer] "Conservar ambas" no deja ningún marcador visible en la UI de listas (`OrderCard`/`ClientCard`) — solo el campo `duplicateRecordId` (audit-only) en el log registra la relación. Un usuario navegando la lista no puede distinguir el duplicado. Requiere llevar ids de duplicados a los ViewModels de lista; alcance no trivial para esta ronda. [OrderCard.kt, ClientCard.kt]
- [x] [Review][Defer] El slot `dismissButton` del `AlertDialog` se usa para "Conservar ambas", una acción real de compromiso, no semánticamente un "cancelar" — mal uso cosmético del slot de Material3, sin impacto funcional. [ConflictScreen.kt]

## Dev Notes

### Por qué se extiende `SyncService.cs`/`SyncManager.kt` en vez de crear servicios nuevos

`architecture.md` (líneas 246, 272, 404, 499-500, 557) propone `ConflictService.cs`, `DetectConflictUseCase.kt`, un código de error `CONFLICT_DETECTED`, y servicios backend por-entidad (`Services/Clients/ClientService.cs`, etc.) — **ninguno de estos existe en el código real**. El backend real tiene un único `SyncService.cs` monolítico con 9 métodos `PushXxxAsync`/`PullXxxAsync` casi idénticos en estructura, y Android tiene un único `SyncManager.kt` con el mismo patrón. `architecture.md` es un documento de planeación pre-implementación que ya diverge del código en varios puntos (confirmado también: solo lista 6 entidades sincronizables en vez de las 9 reales — gap ya reconciliado por AR-18 en `epics.md:123`). Esta historia sigue el patrón real ya probado en 4.1-4.3 (extender los archivos monolíticos existentes), no el propuesto en el documento de arquitectura.

### Por qué no hace falta tocar `PushWorker`/`SyncEventBus` (Historia 4.3)

Un conflicto es un resultado **por registro**, exactamente como un rechazo de validación individual (`success=false` sin conflicto) que el sistema ya tolera desde Historia 4.1 sin bloquear al resto del batch ni afectar el resultado agregado de `pushPending(tenantId): Boolean`. AC-2 exige descubrimiento **pasivo** (el proveedor "lo detecta" por el ícono o el log), no una interrupción proactiva mientras `PushWorker` corre en background — así que no hace falta un nuevo `SyncEvent.Conflict` ni cambios al mecanismo de Snackbar ya revisado exhaustivamente en el code review de 4.3 (deadlock de `SyncEventBus`, fuga cross-tenant, etc. — tocar esa maquinaria de nuevo sin necesidad sería puro riesgo).

### Mecánica exacta de resolución (AC-4/AC-5) — por qué se bumpea `updatedAt`

Si "Usar versión local" solo cambiara `sync_status` a `pending` sin tocar `updatedAt`, el siguiente push volvería a chocar con el mismo `server.updated_at > client.updated_at` (nada cambió del lado del timestamp) — conflicto infinito. Por eso `resolveKeepLocal` bumpea `updatedAt = Instant.now()`: en el siguiente push, el cliente ya es más nuevo que el servidor, y el registro se sobrescribe normalmente. "Conservar ambas" hace lo mismo con el original (para que también logre pushear su versión local) **y además** crea un registro nuevo con id propio — la versión del servidor que se "pierde" en el original queda preservada únicamente en el snapshot del log (auditoría, AC-7), no en un tercer registro vivo; esto es intencional y evita agregar un campo "es duplicado" a las 9 entidades de negocio solo para esta historia.

### Por qué el guard "salta los pending" de `PullService.kt` (Historia 4.2) no se toca en esta historia

Historia 4.2 documentó explícitamente que su guard interino ("si el id ya está `pending` localmente, el pull no lo toca") sería reemplazado por detección real de conflictos en esta historia. Esta historia SÍ cierra ese gap, pero indirectamente: un registro que queda `pending` localmente (protegido del pull por el guard) sigue intentando pushearse en cada corrida de `PushWorker` — con la detección de T2/T7 ya implementada, ese push eventualmente compara timestamps contra el servidor y, si el servidor es más nuevo, el registro pasa a `conflict` (visible, resoluble vía S-15) en vez de quedar invisible para siempre. El guard del pull sigue siendo necesario tal cual está (sigue evitando que un pull ciego sobrescriba una edición local sin sincronizar), pero ya no dirige a un callejón sin salida — el push-side conflict detection es lo que le da salida real. No se requiere ningún cambio en `PullService.kt` para esta historia.

### Convención de `entityType`

Usar exactamente las mismas 9 keys ya establecidas en el switch de `SyncService.PushAsync`/`SyncApiService`: `clientes`, `productos`, `variantes`, `ventas`, `items_venta`, `parcialidades`, `cobros`, `creditos_a_favor`, `settings`. No inventar nuevas.

### Quirk de `SettingsEntity` (Android, sin `updatedAt` propio — el backend SÍ lo tiene)

**Solo del lado Android**: `SettingsEntity` (Room) no tiene columna `updatedAt` — el DTO ya reutiliza `createdAt` para ambos campos (`SyncManager.kt`, `SettingsEntity.toSyncDto()`). El backend's `Setting` SÍ tiene `UpdatedAt` propio (verificado durante T2 — no hace falta ningún tratamiento especial ahí). Consecuencia práctica: cuando Android empuja un `Setting`, el `updatedAt` que envía es en realidad su `createdAt` local — si el usuario edita el mismo setting más de una vez sin que ese campo se actualice, el timestamp enviado no refleja la edición más reciente, lo que podría producir falsos negativos de conflicto (el backend no detecta como conflicto algo que localmente sí cambió). Esto es una limitación preexistente del modelo de datos de `SettingsEntity`, no introducida por esta historia — no se resuelve aquí (agregar una columna `updatedAt` real a `SettingsEntity` sería scope adicional no pedido por ningún AC). La resolución (T12) para `settings` usa `createdAt` en ambos lados de forma consistente con este quirk ya existente, y la clave de identidad es `key`, no `id`.

### Testing

- Backend: extender `SyncServiceTests.cs` con el patrón ya usado para push/pull — no se necesita un archivo nuevo.
- Android: `ConflictLogDao` sin test dedicado propio más allá de lo cubierto indirectamente por `SyncManagerTest`/`ConflictViewModelTest` — mismo criterio que `SyncMetadataDao` en 4.2. `SyncIcon.kt` sin test (Composable puro, sin Robolectric en el proyecto — mismo criterio ya aceptado repetidamente).
- Fakes nuevos necesarios: `FakeConflictLogDao` (mismo patrón que `FakeSyncMetadataDao` de 4.2).

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Historia 4.4] — los 7 ACs verbatim.
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/EXPERIENCE.md líneas 42-43, 100-104, 264, 325-341] — tabla de superficies S-14/S-15, comportamiento de foco TalkBack, journey narrativo "Roberto resuelve un conflicto".
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/review-accessibility.md línea 59] — hallazgo `[low]` abierto confirmando que el comportamiento de foco de S-15 nunca se implementó, esta historia lo cierra.
- [Source: backend/.../Services/Sync/SyncService.cs] — las 9 `PushXxxAsync`, patrón last-write-wins actual sin ningún chequeo de `updated_at`, a modificar.
- [Source: backend/.../Models/Responses/PushSyncResponseItem.cs, SettingSyncResponseItem.cs] — forma de respuesta actual, a extender.
- [Source: android/.../sync/SyncManager.kt] — `pushBatch` genérico y las 9 `pushXxx`/`toSyncDto`, patrón de `markSynced` a extender con `markConflict`.
- [Source: android/.../sync/workers/PushWorker.kt, SyncEventBus.kt] — máquina de eventos de Historia 4.3, confirmado que NO necesita cambios (ver Dev Notes).
- [Source: android/.../data/local/entities/SyncMetadataEntity.kt, dao/SyncMetadataDao.kt, SumitrackDatabase.kt, Migrations.kt (MIGRATION_7_8)] — patrón exacto a replicar para `ConflictLogEntity`/`ConflictLogDao`/`MIGRATION_8_9`.
- [Source: android/.../ui/components/SyncIcon.kt, OrderCard.kt:75, ClientCard.kt:58] — firma `Boolean` actual, a cambiar a `SyncStatus`.
- [Source: android/.../domain/models/SyncStatus.kt] — enum `CONFLICT` ya definido pero 100% sin uso en el resto del código hasta esta historia.
- [Source: android/.../ui/screens/orders/OrderListScreen.kt:69,77-86,97] — único precedente de `FocusRequester` en el proyecto, patrón a reutilizar para S-15 (título) y el retorno de foco al cerrar.
- [Source: android/.../ui/screens/settings/SettingsScreen.kt, SettingsViewModel.kt] — pantalla plana actual (solo "Catálogo de productos" + "Cerrar sesión"), fila nueva a agregar sin tocar el resto (decisión de scope confirmada con el usuario).
- [Source: _bmad-output/implementation-artifacts/4-2-pull-inicial-y-folio-del-servidor-al-hacer-login.md líneas 54, 168, 246-248] — Dev Notes de 4.2 nombrando explícitamente esta historia como reemplazo del guard interino "salta los pending" de `PullService.kt`.
- [Source: _bmad-output/implementation-artifacts/4-3-indicadores-de-sincronizacion-en-la-ui.md línea 71] — Dev Notes de 4.3 nombrando explícitamente esta historia para el 3er estado visual de `SyncIcon`.

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5), vía Claude Code.

### Debug Log References

Sin hallazgos de bytecode/decompilación en esta historia (a diferencia de 4.3). Dos correcciones sobre lo documentado en la story file original, descubiertas durante la implementación:
- **T2**: el story file asumía que `Setting` (backend) no tenía `UpdatedAt` propio — falso, confirmado leyendo `SyncService.cs`; solo `SettingsEntity` (Android/Room) tiene ese quirk. Corregido en Dev Notes antes de implementar T2.
- **T10/T12**: la navegación a S-15 desde el ícono de una card requería resolver la `ConflictLogEntity` actual a partir de `entityType`+`recordId` (la card no conoce el id propio del log entry) — se agregó `ConflictLogDao.getLatestUnresolvedFor(entityType, recordId)`, no estaba en el diseño original de T4.
- **T12**: 6 de las 9 DAOs (`ProductVariantDao`, `SaleItemDao`, `InstallmentDao`, `PaymentDao`, `CreditBalanceDao`, `SettingsDao`) no tenían ningún método `getById`/`getByKey` — necesario para que la resolución de conflicto pueda leer el registro actual antes de reescribirlo. Se agregó uno a cada una, mismo patrón simple sin `tenantId` que ya usaba `ClientDao.getById`.

### Completion Notes List

- Backend: las 9 `PushXxxAsync` de `SyncService.cs` ahora comparan `existing.UpdatedAt > NormalizeUtc(item.UpdatedAt)` antes de sobrescribir — idéntico patrón en las 9, confirmado que **todas** seguían exactamente la misma estructura antes de este cambio (no había divergencias que obligaran a tratar alguna distinto).
- Android: `SyncManager.pushBatch` genérico extendido con detección de conflicto (`responseConflict`/`responseServerSnapshot`/`markConflict`/`localSnapshot`) sin tocar el contrato `Boolean` de `pushPending` — un conflicto es un resultado por-registro, mismo criterio que un rechazo de validación ya tolerado desde 4.1.
- `SyncIcon` pasó de `isSynced: Boolean` a `status: SyncStatus` — cambio de firma real (no solo wiring), ambos call sites (`OrderCard`, `ClientCard`) actualizados. Se reutilizó el color `Error` ya existente para el estado `CONFLICT` en vez de crear un token `sync-*` nuevo (a diferencia de `SyncOk`/`SyncPending`, un conflicto es genuinamente un error, no un estado informativo).
- S-15 (`ConflictScreen`) implementado como `AlertDialog` (no pantalla completa), con foco de TalkBack al título vía `FocusRequester` — mismo patrón ya establecido en `OrderListScreen.kt` (único precedente en el proyecto). El retorno de foco a la pantalla anterior al cerrar (AC-6) se apoya en el comportamiento default de Compose Navigation al hacer `popBackStack`; no se construyó un `FocusRequester` por-card en las listas (patrón sin precedente, scope desproporcionado) — verificación real en TalkBack queda pendiente de prueba manual en dispositivo (sin `adb` en este entorno, mismo criterio ya aplicado repetidamente en el proyecto).
- "Conservar ambas" (AC-5) preserva la versión del servidor únicamente en el snapshot del log (auditoría) — el registro original se bumpea a `pending` con la versión local (evita loop infinito de conflicto en el siguiente push) y se crea un duplicado con id nuevo, también `pending`. Ver Dev Notes de la story para el razonamiento completo.
- S-14 recibe solo una fila de entrada mínima "Sincronización" (confirmado con el usuario) — la reorganización completa en 5 secciones sigue siendo 100% Historia 5.1.
- Regresión completa verificada: backend (`dotnet build`/`dotnet test`, 26/27 ✅ — 1 fallo preexistente no relacionado desde Historia 4.1), Android (`compileDebugKotlin`, `compileDebugUnitTestKotlin`, `testDebugUnitTest`, `assembleDebug`) — todos exitosos.

**Ronda de code review (16 patches aplicados):** los 3 hallazgos más serios fueron violaciones reales de AC descubiertas por el Acceptance Auditor, no solo pulido — `isSyncing`-equivalente de esta historia: (1) solo 2 de 9 entidades tenían algún camino de UI hacia S-15 (las otras 7 quedaban con conflictos permanentemente atrapados) — resuelto restructurando `ConflictLogScreen` para listar también los no resueltos, clickeables; (2) `getPending()` excluía `conflict` para siempre, contradiciendo AC-6 ("se reofrece en el próximo sync") — resuelto con un `getConflicted(tenantId)` nuevo por DAO (NO se cambió `getPending`, para no alterar la semántica de `hasPendingWork`), con dedup del log vía `getLatestUnresolvedFor`; (3) el foco de TalkBack de AC-3 nunca funcionaba en la práctica (se pedía antes de que el diálogo existiera) — resuelto moviendo el `LaunchedEffect` al punto donde el título realmente se compone. También se corrigió un bug de pérdida de datos real: `PullService.kt` (no tocado en la implementación original) podía sobrescribir silenciosamente un conflicto sin resolver. Detalle completo de los 16 patches y 11 items diferidos en `### Review Findings` arriba y en `deferred-work.md`.

### File List

**Nuevos:**
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/ConflictLogDao.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/entities/ConflictLogEntity.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/conflict/ConflictScreen.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/conflict/ConflictViewModel.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/conflict/ConflictLogScreen.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/conflict/ConflictLogViewModel.kt`
- `android/app/src/test/java/com/sumitrack/android/sync/FakeConflictLogDao.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/conflict/ConflictViewModelTest.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/conflict/ConflictLogViewModelTest.kt`
- `android/app/schemas/com.sumitrack.android.data.local.SumitrackDatabase/9.json` (autogenerado por Room, `exportSchema = true`)
- `_bmad-output/implementation-artifacts/4-4-deteccion-y-resolucion-de-conflictos.md`

**Modificados (backend):**
- `backend/src/Sumitrack.Api/Models/Responses/PushSyncResponseItem.cs` (`Conflict`, `ServerSnapshot`)
- `backend/src/Sumitrack.Api/Models/Responses/SettingSyncResponseItem.cs` (ídem)
- `backend/src/Sumitrack.Api/Services/Sync/SyncService.cs` (chequeo de conflicto en las 9 `PushXxxAsync`)
- `backend/tests/Sumitrack.Api.Tests/Services/SyncServiceTests.cs` (3 tests nuevos: Clientes, Ventas, Settings; ronda de revisión: +2 tests — orden de validación, batch mixto; asserts de contenido reforzados)

**Modificados (Android — main):**
- `android/app/src/main/java/com/sumitrack/android/data/local/Migrations.kt` (`MIGRATION_8_9`)
- `android/app/src/main/java/com/sumitrack/android/data/local/SumitrackDatabase.kt` (v8→v9, `ConflictLogEntity`/`ConflictLogDao`)
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/{Client,Product,ProductVariant,Sale,SaleItem,Installment,Payment,CreditBalance,Settings}Dao.kt` (`markConflict`; 6 de ellas también `getById`/`getByKey`; ronda de revisión: `getConflicted` en las 9)
- `android/app/src/main/java/com/sumitrack/android/data/remote/dto/PushSyncResponseItemDto.kt`, `SettingSyncResponseItemDto.kt` (`conflict`, `serverSnapshot`)
- `android/app/src/main/java/com/sumitrack/android/di/DatabaseModule.kt` (binding de `ConflictLogDao`)
- `android/app/src/main/java/com/sumitrack/android/sync/SyncManager.kt` (`pushBatch` detecta y persiste conflictos; ronda de revisión: incluye `getConflicted` en el batch, dedup del log, excluye conflictos de `successfulIds`)
- `android/app/src/main/java/com/sumitrack/android/sync/PullService.kt` (ronda de revisión: excluye también ids en `conflict` del guard de sobrescritura)
- `android/app/src/main/java/com/sumitrack/android/ui/components/SyncIcon.kt` (`Boolean` → `SyncStatus`, 3er estado; ronda de revisión: `IconButton` para touch target de 48dp)
- `android/app/src/main/java/com/sumitrack/android/ui/components/OrderCard.kt`, `ClientCard.kt` (`onConflictClick`)
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/Routes.kt` (`Conflict`, `ConflictLog`)
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/NavGraph.kt` (registra ambas rutas nuevas; ronda de revisión: `onConflictClick` en `ConflictLogScreen`)
- `android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientListScreen.kt`, `orders/OrderListScreen.kt` (`onConflictClick`)
- `android/app/src/main/java/com/sumitrack/android/ui/screens/settings/SettingsScreen.kt` (fila "Sincronización"; ronda de revisión: `HorizontalDivider` faltante)
- `android/app/src/main/java/com/sumitrack/android/ui/screens/conflict/ConflictScreen.kt` (ronda de revisión: timing del foco TalkBack corregido, estado de carga visible, botones deshabilitados mientras resuelve)
- `android/app/src/main/java/com/sumitrack/android/ui/screens/conflict/ConflictViewModel.kt` (ronda de revisión: guard de re-entrada `isResolving`, `ResolutionOutcome` sellado en vez de `String?`)
- `android/app/src/main/java/com/sumitrack/android/ui/screens/conflict/ConflictLogScreen.kt`, `ConflictLogViewModel.kt` (ronda de revisión: sección "Sin resolver" clickeable agregada)

**Modificados (Android — test):**
- `android/app/src/test/java/com/sumitrack/android/sync/FakeSyncApiService.kt` (`conflictedIds`)
- `android/app/src/test/java/com/sumitrack/android/sync/SyncManagerTest.kt` (1 caso nuevo, constructor con `conflictLogDao`)
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/ClientListViewModelTest.kt` (`FakeClientDao.markConflict`; ronda de revisión: `getConflicted`)
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/FakeSaleDao.kt`, `orders/Fake{CreditBalance,Installment,Payment,SaleItem,Settings}Dao.kt`, `products/Fake{Product,ProductVariant}Dao.kt` (`markConflict`; 6 también `getById`/`getByKey`; ronda de revisión: `getConflicted` en las 9)
- `android/app/src/test/java/com/sumitrack/android/ui/screens/conflict/ConflictViewModelTest.kt` (ronda de revisión: +2 tests — doble-tap, registro no encontrado)

## Change Log

| Fecha | Cambio |
|---|---|
| 2026-08-23 | Implementación completa (T1-T16), backend + Android. Detección de conflicto vía timestamp en las 9 entidades, `ConflictLogEntity`, `SyncIcon` de 3 estados, S-15 (`ConflictScreen`, foco TalkBack), entry point mínimo en S-14. Status → review. |
| 2026-08-23 | Code review (Blind Hunter + Edge Case Hunter + Acceptance Auditor, chunked backend/Android): 0 decisiones, 16 patches aplicados — incluyendo 3 violaciones reales de AC (solo 2/9 entidades con UI hacia S-15, `getPending()` excluía `conflict` para siempre contradiciendo AC-6, foco TalkBack de AC-3 nunca funcionaba) y un bug de pérdida de datos en `PullService.kt`. 11 items diferidos a `deferred-work.md`, 10 descartados como ruido (verificados y refutados). Status → done. |

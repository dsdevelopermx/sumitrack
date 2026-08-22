---
baseline_commit: 866a6191f6b9c78d69d270cc0a7c366b4dc172e9
---

# Story 4.3: Indicadores de Sincronización en la UI

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

Como proveedor,
quiero ver claramente qué registros están sincronizados y cuáles están pendientes,
para que siempre sepa el estado real de mis datos sin entrar en ningún menú.

## Acceptance Criteria

**AC-1 — Ícono de sincronizado**

**Dado** que un registro tiene `sync_status = synced`
**Cuando** aparece en una `OrderCard` o `ClientCard`
**Entonces** `SyncIcon` muestra nube con checkmark en `sync-ok` (#00BCD4) con `contentDescription` "Sincronizado con la nube."

**AC-2 — Ícono de pendiente**

**Dado** que un registro tiene `sync_status = pending`
**Cuando** aparece en una card
**Entonces** `SyncIcon` muestra nube outline (trazo 2dp) en `sync-pending` (#FF7043) con `contentDescription` "Pendiente de sincronizar."
**Y** el color `sync-pending` se usa solo en íconos, nunca como color de texto

**AC-3 — Progreso durante sync**

**Dado** que hay sync en progreso
**Cuando** `PushWorker` está ejecutando
**Entonces** aparece `LinearProgressIndicator` en la parte superior de la app bar (no bloquea la UI)

**AC-4 — Banner sin conexión**

**Dado** que el dispositivo pierde conexión
**Cuando** se detecta el cambio de estado
**Entonces** aparece banner no intrusivo: "Sin internet. Los cambios se guardarán localmente." que se colapsa a ícono en la app bar después de 3 segundos

**AC-5 — Snackbar de resultado**

**Dado** que el sync completa exitosamente
**Cuando** `PushWorker` termina
**Entonces** aparece `Snackbar` de 3 segundos: "Sincronizado correctamente ☁" sin acción adicional

**Dado** que el sync falla con error
**Cuando** se agota el backoff
**Entonces** aparece `Snackbar` con acción "Reintentar" que dispara un nuevo intento de push inmediato

### Estado de partida — qué ya existe (confirmado por investigación exhaustiva antes de escribir esta historia)

**AC-1/AC-2 ya están implementados**, aparentemente construidos por adelantado junto con el enum `SyncStatus` durante Epic 2/3:
- `ui/components/SyncIcon.kt` — `SyncIcon(isSynced: Boolean)`, ya usa `Icons.Outlined.CloudDone`/`Icons.Outlined.Cloud`, colores `SyncOk`(#00BCD4)/`SyncPending`(#FF7043) desde `ui/theme/Color.kt` (match exacto), `contentDescription` exacto a los ACs.
- `ui/components/OrderCard.kt:75` — `SyncIcon(isSynced = order.syncStatus == SyncStatus.SYNCED)`.
- `ui/components/ClientCard.kt:58` — `SyncIcon(isSynced = client.syncStatus == SyncStatus.SYNCED)`.
- **Confirmado (no asumido):** `ClientListViewModel.clients: StateFlow<List<Client>>` (el modelo de dominio completo, con `syncStatus`) es lo que realmente alimenta `ClientListScreen` → `ClientCard` — **no** `ClientSearchRow` (que sí carece de `syncStatus`, pero no es el tipo usado en esa pantalla). AC-1/AC-2 ya están vivos también en la lista de clientes, no solo en la de órdenes.
- `sync-pending` ya se usa exclusivamente en `Icon(tint = ...)`, nunca en texto — AC-2's segunda cláusula ya se cumple.

**Esta historia no necesita tocar ninguno de los archivos de arriba** — su Tasks/Subtasks documenta esto como verificado, no como trabajo pendiente.

**AC-3 tiene un placeholder deliberado, construido en Historia 4.2, para que esta historia lo generalice** — `PullSyncViewModel` + un `LinearProgressIndicator` desnudo (sin `TopAppBar` real) colocado arriba de `NavGraph` dentro de `MainScreen.kt`, observando solo el trabajo one-time `"pull-sync"`. El propio Dev Notes de 4.2 dice explícitamente: *"Historia 4.3 construye el patrón definitivo (LinearProgressIndicator en la app bar + Snackbar al completar/fallar)... puede reemplazarla o generalizarla sin fricción."*

**AC-4/AC-5 no tienen ningún precedente** — no existe observador de conectividad en vivo en ningún lado (solo un chequeo de una sola vez en `LoginViewModel.checkConnectivity()`, nunca actualizado), no existe ningún `SnackbarHost` compartido a nivel de app (cada pantalla tiene el suyo, local, que no sobrevive la navegación), y no existe ningún banner en absoluto en el codebase hoy.

### Fuera de alcance en esta historia (explícito)

- **`ConflictScreen`/estado visual de `SyncStatus.CONFLICT`** — `SyncIcon` hoy colapsa `PENDING` y `CONFLICT` en el mismo ícono (nube outline naranja). Los ACs de esta historia solo hablan de `synced`/`pending` literalmente; un tercer estado visual distintivo para `CONFLICT` es 100% Historia 4.4, cuando `ConflictService`/S-15 empiecen a generar conflictos reales.
- **Botón "Sincronizar ahora" en Configuración (S-14, FR-31)** — 100% Historia 5.1. Esta historia sí conecta el gesto de pull-to-refresh ya armado en las listas (ver T8), pero no construye ningún botón nuevo en Settings.
- **Copy exacto de EXPERIENCE.md vs. epics.md** — existen 3 variantes de texto ligeramente distintas entre `epics.md` (AC, autoritativo), la tabla "Estados de Conectividad" de `EXPERIENCE.md`, y su tabla "Voice & Tone". Se usa el texto literal de `epics.md` para el banner y el Snackbar de éxito; para el mensaje del Snackbar de error (que `epics.md` no especifica, solo el label del botón "Reintentar"), se usa el texto de `EXPERIENCE.md` ("Error al sincronizar.") por ser la única fuente que lo da.
- **Duración exacta de 3.0s del Snackbar de éxito vía Compose estándar** — se usa `SnackbarDuration.Short` (el token estándar de Material 3, ~4s) en vez de un timer custom con `dismiss()` manual — ver Dev Notes. El banner de AC-4 sí necesita su propio timer custom de 3s exactos porque no usa el mecanismo de Snackbar (es un banner persistente con colapso a ícono, no algo que se auto-descarta).

## Tasks / Subtasks

### Bus de eventos de sync (rediseño post-investigación — ver Dev Notes → "Por qué WorkInfo no sirve para detectar éxito/fallo de trabajo periódico")

- [x] **T1: `SyncEventBus`** (AC-5) — bus de eventos simple, sin ninguna dependencia de Android (no envuelve `Context` ni `WorkManager` — trivialmente construible en tests JVM):
    ```kotlin
    sealed class SyncEvent {
        data object Success : SyncEvent()
        data object Failure : SyncEvent()
    }

    @Singleton
    class SyncEventBus @Inject constructor() {
        private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<SyncEvent> = _events.asSharedFlow()
        suspend fun emit(event: SyncEvent) = _events.emit(event)
    }
    ```

- [x] **T2: `SyncManager.hasPendingWork(tenantId)`** (AC-5) — chequeo barato de si hay algo pendiente en cualquiera de las 9 entidades, sin tocar `pushPending` (firma/tests existentes de Historia 4.1 intactos). Evita que `PushWorker` (T3) emita "Sincronizado correctamente" en cada corrida periódica silenciosa cuando no había nada que sincronizar (algo que ocurriría la mayoría de las veces en estado estable, cada 15 min, por tiempo indefinido — un Snackbar así sería ruido puro, no la intención de AC-5):
    ```kotlin
    suspend fun hasPendingWork(tenantId: String): Boolean =
        clientDao.getPending(tenantId).isNotEmpty() || productDao.getPending(tenantId).isNotEmpty() ||
            /* ... los 9, mismo patrón, corto-circuita en el primer `true` ... */
            settingsDao.getPending().isNotEmpty()
    ```

- [x] **T3: `PushWorker` — emite a `SyncEventBus`** (AC-5) — **`Result.retry()`/`Result.success()` NO cambian respecto a Historia 4.1/4.2** (ver Dev Notes — WorkManager no permite detectar fallo de trabajo periódico vía `Result.failure()`, así que no hay motivo para tocarlo y si se tocara sería puro riesgo sin beneficio). Se agrega SOLO la emisión al bus, con tope de intentos para decidir cuándo el fallo es "Snackbar-worthy":
    ```kotlin
    companion object {
        internal const val MAX_ATTEMPTS = 5
        internal fun shouldNotifyFailure(runAttemptCount: Int): Boolean = runAttemptCount >= MAX_ATTEMPTS
    }

    override suspend fun doWork(): Result {
        return try {
            val tenant = tenantId.first() ?: return Result.success()
            val hadPendingWork = syncManager.hasPendingWork(tenant)
            if (syncManager.pushPending(tenant)) {
                if (hadPendingWork) syncEventBus.emit(SyncEvent.Success)
                Result.success()
            } else {
                if (shouldNotifyFailure(runAttemptCount)) syncEventBus.emit(SyncEvent.Failure)
                Result.retry()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (shouldNotifyFailure(runAttemptCount)) syncEventBus.emit(SyncEvent.Failure)
            Result.retry()
        }
    }
    ```
    `shouldNotifyFailure` se extrae como función pura (testeable sin `Context`) — mismo criterio de "empujar lo no testeable a la capa más delgada posible" ya usado repetidamente en el proyecto. Nota: `runAttemptCount` se reinicia en cada corrida periódica nueva (confirmado en el bytecode de WorkManager durante la investigación de esta historia), así que el tope de 5 aplica *dentro* de cada ventana de 15 min, no de forma acumulada para siempre.

### Observadores testeables (mismo patrón de interfaz angosta ya usado en Historia 4.2 con `FolioBaselineStore`)

- [x] **T4: `SyncWorkObserver`** (AC-3) — abstrae `WorkManager.getWorkInfosForUniqueWorkFlow(name)` detrás de una interfaz angosta, para que `SyncStatusViewModel` (T7) sea testeable en JVM puro sin necesitar fakear `WorkManager` completo (clase abstracta con decenas de miembros — mismo problema ya documentado para `AuthRepository` en 4.2). **Solo se usa para `isSyncing`/AC-3 (RUNNING/ENQUEUED) — ya no para detectar éxito/fallo** (eso ahora es responsabilidad de `SyncEventBus`, T1/T3):
    ```kotlin
    interface SyncWorkObserver {
        fun observe(uniqueWorkName: String): Flow<List<WorkInfo>>
    }

    class WorkManagerSyncWorkObserver @Inject constructor(
        private val workManager: WorkManager,
    ) : SyncWorkObserver {
        override fun observe(uniqueWorkName: String): Flow<List<WorkInfo>> =
            workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName)
    }
    ```
    `WorkInfo`/`WorkInfo.State` en sí son POJOs simples con constructor público — no requieren `Context` ni Robolectric, se pueden construir directamente en tests JVM. Registrar el binding en `di/SyncModule.kt` (ya existe desde 4.2).

- [x] **T5: `ConnectivityObserver`** (AC-4) — interfaz angosta + implementación real con `ConnectivityManager.NetworkCallback` vía `callbackFlow`:
    ```kotlin
    interface ConnectivityObserver {
        val isOnline: Flow<Boolean>
    }

    class AndroidConnectivityObserver @Inject constructor(
        @ApplicationContext context: Context,
    ) : ConnectivityObserver {
        private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        override val isOnline: Flow<Boolean> = callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { trySend(true) }
                override fun onLost(network: Network) { trySend(false) }
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
            val current = connectivityManager.activeNetwork
                ?.let { connectivityManager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) }
                ?: false
            trySend(current)
            awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
        }.distinctUntilChanged()
    }
    ```
    Primer mecanismo de conectividad EN VIVO del proyecto — el único precedente existente (`LoginViewModel.checkConnectivity()`) es una sola lectura, nunca actualizada, y se deja intacta (fuera de alcance tocarla). Registrar el binding en `di/SyncModule.kt`.

### Disparo manual de push (reutilizado por AC-5's "Reintentar" Y por T10's pull-to-refresh)

- [x] **T6: `TriggerPushSyncUseCase`** (AC-5) — encola un `PushWorker` de un solo disparo, mismo patrón que `PullWorker` ya usa desde `AuthRepository` en 4.2, con un nombre de trabajo único DISTINTO del periódico (`"push-sync"`) para no colisionar/reemplazarlo:
    ```kotlin
    class TriggerPushSyncUseCase @Inject constructor(
        private val workManager: WorkManager,
    ) {
        operator fun invoke() {
            val request = OneTimeWorkRequestBuilder<PushWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueueUniqueWork("push-sync-now", ExistingWorkPolicy.REPLACE, request)
        }
    }
    ```
    Sin test dedicado (envoltura de 3 líneas sobre `WorkManager`, mismo criterio ya aceptado para `PushWorker`/`PullWorker`/`AuthRepository`). **Importante:** el `PushWorker` de este trigger one-time también pasa por T3 (emite a `SyncEventBus`) — es la MISMA clase `PushWorker`, solo con un `WorkRequest` distinto, así que el Snackbar de éxito/error funciona igual sin importar si el push vino del ciclo periódico o de este disparo manual.

### `SyncStatusViewModel` — reemplaza `PullSyncViewModel`

- [x] **T7: `SyncStatusViewModel`** (AC-3, AC-4, AC-5) — reemplaza `PullSyncViewModel.kt` (Historia 4.2 lo documentó explícitamente como placeholder para esta historia). Combina 3 responsabilidades — **ya no necesita lógica de deduplicación de estados** (eso lo resuelve `SyncEventBus`, T1/T3, con eventos de una sola emisión):
    ```kotlin
    @HiltViewModel
    class SyncStatusViewModel @Inject constructor(
        private val syncWorkObserver: SyncWorkObserver,
        private val connectivityObserver: ConnectivityObserver,
        private val syncEventBus: SyncEventBus,
        private val triggerPushSyncUseCase: TriggerPushSyncUseCase,
    ) : ViewModel() {

        val isSyncing: StateFlow<Boolean> = combine(
            syncWorkObserver.observe("pull-sync"),
            syncWorkObserver.observe("push-sync"),
            syncWorkObserver.observe("push-sync-now"),
        ) { pull, push, pushNow ->
            (pull + push + pushNow).any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        val isOffline: StateFlow<Boolean> = connectivityObserver.isOnline
            .map { online -> !online }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        val syncEvents: SharedFlow<SyncEvent> = syncEventBus.events

        fun retryPush() = triggerPushSyncUseCase()
    }
    ```
    `isSyncing` observa las 3 colas (`pull-sync` incluido, porque AC-3 dice "hay sync en progreso" sin especificar cuál); `syncEvents` viene directo del bus (ya filtrado a solo push, ver T3).

### UI — `MainScreen`, banner, Snackbar

- [x] **T8: `OfflineBanner.kt`** (AC-4) — composable nuevo en `ui/components/`. Texto completo 3 segundos, luego colapsa a un ícono pequeño mientras siga offline; vuelve a mostrar el texto completo si hay una transición nueva online→offline:
    ```kotlin
    @Composable
    fun OfflineBanner(modifier: Modifier = Modifier) {
        var collapsed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(3_000)
            collapsed = true
        }
        if (collapsed) {
            Icon(Icons.Outlined.CloudOff, contentDescription = "Sin internet.", modifier = modifier.padding(8.dp))
        } else {
            Surface(modifier = modifier.fillMaxWidth(), color = /* token nuevo, ver Dev Notes */) {
                Text("Sin internet. Los cambios se guardarán localmente.", modifier = Modifier.padding(12.dp))
            }
        }
    }
    ```
    Se monta/desmonta desde `MainScreen` condicionado a `isOffline` — al desmontarse y volver a montarse (nueva transición a offline) el `LaunchedEffect(Unit)` se re-ejecuta solo porque la composable entera se recrea, reiniciando el timer de 3s automáticamente sin lógica adicional.

- [x] **T9: `MainScreen.kt` — reemplazar `PullSyncViewModel` por `SyncStatusViewModel`, agregar `SnackbarHost`, banner y progreso** (AC-3, AC-4, AC-5) — **no se introduce un `TopAppBar` global nuevo** (ver Dev Notes → "Por qué no hay un TopAppBar compartido") — se extiende el mismo patrón ya establecido en 4.2 (franja encima de `NavGraph`, dentro del `Column` existente):
    ```kotlin
    @Composable
    fun MainScreen(viewModel: SyncStatusViewModel = hiltViewModel()) {
        val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
        val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        // ... navController, tabs sin cambios ...

        LaunchedEffect(Unit) {
            viewModel.syncEvents.collect { event ->
                when (event) {
                    SyncEvent.Success -> snackbarHostState.showSnackbar("Sincronizado correctamente ☁", duration = SnackbarDuration.Short)
                    SyncEvent.Failure -> {
                        val result = snackbarHostState.showSnackbar(
                            message = "Error al sincronizar.",
                            actionLabel = "Reintentar",
                            duration = SnackbarDuration.Indefinite,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.retryPush()
                    }
                }
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = { /* sin cambios */ },
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                if (isOffline) OfflineBanner(modifier = Modifier.fillMaxWidth())
                if (isSyncing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                NavGraph(navController = navController, modifier = Modifier.weight(1f))
            }
        }
    }
    ```
    Borrar `PullSyncViewModel.kt` (reemplazado por `SyncStatusViewModel.kt`, T7).

### Pull-to-refresh (decisión confirmada con el usuario)

- [x] **T10: Conectar el gesto de pull-to-refresh ya armado** (fuera de los ACs literales, decisión explícita del usuario) — `OrderListScreen.kt:154` y `ClientListScreen.kt:122` ya tienen `PullToRefreshBox(onRefresh = { /* TODO Historia 4.x: trigger sync */ })` construido y sin conectar. Inyectar `TriggerPushSyncUseCase` en `OrderListViewModel`/`ClientListViewModel`, exponer un método `onRefresh()`, y llamarlo desde el `onRefresh` de cada `PullToRefreshBox`. No se agrega ningún estado de "refrescando" nuevo — `isSyncing` de `SyncStatusViewModel` (T7, visible en `MainScreen`) ya cubre la retroalimentación visual; `PullToRefreshBox` recibe `isRefreshing = false` igual que hoy (cambiarlo requeriría plumbing adicional no pedido por ningún AC).

### Tests

- [x] **T11: Tests unitarios (JVM)** — `PushWorkerTest` (nuevo, solo para `shouldNotifyFailure`: `false` antes del tope, `true` en/después del tope). `SyncManagerTest` — nuevo caso para `hasPendingWork` (true si cualquiera de las 9 entidades tiene algo pending, false si todas vacías). `SyncStatusViewModelTest` (el más importante — vía `FakeSyncWorkObserver`/`FakeConnectivityObserver`/`SyncEventBus` real, que ya es trivialmente instanciable en tests: `isSyncing` combina las 3 colas correctamente; `isOffline` refleja `FakeConnectivityObserver`; `syncEvents` refleja lo emitido al bus; `retryPush()` delega a `TriggerPushSyncUseCase`). Fakes nuevos: `FakeSyncWorkObserver`, `FakeConnectivityObserver`. `TriggerPushSyncUseCase`/`AndroidConnectivityObserver`/`WorkManagerSyncWorkObserver` sin test dedicado (envolturas mínimas sobre `WorkManager`/`ConnectivityManager`, mismo criterio ya aceptado repetidamente en Epic 4).

### Review Findings

- [x] [Review][Decision] Duración del Snackbar de éxito usa `SnackbarDuration.Short` (~4s), no los "3 segundos" literales de AC-5 — **Resuelto con el usuario: se mantiene `SnackbarDuration.Short`**, sin código nuevo. Razón: el token estándar de Compose M3 cubre la intención de AC-5 ("breve, auto-descarta"); un timer custom de 3.0s exactos sería over-engineering para esta diferencia.
- [x] [Review][Decision] Snackbar de éxito se suprime en corridas periódicas silenciosas sin nada pendiente (`hasPendingWork` gate en `PushWorker`) — **Resuelto con el usuario: se mantiene el gate**, sin código nuevo. Razón: sin él, el usuario vería "Sincronizado correctamente" cada 15 min para siempre en estado estable, que es ruido puro, no la intención de AC-5.
- [x] [Review][Patch] `isSyncing` trata `ENQUEUED` de `push-sync` (periódico) como "sincronizando" — por el mismo hallazgo de `resetPeriodic()` de esta historia, `ENQUEUED` es el reposo normal entre corridas de 15 min, no una ejecución inminente. El `LinearProgressIndicator` quedaría visible casi permanentemente, violando AC-3. **Corregido**: `push-sync` solo cuenta como sincronizando en `RUNNING`; `pull-sync`/`push-sync-now` (one-time) mantienen `RUNNING||ENQUEUED`. [SyncStatusViewModel.kt]
- [x] [Review][Patch] `SyncEventBus.emit()` puede suspender indefinidamente dentro de `PushWorker.doWork()` si no hay colector activo y el buffer (capacidad 1, `SUSPEND` por default) ya está lleno — arriesga que el Worker periódico quede colgado hasta que el sistema lo mate por exceder su tiempo de ejecución. **Corregido**: `onBufferOverflow = DROP_OLDEST` + `emit()` no-suspend vía `tryEmit()`. [SyncEventBus.kt, PushWorker.kt]
- [x] [Review][Patch] `SyncEventBus` no distingue tenant — un resultado de push de un tenant anterior puede aparecer como Snackbar tras cambiar de sesión (logout/login), ya que ningún trabajo de WorkManager se cancela al cerrar sesión. **Corregido**: `SyncEvent` ahora lleva `tenantId`; `SyncStatusViewModel.syncEvents` filtra por el tenant activo. [SyncEvent.kt, PushWorker.kt, SyncStatusViewModel.kt]
- [x] [Review][Patch] `SyncManager.pushPending` no tiene ningún mutex — `push-sync` (periódico) y `push-sync-now` (manual, nuevo en esta historia) pueden correr en paralelo y enviar el mismo lote pendiente dos veces al backend. **Corregido**: `Mutex` de proceso guardando `pushPending`. [SyncManager.kt]
- [x] [Review][Patch] `shouldNotifyFailure` usa `>=` en vez de exactamente el tope — cada intento desde el 5 en adelante vuelve a emitir `SyncEvent.Failure`, arriesgando Snackbars repetidos dentro de la misma ventana de backoff. **Corregido**: `==` en vez de `>=`. [PushWorker.kt]
- [x] [Review][Patch] El botón "Reintentar" y el pull-to-refresh comparten el mismo tope `MAX_ATTEMPTS=5` que las corridas periódicas — como `push-sync-now` siempre arranca con `runAttemptCount` en 0, un reintento manual no da ningún feedback de fallo por ~5 ciclos de backoff (varios minutos), y toques repetidos (vía `ExistingWorkPolicy.REPLACE`) pueden resetear el contador indefinidamente, suprimiendo el Snackbar de error durante una caída real. **Corregido**: `TriggerPushSyncUseCase` marca el `WorkRequest` con `inputData[KEY_MANUAL_RETRY]=true`; `PushWorker` notifica en el primer intento fallido (`runAttemptCount==0`) cuando el trigger fue manual, en vez de esperar el tope de 5. [PushWorker.kt, TriggerPushSyncUseCase.kt]
- [x] [Review][Patch] `ConnectivityObserver` solo filtra por `NET_CAPABILITY_INTERNET`, no por `NET_CAPABILITY_VALIDATED` — un portal cautivo o router sin WAN funcional reporta `isOnline = true` aunque nada llegue a internet, ocultando el banner offline mientras el push falla silenciosamente. **Corregido**: se agrega `NET_CAPABILITY_VALIDATED` al `NetworkRequest` y al chequeo de estado inicial. [ConnectivityObserver.kt]
- [x] [Review][Patch] Cambios rápidos de red (ej. WiFi→celular) pueden producir un parpadeo offline→online espurio (`onLost` seguido de `onAvailable` de otra red), re-disparando el banner offline y su timer de colapso de 3s innecesariamente. **Corregido**: `debounce(800)` antes de `distinctUntilChanged()`. [ConnectivityObserver.kt]
- [x] [Review][Patch] Ningún test verifica que `OrderListViewModel.onRefresh()`/`ClientListViewModel.onRefresh()` realmente invoquen el `PushSyncTrigger` inyectado — el wiring de pull-to-refresh (T10) no tiene cobertura de test. **Corregido**: nuevo test en cada ViewModel con un `PushSyncTrigger` espía. [OrderListViewModelTest.kt, ClientListViewModelTest.kt]
- [x] [Review][Patch] `FakeSyncWorkObserver` construye `WorkInfo(...)` con argumentos posicionales sin nombrar — frágil ante un futuro bump de versión de `androidx.work` que reordene/agregue parámetros. **Corregido**: argumentos nombrados. [FakeSyncWorkObserver.kt]
- [x] [Review][Patch] `SyncStatusViewModel.isSyncing` no tiene `distinctUntilChanged()` antes de `stateIn` — causa recomposición evitable ante cualquier emisión de `WorkInfo` que no cambie el resultado booleano. **Corregido**: `distinctUntilChanged()` agregado. [SyncStatusViewModel.kt]
- [x] [Review][Defer] Race entre `registerNetworkCallback` y la lectura de `activeNetwork` en `ConnectivityObserver` — deferred, ventana angosta y autocorregible (el próximo callback de red la corrige), no amerita sincronización adicional. [ConnectivityObserver.kt:85-90]
- [x] [Review][Defer] TOCTOU entre `hasPendingWork` y `pushPending` en `PushWorker` — deferred, ventana angosta; peor caso es un Snackbar de éxito omitido puntualmente, el dato igual queda sincronizado correctamente. [PushWorker.kt:298-307]
- [x] [Review][Defer] Con el Snackbar de error en `SnackbarDuration.Indefinite`, un segundo `SyncEvent` que llegue mientras sigue abierto puede perderse (el colector está suspendido dentro de `showSnackbar`) — deferred, mitigado por el fix del hallazgo de `SyncEventBus` (el Worker ya no queda colgado), pero construir cola/cancelación de Snackbars sería scope creep para esta ronda. [MainScreen.kt:422-429]
- [x] [Review][Defer] `ExistingWorkPolicy.REPLACE` en `push-sync-now` cancela un push en curso ante un doble tap — deferred, no es dañino porque el push es incremental por entidad (idempotente), la próxima corrida retoma donde quedó. [TriggerPushSyncUseCase.kt:266]
- [x] [Review][Defer] El `catch` de `PushWorker` emite `SyncEvent.Failure` aunque la excepción haya ocurrido dentro de `hasPendingWork()` mismo (sin saber si realmente había algo pendiente) — deferred, es un default seguro razonable ante fallo del propio chequeo. [PushWorker.kt:308-314]

## Dev Notes

### Por qué WorkInfo no sirve para detectar éxito/fallo de trabajo periódico (hallazgo central de esta historia)

Investigación directa del bytecode de `androidx.work.impl.WorkerWrapper.handleResult()` (decompilado con `javap` durante la creación de esta historia, no es una suposición): para trabajo **periódico**, tanto `Result.success()` como `Result.failure()` pasan por el mismo camino interno, `resetPeriodic()`, que reinicia el estado a `ENQUEUED` de inmediato (y `runAttemptCount` a 0) para la siguiente corrida — **`WorkInfo.State.SUCCEEDED`/`FAILED` como estados observables solo existen para trabajo *one-time***. Esto confirma que la serie periódica nunca se cancela por un fallo (el supuesto de seguridad original era correcto), pero también significa que observar `WorkInfo` del trabajo periódico `"push-sync"` **nunca** expondría un evento de éxito/fallo a la UI — ni para el Snackbar de éxito ni para el de error, sin importar cuánto se espere. Por eso esta historia usa un canal explícito (`SyncEventBus`, T1) que `PushWorker` alimenta directamente antes de devolver su `Result`, en vez de inferir el resultado desde `WorkInfo`. `isSyncing` (AC-3) sigue usando `WorkInfo` sin problema porque `RUNNING`/`ENQUEUED` sí son observables normalmente durante la ejecución — el hallazgo aplica solo a los estados terminales.

### Por qué `PushWorker` no cambia su `Result.retry()`/`Result.success()`

Dado el hallazgo anterior, cambiar `Result` a `Result.failure()` tras el tope de intentos no aportaría nada observable (WorkManager lo trataría igual que un éxito periódico, reseteando a `ENQUEUED`) — así que no hay motivo para tocar la semántica de reintento ya aceptada y en producción desde Historia 4.1/4.2. El tope de intentos (`MAX_ATTEMPTS`) solo decide cuándo emitir `SyncEvent.Failure` al bus, sin afectar el comportamiento real de reintento de WorkManager.

### Por qué no hay un `TopAppBar` compartido

Cada pantalla individual (`OrderDetailScreen`, `ClientProfileScreen`, `PaymentScreen`, etc.) ya tiene su PROPIO `TopAppBar` local con su propio título/botón de regreso — no existe ni existió nunca un `TopAppBar` compartido a nivel de toda la app. Introducir uno nuevo en `MainScreen` produciría una barra doble apilada sobre cualquier pantalla que ya tenga la suya. La solución ya adoptada en Historia 4.2 (una franja — `LinearProgressIndicator` — colocada ENCIMA de `NavGraph`, fuera y por encima de cualquier `TopAppBar` local de la pantalla activa) logra visualmente "arriba de la app" sin necesitar unificar la arquitectura de barras superiores ya fragmentada por pantalla. Esta historia extiende esa misma franja (agrega el banner al lado del progreso) en vez de construir un `TopAppBar` real.

### Consistencia de copy entre fuentes

`epics.md` (autoritativo para los ACs) da el texto exacto del banner ("Sin internet. Los cambios se guardarán localmente.") y del Snackbar de éxito ("Sincronizado correctamente ☁"), pero no da el texto del mensaje de error (solo el label del botón, "Reintentar"). `EXPERIENCE.md` tiene 2 tablas con variantes ligeramente distintas entre sí (líneas 124-126 "Voice & Tone" vs. líneas 180-188 "Estados de Conectividad") — se usó la tabla "Estados de Conectividad" para el mensaje de error ("Error al sincronizar.") por ser la más cercana en formato/contexto a los demás textos ya tomados de `epics.md`.

### Testing

- **`SyncWorkObserver`/`ConnectivityObserver`** — mismo patrón de interfaz angosta + fake ya establecido en Historia 4.2 con `FolioBaselineStore`, permite que `SyncStatusViewModel` tenga cobertura de test completa pese a depender transitivamente de `WorkManager`/`ConnectivityManager`.
- **`SyncEventBus`** no necesita interfaz ni fake — es una clase plana sin dependencias de Android, instanciable directamente (`SyncEventBus()`) en cualquier test.
- `WorkInfo`/`WorkInfo.State` tienen constructor público, no requieren `Context` — confirmado durante la investigación de esta historia (decompilación del jar de WorkManager); usarlos directamente en `FakeSyncWorkObserver`.
- `PushWorker` sigue sin test dedicado más allá de la función pura `shouldNotifyFailure` extraída — mismo criterio ya aceptado desde Historia 4.1 (`TestListenableWorkerBuilder` no viable en este proyecto).

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Historia 4.3] — los 5 ACs verbatim.
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/EXPERIENCE.md líneas 124-126, 180-188, 254-255, 295-307] — tablas de conectividad/voice-tone, reglas de accesibilidad, journey narrativo confirmando el emoji ☁ es deliberado.
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/DESIGN.md] — tokens de color `sync-ok`/`sync-pending`, ya reflejados en `Color.kt`.
- [Source: android/.../ui/components/SyncIcon.kt, OrderCard.kt, ClientCard.kt] — AC-1/AC-2 ya implementados, confirmados sin cambios necesarios.
- [Source: android/.../ui/screens/PullSyncViewModel.kt, MainScreen.kt] — placeholder de Historia 4.2 a reemplazar, con su propio Dev Notes apuntando explícitamente a esta historia.
- [Source: android/.../sync/workers/PushWorker.kt, SumitrackApp.kt] — retry infinito actual y registro del trabajo periódico `"push-sync"` a modificar/reutilizar.
- [Source: android/.../data/repositories/AuthRepository.kt] — patrón de encolado one-time (`"pull-sync"`) de Historia 4.2, espejado para `"push-sync-now"`.
- [Source: android/.../ui/screens/orders/OrderListScreen.kt:154, clients/ClientListScreen.kt:122] — `PullToRefreshBox` ya armado con TODO explícito "Historia 4.x".
- [Source: _bmad-output/implementation-artifacts/4-2-pull-inicial-y-folio-del-servidor-al-hacer-login.md] — historia predecesora directa, Dev Notes nombra esta historia explícitamente para el patrón definitivo de indicador.

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5), vía Claude Code.

### Debug Log References

Rediseño de T1-T7 motivado por decompilación directa de bytecode de `androidx.work.impl.WorkerWrapper.handleResult()` (WorkManager 2.10.0, jar extraído de `~/.gradle/caches`, decompilado con `javap` del JBR de Android Studio) — confirmó que `WorkInfo.State.SUCCEEDED`/`FAILED` nunca son observables para trabajo periódico (`resetPeriodic()` intercepta ambas ramas). Ver Dev Notes → "Por qué WorkInfo no sirve para detectar éxito/fallo de trabajo periódico" para el detalle completo. Decisión confirmada con el usuario ("procede con el rediseño") antes de implementar.

### Completion Notes List

- AC-1/AC-2 no requirieron cambios de código — ya estaban completos desde antes de esta historia (verificado leyendo `SyncIcon.kt`, `OrderCard.kt`, `ClientCard.kt`, `ClientListViewModel.kt`).
- Se introdujo `SyncEventBus` (T1-T3) como canal explícito para AC-5, en vez de observar `WorkInfo` del trabajo periódico `"push-sync"` — ver hallazgo de bytecode arriba. `PushWorker.Result` no cambió su semántica de reintento (sigue siempre `Result.retry()` en fallo, igual que Historia 4.1/4.2).
- Se extrajo la interfaz `PushSyncTrigger` (SAM, en `TriggerPushSyncUseCase.kt`) además de `TriggerPushSyncUseCase` mismo, para que `SyncStatusViewModel`, `OrderListViewModel` y `ClientListViewModel` sean testeables en JVM puro sin necesitar un `WorkManager` real — mismo patrón de interfaz angosta ya usado para `SyncWorkObserver`/`ConnectivityObserver` en esta historia y `FolioBaselineStore` en Historia 4.2. No estaba en el código de ejemplo original de T6/T7 del story file, pero fue necesario para no romper la testabilidad ya establecida de `OrderListViewModel`/`ClientListViewModel`.
- Se agregó el token de color `SyncOfflineBanner` (`ui/theme/Color.kt`) para el fondo del banner de AC-4 — no había ninguno definido en `DESIGN.md`/Dev Notes para este caso específico (los tokens `sync-ok`/`sync-pending` documentados son exclusivos para íconos, no para superficies con texto).
- `PullSyncViewModel.kt` (placeholder de Historia 4.2) fue eliminado y reemplazado por `SyncStatusViewModel.kt` en T7/T9.
- Regresión completa verificada: `compileDebugKotlin`, `compileDebugUnitTestKotlin`, `testDebugUnitTest` (todos los tests existentes + nuevos, 0 fallos), `assembleDebug` — todos exitosos.

### File List

**Nuevos:**
- `android/app/src/main/java/com/sumitrack/android/sync/SyncEvent.kt`
- `android/app/src/main/java/com/sumitrack/android/sync/SyncEventBus.kt`
- `android/app/src/main/java/com/sumitrack/android/sync/SyncWorkObserver.kt`
- `android/app/src/main/java/com/sumitrack/android/sync/ConnectivityObserver.kt`
- `android/app/src/main/java/com/sumitrack/android/sync/TriggerPushSyncUseCase.kt` (incluye `PushSyncTrigger`)
- `android/app/src/main/java/com/sumitrack/android/sync/SyncStatusViewModel.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/components/OfflineBanner.kt`
- `android/app/src/test/java/com/sumitrack/android/sync/workers/PushWorkerTest.kt`
- `android/app/src/test/java/com/sumitrack/android/sync/FakeSyncWorkObserver.kt`
- `android/app/src/test/java/com/sumitrack/android/sync/FakeConnectivityObserver.kt`
- `android/app/src/test/java/com/sumitrack/android/sync/SyncStatusViewModelTest.kt`

**Modificados:**
- `android/app/src/main/java/com/sumitrack/android/sync/workers/PushWorker.kt` (emite a `SyncEventBus`, `shouldNotifyFailure`/`MAX_ATTEMPTS`; ronda de revisión: eventos con `tenantId`, `==` en vez de `>=`, umbral distinto para reintento manual vía `KEY_MANUAL_RETRY`)
- `android/app/src/main/java/com/sumitrack/android/sync/SyncManager.kt` (`hasPendingWork`; ronda de revisión: `Mutex` guardando `pushPending`)
- `android/app/src/main/java/com/sumitrack/android/sync/SyncEvent.kt` (ronda de revisión: ahora lleva `tenantId`, pasó de `data object` a `data class`)
- `android/app/src/main/java/com/sumitrack/android/sync/SyncEventBus.kt` (ronda de revisión: `onBufferOverflow = DROP_OLDEST`, `emit()` no-suspend vía `tryEmit()`)
- `android/app/src/main/java/com/sumitrack/android/sync/SyncStatusViewModel.kt` (ronda de revisión: `isSyncing` distingue periódico/one-time, `distinctUntilChanged()`, `syncEvents` filtrado por tenant activo, nueva dependencia `@TenantId Flow<String?>`)
- `android/app/src/main/java/com/sumitrack/android/sync/ConnectivityObserver.kt` (ronda de revisión: agrega `NET_CAPABILITY_VALIDATED`, `debounce(800)`)
- `android/app/src/main/java/com/sumitrack/android/sync/TriggerPushSyncUseCase.kt` (ronda de revisión: `inputData[KEY_MANUAL_RETRY]=true`)
- `android/app/src/main/java/com/sumitrack/android/di/SyncModule.kt` (bindings de `SyncWorkObserver`, `ConnectivityObserver`, `PushSyncTrigger`)
- `android/app/src/main/java/com/sumitrack/android/ui/theme/Color.kt` (`SyncOfflineBanner`)
- `android/app/src/main/java/com/sumitrack/android/ui/screens/MainScreen.kt` (reemplaza `PullSyncViewModel` por `SyncStatusViewModel`, agrega `SnackbarHost`, banner, progreso; ronda de revisión: `when` con `is SyncEvent.Success/Failure`)
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderListViewModel.kt` (`onRefresh()`)
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderListScreen.kt` (conecta `PullToRefreshBox.onRefresh`)
- `android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientListViewModel.kt` (`onRefresh()`)
- `android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientListScreen.kt` (conecta `PullToRefreshBox.onRefresh`)
- `android/app/src/test/java/com/sumitrack/android/sync/SyncManagerTest.kt` (2 casos nuevos para `hasPendingWork`)
- `android/app/src/test/java/com/sumitrack/android/sync/SyncStatusViewModelTest.kt` (ronda de revisión: regresión periódico ENQUEUED/RUNNING, filtrado por tenant, constructor con `Flow<String?>`)
- `android/app/src/test/java/com/sumitrack/android/sync/workers/PushWorkerTest.kt` (ronda de revisión: reescrito para `isManualRetry` y semántica `==`)
- `android/app/src/test/java/com/sumitrack/android/sync/FakeSyncWorkObserver.kt` (ronda de revisión: argumentos nombrados en `WorkInfo(...)`)
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/OrderListViewModelTest.kt` (constructor actualizado con `PushSyncTrigger`; ronda de revisión: test de `onRefresh()`)
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/ClientListViewModelTest.kt` (constructor actualizado con `PushSyncTrigger`; ronda de revisión: test de `onRefresh()`)

**Eliminados:**
- `android/app/src/main/java/com/sumitrack/android/ui/screens/PullSyncViewModel.kt` (reemplazado por `sync/SyncStatusViewModel.kt`)

## Change Log

| Fecha | Cambio |
|---|---|
| 2026-08-22 | Implementación completa (T1-T11). Rediseño de detección de éxito/fallo de sync (T1-T7) tras hallazgo de bytecode sobre `WorkInfo` y trabajo periódico — ver Dev Notes y Debug Log References. Status → review. |
| 2026-08-22 | Code review (Blind Hunter + Edge Case Hunter + Acceptance Auditor): 2 decisiones resueltas con el usuario (sin cambio de código), 11 patches aplicados (fix crítico de AC-3 en `isSyncing`, deadlock de `SyncEventBus`, fuga cross-tenant de eventos, mutex de `pushPending`, feedback de reintento manual, `NET_CAPABILITY_VALIDATED`, entre otros), 5 items deferred a `deferred-work.md`, 4 descartados como ruido (incluyendo un falso positivo verificado por compilación limpia). Status → done. |

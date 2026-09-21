---
baseline_commit: 6bbd311c331da8a497a743dcb96537e8f7ac0ece
---

# Story 5.2: Notificaciones Push Locales para Recordatorios de Cobro

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

Como proveedor,
quiero recibir notificaciones automáticas antes de que venza un pago,
para que nunca olvide una visita de cobro y no pierda ningún ingreso por descuido.

## Acceptance Criteria

**AC-1 — Programación del recordatorio**

**Dado** que existe una parcialidad en estado `pending` (de una venta Pendiente o Parcial) cuya fecha de vencimiento (`dueDate`) es futura
**Cuando** el sistema reconcilia los recordatorios (al abrir la app, o cuando cambia cualquier parcialidad o `dias_anticipacion_recordatorio`)
**Entonces** queda programado en WorkManager un recordatorio para el día `fecha_vencimiento − dias_anticipacion_recordatorio` a las 09:00 hora local; si ese momento ya pasó, no se programa nada para esa parcialidad

**AC-2 — Contenido de la notificación**

**Dado** que llega el momento programado
**Cuando** WorkManager ejecuta el recordatorio
**Entonces** aparece una notificación local con: nombre del cliente, monto pendiente de la parcialidad y fecha de vencimiento (FR-21)

**AC-3 — Cancelación automática**

**Dado** que una parcialidad pasa a `paid` o `cancelled` (cobro registrado, venta liquidada, o venta cancelada)
**Cuando** se actualiza su estado (por cualquier vía: registro local, cancelación local, o pull de otro dispositivo)
**Entonces** su recordatorio programado se cancela automáticamente; y si el recordatorio llegara a ejecutarse igualmente, el worker verifica el estado actual y no muestra nada

**AC-4 — Reprogramación al cambiar el adelanto**

**Dado** que el proveedor cambia `dias_anticipacion_recordatorio` en Configuración (Historia 5.1)
**Cuando** se guarda el nuevo valor
**Entonces** todos los recordatorios pendientes se reprograman con el nuevo adelanto (FR-22) — sin ningún código adicional en `SettingsViewModel`

**AC-5 — Sin dependencia de red**

**Dado** que el dispositivo no tiene conexión a internet
**Cuando** WorkManager ejecuta el recordatorio
**Entonces** la notificación se muestra igualmente, usando solo datos de SQLite local (el trabajo NO lleva constraint de red)

**AC-6 — Permiso y cierre de sesión**

**Dado** que el dispositivo corre Android 13+ (API 33+)
**Cuando** el proveedor llega a la pantalla principal tras iniciar sesión
**Entonces** la app pide el permiso `POST_NOTIFICATIONS`; si se niega, los recordatorios se omiten silenciosamente (sin crash) y no vuelve a insistir por su cuenta
**Y** cuando el proveedor cierra sesión, todos los recordatorios programados se cancelan

## Fuera de alcance (decisiones de scope explícitas)

- **Recordatorios para ventas de pago único** — el AC de `epics.md` habla de "venta ... con fecha de pago futura", pero el modelo de datos no tiene fecha de pago para ventas de pago único: `SaleEntity` no tiene `dueDate` y `GenerateTicketUseCase` usa `payments.firstOrNull()?.paidAt ?: sale.createdAt` para ellas. La ÚNICA entidad con fecha de vencimiento es `InstallmentEntity.dueDate`. Esta historia programa recordatorios solo por parcialidad (mismo alcance que la agenda de Historia 5.3, que también opera sobre parcialidades).
- **Recordatorios para parcialidades ya vencidas** — solo se avisa ANTES del vencimiento (FR-21 "pagos próximos"). Una parcialidad vencida no recibe notificación.
- **Deep link al tocar la notificación** — tocar la notificación abre la app (`MainActivity`), sin navegar a S-09 (Detalle de Orden). La navegación por cobro pertenece a la agenda (Historia 5.3).
- **Alarmas exactas (`AlarmManager`/`SCHEDULE_EXACT_ALARM`)** — `architecture.md` (línea 231) decide WorkManager local; el disparo de WorkManager es aproximado (puede retrasarse por Doze/ahorro de batería) y eso es aceptable para un recordatorio "N días antes".
- **Hora configurable** — el momento del día es una constante (`REMINDER_HOUR = 9`, hora local del dispositivo), no un Setting. Agregar un setting nuevo requeriría tocar S-14 y el backend de settings.
- **Firebase/FCM, agrupar notificaciones, acciones en la notificación (botón "Cobrar")** — fuera de v1 (`architecture.md` líneas 155/713).
- **Cambios de backend y de schema Room** — esta historia es 100% Android y NO agrega columnas/tablas (no hay migración de DB). El "ya se notificó" se resuelve sin persistencia propia: solo se programan disparos futuros, y una vez pasado el momento nunca se reprograma (ver Dev Notes).

## Tasks / Subtasks

### Android — datos observables para el reconciliador

- [x] T1: Agregar a `InstallmentDao` `@Query("SELECT * FROM installments WHERE fk_tenant = :tenantId AND status = 'pending'") fun observeOpenForTenant(tenantId: String): Flow<List<InstallmentEntity>>`. **No hace falta join con `sales`**: `SaleRepository.cancelSale` ya marca `cancelled` todas las parcialidades `pending` de la venta, y `registerPayment` marca `paid` la parcialidad cobrada — el status de la parcialidad ya refleja el de su venta.
- [x] T2: Actualizar `FakeInstallmentDao` (`android/app/src/test/java/com/sumitrack/android/ui/screens/orders/FakeInstallmentDao.kt`) — hoy guarda un `mutableMapOf` sin Flow. Migrar el almacenamiento a un `MutableStateFlow<Map<String, InstallmentEntity>>` (o mantener el mapa y publicar una lista en un `MutableStateFlow` en cada `upsertAll`/`markSynced`/`markConflict`) e implementar `observeOpenForTenant` filtrando `fkTenant == tenantId && status == "pending"`. **Regla ya vista 3 veces (4.4, 5.1): cuando una interfaz DAO gana un método, TODOS los Fakes que la implementan dejan de compilar** — buscar con `grep -rn ": InstallmentDao" android/app/src/test` y actualizar cada uno.
- [x] T3: Agregar `SettingsRepository.observeDiasAnticipacion(): Flow<Int>` — `settingsDao.getAll().map { list -> list.find { it.key == "dias_anticipacion_recordatorio" }?.value?.toIntOrNull()?.coerceIn(1, 30) ?: 3 }.distinctUntilChanged()`. Default 3 = el valor sembrado en backend (`ApplicationBuilderExtensions.cs`). `getAll()` ya existe en `SettingsDao` y `FakeSettingsDao` — no requiere cambios de DAO. Tests en `SettingsRepositoryTest.kt` (ya existe): default 3 sin la key, valor válido, valor fuera de rango se acota, valor no numérico → 3.

### Android — planificador puro (núcleo testeable)

- [x] T4: Crear `android/app/src/main/java/com/sumitrack/android/sync/reminders/ReminderPlanner.kt`:
  ```kotlin
  data class ReminderPlan(val installmentId: String, val triggerAt: Instant)
  object ReminderPlanner {
      const val REMINDER_HOUR = 9
      fun plan(installments: List<InstallmentEntity>, diasAnticipacion: Int, now: Instant, zone: ZoneId): List<ReminderPlan>
  }
  ```
  Regla: solo parcialidades con `status == "pending"`; `triggerAt = dueDate.atZone(zone).toLocalDate().minusDays(diasAnticipacion).atTime(REMINDER_HOUR, 0).atZone(zone).toInstant()`; se incluye solo si `triggerAt.isAfter(now)`. `now` y `zone` son PARÁMETROS (mismo criterio que `CalculateInstallmentsUseCase`/`toUiStatus`): tests deterministas sin reloj de sistema. No leer `Instant.now()` dentro de la función.
- [x] T5: Tests `ReminderPlannerTest.kt` (JVM puro, zona fija `ZoneId.of("America/Mexico_City")`): dueDate a 10 días con dias=3 → dispara en día 7 a las 09:00; trigger ya pasado (dueDate mañana, dias=3) → lista vacía; trigger hoy a las 09:00 con `now` = 08:59 → incluido, con `now` = 09:00 exacto → excluido (`isAfter` estricto); parcialidad `paid`/`cancelled` → excluida; dias=1 y dias=30 (bordes del rango); varias parcialidades → un `ReminderPlan` por cada una.

### Android — programador de WorkManager

- [x] T6: Crear `sync/reminders/ReminderScheduler.kt` con `interface ReminderScheduler { suspend fun replaceAll(plans: List<ReminderPlan>, openInstallmentIds: Set<String>) }` (interfaz angosta para testabilidad, mismo patrón que `PushSyncTrigger`/`SyncWorkObserver`) y `class WorkManagerReminderScheduler @Inject constructor(private val workManager: WorkManager) : ReminderScheduler`. `plans` = disparos FUTUROS a (re)encolar; `openInstallmentIds` = TODAS las parcialidades `pending` del tenant (incluidas las que ya no tienen un disparo futuro) — se necesita para no cancelar un recordatorio cuya hora ya pasó mientras el dispositivo estaba apagado (ver Dev Notes).
  - Nombre único por parcialidad: `"reminder-$installmentId"`; tag común `"payment-reminder"`; tag por ítem `"reminder-installment:$installmentId"`.
  - `replaceAll`: (1) leer `workManager.getWorkInfosByTagFlow("payment-reminder").first()` (mismo estilo que `WorkManagerSyncWorkObserver.observe`, que ya usa `getWorkInfosForUniqueWorkFlow` — la API Flow existe en WorkManager 2.10.0), y para cada `WorkInfo` en estado `ENQUEUED` cuyo id (extraído del tag por ítem) NO esté en `openInstallmentIds`, llamar `workManager.cancelUniqueWork("reminder-$id")`; (2) para cada plan, encolar `OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(Duration.between(now, plan.triggerAt))` con `setInputData(workDataOf(ReminderWorker.KEY_INSTALLMENT_ID to id))`, ambos tags, vía `enqueueUniqueWork("reminder-$id", ExistingWorkPolicy.REPLACE, request)`. **SIN `setConstraints` de red** (AC-5) — a diferencia de `TriggerPullSyncUseCase`/`PushWorker`, que sí llevan `NetworkType.CONNECTED`, este trabajo NO debe llevarlo o no dispararía offline.
  - `REPLACE` sobre el mismo nombre es idempotente (mismo `triggerAt` ⇒ mismo resultado); reconciliaciones repetidas no duplican recordatorios.
  - Envolver cada llamada a `WorkManager` en `runCatching` — un fallo de WorkManager nunca debe tumbar el reconciliador (mismo criterio que `AuthRepository.login`, que ya descarta silenciosamente un fallo al encolar).
- [x] T7: Registrar el binding en `SyncModule.kt`: `@Provides @Singleton fun provideReminderScheduler(impl: WorkManagerReminderScheduler): ReminderScheduler = impl`.

### Android — worker y notificación

- [x] T8: Crear `sync/reminders/ReminderContentBuilder.kt` — lógica pura (JVM-testeable, sin `android.*`): `fun build(installment: InstallmentEntity?, sale: SaleEntity?, clientName: String?): ReminderContent?` donde `ReminderContent(title: String, text: String)`. Devuelve `null` (no notificar) si `installment == null`, `installment.status != "pending"`, o `sale?.status` es `"cancelled"`/`"paid"`. Título: nombre del cliente, o `"Cobro próximo a vencer"` si `clientName` es null/vacío (fallback si el pull aún no trajo al cliente — no se pierde el recordatorio por eso). Texto: `"$<monto con 2 decimales> · vence el <d MMM yyyy>"` — mismo formato que `OrderDetailScreen.formatAmount`/`formatDate` (`"d MMM yyyy"`, `Locale.forLanguageTag("es-MX")`, `ZoneId.systemDefault()` — `zone` como parámetro para tests). Tests `ReminderContentBuilderTest.kt`: parcialidad `pending` con cliente → contenido correcto; parcialidad `paid` → null; venta `cancelled` → null; parcialidad inexistente → null; cliente ausente → título fallback.
- [x] T9: Crear `sync/workers/ReminderWorker.kt` — `@HiltWorker class ReminderWorker @AssistedInject constructor(@Assisted context, @Assisted params, installmentDao: InstallmentDao, saleDao: SaleDao, clientDao: ClientDao) : CoroutineWorker`. `doWork()`: leer `KEY_INSTALLMENT_ID`; `installmentDao.getById(id)`; `saleDao.getById(installment.fkSale, installment.fkTenant)`; `clientDao.getById(sale.fkClient)`; `ReminderContentBuilder.build(...)`; si `null` → `Result.success()` sin notificar; si `!NotificationManagerCompat.from(context).areNotificationsEnabled()` → `Result.success()` sin notificar (permiso denegado o notificaciones apagadas: AC-6, sin crash); si no, `notify(installmentId.hashCode(), notification)` (ID determinista: re-postear el mismo id actualiza en vez de apilar). Todo dentro de `try`/`catch` que relance `CancellationException` (igual que `PushWorker`/`PullWorker`) y devuelva `Result.success()` ante cualquier otro error — un recordatorio fallido NO debe reintentarse indefinidamente (un `Result.retry()` dispararía la notificación tarde y potencialmente duplicada).
  - La notificación usa el canal `"payment_reminders"`, ícono pequeño existente de la app (verificar `res/drawable`/mipmap; si no hay ícono monocromo apto, usar `android.R.drawable.ic_dialog_info` y anotarlo en Debug Log), `setAutoCancel(true)`, y un `PendingIntent.getActivity(..., Intent(context, MainActivity::class.java), FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT)` — sin deep link (ver Fuera de alcance).
- [x] T10: Crear `sync/reminders/NotificationChannels.kt` con `const val CHANNEL_PAYMENT_REMINDERS = "payment_reminders"` y `fun ensurePaymentReminderChannel(context: Context)` (crea `NotificationChannel(id, "Recordatorios de cobro", IMPORTANCE_DEFAULT)` vía `NotificationManagerCompat.createNotificationChannel`; idempotente). `minSdk = 26` ⇒ los canales son obligatorios siempre, no hace falta `if (Build.VERSION.SDK_INT >= 26)`.

### Android — reconciliador reactivo

- [x] T11: Crear `sync/reminders/ReminderReconciler.kt` — `@Singleton class ReminderReconciler @Inject constructor(private val installmentDao: InstallmentDao, private val settingsRepository: SettingsRepository, private val scheduler: ReminderScheduler, @TenantId private val tenantId: Flow<String?>)` con `fun start(scope: CoroutineScope, now: () -> Instant = Instant::now, zone: ZoneId = ZoneId.systemDefault())`:
  ```kotlin
  scope.launch {
      combine(
          tenantId.flatMapLatest { t -> if (t == null) flowOf(emptyList()) else installmentDao.observeOpenForTenant(t) },
          settingsRepository.observeDiasAnticipacion(),
      ) { installments, dias ->
          ReminderPlanner.plan(installments, dias, now(), zone) to installments.map { it.id }.toSet()
      }.collectLatest { (plans, openIds) -> scheduler.replaceAll(plans, openIds) }
  }
  ```
  Con `tenantId == null` (sin sesión) la lista de parcialidades queda vacía, así que `replaceAll(emptyList(), emptySet())` cancela todo (AC-6, cierre de sesión). **Por qué reactivo y no hooks**: los puntos de escritura que afectan parcialidades son 5 (`createSale`, `registerPayment`, `cancelSale`, `PullService` y resolución de conflictos de la 4.4) más el cambio de Settings; observar Room cubre TODOS sin tocar ninguno (ver Dev Notes).
- [x] T12: Tests `ReminderReconcilerTest.kt` (JVM puro, `runTest` + `UnconfinedTestDispatcher`/`backgroundScope`; `FakeInstallmentDao` de T2, `FakeSettingsDao` + `SettingsRepository`, `FakeReminderScheduler` que guarda la última lista recibida y el nº de llamadas, `MutableStateFlow<String?>` como tenant, `now`/`zone` fijos inyectados en `start`): (a) parcialidad futura → scheduler recibe 1 plan con el `triggerAt` esperado y su id en `openInstallmentIds`; (b) marcar la parcialidad `paid` vía `upsertAll` → scheduler recibe planes vacíos Y `openInstallmentIds` vacío (AC-3); (c) cambiar `dias_anticipacion_recordatorio` en el FakeSettingsDao → el `triggerAt` se recalcula (AC-4); (d) tenant pasa a `null` → `replaceAll(emptyList(), emptySet())` (AC-6); (e) parcialidad de OTRO tenant no se programa; (f) parcialidad abierta cuyo `triggerAt` ya pasó (pero `dueDate` futura) → NO está en `plans` pero SÍ en `openInstallmentIds` (así el scheduler no cancela su recordatorio ya encolado).

### Android — arranque, permiso y manifest

- [x] T13: `SumitrackApp.onCreate()`: inyectar `@Inject lateinit var reminderReconciler: ReminderReconciler`; llamar `ensurePaymentReminderChannel(this)` y `reminderReconciler.start(CoroutineScope(SupervisorJob() + Dispatchers.Default))`. Como `SumitrackApp` ya es `@HiltAndroidApp` con `@Inject lateinit var workerFactory`, los campos están disponibles en `onCreate` después de `super.onCreate()`. El scope vive lo que el proceso — correcto: el reconciliador debe correr mientras haya proceso; al reiniciar la app se reconcilia de nuevo (y WorkManager ya persiste el trabajo encolado tras un reinicio del dispositivo por sí mismo).
- [x] T14: `AndroidManifest.xml`: agregar `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` (el proyecto tiene `targetSdk = 36`; sin esto el permiso runtime nunca se puede conceder en Android 13+).
- [x] T15: `MainScreen.kt` (composable posterior al login, ya renderizado solo con `SessionState.LoggedIn`): pedir el permiso una vez al entrar — `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }` + `LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) != PERMISSION_GRANTED) launcher.launch(POST_NOTIFICATIONS) }`. Sin diálogo explicativo propio ni reintento (AC-6: "no vuelve a insistir por su cuenta"; el sistema ya limita los prompts tras denegaciones repetidas). El resultado del launcher se ignora: el worker consulta `areNotificationsEnabled()` cada vez.
- [x] T16: Verificación: `./gradlew compileDebugKotlin compileDebugUnitTestKotlin testDebugUnitTest assembleDebug` (suite completa; estado previo: 358 tests, 0 fallos). La verificación en dispositivo real (disparo real de WorkManager, prompt de permiso, tap en la notificación) NO es posible en este entorno (sin `adb`/emulador) — anotarla explícitamente como pendiente en Completion Notes, igual que el gap heredado desde 2.1.

### Review Findings

Revisión ejecutada con `/code-review` (7 hallazgos), verificados contra el código antes de aplicarlos.

- [x] [Review][Patch] El prompt de `POST_NOTIFICATIONS` se repetía en cada arranque en frío: el flag `rememberSaveable` solo sobrevive a la rotación, contradiciendo AC-6 ("no vuelve a insistir") [MainScreen.kt] — **Corregido**: el flag `notification_permission_asked` ahora vive en `SharedPreferences` — una sola vez por instalación.
- [x] [Review][Patch] Cada emisión de Room (p. ej. `markSynced` tras cada push) re-planeaba y re-encolaba (`REPLACE`) TODOS los recordatorios aunque el plan fuera idéntico [ReminderReconciler.kt] — **Corregido**: `.distinctUntilChanged()` sobre el par `(plans, openIds)`. Test nuevo: `una escritura que no cambia el plan (markSynced) no vuelve a llamar al scheduler`.
- [x] [Review][Patch] El reconciliador corre en un scope de aplicación sin manejo de excepciones — un fallo del flujo de Room o de `ReminderPlanner` tumbaba el proceso al arrancar [ReminderReconciler.kt] — **Corregido**: `.catch { }` sobre el flujo combinado (un fallo detiene el reconciliador hasta el próximo arranque; los recordatorios no son críticos). Test nuevo: `un fallo del flujo de Room no propaga la excepcion ni llama al scheduler`.
- [x] [Review][Patch] `zone = ZoneId.systemDefault()` se evaluaba una sola vez en `start()` — un cambio de zona horaria con el proceso vivo dejaba el cálculo con la zona vieja [ReminderReconciler.kt] — **Corregido**: `zone` ahora es un proveedor `() -> ZoneId` que se lee en cada re-plan (igual que `now`).
- [x] [Review][Patch] `installmentId.hashCode()` (32 bits) como ID de notificación y `requestCode` podía colisionar entre dos parcialidades y pisar un recordatorio [ReminderWorker.kt] — **Corregido**: `notify(tag = "payment-reminder:<id>", id = 0, ...)` (sin colisión posible) y `requestCode = 0` (sin deep link, un solo `PendingIntent` basta).
- [x] [Review][Defer] Si el recordatorio se dispara con notificaciones apagadas/permiso denegado, el trabajo queda `SUCCEEDED` y nunca se recupera al activarlas después — deferred: es exactamente el "se omiten silenciosamente" de AC-6; recuperarlo exigiría persistir qué recordatorios no se mostraron.
- [Review][Dismissed] "El camino `installmentId == null` de `registerPayment` deja parcialidades `pending` en una venta `paid`" — no alcanzable: `RegisterPaymentUseCase` lo rechaza cuando la venta tiene parcialidades. Solo se corrigió el comentario del DAO para dejar explícito ese invariante.

## Dev Notes

### Por qué un reconciliador reactivo y no "ganchos" en cada punto de escritura

Las parcialidades cambian en cinco lugares: `SaleRepository.createSale` (alta), `registerPayment` (a `paid`), `cancelSale` (a `cancelled`), `PullService` (parcialidades que llegan de otro dispositivo) y la resolución de conflictos de la 4.4 (`ConflictViewModel`, que reescribe la fila) — más el cambio de `dias_anticipacion_recordatorio` en S-14. Engancharse a cada uno significaría inyectar un `ReminderScheduler` en `SaleRepository` (cuyo constructor lo usan decenas de tests con Fakes), en `PullService` y en `SettingsViewModel`, y aun así el siguiente punto de escritura que alguien agregue se olvidaría del hook. Observar `InstallmentDao.observeOpenForTenant` + `SettingsRepository.observeDiasAnticipacion` con un único `combine` cubre todos esos caminos —y los futuros— con cero cambios en el código existente. Es la misma razón por la que 4.3 detectó fallos vía `SyncEventBus` en vez de instrumentar cada worker. El texto del AC de `epics.md` dice "cuando `PushWorker` programa las alarmas"; ese es un remanente de una idea temprana de `architecture.md` — la observable de Room lo supera sin acoplar recordatorios al motor de sync.

### Por qué no hace falta persistir "ya se notificó"

Riesgo natural: reconciliar de nuevo tras el disparo ¿volvería a notificar? No: `ReminderPlanner` solo incluye disparos con `triggerAt > now`. Una vez pasado el momento (haya o no notificado), esa parcialidad deja de estar en `plans` y nunca se reprograma — no hay duplicados y no se necesita columna ni DataStore.

Escenario "dispositivo apagado a la hora del disparo": el trabajo YA está encolado en la base de WorkManager, que lo ejecuta en cuanto el dispositivo vuelve. Para no destruirlo, el reconciliador NO puede cancelar "todo lo que ya no está en `plans`" (un trabajo cuya hora ya pasó también sale de `plans` y se perdería el recordatorio). Por eso `replaceAll` recibe un segundo parámetro, `openInstallmentIds` (todas las parcialidades `pending`): solo se cancelan los `ENQUEUED` cuya parcialidad ya NO está abierta (pagada, cancelada o inexistente); los abiertos con hora ya pasada se dejan correr.

### Constraint de red — el error más probable

`PushWorker`, `PullWorker`, `TriggerPushSyncUseCase`, `TriggerPullSyncUseCase` y `AuthRepository` construyen sus `OneTimeWorkRequest` con `Constraints(NetworkType.CONNECTED)`. Copiar ese bloque para el recordatorio rompería AC-5 en silencio: sin red el trabajo se quedaría `ENQUEUED` indefinidamente y la notificación nunca aparecería justo cuando el vendedor está en campo sin señal. El `ReminderWorker` no lleva ningún constraint.

### Fuente de "monto pendiente" y de "vencida"

La parcialidad es atómica: se cobra completa (`RegisterPaymentUseCase` usa `installment.amount`; no hay cobros parciales de una parcialidad). Por eso "monto pendiente" de una parcialidad `pending` = `installment.amount`. "Vencida" no es un status persistido (`OrderDetailViewModel.toUiStatus`, Historia 3.5); aquí solo importan las `pending` con `dueDate` futura, así que no se usa.

### `dueDate` es un `Instant`; el día se calcula en la zona del dispositivo

`CalculateInstallmentsUseCase` genera `dueDate` desde `ZonedDateTime.now()` (misma hora del día en que se creó la venta, zona del sistema), y la UI la muestra con `ZoneId.systemDefault()`. `ReminderPlanner` debe convertir con la MISMA zona para que el día del recordatorio coincida con la fecha que el proveedor ve en pantalla. Zona pasada como parámetro (tests fijan `America/Mexico_City`).

### Cambios de DAO ⇒ actualizar Fakes (regla recurrente)

Es la tercera historia consecutiva (4.4, 5.1, 5.2) donde una interfaz gana un método. `InstallmentDao` tiene al menos `FakeInstallmentDao` (`ui/screens/orders/`). Buscar y actualizar todos antes de compilar tests. No hay librería de mocking en el proyecto (solo Fakes escritos a mano) — no introducir Mockito/MockK.

### Lo que ya existe y NO hay que tocar

- `SettingsViewModel`/`SettingsScreen` (Historia 5.1): ya persisten `dias_anticipacion_recordatorio` (1–30, `pending`). El reconciliador lo observa solo — NO agregar llamadas de reprogramación ahí (la 5.1 lo dejó explícitamente para esta historia).
- `SaleRepository`, `PullService`, `SyncManager`, `RegisterPaymentUseCase`, `CancelSaleUseCase`: sin cambios.
- Backend: sin cambios.

### Testing

- JVM puro con Fakes: `ReminderPlannerTest`, `ReminderContentBuilderTest`, `ReminderReconcilerTest`, más casos nuevos en `SettingsRepositoryTest`.
- No hay `work-testing` en el proyecto y no se debe agregar (dependencia nueva requiere aprobación): `WorkManagerReminderScheduler` y `ReminderWorker` son delgados por diseño (todo lo con lógica vive en `ReminderPlanner`/`ReminderContentBuilder`) y se verifican por compilación + revisión; la ejecución real queda como verificación manual pendiente (T16).

### References

- `_bmad-output/planning-artifacts/epics.md` — Historia 5.2 (líneas 872-898), FR-21, FR-22
- `_bmad-output/planning-artifacts/architecture/architecture.md` — línea 231 (WorkManager local, sin Firebase en v1)
- `_bmad-output/implementation-artifacts/5-1-pantalla-de-configuracion-del-tenant.md` — `dias_anticipacion_recordatorio` ya persistido; "Fuera de alcance" que delega la reprogramación a esta historia
- `_bmad-output/implementation-artifacts/4-3-indicadores-de-sincronizacion-en-la-ui.md` — patrón de interfaz angosta + Fake para WorkManager (`PushSyncTrigger`, `SyncWorkObserver`)
- `android/app/src/main/java/com/sumitrack/android/SumitrackApp.kt` — `HiltWorkerFactory` + único punto de encolado de `push-sync`

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- **`backgroundScope` + `advanceUntilIdle()` no se llevan**: los 6 tests de `ReminderReconcilerTest` fallaron al principio (`List is empty`) porque `advanceUntilIdle()` no ejecuta las corrutinas de `backgroundScope` (las trata como trabajo de fondo). Se confirmó con un test de depuración (borrado) que ningún colector arrancaba. Solución: `runCurrent()` en esos tests (comentario en el archivo). Vale para futuros tests que arranquen un colector de larga vida en `backgroundScope`.
- **`replaceAll` con dos parámetros** (`plans` + `openInstallmentIds`), como fijan las Dev Notes: si solo recibiera `plans`, cancelaría un recordatorio ya encolado cuya hora pasó con el dispositivo apagado.
- **`try/catch` con relanzado de `CancellationException` en vez de `runCatching`** en la parte suspendible de `WorkManagerReminderScheduler.replaceAll` (la lectura de `getWorkInfosByTagFlow(...).first()`): `runCatching` tragaría la cancelación de `collectLatest` y seguiría encolando con un plan obsoleto. Las llamadas de encolado (no suspendibles) sí usan `runCatching`.
- **Ícono de la notificación**: el proyecto no tiene NINGÚN recurso `drawable`/`mipmap` (solo `values` y `xml`), así que se usa `android.R.drawable.ic_dialog_info` como prevé la historia. Un ícono propio (monocromo) queda pendiente — ver `deferred-work.md`.
- **Sin cambios en `SettingsViewModel`** (AC-4): el reconciliador observa el valor solo.
- Comandos: `./gradlew testDebugUnitTest --tests ReminderPlannerTest --tests SettingsRepositoryTest` (tras T1-T5), `./gradlew compileDebugKotlin compileDebugUnitTestKotlin`, `./gradlew testDebugUnitTest assembleDebug` (suite completa, 381 tests, 0 fallos; build exitoso). Tras la ronda de revisión: 383 tests, 0 fallos.

### Completion Notes List

- T1-T3: `InstallmentDao.observeOpenForTenant` (sin join con `sales`), `FakeInstallmentDao` migrado a `MutableStateFlow` (único Fake de `InstallmentDao`), `SettingsRepository.observeDiasAnticipacion()` (default 3, acota 1–30). +4 tests en `SettingsRepositoryTest`.
- T4-T5: `ReminderPlanner` (puro; `now`/`zone` parámetros; 09:00 hora local; solo disparos futuros). 6 tests.
- T6-T7: `ReminderScheduler` + `WorkManagerReminderScheduler` (nombre único `reminder-<id>`, tags `payment-reminder` y `reminder-installment:<id>`, **sin constraint de red**, `REPLACE` idempotente, cancela solo los `ENQUEUED` cuya parcialidad ya no está abierta). Binding en `SyncModule`.
- T8: `ReminderContentBuilder` (puro): no notifica si la parcialidad no está `pending` o la venta está `cancelled`/`paid`; cliente/venta ausentes → título genérico "Cobro próximo a vencer". 7 tests.
- T9-T10: `ReminderWorker` (`@HiltWorker`, solo Room, `Result.success()` siempre, omite si `areNotificationsEnabled()` es falso, ID de notificación determinista, `PendingIntent` a `MainActivity`) y `ensurePaymentReminderChannel`.
- T11-T12: `ReminderReconciler` reactivo (`combine` de parcialidades abiertas del tenant + días de anticipación → `replaceAll`); cubre todos los puntos de escritura sin engancharse a ninguno; tenant null cancela todo. 6 tests (AC-1, AC-3, AC-4, AC-6, aislamiento por tenant, hora ya pasada).
- T13-T15: `SumitrackApp` crea el canal y arranca el reconciliador; `POST_NOTIFICATIONS` en el manifest; `MainScreen` pide el permiso en Android 13+ una sola vez por instalación (flag en `SharedPreferences`; ver ronda de revisión — la primera versión usaba `rememberSaveable` y repetía el prompt en cada arranque en frío). Sin reintento propio: si se niega, se puede activar desde los ajustes del sistema.
- T16: `./gradlew testDebugUnitTest assembleDebug` → 381 tests, 0 fallos, build exitoso. **PENDIENTE (no verificable en este entorno, sin `adb`/emulador)**: disparo real de WorkManager a la hora programada, el prompt de permiso en un dispositivo Android 13+, y el tap en la notificación. `WorkManagerReminderScheduler` y `ReminderWorker` no tienen tests directos (no hay `work-testing` en el proyecto y agregarlo requiere aprobación; toda la lógica vive en `ReminderPlanner`/`ReminderContentBuilder`/`ReminderReconciler`, que sí están cubiertos).
- Ronda de revisión (`/code-review`, 2026-09-21): 7 hallazgos → 5 patches aplicados (prompt de permiso persistente, `distinctUntilChanged`, `.catch` en el scope de aplicación, proveedor de zona horaria, ID de notificación sin colisión), 1 diferido, 1 descartado (no alcanzable). +2 tests en `ReminderReconcilerTest` (total 8). Verificación final: **383 tests, 0 fallos**, `assembleDebug` exitoso.
- AC-5 (offline): garantizado por diseño — el request no lleva constraint de red y el worker solo lee Room.

### File List

**Nuevos:**
- `android/app/src/main/java/com/sumitrack/android/sync/reminders/ReminderPlanner.kt`
- `android/app/src/main/java/com/sumitrack/android/sync/reminders/ReminderContentBuilder.kt`
- `android/app/src/main/java/com/sumitrack/android/sync/reminders/ReminderScheduler.kt`
- `android/app/src/main/java/com/sumitrack/android/sync/reminders/ReminderReconciler.kt`
- `android/app/src/main/java/com/sumitrack/android/sync/reminders/NotificationChannels.kt`
- `android/app/src/main/java/com/sumitrack/android/sync/workers/ReminderWorker.kt`
- `android/app/src/test/java/com/sumitrack/android/sync/reminders/ReminderPlannerTest.kt`
- `android/app/src/test/java/com/sumitrack/android/sync/reminders/ReminderContentBuilderTest.kt`
- `android/app/src/test/java/com/sumitrack/android/sync/reminders/ReminderReconcilerTest.kt`

**Modificados:**
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/InstallmentDao.kt` (`observeOpenForTenant`)
- `android/app/src/main/java/com/sumitrack/android/data/repositories/SettingsRepository.kt` (`observeDiasAnticipacion`)
- `android/app/src/main/java/com/sumitrack/android/di/SyncModule.kt` (binding de `ReminderScheduler`)
- `android/app/src/main/java/com/sumitrack/android/SumitrackApp.kt` (canal + arranque del reconciliador)
- `android/app/src/main/AndroidManifest.xml` (`POST_NOTIFICATIONS`)
- `android/app/src/main/java/com/sumitrack/android/ui/screens/MainScreen.kt` (solicitud del permiso, una vez por instalación)
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/FakeInstallmentDao.kt` (Flow + `observeOpenForTenant`)
- `android/app/src/test/java/com/sumitrack/android/data/repositories/SettingsRepositoryTest.kt` (+4 tests)

## Change Log

| Fecha | Cambio |
|-------|--------|
| 2026-09-21 | Historia creada (`bmad-create-story`), status: ready-for-dev |
| 2026-09-21 | Implementación completa (T1-T16), status: review |
| 2026-09-21 | Code review (`/code-review`, 7 hallazgos) — 5 patches aplicados, 1 diferido, 1 descartado. Status: done |

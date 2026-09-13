---
baseline_commit: 62dc535a0c5330740089e6e7743b1cd9e0a855bd
---

# Story 5.1: Pantalla de Configuración del Tenant

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

Como proveedor,
quiero configurar los datos de mi empresa y los parámetros de operación desde la app,
para que mis tickets muestren mi información correcta y el sistema se comporte según mis preferencias.

## Acceptance Criteria

**AC-1 — Estructura de 5 secciones**

**Dado** que el proveedor navega al tab Config
**Cuando** S-14 se muestra
**Entonces** la pantalla está organizada en secciones, en este orden: Datos Fiscales · Catálogo de Productos · Parámetros de Venta · Sincronización · Sesión

**AC-2 — Datos Fiscales**

**Dado** que el proveedor edita los Datos Fiscales (nombre/razón social, RFC, dirección fiscal, teléfono)
**Cuando** toca "Guardar" en esa sección
**Entonces** los 4 valores persisten en `SettingsEntity` (keys: `negocio_nombre`, `negocio_rfc`, `negocio_direccion`, `negocio_telefono`) con `sync_status = pending`; estos datos ya son consumidos por `GenerateTicketUseCase` para todos los tickets generados

**AC-3 — Parámetros de Venta: max_parcialidades y serie_folio**

**Dado** que el proveedor modifica `max_parcialidades` (entero, rango 1–15) o `serie_folio` (texto, máximo 5 caracteres)
**Cuando** toca "Guardar" en la sección Parámetros de Venta
**Entonces** los nuevos valores persisten (`sync_status = pending`) y se aplican inmediatamente a las siguientes ventas nuevas — `max_parcialidades` ya es leído por `PaymentViewModel`, `serie_folio` ya es leído por `ValidateFolioUseCase`

**AC-4 — Parámetros de Venta: dias_anticipacion_recordatorio**

**Dado** que el proveedor modifica `dias_anticipacion_recordatorio` (entero, rango 1–30)
**Cuando** toca "Guardar" en la sección Parámetros de Venta
**Entonces** el nuevo valor persiste (`sync_status = pending`) — la reprogramación real de alarmas de notificación queda fuera de esta historia (ver "Fuera de alcance")

**AC-5 — Sincronizar ahora**

**Dado** que el proveedor toca "Sincronizar ahora" en la sección Sincronización
**Cuando** se ejecuta
**Entonces** se encola un pull manual inmediato (mismo mecanismo de `PullWorker` que ya usa `AuthRepository` al hacer login, con nombre de trabajo único `"pull-sync"`) — no requiere nuevo endpoint ni cambios de backend

**AC-6 — Validación de campos numéricos**

**Dado** que el proveedor ingresa un valor no numérico o fuera de rango en `max_parcialidades`, `serie_folio` o `dias_anticipacion_recordatorio`
**Cuando** intenta guardar esa sección
**Entonces** el campo inválido muestra `isError = true` y `supportingText` descriptivo; el botón "Guardar" de esa sección permanece deshabilitado hasta corregir

**AC-7 — Catálogo de Productos y Sesión sin regresión**

**Dado** que las filas "Catálogo de productos" (navega a `ProductListScreen`), "Sincronización" (navega a `ConflictLogScreen`) y "Cerrar sesión" (AlertDialog destructivo) ya existen y funcionan
**Cuando** se reorganiza la pantalla en 5 secciones
**Entonces** estas 3 funcionalidades seguían funcionando exactamente igual, solo reubicadas dentro de sus secciones correspondientes (Catálogo de Productos, Sincronización, Sesión)

## Fuera de alcance (decisiones de scope explícitas)

- **Reprogramación real de alarmas al guardar `dias_anticipacion_recordatorio`** — el AC literal de `epics.md` dice "el WorkManager de notificaciones reprograma todas las alarmas pendientes", pero ese WorkManager de notificaciones (`AlarmManager`/`NotificationWorker`) es exactamente lo que construye la Historia 5.2 (`backlog`), que hoy no existe (confirmado: no hay ningún `NotificationManager`/`AlarmManager`/`ReminderWorker` en el proyecto). **Confirmado con el usuario**: esta historia solo persiste el valor (`sync_status = pending`), igual que los demás campos; 5.2 leerá este valor cuando construya su motor de alarmas. Mismo patrón que 4.2→4.3→4.4 (cada historia extiende sobre infraestructura que ya existe, nunca la que falta).
- **Push automático tras guardar un setting** — igual que `ClientFormViewModel` (Historia 2.2) no dispara `PushSyncTrigger` tras crear/editar un cliente, guardar un setting solo lo marca `pending`; el `push-sync` periódico (cada 15 min, Historia 4.1) lo sube. "Sincronizar ahora" (AC-5) es explícitamente un **pull**, no un push — así lo especifica `epics.md` textualmente ("`PullService` inicia un pull manual").
- **Validación de formato de RFC/teléfono en Datos Fiscales** — ni `epics.md` ni el `ClientEntity.rfc` existente (sin validación de formato) lo exigen; los 4 campos de Datos Fiscales son de texto libre, sin validación de contenido (solo se guardan tal cual, igual que `ClientFormViewModel` trata RFC/dirección/notas como opcionales sin formato).
- **Seed de datos fiscales por defecto en el backend** — `negocio_nombre/rfc/direccion/telefono` no existen en el seed de desarrollo (`ApplicationBuilderExtensions.cs`, solo siembra `max_parcialidades`, `serie_folio`, `dias_anticipacion_recordatorio`); no hace falta agregarlos ahí — el backend acepta cualquier `key` no vacía en el push genérico (`ValidateSetting` solo exige `key` no vacía), así que un tenant nuevo simplemente no tiene esas keys hasta que el proveedor las guarda por primera vez desde S-14. `GenerateTicketUseCase` ya tolera su ausencia con `.orEmpty()`.
- **Cambios de backend** — esta historia es 100% Android. El endpoint de push genérico (`PushSettingsAsync`) y pull genérico (`PullSettingsAsync`) en `SyncService.cs` ya soportan cualquier `key`/`value` arbitrarios desde la Historia 4.1; no requieren cambios.

## Tasks / Subtasks

### Android — persistencia de settings individuales

- [x] T1: Agregar `SettingsRepository.updateSetting(key: String, value: String)` — hace `settingsDao.upsertAll(listOf(SettingsEntity(key = key, value = value, createdAt = Instant.now(), syncStatus = "pending")))`. Bumpear `createdAt` a `Instant.now()` en cada llamada es intencional: `SettingsEntity` no tiene `updatedAt` propio (ver Dev Notes de Historia 4.4) — `createdAt` es el campo reutilizado como timestamp de sincronización en `SyncManager.toSyncDto()`, así que debe avanzar en cada edición para que la detección de conflictos de Historia 4.4 funcione correctamente sobre settings editados desde esta pantalla.
- [x] T2: Agregar `SettingsRepository.getAllValues(): Map<String, String>` — `settingsDao.getAll().first().associate { it.key to it.value.orEmpty() }`. Usado por `SettingsViewModel` para poblar el estado inicial de los 7 campos con una sola consulta en vez de 7 llamadas a `getValue`.
- [x] T3: Tests de `SettingsRepository` (nuevo archivo `SettingsRepositoryTest.kt`, no existe todavía) cubriendo `updateSetting` (upsert con `sync_status = pending`, `createdAt` avanza) y `getAllValues` (mapa correcto, keys ausentes no aparecen). Usar `FakeSettingsDao` existente (`ui/screens/orders/FakeSettingsDao.kt`).

### Android — PullSyncTrigger (extracción para "Sincronizar ahora")

- [x] T4: Crear `android/app/src/main/java/com/sumitrack/android/sync/TriggerPullSyncUseCase.kt` — mismo patrón que `TriggerPushSyncUseCase.kt` (interfaz angosta `fun interface PullSyncTrigger { operator fun invoke() }` + implementación `TriggerPullSyncUseCase` que encola `OneTimeWorkRequestBuilder<PullWorker>()` con `Constraints(NetworkType.CONNECTED)` vía `workManager.enqueueUniqueWork("pull-sync", ExistingWorkPolicy.REPLACE, request)`). Reutiliza el mismo nombre de trabajo `"pull-sync"` que ya usa `AuthRepository` en login — así `SyncStatusViewModel.isSyncing` (que ya observa `"pull-sync"` como trabajo one-time `RUNNING||ENQUEUED`, Historia 4.3) refleja el pull manual sin ningún cambio adicional.
- [x] T5: Registrar el binding en `SyncModule.kt`: `@Provides @Singleton fun providePullSyncTrigger(useCase: TriggerPullSyncUseCase): PullSyncTrigger = useCase` (misma forma que `providePushSyncTrigger`).
- [x] T6: **No modificar** `AuthRepository.kt` — sigue encolando su propio `OneTimeWorkRequestBuilder<PullWorker>()` inline en login; no hay necesidad funcional de que reutilice `PullSyncTrigger` y hacerlo sería un refactor no pedido por esta historia.

### Android — SettingsViewModel (reescritura)

- [x] T7: Reescribir `SettingsUiState` con: `showLogoutDialog: Boolean`, y dos sub-estados independientes:
  - Datos Fiscales: `businessName: String`, `rfc: String`, `address: String`, `phone: String`, `isSavingFiscal: Boolean`, `fiscalSaved: Boolean` (para mostrar confirmación breve, se limpia solo)
  - Parámetros de Venta: `maxParcialidades: String`, `serieFolio: String`, `diasAnticipacion: String`, `maxParcialidadesError: String?`, `serieFolioError: String?`, `diasAnticipacionError: String?`, `isSavingParams: Boolean`, `paramsSaved: Boolean`
  - `isFiscalSaveEnabled: Boolean get() = !isSavingFiscal` (Datos Fiscales no tiene validación de contenido, ver "Fuera de alcance")
  - `isParamsSaveEnabled: Boolean get() = !isSavingParams` (la validación ocurre al tocar Guardar, no deshabilita reactivamente — mismo patrón que errores de `ClientFormViewModel`, que también valida en el submit)
- [x] T8: En `init`, cargar `settingsRepository.getAllValues()` (T2) y poblar los 7 campos de una vez (valores ausentes → `""`).
- [x] T9: Implementar `onBusinessNameChange/onRfcChange/onAddressChange/onPhoneChange` (actualizan el campo, limpian `fiscalSaved`).
- [x] T10: Implementar `onSaveFiscalClick()` — sin validación de contenido (ver Fuera de alcance), guarda los 4 campos vía 4 llamadas a `settingsRepository.updateSetting(key, value.trim())` dentro de `viewModelScope.launch`, con `isSavingFiscal = true` durante la operación y `fiscalSaved = true` al terminar.
- [x] T11: Implementar `onMaxParcialidadesChange/onSerieFolioChange/onDiasAnticipacionChange` (actualizan el campo, limpian el error de ese campo específico y `paramsSaved`).
- [x] T12: Implementar `onSaveParamsClick()` — valida los 3 campos:
  - `maxParcialidades`: `toIntOrNull()` debe estar en `1..15`; si no, error = `"Debe ser un número entre 1 y 15"`
  - `serieFolio`: longitud `1..5` (no vacío — a diferencia de `ValidateFolioUseCase` que tolera blank con fallback `"A"`, aquí se exige explícito para que el proveedor sepa qué prefijo está guardando); si no, error = `"Debe tener entre 1 y 5 caracteres"`
  - `diasAnticipacion`: `toIntOrNull()` debe estar en `1..30`; si no, error = `"Debe ser un número entre 1 y 30"`
  - Si cualquiera falla, setea los errores correspondientes y `return` sin guardar (ningún campo se guarda parcialmente — igual que `SyncService.PushClientesAsync` valida todo antes de escribir, aquí se valida todo antes de guardar cualquiera de los 3).
  - Si los 3 son válidos, guarda vía `settingsRepository.updateSetting` (3 llamadas) con `isSavingParams = true` → `paramsSaved = true`.
- [x] T13: Inyectar `pullSyncTrigger: PullSyncTrigger` (T4/T5) y agregar `onSyncNowClick() = pullSyncTrigger()`.
- [x] T14: Tests de `SettingsViewModelTest.kt` (nuevo archivo) cubriendo: carga inicial de los 7 valores, guardar Datos Fiscales persiste las 4 keys como `pending`, guardar Parámetros de Venta con valores válidos persiste las 3 keys, cada uno de los 3 casos de validación fuera de rango (`max_parcialidades=0`, `max_parcialidades=16`, `serie_folio` vacío, `serie_folio` de 6 caracteres, `dias_anticipacion_recordatorio=0`, `=31`) deja el estado sin guardar y con el error correspondiente, `onSyncNowClick` invoca el `PullSyncTrigger` fake exactamente una vez. Reutilizar `FakeSettingsDao` (`ui/screens/orders/FakeSettingsDao.kt`); crear un fake trivial de `PullSyncTrigger` (SAM, un lambda que incrementa un contador basta, no requiere clase nueva).

### Android — SettingsScreen (reorganización en 5 secciones)

- [x] T15: Reescribir `SettingsScreen.kt` en 5 secciones, en este orden exacto (AC-1), cada una con un `Text` de encabezado (`titleMedium`, `onSurfaceVariant`) seguido de su contenido, separadas por `HorizontalDivider()`:
  1. **Datos Fiscales**: 4 `OutlinedTextField` (nombre/razón social, RFC, dirección, teléfono) con `ImeAction.Next` entre ellos y `ImeAction.Done` en el último, botón "Guardar" (`enabled = uiState.isFiscalSaveEnabled`) al final de la sección.
  2. **Catálogo de Productos**: la fila `Surface(onClick = onCatalogClick)` existente, sin cambios de comportamiento.
  3. **Parámetros de Venta**: 3 `OutlinedTextField` (`max_parcialidades` y `dias_anticipacion_recordatorio` con `KeyboardType.Number`, `serie_folio` de texto libre), cada uno con `isError`/`supportingText` ligado a su error de `uiState`, botón "Guardar" (`enabled = uiState.isParamsSaveEnabled`) al final.
  4. **Sincronización**: la fila `Surface(onClick = onSyncLogClick)` existente sin cambios, más un nuevo `Button`/`OutlinedButton` "Sincronizar ahora" (`onClick = viewModel::onSyncNowClick`) debajo.
  5. **Sesión**: el botón "Cerrar sesión" y su `AlertDialog` de confirmación existentes, sin cambios de comportamiento.
  - Eliminar el texto placeholder "Más opciones estarán disponibles próximamente." (ya no aplica — la pantalla ahora tiene contenido real en todas las secciones).
  - Envolver todo en `Column(Modifier.verticalScroll(rememberScrollState()))` — el contenido ya no cabe en una pantalla sin scroll con 5 secciones.
- [x] T16: Verificar manualmente (o vía test de Compose si el proyecto tuviera suite de UI tests — no la tiene, ver Dev Notes de Historia 3.x) que las 3 funcionalidades preexistentes (T-catálogo, T-sync-log, T-logout) siguen intactas tras la reorganización (AC-7).

### Review Findings

- [x] [Review][Patch] AC-6 violado: "Guardar" de Parámetros de Venta nunca se deshabilita tras un error de validación [SettingsViewModel.kt:isParamsSaveEnabled] — **Corregido**: `isParamsSaveEnabled` ahora recomputa `!isSavingParams && maxParcialidadesError == null && serieFolioError == null && diasAnticipacionError == null`, así se deshabilita en cuanto `onSaveParamsClick` setea un error y se rehabilita solo cuando `onXxxChange` lo limpia. Test nuevo: `isParamsSaveEnabled se deshabilita tras un error de validacion y se rehabilita al corregir (AC-6)`.
- [x] [Review][Patch] Las 3-4 llamadas secuenciales a `updateSetting` no son atómicas — un fallo a mitad de secuencia deja escritura parcial aunque la UI reporte el guardado completo como fallido [SettingsViewModel.kt:onSaveFiscalClick/onSaveParamsClick] — **Corregido**: nuevo `SettingsRepository.updateSettings(values: Map<String,String>)` hace un único `upsertAll(lista)` — Room ejecuta el upsert de una lista en una sola transacción. `onSaveFiscalClick`/`onSaveParamsClick` ahora llaman este método una sola vez en vez de N `updateSetting` secuenciales. Test nuevo: `updateSettings guarda varias keys en un solo lote, todas pending`.
- [x] [Review][Patch] `fiscalError`/`paramsError` no se limpian cuando el usuario edita un campo tras un guardado fallido [SettingsViewModel.kt:onBusinessNameChange/onRfcChange/onAddressChange/onPhoneChange/onMaxParcialidadesChange/onSerieFolioChange/onDiasAnticipacionChange] — **Corregido**: los 7 handlers `onXxxChange` ahora también limpian `fiscalError`/`paramsError` respectivamente.
- [x] [Review][Patch] `paramsError` de un intento fallido anterior convive con errores de validación nuevos si un guardado posterior falla la validación [SettingsViewModel.kt:onSaveParamsClick, rama de validación fallida] — **Corregido**: la rama de validación fallida de `onSaveParamsClick` ahora también setea `paramsError = null`.
- [x] [Review][Patch] Los campos de texto están habilitados y editables antes de que `init` termine de cargar los 7 valores [SettingsViewModel.kt:init / SettingsScreen.kt] — **Corregido**: nuevo `SettingsUiState.isLoading` (default `true`, pasa a `false` al terminar `init`); `SettingsScreen` muestra un `CircularProgressIndicator` centrado y no renderiza ningún campo mientras `isLoading` — mismo patrón que `ClientFormScreen`.
- [x] [Review][Patch] `settingsRepository.getAllValues()` en `init` no está protegido [SettingsViewModel.kt:init] — **Corregido**: envuelto en `runCatching { ... }.getOrDefault(emptyMap())` — un fallo de carga deja los 7 campos vacíos en vez de tumbar la pantalla.
- [x] [Review][Patch] `onSyncNowClick`/`pullSyncTrigger()` no está protegido [SettingsViewModel.kt:onSyncNowClick] — **Corregido**: envuelto en `runCatching { pullSyncTrigger() }`.
- [x] [Review][Patch] Dev Agent Record subcuenta los tests de `SettingsViewModelTest.kt` (dice 11, el archivo tiene 15) [historia, Completion Notes List] — **Corregido**: Completion Notes List actualizada con el conteo real (16, tras sumar el test de AC-6) y la enumeración completa de qué cubre cada test nuevo.
- [x] [Review][Defer] Cerrar sesión mientras un guardado de settings está en curso puede dejar filas residuales tras `clearLocalSettings()` (race infrecuente, misma clase de riesgo ya tolerada en `deferred-work.md` desde Historia 4.4) — deferred, pre-existing
- [x] [Review][Defer] `getAllValues()` es una foto fija (`.first()`), no un `Flow` reactivo — la pantalla no refleja un pull concurrente exitoso hasta reabrirse (mismo patrón de carga única que `ClientFormViewModel` ya usa en todo el proyecto) — deferred, pre-existing
- [x] [Review][Defer] "Sincronizar ahora" sin debounce — taps repetidos reinician el pull en curso (`ExistingWorkPolicy.REPLACE`) — deferred, pre-existing
- [x] [Review][Defer] Sin indicador de carga (`CircularProgressIndicator`) dentro de los botones "Guardar" mientras `isSaving*` — inconsistente con el patrón ya establecido en `ClientFormScreen` — deferred, pre-existing
- [x] [Review][Defer] Los textos de confirmación ("...guardados.") no se autolimpian con el tiempo — deferred, pre-existing
- [x] [Review][Defer] `TriggerPullSyncUseCase` no tiene test dedicado (cobertura indirecta solo vía el fake de `SettingsViewModelTest`) — deferred, pre-existing
- [x] [Review][Defer] Los dos botones "Guardar" (Datos Fiscales y Parámetros de Venta) comparten el mismo texto/`contentDescription` — ambigüedad menor de accesibilidad para TalkBack — deferred, pre-existing

## Dev Notes

### Por qué esta historia no toca el backend

Investigación exhaustiva confirmó que `SyncService.cs` (`PushSettingsAsync`/`PullSettingsAsync`, Historia 4.1/4.4) ya acepta cualquier par `key`/`value` arbitrario — `ValidateSetting` solo exige que `key` no esté vacía. Las 7 keys que esta historia introduce o usa (`negocio_nombre`, `negocio_rfc`, `negocio_direccion`, `negocio_telefono`, `max_parcialidades`, `serie_folio`, `dias_anticipacion_recordatorio`) no requieren ningún cambio de schema, endpoint, ni seed — 3 de ellas (`max_parcialidades`, `serie_folio`, `dias_anticipacion_recordatorio`) ya están sembradas en `ApplicationBuilderExtensions.cs` y ya son leídas por `PaymentViewModel`/`ValidateFolioUseCase` respectivamente; las 4 de Datos Fiscales ya son leídas por `GenerateTicketUseCase` (con `.orEmpty()` como fallback) pero **nunca han tenido ninguna UI que las escriba** — esta historia cierra exactamente ese hueco.

### Quirk heredado de `SettingsEntity` (Historia 4.4) — por qué `updateSetting` bumpea `createdAt`

`SettingsEntity` (Android) no tiene columna `updatedAt` propia, solo `createdAt`. `SyncManager.toSyncDto()` (Historia 4.4) ya reutiliza `createdAt` para ambos campos del DTO de sync (`createdAt` y `updatedAt`), con un comentario explícito documentando esta decisión. Esto significa que **si `updateSetting` no avanza `createdAt` en cada edición**, el backend nunca detectará que el registro cambió (`existing.UpdatedAt > NormalizeUtc(item.UpdatedAt)` sería siempre falso con un timestamp viejo), y peor: la detección de conflictos de Historia 4.4 se rompería silenciosamente para settings editados desde esta pantalla. T1 depende de este detalle — no es opcional.

### Por qué "Sincronizar ahora" reutiliza el nombre de trabajo `"pull-sync"` (no uno nuevo)

`AuthRepository.login()` ya encola `OneTimeWorkRequestBuilder<PullWorker>()` bajo el nombre único `"pull-sync"` al iniciar sesión (Historia 4.2), y ese mismo comentario en el código ya anticipaba esta historia: *"el próximo PushWorker/pull manual (Historia 5.1) puede retomar la sincronización más tarde"*. `SyncStatusViewModel.isSyncing` (Historia 4.3) ya observa `"pull-sync"` como trabajo one-time (`RUNNING||ENQUEUED` cuenta como sincronizando). Si "Sincronizar ahora" encolara un nombre de trabajo distinto, habría que duplicar esa lógica de observación — reusar `"pull-sync"` con `ExistingWorkPolicy.REPLACE` es gratis y correcto: si ya hay un pull en curso (p.ej. el del login), el manual simplemente lo reemplaza/reinicia, comportamiento aceptable para un botón de "sincronizar ahora".

### Por qué se extrae `PullSyncTrigger` en vez de llamar `WorkManager` directo desde el ViewModel

Mismo patrón ya establecido por `PushSyncTrigger`/`TriggerPushSyncUseCase` (Historia 4.3, usado por `OrderListViewModel`/`ClientListViewModel` para pull-to-refresh): una interfaz angosta (`fun interface`, SAM) permite que `SettingsViewModelTest` sea 100% JVM puro sin necesitar un `WorkManager` real — un fake de una línea (`var invocations = 0; override fun invoke() { invocations++ }`) basta.

### Por qué Datos Fiscales no tiene validación de contenido

Ni el AC de `epics.md` ni el precedente de `ClientEntity.rfc` (sin validación de formato en toda la historia del proyecto) exigen validar RFC/teléfono/dirección. `GenerateTicketUseCase` ya trata estos 4 valores con `.orEmpty()` — tolera strings vacíos sin romper la generación del ticket. Introducir validación de formato aquí sería alcance no pedido.

### Por qué "Guardar" es por-sección, no un único botón global

La granularidad de los ACs de `epics.md` es explícitamente por-sección: cada AC describe "el proveedor edita X / cuando guarda / entonces Y" para Datos Fiscales y para Parámetros de Venta por separado, con reglas de validación independientes (Parámetros de Venta puede fallar validación sin afectar Datos Fiscales). Dos formularios independientes, cada uno con su propio botón "Guardar" y su propio estado `isSaving`, refleja esto directamente sin necesitar coordinar validación cruzada entre secciones no relacionadas.

### Convención de Fake reutilizada

`FakeSettingsDao` (`android/app/src/test/java/com/sumitrack/android/ui/screens/orders/FakeSettingsDao.kt`) ya existe y ya implementa las 8 funciones de `SettingsDao` (incluyendo `getConflicted`/`markConflict` de Historia 4.4) — no requiere ningún método nuevo para esta historia, ya que `updateSetting`/`getAllValues` (T1/T2) se construyen sobre `upsertAll`/`getAll`, ambos ya soportados.

### Testing

- Backend: ninguno (esta historia no toca backend).
- Android: `SettingsRepositoryTest.kt` (nuevo), `SettingsViewModelTest.kt` (nuevo) — ambos JVM puro con `FakeSettingsDao` + fake `PullSyncTrigger`. No hay suite de Compose/UI tests en este proyecto (ver historias 3.x) — la verificación de `SettingsScreen.kt` es manual (T16).

### References

- `_bmad-output/planning-artifacts/epics.md` — Historia 5.1 (líneas 834-869), FR-27 a FR-31, FR-21/22/23 (Epic 5 completo)
- `_bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/EXPERIENCE.md` — S-14 (línea 100-101)
- `_bmad-output/planning-artifacts/architecture/architecture.md` — línea 231 (WorkManager de notificaciones lee `dias_anticipacion_recordatorio`, confirma que es responsabilidad de 5.2)
- `_bmad-output/implementation-artifacts/4-4-deteccion-y-resolucion-de-conflictos.md` — Dev Notes sobre el quirk de `SettingsEntity`/`createdAt` reutilizado, y la fila mínima "Sincronización" que esta historia expande

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- **Desviación no prevista por la historia**: para T14 (tests de `SettingsViewModelTest`), `SettingsViewModel` no podía construirse en un test JVM puro porque dependía directamente de `SessionManager` (clase concreta que envuelve un `Context`/DataStore real) para `onLogoutConfirm()`. No existía ningún test previo de `SettingsViewModel` precisamente por este motivo. Solución: se extrajo `SessionClearer` (interfaz angosta con `suspend fun clearToken()`), implementada por `SessionManager` — mismo patrón exacto ya usado por `FolioBaselineStore` (comentario idéntico en el código explica el motivo). `SettingsViewModel` ahora depende de `SessionClearer`, no de `SessionManager`. Cambios: nuevo archivo `SessionClearer.kt`, `SessionManager` implementa la interfaz adicional, `SessionModule.kt` agrega el binding `provideSessionClearer`.
- `dotnet`/backend: sin cambios, sin ejecución de tests de backend (esta historia es 100% Android, confirmado en Dev Notes de la historia).
- Comandos ejecutados (implementación): `./gradlew compileDebugKotlin compileDebugUnitTestKotlin` (dos veces, tras T1-T3 y tras T15), `./gradlew testDebugUnitTest --tests SettingsViewModelTest --tests SettingsRepositoryTest`, `./gradlew testDebugUnitTest` (suite completa, 352 tests, 0 fallos), `./gradlew assembleDebug` (build completo exitoso).
- Ronda de revisión (code-review, 2026-09-13): 3 capas en paralelo (Blind Hunter ejecutado inline, Edge Case Hunter + Acceptance Auditor como subagentes) sobre el diff completo. El hallazgo más serio fue del Acceptance Auditor: violación real de AC-6 (`isParamsSaveEnabled` no consideraba los errores de validación). Edge Case Hunter aportó 8 hallazgos, mayormente sobre manejo de excepciones y condiciones de carrera en `SettingsViewModel` (7 de los 8 patches aplicados vienen de esa capa). 8 patches aplicados, 7 diferidos a `deferred-work.md`, 3 descartados como ruido. Comandos: `./gradlew compileDebugKotlin compileDebugUnitTestKotlin`, `./gradlew testDebugUnitTest --tests SettingsViewModelTest --tests SettingsRepositoryTest`, `./gradlew testDebugUnitTest assembleDebug` (suite completa, 358 tests, 0 fallos; build exitoso).

### Completion Notes List

- T1-T3: `SettingsRepository.updateSetting`/`getAllValues` agregados sobre `upsertAll`/`getAll` ya existentes en `SettingsDao` — sin cambios de DAO ni de schema. 4 tests nuevos en `SettingsRepositoryTest.kt`.
- T4-T6: `PullSyncTrigger`/`TriggerPullSyncUseCase` extraídos espejando `PushSyncTrigger`/`TriggerPushSyncUseCase` (Historia 4.3), reutilizando el nombre de trabajo `"pull-sync"` que `AuthRepository` ya usa desde el login (Historia 4.2) — sin tocar `AuthRepository.kt` (T6, decisión explícita de no-refactor).
- T7-T14: `SettingsViewModel` reescrito con dos formularios independientes (Datos Fiscales, Parámetros de Venta), cada uno con su propio `isSaving`/botón "Guardar", validación de rango en `onSaveParamsClick` (todo-o-nada, ningún campo se guarda parcialmente si algo falla). 15 tests nuevos en `SettingsViewModelTest.kt` (implementación) cubriendo carga inicial, guardado exitoso de ambas secciones, los 6 casos de validación fuera de rango especificados en T14, `onSyncNowClick`, `onLogoutConfirm`, doble-tap en ambos guardados, y manejo de excepción en ambos guardados.
- T15-T16: `SettingsScreen.kt` reescrito en las 5 secciones exactas del AC-1, envuelto en `verticalScroll`; las 3 funcionalidades preexistentes (catálogo, log de sincronización, cerrar sesión) se movieron sin cambios de comportamiento — verificado por inspección de código (no hay suite de Compose/UI tests en el proyecto, ver Dev Notes de Historias 3.x); pendiente verificación visual en dispositivo/emulador real (gap heredado, ver `deferred-work.md`).
- Ronda de revisión: `SettingsRepository.updateSettings` (batch atómico) agregado, `SettingsUiState.isLoading` agregado con gate en `SettingsScreen`, `isParamsSaveEnabled` corregido para AC-6, `fiscalError`/`paramsError` se limpian en los handlers `onXxxChange` y en la rama de validación fallida, `init`/`onSyncNowClick` protegidos con `runCatching`. 1 test nuevo en `SettingsRepositoryTest.kt` (`updateSettings`), 1 test nuevo en `SettingsViewModelTest.kt` (AC-6) — total final: 5 y 16 tests respectivamente.
- Verificación final: `./gradlew testDebugUnitTest` → 358 tests, 0 fallos, 0 errores. `./gradlew assembleDebug` → build exitoso.

### File List

**Nuevos:**
- `android/app/src/main/java/com/sumitrack/android/sync/TriggerPullSyncUseCase.kt`
- `android/app/src/main/java/com/sumitrack/android/data/repositories/SessionClearer.kt`
- `android/app/src/test/java/com/sumitrack/android/data/repositories/SettingsRepositoryTest.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/settings/SettingsViewModelTest.kt`

**Modificados:**
- `android/app/src/main/java/com/sumitrack/android/data/repositories/SettingsRepository.kt` (`updateSetting`, `getAllValues`)
- `android/app/src/main/java/com/sumitrack/android/data/repositories/SessionManager.kt` (implementa `SessionClearer`)
- `android/app/src/main/java/com/sumitrack/android/di/SyncModule.kt` (binding de `PullSyncTrigger`)
- `android/app/src/main/java/com/sumitrack/android/di/SessionModule.kt` (binding de `SessionClearer`)
- `android/app/src/main/java/com/sumitrack/android/ui/screens/settings/SettingsViewModel.kt` (reescritura completa)
- `android/app/src/main/java/com/sumitrack/android/ui/screens/settings/SettingsScreen.kt` (reescritura completa, 5 secciones)

## Change Log

| Fecha | Cambio |
|-------|--------|
| 2026-09-12 | Historia creada (`bmad-create-story`), status: ready-for-dev |
| 2026-09-13 | Implementación completa (T1-T16), status: review |
| 2026-09-13 | Code review completo — 8 patches aplicados, 7 diferidos, 3 descartados. Status: done |

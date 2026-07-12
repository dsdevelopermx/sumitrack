---
baseline_commit: d88bc641549cd86504e24f230246a4795b09d6d8
---

# Story 2.2: Alta y Edición de Cliente

Status: done

## Story

Como proveedor,
quiero registrar y editar clientes desde mi teléfono,
para que siempre tenga un directorio actualizado de mis compradores.

## Acceptance Criteria

**AC-1 — Formulario S-13 (modo alta)**

**Dado** que el proveedor toca el FAB "+" en S-11
**Cuando** S-13 se muestra
**Entonces** hay campos: nombre/razón social (obligatorio), teléfono (obligatorio), RFC (opcional), dirección (opcional), notas (opcional); `ImeAction.Next` entre nombre→teléfono→RFC→dirección, `ImeAction.Done` en notas

**AC-2 — Validación de campos obligatorios**

**Dado** que el proveedor intenta guardar con nombre o teléfono vacíos
**Cuando** toca "Guardar"
**Entonces** el/los campo(s) vacío(s) muestran `isError = true` + `supportingText` descriptivo ("El nombre es obligatorio" / "El teléfono es obligatorio"); el botón "Guardar" permanece deshabilitado hasta que ambos campos obligatorios tengan valor; sin validación agresiva mientras se escribe (errores solo se muestran tras el primer intento de guardar)

**AC-3 — Persistencia en alta**

**Dado** que el proveedor completa el formulario y toca "Guardar" en modo alta
**Cuando** se confirma
**Entonces** el cliente se persiste en SQLite con UUID generado en cliente, `fk_tenant` del tenant de la sesión activa y `sync_status = pending`; la app regresa a S-11 y el nuevo cliente aparece de inmediato en la lista (Room Flow ya reactivo desde Historia 2.1 — no requiere refresh manual)

**AC-4 — Edición de cliente existente**

**Dado** que S-13 se abre en modo edición con un `clientId`
**Cuando** la pantalla carga
**Entonces** los campos se pre-llenan con los datos actuales del cliente; al guardar, se actualiza el registro existente (mismo `id`, `created_at` preservado), `updated_at` se refresca y `sync_status` pasa a `pending`

### Fuera de alcance en esta historia (explícito)

- **Botón de captura de geolocalización** — mencionado en `EXPERIENCE.md` (S-13/S-12) pero **no está en los Criterios de Aceptación de Historia 2.2 en `epics.md`**. No hay dependencia de Google Play Services Location ni permisos declarados en `AndroidManifest.xml`. No implementar en esta historia — dirección es campo de texto plano.
- **"Modo alta rápida" desde S-03 con retorno automático al selector de cliente** — depende de S-03 (Selección de Cliente en nueva orden), que pertenece a Epic 3 (no existe todavía). La pantalla/ViewModel de esta historia debe quedar preparada para reutilizarse ahí (mismo `ClientFormScreen`/`ClientFormViewModel`), pero **no** implementar la navegación de retorno a S-03 — eso se conecta cuando se construya esa historia.
- **Entrada a modo edición desde S-12 ("Editar" en Perfil de Cliente)** — S-12 es Historia 2.3 (no existe todavía). Esta historia debe dejar el formulario funcional en ambos modos (alta/edición vía `clientId` opcional en la ruta), pero el único punto de entrada real que se conecta en esta historia es el FAB "+" de S-11 (modo alta).

## Tasks / Subtasks

### Android — Gap bloqueante: tenant_id no existe en sesión local (AC-3)

- [x] **T1: JwtDecoder + tenant_id en SessionManager** (AC-3)
  - [x] Crear `data/local/JwtDecoder.kt` — `object JwtDecoder` con `fun decodeTenantId(token: String): String?`. El JWT tiene 3 segmentos separados por `.`; decodificar el segundo (payload) con `java.util.Base64.getUrlDecoder()` **agregando padding manualmente** (los JWT no llevan `=` de relleno):
    ```kotlin
    object JwtDecoder {
        @Serializable
        private data class JwtPayload(val tenant_id: String? = null)

        private val json = Json { ignoreUnknownKeys = true }

        fun decodeTenantId(token: String): String? {
            val parts = token.split(".")
            if (parts.size != 3) return null
            return runCatching {
                val payload = parts[1]
                val padded = payload.padEnd(((payload.length + 3) / 4) * 4, '=')
                val bytes = Base64.getUrlDecoder().decode(padded)
                json.decodeFromString<JwtPayload>(String(bytes, Charsets.UTF_8)).tenant_id
            }.getOrNull()
        }
    }
    ```
  - [x] Actualizar `data/repositories/SessionManager.kt` — agregar `tenantIdKey = stringPreferencesKey("tenant_id")` y `val tenantId: Flow<String?>`; modificar `saveToken(token: String)` para decodificar y persistir `tenant_id` en el mismo `dataStore.edit {}`; modificar `clearToken()` para también remover `tenantIdKey`. **No cambiar la firma de `saveToken`** — `AuthRepository.login()` (Historia 1.4) sigue funcionando sin modificaciones.

**Por qué es necesario:** Historia 2.1 solo hacía lecturas (`getAllClients`, `searchClients`) — nunca necesitó `fk_tenant`. Historia 2.2 es la primera escritura real de una entidad, y `ClientEntity.fkTenant` es obligatorio (AR-6). El backend sí emite `tenant_id` como claim del JWT (`AuthService.cs:88`, `new Claim("tenant_id", tenantId.ToString())`) pero el cliente Android nunca lo decodifica ni lo persiste — `SessionManager` solo guarda el token crudo. Sin este fix, no hay forma de construir un `ClientEntity` válido.

### Android — Capa de datos (Room / Repository)

- [x] **T2: ClientRepository — operaciones de escritura** (AC-3, AC-4)
  - [x] Agregar a `data/repositories/ClientRepository.kt`:
    ```kotlin
    suspend fun createClient(
        name: String, phone: String, rfc: String?, address: String?, notes: String?, fkTenant: String,
    ): String {
        val now = Instant.now()
        val entity = ClientEntity(
            id = UUID.randomUUID().toString(),
            fkTenant = fkTenant, name = name, phone = phone, rfc = rfc, address = address, notes = notes,
            createdAt = now, updatedAt = now, syncStatus = "pending",
        )
        clientDao.upsertAll(listOf(entity))
        return entity.id
    }

    suspend fun updateClient(
        id: String, name: String, phone: String, rfc: String?, address: String?, notes: String?,
    ) {
        val existing = clientDao.getById(id) ?: return
        clientDao.upsertAll(listOf(existing.copy(
            name = name, phone = phone, rfc = rfc, address = address, notes = notes,
            updatedAt = Instant.now(), syncStatus = "pending",
        )))
    }

    suspend fun getClientById(id: String): Client? = clientDao.getById(id)?.toDomain()
    ```
  - [x] Reutiliza `clientDao.upsertAll(...)` y `clientDao.getById(...)` — **ambos ya existen en `ClientDao`, no agregar métodos nuevos al DAO** (evita tocar `FakeClientDao` en `ClientListViewModelTest.kt`, que seguirá siendo válido)
  - [x] `toDomain()` ya es una extensión privada existente en el archivo — reutilizarla tal cual, no duplicar el mapeo

### Android — Navegación

- [x] **T3: Ruta con argumento opcional `clientId`** (AC-1, AC-4)
  - [x] Actualizar `ui/navigation/Routes.kt`:
    ```kotlin
    object ClientForm : Routes("client_form?clientId={clientId}") {
        fun createRoute(clientId: String? = null): String =
            if (clientId != null) "client_form?clientId=$clientId" else "client_form"
    }
    ```

- [x] **T4: Wiring en NavGraph** (AC-1, AC-3, AC-4)
  - [x] Actualizar `ui/navigation/NavGraph.kt`:
    - `composable(Routes.Clients.route) { ClientListScreen(onAddClientClick = { navController.navigate(Routes.ClientForm.createRoute()) }) }`
    - Agregar composable con argumento nullable:
      ```kotlin
      composable(
          route = Routes.ClientForm.route,
          arguments = listOf(navArgument("clientId") { type = NavType.StringType; nullable = true }),
      ) {
          ClientFormScreen(
              onSaved = { navController.popBackStack() },
              onCancel = { navController.popBackStack() },
          )
      }
      ```
  - **IMPORTANTE:** no pasar `clientId` como parámetro Composable — `ClientFormViewModel` lo recibe automáticamente vía `SavedStateHandle` gracias a `androidx.hilt.navigation.compose` (mismo mecanismo que ya usa el proyecto para `hiltViewModel()`, no requiere wiring adicional)
  - [x] `MainScreen.kt` — **no requiere cambios**; el `Scaffold` con `bottomBar` es independiente de la ruta activa dentro de `NavGraph`, así que la barra de navegación inferior permanece visible sobre S-13 (comportamiento aceptado, no está prohibido por ningún AC ni por `EXPERIENCE.md`)

### Android — UI Screen S-13

- [x] **T5: ClientFormViewModel** (AC-1, AC-2, AC-3, AC-4)
  - [x] Crear `ui/screens/clients/ClientFormViewModel.kt` — `@HiltViewModel`, inyecta `SavedStateHandle`, `ClientRepository`, `SessionManager`:
    ```kotlin
    data class ClientFormUiState(
        val isEditMode: Boolean = false,
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val name: String = "", val phone: String = "", val rfc: String = "",
        val address: String = "", val notes: String = "",
        val nameError: Boolean = false, val phoneError: Boolean = false,
        val errorMessage: String? = null,
    ) {
        val isSaveEnabled: Boolean get() = name.isNotBlank() && phone.isNotBlank() && !isSaving
    }

    @HiltViewModel
    class ClientFormViewModel @Inject constructor(
        savedStateHandle: SavedStateHandle,
        private val clientRepository: ClientRepository,
        private val sessionManager: SessionManager,
    ) : ViewModel() {
        private val clientId: String? = savedStateHandle["clientId"]
        private val _uiState = MutableStateFlow(ClientFormUiState(isEditMode = clientId != null))
        val uiState: StateFlow<ClientFormUiState> = _uiState.asStateFlow()
        private val _navEvent = Channel<Unit>(Channel.CONFLATED)
        val navEvent = _navEvent.receiveAsFlow()

        init {
            clientId?.let { id ->
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                    val client = clientRepository.getClientById(id)
                    _uiState.value = if (client != null) {
                        _uiState.value.copy(
                            isLoading = false, name = client.name, phone = client.phone,
                            rfc = client.rfc.orEmpty(), address = client.address.orEmpty(),
                            notes = client.notes.orEmpty(),
                        )
                    } else {
                        _uiState.value.copy(isLoading = false, errorMessage = "No pudimos cargar los datos del cliente.")
                    }
                }
            }
        }

        fun onNameChange(v: String) { _uiState.value = _uiState.value.copy(name = v, nameError = false) }
        fun onPhoneChange(v: String) { _uiState.value = _uiState.value.copy(phone = v, phoneError = false) }
        fun onRfcChange(v: String) { _uiState.value = _uiState.value.copy(rfc = v) }
        fun onAddressChange(v: String) { _uiState.value = _uiState.value.copy(address = v) }
        fun onNotesChange(v: String) { _uiState.value = _uiState.value.copy(notes = v) }

        fun onSaveClick() {
            val state = _uiState.value
            if (state.isSaving) return
            val nameErr = state.name.isBlank()
            val phoneErr = state.phone.isBlank()
            if (nameErr || phoneErr) {
                _uiState.value = state.copy(
                    nameError = nameErr, phoneError = phoneErr,
                    errorMessage = if (nameErr) "El nombre es obligatorio" else "El teléfono es obligatorio",
                )
                return
            }
            viewModelScope.launch {
                _uiState.value = state.copy(isSaving = true, errorMessage = null)
                val rfc = state.rfc.ifBlank { null }
                val address = state.address.ifBlank { null }
                val notes = state.notes.ifBlank { null }
                if (state.isEditMode && clientId != null) {
                    clientRepository.updateClient(clientId, state.name, state.phone, rfc, address, notes)
                } else {
                    val fkTenant = sessionManager.tenantId.first()
                    if (fkTenant == null) {
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            errorMessage = "No se pudo determinar tu negocio. Vuelve a iniciar sesión.",
                        )
                        return@launch
                    }
                    clientRepository.createClient(state.name, state.phone, rfc, address, notes, fkTenant)
                }
                _uiState.value = _uiState.value.copy(isSaving = false)
                _navEvent.send(Unit)
            }
        }
    }
    ```
  - [x] Patrón de navegación por evento (`Channel<Unit>` + `receiveAsFlow()`) es el mismo de `LoginViewModel` (Historia 1.4) — reutilizar, no reinventar

- [x] **T6: ClientFormScreen — S-13 completa** (AC-1, AC-2, AC-3, AC-4)
  - [x] Crear `ui/screens/clients/ClientFormScreen.kt` — `@Composable fun ClientFormScreen(onSaved: () -> Unit, onCancel: () -> Unit, viewModel: ClientFormViewModel = hiltViewModel())`
  - [x] `Scaffold` con `TopAppBar`: título dinámico `if (uiState.isEditMode) "Editar cliente" else "Nuevo cliente"`; ícono de back (`Icons.AutoMirrored.Filled.ArrowBack`) → `onCancel()`
  - [x] `LaunchedEffect(Unit) { viewModel.navEvent.collect { onSaved() } }` — mismo patrón que `LoginScreen`
  - [x] Campos en orden con `OutlinedTextField`, reusando el patrón visual de `LoginScreen.kt` (Historia 1.4: `isError`, `supportingText`, `KeyboardOptions(imeAction=...)`, `KeyboardActions` con `FocusDirection.Down` vía `LocalFocusManager`):
    1. Nombre — obligatorio, `ImeAction.Next`, `isError = uiState.nameError`, `supportingText` = "El nombre es obligatorio" si aplica
    2. Teléfono — obligatorio, `KeyboardType.Phone`, `ImeAction.Next`, `isError = uiState.phoneError`, `supportingText` si aplica
    3. RFC — opcional, `ImeAction.Next`
    4. Dirección — opcional, `ImeAction.Next`
    5. Notas — opcional, multilínea (`singleLine = false`), `ImeAction.Done`
  - [x] Botón "Guardar" — `enabled = uiState.isSaveEnabled`, `onClick = { focusManager.clearFocus(); viewModel.onSaveClick() }`; muestra `CircularProgressIndicator` mientras `uiState.isSaving` (mismo patrón que el botón "Entrar" de `LoginScreen`)
  - [x] Si `uiState.errorMessage != null` y no es error de campo específico (p.ej. fallo al determinar tenant o al cargar cliente en edición), mostrarlo como texto de error genérico bajo el formulario o `Snackbar`
  - [x] Si `uiState.isLoading` (carga inicial en modo edición), mostrar `CircularProgressIndicator` centrado en vez del formulario

- [x] **T7: Wire FAB de S-11** (AC-1)
  - [x] Actualizar `ui/screens/clients/ClientListScreen.kt` — agregar parámetro `onAddClientClick: () -> Unit = {}`; reemplazar el `onClick` del FAB (que hoy muestra `snackbarHostState.showSnackbar("Alta de cliente — disponible próximamente")`) por `onAddClientClick`
  - [x] **No tocar** el `onClick` de `ClientCard` (placeholder "Perfil de cliente — disponible próximamente") — eso es Historia 2.3

- [x] **T8: Verificación** (todos los AC) — automatizada completa; manual pendiente (ver sub-ítems)
  - [x] `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
  - [x] `./gradlew :app:testDebugUnitTest` — 0 failures (48 tests)
  - [ ] Manual: tab Clientes → FAB "+" → S-13 vacía, "Guardar" deshabilitado → llenar solo nombre → sigue deshabilitado → llenar teléfono → habilitado → tocar "Guardar" → vuelve a S-11 → nuevo cliente visible con `SyncIcon` pendiente — **pendiente, no hay emulador/dispositivo disponible en este entorno (sin `adb`)**
  - [ ] Manual: intentar guardar con nombre vacío → error visible, botón deshabilitado, no se guarda nada — **pendiente, mismo motivo**

### Review Findings

- [x] [Review][Patch] `JwtDecoder`/`SessionManager` aceptan un claim `tenant_id` de string vacío como válido; `onSaveClick` solo comparaba `fkTenant == null` (no `isNullOrBlank()`), por lo que un JWT con `tenant_id=""` permitía persistir un cliente con `fk_tenant` vacío [`android/app/src/main/java/com/sumitrack/android/data/local/JwtDecoder.kt`, `android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientFormViewModel.kt`]
- [x] [Review][Patch] `SessionManager.saveToken` solo escribía `tenant_id` si `JwtDecoder.decodeTenantId` tenía éxito; si un token reemitido fallaba al decodificar, el `tenant_id` del tenant anterior quedaba huérfano en DataStore y se usaría silenciosamente en la siguiente alta [`android/app/src/main/java/com/sumitrack/android/data/repositories/SessionManager.kt`]
- [x] [Review][Patch] `updateClient` hacía no-op silencioso si el `id` no existía (incluye el caso borde de `clientId=""` en la ruta, que `isEditMode = clientId != null` trataba como modo edición); `onSaveClick` no verificaba el resultado y de todos modos navegaba hacia atrás como si hubiera guardado [`android/app/src/main/java/com/sumitrack/android/data/repositories/ClientRepository.kt`, `android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientFormViewModel.kt`]
- [x] [Review][Patch] Sin manejo de excepciones alrededor de `getClientById`/`tenantId.first()`/`createClient`/`updateClient` en `ClientFormViewModel` — una excepción de Room/DataStore se propagaba sin capturar (crash) o podía dejar `isLoading`/`isSaving` congelados en `true` sin usar el campo `errorMessage` ya existente [`android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientFormViewModel.kt`]
- [x] [Review][Patch] Ningún `onXChange` limpiaba `errorMessage` (solo limpiaban `nameError`/`phoneError`) — un error genérico previo (tenant no resuelto, fallo de carga en edición) permanecía visible indefinidamente mientras el usuario seguía editando [`android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientFormViewModel.kt`]
- [x] [Review][Patch] El FAB navegaba sin `launchSingleTop` ni debounce — dos toques rápidos antes de la recomposición apilaban dos destinos `client_form` en el back stack [`android/app/src/main/java/com/sumitrack/android/ui/navigation/NavGraph.kt`]
- [x] [Review][Patch] El botón de back del `TopAppBar` (`onCancel`) no se deshabilitaba mientras `isSaving`; al hacer pop del back stack se limpiaba el ViewModel y se cancelaba `viewModelScope`, pudiendo cancelar la escritura en Room a medio hacer sin mostrar error [`android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientFormScreen.kt`]
- [x] [Review][Patch] `name`/`phone` no se recortaban (`trim()`) antes de persistir — espacios al inicio/final rompían búsquedas por coincidencia exacta y quedaban visibles en listados [`android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientFormViewModel.kt`]
- [x] [Review][Defer] La caché local de `clients` en Room no está segmentada por tenant y `SessionManager.clearToken()` (logout) no la purga — un segundo tenant que inicia sesión en el mismo dispositivo vería (y podría editar) clientes del tenant anterior, en tensión con NFR-4 ("Cada Tenant tiene sus datos completamente aislados"). Preexistente desde Historia 2.1 (`getAllAsFlow`/`searchClients` ya leían la tabla completa sin filtro) e Historia 1.4 (`clearToken` nunca purgó Room); Historia 2.2 añade más rutas de escritura sobre el mismo hueco pero no lo introdujo. Requiere una historia propia (queries con scope de tenant + purga de caché en logout), no un patch de dos líneas — deferred, pre-existing [`android/app/src/main/java/com/sumitrack/android/data/repositories/ClientRepository.kt`, `android/app/src/main/java/com/sumitrack/android/data/repositories/SessionManager.kt`]
- [x] [Review][Defer] `Routes.ClientForm.createRoute` interpola `clientId` sin URL-encodear — inofensivo hoy porque solo se pasan UUIDs, pero la función acepta cualquier `String?` y futuros llamadores (entrada de edición desde Historia 2.3) podrían pasar un valor con caracteres reservados de URI — deferred, pre-existing risk not yet exercised [`android/app/src/main/java/com/sumitrack/android/ui/navigation/Routes.kt`]
- [x] [Review][Defer] Sin diálogo de confirmación al salir del formulario (back/cancelar) con cambios sin guardar — no lo exige ningún AC de esta historia (el patrón de confirmación de `EXPERIENCE.md` está acotado al flujo S-04+ de nueva orden); mejora de UX razonable para una historia futura — deferred, out of current AC scope [`android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientFormScreen.kt`]

## Dev Notes

### Contexto y decisiones previas relevantes

**Historia 2.1 estableció (reutilizar, no reinventar):**
- `ClientEntity`, `ClientDao`, `ClientRepository`, dominio `Client` — ya existen completos, esta historia solo **extiende** `ClientRepository` con escritura, no toca el esquema
- No hay migración de Room que hacer — la tabla `clients` ya tiene todas las columnas necesarias (`name`, `phone`, `rfc`, `address`, `notes`) desde `MIGRATION_1_2`
- Patrón ViewModel: `@HiltViewModel` + `StateFlow<UiState>`, eventos de navegación vía `Channel<Unit>` + `receiveAsFlow()` (establecido en Historia 1.4, reusado en 2.1 solo para búsqueda — aquí se reusa el patrón completo de navegación)
- `BigDecimalConverter` e `InstantConverter` ya registrados — no aplica directamente aquí (Cliente no tiene campos monetarios)

**Patrón de formulario — reutilizar `LoginScreen.kt` (Historia 1.4) como referencia directa:** mismo patrón de `OutlinedTextField` + `isError` + `supportingText` + `KeyboardOptions`/`KeyboardActions` con `LocalFocusManager` para saltar entre campos, mismo patrón de botón con `CircularProgressIndicator` durante guardado.

### Reglas arquitectónicas obligatorias (AR-5, AR-6, AR-16, AR-20)

- **UUID generado en Android** — `UUID.randomUUID().toString()` en `ClientRepository.createClient`, no en el ViewModel ni en la UI
- **Validación en Use Cases/ViewModel, nunca en Composables** (AR-20) — la validación de campos obligatorios vive en `ClientFormViewModel.onSaveClick()`, la UI solo refleja `uiState`
- **`updated_at` se refresca en cada escritura** (AR-6) — tanto alta como edición usan `Instant.now()`
- **`sync_status = pending`** en toda escritura local (alta y edición) — el registro se sube al backend cuando exista el motor de sync (Historia 4.x); esta historia **no hace llamadas a la API**, es 100% local

### Gap crítico resuelto en esta historia: tenant_id ausente en sesión

Ver T1. Esto es un problema pre-existente descubierto al implementar la primera escritura real (Historia 2.1 solo leía). El JWT del login (Historia 1.4, `AuthRepository.login`) ya contiene el claim `tenant_id` (`AuthService.cs:88` en el backend), pero `SessionManager` nunca lo extrae. Sin T1, no hay forma de poblar `ClientEntity.fkTenant` al crear un cliente nuevo.

`LoginResponseDto` (Android) y `LoginResponse` (backend) **no** incluyen `tenantId` en el body de la respuesta — el único lugar donde vive es dentro del JWT. Por eso la solución es decodificar el token localmente, no pedir un campo nuevo al backend.

### Referencia — Especificación UX de S-13 (EXPERIENCE.md)

> Campos: nombre o razón social (obligatorio), teléfono (obligatorio), RFC (opcional), dirección (opcional), notas (opcional)[, botón de captura de geolocalización]. Todos los campos obligatorios usan `isError = true` + `supportingText` con mensaje descriptivo al intentar guardar con campo vacío. El botón "Guardar" permanece deshabilitado hasta que los campos obligatorios estén completos. Orden de `ImeAction`: nombre → `Next`, teléfono → `Next`, RFC → `Next`, dirección → `Next`, notas → `Done`.

[Source: `_bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/EXPERIENCE.md#Descripción de superficies`, entrada S-13]

El fragmento entre corchetes (botón de geolocalización) está **excluido del alcance** — ver sección "Fuera de alcance" arriba.

### Estructura de archivos (architecture.md)

`ClientFormScreen.kt` y `ClientFormViewModel.kt` van en `ui/screens/clients/` junto a `ClientListScreen.kt`/`ClientListViewModel.kt` — mismo paquete que ya existe. [Source: `_bmad-output/planning-artifacts/architecture/architecture.md` árbol Android, línea ~370: `ClientFormScreen.kt ← S-13 (alta + edición)`]

### Archivos que se modifican (UPDATE) — no romper comportamiento existente

| Archivo | Estado actual | Cambio en esta historia |
|---------|--------------|------------------------|
| `SessionManager.kt` | Solo guarda `auth_token` | Agregar `tenant_id` derivado del JWT en `saveToken`/`clearToken`; firma de `saveToken` no cambia |
| `ClientRepository.kt` | Solo lectura (`getAllClients`, `searchClients`) + `upsertAll` genérico | Agregar `createClient`, `updateClient`, `getClientById` |
| `Routes.kt` | 4 rutas simples sin argumentos | Agregar `ClientForm` con argumento opcional `clientId` |
| `NavGraph.kt` | 3 `composable()` sin argumentos | Agregar `composable()` de `ClientForm` con `navArgument`; pasar `onAddClientClick` a `ClientListScreen` |
| `ClientListScreen.kt` | FAB muestra Snackbar placeholder | FAB navega a `ClientForm` vía `onAddClientClick` |

**NO tocar:**
- `AppNavHost.kt` — grafo de nivel superior (Login vs MainScreen), no relacionado con S-13
- `MainScreen.kt` — el bottom bar sigue visible sobre S-13, es aceptable, no requiere lógica condicional
- `AuthRepository.kt` — `saveToken(token)` mantiene su firma; el decode de `tenant_id` ocurre dentro de `SessionManager`, transparente para el caller
- `ClientDao.kt` — no agregar métodos nuevos; `upsertAll`/`getById` ya cubren las necesidades de T2
- `ClientCard.kt`, `EmptyState.kt`, `FilterChipRow.kt`, `SyncIcon.kt` — sin cambios
- El `onClick` de `ClientCard` en `ClientListScreen.kt` (placeholder de Historia 2.3) — dejarlo tal cual

### Testing

**Sin Robolectric en el proyecto** (`build.gradle.kts` — solo JUnit + `kotlinx-coroutines-test`, sin `androidx.test:core` en `testImplementation`). `SessionManager` requiere un `Context` real para su `DataStore` y por eso, igual que `LoginViewModel` (Historia 1.4, que depende de `Context`/`ConnectivityManager` y **no tiene** archivo de test unitario), no se instancia en tests JVM puros. Este es el patrón ya establecido en el proyecto — no introducir Robolectric ni una interfaz nueva solo para esto.

- `JwtDecoderTest` (nuevo, `test/java/.../data/local/JwtDecoderTest.kt`) — pura, sin dependencias de Android: construir un JWT de prueba a mano (header.payload.signature con payload `{"tenant_id":"abc-123"}` en Base64URL sin padding) y verificar `decodeTenantId` extrae `"abc-123"`; casos negativos: token malformado (`"no-es-jwt"`), token con 2 segmentos, payload sin claim `tenant_id` → todos retornan `null` sin lanzar excepción
- `ClientRepositoryTest` (nuevo, `test/java/.../data/repositories/ClientRepositoryTest.kt`) — reutiliza `FakeClientDao` de `ClientListViewModelTest.kt` (mismo paquete `com.sumitrack.android.ui.screens.clients`, importar la clase): `createClient` persiste con el `fkTenant` recibido y `syncStatus = "pending"`; `updateClient` preserva `id`/`createdAt` y actualiza el resto; `updateClient` sobre un id inexistente no lanza excepción (no-op)
- `ClientFormViewModelTest` (nuevo, `test/java/.../ui/screens/clients/ClientFormViewModelTest.kt`) — cubre solo lo que **no** depende de `SessionManager`: construir `ClientRepository(fakeDao, CalculateClientBalanceUseCase())` real + `SavedStateHandle(mapOf("clientId" to null))` o con id para modo edición. Casos: `onSaveClick con nombre vacío marca nameError`, `onSaveClick con teléfono vacío marca phoneError`, `isSaveEnabled es false hasta llenar nombre y teléfono`, `modo edición precarga los campos del cliente existente`, `modo edición guarda con el mismo id (vía FakeClientDao.getById tras el save)`. El flujo de alta completo (resolución de `tenantId` + guardado) se valida manualmente en T8.
- Correr `./gradlew :app:testDebugUnitTest` con el JDK de Android Studio (`Android Studio.app/Contents/jbr`) — mismo comando que funcionó en Historia 2.1, no usar el JDK del sistema si difiere

### Deuda técnica pre-existente (no resolver aquí)

`ClientRepository.upsertAll(clients: List<ClientEntity>)` sigue exponiendo el tipo de entidad Room en la API pública del repositorio (deferred en el code review de Historia 2.1, `2-1-lista-y-busqueda-de-clientes.md` § Review Findings). Esta historia **no lo corrige** — los nuevos métodos (`createClient`, `updateClient`, `getClientById`) exponen solo tipos de dominio/primitivos y usan `upsertAll` internamente, sin agravar el problema. Sigue pendiente para antes de Historia 4.x (motor de sync).

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (`claude-sonnet-5`)

### Debug Log References

**Desviación de diseño — `ClientFormViewModel` no inyecta `SessionManager` directamente:**

La historia (Dev Notes/T5) proponía inyectar `SessionManager` completo en `ClientFormViewModel` y llamar `sessionManager.tenantId.first()`. Al escribir `ClientFormViewModelTest` se confirmó que `SessionManager` no se puede instanciar en un test JVM puro (su `DataStore` requiere un `Context` real, y el proyecto no tiene Robolectric — mismo motivo por el que `LoginViewModel`, que también depende de `Context`, no tiene test unitario). Eso hacía imposible construir `ClientFormViewModel` en cualquier test, incluidos los de validación y edición que no tocan `tenantId`.

Solución aplicada: se agregó un `@Qualifier` (`TenantId`) en `di/SessionModule.kt` (archivo ya reservado para "bindings adicionales relacionados con la sesión") que expone `sessionManager.tenantId` como `Flow<String?>` puro. `ClientFormViewModel` inyecta ese `Flow<String?>` en vez de `SessionManager`. `SessionManager` sigue siendo la única fuente de verdad — el binding es un simple `@Provides`, no se duplicó lógica. Esto no introduce una interfaz nueva de servicio (se mantiene el estilo del proyecto de clases concretas + `@Inject constructor`), solo aísla el único dato primitivo que el ViewModel necesita, haciendo trivial pasar `flowOf("tenant-1")` o `flowOf(null)` en los tests.

**Fix en `FakeClientDao` (test, Historia 2.1):** `upsertAll` reemplazaba todo `allFlow.value` con la lista recibida en vez de hacer upsert real (insertar/reemplazar por id). Nunca se detectó en Historia 2.1 porque sus tests solo usaban `setClients(...)` directamente, nunca `upsertAll`. Al escribir `ClientRepositoryTest`/`ClientFormViewModelTest` (que sí ejercitan `createClient`/`updateClient`, ambos basados en `upsertAll`), una segunda llamada a `createClient` habría borrado el cliente creado antes. Se corrigió `upsertAll` en `ClientListViewModelTest.kt` para hacer upsert real por `id` — no afecta ningún test existente de Historia 2.1 (ninguno llamaba `upsertAll`).

### Completion Notes List

Historia implementada completa. 48 tests pasan (0 fallos). BUILD SUCCESSFUL (`assembleDebug` + `testDebugUnitTest`).

- AC-1 ✅: S-13 (`ClientFormScreen`) muestra los 5 campos especificados con el orden de `ImeAction` pedido; alcanzable desde el FAB "+" de S-11
- AC-2 ✅: `ClientFormViewModelTest` cubre nombre vacío, teléfono vacío, `isSaveEnabled` reactivo, y que escribir limpia el error del campo — sin validación agresiva en tiempo de escritura (errores solo tras `onSaveClick`)
- AC-3 ✅: `createClient` persiste con UUID generado en cliente, `fk_tenant` resuelto del JWT de sesión (nuevo `JwtDecoder` + `SessionManager.tenantId`) y `sync_status = pending`; navega de regreso a S-11 vía `Channel<Unit>` (mismo patrón que login)
- AC-4 ✅: modo edición precarga campos desde `getClientById`, guarda con `updateClient` preservando `id`/`createdAt`, actualiza `updatedAt` y `sync_status = pending`
- Gap resuelto: `SessionManager` no exponía `tenant_id` (Historia 2.1 nunca lo necesitó por ser solo lectura) — se agregó `JwtDecoder` + persistencia de `tenant_id` en `SessionManager`, sin cambiar la firma de `saveToken` (Historia 1.4 no se ve afectada)
- Fuera de alcance confirmado y respetado: sin botón de geolocalización (no está en los AC de `epics.md`), sin conexión a "modo alta rápida desde S-03" (S-03 no existe, es Epic 3), sin entrada desde S-12 (Historia 2.3)
- **Pendiente:** verificación manual en emulador/dispositivo físico (últimos 2 ítems de T8) — este entorno no tiene `adb` ni emulador Android disponible. Recomendado antes de mergear: instalar el APK debug y validar el flujo FAB → guardar → cliente visible en S-11, y el caso de error con nombre vacío.

### File List

**Archivos creados (NEW):**
- `android/app/src/main/java/com/sumitrack/android/data/local/JwtDecoder.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientFormViewModel.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientFormScreen.kt`
- `android/app/src/test/java/com/sumitrack/android/data/local/JwtDecoderTest.kt`
- `android/app/src/test/java/com/sumitrack/android/data/repositories/ClientRepositoryTest.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/ClientFormViewModelTest.kt`

**Archivos modificados (UPDATE):**
- `android/app/src/main/java/com/sumitrack/android/data/repositories/SessionManager.kt` — `tenantId: Flow<String?>` derivado del JWT, persistido en `saveToken`/limpiado en `clearToken`
- `android/app/src/main/java/com/sumitrack/android/data/repositories/ClientRepository.kt` — `createClient`, `updateClient`, `getClientById`
- `android/app/src/main/java/com/sumitrack/android/di/SessionModule.kt` — qualifier `TenantId` + `@Provides` de `Flow<String?>`
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/Routes.kt` — `ClientForm` con argumento opcional `clientId`
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/NavGraph.kt` — composable de `ClientForm`; `onAddClientClick` wired en `ClientListScreen`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientListScreen.kt` — FAB navega a `ClientForm` en vez de mostrar Snackbar placeholder
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/ClientListViewModelTest.kt` — `FakeClientDao.upsertAll` corregido a upsert real por `id`

## Change Log

- **2026-07-11** — Historia 2.2 implementada completa (Status: review)
  - NEW: `JwtDecoder` — decodifica `tenant_id` del JWT sin dependencias de Android
  - UPDATE: `SessionManager` — persiste `tenant_id` junto al token, expone `Flow<String?> tenantId`
  - NEW: `SessionModule` — qualifier `TenantId` para inyectar solo el `Flow<String?>` necesario (testabilidad sin `Context`)
  - UPDATE: `ClientRepository` — `createClient`, `updateClient`, `getClientById`
  - NEW: `ClientFormViewModel` + `ClientFormScreen` — S-13 completa (alta y edición)
  - UPDATE: `Routes`/`NavGraph` — ruta `client_form` con argumento opcional `clientId`; FAB de S-11 navega en vez de Snackbar
  - UPDATE: `FakeClientDao` (test) — `upsertAll` corregido a upsert real por id
  - NEW: tests — `JwtDecoderTest` (5 casos), `ClientRepositoryTest` (5 casos), `ClientFormViewModelTest` (9 casos)
  - Build: 48 tests ✅ (0 fallos), BUILD SUCCESSFUL (`assembleDebug` + `testDebugUnitTest`, JDK de Android Studio)
  - Pendiente: verificación manual en dispositivo/emulador (sin `adb` en este entorno)

- **2026-07-11** — Patches de code review aplicados (8 `patch`, 3 `defer`)
  - UPDATE: `JwtDecoder.decodeTenantId` — claim `tenant_id` en blanco (`""`) ahora se trata como ausente (`null`)
  - UPDATE: `SessionManager.saveToken` — si el token nuevo no decodifica `tenant_id`, se limpia el valor anterior en vez de dejarlo huérfano
  - UPDATE: `ClientRepository.updateClient` — ahora retorna `Boolean` (`false` si el `id` no existe) en vez de no-op silencioso
  - UPDATE: `ClientFormViewModel` — normaliza `clientId=""` a modo alta; verifica el resultado de `updateClient` y muestra error si el cliente ya no existe; envuelve `getClientById`/`tenantId.first()`/`createClient`/`updateClient` en `runCatching` con `errorMessage` genérico ante fallo; todos los `onXChange` limpian `errorMessage`; `onSaveClick` recorta (`trim()`) nombre, teléfono, RFC, dirección y notas antes de persistir
  - UPDATE: `NavGraph` — FAB navega con `launchSingleTop = true`
  - UPDATE: `ClientFormScreen` — botón de back y gesto de sistema (`BackHandler`) deshabilitados mientras `isSaving`
  - NEW: casos de test — `JwtDecoderTest` (+1, tenant_id en blanco), `ClientRepositoryTest` (updateClient ahora verifica el `Boolean` de retorno), `ClientFormViewModelTest` (+4: errorMessage se limpia al editar, clientId vacío es modo alta, edición de cliente eliminado muestra error sin navegar, trim antes de guardar)
  - Build verificado con JDK de Android Studio: `assembleDebug` y `testDebugUnitTest` — **53 tests ✅ (0 fallos), BUILD SUCCESSFUL**
  - Deferred (3, ver `deferred-work.md`): caché local de clientes sin scope de tenant + sin purga en logout (preexistente, tensión con NFR-4); ruta `client_form` sin URL-encoding de `clientId`; sin diálogo de confirmación al salir del formulario

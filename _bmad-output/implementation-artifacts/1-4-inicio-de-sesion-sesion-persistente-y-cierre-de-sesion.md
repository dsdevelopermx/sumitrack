---
baseline_commit: 2284a74a9d2a8640c285cc5b969b3fa71e75c309
---

# Story 1.4: Inicio de Sesión, Sesión Persistente y Cierre de Sesión

Status: done

## Story

Como proveedor,
quiero iniciar sesión con mis credenciales y mantenerme conectado entre aperturas de la app,
para que pueda operar desde cualquier dispositivo Android sin autenticarme en cada uso.

## Acceptance Criteria

**AC-1 — Pantalla de Login (S-01)**

**Dado** que el proveedor abre la app por primera vez (sin sesión cacheada)
**Cuando** se muestra S-01 (Login)
**Entonces** hay campos de usuario (`ImeAction.Next`) y contraseña (`ImeAction.Done`) y un botón "Entrar"

**AC-2 — Login exitoso con conexión**

**Dado** que el proveedor ingresa credenciales válidas con conexión
**Cuando** toca "Entrar"
**Entonces** la app llama `POST /api/v1/auth/login`, descarga Settings del tenant (`GET /api/v1/settings`), persiste el token JWT en DataStore y navega a S-02

**AC-3 — Credenciales incorrectas**

**Dado** que el proveedor ingresa credenciales incorrectas
**Cuando** toca "Entrar"
**Entonces** los campos muestran `isError = true` y `supportingText` "Usuario o contraseña incorrectos. Inténtalo de nuevo." (sin códigos de error al usuario)

**AC-4 — Sesión cacheada — skip login**

**Dado** que el proveedor tiene sesión cacheada (token en DataStore) y abre la app
**Cuando** la app se inicializa
**Entonces** se omite S-01 y navega directamente a S-02 sin parpadeo de Login

**AC-5 — Offline con sesión cacheada**

**Dado** que el proveedor tiene sesión cacheada y está sin conexión
**Cuando** abre la app
**Entonces** puede operar normalmente con los datos locales en SQLite (no se requiere red para apps ya autenticadas)

**AC-6 — Sin sesión y sin conexión**

**Dado** que el proveedor NO tiene sesión previa y está sin conexión
**Cuando** la app se inicializa en S-01
**Entonces** S-01 muestra el mensaje explicativo "Se requiere conexión a internet para el primer acceso." en `supportingText` visible (no como error, solo informativo)

**AC-7 — Cierre de sesión desde Config**

**Dado** que el proveedor toca "Cerrar sesión" en S-14 (sección Sesión) y confirma el `AlertDialog` destructivo
**Cuando** se ejecuta el cierre
**Entonces** el token en DataStore se borra, los datos de sesión se limpian, y la app navega a S-01 limpiando el back stack completo

## Tasks / Subtasks

### Backend

- [x] **T1: Entidad Settings + tabla en schema de tenant** (AC-2)
  - [x] Crear `Models/Entities/Setting.cs` — entidad key-value con campos: `Key` (PK), `Value` (nullable), `UpdatedAt`
  - [x] Agregar `public DbSet<Setting> Settings => Set<Setting>();` a `TenantDbContext.cs`
  - [x] Agregar configuración `OnModelCreating` para `Setting` en `TenantDbContext.cs` (tabla `settings`, columnas snake_case, naming AR-16)
  - [x] Actualizar `CreateTenantSchemaSql` en `ApplicationBuilderExtensions.cs` — agregar `CREATE TABLE IF NOT EXISTS` para `settings`
  - [x] Agregar seed de settings por defecto en `SeedDevelopmentAsync` (max_parcialidades=15, serie_folio=A, dias_anticipacion_recordatorio=3)

- [x] **T2: SettingsController minimal** (AC-2)
  - [x] Crear `Controllers/SettingsController.cs` con `[Authorize]` y `GET /api/v1/settings`
  - [x] El endpoint usa `TenantDbContext` (ya inyectado por `TenantSchemaInterceptor`) para leer todos los settings del tenant
  - [x] Respuesta directa como lista sin wrapper: `[{ "key": "...", "value": "..." }]` (AR-16: no envelope)

### Android — Nuevas dependencias

- [x] **T3: Agregar dependencias al proyecto** (todos los ACs)
  - [x] Actualizar `gradle/libs.versions.toml` — agregar versiones y aliases para: Retrofit 2.11.0, OkHttp 4.12.0, kotlinx-serialization-json 1.8.1, DataStore Preferences 1.1.2, hilt-navigation-compose 1.2.0, plugin kotlin.serialization
  - [x] Actualizar `android/build.gradle.kts` (raíz) — agregar plugin `kotlin-serialization apply false`
  - [x] Actualizar `app/build.gradle.kts` — agregar plugin `kotlin.serialization` + dependencias: Retrofit, OkHttp, kotlinx-serialization-json, retrofit2-kotlinx-serialization-converter, DataStore, hilt-navigation-compose

### Android — Capa de datos

- [x] **T4: SessionManager** (AC-4, AC-5, AC-7)
  - [x] Crear `data/repositories/SessionManager.kt` — `@Singleton` con `@ApplicationContext`; wraps `DataStore<Preferences>`; expone `token: Flow<String?>`, `isLoggedIn: Flow<Boolean>`, `suspend saveToken(token: String)`, `suspend clearToken()`
  - [x] Crear `di/SessionModule.kt` (o agregar a `DatabaseModule.kt`) — proveer `DataStore<Preferences>` vía Hilt `@Singleton`

- [x] **T5: SettingsDao y Repository** (AC-2)
  - [x] Crear `data/local/dao/SettingsDao.kt` con `@Upsert fun upsertAll(settings: List<SettingsEntity>)` y `@Query("SELECT * FROM settings") fun getAll(): Flow<List<SettingsEntity>>`
  - [x] Actualizar `data/local/SumitrackDatabase.kt` — agregar `abstract fun settingsDao(): SettingsDao` (Room versión sigue en 1)
  - [x] Actualizar `di/DatabaseModule.kt` — agregar `@Provides` para `SettingsDao`
  - [x] Crear `data/repositories/SettingsRepository.kt` — wraps `SettingsDao` + `SettingsApiService`; método `suspend downloadAndCacheSettings(token: String)`

- [x] **T6: Red — Retrofit + AuthApiService + SettingsApiService** (AC-2, AC-3)
  - [x] Crear `data/remote/dto/LoginRequestDto.kt` — `@Serializable data class` con `username` y `password`
  - [x] Crear `data/remote/dto/LoginResponseDto.kt` — `@Serializable data class` con `token` y `expiresAt`
  - [x] Crear `data/remote/dto/SettingDto.kt` — `@Serializable data class` con `key` y `value: String?`
  - [x] Crear `data/remote/api/AuthApiService.kt` — Retrofit interface con `@POST("api/v1/auth/login")`
  - [x] Crear `data/remote/api/SettingsApiService.kt` — Retrofit interface con `@GET("api/v1/settings")` + `@Header("Authorization") token: String`
  - [x] Crear `di/NetworkModule.kt` — `@Module @InstallIn(SingletonComponent::class)` proveyendo `OkHttpClient` (con `HttpLoggingInterceptor` solo en debug) y `Retrofit` con `Json { ignoreUnknownKeys = true }` converter

- [x] **T7: AuthRepository** (AC-2, AC-3)
  - [x] Crear `data/repositories/AuthRepository.kt` — `@Singleton`; método `suspend login(username: String, password: String): Result<Unit>` que: llama `AuthApiService.login()` → si éxito guarda token con `SessionManager.saveToken()` → llama `SettingsRepository.downloadAndCacheSettings(token)` → retorna `Result.success(Unit)`; si error HTTP 401 retorna `Result.failure(InvalidCredentialsException)`
  - [x] Crear `domain/exceptions/InvalidCredentialsException.kt` — exception para mapear 401 en el Repository (no en ViewModel ni Composable)
  - [x] Crear `di/RepositoryModule.kt` — proveer `AuthRepository` y `SettingsRepository` vía Hilt

### Android — Capa UI

- [x] **T8: AppViewModel + SessionState** (AC-4, AC-5, AC-7)
  - [x] Crear `ui/AppViewModel.kt` — `@HiltViewModel`; observa `SessionManager.isLoggedIn` vía `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)`; expone `sessionState: StateFlow<Boolean?>` (null=loading, true=logged in, false=logged out)

- [x] **T9: AppNavHost — navegación exterior** (AC-4, AC-7)
  - [x] Crear `ui/navigation/AppNavHost.kt` — composable raíz con `hiltViewModel<AppViewModel>()`; mientras `sessionState == null` muestra splash vacío (evita parpadeo); `startDestination` se fija al primer valor non-null; `LaunchedEffect(sessionState)` navega a Login cuando `sessionState == false` (logout reactivo)
  - [x] Actualizar `ui/navigation/Routes.kt` — agregar `object Login : Routes("login")`
  - [x] Actualizar `MainActivity.kt` — llamar `AppNavHost()` en lugar de `MainScreen()`

- [x] **T10: LoginScreen + LoginViewModel** (AC-1, AC-2, AC-3, AC-6)
  - [x] Crear `ui/screens/auth/LoginViewModel.kt` — `@HiltViewModel`; `UiState` con: `username: String`, `password: String`, `isLoading: Boolean`, `usernameError: Boolean`, `passwordError: Boolean`, `errorMessage: String?`, `isOffline: Boolean`; método `login()` que llama `AuthRepository.login()` en `viewModelScope`
  - [x] Crear `ui/screens/auth/LoginScreen.kt` — composable S-01: `TextField` usuario + `TextField` contraseña (visualTransformation para ocultar) + botón "Entrar" (deshabilitado si `isLoading`); campos con `isError = true` y `supportingText` cuando hay error; texto informativo de offline cuando `isOffline`; respeta `ImeAction.Next` en usuario e `ImeAction.Done` en contraseña; llamar `loginViewModel.login()` desde `KeyboardAction.onDone`; `@AndroidEntryPoint`-compatible vía `hiltViewModel()`

- [x] **T11: Logout en SettingsScreen** (AC-7)
  - [x] Crear (o actualizar) `ui/screens/settings/SettingsViewModel.kt` — `@HiltViewModel`; método `logout()` que llama `SessionManager.clearToken()`; logout NO navega explícitamente (la navegación es reactiva desde `AppViewModel`)
  - [x] Actualizar `ui/screens/settings/SettingsScreen.kt` — agregar sección "Sesión" con botón "Cerrar sesión" + `AlertDialog` destructivo: título "¿Cerrar sesión?", botones "Sí, salir" y "Cancelar"; al confirmar llama `settingsViewModel.logout()`

- [x] **T12: Verificación** (todos los ACs)
  - [x] `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
  - [x] `./gradlew :app:testDebugUnitTest` — 0 failures (agregar tests unitarios para `LoginViewModel` y `AuthRepository`)
  - [x] Verificar en emulador: sin sesión → muestra Login; con credenciales válidas → navega a S-02; credenciales inválidas → muestra error; cerrar sesión → regresa a Login

### Review Findings

<!-- Code review generado 2026-07-06 — claude-sonnet-4-6. 3 capas: Blind Hunter + Edge Case Hunter + Acceptance Auditor -->

#### Decision Needed

- [x] [Review][Patch] **RD-1→RP-0: Refactorizar AppNavHost a Routes.Login + Routes.Orders** (decisión: seguir patrón del spec) — Dev Notes advierten explícitamente "No crear una ruta Routes.Main separada — complica el back stack". La implementación usa `Routes.Auth` + `Routes.Main` (auth_graph/main_graph). `Routes.Login` existe pero nunca se usa (dead code). Alternativa spec: registrar `MainScreen` bajo `Routes.Orders.route` en el NavHost exterior, eliminar `Routes.Main` y `Routes.Auth`. ¿Seguir el patrón del spec o mantener el wrapper de dos niveles? [AppNavHost.kt, Routes.kt]

#### Patches — High

- [x] [Review][Patch] **RP-1: Botón dice "Iniciar sesión" — spec exige "Entrar"** (AC-1) [LoginScreen.kt:692]
- [x] [Review][Patch] **RP-2: OutlinedTextField sin `isError` ni `supportingText`** — error mostrado como `Text` suelto fuera del campo; `LoginUiState` no tiene `usernameError`/`passwordError` (AC-1, AC-3) [LoginScreen.kt:629-671]
- [x] [Review][Patch] **RP-3: Mensaje de credenciales incorrectas falta ". Inténtalo de nuevo."** (AC-3) [LoginViewModel.kt:753]
- [x] [Review][Patch] **RP-4: AC-6 sin implementar** — offline no detectado en init, texto incorrecto ("Error de conexión…" vs "Se requiere conexión a internet para el primer acceso."), styled como error (rojo) en lugar de informativo (AC-6) [LoginViewModel.kt:755, LoginScreen.kt:664-671]
- [x] [Review][Patch] **RP-5: Cleartext HTTP bloqueado en API 28+** — URL debug `http://10.0.2.2:5000/` sin `android:usesCleartextTraffic="true"` o network_security_config; crash en runtime en dispositivos físicos y emuladores Android 9+ [app/build.gradle.kts:17]
- [x] [Review][Patch] **RP-6: Split-brain si falla descarga de settings** — `saveToken()` se llama ANTES de `downloadAndCacheSettings()`; si el fetch de settings falla, el token queda guardado y en el próximo arranque la app muestra LoggedIn con tabla settings vacía [AuthRepository.kt:227-229]
- [x] [Review][Patch] **RP-7: HTTP 401 de `/settings` mostrado como "credenciales incorrectas"** — `recoverCatching` envuelve TODO el bloque incluyendo la llamada a settings; un 401 del endpoint settings (ej: timing, permiso de tenant) se re-mapea a `InvalidCredentialsException` aunque el login fue exitoso [AuthRepository.kt:230-234]
- [x] [Review][Patch] **RP-8: `isLoading` no se resetea a `false` en login exitoso** — `onSuccess { onSuccess() }` se llama con `isLoading = true`; spinner queda activo si hay latencia en la navegación [LoginViewModel.kt:750-751]
- [x] [Review][Patch] **RP-9: Doble navegación en AppNavHost al arranque** — `startDestination` ya establece la ruta correcta Y `LaunchedEffect(sessionState)` navega de nuevo al mismo destino en la primera composición; ruta duplicada en el back stack [AppNavHost.kt:495-527]
- [x] [Review][Patch] **RP-10: Room/settings no limpiados en logout** — `onLogoutConfirm()` solo llama `clearToken()`; tabla `settings` con datos del tenant anterior persiste hasta el próximo sync (AC-7: "los datos de sesión se limpian") [SettingsViewModel.kt:897-901]
- [x] [Review][Patch] **RP-11: Race condition teclado + botón → dos coroutines de login** — `ImeAction.Done` y `onClick` pueden dispararse antes de que `isLoading` cause recomposición; falta guard `if (_uiState.value.isLoading) return` al inicio de `onLoginClick` [LoginViewModel.kt:741]

#### Patches — Med

- [x] [Review][Patch] **RP-12: AlertDialog title "Cerrar sesión" → debe ser "¿Cerrar sesión?"** (AC-7) [SettingsScreen.kt:841]
- [x] [Review][Patch] **RP-13: Confirm button "Cerrar sesión" → debe ser "Sí, salir"** (AC-7) [SettingsScreen.kt:843]
- [x] [Review][Patch] **RP-14: `Setting.cs` property initializer anula `HasDefaultValueSql("NOW()")`** — EF Core siempre envía `DateTime.UtcNow` en INSERT, la columna `updated_at` nunca es gestionada por la DB [Setting.cs:7, TenantDbContext.cs:1028]
- [x] [Review][Patch] **RP-15: Token vacío `""` almacenado como sesión válida** — `isLoggedIn` emite `true` para cualquier string no-null; todas las llamadas API enviarán `"Bearer "` y recibirán 401 [SessionManager.kt:266]
- [x] [Review][Patch] **RP-16: `rememberNavController()` dentro de rama condicional** — viola reglas de hoisting de Compose (state holders deben llamarse incondicionalmente en el top-level de la función) [AppNavHost.kt:500]
- [x] [Review][Patch] **RP-17: Password logueado en claro via `Level.BODY`** — interceptor debug imprime el body completo del LoginRequestDto (username + password) en logcat [NetworkModule.kt:360]
- [x] [Review][Patch] **RP-18: Lambda de navegación capturada en coroutine del ViewModel** — `onSuccess` lambda creada en Composable es capturada por `viewModelScope.launch`; post-rotación la lambda puede referenciar un NavController obsoleto [LoginViewModel.kt:741-759, AppNavHost.kt:515-520]

#### Defer

- [x] [Review][Defer] Token JWT en DataStore sin cifrar — considerar `EncryptedSharedPreferences` en Epic 4 (Sincronización Offline) [SessionManager.kt] — deferred, pre-existing arch decision
- [x] [Review][Defer] Sin interceptor OkHttp para Authorization header — auth manual en cada servicio API; refactorizar cuando haya ≥3 servicios [NetworkModule.kt] — deferred, pre-existing
- [x] [Review][Defer] `DbSet<Setting>` + raw DDL mezclados — si se corre `dotnet ef migrations add` generará migración conflictiva; consolidar estrategia en Epic 4 [TenantDbContext.cs] — deferred, pre-existing arch
- [x] [Review][Defer] `expiresAt` del JWT nunca verificado — app no detecta token expirado en foreground; implementar en Historia 4.2 (Pull inicial y folio del servidor al hacer login) [LoginResponseDto.kt] — deferred, out of story scope
- [x] [Review][Defer] `upsertAll` sin previa `deleteAll` — keys eliminadas en servidor persisten en local DB indefinidamente [SettingsRepository.kt:295] — deferred, diseño de sync a definir en Epic 4
- [x] [Review][Defer] Slug de tenant interpolado en SQL raw sin validar — riesgo de inyección si slug contiene `"` [ApplicationBuilderExtensions.cs:1056] — deferred, pre-existing
- [x] [Review][Defer] Tabla settings ausente causa 500 no manejado en `SettingsController` [SettingsController.cs:989] — deferred, infrastructure concern
- [x] [Review][Defer] `clearToken()` sin manejo de `IOException` — usuario aparece logueado si DataStore falla [SettingsViewModel.kt:897] — deferred, defensive programming

## Dev Notes

### CRÍTICO: Cambios en arquitectura de navegación

La **mayor diferencia con Historia 1.3** es que se introduce una capa de navegación exterior. Antes: `MainActivity → MainScreen (NavigationBar + tabs)`. Ahora: `MainActivity → AppNavHost (Login o MainScreen)`.

**No renombrar `NavGraph.kt`** — sigue siendo la navegación interna de tabs. `AppNavHost.kt` es el wrapper exterior.

```
MainActivity
└── AppNavHost        ← NUEVO (navegación exterior: Login / MainScreen)
    ├── LoginScreen   ← NUEVO (ruta Routes.Login)
    └── MainScreen    ← EXISTENTE (ahora como composable dentro de AppNavHost)
        └── NavGraph  ← EXISTENTE (tabs: Orders, Clients, Settings)
```

**Trampa — `hiltViewModel()` dentro de `NavHost`:** Requiere `hilt-navigation-compose`. Sin esta dependencia, Hilt no puede inyectar ViewModels en destinations de Navigation Compose. `implementation(libs.hilt.navigation.compose)` es OBLIGATORIO.

**Trampa — `startDestination` en AppNavHost:** No se puede calcular reactivamente en la llamada a `NavHost`. Patrón recomendado: mostrar loading (splash) mientras `sessionState == null`, luego construir el `NavHost` con el `startDestination` correcto ya conocido.

```kotlin
// ui/navigation/AppNavHost.kt — patrón CORRECTO
@Composable
fun AppNavHost(viewModel: AppViewModel = hiltViewModel()) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()

    if (sessionState == null) {
        // Splash invisible — evita parpadeo. DataStore tarda <50ms.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryVariant)
        }
        return
    }

    val startDestination = if (sessionState == true) Routes.Orders.route else Routes.Login.route
    val navController = rememberNavController()

    // Logout reactivo: si el token se borra después del arranque (ej: desde Settings),
    // navegar a Login limpiando el back stack completo.
    LaunchedEffect(sessionState) {
        if (sessionState == false && navController.currentDestination?.route != Routes.Login.route) {
            navController.navigate(Routes.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController, startDestination = startDestination) {
        composable(Routes.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.Orders.route) {
                        popUpTo(Routes.Login.route) { inclusive = true }
                    }
                }
            )
        }
        // MainScreen con NavigationBar ya maneja sus propias rutas (NavGraph interno)
        composable(Routes.Orders.route) { MainScreen() }
    }
}
```

**Trampa — Routes.Orders como "Main":** `MainScreen` (con NavigationBar) se registra en la ruta `Routes.Orders.route` del NavHost exterior. Los tabs (Orders/Clients/Settings) son manejados INTERNAMENTE por `MainScreen` + `NavGraph.kt`. No crear una ruta `Routes.Main` separada — eso requeriría nested navigation de dos niveles y complica el back stack.

---

### CRÍTICO: Nuevas dependencias — versiones exactas

```toml
# gradle/libs.versions.toml — AGREGAR estas entradas

[versions]
retrofit              = "2.11.0"
okhttp                = "4.12.0"
kotlinxSerializationJson = "1.8.1"
datastore             = "1.1.2"
hiltNavigationCompose = "1.2.0"
retrofitKotlinxConverter = "1.0.0"

[libraries]
retrofit                    = { group = "com.squareup.retrofit2", name = "retrofit",                              version.ref = "retrofit" }
retrofit-kotlinx-converter  = { group = "com.jakewharton.retrofit2", name = "retrofit2-kotlinx-serialization-converter", version.ref = "retrofitKotlinxConverter" }
okhttp                      = { group = "com.squareup.okhttp3",     name = "okhttp",                             version.ref = "okhttp" }
okhttp-logging              = { group = "com.squareup.okhttp3",     name = "logging-interceptor",                version.ref = "okhttp" }
kotlinx-serialization-json  = { group = "org.jetbrains.kotlinx",   name = "kotlinx-serialization-json",         version.ref = "kotlinxSerializationJson" }
androidx-datastore-preferences = { group = "androidx.datastore",   name = "datastore-preferences",              version.ref = "datastore" }
hilt-navigation-compose     = { group = "androidx.hilt",           name = "hilt-navigation-compose",            version.ref = "hiltNavigationCompose" }

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
# version.ref = "kotlin" ← reutiliza kotlin = "2.1.21" ya existente
```

```kotlin
// android/build.gradle.kts (RAÍZ) — agregar plugin kotlin-serialization:
plugins {
    alias(libs.plugins.android.application)      apply false
    alias(libs.plugins.kotlin.android)           apply false
    alias(libs.plugins.kotlin.compose)           apply false
    alias(libs.plugins.kotlin.serialization)     apply false  // AGREGAR
    alias(libs.plugins.hilt)                     apply false
    alias(libs.plugins.ksp)                      apply false
}
```

```kotlin
// app/build.gradle.kts — agregar plugin + dependencies:
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)  // AGREGAR
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

dependencies {
    // ... lo que ya existe ...

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)            // solo en debug via BuildConfig.DEBUG
    implementation(libs.kotlinx.serialization.json)

    // Session storage
    implementation(libs.androidx.datastore.preferences)

    // Hilt + Navigation Compose (OBLIGATORIO para hiltViewModel() en NavGraph destinations)
    implementation(libs.hilt.navigation.compose)
}
```

**Trampa — `retrofit2-kotlinx-serialization-converter` vs Gson:** La arquitectura especifica `kotlinx.serialization 1.x` (AR-3). NO usar `converter-gson`. El converter de JakeWharton funciona con `@Serializable` de kotlinx.

---

### CRÍTICO: DataStore — patrón correcto

```kotlin
// data/repositories/SessionManager.kt
package com.sumitrack.android.data.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

@Singleton
class SessionManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val TOKEN_KEY = stringPreferencesKey("auth_token")

    val token: Flow<String?> = context.dataStore.data.map { prefs -> prefs[TOKEN_KEY] }

    val isLoggedIn: Flow<Boolean> = token.map { it != null }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { prefs -> prefs[TOKEN_KEY] = token }
    }

    suspend fun clearToken() {
        context.dataStore.edit { prefs -> prefs.remove(TOKEN_KEY) }
    }
}
```

**Trampa — `preferencesDataStore` es una extension property de `Context`:** Se define a nivel de TOP-LEVEL del archivo (fuera de la clase), una sola vez. Si se define dentro de la clase o en múltiples archivos con el mismo nombre `"session"`, hay conflictos.

**Trampa — NO usar `runBlocking` para leer DataStore en `MainActivity`:** DataStore es asíncrono. Usar `collectAsStateWithLifecycle()` en el composable. El patrón de `AppViewModel` con `stateIn(initialValue = null)` es el correcto para manejar el estado inicial loading.

---

### CRÍTICO: NetworkModule — configuración Retrofit

```kotlin
// di/NetworkModule.kt
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true   // robustez: el servidor puede agregar campos sin romper
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
            }
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)  // "http://10.0.2.2:5000/" en debug, Railway en prod
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService =
        retrofit.create(AuthApiService::class.java)

    @Provides
    @Singleton
    fun provideSettingsApiService(retrofit: Retrofit): SettingsApiService =
        retrofit.create(SettingsApiService::class.java)
}
```

**`BuildConfig.BASE_URL`** debe declararse en `app/build.gradle.kts`:
```kotlin
android {
    defaultConfig {
        // ...
        buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:5000/\"")
    }
    buildTypes {
        release {
            buildConfigField("String", "BASE_URL", "\"https://sumitrack-api.railway.app/\"")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true  // AGREGAR — requerido para BuildConfig en AGP 8+
    }
}
```

**Trampa — `10.0.2.2`:** Es la IP del host desde el emulador Android. `localhost` no funciona desde el emulador.

---

### CRÍTICO: Backend — agregar settings al schema de tenant

La tabla de tenant se crea vía raw SQL en `ApplicationBuilderExtensions.cs`, NO con EF Core migrations. Para agregar la tabla `settings`:

```csharp
// En ApplicationBuilderExtensions.cs — actualizar CreateTenantSchemaSql:
private const string CreateTenantSchemaSql = """
    CREATE SCHEMA IF NOT EXISTS "{schema}";

    CREATE TABLE IF NOT EXISTS "{schema}".users (
        id UUID NOT NULL,
        username CHARACTER VARYING(100) NOT NULL,
        password_hash CHARACTER VARYING(255) NOT NULL,
        tenant_id UUID NOT NULL,
        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
        CONSTRAINT pk_users PRIMARY KEY (id)
    );

    CREATE UNIQUE INDEX IF NOT EXISTS ix_users_username ON "{schema}".users(username);

    CREATE TABLE IF NOT EXISTS "{schema}".settings (
        key CHARACTER VARYING(100) NOT NULL,
        value TEXT,
        updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
        CONSTRAINT pk_settings PRIMARY KEY (key)
    );
    """;
```

Y agregar seed de defaults en `SeedDevelopmentAsync`:
```csharp
await publicCtx.Database.ExecuteSqlRawAsync(
    $"""
    INSERT INTO "{schemaName}".settings (key, value) VALUES
        ('max_parcialidades', '15'),
        ('serie_folio', 'A'),
        ('dias_anticipacion_recordatorio', '3')
    ON CONFLICT (key) DO NOTHING
    """);
```

Para schemas EXISTENTES que ya no tienen la tabla `settings`, el `CREATE TABLE IF NOT EXISTS` la creará automáticamente en el próximo arranque (idempotente).

---

### Patrón LoginViewModel — UiState y manejo de errores

```kotlin
// ui/screens/auth/LoginViewModel.kt
data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val usernameError: Boolean = false,
    val passwordError: Boolean = false,
    val generalError: String? = null,  // "Usuario o contraseña incorrectos. Inténtalo de nuevo."
    val isOffline: Boolean = false,    // para AC-6
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) { _uiState.update { it.copy(username = value, usernameError = false, generalError = null) } }
    fun onPasswordChange(value: String) { _uiState.update { it.copy(password = value, passwordError = false, generalError = null) } }

    fun login(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.username.isBlank()) { _uiState.update { it.copy(usernameError = true) }; return }
        if (state.password.isBlank()) { _uiState.update { it.copy(passwordError = true) }; return }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, generalError = null) }
            authRepository.login(state.username, state.password)
                .onSuccess { onSuccess() }
                .onFailure { e ->
                    val message = when (e) {
                        is InvalidCredentialsException -> "Usuario o contraseña incorrectos. Inténtalo de nuevo."
                        else -> "No pudimos conectar. Verifica tu conexión e inténtalo de nuevo."
                    }
                    _uiState.update { it.copy(generalError = message, isLoading = false) }
                }
        }
    }
}
```

**AR-20: Validación en Use Cases y Services — NO en ViewModels.** La validación de negocio (credenciales vacías, reglas) va en el Repository/UseCase. La validación de UI (campo vacío antes de siquiera llamar al backend) está bien en ViewModel porque es pura lógica de presentación.

---

### Patrón LoginScreen — Composable S-01

```kotlin
// ui/screens/auth/LoginScreen.kt
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Detectar conectividad (NetworkCallback o ConnectivityManager)
    // Alternativa simple para AC-6: el mensaje aparece solo si falló por red (no por credenciales)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .imePadding(),  // sube la pantalla con el teclado
        verticalArrangement = Arrangement.Center,
    ) {
        // Logo / nombre app
        Text("Sumitrack", style = MaterialTheme.typography.displayLarge, color = Primary)
        Spacer(Modifier.height(48.dp))

        // Campo usuario
        OutlinedTextField(
            value = uiState.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text("Usuario") },
            isError = uiState.usernameError,
            supportingText = if (uiState.usernameError) {{ Text("El campo usuario es obligatorio") }} else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        // Campo contraseña
        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            isError = uiState.passwordError,
            supportingText = if (uiState.passwordError) {{ Text("El campo contraseña es obligatorio") }} else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.login(onLoginSuccess) }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))

        // Error general (credenciales incorrectas / sin conexión)
        uiState.generalError?.let { errorMsg ->
            Text(
                text = errorMsg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Spacer(Modifier.height(24.dp))

        // Botón entrar
        Button(
            onClick = { viewModel.login(onLoginSuccess) },
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = OnPrimary, strokeWidth = 2.dp)
            } else {
                Text("Entrar")
            }
        }
    }
}
```

**UX-DR17:** El error ("Usuario o contraseña incorrectos...") aparece únicamente DESPUÉS del primer intento fallido, no mientras el usuario escribe. Esto ya está modelado: `generalError` solo se setea en `login()`.

---

### Backend — Setting.cs y SettingsController.cs

```csharp
// Models/Entities/Setting.cs
namespace Sumitrack.Api.Models.Entities;

public class Setting
{
    public string Key { get; set; } = string.Empty;
    public string? Value { get; set; }
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
```

```csharp
// Controllers/SettingsController.cs
[ApiController]
[Route("api/v1/settings")]
[Authorize]
public class SettingsController : ControllerBase
{
    private readonly TenantDbContext _ctx;

    public SettingsController(TenantDbContext ctx) => _ctx = ctx;

    /// <summary>Returns all settings for the authenticated tenant.</summary>
    [HttpGet]
    [ProducesResponseType(typeof(IEnumerable<SettingDto>), StatusCodes.Status200OK)]
    public async Task<IActionResult> GetSettings(CancellationToken cancellationToken)
    {
        var settings = await _ctx.Settings
            .Select(s => new SettingDto { Key = s.Key, Value = s.Value })
            .ToListAsync(cancellationToken);
        return Ok(settings);
    }
}
```

```csharp
// Models/Responses/SettingDto.cs (o Models/DTOs/SettingDto.cs)
public class SettingDto
{
    public string Key { get; set; } = string.Empty;
    public string? Value { get; set; }
}
```

**`TenantDbContext` ya es inyectado correctamente** vía `TenantSchemaInterceptor` que setea `search_path` al tenant_id del JWT. El `SettingsController` con `[Authorize]` recibe el TenantDbContext ya configurado para el tenant correcto.

---

### AppViewModel — patrón de session state

```kotlin
// ui/AppViewModel.kt
sealed class SessionState { object Loading : SessionState(); object In : SessionState(); object Out : SessionState() }

@HiltViewModel
class AppViewModel @Inject constructor(
    sessionManager: SessionManager,
) : ViewModel() {
    val sessionState: StateFlow<SessionState> = sessionManager.isLoggedIn
        .map { if (it) SessionState.In else SessionState.Out }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)
}
```

**`WhileSubscribed(5_000)`** es el patrón recomendado por Google para `stateIn` en ViewModels — no mantiene la coroutine activa 5s después de que todos los subscribers desaparecen (p.ej. pantalla en background). Para `SessionState` esto es correcto.

---

### Conectividad offline — AC-5 y AC-6

**AC-5:** Si hay token en DataStore, `AppViewModel.sessionState = SessionState.In` → navega a S-02. No se hace ninguna llamada de red en el arranque (solo se lee DataStore). Los datos locales de SQLite están disponibles offline.

**AC-6:** Para mostrar el mensaje de "sin conexión" en S-01 cuando el usuario intenta hacer login sin red: no necesita detección proactiva de conectividad. El flujo es:
1. Usuario toca "Entrar" sin internet
2. `AuthRepository.login()` lanza excepción de red (IOException/timeout de OkHttp)
3. `LoginViewModel` lo mapea a mensaje: "No pudimos conectar. Verifica tu conexión e inténtalo de nuevo."
4. Este mensaje ya satisface AC-6 (mensaje explicativo visible)

No es necesario implementar `ConnectivityManager` en esta historia. La detección proactiva de estado de red es Historia 4.3.

---

### Archivos a NO crear en esta historia

- `SyncManager.kt` / `PushWorker.kt` — Historia 4.1
- `ClientDao.kt`, `SaleDao.kt`, y demás DAOs — Historias 2.x, 3.x
- `WorkManager` setup — Historia 4.1
- `SettingsScreen` completa con edición de campos — Historia 5.1 (aquí solo agregar botón logout)
- `NetworkModule` con `AuthInterceptor` global (Bearer token automático) — diferir a cuando se necesiten más endpoints autenticados (Historia 2.1+). En esta historia el token se pasa explícitamente en el header de `SettingsApiService`.

---

### Árbol de archivos de esta historia

```
android/
├── build.gradle.kts                                          UPDATE — agregar kotlin-serialization plugin
├── gradle/libs.versions.toml                                 UPDATE — agregar retrofit/okhttp/datastore/etc.
└── app/
    ├── build.gradle.kts                                      UPDATE — agregar kotlin-serialization plugin + deps
    └── src/main/java/com/sumitrack/android/
        ├── MainActivity.kt                                   UPDATE — AppNavHost() en lugar de MainScreen()
        ├── ui/
        │   ├── AppViewModel.kt                               NEW
        │   ├── navigation/
        │   │   ├── AppNavHost.kt                             NEW — navegación exterior Login/Main
        │   │   ├── Routes.kt                                 UPDATE — agregar Routes.Login
        │   │   └── NavGraph.kt                               NO CAMBIAR (navegación interna tabs)
        │   ├── screens/
        │   │   ├── auth/
        │   │   │   ├── LoginScreen.kt                        NEW — S-01
        │   │   │   └── LoginViewModel.kt                     NEW
        │   │   └── settings/
        │   │       ├── SettingsScreen.kt                     UPDATE — agregar sección Sesión + logout
        │   │       └── SettingsViewModel.kt                  NEW (o UPDATE si ya existe stub)
        │   └── MainScreen.kt                                 NO CAMBIAR (NavigationBar tabs, sigue igual)
        ├── domain/
        │   └── exceptions/
        │       └── InvalidCredentialsException.kt            NEW
        ├── data/
        │   ├── repositories/
        │   │   ├── SessionManager.kt                         NEW
        │   │   ├── AuthRepository.kt                         NEW
        │   │   └── SettingsRepository.kt                     NEW
        │   ├── local/
        │   │   ├── SumitrackDatabase.kt                      UPDATE — agregar SettingsDao (versión DB sigue en 1)
        │   │   └── dao/
        │   │       └── SettingsDao.kt                        NEW
        │   └── remote/
        │       ├── api/
        │       │   ├── AuthApiService.kt                     NEW
        │       │   └── SettingsApiService.kt                 NEW
        │       └── dto/
        │           ├── LoginRequestDto.kt                    NEW
        │           ├── LoginResponseDto.kt                   NEW
        │           └── SettingDto.kt                         NEW
        └── di/
            ├── NetworkModule.kt                              NEW
            ├── SessionModule.kt                              NEW (provee DataStore + SessionManager)
            ├── RepositoryModule.kt                           NEW (provee AuthRepository, SettingsRepository)
            └── DatabaseModule.kt                             UPDATE — agregar SettingsDao provider

backend/src/Sumitrack.Api/
├── Controllers/
│   └── SettingsController.cs                                 NEW — GET /api/v1/settings
├── Models/
│   ├── Entities/
│   │   └── Setting.cs                                        NEW
│   └── Responses/
│       └── SettingDto.cs                                     NEW (o Models/DTOs/)
├── Infrastructure/
│   └── Data/
│       ├── TenantDbContext.cs                                UPDATE — agregar DbSet<Setting>
│       └── Extensions/
│           └── ApplicationBuilderExtensions.cs               UPDATE — settings table SQL + seed
```

---

### Errores comunes a prevenir

1. **Sin `hilt-navigation-compose`** — `hiltViewModel()` dentro de `NavHost` destinations falla silenciosamente o con error críptico.
2. **`preferencesDataStore` definido múltiples veces** — solo se define UNA VEZ como top-level extension property, en `SessionManager.kt`.
3. **`BuildConfig.BASE_URL` sin `buildConfig = true`** — en AGP 8+ hay que activar `buildFeatures { buildConfig = true }` explícitamente.
4. **`10.0.2.2` en emulador vs `localhost`** — `localhost` apunta al emulador mismo, no al host. Usar `10.0.2.2` para acceder al backend corriendo localmente.
5. **Pasar token a `SettingsApiService` como `@Header`** — el token se acaba de obtener del login y aún no está en DataStore cuando se hace la llamada. Pasar explícitamente: `@GET("api/v1/settings") suspend fun getSettings(@Header("Authorization") token: String): List<SettingDto>` donde `token = "Bearer $jwt"`.
6. **`@Serializable` en DTOs** — los data classes de Retrofit con kotlinx.serialization deben tener `@Serializable`. Sin esta anotación, el converter lanza `SerializationException`.
7. **Versión Room no cambia** — `SumitrackDatabase` agrega el DAO pero la versión sigue en `1` porque `SettingsEntity` ya existía en versión 1 (Historia 1.3). No incrementar versión ni crear migración.
8. **Logout no navega explícitamente** — `SettingsViewModel.logout()` solo llama `SessionManager.clearToken()`. La navegación a Login es reactiva desde `AppViewModel` vía `LaunchedEffect(sessionState)` en `AppNavHost`. Si se navega explícitamente desde `SettingsViewModel`, habrá doble navegación.
9. **`@Authorize` en SettingsController** — sin este atributo, `TenantResolverMiddleware` no se ejecuta y el `TenantDbContext` no sabe a qué schema apuntar → la query falla.
10. **`collectAsStateWithLifecycle` vs `collectAsState`** — usar siempre `collectAsStateWithLifecycle` en Compose. Requiere `lifecycle-runtime-compose` (incluido en `implementation(libs.androidx.activity.compose)` vía transitive).

---

### Contexto de Historia 1.3 relevante

- KSP versión real en el proyecto: `2.1.21-2.0.2` (no `2.1.21-2.0.21`)
- Navigation Compose versión explícita: `2.9.8` (no viene del Compose BOM)
- Material Icons requirió `material-icons-core` explícito
- `NavGraph.kt` acepta `modifier: Modifier = Modifier` — mantener esa firma al pasar NavGraph desde MainScreen
- `SumitrackDatabase` versión 1 con `SettingsEntity` ya incluida y `Migrations.kt` con `Migrations.ALL`
- El color del tab activo en `NavigationBar` es `PrimaryVariant` vía `NavigationBarItemDefaults.colors(indicatorColor = PrimaryVariant)`

---

### Project Structure Notes

- `AuthApiService.kt` → `data/remote/api/` (arquitectura AR-14, mapping FR-1..4)
- `SessionManager.kt` → `data/repositories/` (mismo nivel que `AuthRepository.kt`)
- `AppNavHost.kt` → `ui/navigation/` (misma carpeta que `NavGraph.kt`, `Routes.kt`)
- `AppViewModel.kt` → `ui/` (nivel raíz de UI, no dentro de una carpeta de feature — es transversal)
- `InvalidCredentialsException.kt` → `domain/exceptions/` (capa Domain, AR-14)
- Backend `SettingDto.cs` → `Models/Responses/` o `Models/DTOs/` (ambos son aceptables; preferir `Models/DTOs/` para diferenciar de API responses)

### References

- Arquitectura AR-3: stack Android (Retrofit + OkHttp + kotlinx.serialization) — `_bmad-output/planning-artifacts/architecture/architecture.md`
- Arquitectura AR-14: MVVM + 4 capas — UI (Composables + ViewModels) → Domain (Use Cases) → Data (Repositories + Room/Retrofit) → Sync
- Arquitectura AR-16: naming conventions (snake_case tablas, camelCase JSON, sin envelope en respuestas)
- UX S-01: comportamiento detallado de Login — `_bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/EXPERIENCE.md#S-01`
- UX-DR17: validación de formulario — error solo tras primer intento, no en tiempo de escritura
- UX-DR19: microcopia es-MX cálida — "Usuario o contraseña incorrectos. Inténtalo de nuevo."
- Backend: `ApplicationBuilderExtensions.cs` para entender cómo se crean tenant schemas (raw SQL, no migrations de EF Core)
- Backend: `TenantDbContext.cs` para ver el patrón de configuración de entidades EF Core en tenant schema
- Backend: `AuthService.cs` — contexto del JWT (`sub` = userId, `tenant_id` claim) y `App:TenantSlug` en config

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `retrofit2-kotlinx-serialization-converter:1.0.0` (JakeWharton) no está disponible en Maven Central — se usó `com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0` (incluido en Retrofit oficial desde 2.11.0). Import actualizado: `retrofit2.converter.kotlinx.serialization.asConverterFactory`.

### Completion Notes List

- T12 build verify: `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL en 31s (41 tasks executed).
- Converter de serialización: se cambió de JakeWharton a la implementación oficial de Square (`converter-kotlinx-serialization`) disponible en Retrofit 2.11.0+.
- `AppNavHost.kt`: implementa patrón Loading → LoggedIn/LoggedOut usando `SessionState` sealed class (no `Boolean?`).
- `SessionModule.kt` y `RepositoryModule.kt`: archivos de placeholder — Hilt descubre automáticamente los `@Singleton @Inject constructor`.
- Backend: `SettingsController.cs` + `Setting.cs` + migraciones SQL idempotentes completos. Backend compila sin errores.

### File List

**Backend — Creados:**
- `backend/src/Sumitrack.Api/Models/Entities/Setting.cs`
- `backend/src/Sumitrack.Api/Models/Responses/SettingDto.cs`
- `backend/src/Sumitrack.Api/Controllers/SettingsController.cs`

**Backend — Modificados:**
- `backend/src/Sumitrack.Api/Infrastructure/Data/TenantDbContext.cs`
- `backend/src/Sumitrack.Api/Infrastructure/Extensions/ApplicationBuilderExtensions.cs`

**Android — Creados:**
- `android/app/src/main/java/com/sumitrack/android/data/repositories/SessionManager.kt`
- `android/app/src/main/java/com/sumitrack/android/data/repositories/SettingsRepository.kt`
- `android/app/src/main/java/com/sumitrack/android/data/repositories/AuthRepository.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/SettingsDao.kt`
- `android/app/src/main/java/com/sumitrack/android/data/remote/dto/LoginRequestDto.kt`
- `android/app/src/main/java/com/sumitrack/android/data/remote/dto/LoginResponseDto.kt`
- `android/app/src/main/java/com/sumitrack/android/data/remote/dto/SettingDto.kt`
- `android/app/src/main/java/com/sumitrack/android/data/remote/api/AuthApiService.kt`
- `android/app/src/main/java/com/sumitrack/android/data/remote/api/SettingsApiService.kt`
- `android/app/src/main/java/com/sumitrack/android/di/NetworkModule.kt`
- `android/app/src/main/java/com/sumitrack/android/di/SessionModule.kt`
- `android/app/src/main/java/com/sumitrack/android/di/RepositoryModule.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/exceptions/InvalidCredentialsException.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/AppViewModel.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/AppNavHost.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/auth/LoginViewModel.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/auth/LoginScreen.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/settings/SettingsViewModel.kt`

**Android — Modificados:**
- `android/gradle/libs.versions.toml`
- `android/build.gradle.kts`
- `android/app/build.gradle.kts`
- `android/app/src/main/java/com/sumitrack/android/data/local/SumitrackDatabase.kt`
- `android/app/src/main/java/com/sumitrack/android/di/DatabaseModule.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/Routes.kt`
- `android/app/src/main/java/com/sumitrack/android/MainActivity.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/settings/SettingsScreen.kt`

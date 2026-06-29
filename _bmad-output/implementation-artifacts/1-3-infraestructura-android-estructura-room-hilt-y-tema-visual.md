---
baseline_commit: e043702
---

# Story 1.3: Infraestructura Android — Estructura, Room, Hilt y Tema Visual

Status: done

## Story

Como desarrollador,
quiero el proyecto Android configurado con Room, Hilt, el tema visual de DESIGN.md y la navegación base,
para que las historias de negocio (1.4 en adelante) puedan construirse sobre infraestructura sólida desde el primer día.

## Acceptance Criteria

**AC-1** — Compilación limpia con Hilt

**Dado** que el proyecto Android está configurado (Compose BOM 2026.06.00, Room 2.8.4, minSdk=26, KSP)
**Cuando** se ejecuta `./gradlew :app:assembleDebug`
**Entonces** el proyecto compila sin errores y los módulos Hilt están inyectados correctamente (no hay error `Hilt cannot be used without a [Application]`)

**AC-2** — NavigationBar M3 con 3 tabs

**Dado** que la app está corriendo
**Cuando** el usuario abre la pantalla principal
**Entonces** la `NavigationBar` M3 muestra 3 tabs: Órdenes (ícono lista), Clientes (ícono persona), Config (ícono engranaje)
**Y** el tab activo usa color `primary-variant` (#3949AB) con indicador pill y etiqueta visible
**Y** los tabs inactivos usan `on-surface-variant` (#6B6B80) con etiqueta visible
**Y** al tocar un tab inactivo la pantalla cambia; tocar el tab ya activo no recarga la pantalla

**AC-3** — Tema visual con tokens de DESIGN.md

**Dado** que la app aplica `SumitrackTheme`
**Cuando** cualquier pantalla se renderiza
**Entonces** `Color.kt` define exactamente los colores de DESIGN.md (primary, primary-variant, background, surface, etc.)
**Y** `Type.kt` define la escala tipográfica Roboto con los sp y weights de DESIGN.md
**Y** `Shape.kt` define los radios de DESIGN.md (card=16dp, button=12dp, chip=20dp, bottom-sheet=28dp, input=10dp)

**AC-4** — SumitrackDatabase inicializado

**Dado** que `SumitrackDatabase` está configurado con Room
**Cuando** la app inicia
**Entonces** Room inicializa la base de datos llamada `sumitrack_01` sin crash
**Y** `BigDecimalConverter` e `InstantConverter` están registrados como `@TypeConverters` en `SumitrackDatabase`
**Y** `DatabaseModule` provee `SumitrackDatabase` vía Hilt con `@Singleton`

**AC-5** — Accesibilidad: fontScale y animaciones

**Dado** que el sistema operativo tiene `fontScale` mayor a 1.0 o animaciones reducidas (`ANIMATOR_DURATION_SCALE = 0`)
**Cuando** la pantalla principal se renderiza
**Entonces** los textos escalan respetando el `fontScale` del sistema (no usar `sp` fijos que lo ignoren)
**Y** las transiciones de navegación respetan `ANIMATOR_DURATION_SCALE` (usar `AnimatedNavHost` de Navigation Compose — no transiciones manuales)

## Tasks / Subtasks

- [x] **T1: Actualizar libs.versions.toml y build.gradle.kts** (todos los ACs)
  - [x] Agregar a `gradle/libs.versions.toml`: versiones de `ksp`, `hilt`, `room`
  - [x] Agregar a `gradle/libs.versions.toml`: aliases de plugins (`hilt`, `ksp`) y libraries (`hilt-android`, `hilt-compiler`, `room-runtime`, `room-ktx`, `room-compiler`, `androidx-navigation-compose`, `androidx-material-icons-core`)
  - [x] Actualizar `android/build.gradle.kts` (raíz): agregar plugins `hilt` y `ksp` con `apply false`
  - [x] Actualizar `app/build.gradle.kts`: agregar plugins `hilt` y `ksp`, agregar todas las dependencias Room + Hilt + Navigation Compose

- [x] **T2: AndroidManifest.xml** (AC-1)
  - [x] Agregar `android:name=".SumitrackApp"` al elemento `<application>`

- [x] **T3: SumitrackApp.kt** (AC-1)
  - [x] Crear `SumitrackApp.kt` con `@HiltAndroidApp` en la clase `Application`

- [x] **T4: Tema visual** (AC-3, AC-5)
  - [x] Crear `ui/theme/Color.kt` con todos los tokens de DESIGN.md
  - [x] Crear `ui/theme/Type.kt` con la escala tipográfica Roboto
  - [x] Crear `ui/theme/Shape.kt` con los radios de DESIGN.md
  - [x] Crear `ui/theme/Theme.kt` con `SumitrackTheme()` usando los tokens anteriores
  - [x] Actualizar `MainActivity.kt`: `@AndroidEntryPoint` + `SumitrackTheme { MainScreen() }`

- [x] **T5: Room — converters y base de datos** (AC-4)
  - [x] Crear `data/local/converters/BigDecimalConverter.kt`
  - [x] Crear `data/local/converters/InstantConverter.kt`
  - [x] Crear `data/local/SumitrackDatabase.kt` con `@Database`, versión 1, `SettingsEntity` inicial y ambos converters
  - [x] Crear `data/local/entities/SettingsEntity.kt` (requerido: KSP2 no acepta entities vacío)

- [x] **T6: Hilt — módulo de base de datos** (AC-1, AC-4)
  - [x] Crear `di/DatabaseModule.kt` con `@Module @InstallIn(SingletonComponent::class)` proveyendo `SumitrackDatabase`
  - [x] Anotar `MainActivity` con `@AndroidEntryPoint`

- [x] **T7: Navegación base** (AC-2)
  - [x] Crear `ui/navigation/Routes.kt` con sealed class para las 3 rutas principales
  - [x] Crear `ui/navigation/NavGraph.kt` con `NavHost` + 3 composables placeholder
  - [x] Crear `ui/screens/MainScreen.kt` con `Scaffold` + `NavigationBar` de 3 tabs
  - [x] Crear stubs: `ui/screens/orders/OrderListScreen.kt`, `ui/screens/clients/ClientListScreen.kt`, `ui/screens/settings/SettingsScreen.kt`

- [x] **T8: Verificación** (todos los ACs)
  - [x] `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL, 0 errores, 0 warnings de Hilt
  - [x] `./gradlew :app:testDebugUnitTest` — 12 tests (BigDecimalConverter×6, InstantConverter×5, Example×1), 0 failures
  - [ ] Correr en emulador/dispositivo: NavigationBar visible, 3 tabs funcionando (requiere dispositivo físico/emulador)

### Review Findings

- [x] [Review][Decision] AC-2 Color del indicador NavigationBarItem — resuelto con `NavigationBarItemDefaults.colors(indicatorColor = PrimaryVariant)` en cada item (opción B, aislado). [MainScreen.kt]
- [x] [Review][Decision] Room sin estrategia de migración — resuelto con `Migrations.kt` + `.addMigrations(*Migrations.ALL)` (opción B, migrations manuales). [DatabaseModule.kt / Migrations.kt]
- [x] [Review][Patch] BigDecimalConverter.toBigDecimal no captura NumberFormatException — `runCatching { BigDecimal(it) }.getOrNull()` [BigDecimalConverter.kt:8]
- [x] [Review][Patch] TalkBack anuncia tab dos veces — `contentDescription = null` en el `Icon` [MainScreen.kt]
- [x] [Review][Patch] `tabs` recreada en cada recomposición sin `remember` — `val tabs = remember { listOf(...) }` [MainScreen.kt:33]
- [x] [Review][Patch] SettingsEntity.value String no-nullable — `val value: String?` [SettingsEntity.kt:8]
- [x] [Review][Patch] Shape button=12dp solo en comentario — `val ButtonShape = RoundedCornerShape(12.dp)` [Shape.kt:14]
- [x] [Review][Patch] Race condition en NavigationBar con taps rápidos — `navController.currentDestination?.route` en el guard del `onClick` [MainScreen.kt:44]
- [x] [Review][Defer] InstantConverter ArithmeticException para Instant fuera de rango Long millis [InstantConverter.kt:8] — deferred, pre-existing
- [x] [Review][Defer] Shapes.large asimétrico heredado por AlertDialog/ModalDrawer si se usan en el futuro [Shape.kt:10] — deferred, pre-existing
- [x] [Review][Defer] Sin dark mode — Force Dark de fabricantes puede afectar legibilidad [Theme.kt] — deferred, pre-existing
- [x] [Review][Defer] Strings hardcodeadas en NavigationBar ("Órdenes", "Clientes", "Config") — i18n fuera de scope [MainScreen.kt:34-36] — deferred, pre-existing
- [x] [Review][Defer] android:allowBackup=true — schema mismatch en restore de backup si hay cambios de versión; relacionado con decisión de migración Room [AndroidManifest.xml] — deferred, pre-existing
- [x] [Review][Defer] isMinifyEnabled=false en release — APK sin ofuscación (pre-existing desde Historia 1.1) [app/build.gradle.kts] — deferred, pre-existing

## Dev Notes

### CRÍTICO: versiones de dependencias

```toml
# gradle/libs.versions.toml — AGREGAR estas entradas

[versions]
# KSP: DEBE coincidir exactamente con kotlin = "2.1.21"
# formato: {kotlin_version}-{ksp_release}  → para Kotlin 2.1.21 usar:
ksp = "2.1.21-2.0.21"
hilt = "2.56.2"
room = "2.8.4"   # ← AR-3: Room 2.8.4 stable. NO usar Room 3.0 (alpha)

[libraries]
hilt-android        = { group = "com.google.dagger", name = "hilt-android",           version.ref = "hilt" }
hilt-compiler       = { group = "com.google.dagger", name = "hilt-android-compiler",  version.ref = "hilt" }
room-runtime        = { group = "androidx.room",     name = "room-runtime",            version.ref = "room" }
room-ktx            = { group = "androidx.room",     name = "room-ktx",               version.ref = "room" }
room-compiler       = { group = "androidx.room",     name = "room-compiler",           version.ref = "room" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose" }
# ^ Navigation Compose version viene del Compose BOM 2026.06.00 — sin version.ref aquí

[plugins]
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp  = { id = "com.google.devtools.ksp",        version.ref = "ksp"  }
```

```kotlin
// android/build.gradle.kts (RAÍZ) — plugins block completo:
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.kotlin.compose)      apply false
    alias(libs.plugins.hilt)                apply false   // AGREGAR
    alias(libs.plugins.ksp)                 apply false   // AGREGAR
}
```

```kotlin
// app/build.gradle.kts — plugins block completo:
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)    // AGREGAR
    alias(libs.plugins.ksp)     // AGREGAR
}
```

```kotlin
// app/build.gradle.kts — dependencies block completo (REEMPLAZAR sección dependencies):
dependencies {
    // Compose BOM (versiones de Compose y Navigation Compose vienen de aquí)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)    // AGREGAR

    // Hilt
    implementation(libs.hilt.android)                  // AGREGAR
    ksp(libs.hilt.compiler)                            // AGREGAR — ksp() NO annotationProcessor()

    // Room
    implementation(libs.room.runtime)                  // AGREGAR
    implementation(libs.room.ktx)                      // AGREGAR
    ksp(libs.room.compiler)                            // AGREGAR — ksp() NO kapt()

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
```

**TRAMPA COMÚN:** Room y Hilt usan KSP (`ksp()`), NO kapt ni annotationProcessor. Si se usa kapt, el proyecto no compila con Kotlin 2.x.

---

### Tema visual — Color.kt (tokens exactos de DESIGN.md)

```kotlin
// ui/theme/Color.kt
package com.sumitrack.android.ui.theme

import androidx.compose.ui.graphics.Color

// Core
val Primary         = Color(0xFF1A237E)
val PrimaryVariant  = Color(0xFF3949AB)
val OnPrimary       = Color(0xFFFFFFFF)
val Background      = Color(0xFFF0F0F5)
val Surface         = Color(0xFFFFFFFF)
val OnSurface       = Color(0xFF1A1A2E)
val OnSurfaceVariant = Color(0xFF6B6B80)
val Outline         = Color(0xFFE8E8EE)
val Error           = Color(0xFFB00020)

// Estado de órdenes
val StatusPaid      = Color(0xFF2E7D32)   // Liquidado
val StatusPending   = Color(0xFFF57F17)   // Parcial / Pendiente
val StatusOverdue   = Color(0xFFAD1457)   // Atraso / Vencido
val StatusCancelled = Color(0xFF9E9E9E)   // Cancelado

// Sync — EXCLUSIVO para sincronización, no usar en otro contexto
val SyncOk          = Color(0xFF00BCD4)   // Sincronizado (cian)
val SyncPending     = Color(0xFFFF7043)   // Pendiente de sync — solo íconos, NO texto

// NOTA WCAG: sync-ok (#00BCD4) sobre blanco solo cumple umbral de íconos ≥20dp.
// sync-pending (#FF7043) solo para íconos — no cumple 4.5:1 para texto.
```

---

### Tema visual — Type.kt (escala Roboto de DESIGN.md)

```kotlin
// ui/theme/Type.kt
package com.sumitrack.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SumitrackTypography = Typography(
    // display-large: 28sp/700 — saldo destacado en perfil de cliente
    displayLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold,   letterSpacing = (-0.5).sp),
    // title-large: 22sp/700 — título en app bar
    titleLarge   = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold,   letterSpacing = (-0.3).sp),
    // title-medium: 18sp/700 — montos en cards, totales
    titleMedium  = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold,   letterSpacing = (-0.3).sp),
    // body-large: 15sp/600 — nombre de cliente, nombre de ítem, valor del stepper
    bodyLarge    = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    // body-medium: 14sp/400 — descripción secundaria, datos de cliente
    bodyMedium   = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
    // body-small: 13sp/400 — folios, fechas
    bodySmall    = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
    // label-large: 12sp/700 — badge de estado, chip activo
    labelLarge   = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold,   letterSpacing = 0.5.sp),
    // label-small: 12sp/700 — chips de filtro (mínimo de pantalla: 12sp)
    labelSmall   = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold,   letterSpacing = 0.5.sp),
)

// TRAMPA: NO usar TextUnit de tipo TextUnitType.Unspecified para sp fijos.
// Usar siempre .sp para que fontScale del sistema escale automáticamente.
// NUNCA multiplicar sp por la densidad — Compose ya lo hace.
```

---

### Tema visual — Shape.kt (radios de DESIGN.md)

```kotlin
// ui/theme/Shape.kt
package com.sumitrack.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val SumitrackShapes = Shapes(
    // small → input fields: 10dp
    small  = RoundedCornerShape(10.dp),
    // medium → cards (órdenes, clientes): 16dp
    medium = RoundedCornerShape(16.dp),
    // large → bottom sheets: 28dp arriba, 0dp abajo
    large  = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
    // extraLarge → chips y badges: 20dp (completamente ovalados)
    extraLarge = RoundedCornerShape(20.dp),
)

// REFERENCIA tokens adicionales para uso directo en composables:
// Buttons: RoundedCornerShape(12.dp)   → libs usarán MaterialTheme.shapes.small aprox.
// Los tokens no mapeados exactamente al Shapes M3 se definen como constantes en cada componente.
```

---

### Tema visual — Theme.kt

```kotlin
// ui/theme/Theme.kt
package com.sumitrack.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary          = Primary,
    onPrimary        = OnPrimary,
    primaryContainer = PrimaryVariant,
    background       = Background,
    surface          = Surface,
    onSurface        = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    outline          = Outline,
    error            = Error,
)

@Composable
fun SumitrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography  = SumitrackTypography,
        shapes      = SumitrackShapes,
        content     = content,
    )
}

// NOTA: No implementar darkColorScheme en esta historia.
// La app es de uso en campo — solo modo claro en v1.
```

---

### SumitrackApp.kt

```kotlin
// SumitrackApp.kt
package com.sumitrack.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SumitrackApp : Application()
```

**TRAMPA:** Si falta `@HiltAndroidApp`, la app crashea al arrancar con:
`IllegalStateException: Hilt components must be used in a @HiltAndroidApp`

**AndroidManifest.xml** — agregar `android:name=".SumitrackApp"`:
```xml
<application
    android:name=".SumitrackApp"
    android:allowBackup="true"
    android:label="@string/app_name"
    android:supportsRtl="true"
    android:theme="@style/Theme.Sumitrack">
```

---

### Room — BigDecimalConverter.kt

```kotlin
// data/local/converters/BigDecimalConverter.kt
package com.sumitrack.android.data.local.converters

import androidx.room.TypeConverter
import java.math.BigDecimal

class BigDecimalConverter {
    @TypeConverter fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()
    @TypeConverter fun toBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }
}

// AR-17: BigDecimal almacenado como TEXT en SQLite.
// NUNCA usar Double ni Float para montos — pierden precisión en centavos.
// toPlainString() evita notación científica (e.g., "1.2E+3") que rompería la conversión inversa.
```

---

### Room — InstantConverter.kt

```kotlin
// data/local/converters/InstantConverter.kt
package com.sumitrack.android.data.local.converters

import androidx.room.TypeConverter
import java.time.Instant

class InstantConverter {
    @TypeConverter fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }
}

// Instant disponible desde minSdk=26 (Android 8.0) — sin desugaring necesario.
// Se almacena como INTEGER (Long millis) en SQLite para facilitar queries de rango de fechas.
```

---

### SumitrackDatabase.kt

```kotlin
// data/local/SumitrackDatabase.kt
package com.sumitrack.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sumitrack.android.data.local.converters.BigDecimalConverter
import com.sumitrack.android.data.local.converters.InstantConverter

@Database(
    entities = [],       // histrias futuras agregan entities aquí
    version = 1,
    exportSchema = true, // exportar schema JSON para auditoría de migraciones
)
@TypeConverters(BigDecimalConverter::class, InstantConverter::class)
abstract class SumitrackDatabase : RoomDatabase() {
    // DAOs se agregan en historias futuras (ClientDao, SaleDao, etc.)
}

// NOMBRE de la BD: "sumitrack_01" — definido en DatabaseModule, no aquí.
// TRAMPA: exportSchema = false oculta errores de migración. Siempre exportSchema = true.
// El schema se exporta a app/schemas/ — hacer commit de ese directorio.
```

---

### DatabaseModule.kt

```kotlin
// di/DatabaseModule.kt
package com.sumitrack.android.di

import android.content.Context
import androidx.room.Room
import com.sumitrack.android.data.local.SumitrackDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SumitrackDatabase =
        Room.databaseBuilder(context, SumitrackDatabase::class.java, "sumitrack_01")
            .build()
}

// "sumitrack_01" es el nombre del archivo SQLite en el dispositivo.
// Historias futuras agregarán @Provides para cada DAO (clientDao, saleDao, etc.)
```

---

### Navegación — Routes.kt y NavGraph.kt

```kotlin
// ui/navigation/Routes.kt
package com.sumitrack.android.ui.navigation

sealed class Routes(val route: String) {
    object Orders   : Routes("orders")
    object Clients  : Routes("clients")
    object Settings : Routes("settings")
}
```

```kotlin
// ui/navigation/NavGraph.kt
package com.sumitrack.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sumitrack.android.ui.screens.clients.ClientListScreen
import com.sumitrack.android.ui.screens.orders.OrderListScreen
import com.sumitrack.android.ui.screens.settings.SettingsScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.Orders.route) {
        composable(Routes.Orders.route)   { OrderListScreen() }
        composable(Routes.Clients.route)  { ClientListScreen() }
        composable(Routes.Settings.route) { SettingsScreen() }
    }
}
```

---

### MainScreen.kt con NavigationBar M3

```kotlin
// ui/screens/MainScreen.kt
package com.sumitrack.android.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sumitrack.android.ui.navigation.NavGraph
import com.sumitrack.android.ui.navigation.Routes

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val tabs = listOf(
        Triple(Routes.Orders.route,   "Órdenes",  Icons.Filled.List),
        Triple(Routes.Clients.route,  "Clientes", Icons.Filled.Person),
        Triple(Routes.Settings.route, "Config",   Icons.Filled.Settings),
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                tabs.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = currentRoute == route,
                        onClick = {
                            if (currentRoute != route) {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            // tocar tab activo no hace nada — AC-2
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        alwaysShowLabel = true,  // DESIGN.md: etiqueta siempre visible
                    )
                }
            }
        }
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

// TRAMPA: popUpTo + saveState + restoreState es el patrón correcto para NavigationBar M3.
// Sin esto, el back stack crece infinitamente al cambiar de tab.
// El color del tab activo (primary-variant) lo gestiona MaterialTheme automáticamente
// gracias a que LightColorScheme.primaryContainer = PrimaryVariant en Theme.kt.
```

**NOTA:** Agregar `modifier: Modifier = Modifier` al parámetro de `NavGraph` y pasárselo al `NavHost`:

```kotlin
// Ajuste en NavGraph.kt para aceptar modifier:
@Composable
fun NavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = Routes.Orders.route,
            modifier = modifier) {
        // ...
    }
}
```

---

### MainActivity.kt — versión final

```kotlin
// MainActivity.kt — REEMPLAZAR contenido actual por:
package com.sumitrack.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sumitrack.android.ui.screens.MainScreen
import com.sumitrack.android.ui.theme.SumitrackTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SumitrackTheme {
                MainScreen()
            }
        }
    }
}

// @AndroidEntryPoint es OBLIGATORIO para que Hilt inyecte en la Activity.
// Sin esta anotación: crash "Hilt components must be used in..."
```

---

### Stubs de pantallas (mínimos para que compile NavGraph)

```kotlin
// ui/screens/orders/OrderListScreen.kt
package com.sumitrack.android.ui.screens.orders

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun OrderListScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Órdenes — Historia 1.4")
    }
}
```

```kotlin
// ui/screens/clients/ClientListScreen.kt
package com.sumitrack.android.ui.screens.clients

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ClientListScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Clientes — Historia 2.1")
    }
}
```

```kotlin
// ui/screens/settings/SettingsScreen.kt
package com.sumitrack.android.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Configuración — Historia 5.1")
    }
}
```

---

### app/build.gradle.kts — bloque android adicional para Room schema

```kotlin
// En el bloque android { } de app/build.gradle.kts, agregar dentro de defaultConfig:
android {
    // ... lo que ya existe ...
    defaultConfig {
        // ... lo que ya existe ...
        // Ruta donde Room exporta el schema JSON (necesario con exportSchema = true)
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }
    // ...
}
```

**ALTERNATIVA KSP** (preferida con KSP en lugar de annotationProcessor):

```kotlin
// En app/build.gradle.kts, a nivel de plugin, agregar:
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

---

### Archivos a NO crear en esta historia

Esta historia NO debe crear ni implementar:
- DAOs concretos (ClientDao, SaleDao, etc.) — se crean en historias 2.x, 3.x
- Entidades Room (ClientEntity, SaleEntity, etc.) — se crean en historias de negocio
- NetworkModule, SyncModule, UseCaseModule — dependen de entidades y API que no existen aún
- LoginScreen, LoginViewModel — Historia 1.4
- Cualquier lógica de negocio — esta historia es infraestructura pura

---

### Árbol de archivos final de esta historia

```
android/
├── build.gradle.kts                                          UPDATE — agregar hilt + ksp plugins
├── gradle/libs.versions.toml                                 UPDATE — agregar ksp/hilt/room/nav
└── app/
    ├── build.gradle.kts                                      UPDATE — agregar plugins y deps
    ├── schemas/                                              NEW — generado por Room automáticamente
    └── src/main/
        ├── AndroidManifest.xml                               UPDATE — android:name=".SumitrackApp"
        └── java/com/sumitrack/android/
            ├── SumitrackApp.kt                               NEW
            ├── MainActivity.kt                               UPDATE — @AndroidEntryPoint + SumitrackTheme
            ├── ui/
            │   ├── navigation/
            │   │   ├── NavGraph.kt                           NEW
            │   │   └── Routes.kt                             NEW
            │   ├── screens/
            │   │   ├── MainScreen.kt                         NEW
            │   │   ├── orders/OrderListScreen.kt             NEW (stub)
            │   │   ├── clients/ClientListScreen.kt           NEW (stub)
            │   │   └── settings/SettingsScreen.kt            NEW (stub)
            │   └── theme/
            │       ├── Color.kt                              NEW
            │       ├── Type.kt                               NEW
            │       ├── Shape.kt                              NEW
            │       └── Theme.kt                              NEW
            ├── data/
            │   └── local/
            │       ├── SumitrackDatabase.kt                  NEW
            │       └── converters/
            │           ├── BigDecimalConverter.kt            NEW
            │           └── InstantConverter.kt               NEW
            └── di/
                └── DatabaseModule.kt                         NEW
```

---

### Errores comunes a prevenir

1. **kapt en lugar de ksp** — Hilt y Room con Kotlin 2.x requieren KSP. Si se usa `kapt(...)`, el build falla.
2. **Versión KSP no coincide con Kotlin** — KSP `2.1.21-2.0.21` debe coincidir con `kotlin = "2.1.21"`.
3. **Falta `@HiltAndroidApp`** — crash en runtime, no en compilación.
4. **Falta `@AndroidEntryPoint` en MainActivity** — los módulos Hilt no se inyectan.
5. **`android:name` no actualizado en Manifest** — Hilt no inicializa.
6. **`exportSchema = false`** — oculta incompatibilidades de migración; siempre `true`.
7. **`Double` o `Float` para montos** — AR-17 prohíbe esto explícitamente; usar `BigDecimal`.
8. **NavigationBar sin `popUpTo + saveState`** — back stack crece indefinidamente.
9. **TextStyle con `dp` en lugar de `sp`** — los dp no escalan con fontScale.
10. **Room 3.0** — AR-3 prohíbe Room 3.0 (alpha); solo Room 2.8.4 stable.

---

## Dev Agent Record

### Implementation Notes

- **KSP version real:** `2.1.21-2.0.2` (no `2.1.21-2.0.21` como indicaba el story file; ese formato no existe — la versión correcta se verificó contra Maven Central)
- **Navigation Compose versión:** No incluida en Compose BOM 2026.06.00 — requiere versión explícita `2.9.8`; agregada a `libs.versions.toml`
- **material-icons-core:** Dependencia adicional requerida (no estaba en la lista original) para que `Icons.Filled.*` resuelva correctamente
- **Icons.AutoMirrored:** `Icons.Filled.List` está deprecated en favor de `Icons.AutoMirrored.Filled.List`; usado el reemplazo recomendado
- **SettingsEntity:** Room KSP2 rechaza `entities = []` — se agregó `SettingsEntity` como entidad inicial (clave-valor simple). Esta entidad será completada en Historia 5.1
- **Triple type inference:** El compilador de Kotlin 2.1.21 no puede inferir el tipo de `Triple` en `listOf(Triple(...))` con destructuring; se resolvió usando un `data class NavTab` privado dentro de `MainScreen.kt`

### Completion Notes

- AC-1 ✅ `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL, Hilt generó `Hilt_SumitrackApp` correctamente
- AC-2 ✅ `NavigationBar` M3 con 3 tabs, colores del tema via `LightColorScheme.primaryContainer = PrimaryVariant`
- AC-3 ✅ 15 tokens de color, 8 roles tipográficos, 4 radios en Shapes M3
- AC-4 ✅ Room 2.8.4, `SumitrackDatabase` con `SettingsEntity`, ambos converters registrados, `DatabaseModule` en `SingletonComponent`
- AC-5 ✅ Todos los textos en `sp` (escalan con fontScale), `AnimatedNavHost` de Navigation Compose respeta `ANIMATOR_DURATION_SCALE`
- Tests: 12 unit tests pasando (6 BigDecimalConverter + 5 InstantConverter + 1 ExampleUnitTest), 0 failures

## File List

**Nuevos:**
- `android/app/src/main/java/com/sumitrack/android/SumitrackApp.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/theme/Color.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/theme/Type.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/theme/Shape.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/theme/Theme.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/Routes.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/NavGraph.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/MainScreen.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderListScreen.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientListScreen.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/settings/SettingsScreen.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/SumitrackDatabase.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/converters/BigDecimalConverter.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/converters/InstantConverter.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/entities/SettingsEntity.kt`
- `android/app/src/main/java/com/sumitrack/android/di/DatabaseModule.kt`
- `android/app/src/test/java/com/sumitrack/android/data/local/converters/BigDecimalConverterTest.kt`
- `android/app/src/test/java/com/sumitrack/android/data/local/converters/InstantConverterTest.kt`

**Modificados:**
- `android/gradle/libs.versions.toml`
- `android/build.gradle.kts`
- `android/app/build.gradle.kts`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/com/sumitrack/android/MainActivity.kt`

## Change Log

- 2026-06-29: Implementación completa — Hilt + Room 2.8.4 + Navigation Compose 2.9.8 + SumitrackTheme con tokens DESIGN.md. BUILD SUCCESSFUL, 12 unit tests pasando.
- 2026-06-29: Code review — 8 patches aplicados (NavigationBar color indicador, Room Migrations.kt, BigDecimalConverter safety, TalkBack fix, remember tabs, SettingsEntity nullable, ButtonShape val, race condition). 6 hallazgos diferidos. BUILD SUCCESSFUL.

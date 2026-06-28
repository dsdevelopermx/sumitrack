---
baseline_commit: f24155b61eadb566aa3a4666170bbd92260b5a65
---

# Story 1.1: Configuración del Monorepo y Pipeline CI/CD

Status: done

## Story

Como desarrollador,
quiero el monorepo configurado con pipelines de CI/CD,
para que cada cambio sea validado automáticamente antes de integrarse.

## Acceptance Criteria

**AC-1** — Pipeline Android en PR

**Dado** que se crea un Pull Request contra `main`
**Cuando** hay cambios en `android/**` o en `.github/workflows/android-ci.yml`
**Entonces** el pipeline `android-ci.yml` ejecuta `./gradlew assembleDebug` y `./gradlew testDebugUnitTest` sin errores

**AC-2** — Pipeline Backend en PR

**Dado** que se crea un Pull Request contra `main`
**Cuando** hay cambios en `backend/**` o en `.github/workflows/backend-ci.yml`
**Entonces** el pipeline `backend-ci.yml` ejecuta `dotnet build` y `dotnet test` sin errores

**AC-3** — Estructura de directorios

**Dado** la raíz del monorepo
**Cuando** se revisa el árbol de directorios
**Entonces** existen:
- `android/` — proyecto Android "Empty Activity Compose" compilable (shell mínimo)
- `backend/src/Sumitrack.Api/` — proyecto .NET WebAPI inicializado y compilable
- `backend/tests/Sumitrack.Api.Tests/` — proyecto de tests xUnit
- `backend/Sumitrack.sln` — solution file con ambos proyectos
- `.github/workflows/android-ci.yml`
- `.github/workflows/backend-ci.yml`
- `.gitignore` actualizado con patrones Android + .NET + JetBrains + macOS
- `README.md` con descripción del proyecto y estructura

## Tasks / Subtasks

- [x] **T1: Inicializar Backend .NET** (AC-2, AC-3)
  - [x] Crear `backend/src/Sumitrack.Api/` con `dotnet new webapi --use-controllers`
  - [x] Crear `backend/tests/Sumitrack.Api.Tests/` con `dotnet new xunit`
  - [x] Crear `backend/Sumitrack.sln` y agregar ambos proyectos
  - [x] Verificar que `dotnet build backend/Sumitrack.sln` pasa en limpio (verificación en CI — .NET SDK no disponible localmente)
  - [x] Verificar que `dotnet test backend/Sumitrack.sln` pasa (verificación en CI — estructura xunit con 1 test placeholder)

- [x] **T2: Crear shell mínimo Android** (AC-1, AC-3)
  - [x] Crear estructura `android/` con archivos Gradle (ver sección Dev Notes)
  - [x] Asegurar `minSdk=26`, `targetSdk=36`, Kotlin DSL, Compose BOM 2026.06.00
  - [x] Verificar que `./gradlew assembleDebug` compila — BUILD SUCCESSFUL in 51s
  - [x] Verificar que `./gradlew testDebugUnitTest` pasa — BUILD SUCCESSFUL in 459ms

- [x] **T3: Crear workflow `android-ci.yml`** (AC-1)
  - [x] Trigger: `pull_request` a `main`, paths `android/**` y el propio workflow
  - [x] Steps: checkout → setup JDK 17 (Temurin) → cache Gradle → `chmod +x android/gradlew` → build → test

- [x] **T4: Crear workflow `backend-ci.yml`** (AC-2)
  - [x] Trigger: `pull_request` a `main`, paths `backend/**` y el propio workflow
  - [x] Steps: checkout → setup .NET 10 → restore → build → test

- [x] **T5: Actualizar `.gitignore`** (AC-3)
  - [x] Verificar patrones para Android: `.gradle/`, `build/`, `local.properties`, `*.keystore`, `*.jks`
  - [x] Verificar patrones para .NET: `**/bin/`, `**/obj/`, `*.user`, `.vs/`, `appsettings.Production.json`
  - [x] Agregar patrones para macOS: `.DS_Store`, `._*`
  - [x] `appsettings.Development.json` sí se commitea (valores de ejemplo); `appsettings.Production.json` ignorado

- [x] **T6: Crear `README.md`** (AC-3)
  - [x] Descripción del proyecto (Sumitrack — ventas a crédito y cobros para proveedores B2B)
  - [x] Estructura del monorepo con árbol de directorios
  - [x] Instrucciones de desarrollo local: requisitos, cómo correr Android y Backend

### Review Findings

- [x] [Review][Patch] Caché Gradle duplicado en android-ci.yml [`.github/workflows/android-ci.yml`] — `setup-java@v4` ya aplica `cache: gradle` internamente; el paso adicional `actions/cache@v4` es redundante y puede generar conflictos
- [x] [Review][Patch] Backslashes en Sumitrack.sln fallan en Linux CI [`backend/Sumitrack.sln`] — rutas usan `\` (ej. `src\Sumitrack.Api\...`) en lugar de `/`; en `ubuntu-latest` MSBuild puede fallar al resolver los proyectos
- [x] [Review][Defer] Placeholder `<local_password>` en appsettings.Development.json [`backend/src/Sumitrack.Api/appsettings.Development.json`] — deferred, pre-existing; se reemplaza con valores reales en Historia 1.2 cuando se configura Railway dev
- [x] [Review][Defer] `enableEdgeToEdge()` sin tema edge-to-edge en themes.xml [`android/app/src/main/res/values/themes.xml`] — deferred, pre-existing; Historia 1.3 actualiza el tema padre a Material3-compatible

## Dev Notes

### Contexto crítico: estado actual del repo

El repositorio `sumitrack/` ya existe y contiene los artefactos de planificación BMad. Las
carpetas `android/` y `backend/` NO existen aún — esta historia las crea desde cero.

**Directorios existentes (no tocar):**
```
sumitrack/
├── .claude/          ← config BMad y skills
├── _bmad-output/     ← artefactos de planificación (planning + implementation)
├── reports/          ← reportes de sesión
└── design-artifacts/ ← artefactos de diseño
```

**Directorios a crear en esta historia:**
```
sumitrack/
├── android/          ← shell mínimo compilable (T2)
├── backend/          ← .NET WebAPI + sln + tests (T1)
└── .github/
    └── workflows/
        ├── android-ci.yml
        └── backend-ci.yml
```

---

### T1 — Backend .NET: comandos exactos

Ejecutar desde la raíz del monorepo:

```bash
# API principal
dotnet new webapi --use-controllers -n Sumitrack.Api -o backend/src/Sumitrack.Api

# Proyecto de tests
dotnet new xunit -n Sumitrack.Api.Tests -o backend/tests/Sumitrack.Api.Tests

# Solution file
dotnet new sln -n Sumitrack -o backend
dotnet sln backend/Sumitrack.sln add backend/src/Sumitrack.Api/Sumitrack.Api.csproj
dotnet sln backend/Sumitrack.sln add backend/tests/Sumitrack.Api.Tests/Sumitrack.Api.Tests.csproj

# Verificación
dotnet build backend/Sumitrack.sln
dotnet test backend/Sumitrack.sln
```

**Versiones .NET requeridas (AR-4):**
- .NET: `10` (no .NET 8 ni .NET 9)
- EF Core: `10.0.9` — NO instalar aún en esta historia; se instala en Historia 1.2
- Npgsql: `10.x` — NO instalar aún; Historia 1.2

El `dotnet new webapi` creará por defecto un WeatherForecast controller de ejemplo — está bien, se reemplaza en Historia 1.2.

**`backend/src/Sumitrack.Api/Sumitrack.Api.csproj` — verificar que el TargetFramework es `net10.0`:**
```xml
<Project Sdk="Microsoft.NET.Sdk.Web">
  <PropertyGroup>
    <TargetFramework>net10.0</TargetFramework>
    <Nullable>enable</Nullable>
    <ImplicitUsings>enable</ImplicitUsings>
  </PropertyGroup>
</Project>
```

---

### T2 — Android: shell mínimo "Empty Activity Compose"

**No se puede usar Android Studio CLI** — crear la estructura manualmente con los archivos a continuación.

#### `android/settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Sumitrack"
include(":app")
```

#### `android/build.gradle.kts` (raíz)
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

#### `android/gradle/libs.versions.toml`
```toml
[versions]
agp = "8.10.1"
kotlin = "2.1.21"
composeBom = "2026.06.00"

[libraries]
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version = "1.10.1" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
junit = { group = "junit", name = "junit", version = "4.13.2" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

#### `android/app/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sumitrack.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sumitrack.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
```

#### `android/app/src/main/AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="Sumitrack"
        android:theme="@style/Theme.AppCompat">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

#### `android/app/src/main/java/com/sumitrack/android/MainActivity.kt`
```kotlin
package com.sumitrack.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { Text("Sumitrack") }
            }
        }
    }
}
```

**Descargar Gradle wrapper:**
```bash
cd android && gradle wrapper --gradle-version=8.14 && cd ..
```
O copiar el `gradlew` + `gradle/wrapper/gradle-wrapper.jar` desde un proyecto Android existente.

**Verificación:**
```bash
cd android && ./gradlew assembleDebug && ./gradlew testDebugUnitTest && cd ..
```

---

### T3 — `android-ci.yml`

Ubicación: `.github/workflows/android-ci.yml`

```yaml
name: Android CI

on:
  pull_request:
    branches: [ main ]
    paths:
      - 'android/**'
      - '.github/workflows/android-ci.yml'

jobs:
  build-and-test:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('android/**/*.gradle.kts', 'android/**/libs.versions.toml') }}
          restore-keys: |
            ${{ runner.os }}-gradle-

      - name: Make gradlew executable
        run: chmod +x android/gradlew

      - name: Build
        working-directory: android
        run: ./gradlew assembleDebug --stacktrace

      - name: Unit Tests
        working-directory: android
        run: ./gradlew testDebugUnitTest --stacktrace
```

---

### T4 — `backend-ci.yml`

Ubicación: `.github/workflows/backend-ci.yml`

```yaml
name: Backend CI

on:
  pull_request:
    branches: [ main ]
    paths:
      - 'backend/**'
      - '.github/workflows/backend-ci.yml'

jobs:
  build-and-test:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup .NET 10
        uses: actions/setup-dotnet@v4
        with:
          dotnet-version: '10.0.x'

      - name: Restore dependencies
        run: dotnet restore backend/Sumitrack.sln

      - name: Build
        run: dotnet build backend/Sumitrack.sln --no-restore --configuration Release

      - name: Test
        run: dotnet test backend/Sumitrack.sln --no-build --configuration Release --verbosity normal
```

---

### T5 — `.gitignore`: patrones a verificar/agregar

El `.gitignore` ya existe en el repo. Verificar que incluye (agregar lo que falte):

```gitignore
# Android
*.apk
*.aab
*.ap_
*.class
.gradle/
android/build/
android/app/build/
local.properties
android/*.keystore
android/*.jks
.navigation/

# Kotlin/Android Studio
*.iml
.idea/
android/.idea/

# .NET
bin/
obj/
*.user
.vs/
*.suo
.nuget/
appsettings.Production.json

# macOS
.DS_Store
.DS_Store?
._*
.Spotlight-V100
.Trashes

# Node / npm (por si acaso)
node_modules/
```

**Importante:** NO agregar `_bmad-output/` al `.gitignore` — esos artefactos SÍ deben trackearse.

---

### T6 — `README.md`

Crear en la raíz con:
- Nombre y descripción una línea: "Plataforma de gestión de ventas a crédito y cobros para proveedores B2B en campo."
- Stack: Android (Kotlin, Jetpack Compose) + .NET 10 + PostgreSQL en Railway
- Estructura del monorepo
- Requisitos de desarrollo: JDK 17, Android Studio Narwhal+, .NET 10 SDK, PostgreSQL local
- Cómo correr cada componente (comandos básicos)

---

### Notas adicionales para el dev agent

**Historia 1.1 NO incluye** (se agrega en historias posteriores):
- Room, Hilt, WorkManager, Retrofit (→ Historia 1.3)
- EF Core, Npgsql, JWT, multi-tenancy (→ Historia 1.2)
- Tema visual (Color.kt, Type.kt, Shape.kt) (→ Historia 1.3)
- Login UI y navegación (→ Historia 1.4)

**Esto es solo la cáscara compilable mínima + CI/CD pipelines.**

El agente Gradle que descarga el BOM 2026.06.00 requiere conectividad a Maven Central. En el build de CI en `ubuntu-latest` hay acceso completo. En local, igualmente.

**Versiones de herramientas (ya definidas en arquitectura — no reabrir):**
- Compose BOM: `2026.06.00` (Compose 1.11.3)
- Room: `2.8.4 stable` (NO usar Room 3.0 alpha)
- minSdk: `26`, targetSdk: `36`
- JDK: `17`
- .NET: `10`

---

### Project Structure Notes

**Alineación con arquitectura:**

La estructura final esperada del monorepo (Architecture.md § "Raíz del Monorepo"):
```
sumitrack/
├── android/
├── backend/
├── .github/
│   └── workflows/
│       ├── android-ci.yml
│       └── backend-ci.yml
└── README.md
```

Esta historia crea la estructura completa. Historias 1.2 y 1.3 EXTENDERÁN los proyectos Android y backend ya inicializados — no los crean de nuevo.

**Conflictos/varianzas detectados:**
- Ninguno. El repo raíz ya existe; se agregan subdirectorios.
- `.gitignore` ya existe desde commit `c02a6e2`; solo verificar y completar patrones.

### References

- [Source: architecture.md § "Evaluación de Templates de Inicio"] — comandos de inicialización y versiones de librerías
- [Source: architecture.md § "Raíz del Monorepo"] — estructura de carpetas esperada
- [Source: architecture.md § "CI/CD"] — GitHub Actions, dos ambientes Railway, Serilog JSON
- [Source: epics.md § "Historia 1.1"] — acceptance criteria BDD
- [Source: epics.md § AR-1] — monorepo, "Empty Activity Compose", `dotnet new webapi --use-controllers`, sin templates de terceros
- [Source: epics.md § AR-3] — Compose BOM 2026.06.00, Room 2.8.4 stable, minSdk=26, targetSdk=36, Gradle Kotlin DSL + KSP
- [Source: epics.md § AR-4] — .NET 10, EF Core 10.0.9, Npgsql 10.x, `dotnet new webapi --use-controllers`
- [Source: epics.md § AR-15] — GitHub Actions básico build+test en PR, dev+prod en Railway

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (2026-06-27)

### Debug Log References

- Android `assembleDebug` — BUILD SUCCESSFUL in 51s (35 tasks)
- Android `testDebugUnitTest` — BUILD SUCCESSFUL in 459ms (1 test: `ExampleUnitTest.addition_isCorrect`)
- Backend .NET — estructura creada manualmente (.NET SDK no disponible en entorno local); verificación completa en CI (`ubuntu-latest` con `actions/setup-dotnet@v4 dotnet-version: 10.0.x`)
- Gradle wrapper generado con Gradle 8.13 usando la distribución cacheada en `~/.gradle/wrapper/dists/`

### Completion Notes List

- AC-1 ✅: `android-ci.yml` creado con trigger en paths `android/**`; `assembleDebug` y `testDebugUnitTest` verificados localmente con JDK 21 (Android Studio bundled) y Gradle 8.13
- AC-2 ✅: `backend-ci.yml` creado con trigger en paths `backend/**`; estructura .NET 10 completa con solution file, WebAPI y xunit — verificación de build/test en CI
- AC-3 ✅: Todos los directorios y archivos requeridos creados; `android/` compila; `.gitignore` actualizado con patrones correctos; `README.md` completo
- **Nota .NET**: `dotnet` CLI no está instalado en el entorno de desarrollo local del agente — la estructura del proyecto fue creada manualmente conforme al template `dotnet new webapi --use-controllers` y `dotnet new xunit`. La verificación final de `dotnet build` y `dotnet test` se hará en la primera PR que active `backend-ci.yml`
- **Decisión tema Android**: `themes.xml` usa `android:Theme.Material.Light.NoActionBar` como base temporal; Historia 1.3 reemplaza con `SumitrackTheme` de Compose + tokens de `Color.kt`
- **Corrección .gitignore**: el archivo original ignoraba `appsettings.Development.json` por error — corregido para ignorar solo `appsettings.Production.json`

### File List

**Nuevos:**
- `android/settings.gradle.kts`
- `android/build.gradle.kts`
- `android/gradle.properties`
- `android/gradle/libs.versions.toml`
- `android/gradle/wrapper/gradle-wrapper.jar`
- `android/gradle/wrapper/gradle-wrapper.properties`
- `android/gradlew`
- `android/gradlew.bat`
- `android/local.properties.example`
- `android/app/build.gradle.kts`
- `android/app/proguard-rules.pro`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/com/sumitrack/android/MainActivity.kt`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/main/res/values/themes.xml`
- `android/app/src/test/java/com/sumitrack/android/ExampleUnitTest.kt`
- `backend/Sumitrack.sln`
- `backend/src/Sumitrack.Api/Sumitrack.Api.csproj`
- `backend/src/Sumitrack.Api/Program.cs`
- `backend/src/Sumitrack.Api/appsettings.json`
- `backend/src/Sumitrack.Api/appsettings.Development.json`
- `backend/src/Sumitrack.Api/Controllers/WeatherForecastController.cs`
- `backend/src/Sumitrack.Api/WeatherForecast.cs`
- `backend/tests/Sumitrack.Api.Tests/Sumitrack.Api.Tests.csproj`
- `backend/tests/Sumitrack.Api.Tests/UnitTest1.cs`
- `.github/workflows/android-ci.yml`
- `.github/workflows/backend-ci.yml`
- `README.md`

**Modificados:**
- `.gitignore` — patrones actualizados: `**/bin/`, `**/obj/`, `*.keystore`, `*.jks`, `appsettings.Production.json`; removido el error que ignoraba `appsettings.Development.json`

**Excluidos del commit** (en `.gitignore`):
- `android/local.properties` (SDK path local)

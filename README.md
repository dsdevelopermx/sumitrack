# Sumitrack

Plataforma de gestión de ventas a crédito y cobros para proveedores B2B en campo.

## Descripción

Sumitrack permite a proveedores registrar ventas, gestionar cobros y parcialidades, y operar sin internet — sincronizando con la nube al reconectar. Diseñado para un proveedor con 100+ clientes activos que opera en campo (ej. materiales para llanteras en Yucatán).

## Stack

| Capa | Tecnología |
|------|-----------|
| App móvil | Android (Kotlin + Jetpack Compose + Material 3) |
| API | .NET 10 (ASP.NET Core, controllers) |
| Base de datos | PostgreSQL (Railway) |
| ORM | EF Core 10.0.9 + Npgsql |
| Sync background | WorkManager |
| CI/CD | GitHub Actions |
| Hosting | Railway (dev + prod) |

## Estructura del Monorepo

```
sumitrack/
├── android/              ← App Android (Kotlin, Compose)
│   ├── app/              ← Módulo principal
│   └── gradle/           ← Versiones de dependencias (libs.versions.toml)
├── backend/              ← API .NET 10
│   ├── src/
│   │   └── Sumitrack.Api/   ← WebAPI principal
│   ├── tests/
│   │   └── Sumitrack.Api.Tests/   ← Tests unitarios
│   └── Sumitrack.sln
├── .github/
│   └── workflows/
│       ├── android-ci.yml   ← Build + tests Android en PR
│       └── backend-ci.yml   ← Build + tests .NET en PR
└── _bmad-output/         ← Artefactos de planificación BMad
```

## Requisitos de Desarrollo

### Android

- **Android Studio** Narwhal o superior
- **JDK 17 a 23** (recomendado 21) — Gradle 8.13 / AGP 8.10.1 no soportan JDK 24+. El JBR de Android Studio 2026.1 o
  posterior es **JDK 25 y no sirve**: el IDE se niega a sincronizar y por terminal AAPT2 no arranca. Instalar un JDK
  compatible (`brew install openjdk@21`) y fijarlo en *Settings → Build Tools → Gradle → Gradle JDK*.
- **Android SDK** API 26+ (Android 8.0+), API 36 recomendado para pruebas

### Backend

- **.NET 10 SDK** — [descargar aquí](https://dot.net)
- **PostgreSQL** local o Railway (dev)

## Desarrollo Local

### Android

```bash
cd android
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home   # ver Requisitos
./gradlew assembleDebug        # Compilar
./gradlew testDebugUnitTest    # Tests unitarios
```

Configuración de SDK: copiar `local.properties.example` → `local.properties` y ajustar la ruta.

#### Contra la API local (dispositivo o emulador)

El build debug apunta a `http://localhost:5600/`. Levantar la API solo con HTTP (con HTTPS redirigiría a un puerto que el
teléfono no tiene) y exponerla al dispositivo con `adb reverse`:

```bash
cd backend && dotnet run --project src/Sumitrack.Api --urls http://localhost:5600
adb reverse tcp:5600 tcp:5600
```

El puerto 5000 no sirve en macOS: lo ocupa AirPlay Receiver.

#### Pruebas end-to-end en dispositivo

Flujos de [Maestro](https://maestro.mobile.dev) contra la API real, más una verificación de sincronización contra
PostgreSQL. Ver [android/maestro/README.md](android/maestro/README.md).

```bash
cd android/maestro
./run.sh              # flujos 00-05
./verify-sync.sh      # lo creado en el teléfono llega al servidor
```

### Backend

```bash
cd backend
dotnet restore Sumitrack.sln
dotnet build Sumitrack.sln
dotnet test Sumitrack.sln
dotnet run --project src/Sumitrack.Api
```

Configuración de BD: ajustar la cadena de conexión en `src/Sumitrack.Api/appsettings.Development.json`.

## CI/CD

Los pipelines de GitHub Actions se activan automáticamente en Pull Requests:

- `android-ci.yml` — al cambiar archivos en `android/`
- `backend-ci.yml` — al cambiar archivos en `backend/`

## Arquitectura

Ver `_bmad-output/planning-artifacts/architecture/architecture.md` para la documentación completa de decisiones arquitectónicas.

## Estado del Proyecto

Ver `_bmad-output/implementation-artifacts/sprint-status.yaml` para el progreso de implementación.

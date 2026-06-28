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
- **JDK 17** (incluido en Android Studio)
- **Android SDK** API 26+ (Android 8.0+), API 36 recomendado para pruebas

### Backend

- **.NET 10 SDK** — [descargar aquí](https://dot.net)
- **PostgreSQL** local o Railway (dev)

## Desarrollo Local

### Android

```bash
cd android
./gradlew assembleDebug        # Compilar
./gradlew testDebugUnitTest    # Tests unitarios
```

Configuración de SDK: copiar `local.properties.example` → `local.properties` y ajustar la ruta.

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

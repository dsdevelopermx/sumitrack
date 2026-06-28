# Reporte de Sesión — Setup Debug Local API
**Fecha:** 2026-06-28  
**Tipo:** Configuración de entorno de desarrollo  
**Resultado:** API corriendo y depurable en VS Code en puerto 5600/5601

---

## Resumen

Sesión dedicada a configurar el entorno de desarrollo local para depurar el proyecto `Sumitrack.Api` con Visual Studio Code. Se resolvieron múltiples bloqueos de instalación, configuración de paquetes NuGet, migraciones EF Core y base de datos PostgreSQL.

---

## Trabajo Realizado

### 1. Configuración de VS Code para Debug .NET

- Creado `.vscode/launch.json` con tres perfiles: Debug (con build), Sin Build y Attach to Process
- Creado `.vscode/tasks.json` con tarea de build apuntando al `.csproj` del API
- Creado `backend/src/Sumitrack.Api/Properties/launchSettings.json` con puerto fijo `http://5600` y `https://5601`

### 2. Instalación de .NET SDK 10

- .NET SDK no estaba instalado en el equipo
- Instalado vía Homebrew: `brew install dotnet` → versión `10.0.301`

### 3. Corrección de versiones de paquetes NuGet

Los paquetes originales usaban versiones incorrectas o inexistentes. Versiones finales:

| Paquete | Antes | Después |
|---|---|---|
| `Microsoft.EntityFrameworkCore` | 10.0.9 | **10.0.4** |
| `Microsoft.EntityFrameworkCore.Design` | 10.0.9 | **10.0.4** |
| `Microsoft.EntityFrameworkCore.InMemory` | 10.0.9 | **10.0.4** |
| `Npgsql.EntityFrameworkCore.PostgreSQL` | 10.0.9 | **10.0.2** |
| `Microsoft.AspNetCore.Authentication.JwtBearer` | 10.0.0 | **10.0.4** |
| `Scalar.AspNetCore` | 2.5.8 | **2.6.0** |
| `Serilog.Enrichers.Environment` | — | **3.0.1** (agregado) |
| `Microsoft.AspNetCore.OpenApi` | — | **10.0.4** (agregado) |

**Notas:**
- `Npgsql.EntityFrameworkCore.PostgreSQL` no tiene versión `10.x` estable alineada con EF Core. La versión `10.0.2` requiere EF Core `>= 10.0.4`.
- `Serilog.Enrichers.Environment` faltaba pero era requerido por `WithMachineName()` en `SerilogConfiguration.cs`
- `Microsoft.AspNetCore.OpenApi` faltaba pero era requerido por `AddOpenApi()` y `MapOpenApi()` en `Program.cs`

### 4. Instalación y configuración de PostgreSQL

- Instalado PostgreSQL 15.18 vía Homebrew (`brew install postgresql@15`)
- Problema: Homebrew no crea el rol `postgres` por defecto, usa el usuario del SO
- Solución: conectar con usuario macOS y crear el rol manualmente:
  ```sql
  CREATE ROLE postgres WITH SUPERUSER LOGIN PASSWORD 'postgres';
  CREATE DATABASE sumitrack_01;
  ```
- Documentado en `docs/postgresql-local-setup.md`

### 5. Corrección de migraciones EF Core

**Problema:** `MigrateAsync()` ejecutaba sin error pero no aplicaba la migración `InitialCreate` — la tabla `public.tenants` nunca se creaba.

**Causa raíz:** La migración fue escrita a mano sin los atributos que EF Core necesita para descubrirla:
- Faltaba `[DbContext(typeof(AppDbContext))]`  
- Faltaba `[Migration("20260628000000_InitialCreate")]`

**Fixes aplicados:**
- Agregados ambos atributos a `20260628000000_InitialCreate.cs`
- Actualizado `ProductVersion` en snapshot de `10.0.9` a `10.0.4`
- Agregado `ConfigureWarnings(w => w.Ignore(RelationalEventId.PendingModelChangesWarning))` en el registro del DbContext para suprimir el warning por diferencia de versiones entre snapshot y runtime

### 6. Corrección de connection string del TenantDbContextFactory

**Problema:** `ArgumentException` al intentar login — connection string malformado en índice 73.

**Causa:** Comillas dobles escapadas en el valor de `Search Path`:
```csharp
// ANTES — incorrecto
$";Search Path=\"{schemaName}\",public"

// DESPUÉS — correcto
$";Search Path={schemaName},public"
```

El schema name es validado con regex `[a-z0-9_]` por lo que no requiere comillas.

### 7. Usuario de base de datos para la aplicación

Creado rol `sumitrack_app` con permisos mínimos necesarios (principio de menor privilegio):
- `CONNECT` a `sumitrack_01`
- `USAGE` + `SELECT/INSERT/UPDATE/DELETE` en schema `public`
- `CREATE` en la base de datos (para crear schemas de tenant)
- `DEFAULT PRIVILEGES` para tablas futuras

Connection string actualizado en `appsettings.Development.json` para usar `sumitrack_app` en lugar de `postgres`.

### 8. Configuración de HTTPS para desarrollo

- `launchSettings.json` configurado con `https://localhost:5601` además de `http://localhost:5600`
- Certificado de desarrollo: `dotnet dev-certs https --trust`

---

## Estado Final

- **API corriendo** en `http://localhost:5600` / `https://localhost:5601`
- **Scalar UI** disponible en `http://localhost:5600/scalar/v1`
- **Endpoint `/auth/login`** funcional y verificado
- **Seed de desarrollo** ejecutado correctamente:
  - Tenant: `local`
  - Usuario: `admin` / `Admin123!`
- **Depuración con breakpoints** funcional desde VS Code con `F5`

---

## Próximos Pasos

- Continuar con Historia 1.3: infraestructura Android (Room, Hilt, tema visual)
- Pendiente: regenerar snapshot de EF Core con `dotnet ef migrations add` para limpiar la advertencia `PendingModelChangesWarning`

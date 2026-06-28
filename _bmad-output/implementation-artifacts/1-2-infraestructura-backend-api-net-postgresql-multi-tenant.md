---
baseline_commit: ""
---

# Story 1.2: Infraestructura Backend — API .NET + PostgreSQL Multi-Tenant

Status: ready-for-dev

## Story

Como desarrollador,
quiero el backend .NET configurado con PostgreSQL, schema-per-tenant y JWT,
para que todos los endpoints futuros tengan una base segura y con aislamiento de datos garantizado.

## Acceptance Criteria

**AC-1** — Login con JWT

**Dado** que el servidor recibe `POST /api/v1/auth/login` con credenciales válidas (username + password)
**Cuando** `AuthService` valida el hash bcrypt contra la BD
**Entonces** devuelve `{ "token": "...", "expiresAt": "..." }` (respuesta directa, sin wrapper)
**Y** el JWT contiene los claims `sub` (user_id) y `tenant_id`

**AC-2** — Migraciones auto-apply en startup

**Dado** que se configura la conexión a PostgreSQL
**Cuando** la API inicia
**Entonces** EF Core aplica las migraciones automáticamente:
- Schema `public`: tabla `tenants` (id UUID, slug, schema_name, created_at)
- Schema `tenant_{id}`: tabla `users` (id, username, password_hash, tenant_id, created_at, updated_at)
**Y** `TenantSchemaResolver` ejecuta `SET search_path TO "{schema}", public` en cada operación de EF Core sobre datos de tenant

**AC-3** — Documentación Scalar

**Dado** que la API está en ejecución
**Cuando** se accede a `/scalar/v1`
**Entonces** la interfaz Scalar muestra los endpoints disponibles (al menos `POST /api/v1/auth/login`)

**AC-4** — Logging y errores estructurados

**Dado** que ocurre cualquier error en un endpoint
**Cuando** el servidor genera la respuesta
**Entonces** el log de Serilog contiene el stack trace completo en formato JSON
**Y** el cliente recibe `{ "errors": [{ "code": "...", "message": "..." }] }` sin detalles internos (AR-13)

## Tasks / Subtasks

- [ ] **T1: Actualizar paquetes NuGet** (todos los ACs)
  - [ ] Agregar a `Sumitrack.Api.csproj`: EF Core 10.0.9, Npgsql 10.x, JwtBearer, BCrypt.Net-Next 4.0.3, Serilog.AspNetCore, Serilog.Formatting.Compact, Scalar.AspNetCore
  - [ ] Agregar a `Sumitrack.Api.Tests.csproj`: Moq 4.20.x
  - [ ] Eliminar `WeatherForecastController.cs` y `WeatherForecast.cs` (placeholders de Historia 1.1)
  - [ ] Eliminar `UnitTest1.cs` (placeholder de Historia 1.1)

- [ ] **T2: Configurar Serilog** (AC-4)
  - [ ] Crear `Infrastructure/Logging/SerilogConfiguration.cs`
  - [ ] Configurar en `Program.cs`: `UseSerilog()` con `CompactJsonFormatter` para Railway
  - [ ] Actualizar `appsettings.json` con sección Serilog (MinimumLevel, Override)
  - [ ] Actualizar `appsettings.Development.json` con nivel Debug

- [ ] **T3: Configurar EF Core + DbContexts + TenantSchemaResolver** (AC-2)
  - [ ] Crear `Infrastructure/Auth/ITenantContext.cs` (interfaz scoped: TenantId, SchemaName)
  - [ ] Crear `Infrastructure/Auth/TenantContext.cs` (implementación scoped)
  - [ ] Crear `Infrastructure/Data/AppDbContext.cs` (PublicDbContext: solo tabla tenants en schema public)
  - [ ] Crear `Infrastructure/Data/TenantDbContext.cs` (contexto tenant: tabla users)
  - [ ] Crear `Infrastructure/Data/TenantSchemaInterceptor.cs` (DbConnectionInterceptor: ejecuta SET search_path)
  - [ ] Actualizar `appsettings.Development.json`: reemplazar placeholder `<local_password>` con contraseña de ejemplo documentada
  - [ ] Agregar sección `Jwt` a `appsettings.json`

- [ ] **T4: Crear migraciones iniciales manualmente** (AC-2)
  - [ ] Crear `Infrastructure/Data/Migrations/Public/20260628000000_InitialCreate.cs` (public.tenants)
  - [ ] Crear `Infrastructure/Data/Migrations/Public/AppDbContextModelSnapshot.cs`
  - [ ] Crear `Infrastructure/Data/Migrations/Tenant/20260628000001_InitialTenantSchema.cs` (users table)
  - [ ] Crear `Infrastructure/Data/Migrations/Tenant/TenantDbContextModelSnapshot.cs`

- [ ] **T5: Auto-apply migraciones + seed en Development** (AC-2)
  - [ ] Crear `Infrastructure/Extensions/ApplicationBuilderExtensions.cs` con `ApplyMigrationsAsync()`
  - [ ] Lógica: MigrateAsync en PublicDbContext, luego por cada tenant → MigrateAsync en TenantDbContext con schema
  - [ ] En Development: si no existe ningún tenant, crear tenant `local` + usuario `admin` / `Admin123!` (seed)
  - [ ] Llamar `app.ApplyMigrationsAsync()` en Program.cs antes de `app.Run()`

- [ ] **T6: Implementar modelos y DTOs** (AC-1)
  - [ ] Crear `Models/Entities/Tenant.cs`
  - [ ] Crear `Models/Entities/User.cs` (con `tenant_id` FK)
  - [ ] Crear `Models/Requests/LoginRequest.cs` (username, password)
  - [ ] Crear `Models/Responses/LoginResponse.cs` (token, expiresAt)
  - [ ] Crear `Models/Responses/ErrorResponse.cs` (errors: [{code, message}])

- [ ] **T7: Implementar AuthService** (AC-1)
  - [ ] Crear `Services/Auth/IAuthService.cs`
  - [ ] Crear `Services/Auth/AuthService.cs`:
    - `LoginAsync(LoginRequest)` → busca tenant por slug (de config `TenantSlug`), busca user por username, verifica bcrypt, genera JWT
    - Lanza excepción tipada si credenciales inválidas

- [ ] **T8: Implementar middleware JWT + TenantResolver** (AC-1, AC-2)
  - [ ] Crear `Infrastructure/Auth/TenantResolver.cs` (middleware: extrae tenant_id del JWT → busca en tenants → puebla ITenantContext)
  - [ ] Crear `Infrastructure/Auth/JwtMiddleware.cs` (si necesario — puede delegarse a `UseAuthentication()`)

- [ ] **T9: Implementar AuthController** (AC-1, AC-3)
  - [ ] Crear `Controllers/AuthController.cs` con `POST /api/v1/auth/login`
  - [ ] No validar lógica en el controller — delegar a AuthService
  - [ ] Retornar 200 con `LoginResponse` o 401 con `ErrorResponse`

- [ ] **T10: Middleware de errores custom** (AC-4)
  - [ ] Crear `Infrastructure/Extensions/ServiceCollectionExtensions.cs`
  - [ ] Crear middleware de excepción global que atrapa errores no controlados → retorna `ErrorResponse` + loguea con Serilog

- [ ] **T11: Configurar Scalar** (AC-3)
  - [ ] Agregar `AddOpenApi()` + `MapScalarApiReference()` en Program.cs
  - [ ] Verificar que `/scalar/v1` es accesible

- [ ] **T12: Actualizar Program.cs completo** (todos los ACs)
  - [ ] Serilog, EF Core, JWT auth, custom error middleware, Scalar, controllers
  - [ ] Orden de middleware correcto: Serilog → error handler → HTTPS redirect → auth → controllers

- [ ] **T13: Escribir tests unitarios AuthService** (AC-1)
  - [ ] Crear `tests/Sumitrack.Api.Tests/Services/AuthServiceTests.cs`
  - [ ] Test: credenciales válidas → retorna LoginResponse con token válido
  - [ ] Test: credenciales inválidas → lanza excepción apropiada
  - [ ] Test: JWT contiene claims sub y tenant_id

## Dev Notes

### Estado del backend después de Historia 1.1

**Archivos existentes que SE MODIFICAN en esta historia:**
- `backend/src/Sumitrack.Api/Sumitrack.Api.csproj` → agregar paquetes NuGet
- `backend/src/Sumitrack.Api/Program.cs` → reescribir completamente con toda la config
- `backend/src/Sumitrack.Api/appsettings.json` → agregar Serilog + JWT config
- `backend/src/Sumitrack.Api/appsettings.Development.json` → reemplazar placeholder, agregar Debug level
- `backend/tests/Sumitrack.Api.Tests/Sumitrack.Api.Tests.csproj` → agregar Moq

**Archivos existentes que SE ELIMINAN (placeholders Historia 1.1):**
- `backend/src/Sumitrack.Api/Controllers/WeatherForecastController.cs`
- `backend/src/Sumitrack.Api/WeatherForecast.cs`
- `backend/tests/Sumitrack.Api.Tests/UnitTest1.cs`

**Deferred item resuelto de Historia 1.1 (`deferred-work.md`):**
- `appsettings.Development.json` tenía `<local_password>` placeholder → esta historia lo reemplaza con password documentado

---

### T1 — Paquetes NuGet exactos

**`backend/src/Sumitrack.Api/Sumitrack.Api.csproj` — resultado final:**
```xml
<Project Sdk="Microsoft.NET.Sdk.Web">
  <PropertyGroup>
    <TargetFramework>net10.0</TargetFramework>
    <Nullable>enable</Nullable>
    <ImplicitUsings>enable</ImplicitUsings>
  </PropertyGroup>
  <ItemGroup>
    <PackageReference Include="Microsoft.EntityFrameworkCore" Version="10.0.9" />
    <PackageReference Include="Microsoft.EntityFrameworkCore.Design" Version="10.0.9">
      <IncludeAssets>runtime; build; native; contentfiles; analyzers; buildtransitive</IncludeAssets>
      <PrivateAssets>all</PrivateAssets>
    </PackageReference>
    <PackageReference Include="Npgsql.EntityFrameworkCore.PostgreSQL" Version="10.0.9" />
    <PackageReference Include="Microsoft.AspNetCore.Authentication.JwtBearer" Version="10.0.0" />
    <PackageReference Include="BCrypt.Net-Next" Version="4.0.3" />
    <PackageReference Include="Serilog.AspNetCore" Version="8.0.3" />
    <PackageReference Include="Serilog.Formatting.Compact" Version="3.0.0" />
    <PackageReference Include="Scalar.AspNetCore" Version="2.5.8" />
  </ItemGroup>
</Project>
```

**`backend/tests/Sumitrack.Api.Tests/Sumitrack.Api.Tests.csproj` — agregar:**
```xml
<PackageReference Include="Moq" Version="4.20.72" />
```

**NOTA de versiones**: Si algún paquete no existe en la versión exacta, usar la versión estable más cercana disponible en NuGet.org. Prioritario: EF Core 10.0.9 y Npgsql que sea compatible con EF Core 10. El CI (`backend-ci.yml`) con .NET 10 SDK verificará la resolución de dependencias.

---

### T2 — Serilog: JSON estructurado para Railway

**`Infrastructure/Logging/SerilogConfiguration.cs`:**
```csharp
using Serilog;
using Serilog.Formatting.Compact;

namespace Sumitrack.Api.Infrastructure.Logging;

public static class SerilogConfiguration
{
    public static void ConfigureSerilog(HostBuilderContext context, LoggerConfiguration config)
    {
        config
            .ReadFrom.Configuration(context.Configuration)
            .Enrich.FromLogContext()
            .Enrich.WithMachineName()
            .WriteTo.Console(new CompactJsonFormatter());
    }
}
```

**`appsettings.json` — secciones a agregar:**
```json
{
  "Logging": {
    "LogLevel": {
      "Default": "Information",
      "Microsoft.AspNetCore": "Warning"
    }
  },
  "AllowedHosts": "*",
  "Serilog": {
    "MinimumLevel": {
      "Default": "Information",
      "Override": {
        "Microsoft": "Warning",
        "System": "Warning"
      }
    }
  },
  "Jwt": {
    "Secret": "REPLACE_WITH_SECURE_64_CHAR_KEY_IN_RAILWAY_ENV_VAR",
    "ExpiresInDays": 365,
    "Issuer": "sumitrack",
    "Audience": "sumitrack-app"
  },
  "App": {
    "TenantSlug": "REPLACE_WITH_TENANT_SLUG_IN_RAILWAY_ENV_VAR"
  }
}
```

**`appsettings.Development.json` — reemplazar contenido completo:**
```json
{
  "ConnectionStrings": {
    "DefaultConnection": "Host=localhost;Database=sumitrack_01;Username=postgres;Password=postgres"
  },
  "Serilog": {
    "MinimumLevel": {
      "Default": "Debug",
      "Override": {
        "Microsoft.EntityFrameworkCore": "Information"
      }
    }
  },
  "Jwt": {
    "Secret": "dev_only_secret_key_minimum_32_characters_do_not_use_in_production",
    "ExpiresInDays": 365,
    "Issuer": "sumitrack",
    "Audience": "sumitrack-app"
  },
  "App": {
    "TenantSlug": "local"
  }
}
```

**IMPORTANTE**: `appsettings.Development.json` SÍ se commitea (contraseña de desarrollo local, no producción). `appsettings.Production.json` NO existe en el repo — los valores de producción vienen de Railway env vars con la misma estructura de llaves (doble guión bajo: `ConnectionStrings__DefaultConnection`, `Jwt__Secret`, `App__TenantSlug`).

---

### T3 — Modelo multi-tenant: ITenantContext + DbContexts

**Arquitectura de contextos:**
- `AppDbContext` (PublicDbContext): manage `public.tenants` SOLAMENTE — sin interceptor de schema
- `TenantDbContext`: manage tablas de tenant (users, etc.) — CON interceptor que ejecuta `SET search_path`

**`Infrastructure/Auth/ITenantContext.cs`:**
```csharp
namespace Sumitrack.Api.Infrastructure.Auth;

public interface ITenantContext
{
    Guid? TenantId { get; set; }
    string? SchemaName { get; set; }  // Formato: "tenant_{id:N}" (sin guiones)
    bool IsResolved { get; }
}
```

**`Infrastructure/Auth/TenantContext.cs`:**
```csharp
namespace Sumitrack.Api.Infrastructure.Auth;

public class TenantContext : ITenantContext
{
    public Guid? TenantId { get; set; }
    public string? SchemaName { get; set; }
    public bool IsResolved => TenantId.HasValue && !string.IsNullOrEmpty(SchemaName);
}
```

**`Infrastructure/Data/TenantSchemaInterceptor.cs`:**
```csharp
using Microsoft.EntityFrameworkCore.Diagnostics;
using Sumitrack.Api.Infrastructure.Auth;
using System.Data.Common;

namespace Sumitrack.Api.Infrastructure.Data;

public class TenantSchemaInterceptor : DbConnectionInterceptor
{
    private readonly ITenantContext _tenantContext;

    public TenantSchemaInterceptor(ITenantContext tenantContext)
    {
        _tenantContext = tenantContext;
    }

    public override async Task ConnectionOpenedAsync(
        DbConnection connection,
        ConnectionEndEventData eventData,
        CancellationToken cancellationToken = default)
    {
        if (_tenantContext.IsResolved)
        {
            await using var cmd = connection.CreateCommand();
            cmd.CommandText = $"SET search_path TO \"{_tenantContext.SchemaName}\", public";
            await cmd.ExecuteNonQueryAsync(cancellationToken);
        }
    }

    public override void ConnectionOpened(DbConnection connection, ConnectionEndEventData eventData)
    {
        if (_tenantContext.IsResolved)
        {
            using var cmd = connection.CreateCommand();
            cmd.CommandText = $"SET search_path TO \"{_tenantContext.SchemaName}\", public";
            cmd.ExecuteNonQuery();
        }
    }
}
```

**`Infrastructure/Data/AppDbContext.cs` (schema public — sin interceptor de tenant):**
```csharp
using Microsoft.EntityFrameworkCore;
using Sumitrack.Api.Models.Entities;

namespace Sumitrack.Api.Infrastructure.Data;

public class AppDbContext : DbContext
{
    public AppDbContext(DbContextOptions<AppDbContext> options) : base(options) { }

    public DbSet<Tenant> Tenants => Set<Tenant>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.HasDefaultSchema("public");
        modelBuilder.Entity<Tenant>(entity =>
        {
            entity.ToTable("tenants", "public");
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Id).ValueGeneratedOnAdd();
            entity.Property(e => e.Slug).HasMaxLength(50).IsRequired();
            entity.HasIndex(e => e.Slug).IsUnique();
            entity.Property(e => e.SchemaName).HasMaxLength(63).IsRequired();
            entity.HasIndex(e => e.SchemaName).IsUnique();
            entity.Property(e => e.CreatedAt).HasDefaultValueSql("NOW()");
        });
    }
}
```

**`Infrastructure/Data/TenantDbContext.cs` (schema de tenant — con interceptor):**
```csharp
using Microsoft.EntityFrameworkCore;
using Sumitrack.Api.Models.Entities;

namespace Sumitrack.Api.Infrastructure.Data;

public class TenantDbContext : DbContext
{
    public TenantDbContext(DbContextOptions<TenantDbContext> options) : base(options) { }

    public DbSet<User> Users => Set<User>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<User>(entity =>
        {
            entity.ToTable("users");
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Username).HasMaxLength(100).IsRequired();
            entity.HasIndex(e => e.Username).IsUnique();
            entity.Property(e => e.PasswordHash).HasMaxLength(255).IsRequired();
            entity.Property(e => e.TenantId).IsRequired();
            entity.Property(e => e.CreatedAt).HasDefaultValueSql("NOW()");
            entity.Property(e => e.UpdatedAt).HasDefaultValueSql("NOW()");
        });
    }
}
```

---

### T4 — Migraciones EF Core (creadas manualmente)

**RAZÓN**: El .NET SDK no está disponible en el entorno de desarrollo del agente. Las migraciones se crean manualmente como código C#.

**Estructura de directorios:**
```
Infrastructure/Data/Migrations/
├── Public/
│   ├── 20260628000000_InitialCreate.cs
│   └── AppDbContextModelSnapshot.cs
└── Tenant/
    ├── 20260628000001_InitialTenantSchema.cs
    └── TenantDbContextModelSnapshot.cs
```

**`Infrastructure/Data/Migrations/Public/20260628000000_InitialCreate.cs`:**
```csharp
using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Sumitrack.Api.Infrastructure.Data.Migrations.Public;

public partial class InitialCreate : Migration
{
    protected override void Up(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.EnsureSchema(name: "public");

        migrationBuilder.CreateTable(
            name: "tenants",
            schema: "public",
            columns: table => new
            {
                id = table.Column<Guid>(type: "uuid", nullable: false, defaultValueSql: "gen_random_uuid()"),
                slug = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false),
                schema_name = table.Column<string>(type: "character varying(63)", maxLength: 63, nullable: false),
                created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false, defaultValueSql: "NOW()")
            },
            constraints: table =>
            {
                table.PrimaryKey("pk_tenants", x => x.id);
            });

        migrationBuilder.CreateIndex(
            name: "ix_tenants_slug",
            schema: "public",
            table: "tenants",
            column: "slug",
            unique: true);

        migrationBuilder.CreateIndex(
            name: "ix_tenants_schema_name",
            schema: "public",
            table: "tenants",
            column: "schema_name",
            unique: true);
    }

    protected override void Down(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.DropTable(name: "tenants", schema: "public");
    }
}
```

**`Infrastructure/Data/Migrations/Tenant/20260628000001_InitialTenantSchema.cs`:**
```csharp
using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Sumitrack.Api.Infrastructure.Data.Migrations.Tenant;

public partial class InitialTenantSchema : Migration
{
    protected override void Up(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.CreateTable(
            name: "users",
            columns: table => new
            {
                id = table.Column<Guid>(type: "uuid", nullable: false),
                username = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                password_hash = table.Column<string>(type: "character varying(255)", maxLength: 255, nullable: false),
                tenant_id = table.Column<Guid>(type: "uuid", nullable: false),
                created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false, defaultValueSql: "NOW()"),
                updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false, defaultValueSql: "NOW()")
            },
            constraints: table =>
            {
                table.PrimaryKey("pk_users", x => x.id);
            });

        migrationBuilder.CreateIndex(
            name: "ix_users_username",
            table: "users",
            column: "username",
            unique: true);
    }

    protected override void Down(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.DropTable(name: "users");
    }
}
```

**ModelSnapshots**: El dev agent debe crear `AppDbContextModelSnapshot.cs` y `TenantDbContextModelSnapshot.cs` siguiendo el patrón estándar de EF Core (clase que extiende `ModelSnapshot` con el modelo completo). Incluir ambas entidades en los snapshots correspondientes.

**ALTERNATIVA si las migraciones manuales no compilan**: Configurar `database.EnsureCreated()` en Development como fallback (crea el esquema directamente sin historial de migraciones). Solo para dev — en producción usar migrations completas.

---

### T5 — Auto-apply migraciones + Seed Development

**`Infrastructure/Extensions/ApplicationBuilderExtensions.cs`:**
```csharp
using Microsoft.EntityFrameworkCore;
using Sumitrack.Api.Infrastructure.Data;
using Sumitrack.Api.Models.Entities;

namespace Sumitrack.Api.Infrastructure.Extensions;

public static class ApplicationBuilderExtensions
{
    public static async Task ApplyMigrationsAsync(this WebApplication app)
    {
        using var scope = app.Services.CreateScope();
        var logger = scope.ServiceProvider.GetRequiredService<ILogger<Program>>();

        // 1. Public schema migrations
        var publicCtx = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        await publicCtx.Database.MigrateAsync();
        logger.LogInformation("Public schema migrations applied");

        // 2. Tenant schema migrations (for each existing tenant)
        var tenants = await publicCtx.Tenants.ToListAsync();
        var tenantCtxFactory = scope.ServiceProvider.GetRequiredService<ITenantDbContextFactory>();

        foreach (var tenant in tenants)
        {
            var tenantCtx = tenantCtxFactory.Create(tenant.SchemaName);
            await tenantCtx.Database.MigrateAsync();
            logger.LogInformation("Tenant schema migrations applied for {TenantSlug}", tenant.Slug);
        }

        // 3. Development seed
        if (app.Environment.IsDevelopment() && !tenants.Any())
        {
            await SeedDevelopmentAsync(publicCtx, tenantCtxFactory, logger);
        }
    }

    private static async Task SeedDevelopmentAsync(
        AppDbContext publicCtx,
        ITenantDbContextFactory tenantCtxFactory,
        ILogger logger)
    {
        var tenantId = Guid.NewGuid();
        var schemaName = $"tenant_{tenantId:N}";

        var tenant = new Tenant
        {
            Id = tenantId,
            Slug = "local",
            SchemaName = schemaName
        };
        publicCtx.Tenants.Add(tenant);
        await publicCtx.SaveChangesAsync();

        // Create tenant schema
        await publicCtx.Database.ExecuteSqlRawAsync(
            $"CREATE SCHEMA IF NOT EXISTS \"{schemaName}\"");

        var tenantCtx = tenantCtxFactory.Create(schemaName);
        await tenantCtx.Database.MigrateAsync();

        var adminUser = new User
        {
            Id = Guid.NewGuid(),
            Username = "admin",
            PasswordHash = BCrypt.Net.BCrypt.HashPassword("Admin123!"),
            TenantId = tenantId
        };
        tenantCtx.Users.Add(adminUser);
        await tenantCtx.SaveChangesAsync();

        logger.LogWarning(
            "DEVELOPMENT SEED: Tenant 'local' + user 'admin'/'Admin123!' created. CHANGE IN PRODUCTION.");
    }
}
```

**`Infrastructure/Data/ITenantDbContextFactory.cs`:**
```csharp
namespace Sumitrack.Api.Infrastructure.Data;

public interface ITenantDbContextFactory
{
    TenantDbContext Create(string schemaName);
}
```

**`Infrastructure/Data/TenantDbContextFactory.cs`:**
```csharp
using Microsoft.EntityFrameworkCore;

namespace Sumitrack.Api.Infrastructure.Data;

public class TenantDbContextFactory : ITenantDbContextFactory
{
    private readonly IConfiguration _configuration;

    public TenantDbContextFactory(IConfiguration configuration)
    {
        _configuration = configuration;
    }

    public TenantDbContext Create(string schemaName)
    {
        // Uses a temporary TenantContext to set search_path
        var connectionString = _configuration.GetConnectionString("DefaultConnection")
            ?? throw new InvalidOperationException("ConnectionStrings:DefaultConnection not configured");

        var options = new DbContextOptionsBuilder<TenantDbContext>()
            .UseNpgsql(connectionString, npgsql =>
                npgsql.MigrationsHistoryTable("__ef_migrations_history", schemaName))
            .Options;

        var ctx = new TenantDbContext(options);

        // Set search_path directly via raw SQL before migrating
        ctx.Database.ExecuteSqlRaw($"SET search_path TO \"{schemaName}\", public");
        return ctx;
    }
}
```

---

### T6 — Entidades y DTOs

**`Models/Entities/Tenant.cs`:**
```csharp
namespace Sumitrack.Api.Models.Entities;

public class Tenant
{
    public Guid Id { get; set; }
    public string Slug { get; set; } = string.Empty;
    public string SchemaName { get; set; } = string.Empty;
    public DateTime CreatedAt { get; set; }
}
```

**`Models/Entities/User.cs`:**
```csharp
namespace Sumitrack.Api.Models.Entities;

public class User
{
    public Guid Id { get; set; }
    public string Username { get; set; } = string.Empty;
    public string PasswordHash { get; set; } = string.Empty;
    public Guid TenantId { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}
```

**`Models/Requests/LoginRequest.cs`:**
```csharp
using System.ComponentModel.DataAnnotations;

namespace Sumitrack.Api.Models.Requests;

public class LoginRequest
{
    [Required]
    public string Username { get; set; } = string.Empty;

    [Required]
    public string Password { get; set; } = string.Empty;
}
```

**`Models/Responses/LoginResponse.cs`:**
```csharp
namespace Sumitrack.Api.Models.Responses;

public class LoginResponse
{
    public string Token { get; set; } = string.Empty;
    public DateTime ExpiresAt { get; set; }
}
```

**`Models/Responses/ErrorResponse.cs`:**
```csharp
namespace Sumitrack.Api.Models.Responses;

public class ErrorResponse
{
    public List<ApiError> Errors { get; set; } = [];
}

public class ApiError
{
    public string Code { get; set; } = string.Empty;
    public string Message { get; set; } = string.Empty;
}
```

---

### T7 — AuthService

**`Services/Auth/IAuthService.cs`:**
```csharp
using Sumitrack.Api.Models.Requests;
using Sumitrack.Api.Models.Responses;

namespace Sumitrack.Api.Services.Auth;

public interface IAuthService
{
    Task<LoginResponse> LoginAsync(LoginRequest request, CancellationToken cancellationToken = default);
}
```

**`Services/Auth/AuthService.cs`:**
```csharp
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;
using Sumitrack.Api.Infrastructure.Data;
using Sumitrack.Api.Models.Requests;
using Sumitrack.Api.Models.Responses;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;

namespace Sumitrack.Api.Services.Auth;

public class AuthService : IAuthService
{
    private readonly AppDbContext _publicCtx;
    private readonly ITenantDbContextFactory _tenantCtxFactory;
    private readonly IConfiguration _configuration;
    private readonly ILogger<AuthService> _logger;

    public AuthService(
        AppDbContext publicCtx,
        ITenantDbContextFactory tenantCtxFactory,
        IConfiguration configuration,
        ILogger<AuthService> logger)
    {
        _publicCtx = publicCtx;
        _tenantCtxFactory = tenantCtxFactory;
        _configuration = configuration;
        _logger = logger;
    }

    public async Task<LoginResponse> LoginAsync(LoginRequest request, CancellationToken cancellationToken = default)
    {
        // Resolve tenant from configured slug (v1: one tenant per deployment)
        var tenantSlug = _configuration["App:TenantSlug"]
            ?? throw new InvalidOperationException("App:TenantSlug not configured");

        var tenant = await _publicCtx.Tenants
            .FirstOrDefaultAsync(t => t.Slug == tenantSlug, cancellationToken)
            ?? throw new UnauthorizedAccessException("INVALID_CREDENTIALS");

        // Query user in tenant schema
        var tenantCtx = _tenantCtxFactory.Create(tenant.SchemaName);
        var user = await tenantCtx.Users
            .FirstOrDefaultAsync(u => u.Username == request.Username, cancellationToken);

        if (user is null || !BCrypt.Net.BCrypt.Verify(request.Password, user.PasswordHash))
        {
            _logger.LogWarning("Failed login attempt for username {Username}", request.Username);
            throw new UnauthorizedAccessException("INVALID_CREDENTIALS");
        }

        var token = GenerateJwt(user.Id, tenant.Id);
        var expiresInDays = _configuration.GetValue<int>("Jwt:ExpiresInDays", 365);
        var expiresAt = DateTime.UtcNow.AddDays(expiresInDays);

        return new LoginResponse { Token = token, ExpiresAt = expiresAt };
    }

    private string GenerateJwt(Guid userId, Guid tenantId)
    {
        var secret = _configuration["Jwt:Secret"]
            ?? throw new InvalidOperationException("Jwt:Secret not configured");
        var issuer = _configuration["Jwt:Issuer"] ?? "sumitrack";
        var audience = _configuration["Jwt:Audience"] ?? "sumitrack-app";
        var expiresInDays = _configuration.GetValue<int>("Jwt:ExpiresInDays", 365);

        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(secret));
        var creds = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);

        var claims = new[]
        {
            new Claim(JwtRegisteredClaimNames.Sub, userId.ToString()),
            new Claim("tenant_id", tenantId.ToString()),
            new Claim(JwtRegisteredClaimNames.Jti, Guid.NewGuid().ToString())
        };

        var token = new JwtSecurityToken(
            issuer: issuer,
            audience: audience,
            claims: claims,
            expires: DateTime.UtcNow.AddDays(expiresInDays),
            signingCredentials: creds);

        return new JwtSecurityTokenHandler().WriteToken(token);
    }
}
```

---

### T8 — JWT Middleware + TenantResolver

El `JwtMiddleware.cs` NO es necesario como middleware custom — `UseAuthentication()` + `UseAuthorization()` del framework maneja la validación del token. El `TenantResolver` puebla `ITenantContext` para requests que ya pasaron autenticación.

**`Infrastructure/Auth/TenantResolver.cs` (middleware):**
```csharp
using Sumitrack.Api.Infrastructure.Data;
using System.Security.Claims;

namespace Sumitrack.Api.Infrastructure.Auth;

public class TenantResolverMiddleware
{
    private readonly RequestDelegate _next;

    public TenantResolverMiddleware(RequestDelegate next) => _next = next;

    public async Task InvokeAsync(HttpContext context, ITenantContext tenantContext, AppDbContext publicCtx)
    {
        var tenantIdClaim = context.User.FindFirst("tenant_id")?.Value;
        if (tenantIdClaim != null && Guid.TryParse(tenantIdClaim, out var tenantId))
        {
            var tenant = await publicCtx.Tenants.FindAsync(tenantId);
            if (tenant != null)
            {
                tenantContext.TenantId = tenantId;
                tenantContext.SchemaName = tenant.SchemaName;
            }
        }
        await _next(context);
    }
}
```

---

### T9 — AuthController

**`Controllers/AuthController.cs`:**
```csharp
using Microsoft.AspNetCore.Mvc;
using Sumitrack.Api.Models.Requests;
using Sumitrack.Api.Models.Responses;
using Sumitrack.Api.Services.Auth;

namespace Sumitrack.Api.Controllers;

[ApiController]
[Route("api/v1/auth")]
public class AuthController : ControllerBase
{
    private readonly IAuthService _authService;

    public AuthController(IAuthService authService)
    {
        _authService = authService;
    }

    [HttpPost("login")]
    [ProducesResponseType(typeof(LoginResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status401Unauthorized)]
    public async Task<IActionResult> Login([FromBody] LoginRequest request, CancellationToken cancellationToken)
    {
        var response = await _authService.LoginAsync(request, cancellationToken);
        return Ok(response);
    }
}
```

**NOTA**: La excepción `UnauthorizedAccessException` del AuthService es atrapada por el middleware de errores global y convertida a 401 + ErrorResponse. NO poner try/catch en el controller.

---

### T10 — Middleware de errores global

**`Infrastructure/Extensions/ServiceCollectionExtensions.cs`:**
```csharp
using Sumitrack.Api.Infrastructure.Auth;
using Sumitrack.Api.Infrastructure.Data;
using Sumitrack.Api.Services.Auth;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using System.Text;

namespace Sumitrack.Api.Infrastructure.Extensions;

public static class ServiceCollectionExtensions
{
    public static IServiceCollection AddSumitrackServices(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        // DbContexts
        var connStr = configuration.GetConnectionString("DefaultConnection")!;
        services.AddDbContext<AppDbContext>(opt =>
            opt.UseNpgsql(connStr));

        services.AddScoped<ITenantContext, TenantContext>();
        services.AddScoped<TenantSchemaInterceptor>();
        services.AddDbContext<TenantDbContext>((sp, opt) =>
        {
            var interceptor = sp.GetRequiredService<TenantSchemaInterceptor>();
            opt.UseNpgsql(connStr).AddInterceptors(interceptor);
        });
        services.AddScoped<ITenantDbContextFactory, TenantDbContextFactory>();

        // Auth services
        services.AddScoped<IAuthService, AuthService>();

        // JWT authentication
        var jwtSecret = configuration["Jwt:Secret"]!;
        services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
            .AddJwtBearer(opt =>
            {
                opt.TokenValidationParameters = new TokenValidationParameters
                {
                    ValidateIssuerSigningKey = true,
                    IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtSecret)),
                    ValidateIssuer = true,
                    ValidIssuer = configuration["Jwt:Issuer"],
                    ValidateAudience = true,
                    ValidAudience = configuration["Jwt:Audience"],
                    ValidateLifetime = true,
                    ClockSkew = TimeSpan.Zero
                };
            });

        return services;
    }
}
```

**Middleware de error global** (anónimo, va directo en Program.cs o en extension):
```csharp
// Middleware de errores — captura excepciones y retorna ErrorResponse JSON
app.Use(async (context, next) =>
{
    try
    {
        await next(context);
    }
    catch (UnauthorizedAccessException ex)
    {
        var logger = context.RequestServices.GetRequiredService<ILogger<Program>>();
        logger.LogWarning("Unauthorized: {Message}", ex.Message);
        context.Response.StatusCode = 401;
        context.Response.ContentType = "application/json";
        await context.Response.WriteAsJsonAsync(new ErrorResponse
        {
            Errors = [new ApiError { Code = ex.Message, Message = "No autorizado." }]
        });
    }
    catch (Exception ex)
    {
        var logger = context.RequestServices.GetRequiredService<ILogger<Program>>();
        logger.LogError(ex, "Unhandled exception");
        context.Response.StatusCode = 500;
        context.Response.ContentType = "application/json";
        await context.Response.WriteAsJsonAsync(new ErrorResponse
        {
            Errors = [new ApiError { Code = "INTERNAL_ERROR", Message = "Ocurrió un error inesperado." }]
        });
    }
});
```

---

### T11 — Program.cs final

```csharp
using Serilog;
using Scalar.AspNetCore;
using Sumitrack.Api.Infrastructure.Auth;
using Sumitrack.Api.Infrastructure.Extensions;
using Sumitrack.Api.Infrastructure.Logging;
using Sumitrack.Api.Models.Responses;

// Serilog bootstrap logger (antes de host build)
Log.Logger = new LoggerConfiguration()
    .WriteTo.Console()
    .CreateBootstrapLogger();

try
{
    var builder = WebApplication.CreateBuilder(args);

    // Serilog
    builder.Host.UseSerilog(SerilogConfiguration.ConfigureSerilog);

    // Controllers + OpenAPI
    builder.Services.AddControllers();
    builder.Services.AddOpenApi();

    // Sumitrack services (EF Core, JWT, Auth)
    builder.Services.AddSumitrackServices(builder.Configuration);

    var app = builder.Build();

    // Error middleware (primero en el pipeline)
    app.Use(async (context, next) =>
    {
        try { await next(context); }
        catch (UnauthorizedAccessException ex)
        {
            var logger = context.RequestServices.GetRequiredService<ILogger<Program>>();
            logger.LogWarning("Unauthorized: {Code}", ex.Message);
            context.Response.StatusCode = 401;
            context.Response.ContentType = "application/json";
            await context.Response.WriteAsJsonAsync(new ErrorResponse
            {
                Errors = [new ApiError { Code = ex.Message, Message = "No autorizado." }]
            });
        }
        catch (Exception ex)
        {
            var logger = context.RequestServices.GetRequiredService<ILogger<Program>>();
            logger.LogError(ex, "Unhandled exception");
            context.Response.StatusCode = 500;
            context.Response.ContentType = "application/json";
            await context.Response.WriteAsJsonAsync(new ErrorResponse
            {
                Errors = [new ApiError { Code = "INTERNAL_ERROR", Message = "Ocurrió un error inesperado." }]
            });
        }
    });

    // Scalar API docs (solo en Development)
    if (app.Environment.IsDevelopment())
    {
        app.MapOpenApi();
        app.MapScalarApiReference();
    }

    app.UseHttpsRedirection();
    app.UseSerilogRequestLogging();

    app.UseAuthentication();
    // Tenant resolver middleware — después de autenticación para tener los claims
    app.UseMiddleware<TenantResolverMiddleware>();
    app.UseAuthorization();

    app.MapControllers();

    // Auto-apply migrations + seed Development
    await app.ApplyMigrationsAsync();

    app.Run();
}
catch (Exception ex)
{
    Log.Fatal(ex, "Application startup failed");
}
finally
{
    Log.CloseAndFlush();
}
```

**NOTA sobre Scalar**: Si `MapScalarApiReference()` produce `/scalar/v1` depende de la versión de Scalar.AspNetCore. Verificar que el path del AC (`/scalar/v1`) coincide. Si el path por defecto es diferente, ajustar con:
```csharp
app.MapScalarApiReference(opt => opt.EndpointPathPrefix = "/scalar/{documentName}");
```

---

### T12 — ModelSnapshots de EF Core

Los snapshots son archivos C# generados por EF Core que describen el modelo en un momento dado. Deben crearse para ambos DbContexts.

**`Infrastructure/Data/Migrations/Public/AppDbContextModelSnapshot.cs`** — crear siguiendo el patrón de EF Core ModelSnapshot. El dev agent puede generar este archivo ejecutando (si tiene .NET SDK disponible):
```bash
cd backend
dotnet ef migrations add InitialCreate --context AppDbContext --output-dir Infrastructure/Data/Migrations/Public
```

Si no tiene SDK local, crear el snapshot manualmente referenciando la entity `Tenant` con sus columnas. El CI con .NET 10 SDK verificará que el snapshot está actualizado.

---

### T13 — Tests de AuthService

**`tests/Sumitrack.Api.Tests/Services/AuthServiceTests.cs`:**
```csharp
using Moq;
using Sumitrack.Api.Infrastructure.Data;
using Sumitrack.Api.Models.Requests;
using Sumitrack.Api.Models.Entities;
using Sumitrack.Api.Services.Auth;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace Sumitrack.Api.Tests.Services;

public class AuthServiceTests
{
    // NOTE: AuthService usa AppDbContext + TenantDbContextFactory.
    // Para tests unitarios, usar InMemory provider o mockar con Moq.
    // Los tests de integración con BD real van en una historia posterior.

    [Fact]
    public async Task LoginAsync_ValidCredentials_ReturnsTokenWithClaims()
    {
        // Arrange
        var tenantId = Guid.NewGuid();
        var userId = Guid.NewGuid();
        var passwordHash = BCrypt.Net.BCrypt.HashPassword("TestPass123!");

        // Configuración en memoria para el test
        var options = new DbContextOptionsBuilder<AppDbContext>()
            .UseInMemoryDatabase(databaseName: Guid.NewGuid().ToString())
            .Options;

        using var publicCtx = new AppDbContext(options);
        publicCtx.Tenants.Add(new Tenant
        {
            Id = tenantId,
            Slug = "test",
            SchemaName = $"tenant_{tenantId:N}"
        });
        await publicCtx.SaveChangesAsync();

        var mockFactory = new Mock<ITenantDbContextFactory>();
        var tenantOptions = new DbContextOptionsBuilder<TenantDbContext>()
            .UseInMemoryDatabase(databaseName: Guid.NewGuid().ToString())
            .Options;
        var tenantCtx = new TenantDbContext(tenantOptions);
        tenantCtx.Users.Add(new User
        {
            Id = userId,
            Username = "testuser",
            PasswordHash = passwordHash,
            TenantId = tenantId
        });
        await tenantCtx.SaveChangesAsync();
        mockFactory.Setup(f => f.Create(It.IsAny<string>())).Returns(tenantCtx);

        var config = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["App:TenantSlug"] = "test",
                ["Jwt:Secret"] = "test_secret_key_minimum_32_characters_for_hmac256",
                ["Jwt:ExpiresInDays"] = "365",
                ["Jwt:Issuer"] = "sumitrack",
                ["Jwt:Audience"] = "sumitrack-app"
            })
            .Build();

        var logger = new Mock<ILogger<AuthService>>();
        var service = new AuthService(publicCtx, mockFactory.Object, config, logger.Object);

        // Act
        var result = await service.LoginAsync(new LoginRequest
        {
            Username = "testuser",
            Password = "TestPass123!"
        });

        // Assert
        Assert.NotEmpty(result.Token);
        Assert.True(result.ExpiresAt > DateTime.UtcNow);

        // Verificar claims del JWT
        var handler = new System.IdentityModel.Tokens.Jwt.JwtSecurityTokenHandler();
        var jwt = handler.ReadJwtToken(result.Token);
        Assert.Equal(userId.ToString(), jwt.Subject);
        Assert.Contains(jwt.Claims, c => c.Type == "tenant_id" && c.Value == tenantId.ToString());
    }

    [Fact]
    public async Task LoginAsync_InvalidPassword_ThrowsUnauthorized()
    {
        // Arrange — misma configuración pero password incorrecta
        var tenantId = Guid.NewGuid();
        var options = new DbContextOptionsBuilder<AppDbContext>()
            .UseInMemoryDatabase(databaseName: Guid.NewGuid().ToString())
            .Options;

        using var publicCtx = new AppDbContext(options);
        publicCtx.Tenants.Add(new Tenant
        {
            Id = tenantId,
            Slug = "test",
            SchemaName = $"tenant_{tenantId:N}"
        });
        await publicCtx.SaveChangesAsync();

        var tenantOptions = new DbContextOptionsBuilder<TenantDbContext>()
            .UseInMemoryDatabase(databaseName: Guid.NewGuid().ToString())
            .Options;
        var tenantCtx = new TenantDbContext(tenantOptions);
        tenantCtx.Users.Add(new User
        {
            Id = Guid.NewGuid(),
            Username = "testuser",
            PasswordHash = BCrypt.Net.BCrypt.HashPassword("CorrectPassword"),
            TenantId = tenantId
        });
        await tenantCtx.SaveChangesAsync();

        var mockFactory = new Mock<ITenantDbContextFactory>();
        mockFactory.Setup(f => f.Create(It.IsAny<string>())).Returns(tenantCtx);

        var config = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["App:TenantSlug"] = "test",
                ["Jwt:Secret"] = "test_secret_key_minimum_32_characters_for_hmac256",
                ["Jwt:ExpiresInDays"] = "365",
                ["Jwt:Issuer"] = "sumitrack",
                ["Jwt:Audience"] = "sumitrack-app"
            })
            .Build();

        var logger = new Mock<ILogger<AuthService>>();
        var service = new AuthService(publicCtx, mockFactory.Object, config, logger.Object);

        // Act & Assert
        await Assert.ThrowsAsync<UnauthorizedAccessException>(() =>
            service.LoginAsync(new LoginRequest
            {
                Username = "testuser",
                Password = "WrongPassword"
            }));
    }
}
```

**NOTA**: `UseInMemoryDatabase` requiere `Microsoft.EntityFrameworkCore.InMemory`. Agregar al test project:
```xml
<PackageReference Include="Microsoft.EntityFrameworkCore.InMemory" Version="10.0.9" />
```

---

### Reglas críticas: qué NO hacer en esta historia

- **NO** crear entidades de negocio (Client, Product, Sale, etc.) — eso es Historia 2.x y 3.x
- **NO** crear endpoints de sync — eso es Historia 4.x
- **NO** cambiar nada en `android/` — eso es Historia 1.3+
- **NO** usar `Double` o `Float` para montos — `NUMERIC(18,6)` en PostgreSQL, `decimal` en C# (AR-17)
- **NO** poner lógica de negocio en controllers — solo en Services (AR-20)
- **NO** devolver wrappers en las responses — respuesta directa (AR-16)
- **NO** incluir detalles internos en errores al cliente — solo code y message (AR-13)
- **NO** commitear `appsettings.Production.json` — los valores de producción van en Railway env vars

### Convenciones de naming (AR-16)

- Tablas PostgreSQL: `snake_case` inglés plural (ej: `tenants`, `users`)
- Columnas: `snake_case` inglés (ej: `tenant_id`, `created_at`, `password_hash`)
- FK: `fk_{entidad}` (ej: `fk_tenant`)
- Índices: `idx_{tabla}_{columna}` (ej: `idx_users_username`)
- JSON responses: `camelCase` (EF Core + `System.Text.Json` por defecto)
- API endpoints: inglés, sustantivos plurales, kebab-case (ej: `/api/v1/auth/login`)

### Precisión monetaria (AR-17)

Aunque esta historia no crea campos monetarios, establecer la convención para futuras historias:
- PostgreSQL: `NUMERIC(18,6)` — NUNCA `FLOAT` o `DOUBLE PRECISION`
- EF Core: `[Column(TypeName = "numeric(18,6)")]` + `[Precision(18, 6)]`
- C#: `decimal` — NUNCA `double` o `float`

### Railway env vars para producción

Configurar en Railway dashboard (NO commitear):
```
ConnectionStrings__DefaultConnection = Host=...;Database=sumitrack_01;Username=...;Password=...
Jwt__Secret = <64-char-random-secret>
Jwt__ExpiresInDays = 365
Jwt__Issuer = sumitrack
Jwt__Audience = sumitrack-app
App__TenantSlug = <slug-del-tenant-real>
ASPNETCORE_ENVIRONMENT = Production
```

### Project Structure Notes

**Alineación con arquitectura (`architecture.md § "Árbol Completo — Backend .NET"`):**

Esta historia crea la siguiente estructura (subconjunto del árbol completo):
```
backend/src/Sumitrack.Api/
├── Controllers/
│   └── AuthController.cs                    ← NEW (reemplaza WeatherForecastController)
├── Infrastructure/
│   ├── Auth/
│   │   ├── ITenantContext.cs                ← NEW
│   │   ├── TenantContext.cs                 ← NEW
│   │   └── TenantResolver.cs               ← NEW (middleware)
│   ├── Data/
│   │   ├── AppDbContext.cs                  ← NEW
│   │   ├── TenantDbContext.cs              ← NEW
│   │   ├── TenantSchemaInterceptor.cs      ← NEW
│   │   ├── ITenantDbContextFactory.cs      ← NEW
│   │   ├── TenantDbContextFactory.cs       ← NEW
│   │   └── Migrations/
│   │       ├── Public/                      ← NEW (public.tenants migration)
│   │       └── Tenant/                     ← NEW (users migration)
│   ├── Logging/
│   │   └── SerilogConfiguration.cs         ← NEW
│   └── Extensions/
│       ├── ServiceCollectionExtensions.cs  ← NEW
│       └── ApplicationBuilderExtensions.cs ← NEW
├── Models/
│   ├── Entities/
│   │   ├── Tenant.cs                       ← NEW
│   │   └── User.cs                         ← NEW
│   ├── Requests/
│   │   └── LoginRequest.cs                 ← NEW
│   └── Responses/
│       ├── LoginResponse.cs                ← NEW
│       └── ErrorResponse.cs               ← NEW
├── Services/
│   └── Auth/
│       ├── IAuthService.cs                 ← NEW
│       └── AuthService.cs                  ← NEW
├── Program.cs                              ← UPDATE (reescribir completo)
├── Sumitrack.Api.csproj                    ← UPDATE (agregar paquetes)
├── appsettings.json                        ← UPDATE (Serilog + JWT)
└── appsettings.Development.json           ← UPDATE (fix placeholder password)
```

**Conflictos detectados:**
- `Sumitrack.Api.Tests.csproj` tiene `ProjectReference` con path Windows (`..\..\`). Esto fue parchado en Historia 1.1 (F-2) para usar `/`. Verificar que sigue siendo forward slashes. Si al agregar paquetes NuGet el archivo se regenera, asegurarse de mantener forward slashes.

### References

- [Source: epics.md § "Historia 1.2"] — criterios de aceptación completos
- [Source: epics.md § AR-2] — schema-per-tenant, tabla control `public.tenants`, TenantSchemaResolver
- [Source: epics.md § AR-4] — EF Core 10.0.9, Npgsql 10.x, JWT Bearer, Serilog, migraciones auto-apply
- [Source: epics.md § AR-12] — Scalar para documentación API
- [Source: epics.md § AR-13] — formato de error: `{ "errors": [{ "code", "message" }] }`
- [Source: epics.md § AR-16] — convenciones de naming (snake_case DB, camelCase JSON, sin wrappers)
- [Source: epics.md § AR-17] — precisión monetaria (NUMERIC 18,6), establecer convención aunque no se use aún
- [Source: epics.md § AR-20] — validación solo en Services, nunca en Controllers
- [Source: architecture.md § "Datos y Multi-tenancy"] — esquema public.tenants, TenantSchemaResolver, SET search_path
- [Source: architecture.md § "API y Comunicación"] — Scalar, error response, versionado /api/v1/
- [Source: architecture.md § "Árbol Completo — Backend .NET"] — estructura de carpetas exacta
- [Source: architecture.md § "Infraestructura y Deployment"] — Serilog JSON a Railway, migraciones auto-apply
- [Source: implementation-artifacts/deferred-work.md] — placeholder `<local_password>` de Historia 1.1 a resolver aquí

## Dev Agent Record

### Agent Model Used

_pendiente_

### Debug Log References

### Completion Notes List

### File List

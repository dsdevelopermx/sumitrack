using Microsoft.EntityFrameworkCore;
using Sumitrack.Api.Infrastructure.Data;
using Sumitrack.Api.Models.Entities;

namespace Sumitrack.Api.Infrastructure.Extensions;

public static class ApplicationBuilderExtensions
{
    private const string CreateTenantSchemaSql = """
        CREATE SCHEMA IF NOT EXISTS "{0}";

        CREATE TABLE IF NOT EXISTS "{0}".users (
            id UUID NOT NULL,
            username CHARACTER VARYING(100) NOT NULL,
            password_hash CHARACTER VARYING(255) NOT NULL,
            tenant_id UUID NOT NULL,
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            CONSTRAINT pk_users PRIMARY KEY (id)
        );

        CREATE UNIQUE INDEX IF NOT EXISTS ix_users_username ON "{0}".users(username);
        """;

    public static async Task ApplyMigrationsAsync(this WebApplication app)
    {
        using var scope = app.Services.CreateScope();
        var logger = scope.ServiceProvider.GetRequiredService<ILogger<Program>>();

        var publicCtx = scope.ServiceProvider.GetRequiredService<AppDbContext>();

        // 1. Apply EF Core migrations for public schema (creates public.tenants)
        await publicCtx.Database.MigrateAsync();
        logger.LogInformation("Public schema migrations applied");

        // 2. For each existing tenant, ensure tenant schema and tables exist
        var tenants = await publicCtx.Tenants.ToListAsync();

        foreach (var tenant in tenants)
        {
            await EnsureTenantSchemaAsync(publicCtx, tenant.SchemaName, logger);
        }

        // 3. Development seed: create default tenant + user if none exist
        if (app.Environment.IsDevelopment() && !tenants.Any())
        {
            await SeedDevelopmentAsync(publicCtx, logger);
        }
    }

    private static async Task EnsureTenantSchemaAsync(
        AppDbContext publicCtx,
        string schemaName,
        ILogger logger)
    {
        // Use raw SQL to create tenant schema and tables (idempotent via IF NOT EXISTS)
        var sql = string.Format(CreateTenantSchemaSql, schemaName);
        await publicCtx.Database.ExecuteSqlRawAsync(sql);
        logger.LogInformation("Tenant schema {SchemaName} ensured", schemaName);
    }

    private static async Task SeedDevelopmentAsync(AppDbContext publicCtx, ILogger logger)
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

        await EnsureTenantSchemaAsync(publicCtx, schemaName, logger);

        // Insert admin user via raw SQL with positional parameters (EF Core escapes them)
        var passwordHash = BCrypt.Net.BCrypt.HashPassword("Admin123!");
        var userId = Guid.NewGuid();

        // {schemaName} is GUID-based (safe), {0}..{3} become SQL parameters
        await publicCtx.Database.ExecuteSqlRawAsync(
            $"INSERT INTO \"{schemaName}\".users (id, username, password_hash, tenant_id) VALUES ({{0}}, {{1}}, {{2}}, {{3}}) ON CONFLICT (username) DO NOTHING",
            userId, "admin", passwordHash, tenantId);

        logger.LogWarning(
            "DEVELOPMENT SEED: Tenant 'local' (slug) created with schema {Schema}. " +
            "Admin user 'admin' / 'Admin123!' created. DO NOT USE IN PRODUCTION.",
            schemaName);
    }
}

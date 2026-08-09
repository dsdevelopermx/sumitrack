using Microsoft.EntityFrameworkCore;
using Sumitrack.Api.Infrastructure.Data;
using Sumitrack.Api.Models.Entities;
using System.Text.RegularExpressions;

namespace Sumitrack.Api.Infrastructure.Extensions;

public static class ApplicationBuilderExtensions
{
    private static readonly Regex ValidSchemaName = new(@"^[a-z0-9_]+$", RegexOptions.Compiled);

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
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            sync_status CHARACTER VARYING(20) NOT NULL DEFAULT 'synced',
            CONSTRAINT pk_settings PRIMARY KEY (key)
        );

        ALTER TABLE "{schema}".settings ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();
        ALTER TABLE "{schema}".settings ADD COLUMN IF NOT EXISTS sync_status CHARACTER VARYING(20) NOT NULL DEFAULT 'synced';

        CREATE TABLE IF NOT EXISTS "{schema}".clients (
            id UUID NOT NULL,
            fk_tenant UUID NOT NULL,
            name CHARACTER VARYING(200) NOT NULL,
            phone CHARACTER VARYING(20) NOT NULL,
            rfc CHARACTER VARYING(20),
            address TEXT,
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            sync_status CHARACTER VARYING(20) NOT NULL DEFAULT 'synced',
            CONSTRAINT pk_clients PRIMARY KEY (id)
        );

        CREATE TABLE IF NOT EXISTS "{schema}".products (
            id UUID NOT NULL,
            fk_tenant UUID NOT NULL,
            name CHARACTER VARYING(200) NOT NULL,
            price NUMERIC(18,6) NOT NULL,
            tax_rate NUMERIC(18,6) NOT NULL,
            is_active BOOLEAN NOT NULL DEFAULT TRUE,
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            sync_status CHARACTER VARYING(20) NOT NULL DEFAULT 'synced',
            CONSTRAINT pk_products PRIMARY KEY (id)
        );

        CREATE TABLE IF NOT EXISTS "{schema}".product_variants (
            id UUID NOT NULL,
            fk_tenant UUID NOT NULL,
            fk_product UUID NOT NULL,
            name CHARACTER VARYING(100) NOT NULL,
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            sync_status CHARACTER VARYING(20) NOT NULL DEFAULT 'synced',
            CONSTRAINT pk_product_variants PRIMARY KEY (id)
        );

        CREATE TABLE IF NOT EXISTS "{schema}".sales (
            id UUID NOT NULL,
            fk_tenant UUID NOT NULL,
            fk_client UUID NOT NULL,
            folio CHARACTER VARYING(50) NOT NULL,
            total NUMERIC(18,6) NOT NULL,
            subtotal NUMERIC(18,6) NOT NULL DEFAULT 0,
            tax NUMERIC(18,6) NOT NULL DEFAULT 0,
            status CHARACTER VARYING(20) NOT NULL DEFAULT 'pending',
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            sync_status CHARACTER VARYING(20) NOT NULL DEFAULT 'synced',
            CONSTRAINT pk_sales PRIMARY KEY (id)
        );

        CREATE TABLE IF NOT EXISTS "{schema}".sale_items (
            id UUID NOT NULL,
            fk_tenant UUID NOT NULL,
            fk_sale UUID NOT NULL,
            fk_product UUID NOT NULL,
            fk_variant UUID,
            product_name CHARACTER VARYING(200) NOT NULL,
            variant_name CHARACTER VARYING(100),
            quantity INTEGER NOT NULL,
            unit_price NUMERIC(18,6) NOT NULL,
            tax_rate NUMERIC(18,6) NOT NULL,
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            sync_status CHARACTER VARYING(20) NOT NULL DEFAULT 'synced',
            CONSTRAINT pk_sale_items PRIMARY KEY (id)
        );

        CREATE TABLE IF NOT EXISTS "{schema}".installments (
            id UUID NOT NULL,
            fk_tenant UUID NOT NULL,
            fk_sale UUID NOT NULL,
            amount NUMERIC(18,6) NOT NULL,
            due_date TIMESTAMP WITH TIME ZONE NOT NULL,
            status CHARACTER VARYING(20) NOT NULL DEFAULT 'pending',
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            sync_status CHARACTER VARYING(20) NOT NULL DEFAULT 'synced',
            CONSTRAINT pk_installments PRIMARY KEY (id)
        );

        CREATE TABLE IF NOT EXISTS "{schema}".payments (
            id UUID NOT NULL,
            fk_tenant UUID NOT NULL,
            fk_sale UUID NOT NULL,
            fk_installment UUID,
            method CHARACTER VARYING(30) NOT NULL,
            amount NUMERIC(18,6) NOT NULL,
            paid_at TIMESTAMP WITH TIME ZONE NOT NULL,
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            sync_status CHARACTER VARYING(20) NOT NULL DEFAULT 'synced',
            CONSTRAINT pk_payments PRIMARY KEY (id)
        );

        CREATE TABLE IF NOT EXISTS "{schema}".credit_balances (
            id UUID NOT NULL,
            fk_tenant UUID NOT NULL,
            fk_client UUID NOT NULL,
            amount NUMERIC(18,6) NOT NULL,
            origin CHARACTER VARYING(20) NOT NULL DEFAULT 'cancellation',
            fk_origin_sale UUID,
            applied_at TIMESTAMP WITH TIME ZONE,
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            sync_status CHARACTER VARYING(20) NOT NULL DEFAULT 'synced',
            CONSTRAINT pk_credit_balances PRIMARY KEY (id)
        );
        """;

    public static async Task ApplyMigrationsAsync(this WebApplication app)
    {
        using var scope = app.Services.CreateScope();
        var logger = scope.ServiceProvider.GetRequiredService<ILogger<Program>>();

        var publicCtx = scope.ServiceProvider.GetRequiredService<AppDbContext>();

        await publicCtx.Database.MigrateAsync();
        logger.LogInformation("Public schema migrations applied");

        var tenants = await publicCtx.Tenants.ToListAsync();

        foreach (var tenant in tenants)
        {
            await EnsureTenantSchemaAsync(publicCtx, tenant.SchemaName, logger);
        }

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
        if (!ValidSchemaName.IsMatch(schemaName))
            throw new InvalidOperationException($"Invalid schema name '{schemaName}': only lowercase letters, digits, and underscores are allowed.");

        var sql = CreateTenantSchemaSql.Replace("{schema}", schemaName);
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

        var passwordHash = BCrypt.Net.BCrypt.HashPassword("Admin123!");
        var userId = Guid.NewGuid();

        await publicCtx.Database.ExecuteSqlRawAsync(
            $"INSERT INTO \"{schemaName}\".users (id, username, password_hash, tenant_id) VALUES ({{0}}, {{1}}, {{2}}, {{3}}) ON CONFLICT (username) DO NOTHING",
            userId, "admin", passwordHash, tenantId);

        await publicCtx.Database.ExecuteSqlRawAsync($"""
            INSERT INTO "{schemaName}".settings (key, value) VALUES
                ('max_parcialidades', '15'),
                ('serie_folio', 'A'),
                ('dias_anticipacion_recordatorio', '3')
            ON CONFLICT (key) DO NOTHING
            """);

        logger.LogWarning(
            "DEVELOPMENT SEED: Tenant 'local' (slug) created with schema {Schema}. " +
            "Admin user 'admin' / 'Admin123!' created. DO NOT USE IN PRODUCTION.",
            schemaName);
    }
}

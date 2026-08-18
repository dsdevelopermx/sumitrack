using Microsoft.EntityFrameworkCore;
using Sumitrack.Api.Infrastructure.Auth;
using Sumitrack.Api.Infrastructure.Data;
using Sumitrack.Api.Models.Entities;
using Sumitrack.Api.Models.Responses;
using Sumitrack.Api.Services.Sync;
using Xunit;

namespace Sumitrack.Api.Tests.Services;

public class SyncServicePullTests
{
    private static TenantDbContext BuildCtx(string dbName)
    {
        var options = new DbContextOptionsBuilder<TenantDbContext>()
            .UseInMemoryDatabase(dbName)
            .Options;
        return new TenantDbContext(options);
    }

    private static ITenantContext BuildTenantContext(Guid tenantId)
    {
        var ctx = new TenantContext();
        ctx.Initialize(tenantId, $"tenant_{tenantId:N}");
        return ctx;
    }

    [Fact]
    public async Task PullAsync_SinSince_DevuelveTodosLosRegistros()
    {
        using var ctx = BuildCtx(nameof(PullAsync_SinSince_DevuelveTodosLosRegistros));
        var tenantId = Guid.NewGuid();
        var now = DateTime.UtcNow;
        ctx.Clients.AddRange(
            new Client { Id = Guid.NewGuid(), FkTenant = tenantId, Name = "A", Phone = "1", CreatedAt = now, UpdatedAt = now },
            new Client { Id = Guid.NewGuid(), FkTenant = tenantId, Name = "B", Phone = "2", CreatedAt = now, UpdatedAt = now }
        );
        await ctx.SaveChangesAsync();

        var service = new SyncService(ctx, BuildTenantContext(tenantId));
        var result = await service.PullAsync("clientes", since: null, CancellationToken.None);

        var items = Assert.IsType<List<ClientPullResponseItem>>(result);
        Assert.Equal(2, items.Count);
    }

    [Fact]
    public async Task PullAsync_ConSince_DevuelveSoloRegistrosMasNuevos()
    {
        using var ctx = BuildCtx(nameof(PullAsync_ConSince_DevuelveSoloRegistrosMasNuevos));
        var tenantId = Guid.NewGuid();
        var since = DateTime.UtcNow;
        var older = new Client { Id = Guid.NewGuid(), FkTenant = tenantId, Name = "Viejo", Phone = "1", CreatedAt = since.AddDays(-2), UpdatedAt = since.AddDays(-1) };
        var newer = new Client { Id = Guid.NewGuid(), FkTenant = tenantId, Name = "Nuevo", Phone = "2", CreatedAt = since.AddMinutes(1), UpdatedAt = since.AddMinutes(1) };
        ctx.Clients.AddRange(older, newer);
        await ctx.SaveChangesAsync();

        var service = new SyncService(ctx, BuildTenantContext(tenantId));
        var result = await service.PullAsync("clientes", since, CancellationToken.None);

        var items = Assert.IsType<List<ClientPullResponseItem>>(result);
        Assert.Single(items);
        Assert.Equal("Nuevo", items[0].Name);
    }

    [Fact]
    public async Task PullAsync_UpdatedAtIgualASince_NoSeIncluye()
    {
        using var ctx = BuildCtx(nameof(PullAsync_UpdatedAtIgualASince_NoSeIncluye));
        var tenantId = Guid.NewGuid();
        var since = DateTime.UtcNow;
        ctx.Clients.Add(new Client { Id = Guid.NewGuid(), FkTenant = tenantId, Name = "Exacto", Phone = "1", CreatedAt = since, UpdatedAt = since });
        await ctx.SaveChangesAsync();

        var service = new SyncService(ctx, BuildTenantContext(tenantId));
        var result = await service.PullAsync("clientes", since, CancellationToken.None);

        var items = Assert.IsType<List<ClientPullResponseItem>>(result);
        Assert.Empty(items);
    }

    [Fact]
    public async Task PullAsync_EntidadDesconocida_DevuelveNull()
    {
        using var ctx = BuildCtx(nameof(PullAsync_EntidadDesconocida_DevuelveNull));
        var service = new SyncService(ctx, BuildTenantContext(Guid.NewGuid()));

        var result = await service.PullAsync("entidad_inexistente", null, CancellationToken.None);

        Assert.Null(result);
    }

    [Fact]
    public async Task PullAsync_Settings_UsaKeyComoIdentidad()
    {
        using var ctx = BuildCtx(nameof(PullAsync_Settings_UsaKeyComoIdentidad));
        var now = DateTime.UtcNow;
        ctx.Settings.Add(new Setting { Key = "serie_folio", Value = "A", CreatedAt = now, UpdatedAt = now, SyncStatus = "synced" });
        await ctx.SaveChangesAsync();

        var service = new SyncService(ctx, BuildTenantContext(Guid.NewGuid()));
        var result = await service.PullAsync("settings", null, CancellationToken.None);

        var items = Assert.IsType<List<SettingPullResponseItem>>(result);
        Assert.Single(items);
        Assert.Equal("serie_folio", items[0].Key);
        Assert.Equal("A", items[0].Value);
    }

    [Fact]
    public async Task PullAsync_SeparateDbContexts_DoNotShareState()
    {
        // NOTA: igual que en SyncServiceTests (push, Historia 4.1), EF Core InMemory nunca abre
        // una conexión ADO.NET real, así que TenantSchemaInterceptor no se ejecuta bajo este test
        // — esto NO verifica el aislamiento por schema de Postgres. Solo confirma que dos
        // instancias de TenantDbContext respaldadas por bases InMemory distintas no comparten
        // estado entre sí.
        using var ctxTenantA = BuildCtx(nameof(PullAsync_SeparateDbContexts_DoNotShareState) + "_a");
        using var ctxTenantB = BuildCtx(nameof(PullAsync_SeparateDbContexts_DoNotShareState) + "_b");
        var now = DateTime.UtcNow;
        ctxTenantA.Clients.Add(new Client { Id = Guid.NewGuid(), FkTenant = Guid.NewGuid(), Name = "Cliente A", Phone = "1", CreatedAt = now, UpdatedAt = now });
        ctxTenantB.Clients.Add(new Client { Id = Guid.NewGuid(), FkTenant = Guid.NewGuid(), Name = "Cliente B", Phone = "2", CreatedAt = now, UpdatedAt = now });
        await ctxTenantA.SaveChangesAsync();
        await ctxTenantB.SaveChangesAsync();

        var serviceA = new SyncService(ctxTenantA, BuildTenantContext(Guid.NewGuid()));
        var serviceB = new SyncService(ctxTenantB, BuildTenantContext(Guid.NewGuid()));

        var resultA = Assert.IsType<List<ClientPullResponseItem>>(await serviceA.PullAsync("clientes", null, CancellationToken.None));
        var resultB = Assert.IsType<List<ClientPullResponseItem>>(await serviceB.PullAsync("clientes", null, CancellationToken.None));

        Assert.Single(resultA);
        Assert.Equal("Cliente A", resultA[0].Name);
        Assert.Single(resultB);
        Assert.Equal("Cliente B", resultB[0].Name);
    }
}

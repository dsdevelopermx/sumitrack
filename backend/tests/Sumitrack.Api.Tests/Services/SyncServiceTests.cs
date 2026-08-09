using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using Sumitrack.Api.Infrastructure.Auth;
using Sumitrack.Api.Infrastructure.Data;
using Sumitrack.Api.Models.Responses;
using Sumitrack.Api.Services.Sync;
using Xunit;

namespace Sumitrack.Api.Tests.Services;

public class SyncServiceTests
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

    private static JsonElement ToPayload(object value) =>
        JsonSerializer.SerializeToElement(value, new JsonSerializerOptions(JsonSerializerDefaults.Web));

    [Fact]
    public async Task PushAsync_NewClient_InsertsRecord()
    {
        using var ctx = BuildCtx(nameof(PushAsync_NewClient_InsertsRecord));
        var tenantId = Guid.NewGuid();
        var service = new SyncService(ctx, BuildTenantContext(tenantId));
        var id = Guid.NewGuid();
        var now = DateTime.UtcNow;

        var payload = ToPayload(new[]
        {
            new
            {
                id,
                fkTenant = Guid.NewGuid(), // deliberately different from the authenticated tenant
                name = "Juan Pérez",
                phone = "5512345678",
                rfc = (string?)null,
                address = (string?)null,
                notes = (string?)null,
                createdAt = now,
                updatedAt = now,
            }
        });

        var result = await service.PushAsync("clientes", payload, CancellationToken.None);

        var items = Assert.IsType<List<PushSyncResponseItem>>(result);
        Assert.Single(items);
        Assert.True(items[0].Success);
        Assert.Equal(id, items[0].Id);

        var stored = await ctx.Clients.SingleAsync();
        Assert.Equal("Juan Pérez", stored.Name);
        Assert.Equal("synced", stored.SyncStatus);
        // FkTenant siempre viene del tenant autenticado, nunca del payload — el cliente mandó un
        // fkTenant distinto arriba y debe ser ignorado.
        Assert.Equal(tenantId, stored.FkTenant);
    }

    [Fact]
    public async Task PushAsync_ExistingClient_UpdatesWithoutDuplicating()
    {
        using var ctx = BuildCtx(nameof(PushAsync_ExistingClient_UpdatesWithoutDuplicating));
        var tenantId = Guid.NewGuid();
        var id = Guid.NewGuid();
        var createdAt = DateTime.UtcNow.AddDays(-1);
        ctx.Clients.Add(new Models.Entities.Client
        {
            Id = id,
            FkTenant = tenantId,
            Name = "Nombre viejo",
            Phone = "5500000000",
            CreatedAt = createdAt,
            UpdatedAt = createdAt,
            SyncStatus = "synced",
        });
        await ctx.SaveChangesAsync();

        var service = new SyncService(ctx, BuildTenantContext(tenantId));
        var updatedAt = DateTime.UtcNow;
        var payload = ToPayload(new[]
        {
            new
            {
                id,
                fkTenant = tenantId,
                name = "Nombre nuevo",
                phone = "5511111111",
                rfc = (string?)null,
                address = (string?)null,
                notes = (string?)null,
                createdAt,
                updatedAt,
            }
        });

        var result = await service.PushAsync("clientes", payload, CancellationToken.None);

        var items = Assert.IsType<List<PushSyncResponseItem>>(result);
        Assert.Single(items);
        Assert.True(items[0].Success);

        Assert.Single(ctx.Clients);
        var stored = await ctx.Clients.SingleAsync();
        Assert.Equal("Nombre nuevo", stored.Name);
        Assert.Equal("5511111111", stored.Phone);
    }

    [Fact]
    public async Task PushAsync_UnknownEntity_ReturnsNull()
    {
        using var ctx = BuildCtx(nameof(PushAsync_UnknownEntity_ReturnsNull));
        var service = new SyncService(ctx, BuildTenantContext(Guid.NewGuid()));

        var result = await service.PushAsync("entidad_inexistente", ToPayload(Array.Empty<object>()), CancellationToken.None);

        Assert.Null(result);
    }

    [Fact]
    public async Task PushAsync_MultipleItems_ReturnsOneResponsePerItem()
    {
        using var ctx = BuildCtx(nameof(PushAsync_MultipleItems_ReturnsOneResponsePerItem));
        var service = new SyncService(ctx, BuildTenantContext(Guid.NewGuid()));
        var now = DateTime.UtcNow;

        var payload = ToPayload(new[]
        {
            new { id = Guid.NewGuid(), fkTenant = Guid.NewGuid(), name = "A", price = 10m, taxRate = 0.16m, isActive = true, createdAt = now, updatedAt = now },
            new { id = Guid.NewGuid(), fkTenant = Guid.NewGuid(), name = "B", price = 20m, taxRate = 0.16m, isActive = true, createdAt = now, updatedAt = now },
            new { id = Guid.NewGuid(), fkTenant = Guid.NewGuid(), name = "C", price = 30m, taxRate = 0.16m, isActive = true, createdAt = now, updatedAt = now },
        });

        var result = await service.PushAsync("productos", payload, CancellationToken.None);

        var items = Assert.IsType<List<PushSyncResponseItem>>(result);
        Assert.Equal(3, items.Count);
        Assert.All(items, i => Assert.True(i.Success));
        Assert.Equal(3, await ctx.Products.CountAsync());
    }

    [Fact]
    public async Task PushAsync_SeparateDbContexts_DoNotShareState()
    {
        // NOTA: EF Core InMemory nunca abre una conexión ADO.NET real, así que
        // TenantSchemaInterceptor (un DbConnectionInterceptor) no se ejecuta bajo este test — esto
        // NO verifica el aislamiento por schema de Postgres. Solo confirma que dos instancias de
        // TenantDbContext respaldadas por bases InMemory distintas no comparten estado entre sí.
        using var ctxTenantA = BuildCtx(nameof(PushAsync_SeparateDbContexts_DoNotShareState) + "_a");
        using var ctxTenantB = BuildCtx(nameof(PushAsync_SeparateDbContexts_DoNotShareState) + "_b");
        var serviceA = new SyncService(ctxTenantA, BuildTenantContext(Guid.NewGuid()));
        var serviceB = new SyncService(ctxTenantB, BuildTenantContext(Guid.NewGuid()));

        var sharedId = Guid.NewGuid();
        var now = DateTime.UtcNow;

        var payloadA = ToPayload(new[] { new { id = sharedId, fkTenant = Guid.NewGuid(), name = "Cliente A", phone = "1", rfc = (string?)null, address = (string?)null, notes = (string?)null, createdAt = now, updatedAt = now } });
        var payloadB = ToPayload(new[] { new { id = sharedId, fkTenant = Guid.NewGuid(), name = "Cliente B", phone = "2", rfc = (string?)null, address = (string?)null, notes = (string?)null, createdAt = now, updatedAt = now } });

        await serviceA.PushAsync("clientes", payloadA, CancellationToken.None);
        await serviceB.PushAsync("clientes", payloadB, CancellationToken.None);

        var storedA = await ctxTenantA.Clients.SingleAsync();
        var storedB = await ctxTenantB.Clients.SingleAsync();
        Assert.Equal("Cliente A", storedA.Name);
        Assert.Equal("Cliente B", storedB.Name);
    }

    [Fact]
    public async Task PushAsync_NewSetting_InsertsByKey()
    {
        using var ctx = BuildCtx(nameof(PushAsync_NewSetting_InsertsByKey));
        var service = new SyncService(ctx, BuildTenantContext(Guid.NewGuid()));
        var now = DateTime.UtcNow;

        var payload = ToPayload(new[] { new { key = "ticket_footer", value = "Gracias por su compra", createdAt = now, updatedAt = now } });

        var result = await service.PushAsync("settings", payload, CancellationToken.None);

        var items = Assert.IsType<List<SettingSyncResponseItem>>(result);
        Assert.Single(items);
        Assert.True(items[0].Success);
        Assert.Equal("ticket_footer", items[0].Key);

        var stored = await ctx.Settings.SingleAsync();
        Assert.Equal("Gracias por su compra", stored.Value);
        Assert.Equal("synced", stored.SyncStatus);
    }

    [Fact]
    public async Task PushAsync_ExistingSetting_UpdatesByKeyWithoutDuplicating()
    {
        using var ctx = BuildCtx(nameof(PushAsync_ExistingSetting_UpdatesByKeyWithoutDuplicating));
        ctx.Settings.Add(new Models.Entities.Setting { Key = "ticket_footer", Value = "Viejo", CreatedAt = DateTime.UtcNow, UpdatedAt = DateTime.UtcNow, SyncStatus = "synced" });
        await ctx.SaveChangesAsync();

        var service = new SyncService(ctx, BuildTenantContext(Guid.NewGuid()));
        var now = DateTime.UtcNow;
        var payload = ToPayload(new[] { new { key = "ticket_footer", value = "Nuevo", createdAt = now, updatedAt = now } });

        await service.PushAsync("settings", payload, CancellationToken.None);

        Assert.Single(ctx.Settings);
        var stored = await ctx.Settings.SingleAsync();
        Assert.Equal("Nuevo", stored.Value);
    }

    [Theory]
    [InlineData("00000000-0000-0000-0000-000000000000", "Nombre", "5512345678")] // id vacío
    [InlineData(null, "", "5512345678")] // name vacío
    [InlineData(null, "Nombre", "")] // phone vacío
    public async Task PushAsync_ClienteInvalido_NoSePersisteYReportaError(string? fixedId, string name, string phone)
    {
        using var ctx = BuildCtx($"{nameof(PushAsync_ClienteInvalido_NoSePersisteYReportaError)}_{fixedId}_{name}_{phone}");
        var service = new SyncService(ctx, BuildTenantContext(Guid.NewGuid()));
        var id = fixedId != null ? Guid.Parse(fixedId) : Guid.NewGuid();
        var now = DateTime.UtcNow;

        var payload = ToPayload(new[]
        {
            new { id, fkTenant = Guid.NewGuid(), name, phone, rfc = (string?)null, address = (string?)null, notes = (string?)null, createdAt = now, updatedAt = now }
        });

        var result = await service.PushAsync("clientes", payload, CancellationToken.None);

        var items = Assert.IsType<List<PushSyncResponseItem>>(result);
        Assert.Single(items);
        Assert.False(items[0].Success);
        Assert.NotNull(items[0].Error);
        Assert.Empty(ctx.Clients);
    }

    [Fact]
    public async Task PushAsync_ProductoConPrecioNegativo_NoSePersisteYReportaError()
    {
        using var ctx = BuildCtx(nameof(PushAsync_ProductoConPrecioNegativo_NoSePersisteYReportaError));
        var service = new SyncService(ctx, BuildTenantContext(Guid.NewGuid()));
        var now = DateTime.UtcNow;

        var payload = ToPayload(new[]
        {
            new { id = Guid.NewGuid(), fkTenant = Guid.NewGuid(), name = "Martillo", price = -10m, taxRate = 0.16m, isActive = true, createdAt = now, updatedAt = now }
        });

        var result = await service.PushAsync("productos", payload, CancellationToken.None);

        var items = Assert.IsType<List<PushSyncResponseItem>>(result);
        Assert.Single(items);
        Assert.False(items[0].Success);
        Assert.Empty(ctx.Products);
    }

    [Fact]
    public async Task PushAsync_LoteMixtoValidoEInvalido_ReportaUnItemPorRegistro()
    {
        using var ctx = BuildCtx(nameof(PushAsync_LoteMixtoValidoEInvalido_ReportaUnItemPorRegistro));
        var service = new SyncService(ctx, BuildTenantContext(Guid.NewGuid()));
        var now = DateTime.UtcNow;
        var validId = Guid.NewGuid();

        var payload = ToPayload(new[]
        {
            new { id = validId, fkTenant = Guid.NewGuid(), name = "Válido", phone = "5512345678", rfc = (string?)null, address = (string?)null, notes = (string?)null, createdAt = now, updatedAt = now },
            new { id = Guid.Empty, fkTenant = Guid.NewGuid(), name = "Inválido", phone = "5512345678", rfc = (string?)null, address = (string?)null, notes = (string?)null, createdAt = now, updatedAt = now },
        });

        var result = await service.PushAsync("clientes", payload, CancellationToken.None);

        var items = Assert.IsType<List<PushSyncResponseItem>>(result);
        Assert.Equal(2, items.Count);
        Assert.True(items[0].Success);
        Assert.False(items[1].Success);
        Assert.Single(ctx.Clients);
    }
}

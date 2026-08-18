using Microsoft.EntityFrameworkCore;
using Sumitrack.Api.Infrastructure.Auth;
using Sumitrack.Api.Infrastructure.Data;
using Sumitrack.Api.Models.Entities;
using Sumitrack.Api.Services.Sync;
using Xunit;

namespace Sumitrack.Api.Tests.Services;

public class SyncServiceFolioCountTests
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
    public async Task GetFolioCountAsync_SinVentas_DevuelveCero()
    {
        using var ctx = BuildCtx(nameof(GetFolioCountAsync_SinVentas_DevuelveCero));
        var service = new SyncService(ctx, BuildTenantContext(Guid.NewGuid()));

        var count = await service.GetFolioCountAsync(CancellationToken.None);

        Assert.Equal(0, count);
    }

    [Fact]
    public async Task GetFolioCountAsync_ConVentas_DevuelveElTotal()
    {
        using var ctx = BuildCtx(nameof(GetFolioCountAsync_ConVentas_DevuelveElTotal));
        var tenantId = Guid.NewGuid();
        var now = DateTime.UtcNow;
        ctx.Sales.AddRange(
            new Sale { Id = Guid.NewGuid(), FkTenant = tenantId, FkClient = Guid.NewGuid(), Folio = "A1", Total = 10m, CreatedAt = now, UpdatedAt = now },
            new Sale { Id = Guid.NewGuid(), FkTenant = tenantId, FkClient = Guid.NewGuid(), Folio = "A2", Total = 20m, CreatedAt = now, UpdatedAt = now },
            new Sale { Id = Guid.NewGuid(), FkTenant = tenantId, FkClient = Guid.NewGuid(), Folio = "A3", Total = 30m, CreatedAt = now, UpdatedAt = now }
        );
        await ctx.SaveChangesAsync();

        var service = new SyncService(ctx, BuildTenantContext(tenantId));
        var count = await service.GetFolioCountAsync(CancellationToken.None);

        Assert.Equal(3, count);
    }
}

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
        var connStr = _configuration.GetConnectionString("DefaultConnection")
            ?? throw new InvalidOperationException("ConnectionStrings:DefaultConnection not configured");

        // Append Search Path so all queries target the tenant schema
        var tenantConnStr = connStr.TrimEnd(';') + $";Search Path=\"{schemaName}\",public";

        var options = new DbContextOptionsBuilder<TenantDbContext>()
            .UseNpgsql(tenantConnStr)
            .Options;

        return new TenantDbContext(options);
    }
}

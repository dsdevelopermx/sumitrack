using Microsoft.EntityFrameworkCore;
using System.Text.RegularExpressions;

namespace Sumitrack.Api.Infrastructure.Data;

public class TenantDbContextFactory : ITenantDbContextFactory
{
    private static readonly Regex ValidSchemaName = new(@"^[a-z0-9_]+$", RegexOptions.Compiled);

    private readonly IConfiguration _configuration;

    public TenantDbContextFactory(IConfiguration configuration)
    {
        _configuration = configuration;
    }

    public TenantDbContext Create(string schemaName)
    {
        if (!ValidSchemaName.IsMatch(schemaName))
            throw new InvalidOperationException($"Invalid schema name '{schemaName}': only lowercase letters, digits, and underscores are allowed.");

        var connStr = _configuration.GetConnectionString("DefaultConnection")
            ?? throw new InvalidOperationException("ConnectionStrings:DefaultConnection not configured");

        var tenantConnStr = connStr.TrimEnd(';') + $";Search Path=\"{schemaName}\",public";

        var options = new DbContextOptionsBuilder<TenantDbContext>()
            .UseNpgsql(tenantConnStr)
            .Options;

        return new TenantDbContext(options);
    }
}

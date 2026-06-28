using Microsoft.EntityFrameworkCore.Diagnostics;
using Sumitrack.Api.Infrastructure.Auth;
using System.Data.Common;
using System.Text.RegularExpressions;

namespace Sumitrack.Api.Infrastructure.Data;

public class TenantSchemaInterceptor : DbConnectionInterceptor
{
    private static readonly Regex ValidSchemaName = new(@"^[a-z0-9_]+$", RegexOptions.Compiled);

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
            ValidateSchemaName(_tenantContext.SchemaName!);
            await using var cmd = connection.CreateCommand();
            cmd.CommandText = $"SET search_path TO \"{_tenantContext.SchemaName}\", public";
            await cmd.ExecuteNonQueryAsync(cancellationToken);
        }
    }

    public override void ConnectionOpened(DbConnection connection, ConnectionEndEventData eventData)
    {
        if (_tenantContext.IsResolved)
        {
            ValidateSchemaName(_tenantContext.SchemaName!);
            using var cmd = connection.CreateCommand();
            cmd.CommandText = $"SET search_path TO \"{_tenantContext.SchemaName}\", public";
            cmd.ExecuteNonQuery();
        }
    }

    private static void ValidateSchemaName(string schemaName)
    {
        if (!ValidSchemaName.IsMatch(schemaName))
            throw new InvalidOperationException($"Invalid schema name '{schemaName}': only lowercase letters, digits, and underscores are allowed.");
    }
}

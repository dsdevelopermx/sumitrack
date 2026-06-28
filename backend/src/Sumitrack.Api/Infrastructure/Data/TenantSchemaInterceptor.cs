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

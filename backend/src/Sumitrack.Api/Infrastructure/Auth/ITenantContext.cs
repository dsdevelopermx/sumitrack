namespace Sumitrack.Api.Infrastructure.Auth;

public interface ITenantContext
{
    Guid? TenantId { get; }
    string? SchemaName { get; }
    bool IsResolved { get; }
    void Initialize(Guid tenantId, string schemaName);
}

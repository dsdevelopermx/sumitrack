namespace Sumitrack.Api.Infrastructure.Auth;

public class TenantContext : ITenantContext
{
    public Guid? TenantId { get; private set; }
    public string? SchemaName { get; private set; }
    public bool IsResolved => TenantId.HasValue && !string.IsNullOrWhiteSpace(SchemaName);

    public void Initialize(Guid tenantId, string schemaName)
    {
        TenantId = tenantId;
        SchemaName = schemaName;
    }
}

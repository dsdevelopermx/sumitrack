namespace Sumitrack.Api.Infrastructure.Auth;

public class TenantContext : ITenantContext
{
    public Guid? TenantId { get; set; }
    public string? SchemaName { get; set; }
    public bool IsResolved => TenantId.HasValue && !string.IsNullOrEmpty(SchemaName);
}

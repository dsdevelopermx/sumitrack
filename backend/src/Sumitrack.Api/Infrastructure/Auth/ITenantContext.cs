namespace Sumitrack.Api.Infrastructure.Auth;

public interface ITenantContext
{
    Guid? TenantId { get; set; }
    string? SchemaName { get; set; }
    bool IsResolved { get; }
}

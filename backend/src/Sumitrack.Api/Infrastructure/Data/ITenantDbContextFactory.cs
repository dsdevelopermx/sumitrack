namespace Sumitrack.Api.Infrastructure.Data;

public interface ITenantDbContextFactory
{
    TenantDbContext Create(string schemaName);
}

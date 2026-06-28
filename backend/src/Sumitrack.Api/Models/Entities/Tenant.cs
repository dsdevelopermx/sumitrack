namespace Sumitrack.Api.Models.Entities;

public class Tenant
{
    public Guid Id { get; set; }
    public string Slug { get; set; } = string.Empty;
    public string SchemaName { get; set; } = string.Empty;
    public DateTime CreatedAt { get; set; }
}

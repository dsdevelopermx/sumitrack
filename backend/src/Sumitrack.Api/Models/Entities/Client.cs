namespace Sumitrack.Api.Models.Entities;

public class Client
{
    public Guid Id { get; set; }
    public Guid FkTenant { get; set; }
    public string Name { get; set; } = string.Empty;
    public string Phone { get; set; } = string.Empty;
    public string? Rfc { get; set; }
    public string? Address { get; set; }
    public string? Notes { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
    public string SyncStatus { get; set; } = "synced";
}

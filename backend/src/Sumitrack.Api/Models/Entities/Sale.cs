namespace Sumitrack.Api.Models.Entities;

public class Sale
{
    public Guid Id { get; set; }
    public Guid FkTenant { get; set; }
    public Guid FkClient { get; set; }
    public string Folio { get; set; } = string.Empty;
    public decimal Total { get; set; }
    public decimal Subtotal { get; set; }
    public decimal Tax { get; set; }
    public string Status { get; set; } = "pending";
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
    public string SyncStatus { get; set; } = "synced";
}

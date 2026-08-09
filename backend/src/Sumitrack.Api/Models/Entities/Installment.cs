namespace Sumitrack.Api.Models.Entities;

public class Installment
{
    public Guid Id { get; set; }
    public Guid FkTenant { get; set; }
    public Guid FkSale { get; set; }
    public decimal Amount { get; set; }
    public DateTime DueDate { get; set; }
    public string Status { get; set; } = "pending";
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
    public string SyncStatus { get; set; } = "synced";
}

namespace Sumitrack.Api.Models.Requests;

public class InstallmentSyncRequest
{
    public Guid Id { get; set; }
    public Guid FkTenant { get; set; }
    public Guid FkSale { get; set; }
    public decimal Amount { get; set; }
    public DateTime DueDate { get; set; }
    public string Status { get; set; } = string.Empty;
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}

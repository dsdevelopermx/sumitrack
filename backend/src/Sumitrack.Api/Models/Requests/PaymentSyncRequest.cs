namespace Sumitrack.Api.Models.Requests;

public class PaymentSyncRequest
{
    public Guid Id { get; set; }
    public Guid FkTenant { get; set; }
    public Guid FkSale { get; set; }
    public Guid? FkInstallment { get; set; }
    public string Method { get; set; } = string.Empty;
    public decimal Amount { get; set; }
    public DateTime PaidAt { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}

namespace Sumitrack.Api.Models.Responses;

public class CreditBalancePullResponseItem
{
    public Guid Id { get; set; }
    public Guid FkTenant { get; set; }
    public Guid FkClient { get; set; }
    public decimal Amount { get; set; }
    public string Origin { get; set; } = string.Empty;
    public Guid? FkOriginSale { get; set; }
    public DateTime? AppliedAt { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}

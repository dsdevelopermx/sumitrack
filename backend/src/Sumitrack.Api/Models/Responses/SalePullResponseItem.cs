namespace Sumitrack.Api.Models.Responses;

public class SalePullResponseItem
{
    public Guid Id { get; set; }
    public Guid FkTenant { get; set; }
    public Guid FkClient { get; set; }
    public string Folio { get; set; } = string.Empty;
    public decimal Total { get; set; }
    public decimal Subtotal { get; set; }
    public decimal Tax { get; set; }
    public string Status { get; set; } = string.Empty;
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}

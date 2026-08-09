namespace Sumitrack.Api.Models.Requests;

public class SaleItemSyncRequest
{
    public Guid Id { get; set; }
    public Guid FkTenant { get; set; }
    public Guid FkSale { get; set; }
    public Guid FkProduct { get; set; }
    public Guid? FkVariant { get; set; }
    public string ProductName { get; set; } = string.Empty;
    public string? VariantName { get; set; }
    public int Quantity { get; set; }
    public decimal UnitPrice { get; set; }
    public decimal TaxRate { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}

namespace Sumitrack.Api.Models.Responses;

public class ProductPullResponseItem
{
    public Guid Id { get; set; }
    public Guid FkTenant { get; set; }
    public string Name { get; set; } = string.Empty;
    public decimal Price { get; set; }
    public decimal TaxRate { get; set; }
    public bool IsActive { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}

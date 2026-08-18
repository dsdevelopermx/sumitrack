namespace Sumitrack.Api.Models.Responses;

public class ProductVariantPullResponseItem
{
    public Guid Id { get; set; }
    public Guid FkTenant { get; set; }
    public Guid FkProduct { get; set; }
    public string Name { get; set; } = string.Empty;
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}

namespace Sumitrack.Api.Models.Responses;

public class SettingPullResponseItem
{
    public string Key { get; set; } = string.Empty;
    public string? Value { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}

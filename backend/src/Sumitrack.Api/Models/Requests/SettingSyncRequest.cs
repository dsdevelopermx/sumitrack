namespace Sumitrack.Api.Models.Requests;

public class SettingSyncRequest
{
    public string Key { get; set; } = string.Empty;
    public string? Value { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}

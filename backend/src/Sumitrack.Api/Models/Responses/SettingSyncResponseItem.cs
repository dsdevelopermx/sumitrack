namespace Sumitrack.Api.Models.Responses;

public class SettingSyncResponseItem
{
    public string Key { get; set; } = string.Empty;
    public bool Success { get; set; }
    public string? Error { get; set; }
}

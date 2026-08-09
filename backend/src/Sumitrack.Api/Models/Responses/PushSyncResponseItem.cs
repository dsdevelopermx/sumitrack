namespace Sumitrack.Api.Models.Responses;

public class PushSyncResponseItem
{
    public Guid Id { get; set; }
    public bool Success { get; set; }
    public string? Error { get; set; }
}

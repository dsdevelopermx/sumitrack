using System.Text.Json;

namespace Sumitrack.Api.Services.Sync;

public interface ISyncService
{
    Task<object?> PushAsync(string entity, JsonElement payload, CancellationToken cancellationToken = default);
}

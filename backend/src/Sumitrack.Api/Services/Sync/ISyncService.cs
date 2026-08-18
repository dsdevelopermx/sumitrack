using System.Text.Json;

namespace Sumitrack.Api.Services.Sync;

public interface ISyncService
{
    Task<object?> PushAsync(string entity, JsonElement payload, CancellationToken cancellationToken = default);

    Task<object?> PullAsync(string entity, DateTime? since, CancellationToken cancellationToken = default);

    // Total de ventas del tenant — usado por el cliente como piso del contador local de folios
    // (AR-10), independiente y mucho más rápido que esperar el pull completo de `ventas`.
    Task<int> GetFolioCountAsync(CancellationToken cancellationToken = default);
}

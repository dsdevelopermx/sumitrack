using System.Text.Json;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Sumitrack.Api.Models.Responses;
using Sumitrack.Api.Services.Sync;

namespace Sumitrack.Api.Controllers;

[ApiController]
[Route("api/v1/sync")]
[Authorize]
public class SyncController : ControllerBase
{
    private readonly ISyncService _syncService;

    public SyncController(ISyncService syncService) => _syncService = syncService;

    /// <summary>Pushes pending records of one entity type into the authenticated tenant's schema.</summary>
    [HttpPost("push/{entity}")]
    public async Task<IActionResult> Push(string entity, [FromBody] JsonElement payload, CancellationToken cancellationToken)
    {
        var result = await _syncService.PushAsync(entity, payload, cancellationToken);
        if (result is null)
        {
            return NotFound(new ErrorResponse
            {
                Errors = [new ApiError { Code = "UNKNOWN_ENTITY", Message = $"Entidad '{entity}' no reconocida." }]
            });
        }

        return Ok(result);
    }

    /// <summary>Returns records of one entity type updated after `since` (or all, if omitted) from the authenticated tenant's schema.</summary>
    [HttpGet("pull/{entity}")]
    public async Task<IActionResult> Pull(string entity, [FromQuery] DateTime? since, CancellationToken cancellationToken)
    {
        var result = await _syncService.PullAsync(entity, since, cancellationToken);
        if (result is null)
        {
            return NotFound(new ErrorResponse
            {
                Errors = [new ApiError { Code = "UNKNOWN_ENTITY", Message = $"Entidad '{entity}' no reconocida." }]
            });
        }

        return Ok(result);
    }

    /// <summary>Returns the total sale count for the authenticated tenant — used as the folio counter floor.</summary>
    [HttpGet("folio-count")]
    public async Task<IActionResult> GetFolioCount(CancellationToken cancellationToken)
    {
        var count = await _syncService.GetFolioCountAsync(cancellationToken);
        return Ok(new FolioCountResponse { Count = count });
    }
}

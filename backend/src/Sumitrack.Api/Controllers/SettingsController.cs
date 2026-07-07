using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Sumitrack.Api.Infrastructure.Data;
using Sumitrack.Api.Models.Responses;

namespace Sumitrack.Api.Controllers;

[ApiController]
[Route("api/v1/settings")]
[Authorize]
public class SettingsController : ControllerBase
{
    private readonly TenantDbContext _ctx;

    public SettingsController(TenantDbContext ctx) => _ctx = ctx;

    /// <summary>Returns all settings for the authenticated tenant.</summary>
    [HttpGet]
    [ProducesResponseType(typeof(IEnumerable<SettingDto>), StatusCodes.Status200OK)]
    public async Task<IActionResult> GetSettings(CancellationToken cancellationToken)
    {
        var settings = await _ctx.Settings
            .Select(s => new SettingDto { Key = s.Key, Value = s.Value })
            .ToListAsync(cancellationToken);

        return Ok(settings);
    }
}

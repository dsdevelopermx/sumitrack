using Sumitrack.Api.Infrastructure.Data;
using Sumitrack.Api.Models.Responses;

namespace Sumitrack.Api.Infrastructure.Auth;

public class TenantResolverMiddleware
{
    private readonly RequestDelegate _next;

    public TenantResolverMiddleware(RequestDelegate next) => _next = next;

    public async Task InvokeAsync(HttpContext context, ITenantContext tenantContext, AppDbContext publicCtx)
    {
        if (context.User.Identity?.IsAuthenticated != true)
        {
            await _next(context);
            return;
        }

        var tenantIdClaim = context.User.FindFirst("tenant_id")?.Value;
        if (tenantIdClaim != null && Guid.TryParse(tenantIdClaim, out var tenantId))
        {
            var tenant = await publicCtx.Tenants.FindAsync([tenantId], context.RequestAborted);
            if (tenant != null)
            {
                tenantContext.Initialize(tenant.Id, tenant.SchemaName);
            }
            else
            {
                context.Response.StatusCode = StatusCodes.Status401Unauthorized;
                context.Response.ContentType = "application/json";
                await context.Response.WriteAsJsonAsync(new ErrorResponse
                {
                    Errors = [new ApiError { Code = "TENANT_NOT_FOUND", Message = "No autorizado." }]
                });
                return;
            }
        }
        await _next(context);
    }
}

using Sumitrack.Api.Infrastructure.Data;

namespace Sumitrack.Api.Infrastructure.Auth;

public class TenantResolverMiddleware
{
    private readonly RequestDelegate _next;

    public TenantResolverMiddleware(RequestDelegate next) => _next = next;

    public async Task InvokeAsync(HttpContext context, ITenantContext tenantContext, AppDbContext publicCtx)
    {
        var tenantIdClaim = context.User.FindFirst("tenant_id")?.Value;
        if (tenantIdClaim != null && Guid.TryParse(tenantIdClaim, out var tenantId))
        {
            var tenant = await publicCtx.Tenants.FindAsync(tenantId);
            if (tenant != null)
            {
                tenantContext.TenantId = tenantId;
                tenantContext.SchemaName = tenant.SchemaName;
            }
        }
        await _next(context);
    }
}

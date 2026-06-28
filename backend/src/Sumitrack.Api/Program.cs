using Scalar.AspNetCore;
using Serilog;
using Serilog.Formatting.Compact;
using Sumitrack.Api.Infrastructure.Auth;
using Sumitrack.Api.Infrastructure.Extensions;
using Sumitrack.Api.Infrastructure.Logging;
using Sumitrack.Api.Models.Responses;

// Bootstrap logger captures startup errors before host builds
Log.Logger = new LoggerConfiguration()
    .WriteTo.Console(new CompactJsonFormatter())
    .CreateBootstrapLogger();

try
{
    var builder = WebApplication.CreateBuilder(args);

    // Serilog: JSON structured output for Railway logs
    builder.Host.UseSerilog(SerilogConfiguration.ConfigureSerilog);

    // Controllers + OpenAPI (for Scalar)
    builder.Services.AddControllers();
    builder.Services.AddOpenApi();

    // EF Core, JWT auth, multi-tenant services
    builder.Services.AddSumitrackServices(builder.Configuration);

    var app = builder.Build();

    // Global error middleware — must be first in the pipeline
    app.Use(async (context, next) =>
    {
        try
        {
            await next(context);
        }
        catch (AuthenticationException ex)
        {
            if (context.Response.HasStarted) throw;
            var logger = context.RequestServices.GetRequiredService<ILogger<Program>>();
            logger.LogWarning(ex, "Authentication failed: {Code}", ex.Code);
            context.Response.StatusCode = StatusCodes.Status401Unauthorized;
            context.Response.ContentType = "application/json";
            await context.Response.WriteAsJsonAsync(new ErrorResponse
            {
                Errors = [new ApiError { Code = ex.Code, Message = "No autorizado." }]
            });
        }
        catch (Exception ex)
        {
            if (context.Response.HasStarted) throw;
            var logger = context.RequestServices.GetRequiredService<ILogger<Program>>();
            logger.LogError(ex, "Unhandled exception");
            context.Response.StatusCode = StatusCodes.Status500InternalServerError;
            context.Response.ContentType = "application/json";
            await context.Response.WriteAsJsonAsync(new ErrorResponse
            {
                Errors = [new ApiError { Code = "INTERNAL_ERROR", Message = "Ocurrió un error inesperado." }]
            });
        }
    });

    // Scalar API docs — always available (auth-protected in production via network/gateway)
    app.MapOpenApi();
    app.MapScalarApiReference();

    app.UseHttpsRedirection();
    app.UseSerilogRequestLogging();

    app.UseAuthentication();
    // Populate ITenantContext from JWT claims after authentication
    app.UseMiddleware<TenantResolverMiddleware>();
    app.UseAuthorization();

    app.MapControllers();

    // Auto-apply EF Core migrations + create tenant schemas + seed Development
    await app.ApplyMigrationsAsync();

    app.Run();
}
catch (Exception ex)
{
    Log.Fatal(ex, "Application startup failed");
    throw;
}
finally
{
    Log.CloseAndFlush();
}

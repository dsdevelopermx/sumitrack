using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Diagnostics;
using Microsoft.IdentityModel.Tokens;
using Sumitrack.Api.Infrastructure.Auth;
using Sumitrack.Api.Infrastructure.Data;
using Sumitrack.Api.Services.Auth;
using System.Text;

namespace Sumitrack.Api.Infrastructure.Extensions;

public static class ServiceCollectionExtensions
{
    public static IServiceCollection AddSumitrackServices(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        var connStr = configuration.GetConnectionString("DefaultConnection")
            ?? throw new InvalidOperationException("ConnectionStrings:DefaultConnection not configured");

        // Public DbContext (public.tenants) — EF Core migrations
        services.AddDbContext<AppDbContext>(opt =>
            opt.UseNpgsql(connStr, npgsql =>
                npgsql.MigrationsHistoryTable("__ef_migrations_history", "public"))
               .ConfigureWarnings(w => w.Ignore(RelationalEventId.PendingModelChangesWarning)));

        // Tenant context — uses TenantSchemaInterceptor to SET search_path per request
        services.AddScoped<ITenantContext, TenantContext>();
        services.AddScoped<TenantSchemaInterceptor>();
        services.AddDbContext<TenantDbContext>((sp, opt) =>
        {
            var interceptor = sp.GetRequiredService<TenantSchemaInterceptor>();
            opt.UseNpgsql(connStr).AddInterceptors(interceptor);
        });

        // Tenant schema factory (used by AuthService for login — before JWT is issued)
        services.AddScoped<ITenantDbContextFactory, TenantDbContextFactory>();

        // Auth services
        services.AddScoped<IAuthService, AuthService>();

        // JWT authentication
        var jwtSecret = configuration["Jwt:Secret"]
            ?? throw new InvalidOperationException("Jwt:Secret not configured");

        if (Encoding.UTF8.GetByteCount(jwtSecret) < 32)
            throw new InvalidOperationException("Jwt:Secret must be at least 32 bytes for HMACSHA256");

        services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
            .AddJwtBearer(opt =>
            {
                opt.TokenValidationParameters = new TokenValidationParameters
                {
                    ValidateIssuerSigningKey = true,
                    IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtSecret)),
                    ValidateIssuer = true,
                    ValidIssuer = configuration["Jwt:Issuer"] ?? "sumitrack",
                    ValidateAudience = true,
                    ValidAudience = configuration["Jwt:Audience"] ?? "sumitrack-app",
                    ValidateLifetime = true,
                    ClockSkew = TimeSpan.Zero
                };
            });

        services.AddAuthorization();

        return services;
    }
}

using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Sumitrack.Api.Infrastructure.Auth;
using Sumitrack.Api.Infrastructure.Data;
using Sumitrack.Api.Models.Requests;
using Sumitrack.Api.Models.Responses;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;

namespace Sumitrack.Api.Services.Auth;

public class AuthService : IAuthService
{
    private readonly AppDbContext _publicCtx;
    private readonly ITenantDbContextFactory _tenantCtxFactory;
    private readonly IConfiguration _configuration;
    private readonly ILogger<AuthService> _logger;

    public AuthService(
        AppDbContext publicCtx,
        ITenantDbContextFactory tenantCtxFactory,
        IConfiguration configuration,
        ILogger<AuthService> logger)
    {
        _publicCtx = publicCtx;
        _tenantCtxFactory = tenantCtxFactory;
        _configuration = configuration;
        _logger = logger;
    }

    public async Task<LoginResponse> LoginAsync(
        LoginRequest request,
        CancellationToken cancellationToken = default)
    {
        var tenantSlug = _configuration["App:TenantSlug"]
            ?? throw new InvalidOperationException("App:TenantSlug not configured");

        var tenant = await _publicCtx.Tenants
            .FirstOrDefaultAsync(t => t.Slug == tenantSlug, cancellationToken)
            ?? throw new InvalidOperationException($"Tenant with slug '{tenantSlug}' not found — check App:TenantSlug configuration");

        using var tenantCtx = _tenantCtxFactory.Create(tenant.SchemaName);

        var user = await tenantCtx.Users
            .FirstOrDefaultAsync(u => u.Username == request.Username, cancellationToken);

        bool passwordValid;
        try
        {
            passwordValid = user is not null && BCrypt.Net.BCrypt.Verify(request.Password, user.PasswordHash);
        }
        catch (BCrypt.Net.SaltParseException)
        {
            _logger.LogError("BCrypt salt parse error for user '{Username}' — password hash is corrupt", request.Username);
            throw new AuthenticationException("INVALID_CREDENTIALS");
        }

        if (!passwordValid)
        {
            _logger.LogWarning("Failed login for username '{Username}'", request.Username);
            throw new AuthenticationException("INVALID_CREDENTIALS");
        }

        var expiresInDays = _configuration.GetValue<int>("Jwt:ExpiresInDays", 365);
        if (expiresInDays <= 0)
            throw new InvalidOperationException("Jwt:ExpiresInDays must be a positive integer");

        var expiresAt = DateTime.UtcNow.AddDays(expiresInDays);
        var token = GenerateJwt(user!.Id, tenant.Id, expiresAt);

        return new LoginResponse { Token = token, ExpiresAt = expiresAt };
    }

    private string GenerateJwt(Guid userId, Guid tenantId, DateTime expiresAt)
    {
        var secret = _configuration["Jwt:Secret"]
            ?? throw new InvalidOperationException("Jwt:Secret not configured");
        var issuer = _configuration["Jwt:Issuer"] ?? "sumitrack";
        var audience = _configuration["Jwt:Audience"] ?? "sumitrack-app";

        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(secret));
        var creds = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);

        var claims = new[]
        {
            new Claim(JwtRegisteredClaimNames.Sub, userId.ToString()),
            new Claim("tenant_id", tenantId.ToString()),
            new Claim(JwtRegisteredClaimNames.Jti, Guid.NewGuid().ToString())
        };

        var token = new JwtSecurityToken(
            issuer: issuer,
            audience: audience,
            claims: claims,
            expires: expiresAt,
            signingCredentials: creds);

        return new JwtSecurityTokenHandler().WriteToken(token);
    }
}

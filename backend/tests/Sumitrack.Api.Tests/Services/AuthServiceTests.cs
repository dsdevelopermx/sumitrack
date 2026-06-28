using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using Moq;
using Sumitrack.Api.Infrastructure.Data;
using Sumitrack.Api.Models.Entities;
using Sumitrack.Api.Models.Requests;
using Sumitrack.Api.Services.Auth;
using System.IdentityModel.Tokens.Jwt;
using Xunit;

namespace Sumitrack.Api.Tests.Services;

public class AuthServiceTests
{
    private static IConfiguration BuildTestConfig(string tenantSlug = "test") =>
        new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["App:TenantSlug"] = tenantSlug,
                ["Jwt:Secret"] = "test_secret_key_minimum_32_characters_for_hmac_sha256",
                ["Jwt:ExpiresInDays"] = "365",
                ["Jwt:Issuer"] = "sumitrack",
                ["Jwt:Audience"] = "sumitrack-app"
            })
            .Build();

    private static AppDbContext BuildPublicCtx(string dbName)
    {
        var options = new DbContextOptionsBuilder<AppDbContext>()
            .UseInMemoryDatabase(dbName)
            .Options;
        return new AppDbContext(options);
    }

    private static (TenantDbContext ctx, string schemaName) BuildTenantCtx(string dbName, Guid tenantId)
    {
        var schemaName = $"tenant_{tenantId:N}";
        var options = new DbContextOptionsBuilder<TenantDbContext>()
            .UseInMemoryDatabase(dbName)
            .Options;
        return (new TenantDbContext(options), schemaName);
    }

    [Fact]
    public async Task LoginAsync_ValidCredentials_ReturnsTokenWithCorrectClaims()
    {
        // Arrange
        var tenantId = Guid.NewGuid();
        var userId = Guid.NewGuid();
        var password = "TestPass123!";

        using var publicCtx = BuildPublicCtx($"pub_{nameof(LoginAsync_ValidCredentials_ReturnsTokenWithCorrectClaims)}");
        publicCtx.Tenants.Add(new Tenant { Id = tenantId, Slug = "test", SchemaName = $"tenant_{tenantId:N}" });
        await publicCtx.SaveChangesAsync();

        var (tenantCtx, schemaName) = BuildTenantCtx($"ten_{nameof(LoginAsync_ValidCredentials_ReturnsTokenWithCorrectClaims)}", tenantId);
        tenantCtx.Users.Add(new User
        {
            Id = userId,
            Username = "testuser",
            PasswordHash = BCrypt.Net.BCrypt.HashPassword(password),
            TenantId = tenantId
        });
        await tenantCtx.SaveChangesAsync();

        var mockFactory = new Mock<ITenantDbContextFactory>();
        mockFactory.Setup(f => f.Create(It.IsAny<string>())).Returns(tenantCtx);

        var service = new AuthService(publicCtx, mockFactory.Object, BuildTestConfig(), new Mock<ILogger<AuthService>>().Object);

        // Act
        var result = await service.LoginAsync(new LoginRequest { Username = "testuser", Password = password });

        // Assert
        Assert.NotEmpty(result.Token);
        Assert.True(result.ExpiresAt > DateTime.UtcNow);

        var jwt = new JwtSecurityTokenHandler().ReadJwtToken(result.Token);
        Assert.Equal(userId.ToString(), jwt.Subject);
        Assert.Contains(jwt.Claims, c => c.Type == "tenant_id" && c.Value == tenantId.ToString());

        await tenantCtx.DisposeAsync();
    }

    [Fact]
    public async Task LoginAsync_WrongPassword_ThrowsUnauthorized()
    {
        // Arrange
        var tenantId = Guid.NewGuid();

        using var publicCtx = BuildPublicCtx($"pub_{nameof(LoginAsync_WrongPassword_ThrowsUnauthorized)}");
        publicCtx.Tenants.Add(new Tenant { Id = tenantId, Slug = "test", SchemaName = $"tenant_{tenantId:N}" });
        await publicCtx.SaveChangesAsync();

        var (tenantCtx, _) = BuildTenantCtx($"ten_{nameof(LoginAsync_WrongPassword_ThrowsUnauthorized)}", tenantId);
        tenantCtx.Users.Add(new User
        {
            Id = Guid.NewGuid(),
            Username = "testuser",
            PasswordHash = BCrypt.Net.BCrypt.HashPassword("CorrectPassword"),
            TenantId = tenantId
        });
        await tenantCtx.SaveChangesAsync();

        var mockFactory = new Mock<ITenantDbContextFactory>();
        mockFactory.Setup(f => f.Create(It.IsAny<string>())).Returns(tenantCtx);

        var service = new AuthService(publicCtx, mockFactory.Object, BuildTestConfig(), new Mock<ILogger<AuthService>>().Object);

        // Act & Assert
        await Assert.ThrowsAsync<UnauthorizedAccessException>(() =>
            service.LoginAsync(new LoginRequest { Username = "testuser", Password = "WrongPassword" }));

        await tenantCtx.DisposeAsync();
    }

    [Fact]
    public async Task LoginAsync_UnknownUser_ThrowsUnauthorized()
    {
        // Arrange
        var tenantId = Guid.NewGuid();

        using var publicCtx = BuildPublicCtx($"pub_{nameof(LoginAsync_UnknownUser_ThrowsUnauthorized)}");
        publicCtx.Tenants.Add(new Tenant { Id = tenantId, Slug = "test", SchemaName = $"tenant_{tenantId:N}" });
        await publicCtx.SaveChangesAsync();

        var (tenantCtx, _) = BuildTenantCtx($"ten_{nameof(LoginAsync_UnknownUser_ThrowsUnauthorized)}", tenantId);
        // No users seeded

        var mockFactory = new Mock<ITenantDbContextFactory>();
        mockFactory.Setup(f => f.Create(It.IsAny<string>())).Returns(tenantCtx);

        var service = new AuthService(publicCtx, mockFactory.Object, BuildTestConfig(), new Mock<ILogger<AuthService>>().Object);

        // Act & Assert
        await Assert.ThrowsAsync<UnauthorizedAccessException>(() =>
            service.LoginAsync(new LoginRequest { Username = "nonexistent", Password = "any" }));

        await tenantCtx.DisposeAsync();
    }

    [Fact]
    public async Task LoginAsync_TenantSlugNotFound_ThrowsUnauthorized()
    {
        // Arrange — config points to non-existent tenant slug
        using var publicCtx = BuildPublicCtx($"pub_{nameof(LoginAsync_TenantSlugNotFound_ThrowsUnauthorized)}");
        // No tenants in DB

        var mockFactory = new Mock<ITenantDbContextFactory>();
        var service = new AuthService(
            publicCtx,
            mockFactory.Object,
            BuildTestConfig("nonexistent-slug"),
            new Mock<ILogger<AuthService>>().Object);

        // Act & Assert
        await Assert.ThrowsAsync<UnauthorizedAccessException>(() =>
            service.LoginAsync(new LoginRequest { Username = "admin", Password = "pass" }));
    }
}

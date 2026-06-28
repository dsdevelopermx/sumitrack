using Sumitrack.Api.Models.Requests;
using Sumitrack.Api.Models.Responses;

namespace Sumitrack.Api.Services.Auth;

public interface IAuthService
{
    Task<LoginResponse> LoginAsync(LoginRequest request, CancellationToken cancellationToken = default);
}

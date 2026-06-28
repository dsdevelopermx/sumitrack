namespace Sumitrack.Api.Infrastructure.Auth;

public class AuthenticationException : Exception
{
    public string Code { get; }

    public AuthenticationException(string code) : base(code)
    {
        Code = code;
    }
}

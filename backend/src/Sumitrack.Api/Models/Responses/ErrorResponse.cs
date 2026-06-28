namespace Sumitrack.Api.Models.Responses;

public class ErrorResponse
{
    public List<ApiError> Errors { get; set; } = [];
}

public class ApiError
{
    public string Code { get; set; } = string.Empty;
    public string Message { get; set; } = string.Empty;
}

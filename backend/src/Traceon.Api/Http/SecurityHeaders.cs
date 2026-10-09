namespace Traceon.Api.Http;

/// <summary>
/// Hardening headers for a JSON-only API: nothing it returns is meant to be rendered, framed or sniffed by a browser.
/// </summary>
internal static class SecurityHeaders
{
    private const string ContentSecurityPolicy = "default-src 'none'; frame-ancestors 'none'";

    /// <summary>Registers the headers on every response, including the ones written by the exception handler.</summary>
    /// <remarks>
    /// Headers are applied in <see cref="HttpResponse.OnStarting(Func{Task})"/> rather than before the next middleware
    /// because <c>UseExceptionHandler</c> clears the response headers before writing the 500.
    /// </remarks>
    public static IApplicationBuilder UseSecurityHeaders(this IApplicationBuilder app) =>
        app.Use(async (context, next) =>
        {
            context.Response.OnStarting(() =>
            {
                var headers = context.Response.Headers;
                headers.XContentTypeOptions = "nosniff";
                headers["Referrer-Policy"] = "no-referrer";
                headers.XFrameOptions = "DENY";
                headers.ContentSecurityPolicy = ContentSecurityPolicy;
                return Task.CompletedTask;
            });

            await next(context);
        });
}

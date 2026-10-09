using System.Net;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Diagnostics.HealthChecks;
using Traceon.IntegrationTests.Infrastructure;

namespace Traceon.IntegrationTests;

public sealed class SecurityHeadersTests
{
    [Theory]
    [InlineData("/health/live", HttpStatusCode.OK)]
    [InlineData("/health/ready", HttpStatusCode.ServiceUnavailable)]
    [InlineData("/this-route-does-not-exist", HttpStatusCode.NotFound)]
    public async Task Responses_carry_security_headers(string path, HttpStatusCode expected)
    {
        using var database = UnreachableDatabase.Create(UnreachableDatabaseKind.RefusesConnections);
        await using var factory = new TraceonApiFactory(database.ConnectionString);
        using var client = factory.CreateClient();

        using var response = await client.GetAsync(path, TestContext.Current.CancellationToken);

        Assert.Equal(expected, response.StatusCode);
        AssertSecurityHeaders(response);
    }

    // The exception handler clears the response headers before writing the 500, so this case is not implied by the others.
    [Fact]
    public async Task Unhandled_exception_response_keeps_security_headers()
    {
        using var database = UnreachableDatabase.Create(UnreachableDatabaseKind.RefusesConnections);
        await using var baseFactory = new TraceonApiFactory(database.ConnectionString);
        var factory = baseFactory.WithWebHostBuilder(builder => builder.ConfigureTestServices(services =>
            services.AddSingleton<HealthCheckService, ThrowingHealthCheckService>()));
        using var client = factory.CreateClient();

        using var response = await client.GetAsync("/health/ready", TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.InternalServerError, response.StatusCode);
        AssertSecurityHeaders(response);
    }

    private static void AssertSecurityHeaders(HttpResponseMessage response)
    {
        Assert.Equal("nosniff", HeaderValue(response, "X-Content-Type-Options"));
        Assert.Equal("no-referrer", HeaderValue(response, "Referrer-Policy"));
        Assert.Equal("DENY", HeaderValue(response, "X-Frame-Options"));
        Assert.Equal("default-src 'none'; frame-ancestors 'none'", HeaderValue(response, "Content-Security-Policy"));
    }

    private static string? HeaderValue(HttpResponseMessage response, string name) =>
        response.Headers.TryGetValues(name, out var values) ? string.Join(", ", values) : null;
}

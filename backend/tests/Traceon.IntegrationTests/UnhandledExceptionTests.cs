using System.Net;
using System.Text.Json;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Diagnostics.HealthChecks;
using Microsoft.Extensions.Logging;
using Traceon.IntegrationTests.Infrastructure;

namespace Traceon.IntegrationTests;

/// <summary>
/// A health endpoint whose <see cref="HealthCheckService"/> throws exercises the real <c>UseExceptionHandler</c>
/// pipeline (the "Testing" environment has no developer exception page, like Production).
/// </summary>
public sealed class UnhandledExceptionTests
{
    private static readonly string[] _forbiddenFragments =
    [
        "Exception", "InvalidOperation", " at ", "Traceon.", "Npgsql", "Host=", "CANARY-41d7a9e2",
    ];

    [Theory]
    [InlineData("/health/live")]
    [InlineData("/health/ready")]
    public async Task Unhandled_exception_returns_generic_500_problem_details_with_trace_id(string path)
    {
        using var database = UnreachableDatabase.Create(UnreachableDatabaseKind.RefusesConnections);
        await using var baseFactory = new TraceonApiFactory(database.ConnectionString);
        using var client = WithThrowingHealthChecks(baseFactory).CreateClient();

        using var response = await client.GetAsync(path, TestContext.Current.CancellationToken);

        var body = await response.Content.ReadAsStringAsync(TestContext.Current.CancellationToken);
        Assert.Equal(HttpStatusCode.InternalServerError, response.StatusCode);
        Assert.Equal("application/problem+json", response.Content.Headers.ContentType?.MediaType);
        Assert.False(string.IsNullOrWhiteSpace(TraceIdOf(body)), "the problem details must carry a traceId");
        Assert.All(_forbiddenFragments, fragment => Assert.DoesNotContain(fragment, body, StringComparison.Ordinal));
    }

    [Fact]
    public async Task Unhandled_exception_is_logged_with_the_trace_id_returned_to_the_client()
    {
        using var database = UnreachableDatabase.Create(UnreachableDatabaseKind.RefusesConnections);
        using var logs = new CapturingLoggerProvider();
        await using var baseFactory = new TraceonApiFactory(database.ConnectionString);
        var factory = WithThrowingHealthChecks(baseFactory).WithWebHostBuilder(builder =>
            builder.ConfigureLogging(logging => logging
                .AddProvider(logs)
                .AddFilter<CapturingLoggerProvider>(category: null, LogLevel.Trace)));
        using var client = factory.CreateClient();

        using var response = await client.GetAsync("/health/ready", TestContext.Current.CancellationToken);

        var traceId = W3CTraceIdOf(TraceIdOf(await response.Content.ReadAsStringAsync(TestContext.Current.CancellationToken)));
        Assert.Contains(logs.Entries, entry =>
            entry.Contains(ThrowingHealthCheckService.CanaryMessage, StringComparison.Ordinal)
            && entry.Contains(traceId, StringComparison.Ordinal));
    }

    private static WebApplicationFactory<Program> WithThrowingHealthChecks(WebApplicationFactory<Program> factory) =>
        factory.WithWebHostBuilder(builder => builder.ConfigureTestServices(services =>
            services.AddSingleton<HealthCheckService, ThrowingHealthCheckService>()));

    private static string? TraceIdOf(string problemDetails)
    {
        using var document = JsonDocument.Parse(problemDetails);
        return document.RootElement.TryGetProperty("traceId", out var traceId) ? traceId.GetString() : null;
    }

    // ProblemDetails uses Activity.Id ("00-<trace-id>-<span-id>-<flags>"); log scopes carry the bare trace id.
    private static string W3CTraceIdOf(string? activityId)
    {
        var parts = activityId?.Split('-') ?? [];
        Assert.True(parts.Length == 4, $"traceId '{activityId}' is not a W3C trace context id");
        return parts[1];
    }
}

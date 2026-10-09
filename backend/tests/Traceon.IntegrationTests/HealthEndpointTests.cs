using System.Diagnostics;
using System.Net;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Logging;
using Traceon.IntegrationTests.Infrastructure;

namespace Traceon.IntegrationTests;

public sealed class HealthEndpointTests(PostgresFixture postgres)
{
    private const string HealthyLiveBody = """{"status":"Healthy"}""";
    private const string HealthyReadyBody = """{"status":"Healthy","checks":[{"name":"database","status":"Healthy"}]}""";
    private const string UnhealthyReadyBody = """{"status":"Unhealthy","checks":[{"name":"database","status":"Unhealthy"}]}""";

    // Readiness gives the database 3 s; the margin absorbs host start-up and slow CI machines.
    private static readonly TimeSpan _maxUnhealthyReadinessDuration = TimeSpan.FromSeconds(10);

    // A hung request must fail the test instead of blocking the run for the default 100 s.
    private static readonly TimeSpan _clientTimeout = TimeSpan.FromSeconds(30);

    [Fact]
    public async Task Live_returns_healthy_when_database_is_available()
    {
        await using var factory = new TraceonApiFactory(postgres.ConnectionString);
        using var client = CreateClient(factory);

        using var response = await client.GetAsync("/health/live", TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(HealthyLiveBody, await response.Content.ReadAsStringAsync(TestContext.Current.CancellationToken));
    }

    [Theory]
    [InlineData(UnreachableDatabaseKind.RefusesConnections)]
    [InlineData(UnreachableDatabaseKind.NeverResponds)]
    public async Task Live_returns_healthy_when_database_is_unreachable(UnreachableDatabaseKind kind)
    {
        using var database = UnreachableDatabase.Create(kind);
        await using var factory = new TraceonApiFactory(database.ConnectionString);
        using var client = CreateClient(factory);

        using var response = await client.GetAsync("/health/live", TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(HealthyLiveBody, await response.Content.ReadAsStringAsync(TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task Ready_returns_healthy_database_check_when_database_is_available()
    {
        await using var factory = new TraceonApiFactory(postgres.ConnectionString);
        using var client = CreateClient(factory);

        using var response = await client.GetAsync("/health/ready", TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(HealthyReadyBody, await response.Content.ReadAsStringAsync(TestContext.Current.CancellationToken));
    }

    [Theory]
    [InlineData(UnreachableDatabaseKind.RefusesConnections)]
    [InlineData(UnreachableDatabaseKind.NeverResponds)]
    public async Task Ready_returns_503_unhealthy_promptly_when_database_is_unreachable(UnreachableDatabaseKind kind)
    {
        using var database = UnreachableDatabase.Create(kind);
        await using var factory = new TraceonApiFactory(database.ConnectionString);
        using var client = CreateClient(factory); // starts the host, so start-up stays out of the measurement

        var stopwatch = Stopwatch.StartNew();
        using var response = await client.GetAsync("/health/ready", TestContext.Current.CancellationToken);
        stopwatch.Stop();

        var body = await response.Content.ReadAsStringAsync(TestContext.Current.CancellationToken);
        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal(UnhealthyReadyBody, body);
        Assert.InRange(stopwatch.Elapsed, TimeSpan.Zero, _maxUnhealthyReadinessDuration);
        AssertNoDiagnosticLeak(body, database);
    }

    [Theory]
    [InlineData(UnreachableDatabaseKind.RefusesConnections)]
    [InlineData(UnreachableDatabaseKind.NeverResponds)]
    public async Task Ready_failure_logs_never_contain_the_database_password(UnreachableDatabaseKind kind)
    {
        using var database = UnreachableDatabase.Create(kind);
        using var logs = new CapturingLoggerProvider();
        // Disposing the base factory also disposes the derived one created by WithWebHostBuilder.
        await using var baseFactory = new TraceonApiFactory(database.ConnectionString);
        var factory = baseFactory.WithWebHostBuilder(builder =>
            builder.ConfigureLogging(logging => logging
                .AddProvider(logs)
                .AddFilter<CapturingLoggerProvider>(category: null, LogLevel.Trace)));
        using var client = CreateClient(factory);

        using var response = await client.GetAsync("/health/ready", TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Contains(logs.Entries, entry => entry.Contains("Health check database", StringComparison.Ordinal));
        Assert.DoesNotContain(logs.Entries, entry => entry.Contains(UnreachableDatabase.CanaryPassword, StringComparison.Ordinal));
    }

    [Theory]
    [InlineData("/health/live")]
    [InlineData("/health/ready")]
    public async Task Health_responses_are_uncacheable_json(string path)
    {
        await using var factory = new TraceonApiFactory(postgres.ConnectionString);
        using var client = CreateClient(factory);

        using var response = await client.GetAsync(path, TestContext.Current.CancellationToken);

        Assert.Equal("application/json", response.Content.Headers.ContentType?.MediaType);
        Assert.True(response.Headers.CacheControl?.NoStore, "Cache-Control must contain no-store");
    }

    [Theory]
    [InlineData("POST", "/health/live")]
    [InlineData("PUT", "/health/live")]
    [InlineData("DELETE", "/health/live")]
    [InlineData("POST", "/health/ready")]
    [InlineData("PUT", "/health/ready")]
    [InlineData("DELETE", "/health/ready")]
    public async Task Health_endpoints_reject_other_methods_with_405_problem_details(string method, string path)
    {
        using var database = UnreachableDatabase.Create(UnreachableDatabaseKind.RefusesConnections);
        await using var factory = new TraceonApiFactory(database.ConnectionString);
        using var client = CreateClient(factory);
        using var request = new HttpRequestMessage(new HttpMethod(method), path);

        using var response = await client.SendAsync(request, TestContext.Current.CancellationToken);

        var body = await response.Content.ReadAsStringAsync(TestContext.Current.CancellationToken);
        Assert.Equal(HttpStatusCode.MethodNotAllowed, response.StatusCode);
        Assert.Equal("application/problem+json", response.Content.Headers.ContentType?.MediaType);
        Assert.Contains("\"status\":405", body, StringComparison.Ordinal);
        Assert.Equal(["GET"], response.Content.Headers.Allow);
    }

    private static HttpClient CreateClient(WebApplicationFactory<Program> factory)
    {
        var client = factory.CreateClient();
        client.Timeout = _clientTimeout;
        return client;
    }

    // The exact-body assertions already rule these out; this states the security intent explicitly.
    private static void AssertNoDiagnosticLeak(string body, UnreachableDatabase database)
    {
        Assert.DoesNotContain(UnreachableDatabase.CanaryPassword, body, StringComparison.Ordinal);
        Assert.DoesNotContain(database.Port.ToString(System.Globalization.CultureInfo.InvariantCulture), body, StringComparison.Ordinal);
        Assert.DoesNotContain("Host=", body, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("Exception", body, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("Npgsql", body, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain(" at ", body, StringComparison.Ordinal);
    }
}

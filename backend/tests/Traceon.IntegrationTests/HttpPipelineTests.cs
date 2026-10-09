using System.Net;
using Traceon.IntegrationTests.Infrastructure;

namespace Traceon.IntegrationTests;

public sealed class HttpPipelineTests
{
    [Fact]
    public async Task Unknown_route_returns_problem_details_without_internal_details()
    {
        using var database = UnreachableDatabase.Create(UnreachableDatabaseKind.RefusesConnections);
        await using var factory = new TraceonApiFactory(database.ConnectionString);
        using var client = factory.CreateClient();

        using var response = await client.GetAsync("/this-route-does-not-exist", TestContext.Current.CancellationToken);

        var body = await response.Content.ReadAsStringAsync(TestContext.Current.CancellationToken);
        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.Equal("application/problem+json", response.Content.Headers.ContentType?.MediaType);
        Assert.Contains("\"status\":404", body, StringComparison.Ordinal);
        Assert.DoesNotContain("Exception", body, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("Traceon.", body, StringComparison.Ordinal);
        Assert.DoesNotContain(" at ", body, StringComparison.Ordinal);
    }

    [Theory]
    [InlineData("Development", HttpStatusCode.OK)]
    [InlineData("Production", HttpStatusCode.NotFound)]
    public async Task OpenApi_document_is_exposed_only_in_development(string environment, HttpStatusCode expected)
    {
        using var database = UnreachableDatabase.Create(UnreachableDatabaseKind.RefusesConnections);
        await using var factory = new TraceonApiFactory(database.ConnectionString, environment);
        using var client = factory.CreateClient();

        using var response = await client.GetAsync("/openapi/v1.json", TestContext.Current.CancellationToken);

        Assert.Equal(expected, response.StatusCode);
    }
}

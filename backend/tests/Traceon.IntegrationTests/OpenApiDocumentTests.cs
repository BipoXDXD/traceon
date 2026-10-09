using System.Net;
using System.Text.Json;
using Traceon.IntegrationTests.Infrastructure;

namespace Traceon.IntegrationTests;

public sealed class OpenApiDocumentTests
{
    [Fact]
    public async Task Document_lists_exactly_the_two_health_get_operations()
    {
        using var document = await GetDevelopmentDocumentAsync();

        var paths = document.RootElement.GetProperty("paths");
        Assert.Equal(["/health/live", "/health/ready"], PropertyNames(paths));
        Assert.Equal(["get"], PropertyNames(paths.GetProperty("/health/live")));
        Assert.Equal(["get"], PropertyNames(paths.GetProperty("/health/ready")));
    }

    [Theory]
    [InlineData("/health/live", "getLiveness", new[] { "200" })]
    [InlineData("/health/ready", "getReadiness", new[] { "200", "503" })]
    public async Task Health_operation_documents_its_identity_and_json_responses(
        string path, string operationId, string[] healthStatusCodes)
    {
        using var document = await GetDevelopmentDocumentAsync();

        var operation = document.RootElement.GetProperty("paths").GetProperty(path).GetProperty("get");
        Assert.Equal(operationId, operation.GetProperty("operationId").GetString());
        Assert.False(string.IsNullOrWhiteSpace(operation.GetProperty("summary").GetString()));
        Assert.Contains("diagnóstico", operation.GetProperty("description").GetString(), StringComparison.Ordinal);
        Assert.Equal(["Health"], operation.GetProperty("tags").EnumerateArray().Select(tag => tag.GetString()));

        var responses = operation.GetProperty("responses");
        Assert.Equal(healthStatusCodes.Append("500").Order(StringComparer.Ordinal), PropertyNames(responses).Order(StringComparer.Ordinal));
        Assert.All(healthStatusCodes, status =>
        {
            var response = responses.GetProperty(status);
            Assert.True(response.GetProperty("content").GetProperty("application/json").TryGetProperty("schema", out _),
                $"{path} {status} must document a JSON schema");
            Assert.True(response.GetProperty("headers").TryGetProperty("Cache-Control", out _),
                $"{path} {status} must document the Cache-Control policy");
        });
        Assert.True(responses.GetProperty("500").GetProperty("content").TryGetProperty("application/problem+json", out _),
            $"{path} 500 must document the problem details body");
    }

    private static async Task<JsonDocument> GetDevelopmentDocumentAsync()
    {
        using var database = UnreachableDatabase.Create(UnreachableDatabaseKind.RefusesConnections);
        await using var factory = new TraceonApiFactory(database.ConnectionString, "Development");
        using var client = factory.CreateClient();

        using var response = await client.GetAsync("/openapi/v1.json", TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return await JsonDocument.ParseAsync(
            await response.Content.ReadAsStreamAsync(TestContext.Current.CancellationToken),
            cancellationToken: TestContext.Current.CancellationToken);
    }

    private static string[] PropertyNames(JsonElement element) =>
        [.. element.EnumerateObject().Select(property => property.Name)];
}

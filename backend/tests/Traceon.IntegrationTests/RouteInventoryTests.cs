using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Http.Metadata;
using Microsoft.AspNetCore.Routing;
using Microsoft.Extensions.DependencyInjection;
using Traceon.IntegrationTests.Infrastructure;

namespace Traceon.IntegrationTests;

/// <summary>
/// Fitness function for "deny by default": every route the host registers must be on the public allowlist, so a new
/// endpoint cannot ship without a deliberate decision. When authentication arrives (stage 2), this test changes to
/// demand 401 without credentials for every route outside the allowlist.
/// </summary>
public sealed class RouteInventoryTests
{
    private static readonly string[] _publicRoutes = ["GET /health/live", "GET /health/ready"];

    private const string OpenApiDocumentRoute = "GET /openapi/{documentName}.json";

    [Theory]
    [InlineData("Testing")]
    [InlineData("Production")]
    public void Registered_routes_are_exactly_the_public_allowlist(string environment)
    {
        Assert.Equal(_publicRoutes, RegisteredRoutes(environment));
    }

    [Fact]
    public void Development_adds_only_the_openapi_document_to_the_allowlist()
    {
        Assert.Equal([.. _publicRoutes, OpenApiDocumentRoute], RegisteredRoutes("Development"));
    }

    private static string[] RegisteredRoutes(string environment)
    {
        using var database = UnreachableDatabase.Create(UnreachableDatabaseKind.RefusesConnections);
        using var factory = new TraceonApiFactory(database.ConnectionString, environment);

        // Services starts the host, so the endpoints mapped in Program.cs are already registered.
        var endpoints = factory.Services.GetRequiredService<EndpointDataSource>().Endpoints;

        return [.. endpoints.Select(Describe).Order(StringComparer.Ordinal)];
    }

    // An endpoint without method metadata answers every verb; "*" makes that visible instead of hiding it.
    private static string Describe(Endpoint endpoint)
    {
        var pattern = endpoint is RouteEndpoint route ? route.RoutePattern.RawText : endpoint.DisplayName;
        var methods = endpoint.Metadata.GetMetadata<IHttpMethodMetadata>()?.HttpMethods ?? ["*"];
        return $"{string.Join(',', methods)} {pattern}";
    }
}

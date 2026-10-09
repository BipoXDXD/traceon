using Microsoft.OpenApi;

namespace Traceon.Api.OpenApi;

/// <summary>Document-level OpenAPI metadata, shared by the build-time spec (docs/api) and the Development endpoint.</summary>
internal static class OpenApiDocumentSetup
{
    private const string Title = "Traceon API";

    private const string Description =
        "API do Traceon, SaaS de monitoramento de integridade de aplicações web. Na etapa Foundation expõe apenas " +
        "os endpoints de saúde (liveness e readiness), sem autenticação e sem dados de negócio.";

    public static IServiceCollection AddTraceonOpenApi(this IServiceCollection services) =>
        services.AddOpenApi(options => options.AddDocumentTransformer((document, _, _) =>
        {
            document.Info.Title = Title;
            document.Info.Description = Description;
            // Relative URL: the browser reaches the API on the frontend's origin through a proxy (ADR 0006), and a
            // host name would make the versioned spec depend on where it was generated.
            document.Servers = [new OpenApiServer { Url = "/", Description = "Mesma origem do frontend" }];
            return Task.CompletedTask;
        }));
}

using System.Net.Mime;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.OpenApi;
using Microsoft.Extensions.Diagnostics.HealthChecks;
using Microsoft.Net.Http.Headers;
using Microsoft.OpenApi;

namespace Traceon.Api.Health;

internal static class HealthEndpoints
{
    public const string ReadyTag = "ready";

    private const string OpenApiTag = "Health";
    private const string NoStore = "no-store";

    public static IEndpointRouteBuilder MapHealthEndpoints(this IEndpointRouteBuilder endpoints)
    {
        var health = endpoints.MapGroup("/health")
            .WithTags(OpenApiTag)
            .ProducesProblem(StatusCodes.Status500InternalServerError)
            .AddEndpointFilter(async (context, next) =>
            {
                context.HttpContext.Response.Headers.CacheControl = NoStore;
                return await next(context);
            })
            .AddOpenApiOperationTransformer((operation, _, _) =>
            {
                DocumentNoStoreHeader(operation);
                return Task.CompletedTask;
            });

        health.MapGet("/live", GetLivenessAsync)
            .WithName("getLiveness")
            .WithSummary("Verifica se o processo da API está vivo")
            .WithDescription(
                "Não executa nenhuma verificação de dependência: uma falha do banco de dados não deve levar o " +
                "orquestrador a reiniciar um processo saudável. Responde 200 enquanto o processo atende. " +
                "Sem autenticação, para o orquestrador e o proxy; por isso o corpo é mínimo e não expõe " +
                "diagnóstico (descrição, exceção, duração, host ou porta).");

        health.MapGet("/ready", GetReadinessAsync)
            .WithName("getReadiness")
            .WithSummary("Verifica se a API está pronta para receber tráfego")
            .WithDescription(
                "Executa as verificações de prontidão; hoje, abre uma conexão real com o banco de dados, limitada " +
                "a 3 segundos. Responde 200 quando todas estão Healthy ou Degraded e 503 quando alguma está " +
                "Unhealthy. Sem autenticação; o corpo traz só o status geral e, por verificação, nome e status, " +
                "sem diagnóstico (descrição, exceção, duração, host, porta ou connection string).")
            .Produces<ReadinessResponse>(StatusCodes.Status503ServiceUnavailable);

        return endpoints;
    }

    // Liveness runs no check: a database outage must not make the orchestrator restart a healthy process.
    private static async Task<Ok<LivenessResponse>> GetLivenessAsync(
        HealthCheckService healthChecks, CancellationToken cancellationToken)
    {
        var report = await healthChecks.CheckHealthAsync(_ => false, cancellationToken);
        return TypedResults.Ok(new LivenessResponse(report.Status));
    }

    private static async Task<Results<Ok<ReadinessResponse>, JsonHttpResult<ReadinessResponse>>> GetReadinessAsync(
        HealthCheckService healthChecks, CancellationToken cancellationToken)
    {
        var report = await healthChecks.CheckHealthAsync(
            registration => registration.Tags.Contains(ReadyTag), cancellationToken);

        var body = new ReadinessResponse(
            report.Status,
            [.. report.Entries.Select(entry => new CheckResponse(entry.Key, entry.Value.Status))]);

        // Degraded still serves traffic: only Unhealthy takes the instance out of rotation.
        return report.Status == HealthStatus.Unhealthy
            ? TypedResults.Json(body, statusCode: StatusCodes.Status503ServiceUnavailable)
            : TypedResults.Ok(body);
    }

    // Only the health bodies: the 500 written by the exception handler carries its own "no-cache,no-store".
    private static void DocumentNoStoreHeader(OpenApiOperation operation)
    {
        var healthResponses = (operation.Responses ?? []).Values.OfType<OpenApiResponse>()
            .Where(response => response.Content?.ContainsKey(MediaTypeNames.Application.Json) == true);

        foreach (var response in healthResponses)
        {
            response.Headers ??= new Dictionary<string, IOpenApiHeader>();
            response.Headers[HeaderNames.CacheControl] = new OpenApiHeader
            {
                Description = "Sempre no-store: o estado de saúde muda a cada chamada e não pode ser reaproveitado.",
                Schema = new OpenApiSchema { Type = JsonSchemaType.String, Enum = [NoStore] },
            };
        }
    }
}

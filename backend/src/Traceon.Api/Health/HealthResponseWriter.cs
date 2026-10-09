using System.Text.Json;
using Microsoft.Extensions.Diagnostics.HealthChecks;

namespace Traceon.Api.Health;

/// <summary>
/// Writes the health contract consumed by the frontend. It is an allowlist: only names and statuses leave
/// the process, never descriptions, exceptions, durations or data, which could reveal connection details.
/// </summary>
internal static class HealthResponseWriter
{
    private const string JsonContentType = "application/json";

    private static readonly JsonSerializerOptions _serializerOptions = new(JsonSerializerDefaults.Web);

    public static Task WriteLivenessAsync(HttpContext context, HealthReport report) =>
        context.Response.WriteAsJsonAsync(
            new LivenessResponse(report.Status.ToString()), _serializerOptions, JsonContentType, context.RequestAborted);

    public static Task WriteReadinessAsync(HttpContext context, HealthReport report)
    {
        var checks = report.Entries
            .Select(entry => new CheckResponse(entry.Key, entry.Value.Status.ToString()))
            .ToList();

        return context.Response.WriteAsJsonAsync(
            new ReadinessResponse(report.Status.ToString(), checks), _serializerOptions, JsonContentType, context.RequestAborted);
    }

    private sealed record LivenessResponse(string Status);

    private sealed record ReadinessResponse(string Status, IReadOnlyList<CheckResponse> Checks);

    private sealed record CheckResponse(string Name, string Status);
}

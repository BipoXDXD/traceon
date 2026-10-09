using System.ComponentModel.DataAnnotations;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Diagnostics.HealthChecks;

namespace Traceon.Api.Health;

// The health contract consumed by the frontend. It is an allowlist: only names and statuses leave the process,
// never descriptions, exceptions, durations or data, which could reveal connection details.

internal sealed record LivenessResponse(
    [property: JsonConverter(typeof(JsonStringEnumConverter<HealthStatus>))] HealthStatus Status);

internal sealed record ReadinessResponse(
    [property: JsonConverter(typeof(JsonStringEnumConverter<HealthStatus>))] HealthStatus Status,
    [property: MaxLength(ReadinessResponse.MaxChecks)] IReadOnlyList<CheckResponse> Checks)
{
    // Upper bound documented in the contract; today there is a single readiness check (database).
    public const int MaxChecks = 16;
}

internal sealed record CheckResponse(
    [property: MaxLength(CheckResponse.MaxNameLength), RegularExpression(CheckResponse.NamePattern)] string Name,
    [property: JsonConverter(typeof(JsonStringEnumConverter<HealthStatus>))] HealthStatus Status)
{
    public const int MaxNameLength = 64;

    // Check names are code constants ("database"), never data; the pattern documents that in the contract.
    public const string NamePattern = "^[a-z][a-z0-9-]*$";
}

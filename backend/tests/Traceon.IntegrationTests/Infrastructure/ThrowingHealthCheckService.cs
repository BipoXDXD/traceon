using Microsoft.Extensions.Diagnostics.HealthChecks;

namespace Traceon.IntegrationTests.Infrastructure;

/// <summary>
/// Replaces the health check service in the test host so a real endpoint throws through the production pipeline
/// (<c>UseExceptionHandler</c>), without adding a test-only route to the API.
/// </summary>
internal sealed class ThrowingHealthCheckService : HealthCheckService
{
    public const string CanaryMessage =
        "CANARY-41d7a9e2-unhandled Npgsql Host=db.internal;Password=CANARY-41d7a9e2 at Traceon.Secret.Method()";

    public override Task<HealthReport> CheckHealthAsync(Func<HealthCheckRegistration, bool>? predicate,
        CancellationToken cancellationToken = default) =>
        throw new InvalidOperationException(CanaryMessage);
}

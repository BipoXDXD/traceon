using Microsoft.Extensions.Diagnostics.HealthChecks;
using Microsoft.Extensions.Options;
using Npgsql;

namespace Traceon.Infrastructure.Persistence;

/// <summary>Readiness probe: opens a real, unpooled connection within <see cref="Timeout"/>.</summary>
/// <remarks>
/// The probe uses its own copy of the connection string because Npgsql ignores the cancellation token while
/// waiting for the server's startup reply (a silent server held the probe for the full default Timeout of 15 s).
/// Only the probe gets the short Timeout; the application's connections keep whatever the operator configured.
/// Pooling is off so every probe reaches the server: a pooled open can return an idle connector without any
/// network round trip and report a dead database as ready.
/// Failures surface as exceptions, which the health check service turns into Unhealthy; the response writer
/// never serializes them.
/// </remarks>
internal sealed class DatabaseHealthCheck(IOptions<DatabaseOptions> options) : IHealthCheck
{
    public static readonly TimeSpan Timeout = TimeSpan.FromSeconds(3);

    public async Task<HealthCheckResult> CheckHealthAsync(HealthCheckContext context, CancellationToken cancellationToken = default)
    {
        await using var connection = new NpgsqlConnection(ProbeConnectionString(options.Value.ConnectionString));
        await connection.OpenAsync(cancellationToken);
        return HealthCheckResult.Healthy();
    }

    private static string ProbeConnectionString(string connectionString) =>
        new NpgsqlConnectionStringBuilder(connectionString)
        {
            Pooling = false,
            Timeout = (int)Timeout.TotalSeconds,
        }.ConnectionString;
}

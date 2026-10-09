using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Diagnostics.HealthChecks;
using Microsoft.Extensions.Options;
using Traceon.Infrastructure.Persistence;

namespace Traceon.Infrastructure;

public static class InfrastructureServiceCollectionExtensions
{
    // Part of the /health/ready response contract consumed by the frontend.
    private const string DatabaseHealthCheckName = "database";

    /// <summary>Registers persistence. The host fails on start if <c>ConnectionStrings:Traceon</c> is missing or malformed.</summary>
    public static IServiceCollection AddInfrastructure(this IServiceCollection services, IConfiguration configuration)
    {
        services.AddOptions<DatabaseOptions>()
            .Configure(options =>
                options.ConnectionString = configuration.GetConnectionString(DatabaseOptions.ConnectionStringName) ?? string.Empty)
            .Validate(options => options.HasConnectionString, DatabaseOptions.MissingMessage)
            .Validate(options => !options.HasConnectionString || options.IsWellFormed(), DatabaseOptions.MalformedMessage)
            .ValidateOnStart();

        // No Timeout/Command Timeout override: Npgsql's defaults (15 s / 30 s) already bound every call, and the
        // operator can tune them in the connection string. EnableSensitiveDataLogging is never turned on:
        // parameter values could carry personal data.
        services.AddDbContext<TraceonDbContext>((provider, options) =>
            options.UseNpgsql(provider.GetRequiredService<IOptions<DatabaseOptions>>().Value.ConnectionString));

        return services;
    }

    /// <summary>Adds a check that opens a real database connection, bounded by a short timeout.</summary>
    public static IHealthChecksBuilder AddDatabaseHealthCheck(this IHealthChecksBuilder builder, IEnumerable<string> tags) =>
        builder.AddCheck<DatabaseHealthCheck>(DatabaseHealthCheckName, HealthStatus.Unhealthy, tags, DatabaseHealthCheck.Timeout);
}

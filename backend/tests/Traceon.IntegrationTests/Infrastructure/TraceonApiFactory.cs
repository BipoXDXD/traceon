using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Configuration;

namespace Traceon.IntegrationTests.Infrastructure;

/// <summary>
/// Hosts the real API in memory. Only the connection string and the environment change between tests;
/// "Testing" is the default so User Secrets (Development only) never leak into a test run.
/// </summary>
internal sealed class TraceonApiFactory(string? connectionString, string environment = "Testing")
    : WebApplicationFactory<Program>
{
    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseEnvironment(environment);

        // Added last, so it overrides any ConnectionStrings__Traceon in the test process environment;
        // a null value makes the "missing" case real instead of depending on the shell.
        builder.ConfigureAppConfiguration(config => config.AddInMemoryCollection(
            [new KeyValuePair<string, string?>("ConnectionStrings:Traceon", connectionString)]));
    }
}

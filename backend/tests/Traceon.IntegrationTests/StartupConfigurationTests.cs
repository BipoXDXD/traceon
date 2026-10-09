using System.Reflection;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Options;
using Traceon.Infrastructure;

namespace Traceon.IntegrationTests;

/// <summary>
/// Never goes through WebApplicationFactory: when start-up fails, the factory races the entry point, which disposes
/// the host, and intermittently surfaces an ObjectDisposedException instead of the validation error. The validation
/// cases run on a bare host with the real persistence registration; one case runs the real entry point on the test
/// thread to prove Program wires that validation into start-up.
/// </summary>
public sealed class StartupConfigurationTests
{
    private const string ConnectionStringKey = "ConnectionStrings:Traceon";

    // Bounds the entry-point test if start-up ever succeeds, since app.Run would then block forever.
    private static readonly TimeSpan _entryPointTimeout = TimeSpan.FromSeconds(30);

    [Fact]
    public async Task Api_entry_point_fails_to_start_with_empty_connection_string()
    {
        string[] args = ["--environment=Testing", "--urls=http://127.0.0.1:0", $"--{ConnectionStringKey}="];
        var entryPoint = typeof(Program).Assembly.EntryPoint
            ?? throw new InvalidOperationException("The API assembly has no entry point.");

        var run = Task.Run(() => entryPoint.Invoke(null, [args]), TestContext.Current.CancellationToken);

        var exception = await Assert.ThrowsAsync<TargetInvocationException>(
            () => run.WaitAsync(_entryPointTimeout, TestContext.Current.CancellationToken));
        var validation = Assert.IsType<OptionsValidationException>(exception.InnerException);
        Assert.Contains(ConnectionStringKey, validation.Message, StringComparison.Ordinal);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    public async Task Host_fails_to_start_without_connection_string(string? connectionString)
    {
        using var host = BuildHost(connectionString);

        var exception = await Assert.ThrowsAsync<OptionsValidationException>(
            () => host.StartAsync(TestContext.Current.CancellationToken));

        Assert.Contains(ConnectionStringKey, exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public async Task Host_fails_to_start_with_malformed_connection_string_without_echoing_it()
    {
        const string canary = "CANARY-2b9d-malformed";
        using var host = BuildHost($"Host=localhost;Password={canary};Not A Real Keyword=1");

        var exception = await Assert.ThrowsAsync<OptionsValidationException>(
            () => host.StartAsync(TestContext.Current.CancellationToken));

        Assert.Contains(ConnectionStringKey, exception.Message, StringComparison.Ordinal);
        Assert.DoesNotContain(canary, exception.ToString(), StringComparison.Ordinal);
    }

    private static IHost BuildHost(string? connectionString)
    {
        // DisableDefaults keeps environment variables and appsettings out, so the "missing" case is real.
        var builder = Host.CreateEmptyApplicationBuilder(new HostApplicationBuilderSettings { DisableDefaults = true });
        builder.Configuration.AddInMemoryCollection([new KeyValuePair<string, string?>(ConnectionStringKey, connectionString)]);
        builder.Services.AddInfrastructure(builder.Configuration);
        return builder.Build();
    }
}

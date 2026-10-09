using Microsoft.Extensions.Options;
using Traceon.IntegrationTests.Infrastructure;

namespace Traceon.IntegrationTests;

public sealed class StartupConfigurationTests
{
    private const string ConnectionStringKey = "ConnectionStrings:Traceon";

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    public void App_fails_to_start_without_connection_string(string? connectionString)
    {
        using var factory = new TraceonApiFactory(connectionString);

        var exception = Assert.Throws<OptionsValidationException>(() => factory.CreateClient());

        Assert.Contains(ConnectionStringKey, exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void App_fails_to_start_with_malformed_connection_string_without_echoing_it()
    {
        const string canary = "CANARY-2b9d-malformed";
        using var factory = new TraceonApiFactory($"Host=localhost;Password={canary};Not A Real Keyword=1");

        var exception = Assert.Throws<OptionsValidationException>(() => factory.CreateClient());

        Assert.Contains(ConnectionStringKey, exception.Message, StringComparison.Ordinal);
        Assert.DoesNotContain(canary, exception.ToString(), StringComparison.Ordinal);
    }
}

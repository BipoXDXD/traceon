using Testcontainers.PostgreSql;

[assembly: AssemblyFixture(typeof(Traceon.IntegrationTests.Infrastructure.PostgresFixture))]

namespace Traceon.IntegrationTests.Infrastructure;

/// <summary>One PostgreSQL container for the whole test assembly, same image as compose.yaml.</summary>
public sealed class PostgresFixture : IAsyncLifetime
{
    private const string Image = "postgres:18.6-alpine3.24";

    private readonly PostgreSqlContainer _container = new PostgreSqlBuilder(Image).Build();

    public string ConnectionString => _container.GetConnectionString();

    public async ValueTask InitializeAsync() => await _container.StartAsync();

    public async ValueTask DisposeAsync() => await _container.DisposeAsync();
}

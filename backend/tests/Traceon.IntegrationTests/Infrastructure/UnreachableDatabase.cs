using System.Net;
using System.Net.Sockets;

namespace Traceon.IntegrationTests.Infrastructure;

public enum UnreachableDatabaseKind
{
    /// <summary>Nothing listens on the port: the connection is refused immediately.</summary>
    RefusesConnections,

    /// <summary>TCP handshake succeeds but the server never answers: only a timeout ends the attempt.</summary>
    NeverResponds,
}

/// <summary>A local endpoint that looks like a database host but never serves PostgreSQL.</summary>
internal sealed class UnreachableDatabase : IDisposable
{
    public const string CanaryPassword = "CANARY-6f1c2e0a-unreachable-db";

    private readonly TcpListener? _silentListener;

    private UnreachableDatabase(int port, TcpListener? silentListener)
    {
        Port = port;
        _silentListener = silentListener;
    }

    public int Port { get; }

    public string ConnectionString =>
        $"Host=127.0.0.1;Port={Port};Database=traceon;Username=traceon;Password={CanaryPassword}";

    public static UnreachableDatabase Create(UnreachableDatabaseKind kind) => kind switch
    {
        UnreachableDatabaseKind.RefusesConnections => new UnreachableDatabase(ReleasedPort(), silentListener: null),
        UnreachableDatabaseKind.NeverResponds => Silent(),
        _ => throw new ArgumentOutOfRangeException(nameof(kind), kind, null),
    };

    public void Dispose() => _silentListener?.Dispose();

    // The kernel completes the handshake into the backlog even though Accept is never called,
    // so the client sends its startup message and waits for a reply that never comes.
    private static UnreachableDatabase Silent()
    {
        var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        return new UnreachableDatabase(((IPEndPoint)listener.LocalEndpoint).Port, listener);
    }

    private static int ReleasedPort()
    {
        using var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        var port = ((IPEndPoint)listener.LocalEndpoint).Port;
        listener.Stop();
        return port;
    }
}

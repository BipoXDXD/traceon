using System.Collections.Concurrent;
using System.Text;
using Microsoft.Extensions.Logging;

namespace Traceon.IntegrationTests.Infrastructure;

/// <summary>
/// Keeps every formatted log message, its exception text and its scopes (request id, trace id) so tests can assert
/// what the host wrote and correlate it with a response.
/// </summary>
internal sealed class CapturingLoggerProvider : ILoggerProvider, ISupportExternalScope
{
    private readonly ConcurrentQueue<string> _entries = new();
    private IExternalScopeProvider _scopes = new LoggerExternalScopeProvider();

    public IReadOnlyCollection<string> Entries => _entries;

    public ILogger CreateLogger(string categoryName) => new CapturingLogger(_entries, () => _scopes);

    public void SetScopeProvider(IExternalScopeProvider scopeProvider) => _scopes = scopeProvider;

    public void Dispose()
    {
    }

    private sealed class CapturingLogger(ConcurrentQueue<string> entries, Func<IExternalScopeProvider> scopes) : ILogger
    {
        public IDisposable? BeginScope<TState>(TState state)
            where TState : notnull => scopes().Push(state);

        public bool IsEnabled(LogLevel logLevel) => true;

        public void Log<TState>(LogLevel logLevel, EventId eventId, TState state, Exception? exception,
            Func<TState, Exception?, string> formatter)
        {
            var entry = new StringBuilder($"{formatter(state, exception)} {exception}");
            scopes().ForEachScope((scope, builder) => builder.Append(" => ").Append(ScopeText(scope)), entry);
            entries.Enqueue(entry.ToString());
        }

        // Activity scopes expose TraceId only as a key/value pair; their ToString() is not guaranteed to include it.
        private static string? ScopeText(object? scope) =>
            scope is IEnumerable<KeyValuePair<string, object?>> pairs
                ? string.Join(", ", pairs.Select(pair => $"{pair.Key}:{pair.Value}"))
                : scope?.ToString();
    }
}

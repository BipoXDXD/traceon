using Npgsql;

namespace Traceon.Infrastructure.Persistence;

internal sealed class DatabaseOptions
{
    public const string ConnectionStringName = "Traceon";
    public const string ConnectionStringKey = "ConnectionStrings:" + ConnectionStringName;

    public string ConnectionString { get; set; } = string.Empty;

    // Messages name the key, never the value: the connection string carries the password.
    public static string MissingMessage =>
        $"{ConnectionStringKey} is required. Set it with User Secrets in Development or the ConnectionStrings__{ConnectionStringName} environment variable.";

    public static string MalformedMessage => $"{ConnectionStringKey} is not a valid Npgsql connection string.";

    public bool HasConnectionString => !string.IsNullOrWhiteSpace(ConnectionString);

    public bool IsWellFormed()
    {
        try
        {
            _ = new NpgsqlConnectionStringBuilder(ConnectionString);
            return true;
        }
        catch (ArgumentException)
        {
            // Npgsql reports malformed strings only by throwing; the message is replaced so the value is never echoed.
            return false;
        }
    }
}

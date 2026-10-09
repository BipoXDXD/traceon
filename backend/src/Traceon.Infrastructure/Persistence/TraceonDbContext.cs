using Microsoft.EntityFrameworkCore;

namespace Traceon.Infrastructure.Persistence;

// No DbSets yet: the Foundation has no entities (ADR 0001). Each module adds its own schema when it gets tables.
internal sealed class TraceonDbContext(DbContextOptions<TraceonDbContext> options) : DbContext(options);

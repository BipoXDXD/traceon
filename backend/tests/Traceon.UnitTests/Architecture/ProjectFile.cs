using System.Xml.Linq;

namespace Traceon.UnitTests.Architecture;

/// <summary>Read-only view of the references declared in one of the solution's .csproj files.</summary>
internal sealed class ProjectFile
{
    private const string SolutionFileName = "Traceon.slnx";

    private ProjectFile(IReadOnlyList<string> projectReferences, IReadOnlyList<string> packageReferences,
        IReadOnlyList<string> frameworkReferences)
    {
        ProjectReferences = projectReferences;
        PackageReferences = packageReferences;
        FrameworkReferences = frameworkReferences;
    }

    /// <summary>Referenced project names, without directory or extension (e.g. "Traceon.Domain").</summary>
    public IReadOnlyList<string> ProjectReferences { get; }

    public IReadOnlyList<string> PackageReferences { get; }

    public IReadOnlyList<string> FrameworkReferences { get; }

    public static IReadOnlyList<string> SourceProjectNames =>
        [.. Directory.GetDirectories(SourceDirectory()).Select(Path.GetFileName).OfType<string>().Order()];

    public static ProjectFile Load(string projectName)
    {
        var path = Path.Combine(SourceDirectory(), projectName, projectName + ".csproj");
        var document = XDocument.Load(path);

        return new ProjectFile(
            [.. IncludesOf(document, "ProjectReference").Select(ProjectNameFromPath)],
            [.. IncludesOf(document, "PackageReference")],
            [.. IncludesOf(document, "FrameworkReference")]);
    }

    private static IEnumerable<string> IncludesOf(XDocument document, string itemType) =>
        document.Descendants(itemType)
            .Select(item => item.Attribute("Include")?.Value)
            .OfType<string>();

    // ProjectReference paths use backslashes even on macOS/Linux, so Path.GetFileName alone is not enough.
    private static string ProjectNameFromPath(string includePath) =>
        Path.GetFileNameWithoutExtension(includePath.Replace('\\', '/').Split('/')[^1]);

    private static string SourceDirectory() => Path.Combine(BackendDirectory(), "src");

    private static string BackendDirectory()
    {
        for (var directory = new DirectoryInfo(AppContext.BaseDirectory); directory is not null; directory = directory.Parent)
        {
            if (File.Exists(Path.Combine(directory.FullName, SolutionFileName)))
            {
                return directory.FullName;
            }
        }

        throw new InvalidOperationException(
            $"{SolutionFileName} not found above {AppContext.BaseDirectory}; architecture tests need the source tree.");
    }
}

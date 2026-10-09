namespace Traceon.UnitTests.Architecture;

// Fitness functions for ADR 0001: project references only point inwards (Api -> Infrastructure -> Application -> Domain).
public sealed class DependencyRuleTests
{
    private static readonly string[] _infrastructurePackagePrefixes =
        ["Microsoft.EntityFrameworkCore", "Npgsql", "Microsoft.AspNetCore"];

    [Fact]
    public void Domain_references_no_project_package_or_framework()
    {
        var domain = ProjectFile.Load("Traceon.Domain");

        Assert.Empty(domain.ProjectReferences);
        Assert.Empty(domain.PackageReferences);
        Assert.Empty(domain.FrameworkReferences);
    }

    [Fact]
    public void Application_references_only_domain()
    {
        var application = ProjectFile.Load("Traceon.Application");

        Assert.Equal(["Traceon.Domain"], application.ProjectReferences);
    }

    [Fact]
    public void Application_references_no_infrastructure_package_or_aspnetcore_framework()
    {
        var application = ProjectFile.Load("Traceon.Application");

        Assert.DoesNotContain(application.PackageReferences, IsInfrastructurePackage);
        Assert.Empty(application.FrameworkReferences);
    }

    [Fact]
    public void Infrastructure_does_not_depend_on_aspnetcore()
    {
        var infrastructure = ProjectFile.Load("Traceon.Infrastructure");

        Assert.DoesNotContain(infrastructure.PackageReferences,
            package => package.StartsWith("Microsoft.AspNetCore", StringComparison.Ordinal));
        Assert.Empty(infrastructure.FrameworkReferences);
    }

    [Theory]
    [InlineData("Traceon.Domain")]
    [InlineData("Traceon.Application")]
    [InlineData("Traceon.Infrastructure")]
    public void Api_is_not_referenced_by_inner_projects(string projectName)
    {
        var project = ProjectFile.Load(projectName);

        Assert.DoesNotContain("Traceon.Api", project.ProjectReferences);
    }

    [Fact]
    public void Every_source_project_is_covered_by_the_api_reference_rule()
    {
        // Guards the Theory above: a new project under src/ must be added to its InlineData.
        Assert.Equal(
            ["Traceon.Api", "Traceon.Application", "Traceon.Domain", "Traceon.Infrastructure"],
            ProjectFile.SourceProjectNames);
    }

    private static bool IsInfrastructurePackage(string package) =>
        _infrastructurePackagePrefixes.Any(prefix => package.StartsWith(prefix, StringComparison.Ordinal));
}

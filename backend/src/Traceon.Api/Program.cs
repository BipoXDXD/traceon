using System.Reflection;
using Traceon.Api.Health;
using Traceon.Api.Http;
using Traceon.Api.OpenApi;
using Traceon.Infrastructure;

var builder = WebApplication.CreateBuilder(args);

// Catch captive dependencies and missing registrations at start-up in every environment, not only Development.
builder.Host.UseDefaultServiceProvider(options =>
{
    options.ValidateScopes = true;
    options.ValidateOnBuild = true;
});

builder.Services.AddProblemDetails();
builder.Services.AddTraceonOpenApi();

// Build-time OpenAPI generation (Microsoft.Extensions.ApiDescription.Server) runs this entry point with a mock server
// and only reads the endpoint metadata. Skipping persistence there means the build needs no connection string and
// never touches a database; every real run still registers it and fails on start without the connection string.
var isGeneratingOpenApiDocument = Assembly.GetEntryAssembly()?.GetName().Name == "GetDocument.Insider";
if (!isGeneratingOpenApiDocument)
{
    builder.Services.AddInfrastructure(builder.Configuration);
}

builder.Services.AddHealthChecks().AddDatabaseHealthCheck([HealthEndpoints.ReadyTag]);

var app = builder.Build();

app.UseSecurityHeaders();
app.UseExceptionHandler();
app.UseStatusCodePages();

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.MapHealthEndpoints();

app.Run();

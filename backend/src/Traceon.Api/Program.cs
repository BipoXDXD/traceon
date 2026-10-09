using Traceon.Api.Health;
using Traceon.Infrastructure;

var builder = WebApplication.CreateBuilder(args);

// Catch captive dependencies and missing registrations at start-up in every environment, not only Development.
builder.Host.UseDefaultServiceProvider(options =>
{
    options.ValidateScopes = true;
    options.ValidateOnBuild = true;
});

builder.Services.AddProblemDetails();
builder.Services.AddOpenApi();
builder.Services.AddInfrastructure(builder.Configuration);
builder.Services.AddHealthChecks().AddDatabaseHealthCheck([HealthEndpoints.ReadyTag]);

var app = builder.Build();

app.UseExceptionHandler();
app.UseStatusCodePages();

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.MapHealthEndpoints();

app.Run();

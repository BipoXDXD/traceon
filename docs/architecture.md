# Arquitetura do Traceon (estado da Foundation)

Este documento descreve o que **existe hoje**, mais o que as ADRs já decidiram para as próximas etapas.
O que está planejado e não implementado é marcado como tal. Decisões: [ADR 0001](adr/0001-clean-architecture-monolito-modular.md)
e [ADR 0002](adr/0002-catalogo-de-design-patterns.md).

## Projetos e regra da dependência

Monólito modular com Clean Architecture pragmática: quatro projetos de código, referências só para dentro.

```mermaid
flowchart LR
    Api["Traceon.Api<br/>(host ASP.NET Core)"] --> Application["Traceon.Application<br/>(vazio)"]
    Api -. "composição: AddInfrastructure" .-> Infrastructure["Traceon.Infrastructure<br/>(EF Core, Npgsql, health check)"]
    Infrastructure --> Application
    Infrastructure --> Domain["Traceon.Domain<br/>(vazio)"]
    Application --> Domain
```

| Projeto | Papel hoje | Dependências |
|---|---|---|
| `Traceon.Domain` | Vazio: não há regra de negócio nem entidade | Nenhuma (nem pacote, nem framework) |
| `Traceon.Application` | Vazio: casos de uso virão com a etapa 2 | `Domain` |
| `Traceon.Infrastructure` | `TraceonDbContext` (sem `DbSet`), `DatabaseOptions`, `DatabaseHealthCheck`, `AddInfrastructure` | `Application`, `Domain`; pacotes `Npgsql.EntityFrameworkCore.PostgreSQL` e `Microsoft.Extensions.Diagnostics.HealthChecks` |
| `Traceon.Api` | `Program.cs` (composition root), endpoints de health, OpenAPI | `Application`, `Infrastructure` (só composição) |

- Os tipos da `Infrastructure` são `internal`; a `Api` enxerga apenas os métodos de extensão públicos
  (`AddInfrastructure`, `AddDatabaseHealthCheck`). O compilador impede o uso do resto.
- A regra é verificada por testes que leem os `.csproj` (`DependencyRuleTests`, ver [ADR 0005](adr/0005-estrategia-de-testes.md)):
  `Domain` sem referências; `Application` só com `Domain`; `Infrastructure` sem ASP.NET Core; nenhum projeto
  interno referencia a `Api`.
- Critérios globais: `Directory.Build.props` liga nullable, analyzers (`AnalysisLevel` `latest-recommended`),
  `EnforceCodeStyleInBuild` e `TreatWarningsAsErrors`.

## Módulos planejados

Identity & Organizations, Sites, Monitoring, Integrity, Findings, Notifications e Audit. **Nenhum existe ainda.**
Quando existirem serão **namespaces/pastas** dentro de cada projeto (`Traceon.Domain.Sites`,
`Traceon.Application.Sites`, ...), com estas regras (ADR 0001):

- Um módulo não usa entidades nem tabelas de outro; a comunicação passa pelo serviço de aplicação do dono.
- Um `TraceonDbContext` e um schema PostgreSQL por módulo, só quando houver tabelas.
- Não são microserviços; não se criam projetos vazios por módulo.

## Fluxo das requisições de health

O navegador fala só com o servidor do Vite; o Vite encaminha apenas `/health/*` para a API (sem CORS).

```mermaid
sequenceDiagram
    participant B as Navegador (React)
    participant V as Vite (:5173, proxy /health)
    participant A as API (:5120)
    participant P as PostgreSQL (127.0.0.1:5432)
    B->>V: GET /health/live e GET /health/ready (em paralelo, timeout de 5 s)
    V->>A: encaminha
    A-->>V: live: 200 {"status":"Healthy"} (sem consultar o banco)
    A->>P: ready: abre conexão própria, sem pool, timeout de 3 s
    alt conexão aberta
        P-->>A: ok
        A-->>V: 200 {"status":"Healthy","checks":[{"name":"database","status":"Healthy"}]}
    else recusa, silêncio ou timeout
        A-->>V: 503 {"status":"Unhealthy","checks":[{"name":"database","status":"Unhealthy"}]}
    end
    V-->>B: resposta (502 do proxy se a API estiver parada)
    B->>B: parse do contrato e estado: operacional / banco indisponível / API sem resposta / resposta inesperada
```

Como o frontend interpreta (`frontend/src/status/systemStatus.ts`): 5xx ou falha de rede em `live` significa
"API não responde" (sem a API, o proxy responde 502); `live` saudável com `ready` 503 e check `database`
`Unhealthy` significa "banco indisponível"; qualquer corpo fora do contrato ou status inesperado vira
"resposta inesperada". A tabela completa está em [frontend/README.md](../frontend/README.md).

## Health checks

- `/health/live` roda **nenhum** check (`Predicate = _ => false`): um banco fora do ar não deve fazer um
  orquestrador reiniciar um processo saudável.
- `/health/ready` roda os checks com a tag `ready` (hoje, `database`) e mapeia `Unhealthy` para 503.
- A sonda abre uma `NpgsqlConnection` própria, com `Pooling=false` e `Timeout=3 s`, em vez de usar o
  `DbContext`/pool: o Npgsql ignora o `CancellationToken` durante o startup da conexão, e uma conexão
  reaproveitada do pool poderia declarar pronto um banco morto.
- O corpo é escrito por allowlist (`HealthResponseWriter`): só status e nome do check.
- Racional completo e alternativas: [ADR 0004](adr/0004-health-checks-liveness-e-readiness.md).

## Configuração e erros

- **Connection string:** `ConnectionStrings:Traceon`, obrigatória. `DatabaseOptions` é validada com
  `ValidateOnStart` (ausente, vazia ou malformada derruba o app na partida); a mensagem nomeia a chave e nunca
  o valor. Em desenvolvimento vem de User Secrets (`UserSecretsId` no `Traceon.Api.csproj`); em outros ambientes,
  da variável `ConnectionStrings__Traceon`. Nunca vai para `appsettings*.json`.
- **Ambientes:** `launchSettings.json` (perfil `http`, porta 5120) usa `Development`; os testes usam `Testing`,
  que não lê User Secrets. OpenAPI (`/openapi/v1.json`) só em `Development`.
- **Container de DI:** `ValidateScopes` e `ValidateOnBuild` ligados em todos os ambientes.
- **EF Core:** sem logging de dados sensíveis e sem timeouts customizados (valem os padrões do Npgsql, ajustáveis
  na connection string). O `TraceonDbContext` ainda não tem entidades nem migrations.
- **Erros:** `AddProblemDetails` + `UseExceptionHandler()` + `UseStatusCodePages()`: exceções e códigos sem corpo
  viram Problem Details sem detalhe interno (rota inexistente testada: 404 `application/problem+json`). O ADR 0002
  cita `IExceptionHandler`; não há implementação própria ainda porque não existem exceções de domínio a mapear.
  Não há teste que force uma exceção não tratada (pendência registrada em [acceptance.md](acceptance.md)).

## Frontend

React + TypeScript + Vite + Tailwind, uma tela ("Estado do sistema"). Camadas pequenas: `api/http.ts` (único
ponto de `fetch`, corpo tratado como `unknown`, timeout e cancelamento), `status/healthContract.ts` (parse do
contrato), `status/systemStatus.ts` (regras de estado), `useSystemStatus` (hook) e componentes de apresentação.
O estado é uma união discriminada; não há biblioteca de estado, de dados nem de componentes.
Integração e configuração: [ADR 0006](adr/0006-integracao-frontend-proxy-e-configuracao.md).

## Onde o worker entrará (etapa 3, não implementado)

Um host separado, `Traceon.Worker`, que referencia `Application` e `Infrastructure` e reaproveita `AddInfrastructure`.
É um processo a mais, **sem banco próprio**: o processamento será persistente (tabela de jobs com estado, lease e
`FOR UPDATE SKIP LOCKED`), com concorrência limitada, cancelamento e isolamento do coletor Playwright (ADR 0002).
Nada disso existe na Foundation, e coleta externa só entra após autorização do site, modelo de ameaças e
controles de SSRF.

## Fora do escopo desta etapa

Autenticação, organizações, sites, entidades, migrations, jobs, notificações, auditoria, infraestrutura em nuvem
e Kubernetes. A pasta `infrastructure/` prevista no prompt mestre ainda não existe.

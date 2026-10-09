# Critérios de aceite da Foundation

Fonte: `docs/prompt-mestre.md`, seções 4 (escopo da Foundation) e 6. Verificações em 2026-10-09, na máquina de
desenvolvimento (macOS Apple Silicon, .NET SDK 10.0.400, Node 24, Docker Desktop). Regra do prompt: verificação
não executada fica **Pendente**, explicitamente.

Legenda: **Atendido** = executado e observado; **Pendente** = não executado ou não coberto.

## Critério de aceite geral

| Critério | Status | Evidência |
|---|---|---|
| Backend compila | Atendido | `dotnet build backend/Traceon.slnx`: 0 avisos, 0 erros (avisos são erros por `TreatWarningsAsErrors`) |
| Formatação do backend | Atendido | `dotnet format backend/Traceon.slnx --verify-no-changes`: sem alterações |
| Frontend compila | Atendido | `npm run build` (`tsc -b` + `vite build`) passou |
| TypeScript é verificado | Atendido | `npm run typecheck` passou; o build também roda `tsc -b`; ESLint strict type-checked com zero avisos (`npm run lint`) |
| Testes executados passam | Atendido | Backend: `dotnet test --solution backend/Traceon.slnx`, 45 testes (8 unidade/arquitetura, 37 integração). Frontend: `npm test`, 83 testes. Os mesmos testes passaram na CI do GitHub (run 37924116086, 2026-10-09) |
| Conectividade real funciona | Atendido | Ponta a ponta: `docker compose up` + API + Vite; navegador mostrou "API e banco respondendo" |
| Falhas previstas são representadas corretamente | Atendido | Banco parado: "A API responde, mas o banco de dados está indisponível" (`ready` 503 via proxy). API parada: "A API não está respondendo" (proxy 502), banco "Desconhecido". Cobertos também por testes de integração (recusa e silêncio do banco) |
| O README permite reproduzir o resultado | Atendido | Fluxo equivalente executado ponta a ponta (`compose up`, API com `dotnet run --launch-profile http`, `npm run dev`) e comandos do README conferidos contra os reais. Limitação: não seguido do zero por uma segunda pessoa |

## Escopo executável (itens 1 a 6)

| # | Critério | Status | Evidência |
|---|---|---|---|
| 1 | Solução, frontend e configurações criados, preservando o que existia | Atendido | `backend/Traceon.slnx` (4 projetos + 2 de teste), `frontend/`, `global.json`, `.editorconfig`, `.gitignore`. Estrutura conforme a seção 2 do prompt (sem a pasta `infrastructure/`, que não tem conteúdo ainda) |
| 2 | PostgreSQL local, EF Core/Npgsql, variáveis externas, `.env.example` sem segredos | Atendido | `compose.yaml` (`postgres:18.6-alpine3.24`, porta publicada só em `127.0.0.1`, senha obrigatória sem padrão), `.env.example` com valor de exemplo, `TraceonDbContext` registrado. Sem entidades nem migrations (proibido pelo prompt) |
| 2a | Connection string obrigatória e sem vazamento | Atendido | `StartupConfigurationTests`: ausente, vazia ou só espaços derruba o app; malformada derruba sem repetir o valor (canário) |
| 3 | `/health/live` independe do banco | Atendido | `Live_returns_healthy_when_database_is_unreachable` (banco recusando e banco mudo) → 200 |
| 3 | `/health/ready` verifica conectividade real e diferencia indisponibilidade | Atendido | `Ready_returns_healthy_database_check_when_database_is_available` → 200; `Ready_returns_503_unhealthy_promptly_when_database_is_unreachable` → 503 dentro de 10 s; o caso de banco mudo mediu 3,07 s depois da correção (15 s antes dela) |
| 3 | Nenhum diagnóstico sensível exposto | Atendido | Corpo exato conferido; canário da senha, porta, `Host=`, `Exception`, `Npgsql` e ` at ` ausentes; `Cache-Control: no-store`; senha ausente do log da falha (`Ready_failure_logs_never_contain_the_database_password`) |
| 3 | OpenAPI apenas em desenvolvimento | Atendido | `OpenApi_document_is_exposed_only_in_development`: 200 em Development, 404 em Production |
| 3 | Tratamento consistente de erros (Problem Details) | Atendido | `Unknown_route_returns_problem_details_without_internal_details`: 404 `application/problem+json` sem detalhe interno; `Health_endpoints_reject_other_methods_with_405_problem_details`: 405 em Problem Details com `Allow: GET` |
| 3a | Exceção não tratada (500) responde Problem Details sem stack trace | Atendido | `UnhandledExceptionTests.Unhandled_exception_returns_generic_500_problem_details_with_trace_id` (live e ready, com exceção semeada de canário: corpo sem `Exception`, `Npgsql`, `Host=`, ` at `, `Traceon.` nem o canário, com `traceId`); `Unhandled_exception_is_logged_with_the_trace_id_returned_to_the_client` (o log leva o mesmo trace id). O handler real é exercitado trocando o `HealthCheckService` por um que lança, sem rota de teste na API |
| 4 | Tela inicial consulta o estado real da API por proxy do Vite, sem CORS aberto | Atendido | `frontend/vite.config.ts` encaminha só `/health`; a API não configura CORS; validado no navegador em 1200 px e 400 px |
| 4 | Sem autenticação improvisada nem endpoints de negócio | Atendido | Únicos endpoints: `GET /health/live`, `GET /health/ready` e, em Development, o documento OpenAPI; garantido por `RouteInventoryTests.Registered_routes_are_exactly_the_public_allowlist` e `RouteInventoryTests.Development_adds_only_the_openapi_document_to_the_allowlist` |
| 5 | Build, análise estática e testes configurados na CI | Atendido | `.github/workflows/ci.yml` com jobs backend (inclui o passo de drift da spec), frontend, api-spec (Spectral + OWASP) e secrets (gitleaks); os quatro passaram no `main` (run 37924116086, 2026-10-09) |
| 5 | Testes de integração HTTP com PostgreSQL real | Atendido | Testcontainers `postgres:18.6-alpine3.24`, mesma imagem do Compose; liveness com banco indisponível e readiness com banco disponível e indisponível |
| 5 | Sem testes triviais para preencher o projeto de unit tests | Atendido | `Traceon.UnitTests` contém só os 8 testes de arquitetura (regra da dependência); `Domain` e `Application` seguem sem código e sem teste |
| 6 | Interface validada no navegador | Atendido | Playwright em 1200 px e 400 px: estados operacional, banco indisponível e API parada; navegação por teclado (Tab + Enter no botão) e foco visível; console sem erros além do 503/502 esperados |
| 6 | Inicialização, testes, variáveis e solução de problemas documentados | Atendido | `README.md`, `frontend/README.md` |
| 6 | Decisões relevantes em ADRs curtos | Atendido | ADRs 0001 a 0007 |

## Convenções de API e segurança (ADR 0007)

Critérios adicionais da rodada de regras de API, design e segurança. Não vêm do prompt mestre.

| Critério | Status | Evidência |
|---|---|---|
| Health só responde a `GET` | Atendido | `HealthEndpointTests.Health_endpoints_reject_other_methods_with_405_problem_details` (`POST`, `PUT`, `DELETE` em live e ready → 405 `application/problem+json`, `Allow: GET`) |
| Spec OpenAPI descreve exatamente as duas operações e seu contrato | Atendido | `OpenApiDocumentTests.Document_lists_exactly_the_two_health_get_operations`; `OpenApiDocumentTests.Health_operation_documents_its_identity_and_json_responses` (`operationId`, resumo, descrição, tags, respostas 200/503/500, `Cache-Control`) |
| Headers de segurança em toda resposta, inclusive 404 e 500 | Atendido | `SecurityHeadersTests.Responses_carry_security_headers`; `SecurityHeadersTests.Unhandled_exception_response_keeps_security_headers` |
| Inventário de rotas como fitness function | Atendido | `RouteInventoryTests` (Testing, Production e Development) |
| Spec versionada igual à gerada pelo build | Atendido | `docs/api/openapi.json` versionado; passo "Verificar drift da spec OpenAPI" do job backend passou na CI (run 37924116086) |
| Lint da spec (Spectral + ruleset OWASP) | Atendido | `.spectral.yaml` e job `api-spec`, versões exatas; passou na CI (run 37924116086). Regras desligadas uma a uma, com motivo e etapa de retorno |
| Dependabot validado pelo GitHub | Atendido | Abriu os PRs #1 (npm) e #2 (NuGet) em 2026-10-09; o #2 passou na CI e o #1 caiu no teste intermitente corrigido em `StartupConfigurationTests` |
| Modelo de ameaças com teste por mitigação | Atendido | [security/threat-model.md](security/threat-model.md): 19 ameaças, cada uma com teste, passo de CI, "sem teste — pendente" ou risco aceito |

## Verificações pendentes (não executadas)

| Item | Status | Motivo e próximo passo |
|---|---|---|
| Revisão das ADRs pelo responsável | **Pendente** | ADRs 0001 a 0007 estão "Aceito" com revisão pendente do responsável do projeto |
| Controles do modelo de ameaças sem teste | **Pendente** | Banco só no loopback (`compose.yaml`) e segredo fora do bundle (`vite.config.ts`) são verificados por revisão; ver linhas 13 e 15 do [threat-model.md](security/threat-model.md) |
| Rate limit, DTO estrito, autenticação, `ETag`/`If-Match`, Schemathesis, `oasdiff breaking` | Não configurados | Dependem de entrada, escrita ou autenticação; entram na etapa 2 ([progress.md](progress.md)) |
| Versões do Spectral fora do Dependabot | Limitação conhecida | Ficam no `env:` do workflow; atualização manual (risco R4 do modelo de ameaças) |
| Cobertura de código e mutation testing | Não configurados | Nenhuma ferramenta nem limiar definidos na CI |

## Limitações conhecidas

- O primeiro `docker compose up` baixa a imagem do PostgreSQL; sem rede, ele falha.
- O PostgreSQL nativo na porta 5432 conflita com o Compose (ver solução de problemas no README).
- A Foundation não prova nada sobre segurança da coleta, isolamento entre organizações ou carga: nada disso existe ainda.
- `/health/ready` não tem rate limit: risco aceito e registrado (R1 do modelo de ameaças); decisão nas etapas 2 e 6.
- Teste intermitente corrigido em 2026-10-09: os testes de partida com configuração inválida usavam o
  `WebApplicationFactory`, que disputa com o entry point o host já descartado e às vezes devolve
  `ObjectDisposedException`. Agora usam host genérico e o entry point real na thread do teste (20/20 sob CPU saturada).
- Não há afirmação de prontidão para produção.

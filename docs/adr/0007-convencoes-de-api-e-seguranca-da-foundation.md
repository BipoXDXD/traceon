# ADR 0007 — Convenções de API e segurança da Foundation

- **Status:** Aceito (2026-10-09). Revisão pendente pelo responsável do projeto.
- **Relacionados:** [ADR 0004](0004-health-checks-liveness-e-readiness.md) (desenho do health),
  [ADR 0006](0006-integracao-frontend-proxy-e-configuracao.md) (proxy, sem CORS),
  [modelo de ameaças](../security/threat-model.md).
- **Revisão (2026-10-09):** as convenções continuam ([ADR 0009](0009-migracao-do-backend-para-java-e-spring-boot.md)). A spec segue code-first e versionada, gerada por springdoc num teste de integração que compara com `docs/api/openapi.json`; headers de segurança vêm de `http.headers(...)` do Spring Security; Problem Details por `ProblemDetail` + `@RestControllerAdvice`.

## Contexto

A Foundation expõe só dois endpoints anônimos, mas as convenções de contrato e de segurança que nascem agora serão
herdadas por toda a API da etapa 2. Faltavam: uma spec OpenAPI versionada e verificada, resposta de erro 500 provada
sem vazamento, headers de segurança, garantia de que nenhuma rota nem método surja sem decisão, e um registro das
ameaças com o teste que garante cada mitigação.

**Origem da spec OpenAPI**

| Opção | Prós | Contras |
|---|---|---|
| **A. Spec-first escrita à mão (YAML)** | Contrato independente da implementação; é o que o DWA recomenda (6.2) | Dois artefatos para manter com um dev só; sem contract test (Schemathesis ainda não existe) a spec e o código divergem em silêncio |
| **B. Code-first em runtime, só em Development (estado anterior)** | Zero custo | Sem spec versionada: não há diff de contrato, nem lint na CI, nem inventário revisável |
| **C. Code-first gerada no build, versionada em `docs/api`, com drift check e lint na CI** | Fonte única (o código); a spec muda no mesmo PR que o contrato e a CI barra o esquecimento; sem servidor nem banco no build | Quem muda contrato precisa commitar a spec regravada; a documentação mora no código C#; depende de um pacote de build e do nome do entrypoint do gerador |

**Endpoints de health**

| Opção | Prós | Contras |
|---|---|---|
| **A. `MapHealthChecks` com `ResponseWriter` próprio (ADR 0004 original)** | Pouco código | O corpo é escrito à mão, sem tipos de resposta que o gerador de OpenAPI infira; método, `operationId` e códigos de status não são metadados do endpoint |
| **B. `MapGet` com `HealthCheckService`, DTOs de resposta e metadados** | Só `GET` (outros métodos → 405); tipos, `operationId` e respostas 200/503/500 documentados e testáveis; o inventário de rotas enxerga método e padrão | Reimplementa o mapeamento `Unhealthy → 503` (poucas linhas) |

**Headers de segurança:** middleware próprio de ~20 linhas × biblioteca de terceiros (mais uma dependência para 4
headers) × configurar só no reverse proxy (não existe até a etapa 6, e a API deve se proteger sozinha: SA 3.4).

**Lint da spec:** Spectral com `spectral:oas` + ruleset OWASP (o SA cap. 12 cita Spectral OWASP) × ruleset próprio
mínimo. Desligar regras em bloco × desligar uma a uma com motivo.

## Decisão

1. **Spec code-first gerada no build e versionada (opção C).**
   - `Microsoft.Extensions.ApiDescription.Server` regrava `docs/api/openapi.json` a cada build da API
     (`OpenApiDocumentsDirectory` no `Traceon.Api.csproj`). Em Development o mesmo documento é servido em
     `/openapi/v1.json`; não existe em outros ambientes.
   - Na geração, o entrypoint é `GetDocument.Insider` (padrão da documentação oficial "Generate OpenAPI documents"):
     `Program.cs` pula `AddInfrastructure` só nesse caso, então o build não precisa de connection string nem de banco.
     Toda execução real continua falhando na partida sem a connection string.
   - Metadados no documento: `operationId` (`getLiveness`, `getReadiness`), `summary`, `description`, tags, respostas
     200/503/500, header `Cache-Control` e limites nos DTOs (`MaxLength`, `RegularExpression`). `servers` é `/` (mesma
     origem do frontend, ADR 0006).
   - **CI:** o job `backend` falha se `git diff --exit-code -- docs/api` acusar diferença ou houver arquivo não
     versionado ("Verificar drift da spec OpenAPI"); o job `api-spec` roda Spectral 6.16.3 com
     `@stoplight/spectral-owasp-ruleset` 2.0.1 (`--fail-severity=warn`). O comando exato está no workflow e no
     cabeçalho de `.spectral.yaml`, e o README traz o equivalente local.
   - **Regras OWASP desligadas uma a uma** em `.spectral.yaml`, cada uma com motivo e etapa de retorno
     (`info-contact` e `api9:*-inventory-*`: etapa 6; `api8:2023-define-cors-origin`: sem CORS, ADR 0006). Os
     `overrides` valem só para os paths de health (anônimos por design) e para o schema `ProblemDetails`; a regra
     continua ativa para qualquer outro path e schema, então o primeiro endpoint da etapa 2 sem `security` ou o
     primeiro DTO sem limites falha o lint.
2. **Health como `MapGet` (opção B).** Só `GET`; `POST`, `PUT` e `DELETE` respondem 405 em Problem Details com
   `Allow: GET`. Status serializado como string (`JsonStringEnumConverter<HealthStatus>`), contrato JSON inalterado.
   `Cache-Control: no-store` por filtro do grupo `/health`, documentado na spec. Liveness chama
   `CheckHealthAsync(_ => false)`; readiness filtra pela tag `ready` e mapeia `Unhealthy` para 503 (`Degraded`
   continua 200).
3. **Headers de segurança em toda resposta** (`UseSecurityHeaders`): `X-Content-Type-Options: nosniff`,
   `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY` e
   `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`. Aplicados em `Response.OnStarting`, porque
   `UseExceptionHandler` limpa os headers antes de escrever o 500. A API só devolve JSON, então a CSP é a mais
   restritiva possível.
4. **500 genérico:** Problem Details sem stack, SQL, classe ou connection string, com `traceId`; o log registra a
   exceção com o mesmo trace id (console com `IncludeScopes`). Vale para o handler real, exercitado com um
   `HealthCheckService` que lança, sem rota de teste na API.
5. **Inventário de rotas como fitness function** (deny by default): o conjunto de rotas registradas tem de ser
   exatamente a allowlist pública (`GET /health/live`, `GET /health/ready`; mais `GET /openapi/{documentName}.json` em
   Development). Rota nova quebra o teste até alguém decidir. Na etapa 2 o mesmo teste passa a exigir 401 sem
   credencial fora da allowlist.
6. **Ameaças** ficam em [docs/security/threat-model.md](../security/threat-model.md): ameaça → mitigação → nome do
   teste ou passo de CI; sem teste fica "pendente" ou "aceito" com motivo.

**Não adotado nesta etapa (fica registrado para a etapa 2, ver [progress.md](../progress.md)):** DTO estrito que
rejeita campo desconhecido, autenticação e fallback policy, rate limit, `ETag`/`If-Match`, Schemathesis, `oasdiff
breaking`, schema de erro com `errors[]`. Não há entrada nem escrita para exercitá-los.

## Consequências

- O contrato tem uma fonte (o código) e uma cópia revisável (`docs/api/openapi.json`). **Quem altera contrato roda o
  build e commita a spec no mesmo PR**; a CI recusa o contrário.
- O build regrava um arquivo versionado: um diff inesperado em `docs/api` é sinal de mudança de contrato, não ruído.
- O build depende do nome do entrypoint do gerador (`GetDocument.Insider`) e de um pacote de build
  (`PrivateAssets=all`, fora do runtime). Se a ferramenta mudar, a geração falha no build, e não em produção.
- A documentação do contrato mora em strings C#; descrições longas poluem os endpoints. Aceito pelo ganho de fonte única.
- As versões do Spectral ficam num `env:` do workflow, **fora do Dependabot**, e o `npx` resolve transitivas sem
  lockfile (risco R4 do modelo de ameaças). Atualização manual.
- O 500 não segue por inteiro o DWA 12.10.3: usa `traceId` do framework em vez de `instance` opaco e não há schema
  com `const`/`pattern` que impeça o vazamento por tipo. O schema `ProblemDetails` é o do framework, com as regras de
  limite do Spectral desligadas só para ele; revisar se a etapa 2 publicar schema próprio de erro com `errors[]`.
- A CSP e os demais headers protegem a API, não a página do frontend (servida pelo Vite). Fica para a hospedagem (etapa 6).
- Nada disso foi executado na CI: o repositório ainda não tem remote.

## Compliance

Testes (xunit, `Traceon.IntegrationTests` salvo indicação):

| Decisão | Teste ou passo de CI |
|---|---|
| Só `GET`, 405 com `Allow: GET` | `HealthEndpointTests.Health_endpoints_reject_other_methods_with_405_problem_details` |
| Spec lista exatamente as duas operações `get` | `OpenApiDocumentTests.Document_lists_exactly_the_two_health_get_operations` |
| `operationId`, `summary`, `description`, tags, respostas JSON e `Cache-Control` documentados | `OpenApiDocumentTests.Health_operation_documents_its_identity_and_json_responses` |
| OpenAPI só em Development | `HttpPipelineTests.OpenApi_document_is_exposed_only_in_development` |
| Spec versionada igual à gerada | CI, job `backend`: "Verificar drift da spec OpenAPI" |
| Spec sem violação de lint | CI, job `api-spec`: "Lint da spec" (Spectral + owasp-ruleset) |
| Headers de segurança, inclusive no 500 | `SecurityHeadersTests.Responses_carry_security_headers`; `SecurityHeadersTests.Unhandled_exception_response_keeps_security_headers` |
| 500 genérico com `traceId`, sem vazamento | `UnhandledExceptionTests.Unhandled_exception_returns_generic_500_problem_details_with_trace_id` |
| Log do 500 com o mesmo trace id | `UnhandledExceptionTests.Unhandled_exception_is_logged_with_the_trace_id_returned_to_the_client` |
| 404 em Problem Details sem detalhe interno | `HttpPipelineTests.Unknown_route_returns_problem_details_without_internal_details` |
| Inventário de rotas (deny by default) | `RouteInventoryTests.Registered_routes_are_exactly_the_public_allowlist`; `RouteInventoryTests.Development_adds_only_the_openapi_document_to_the_allowlist` |
| Contrato do health inalterado, sem diagnóstico, sem cache | `HealthEndpointTests.Ready_returns_503_unhealthy_promptly_when_database_is_unreachable`; `HealthEndpointTests.Health_responses_are_uncacheable_json`; `HealthEndpointTests.Ready_failure_logs_never_contain_the_database_password` |
| Build gera a spec sem connection string | Passo "Build" da CI (um build sem banco nem segredo não pode falhar) e o drift check |

- Revisão de PR: mudança de contrato sem `docs/api/openapi.json` no diff é recusada; regra OWASP nova desligada exige
  motivo e etapa de retorno no `.spectral.yaml`.

## Referências

Apenas o que consta em `~/.claude/knowledge/api-security.md`:

- *Secure APIs* (SA): 3.4 (não existe API interna), 3.5 (spec como mapa da superfície; shadow, zombie e ghost APIs),
  5.4–5.5 (erro sem stack trace; debug e docs fechados), 5.6 (inventário e spec versionada), 6.1 (conserto só na
  implementação gera drift entre spec e código), 9.1 (headers de segurança), 11.5 (log sem dado sensível),
  cap. 12 (testes de segurança: Spectral OWASP, Schemathesis, rotas enumeradas).
- *The Design of Web APIs*, 2ª ed. (DWA): 6.2 (spec-first × code-first), 12.10 (erros que não vazam; 500 genérico
  com referência), 13.4 (política de `Cache-Control` escrita na spec), 15.2.8 e 15.7 (diff da spec; breaking change
  explícito), 16.2–16.4 e 18.4 (decisão transversal vira ADR e regra de lint).
- *API Design Patterns* (ADP): cap. 24 (política de compatibilidade e versionamento), para o `oasdiff` da etapa 2.
- A recomendação de compensar o code-first versionando a spec gerada e rodando diff e lint na CI vem da síntese do
  assistente no mesmo arquivo (§29), não de um capítulo.

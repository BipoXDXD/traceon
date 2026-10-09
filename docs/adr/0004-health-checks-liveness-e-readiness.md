# ADR 0004 — Health checks: liveness × readiness

- **Status:** Aceito (2026-10-09). Revisão pendente pelo responsável do projeto.
- **Revisão (2026-10-09):** o desenho e o contrato JSON do health continuam ([ADR 0009](0009-migracao-do-backend-para-java-e-spring-boot.md), D8). A implementação passa a controllers Spring com sonda de banco própria; o Actuator fica numa porta de gerenciamento não exposta.

## Contexto

O prompt mestre exige `/health/live` sem dependência do banco e `/health/ready` com verificação real de
conectividade, diferenciando indisponibilidade de sucesso e sem expor diagnóstico sensível. O frontend usa as duas
respostas para mostrar o estado real. A implementação teve que resolver três problemas concretos: quanto tempo a
readiness pode esperar, se pode reaproveitar conexões do pool e o que a resposta pode conter.

Alternativas para a sonda de readiness:

| Opção | Prós | Contras |
|---|---|---|
| **A. Check do EF Core (`AddDbContextCheck`) ou `CanConnectAsync`** | Pouco código | Passa pelo pool: uma conexão ociosa pode ser devolvida sem ida ao servidor e declarar pronto um banco morto; usa o timeout padrão |
| **B. `NpgsqlConnection` comum com `CancellationToken`** | Simples | Medição neste projeto: o Npgsql ignora o token na fase de startup da conexão; contra um servidor que aceita TCP e nunca responde, a sonda levou 15 s (o `Timeout` padrão) |
| **C. Sonda própria: `NpgsqlConnection` com cópia da connection string, `Pooling=false` e `Timeout=3`** | Cada sonda vai ao servidor e o limite é de 3 s de fato (medido em 3,07 s com o servidor mudo) | Cria e fecha uma conexão por sonda; a frequência de sondagem precisa ser razoável |

Alternativas para o corpo da resposta: writer padrão do ASP.NET Core (texto), JSON com tudo
(`descriptions`, `exception`, `data`, duração) ou JSON por allowlist.

Alternativas para expor as rotas: `MapHealthChecks` com `ResponseWriter` próprio (desenho original deste ADR) ou
`MapGet` que consulta o `HealthCheckService` e devolve DTOs tipados. A segunda foi adotada em 2026-10-09 porque
método, tipos de resposta e `operationId` passam a ser metadados do endpoint, documentados no OpenAPI e enxergados pelo
inventário de rotas ([ADR 0007](0007-convencoes-de-api-e-seguranca-da-foundation.md)).

## Decisão

- **Endpoints**: dois `MapGet` no grupo `/health` (`getLiveness`, `getReadiness`), cada um consultando o
  `HealthCheckService` e devolvendo um DTO. **Só `GET`**: `POST`, `PUT` e `DELETE` respondem `405` em Problem Details
  com `Allow: GET`.
- **`/health/live`**: nenhuma verificação (`CheckHealthAsync(_ => false)`). Responde `200 {"status":"Healthy"}` se o
  processo atende. Um banco indisponível não deve levar um orquestrador a reiniciar um processo saudável, e reiniciar
  não repara o banco.
- **`/health/ready`**: roda os checks com a tag `ready` (hoje, `database`). `Healthy` e `Degraded` → `200`;
  `Unhealthy` → `503` (mapeamento feito no endpoint). Corpo: `{"status":"...","checks":[{"name":"database","status":"..."}]}`,
  com o status serializado como string.
- **Sonda (opção C)**: `DatabaseHealthCheck` abre uma conexão real sem pool e com timeout de 3 s
  (`DatabaseHealthCheck.Timeout`), registrada com esse mesmo timeout no serviço de health checks. As conexões da
  aplicação mantêm a configuração do operador.
- **Allowlist na resposta**: os DTOs `LivenessResponse`, `ReadinessResponse` e `CheckResponse` só têm o status geral e,
  por check, nome e status. Nunca descrição, exceção, duração, `data`, host, porta, usuário nem trecho da connection
  string. Cabeçalhos: `application/json` e `Cache-Control: no-store` (filtro do grupo `/health`, documentado na spec).
  Os headers de segurança vêm do pipeline (ADR 0007).
- **Sem autenticação**, para que o orquestrador e o proxy consultem; por isso o corpo é mínimo.
- Contrato consumido pelo frontend: o nome `database` e os valores `Healthy`/`Unhealthy` fazem parte dele; mudá-los
  é mudança de contrato.

## Consequências

- Distinguimos "processo vivo" de "app pronta"; o painel mostra "banco indisponível" sem confundir com "API fora".
- Uma sonda por chamada custa uma conexão TCP/autenticação. Se o endpoint for consultado em alta frequência, será
  preciso limitar ou cachear o resultado (decisão futura, só com medição).
- O 503 demora até 3 s no pior caso (banco mudo); a recusa de conexão responde de imediato.
- Qualquer novo check de readiness só entra se for interno ao sistema; dependência opcional ou de terceiros não
  entra (evita derrubar a readiness por algo que o app tolera).
- Sem rate limit nem autenticação, os endpoints podem ser usados para forçar conexões ao banco. **Risco aceito na
  Foundation** (limite atual: timeout de 3 s); decisão pendente nas etapas 2 e 6 (risco R1 em
  [threat-model.md](../security/threat-model.md)). As regras OWASP de rate limit do Spectral estão desligadas só para
  estes dois paths por esse motivo.

## Compliance

Testes de integração (`HealthEndpointTests`, PostgreSQL real e banco falso que recusa ou fica mudo):

- liveness `200` com banco disponível, recusando conexões e mudo;
- readiness `200` com banco disponível; `503` em até 10 s (margem de CI) com banco recusando e mudo;
- corpo exato e ausência do canário da senha, da porta, de `Host=`, `Exception`, `Npgsql` e ` at `
  (`Ready_returns_503_unhealthy_promptly_when_database_is_unreachable`);
- a senha nunca aparece no log da falha (`Ready_failure_logs_never_contain_the_database_password`);
- `application/json` e `Cache-Control: no-store` nas duas rotas (`Health_responses_are_uncacheable_json`);
- `POST`, `PUT` e `DELETE` → `405` `application/problem+json` com `Allow: GET`
  (`Health_endpoints_reject_other_methods_with_405_problem_details`);
- spec e inventário de rotas: `OpenApiDocumentTests` e `RouteInventoryTests` (ADR 0007).

## Referências

*Kubernetes in Action*, 2ª ed., caps. 6 (liveness: só componentes internos, sem dependências externas, sem
autenticação) e 11 (readiness: falhar tira do tráfego sem reiniciar), conforme as notas do projeto;
*Release It!*, 2ª ed., cap. 5 (padrões de estabilidade: timeouts); documentação do Npgsql (`Timeout`, `Pooling`).
Kubernetes não é requisito do MVP: as referências valem como base conceitual.

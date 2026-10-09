# Modelo de ameaças

- **Revisão:** 2026-10-09 (Foundation e etapa 2). Próxima revisão: no início da etapa 3 (coletor) e a cada
  trimestre.
- **Método:** inventário de dados → DFD → STRIDE → mitigação → teste que a garante (SA cap. 2.2 e 12.1, conforme
  `~/.claude/knowledge/api-security.md` §1). Regra de leitura: ameaça mitigada aponta para o **nome exato** do teste ou
  para o passo de CI; sem teste fica marcada **"sem teste — pendente"**; risco que se decidiu não tratar fica
  **"aceito"** com motivo.
- **Estado dos testes:** os da Foundation estão commitados e passam na CI (run 37924116086). Os da
  [etapa 2](#etapa-2-identity--sites) são **planejados**: os nomes fixam o comportamento exigido e cada teste entra no
  mesmo PR da mitigação; a etapa não fecha com algum deles faltando.

# Foundation

## Escopo

Superfície da Foundation, e só ela:

- API ASP.NET Core com `GET /health/live`, `GET /health/ready` e, só em Development, `GET /openapi/{documentName}.json`.
  Sem autenticação, sem dados de negócio, sem entidades nem migrations.
- PostgreSQL local (Docker Compose, porta publicada só em `127.0.0.1`).
- Frontend React servido pelo Vite, que encaminha apenas `/health/` para a API (sem CORS).
- Cadeia de entrega: repositório, GitHub Actions (jobs backend, frontend, api-spec e secrets) e Dependabot.

Fora do escopo da Foundation: identidade, organizações e sites (ver [etapa 2](#etapa-2-identity--sites)); coletor,
worker e nuvem (ver "Ameaças das próximas etapas").

## Inventário de dados

| Dado | Onde está | Quem vê | Observação |
|---|---|---|---|
| Dados de usuário ou de negócio | Em lugar nenhum | Ninguém | Não existem hoje: sem contas, sites, evidências nem PII |
| Senha do PostgreSQL | `.env` (Compose, fora do Git) e connection string em User Secrets ou `ConnectionStrings__Traceon` | Desenvolvedor; processo da API | Nunca em `appsettings*.json`, bundle, log nem resposta (testes na tabela abaixo) |
| Connection string (host, porta, usuário) | Memória da API | Processo da API | Não sai nas respostas nem nos logs de falha da readiness |
| Estado de saúde (`Healthy`/`Unhealthy`, nome do check `database`) | Resposta de `/health/*` | Qualquer cliente que alcance a API | Público por design (ADR 0004); é o único dado exposto |
| Logs (console da API) | stdout do processo | Quem opera o processo | Contêm `traceId` e, no 500, a mensagem completa da exceção (risco R3) |
| Spec OpenAPI | `docs/api/openapi.json` (Git) e `/openapi/v1.json` (Development) | Quem lê o repositório | Descreve só as duas rotas de health |
| Segredos de CI | Nenhum configurado | n/a | Jobs com `permissions: contents: read` e `persist-credentials: false` |

Endpoints mais sensíveis: nenhum manipula dado sensível. O mais caro é `GET /health/ready`, que abre uma conexão
real com o banco a cada chamada (risco R1).

## DFD

```mermaid
flowchart LR
    subgraph Dev["Máquina do desenvolvedor"]
        B["Navegador (React)"]
        V["Vite :5173<br/>proxy só de /health/"]
        A["API ASP.NET Core :5120<br/>sem auth, só health"]
        P[("PostgreSQL 127.0.0.1:5432<br/>(Docker Compose)")]
    end
    subgraph GH["GitHub"]
        R["Repositório"]
        CI["Actions: backend, frontend,<br/>api-spec, secrets"]
        D["Dependabot"]
    end
    B -- "1 GET /health/live e /ready" --> V
    V -- "2 encaminha" --> A
    A -- "3 conexão sem pool, timeout 3 s (só ready)" --> P
    A -. "4 JSON por allowlist + headers de segurança" .-> V
    A -. "logs (stdout)" .-> Dev
    Dev -- "push" --> R
    R --> CI
    D -- "PRs de versões" --> R
    CI -- "baixa actions por SHA, gitleaks com sha256, Spectral via npx" --> Reg["Registries (NuGet, npm, GitHub)"]
```

Fronteiras de confiança: navegador × Vite/API; API × PostgreSQL; repositório × CI × registries. Fluxos fáceis de
esquecer: o log (sai do processo), a CI (baixa código de terceiros) e a spec (mapa da superfície).

## STRIDE

Legenda da coluna "Garantia": nome de teste = `Classe.Metodo`; **CI** = passo do workflow
(`.github/workflows/ci.yml`); **revisão** = controle sem automação.

| # | Cat. | Ameaça | Mitigação | Garantia (teste ou controle) |
|---|---|---|---|---|
| 1 | I | Diagnóstico ou connection string vazam no corpo do health (descrição, exceção, host, porta, senha) | Resposta por allowlist (`LivenessResponse`, `ReadinessResponse`, `CheckResponse`): só status e nome do check | `HealthEndpointTests.Ready_returns_503_unhealthy_promptly_when_database_is_unreachable` (corpo exato; canário da senha, porta, `Host=`, `Exception`, `Npgsql` e ` at ` ausentes); `HealthEndpointTests.Ready_returns_healthy_database_check_when_database_is_available` e `HealthEndpointTests.Live_returns_healthy_when_database_is_available` (corpo exato) |
| 2 | I | Frontend repassa campo sensível extra, caso a API o enviasse | O parse do contrato descarta campos fora dele | `healthContract.test.ts`: "keeps only the contract fields when the body has extra ones" |
| 3 | I | Erro 500 ou 404 vaza stack, SQL, classe ou connection string | `AddProblemDetails` + `UseExceptionHandler` + `UseStatusCodePages`: corpo genérico com `traceId` | `UnhandledExceptionTests.Unhandled_exception_returns_generic_500_problem_details_with_trace_id` (live e ready; exceção com canário, `Npgsql`, `Host=`, ` at ` e `Traceon.` não aparece no corpo); `HttpPipelineTests.Unknown_route_returns_problem_details_without_internal_details` |
| 4 | I | Configuração inválida derruba o app repetindo a connection string na mensagem | `DatabaseOptions` com `ValidateOnStart`; a mensagem nomeia a chave, nunca o valor | `StartupConfigurationTests.Host_fails_to_start_with_malformed_connection_string_without_echoing_it`; `StartupConfigurationTests.Host_fails_to_start_without_connection_string` (ausente, vazia, só espaços); `StartupConfigurationTests.Api_entry_point_fails_to_start_with_empty_connection_string` (o `Program` real) |
| 5 | I | Senha do banco aparece em log quando a readiness falha | A sonda não loga nada: a falha vira exceção e o `HealthCheckService` a registra (nome do check e exceção do Npgsql, sem a senha); a sonda não interpola a connection string em mensagem alguma | `HealthEndpointTests.Ready_failure_logs_never_contain_the_database_password` (banco recusando e mudo) |
| 6 | I | Resposta de health é cacheada e mostra estado velho (ou ficaria em cache compartilhado) | `Cache-Control: no-store` por filtro do grupo `/health` | `HealthEndpointTests.Health_responses_are_uncacheable_json`; contrato do header em `OpenApiDocumentTests.Health_operation_documents_its_identity_and_json_responses` |
| 7 | I/T | Clickjacking, MIME sniffing e referrer vazando em respostas da API | `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY`, `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, via `Response.OnStarting` (vale também no 500) | `SecurityHeadersTests.Responses_carry_security_headers` (live 200, ready 503, rota inexistente 404); `SecurityHeadersTests.Unhandled_exception_response_keeps_security_headers` (o handler limpa os headers antes de escrever o 500). **Lacuna:** a página do frontend (servida pelo Vite) não tem esses headers; fica para a hospedagem (etapa 6, ver R5) |
| 8 | S/E | Rota ou método não previstos (endpoint esquecido, `POST` em health, ghost API) | Health só em `MapGet`; outros métodos → 405 Problem Details com `Allow: GET`; inventário de rotas como fitness function; spec lista só as duas operações | `RouteInventoryTests.Registered_routes_are_exactly_the_public_allowlist` (Testing e Production); `RouteInventoryTests.Development_adds_only_the_openapi_document_to_the_allowlist`; `HealthEndpointTests.Health_endpoints_reject_other_methods_with_405_problem_details`; `OpenApiDocumentTests.Document_lists_exactly_the_two_health_get_operations` |
| 9 | I | Spec OpenAPI exposta em produção revela a superfície | `MapOpenApi` só em Development | `HttpPipelineTests.OpenApi_document_is_exposed_only_in_development` |
| 10 | T | Spec versionada diverge do código (contrato muda sem revisão) | O build regrava `docs/api/openapi.json`; a CI falha se houver diferença ou arquivo não versionado | CI: passo "Verificar drift da spec OpenAPI" (job backend); lint da spec: job `api-spec` (Spectral + owasp-ruleset). Ambos passaram na CI (run 37924116086) |
| 11 | S/E | Falsa sensação de autenticação: alguém assume que a API é protegida | Não há autenticação, e nenhuma rota além de health existe; health é anônimo por design (ADR 0004); o Spectral tem `overrides` só para os dois paths de health, então rota nova sem `security` falha o lint | `RouteInventoryTests.*` (nenhuma rota fora da allowlist). Na etapa 2 o inventário passa a exigir 401 sem credencial (ver "Ameaças das próximas etapas") |
| 12 | T | Resposta adulterada ou inesperada confundida com estado válido no painel | O frontend trata o corpo como `unknown`, valida o contrato e cai em "resposta inesperada" | `healthContract.test.ts`: "rejects %s" (13 corpos inválidos); `http.test.ts`: "reports a body that is not JSON as invalid" e "reports JSON that the parser rejects as invalid" |
| 13 | I | Banco exposto na rede | Compose publica a porta só em `127.0.0.1`; senha obrigatória, sem padrão (`${POSTGRES_PASSWORD:?...}`) | **Sem teste — pendente** (controle de configuração em `compose.yaml`, só desenvolvimento local; produção é etapa 6) |
| 14 | I | Segredo commitado (senha, connection string, `.env`) | `.env` no `.gitignore` (`.env.example` sem segredo); User Secrets em dev; gitleaks sobre o histórico completo, binário com versão e sha256 fixos | CI: job `secrets` ("Procurar segredos no histórico"), passou na CI; o histórico também foi varrido antes de tornar o repositório público. `ConnectionStrings` fora de `appsettings*.json` por revisão |
| 15 | I | Segredo no bundle do frontend | `TRACEON_API_URL` sem prefixo `VITE_`, lida só pelo `vite.config.ts`; nenhum valor sensível no frontend (ADR 0006) | **Sem teste — pendente** (revisão; um teste do bundle é candidato quando houver segredo possível) |
| 16 | T/E | Supply chain: action, pacote ou imagem comprometidos ou trocados | Actions fixadas por SHA com a versão em comentário; pacotes NuGet e npm em versão exata (`Directory.Packages.props`, `.npmrc` `save-exact`), `npm ci`; imagem `postgres:18.6-alpine3.24` sem `latest`; `permissions: contents: read`; `persist-credentials: false`; idade mínima de 2 semanas aplicada à mão (ADR 0003); Dependabot semanal sem major | CI: `dotnet restore` (falha se `<PackageReference>` tiver `Version`), `npm ci` (falha se o lockfile divergir), checksum do gitleaks. Dependabot ativo (abriu os PRs #1 e #2). Política de SHA e idade mínima: **revisão de PR, sem automação**. Lacunas em R4 |
| 17 | D | Abuso de `GET /health/ready`: cada chamada abre uma conexão real sem pool e pode esgotar `max_connections` ou segurar workers | Limite atual: a sonda tem timeout de 3 s e fecha a conexão; health sem dado, sem autenticação | Timeout garantido por `HealthEndpointTests.Ready_returns_503_unhealthy_promptly_when_database_is_unreachable` (banco mudo responde 503 dentro da margem de 10 s para o limite de 3 s). **Sem rate limit: risco aceito (R1)** |
| 18 | D | Banco lento ou mudo trava a API | Timeout de 3 s na sonda; liveness não consulta o banco | `HealthEndpointTests.Live_returns_healthy_when_database_is_unreachable` (recusando e mudo); teste acima para a readiness |
| 19 | R | Ação sem autoria (repudiation) | Não há ação de usuário nem de negócio; cada resposta de erro traz `traceId` e o log do 500 usa o mesmo id | `UnhandledExceptionTests.Unhandled_exception_is_logged_with_the_trace_id_returned_to_the_client`. Trilha de auditoria de ações de usuário: módulo Audit, etapa 5 |

## Riscos aceitos e pendências

| # | Risco | Situação | Decisão |
|---|---|---|---|
| R1 | DoS por chamadas repetidas a `/health/ready` (conexão real por chamada, sem pool, sem rate limit) | **Aceito na Foundation.** Mitigação parcial: timeout de 3 s. Sem limite por IP nem cache do resultado. As regras OWASP `api4:2023-rate-limit*` estão desligadas só para os paths de health no `.spectral.yaml`, com este motivo | **Decisão pendente** na etapa 2 (rate limit de login, mesma infraestrutura) e etapa 6 (limite no reverse proxy/Azure). Se health ganhar limite ou cache, as duas regras voltam para esses paths |
| R2 | `AllowedHosts: "*"` (qualquer `Host` é aceito) | **Aceito:** só desenvolvimento local, sem links gerados a partir do host | Fechar na etapa 6, com o domínio real |
| R3 | O log do 500 guarda a mensagem completa da exceção (o teste de correlação semeia canário nela). Se uma exceção futura carregar segredo ou PII na mensagem, ele vai ao log | **Aceito na Foundation:** hoje não há dado sensível nem fonte de PII, e o log não sai da máquina | Revisar na etapa 2, junto com a allowlist de campos de log e o canário de vazamento em logs de requisições com dado de usuário |
| R4 | Supply chain com lacunas: (a) as versões do Spectral na CI (`SPECTRAL_CLI_VERSION`, `SPECTRAL_OWASP_RULESET_VERSION`) são exatas, mas ficam num `env:` do workflow, **fora do Dependabot**; (b) o `npx` do job `api-spec` resolve dependências transitivas do registry sem lockfile nem hash; (c) NuGet também sem lockfile (ADR 0003) | **Aceito**, com atualização manual das duas versões | Reavaliar se o Spectral virar dependência do repositório (`package.json` com lockfile) |
| R5 | Headers de segurança e CSP só existem na API; o HTML do frontend não os tem. HSTS inexistente (HTTP local). `servers` por ambiente ausentes na spec. CORS × reverse proxy não decidido | **Aceito:** a hospedagem ainda não existe | Etapa 6: CSP do frontend na hospedagem, HSTS, `servers`, decisão de CORS |
| R6 | A CI, o gitleaks e o Dependabot nunca tinham rodado; os controles 10, 14 e 16 estavam configurados, não comprovados | **Resolvido em 2026-10-09:** os 4 jobs passaram no `main` e o Dependabot abriu PRs | Nenhuma |

# Etapa 2: Identity & Sites

Decisões de base: [ADR 0008](../adr/0008-identidade-organizacoes-e-comprovacao-de-sites.md). Escolhas do responsável
em 2026-10-09 que moldam esta seção: confirmação de e-mail obrigatória antes do login; `Member` lê, cadastra e
verifica sites, e só `Owner` remove site e exclui organização; o mesmo domínio pode existir em várias organizações,
cada uma com sua comprovação; a comprovação não expira nesta etapa e a etapa 3 a revalida antes de coletar.

## Escopo

Superfície nova. Os caminhos são provisórios e o contrato OpenAPI os fixa no passo seguinte:

- **Conta:** cadastro, confirmação de e-mail, login, logout, "esqueci a senha", redefinição, `GET` da própria conta e
  exclusão da conta (exige a senha atual). Token antiforgery para o frontend.
- **Organizações:** criar (quem cria vira `Owner`), listar as minhas, ler uma, excluir (`Owner`).
- **Sites:** cadastrar um domínio na organização (nasce "não verificado", com token de desafio), listar, ler, remover
  (`Owner`) e pedir a verificação, que consulta o TXT `_traceon-challenge.<domínio>`.
- **Envio de e-mail:** porta na `Application`, com adaptador falso em dev e testes. Envio real só com decisão própria.

Fora do escopo: convites e gestão de membros (cada organização tem só o criador), MFA e passkeys, login externo,
qualquer requisição HTTP a site do usuário.

## Inventário de dados

| Dado | Onde está | Quem vê | Observação |
|---|---|---|---|
| E-mail | `identity` (tabela de usuários do Identity) | O próprio usuário | PII. Nunca em path, query nem log; normalizado pelo Identity; removido com a conta |
| Hash da senha | `identity` | Ninguém (só o processo) | Hasher padrão do Identity (PBKDF2); a senha em claro nunca é persistida nem logada |
| Security stamp, contadores de lockout, tokens de confirmação e de redefinição | `identity` (os tokens são derivados do stamp e da Data Protection, não armazenados) | Ninguém | Uso único: o stamp muda após a redefinição, o que invalida tokens e sessões antigos |
| Cookie de sessão | Navegador (`HttpOnly`) | Navegador; API | Cifrado pela Data Protection; as chaves precisam persistir fora do processo na etapa 6 (R8) |
| Organização (nome) e associação com papel | `identity` | Membros da organização | Nome com limite de tamanho; sem PII esperada |
| Site (domínio, estado, token de desafio, data da verificação) | `sites` | Membros da organização | O token não é segredo (vai para o DNS), mas é imprevisível: 128 bits de CSPRNG por site |
| IP e contagem do rate limit | Memória do processo | Ninguém | Não persistido; janela curta |
| E-mails falsos (dev e testes) | Memória do processo (testes); caixa local fora do Git (dev) | Desenvolvedor | Carregam tokens de uso único; nunca vão para log |

Endpoints mais sensíveis: login, cadastro, "esqueci a senha" e redefinição (credencial e enumeração); exclusão de
conta e de organização (destruição de dados); verificação de site (aciona consulta DNS para nome escolhido pelo
usuário).

## DFD

```mermaid
flowchart LR
    U["Navegador (React)"] -- "1 HTTPS mesma origem<br/>cookie + token antiforgery" --> V["Vite / reverse proxy"]
    V --> A["API: Identity, Organizations, Sites"]
    A -- "2 queries filtradas por organização" --> P[("PostgreSQL<br/>schemas identity e sites")]
    A -- "3 e-mail de confirmação e redefinição" --> E["Porta de e-mail<br/>(falsa em dev e testes)"]
    A -- "4 consulta TXT de _traceon-challenge.dominio<br/>timeout, sem retry ilimitado" --> R["Resolvedor DNS configurado"]
    R -. "resposta não confiável" .-> A
    A -. "logs estruturados (allowlist de campos)" .-> L["stdout"]
```

Fronteiras de confiança novas: usuário autenticado × usuário de outra organização (a mais importante desta etapa);
API × resolvedor DNS (a resposta do DNS é entrada não confiável); API × porta de e-mail (o link carrega um token).

## STRIDE

Todos os testes desta tabela são **planejados**. Teste de acesso afirma o status **e** que o corpo não traz dado
alheio.

| # | Cat. | Ameaça | Mitigação | Teste que a garante |
|---|---|---|---|---|
| 20 | S | Força bruta e credential stuffing no login | Lockout do Identity por conta (padrão: 5 falhas → 5 min) e rate limit por IP; a conta bloqueia mesmo trocando de IP | `LoginTests.Repeated_failed_logins_lock_the_account_even_across_ips`; `RateLimitTests.Login_beyond_the_limit_returns_429_with_retry_after` |
| 21 | I | Enumeração de contas por cadastro, login ou "esqueci a senha" | Cadastro e "esqueci a senha" respondem sempre 202 com o mesmo corpo; login falho responde 401 genérico, igual para senha errada, conta inexistente, não confirmada ou bloqueada | `RegistrationTests.Existing_and_new_email_get_the_same_response`; `PasswordResetTests.Existing_and_unknown_email_get_the_same_response`; `LoginTests.Wrong_password_and_unknown_email_get_the_same_response` |
| 22 | S | Conta criada com e-mail alheio e usada sem confirmação | Login exige e-mail confirmado; token de confirmação de uso único e com validade | `LoginTests.Unconfirmed_account_cannot_sign_in`; `EmailConfirmationTests.Confirmation_token_cannot_be_reused` |
| 23 | S | Sessão sequestrada ou fixada | Cookie `HttpOnly`, `Secure`, `SameSite=Strict`; novo cookie no login; logout e redefinição de senha trocam o security stamp, o que invalida sessões antigas | `SessionTests.Session_cookie_is_httponly_secure_and_samesite_strict`; `SessionTests.Login_issues_a_new_session_cookie`; `SessionTests.Old_cookie_is_rejected_after_logout`; `PasswordResetTests.Reset_invalidates_existing_sessions` |
| 24 | T | CSRF numa mutação (criar organização, excluir site, excluir conta) | Antiforgery exigido em todo método não seguro, além do `SameSite=Strict` | `AntiforgeryTests.Mutation_without_antiforgery_token_is_rejected_and_changes_nothing` (cada rota de escrita) |
| 25 | S/E | Rota nova sem autenticação | *Fallback policy* exige usuário autenticado; só a allowlist pública (health, cadastro, confirmação, login, recuperação, antiforgery) fica anônima | `RouteInventoryTests.Every_route_outside_the_public_allowlist_returns_401_without_credentials` |
| 26 | I/E | Usuário da organização B lê, altera ou remove recurso da organização A (IDOR) | Toda query filtra por id **e** associação do usuário; recurso alheio responde 404, igual ao inexistente | `OrganizationIsolationTests.Other_organization_returns_404_without_its_data`; `SiteIsolationTests.Every_site_route_returns_404_for_another_organization_and_leaves_the_site_intact`; `SiteIsolationTests.Site_list_shows_only_the_callers_organization` |
| 27 | E | `Member` executa ação de `Owner` (remover site, excluir organização) | Política de autorização por papel no caso de uso; `Member` recebe 403 | `OrganizationRoleTests.Member_gets_403_when_deleting_the_organization`; `OrganizationRoleTests.Member_gets_403_when_removing_a_site` |
| 28 | T/E | Mass assignment: cliente envia `id`, `role`, `ownerId`, `organizationId`, `status` ou `verifiedAt` | DTOs de entrada estritos (`UnmappedMemberHandling.Disallow`); dono, papel, estado e ids vêm do servidor; verificação só pela ação dedicada | `RequestBodyTests.Unknown_or_server_owned_fields_return_400_and_change_nothing` |
| 29 | T | Site marcado como verificado sem controle do domínio | Token de 128 bits por site e organização; verificado só se um TXT de `_traceon-challenge.<domínio>` for exatamente igual ao token; resposta DNS ausente, com erro ou diferente mantém "não verificado" | `SiteVerificationTests.Matching_txt_record_verifies_the_site`; `SiteVerificationTests.Missing_or_different_txt_record_keeps_the_site_unverified`; `SiteVerificationTests.Token_of_another_organization_does_not_verify` |
| 30 | T/I | Domínio inválido ou perigoso aceito no cadastro: IP literal, `localhost`, nome de um rótulo só, userinfo, porta, esquema, caractere de controle, tamanho excessivo | A entrada é só o nome de domínio, com parse para tipo próprio (`DomainName`): IDN convertido para punycode, até 253 caracteres, rótulos até 63; esquema, path, porta, userinfo, IP literal, loopback e nomes internos (`.local`, `.internal`, `localhost`) rejeitados com 400. Nenhuma requisição HTTP ao domínio nesta etapa | `DomainNameTests.Rejects_invalid_or_internal_names` (`127.0.0.1`, `[::1]`, `169.254.169.254`, `10.0.0.1`, `2130706433`, `user:pass@x.com`, `x.com:8080`, `javascript:`, `\u0000`, rótulo de 64, nome de 254); `DomainNameTests.Normalizes_case_and_idn` |
| 31 | D | Abuso da verificação: rajada de consultas DNS ou resolvedor lento segurando a requisição | Rate limit por usuário na ação de verificar; timeout de DNS; uma consulta por pedido, sem retry ilimitado | `RateLimitTests.Verification_beyond_the_limit_returns_429_with_retry_after`; `SiteVerificationTests.Slow_resolver_times_out_and_keeps_the_site_unverified` |
| 32 | D | Custo de CPU do hash de senha e criação ilimitada de recursos | Rate limit em cadastro, login e recuperação; senha de 12 a 128 caracteres; limites por conta (organizações por usuário, sites por organização) como configuração | `RateLimitTests.Registration_beyond_the_limit_returns_429_with_retry_after`; `RegistrationTests.Password_outside_length_limits_returns_400`; `SiteRegistrationTests.Site_beyond_the_organization_limit_returns_409` |
| 33 | I | Senha, cookie, token de e-mail ou e-mail vazam em log, resposta ou header | Log por allowlist de campos (id do usuário, nunca e-mail); nenhum header `Authorization` ou `Cookie` logado; corpo e erros sem esses dados | `LogLeakTests.Canaries_in_password_token_and_email_never_reach_logs_or_responses` |
| 34 | I | Resposta expõe dado demais (outros membros, hash, stamp, token de desafio de outro site) | DTOs de saída por allowlist | `ResponseShapeTests.Account_organization_and_site_bodies_have_exactly_the_expected_keys` |
| 35 | T | Troca de senha ou exclusão de conta por quem só tem a sessão aberta | Exige a senha atual | `AccountTests.Deleting_the_account_requires_the_current_password` |
| 36 | R | Login, falha de login e exclusões sem trilha | Evento estruturado com id do usuário, organização, ação e `traceId` (sem e-mail); a trilha de auditoria persistente fica para o módulo Audit (etapa 5) | `AuditLogTests.Sign_in_and_deletions_emit_a_structured_event_without_email` |
| 37 | I | Dados ficam após a exclusão da conta | Exclusão remove usuário, organizações em que é o único `Owner` e seus sites, numa transação | `AccountTests.Deleting_the_account_removes_its_organizations_and_sites` |

## Riscos aceitos e pendências da etapa 2

| # | Risco | Situação | Decisão |
|---|---|---|---|
| R7 | O rate limiter do ASP.NET Core guarda as contagens na memória da instância; com várias réplicas o limite se multiplica | **Aceito** enquanto houver uma instância só (dev local) | Etapa 6: limite no reverse proxy ou estado compartilhado |
| R8 | As chaves da Data Protection (cookie e tokens de e-mail) ficam no disco local por padrão; numa troca de contêiner as sessões caem | **Aceito** em dev | Etapa 6: persistir as chaves em armazenamento protegido |
| R9 | A comprovação não expira: o domínio pode mudar de dono depois de verificado | **Aceito** nesta etapa, porque não há coleta | Etapa 3: revalidar o TXT antes de cada ciclo de coleta |
| R10 | A resposta do DNS não é autenticada (sem validação DNSSEC); um resolvedor envenenado poderia confirmar um site | **Aceito:** consequência limitada a um site marcado verificado sem coleta nesta etapa | Reavaliar na etapa 3, junto da revalidação |
| R1 | `/health/ready` sem rate limit (vindo da Foundation) | Continua aceito | Decidir quando o rate limiter entrar: aplicar ou não a política aos paths de health |
| R3 | Exceção completa no log do 500 (vindo da Foundation) | Passa a ser tratada pela ameaça 33 | Fechar com a allowlist de campos de log desta etapa |

# Ameaças das próximas etapas

Não implementadas; registradas para que a etapa não comece sem o controle.

- **SSRF do coletor (etapa 3).** O coletor busca URLs fornecidas pelo usuário, o vetor típico de SSRF (SA 5.2–5.3).
  Antes de qualquer coleta externa o prompt mestre exige autorização do site, este modelo e controles na
  **aplicação e na rede**: (1) perguntar se a feature precisa de cada URL (eliminar antes de mitigar); (2) allowlist de
  destino, ou bloqueio de loopback, redes privadas, link-local e metadata (`169.254.169.254`) **depois** de resolver o
  DNS, fixando o IP resolvido na conexão (evita DNS rebinding); (3) redirecionamentos desligados ou revalidados a cada
  salto; (4) **sub-recursos** (scripts, imagens, iframes que a página carrega) passam pelo mesmo filtro; (5) limite de
  tempo, de tamanho de resposta e de profundidade; (6) isolamento de rede do coletor (sem rota para o banco nem para a
  rede interna). Testes exigidos: `127.0.0.1`, `[::1]`, `169.254.169.254`, `10.x`, IP decimal, DNS que resolve para IP
  interno e redirect para interno são rejeitados.
- **Conteúdo externo não confiável (etapas 3 e 4).** HTML, scripts, headers e texto coletados são dado, nunca
  instrução: validados contra schema, limitados em tamanho, renderizados sem interpretação (sem `dangerouslySetInnerHTML`),
  e nunca entram em prompt, log nem comando como confiáveis.
- **Revalidação da comprovação antes da coleta (etapa 3).** O worker confere de novo o TXT antes de cada ciclo de
  coleta; se o registro sumiu, o site volta a "não verificado" e não é coletado (risco R9).

Decisões e convenções que sustentam esta tabela: [ADR 0007](../adr/0007-convencoes-de-api-e-seguranca-da-foundation.md)
e [ADR 0004](../adr/0004-health-checks-liveness-e-readiness.md).

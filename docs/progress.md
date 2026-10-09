# Progresso

Última atualização: 2026-10-09. Roteiro: `docs/prompt-mestre.md`, seção 4. Cada etapa só começa com
autorização explícita; o roteiro não autoriza implementar etapas futuras.

## Etapas

| Etapa | Status | Observações |
|---|---|---|
| 1. Foundation | Concluída em 2026-10-09 | API, PostgreSQL local, frontend integrado, testes e CI executada no GitHub (os 4 jobs passaram). Pendente só a revisão das ADRs pelo responsável (veja [acceptance.md](acceptance.md)) |
| 2. Identity & Sites | Em andamento | Decisões tomadas em 2026-10-09 ([ADR 0008](adr/0008-identidade-organizacoes-e-comprovacao-de-sites.md)); STRIDE escrito (ameaças 20 a 37, testes planejados) e [contrato](api/contrato-etapa-2.md) em rascunho para revisão; próximo passo: tracer bullet (cadastro → login → organização → site não verificado) |
| Migração do backend para Java | Concluída em 2026-10-09 | Aprovada e executada em 2026-10-09 ([ADR 0009](adr/0009-migracao-do-backend-para-java-e-spring-boot.md), [plano](planejamento-migracao-java.md)): planejamento (#4), fase 0 (#5) e fases 1 a 4 (#6) mesclados na `main`, com a CI verde. Contrato da etapa 2 revisado no PR #3, aguardando revisão. A etapa 2 recomeça em Java pelo tracer bullet planejado |
| 3. Monitoring | Não iniciada | Depende da etapa 2 (sites com controle comprovado) |
| 4. Integrity | Não iniciada | Depende da etapa 3 |
| 5. Findings & Notifications | Não iniciada | Depende da etapa 4 |
| 6. Cloud | Não iniciada | Nada provisionado no Azure; exige confirmar saldo, região autorizada, cotas e preços antes |

## O que a Foundation entregou

Entregue em .NET e migrada para Java 25 + Spring Boot 4.1.1 com os mesmos comportamentos e testes equivalentes
([ADR 0009](adr/0009-migracao-do-backend-para-java-e-spring-boot.md)). Estado atual:

- Um módulo Maven com os pacotes técnicos `health` e `shared` (nenhum módulo de negócio ainda); regra da dependência
  e fronteiras verificadas por ArchUnit e Spring Modulith.
- `/health/live` e `/health/ready` (só GET; 405 nos outros métodos) com contrato fixo, sonda de banco com timeout e
  Problem Details (404, 405, 500 genérico com `traceId`).
- OpenAPI code-first (springdoc) versionado em `docs/api/openapi.json` (drift conferido por teste e Spectral + OWASP
  na CI); endpoint `/openapi/v1.json` só no profile `api-docs`.
- Headers de segurança em toda resposta, inventário de rotas como fitness function e
  [modelo de ameaças](security/threat-model.md) com um teste (ou pendência) por mitigação.
- PostgreSQL 18 no Docker Compose (loopback), configuração por variáveis de ambiente (`SPRING_DATASOURCE_*`) com falha
  na partida.
- Frontend React com o painel "Estado do sistema" (carregando, operacional, banco indisponível, API sem resposta,
  resposta inesperada), acessível por teclado, validado em 1200 px e 400 px.
- CI (backend, frontend, api-spec, gitleaks) executada no GitHub e Dependabot ativo (PRs #1 e #2).
- ADRs 0001 a 0007.

## Etapa 2, Identity & Sites

Escopo (do roteiro): identidade, organizações, permissões, cadastro de sites e comprovação de controle, com
isolamento testado. Autorizada em 2026-10-09; nada implementado ainda.

### Decisões (tomadas em 2026-10-09, ver ADR 0008)

| Decisão | Escolha |
|---|---|
| Provedor de identidade e sessão | Spring Security 7 + Spring Session JDBC + fluxos de conta próprios (revisado pelo ADR 0009, D6; era ASP.NET Core Identity), cookie `HttpOnly`/`Secure`/`SameSite=Strict` e CSRF (`csrf.spa()`) |
| Organização e papéis | `Membership(user, organization, role)` com `Owner` e `Member`; várias organizações por usuário |
| Banco e migrations | Schemas `identity` e `sites` no mesmo `DbContext`; PK UUIDv7; migrations aplicadas por comando explícito |
| Comprovação de controle do site | DNS TXT primeiro; sem HTTP à URL do usuário nesta etapa |
| Dados pessoais | Só e-mail e hash de senha, removidos com a conta; STRIDE em `security/threat-model.md` (ameaças 20 a 37) |
| Enumeração no cadastro | Confirmação de e-mail obrigatória; cadastro responde sempre 202 |
| Permissões do `Member` | Lê, cadastra e verifica sites; só `Owner` remove site e exclui a organização |
| Domínio em várias organizações | Permitido, cada uma comprova: `UNIQUE (organização, domínio)` |
| Expiração da comprovação | Não expira nesta etapa; a etapa 3 revalida antes de coletar |
| Endpoints de conta | Próprios sobre `UserManager`/`SignInManager`, sem `MapIdentityApi` (token na query, enumeração no cadastro, rotas fora do escopo) |
| Erro de validação | `HttpValidationProblemDetails` do framework |

### Pendências de API e segurança herdadas da Foundation

Itens que a Foundation não pôde fazer por falta de entrada, escrita ou autenticação (ver
[threat-model.md](security/threat-model.md) e [ADR 0007](adr/0007-convencoes-de-api-e-seguranca-da-foundation.md)).

**Etapa 2:**

- DTO de entrada estrito: `FAIL_ON_UNKNOWN_PROPERTIES` do Jackson; campo desconhecido, `id`, `status`, dono ou
  papel vindos do cliente → 400, banco inalterado.
- Autenticação com mecanismo consolidado e `SecurityFilterChain` que termina em `anyRequest().authenticated()`; o `RouteInventoryIT`
  passa a exigir **401** sem credencial para toda rota fora da allowlist pública.
- Rate limit (login e endpoints caros; 429 com `Retry-After`) e a decisão sobre limite ou cache em
  `/health/ready` (risco R1, aceito na Foundation). Se health ganhar limite, as regras OWASP de rate limit voltam
  para os paths de health no `.spectral.yaml`.
- `ETag` na leitura e `If-Match` em PUT/PATCH (412 se desatualizado).
- Schemathesis (`--checks=all`) e `oasdiff breaking` na CI, ao lado do Spectral.
- ~~Schema de erro próprio com lista `errors[]`~~: decidido em 2026-10-09 usar o `HttpValidationProblemDetails` do framework (`errors` como mapa campo → mensagens); o override do Spectral para o schema `ProblemDetails` continua.
- Revisão de logs: allowlist de campos e canário de vazamento nos logs de requisição com dado de usuário (risco R3).

**Etapa 6 (Cloud):**

- Allowlist de `Host` com os hosts reais (hoje qualquer um, risco R2), HSTS e `servers` por ambiente na spec (as regras
  `owasp:api9:2023-inventory-*` voltam), `info-contact` com o canal público.
- CORS × reverse proxy em produção (o Vite só resolve em desenvolvimento; ADR 0006) e CSP do frontend na hospedagem
  (os headers atuais protegem só a API).
- Limite de taxa no ponto de entrada (reverse proxy/Azure).

**Manutenção:**

- As versões do Spectral e do ruleset OWASP ficam em `env:` no workflow, **fora do Dependabot**: atualizar à mão.

### Primeiro passo proposto (tracer bullet)

Antes de qualquer tela, definir o contrato OpenAPI e os testes de isolamento entre organizações (usuário de uma
organização recebe 404 ao consultar recurso de outra, sem dado alheio no corpo), depois uma fatia fina ponta a
ponta: criar organização → cadastrar site em estado "não verificado". Entidades ricas e Value Objects entram
aqui, conforme o ADR 0002.

## Dívidas e riscos em aberto

- Controles sem teste automatizado: banco só no loopback e segredo fora do bundle (threat-model, linhas 13 e 15).
- Risco aceito: `/health/ready` sem rate limit (R1), qualquer `Host` aceito (R2), exceção completa no log do 500 (R3).
- Pasta `infrastructure/` ainda inexistente; prevista no prompt mestre e sem conteúdo até haver infraestrutura como código.

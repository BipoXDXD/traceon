# Progresso

Última atualização: 2026-10-09. Roteiro: `docs/prompt-mestre.md`, seção 4. Cada etapa só começa com
autorização explícita; o roteiro não autoriza implementar etapas futuras.

## Etapas

| Etapa | Status | Observações |
|---|---|---|
| 1. Foundation | Concluída em 2026-10-09 | API, PostgreSQL local, frontend integrado, testes e CI executada no GitHub (os 4 jobs passaram). Pendente só a revisão das ADRs pelo responsável (veja [acceptance.md](acceptance.md)) |
| 2. Identity & Sites | Em andamento | Decisões tomadas em 2026-10-09 ([ADR 0008](adr/0008-identidade-organizacoes-e-comprovacao-de-sites.md)); STRIDE escrito (ameaças 20 a 37, testes planejados); próximo passo: contrato OpenAPI, ainda sem código |
| Migração do backend para Java | Fase 1 feita localmente | Aprovada em 2026-10-09 ([ADR 0009](adr/0009-migracao-do-backend-para-java-e-spring-boot.md), [plano](planejamento-migracao-java.md)). Fase 0 no PR #5. Fase 1 (esqueleto Maven, job `backend-java` na CI) na branch `feat/backend-java`, com `./mvnw verify` verde local e na CI (PR #6); próximo: fase 2 (paridade da Foundation). A etapa 2 recomeça em Java depois do corte |
| 3. Monitoring | Não iniciada | Depende da etapa 2 (sites com controle comprovado) |
| 4. Integrity | Não iniciada | Depende da etapa 3 |
| 5. Findings & Notifications | Não iniciada | Depende da etapa 4 |
| 6. Cloud | Não iniciada | Nada provisionado no Azure; exige confirmar saldo, região autorizada, cotas e preços antes |

## O que a Foundation entregou

- Solução .NET com `Domain`, `Application`, `Infrastructure` e `Api` (os dois primeiros vazios de propósito) e dois
  projetos de teste; regra da dependência verificada por testes.
- `/health/live` e `/health/ready` como `MapGet` (só GET; 405 nos outros métodos) com contrato fixo, sonda de banco
  com timeout e Problem Details (404, 405, 500 genérico com `traceId`).
- OpenAPI code-first gerado no build e versionado em `docs/api/openapi.json` (drift check e Spectral + OWASP na CI);
  endpoint `/openapi/v1.json` só em Development.
- Headers de segurança em toda resposta, inventário de rotas como fitness function e
  [modelo de ameaças](security/threat-model.md) com um teste (ou pendência) por mitigação.
- PostgreSQL 18 no Docker Compose (loopback), configuração por User Secrets/variável de ambiente com falha na partida.
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
| Provedor de identidade e sessão | ASP.NET Core Identity com cookie `HttpOnly`/`Secure`/`SameSite=Strict` e antiforgery |
| Organização e papéis | `Membership(user, organization, role)` com `Owner` e `Member`; várias organizações por usuário |
| Banco e migrations | Schemas `identity` e `sites` no mesmo `DbContext`; PK UUIDv7; migrations aplicadas por comando explícito |
| Comprovação de controle do site | DNS TXT primeiro; sem HTTP à URL do usuário nesta etapa |
| Dados pessoais | Só e-mail e hash de senha, removidos com a conta; STRIDE em `security/threat-model.md` (ameaças 20 a 37) |
| Enumeração no cadastro | Confirmação de e-mail obrigatória; cadastro responde sempre 202 |
| Permissões do `Member` | Lê, cadastra e verifica sites; só `Owner` remove site e exclui a organização |
| Domínio em várias organizações | Permitido, cada uma comprova: `UNIQUE (organização, domínio)` |
| Expiração da comprovação | Não expira nesta etapa; a etapa 3 revalida antes de coletar |

### Pendências de API e segurança herdadas da Foundation

Itens que a Foundation não pôde fazer por falta de entrada, escrita ou autenticação (ver
[threat-model.md](security/threat-model.md) e [ADR 0007](adr/0007-convencoes-de-api-e-seguranca-da-foundation.md)).

**Etapa 2:**

- DTO de entrada estrito: `UnmappedMemberHandling.Disallow` no JSON; campo desconhecido, `id`, `status`, dono ou
  papel vindos do cliente → 400, banco inalterado.
- Autenticação com mecanismo consolidado e *fallback policy* que exige usuário autenticado; o `RouteInventoryTests`
  passa a exigir **401** sem credencial para toda rota fora da allowlist pública.
- Rate limit (login e endpoints caros; 429 com `Retry-After`) e a decisão sobre limite ou cache em
  `/health/ready` (risco R1, aceito na Foundation). Se health ganhar limite, as regras OWASP de rate limit voltam
  para os paths de health no `.spectral.yaml`.
- `ETag` na leitura e `If-Match` em PUT/PATCH (412 se desatualizado).
- Schemathesis (`--checks=all`) e `oasdiff breaking` na CI, ao lado do Spectral.
- Schema de erro próprio com lista `errors[]` (Problem Details); as regras de limite do Spectral voltam para ele.
- Revisão de logs: allowlist de campos e canário de vazamento nos logs de requisição com dado de usuário (risco R3).

**Etapa 6 (Cloud):**

- `AllowedHosts` com os hosts reais (hoje `*`, risco R2), HSTS e `servers` por ambiente na spec (as regras
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
- Risco aceito: `/health/ready` sem rate limit (R1), `AllowedHosts: "*"` (R2), exceção completa no log do 500 (R3).
- Pasta `infrastructure/` ainda inexistente; prevista no prompt mestre e sem conteúdo até haver infraestrutura como código.

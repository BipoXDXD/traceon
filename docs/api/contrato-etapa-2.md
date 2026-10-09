# Contrato da etapa 2: conta, organizações e sites

- **Status:** rascunho para revisão (2026-10-09). Escrito antes do código. A spec continua code-first
  ([ADR 0007](../adr/0007-convencoes-de-api-e-seguranca-da-foundation.md)): ao implementar, os endpoints geram
  `openapi.json`, e este documento é a referência que a spec tem de cumprir.
- **Stack:** Java 25 + Spring Boot 4.1.1 ([ADR 0009](../adr/0009-migracao-do-backend-para-java-e-spring-boot.md),
  D6): Spring Security 7, Spring Session JDBC e fluxos de conta próprios. Revisado em 2026-10-09 no corte da migração.
- **Base:** [ADR 0008](../adr/0008-identidade-organizacoes-e-comprovacao-de-sites.md) e
  [modelo de ameaças](../security/threat-model.md#etapa-2-identity--sites), ameaças 20 a 37 (citadas como T20…T37).

## Convenções

| Tema | Regra |
|---|---|
| Prefixo | `/api`, sem versão no path: há um frontend próprio, uma versão só e mudanças aditivas. O proxy do Vite passa a encaminhar `/api` além de `/health` |
| Endpoints de conta | Próprios, em controllers sobre o Spring Security (`AuthenticationManager`, `DelegatingPasswordEncoder` com bcrypt); **sem form login nem HTTP Basic**. Tokens de confirmação e de redefinição aleatórios (128 bits), guardados só com hash, com validade e uso único |
| Autenticação | Sessão do Spring Session JDBC, guardada no PostgreSQL; cookie `HttpOnly`, `Secure`, `SameSite=Strict`, sem persistência além da sessão do navegador. A `SecurityFilterChain` termina em `anyRequest().authenticated()`; as rotas anônimas estão marcadas abaixo e formam a allowlist do `RouteInventoryIT` (T25). XHR sem sessão recebe 401, nunca redirecionamento |
| CSRF | `csrf.spa()` do Spring Security: todo método não seguro exige no header `X-XSRF-TOKEN` o valor do cookie `XSRF-TOKEN` (legível pelo frontend). O cookie vem de `GET /api/csrf` e é trocado no login e no logout, então o frontend relê o cookie depois deles (T24) |
| JSON | `camelCase`; entrada em `record` com `FAIL_ON_UNKNOWN_PROPERTIES` do Jackson: campo desconhecido → 400 (T28). Enums como string |
| Ids | `uuid` (UUIDv7 gerado pelo servidor); nunca aceitos no corpo de criação |
| Datas | ISO 8601 em UTC (`2026-10-09T12:00:00Z`) |
| Listas | Objeto `{ "items": [...] }` para crescer de forma aditiva. Sem paginação nesta etapa: o tamanho é limitado pelas cotas por conta (até 10 organizações por usuário e 50 sites por organização, por configuração) |
| Erros | Problem Details (`application/problem+json`, `ProblemDetail` do Spring). 400 de validação com o membro de extensão `errors` como mapa campo → mensagens (o formato do `HttpValidationProblemDetails` decidido antes da migração, agora montado no `@RestControllerAdvice`), sem repetir o valor recebido. 401 sem corpo de detalhe; 403 e 404 genéricos; 409 com `detail` legível; 429 com `Retry-After`; 500 genérico com `traceId` (Foundation) |
| Acesso alheio | Recurso de outra organização responde **404**, igual ao inexistente (T26). Papel insuficiente na própria organização responde **403** (T27) |
| Cache | `Cache-Control: no-store` em toda resposta de `/api` |
| Dados sensíveis | Nenhum e-mail, senha ou token em path ou query (T33). O link dos e-mails leva `userId` e `token` no **fragmento** (`#`), que o navegador não envia ao servidor; o frontend os repassa no corpo do POST |
| `ETag`/`If-Match` | Não se aplica: esta fatia não tem PUT nem PATCH. Entra junto do primeiro endpoint de edição |

## Limites de entrada

| Campo | Regra | Inválido |
|---|---|---|
| `email` | 3 a 254 caracteres, formato de e-mail, sem caracteres de controle | 400 |
| `password`, `newPassword` | 12 a 128 caracteres; sem outras regras de composição (NIST 800-63B) | 400 |
| `currentPassword` | 1 a 128 caracteres | 400 |
| `token` (e-mail) | 1 a 1024 caracteres | 400 genérico |
| `userId` | `uuid` | 400 genérico |
| `name` (organização) | 1 a 100 caracteres após `Trim`, sem caracteres de controle | 400 |
| `domain` | Só o nome: 1 a 253 caracteres, rótulos de 1 a 63, IDN convertido para punycode e minúsculas; rejeita esquema, path, porta, userinfo, IP literal, `localhost`, `.local`, `.internal` e nome de um rótulo só (T30) | 400 |

## Operações

Legenda de acesso: **anônima** (na allowlist pública), **autenticada**, **membro** (qualquer papel na organização),
**owner**. Todo método não seguro também exige o token CSRF.

### CSRF

| Método e path | `operationId` | Acesso | Corpo | Respostas |
|---|---|---|---|---|
| `GET /api/csrf` | `getCsrfToken` | anônima | — | 204 com `Set-Cookie: XSRF-TOKEN` (sem `HttpOnly`, `SameSite=Strict`), que o frontend devolve no header `X-XSRF-TOKEN` |

### Conta

| Método e path | `operationId` | Acesso | Corpo | Respostas |
|---|---|---|---|---|
| `POST /api/account/register` | `registerAccount` | anônima | `{ email, password }` | **202 sempre**, sem corpo, exista ou não o e-mail (T21). E-mail novo recebe o link de confirmação; e-mail existente recebe um aviso de tentativa de cadastro. 400 entrada inválida (validada antes de consultar a existência). 429 |
| `POST /api/account/confirm-email` | `confirmEmail` | anônima | `{ userId, token }` | 204. 400 genérico para usuário inexistente, token inválido, expirado ou já usado (T22) |
| `POST /api/account/resend-confirmation` | `resendConfirmationEmail` | anônima | `{ email }` | **202 sempre**, sem corpo. 429 |
| `POST /api/account/login` | `signIn` | anônima | `{ email, password }` | 204 com `Set-Cookie` de sessão nova (T23). **401 genérico** para senha errada, conta inexistente, não confirmada ou bloqueada (T21, T22). 429 |
| `POST /api/account/logout` | `signOut` | autenticada | — | 204; a sessão é apagada no banco e o cookie antigo deixa de valer |
| `POST /api/account/forgot-password` | `requestPasswordReset` | anônima | `{ email }` | **202 sempre**, sem corpo (T21). 429 |
| `POST /api/account/reset-password` | `resetPassword` | anônima | `{ userId, token, newPassword }` | 204; todas as sessões da conta são apagadas no banco. 400 genérico para usuário ou token inválidos |
| `GET /api/account` | `getAccount` | autenticada | — | 200 `AccountResponse` |
| `POST /api/account/delete` | `deleteAccount` | autenticada | `{ currentPassword }` | 204; remove o usuário, as organizações em que é o único `Owner` e os sites delas, numa transação (T35, T37). 400 senha atual errada (sem lockout separado; conta no rate limit do login) |

Exclusão de conta como POST de ação dedicada, porque exige a senha no corpo e DELETE com corpo não é confiável em
proxies.

### Organizações

| Método e path | `operationId` | Acesso | Corpo | Respostas |
|---|---|---|---|---|
| `POST /api/organizations` | `createOrganization` | autenticada | `{ name }` | 201 `OrganizationResponse` + `Location`; quem cria vira `Owner`. 409 cota de organizações atingida |
| `GET /api/organizations` | `listOrganizations` | autenticada | — | 200 `{ items: OrganizationResponse[] }`, só as do usuário |
| `GET /api/organizations/{organizationId}` | `getOrganization` | membro | — | 200 `OrganizationResponse`. 404 inexistente ou alheia |
| `DELETE /api/organizations/{organizationId}` | `deleteOrganization` | owner | — | 204, com os sites. 403 `Member`. 404 inexistente ou alheia |

### Sites

| Método e path | `operationId` | Acesso | Corpo | Respostas |
|---|---|---|---|---|
| `POST /api/organizations/{organizationId}/sites` | `registerSite` | membro | `{ domain }` | 201 `SiteResponse` + `Location`, com `status: "unverified"` e as instruções do TXT. 400 domínio inválido. 404 organização alheia. 409 domínio já cadastrado nesta organização ou cota de sites atingida |
| `GET /api/organizations/{organizationId}/sites` | `listSites` | membro | — | 200 `{ items: SiteResponse[] }`. 404 organização alheia |
| `GET /api/organizations/{organizationId}/sites/{siteId}` | `getSite` | membro | — | 200 `SiteResponse`. 404 |
| `DELETE /api/organizations/{organizationId}/sites/{siteId}` | `removeSite` | owner | — | 204. 403 `Member`. 404 |
| `POST /api/organizations/{organizationId}/sites/{siteId}/verify` | `verifySite` | membro | — | 200 `SiteResponse` com `lastVerification` atualizado. Site já verificado continua verificado (idempotente). 404. 429 (por usuário, T31) |

`verify` é ação dedicada: o estado do site nunca muda por PUT/PATCH (T28). Falha de verificação não é erro HTTP: o
pedido foi atendido e o resultado vem em `lastVerification.outcome`.

## Respostas

```jsonc
// AccountResponse
{ "id": "uuid", "email": "string" }

// OrganizationResponse
{ "id": "uuid", "name": "string", "role": "owner" | "member", "createdAt": "date-time" }

// SiteResponse
{
  "id": "uuid",
  "domain": "loja.example.com",
  "status": "unverified" | "verified",
  "verifiedAt": "date-time" | null,
  "challenge": {
    "recordName": "_traceon-challenge.loja.example.com",
    "recordType": "TXT",
    "recordValue": "traceon-verification=<token>"
  },
  "lastVerification": {
    "checkedAt": "date-time",
    "outcome": "verified" | "recordNotFound" | "recordMismatch" | "lookupFailed"
  } | null,
  "createdAt": "date-time"
}
```

O conjunto de chaves de cada resposta é exatamente o listado (T34). `challenge` é do próprio site: o valor vai
para o DNS do usuário e não é segredo, mas só membros da organização o veem. `lookupFailed` cobre timeout e erro
do resolvedor, sem detalhe interno.

## Rate limit

Por IP, salvo indicação. Bucket4j com os baldes no PostgreSQL: o limite vale somado entre réplicas (fecha o risco R7). Toda recusa responde 429 em Problem Details
com `Retry-After`.

| Política | Operações | Limite inicial (configurável) |
|---|---|---|
| `sign-in` | `signIn` | 10 por minuto por IP; o lockout por conta soma 5 falhas → 5 min (T20) |
| `account-email` | `registerAccount`, `resendConfirmationEmail`, `requestPasswordReset` | 5 por 15 minutos por IP (T32) |
| `site-verification` | `verifySite` | 10 por hora por usuário (T31) |

Os health checks continuam sem limite (R1): a decisão de aplicar uma política a eles fica registrada quando o rate
limiter entrar no código.

## Verificação na CI ao implementar

- Drift e Spectral OWASP como hoje. Os `overrides` do `.spectral.yaml` não se estendem a `/api`: as rotas novas
  precisam declarar `security` e limites nos DTOs.
- **Schemathesis** (`--checks=all`) contra a API com banco real, sem 500, e **`oasdiff breaking`** contra a spec do
  `main`: entram no mesmo PR dos primeiros endpoints (pendência herdada da Foundation).

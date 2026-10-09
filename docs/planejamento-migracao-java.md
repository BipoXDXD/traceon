# Planejamento: migração do backend para Java 25 + Spring Boot 4.1.1

- **Status:** aprovado pelo responsável em 2026-10-09 (decisões abaixo). Nada foi migrado ainda; a fase 0
  (emenda ao prompt mestre e ADR 0009) vem primeiro.

**Decisões do responsável (2026-10-09), todas conforme a recomendação:**

| # | Escolha |
|---|---|
| D1 | Emendar o prompt mestre para Java 25, Spring Boot 4.1.1, JUnit 6 e Playwright Java |
| D3 | Mesmo repositório; pasta local movida para `~/Documents/Projetos/Java/traceon` |
| D4 | Maven com wrapper |
| D5 | Um módulo Maven, pacote por módulo de negócio, ArchUnit + Spring Modulith |
| D6 | Spring Security 7 + Spring Session JDBC + fluxos de conta próprios |
| D7 | Spring Data JPA + `JdbcClient`; Flyway em SQL, desligado na partida e aplicado por comando |
| D8 | Manter o JSON atual do health; Actuator só em porta de gerenciamento |
| D9 | Substituição completa na branch `feat/backend-java` |
- **Escopo:** o backend (`backend/`) e o que depende dele: CI, Dependabot, README, CLAUDE.md, ADRs e o contrato da
  etapa 2. O frontend React, o Docker Compose, o PostgreSQL e o modelo de ameaças continuam válidos.

## 1. Estado do repositório nesta data

| Item | Situação |
|---|---|
| `main` | `004514e`, CI verde nos 4 jobs (backend, frontend, api-spec, gitleaks) |
| PR #3 | `docs/contrato-etapa-2`: contrato da API da etapa 2, só documentação. Aberto, aguardando revisão |
| PRs #1 e #2 (Dependabot) | Verdes; sem merge até conferir a idade mínima de 2 semanas (ADR 0003). Se a migração for aprovada, o #2 (NuGet) perde o sentido e pode ser fechado |
| Etapa 2 | Decisões (ADR 0008), STRIDE (T20–T37) e contrato prontos; **nenhum código**. É o melhor momento para trocar de stack |

## 2. O que existe para migrar

O backend é pequeno: cerca de 330 linhas de produção e 810 de teste.

| .NET hoje | Linhas | Comportamento que precisa sobreviver |
|---|---|---|
| `Program.cs` | 43 | Validação de escopo e de build do container; OpenAPI só em Development; partida falha sem connection string |
| `Health/HealthEndpoints.cs`, `HealthResponses.cs` | 124 | `GET /health/live` (sem banco) e `/health/ready` (banco real); JSON fixo consumido pelo frontend; 503 quando `Unhealthy`; só GET (405 com `Allow: GET`); `Cache-Control: no-store` |
| `Http/SecurityHeaders.cs` | 30 | `nosniff`, `no-referrer`, `X-Frame-Options: DENY`, CSP `default-src 'none'; frame-ancestors 'none'`, inclusive no 500 |
| `OpenApi/OpenApiDocumentSetup.cs` | 24 | `operationId`, tags, respostas e header documentados; spec gerada e versionada em `docs/api/openapi.json` |
| `Infrastructure/*` | 110 | `DatabaseOptions` (ausente, vazia ou malformada → falha na partida sem ecoar o valor); sonda de banco com timeout de 3 s; `DbContext` vazio |
| Testes | 810 | 45 casos: 8 de arquitetura (regra da dependência) e 37 de integração (health, pipeline HTTP, headers, 500 genérico, inventário de rotas, spec, partida) |

Fora do backend: `ci.yml` (jobs `backend` e `api-spec` mudam), `dependabot.yml` (NuGet → Maven), `global.json`,
`backend/Directory.Packages.props`, README, CLAUDE.md e os ADRs 0001, 0003, 0005, 0007 e 0008.

## 3. Decisões que são suas

Cada uma tem uma recomendação, e o bottom line de cada item está na última frase.

### D1. Emendar o prompt mestre (obrigatória)

O `docs/prompt-mestre.md` manda sobre qualquer outro texto e fixa .NET em quatro pontos: backend "C# 14, ASP.NET
Core 10, EF Core 10, Npgsql" (linha 17), testes "xUnit" (linha 19), worker "Playwright .NET" (linha 69) e o livro
"C# Concurrency" na lista de leituras (linha 139). Sem a emenda, a migração viola a fonte de verdade do projeto.

**Recomendo** reescrever essas linhas para Java 25, Spring Boot 4, Spring Data JPA, JUnit 6, Playwright Java e
trocar o livro 15 por *Java Concurrency in Practice* ou pelo material de concorrência que você preferir. A emenda
fica registrada num ADR (D10). Custo: o prompt deixa de ser o texto original; o histórico do Git guarda a versão
anterior.

### D2. Por que migrar (trade-off a registrar)

| A favor | Contra |
|---|---|
| Você já tem um projeto Java 25 + Boot 4 funcionando (`duora-api`, na 4.1.1), com padrões prontos: drift da spec por teste, Bucket4j no Postgres, ArchUnit, jqwik e PIT | **O ASP.NET Core Identity não tem equivalente no Spring.** O Spring Security cuida de hash, sessão, CSRF e fixação de sessão, mas cadastro, confirmação de e-mail, redefinição de senha e lockout viram código nosso (ver D6) |
| Spring Session JDBC guarda a sessão no Postgres: logout invalida de verdade no servidor e as sessões sobrevivem a réplicas (fecha o R8 e o problema do cookie atrelado ao security stamp) | Base de conhecimento e prompt mestre foram escritos para .NET; ADRs 0001, 0003, 0005, 0007 e 0008 precisam de revisão |
| Bucket4j com PostgreSQL dá rate limit compartilhado entre réplicas (fecha o R7 já na etapa 2) | A JVM pede mais memória que o .NET no Azure Container Apps; com crédito de US$ 100, conferir custo na etapa 6 |
| `csrf.spa()` do Security 7 já usa cookie `XSRF-TOKEN` + header `X-XSRF-TOKEN`, o mesmo nome do contrato | Uma rodada inteira de trabalho sem feature nova |

Bottom line: se o motivo é consolidar o portfólio em Java, **agora é o momento mais barato**, porque não há código
de negócio. O custo principal é escrever os fluxos de conta que o Identity daria prontos.

### D3. Onde fica o código

| Opção | Prós | Contras |
|---|---|---|
| **A. Mesmo repositório `BipoXDXD/traceon`, pasta movida para `~/Documents/Projetos/Java/traceon` (recomendo)** | Mantém histórico, issues, PRs, docs, ADRs e frontend; o projeto fica junto dos outros Java | O histórico mostra a troca de stack (é um ponto positivo para portfólio, se bem documentada) |
| B. Repositório novo | Começo limpo | Perde histórico e ADRs, ou exige copiá-los; dois repositórios para o mesmo produto |

Mover a pasta muda o caminho da memória do Claude Code (`~/.claude/projects/...TraceOn`): as duas memórias atuais
(porta 5433 e fork do Rodin) precisam ser copiadas para o novo caminho.

### D4. Build: Maven com wrapper (recomendo) × Gradle

Maven, porque é o que o `duora-api` usa e já está validado nesta máquina com Java 25 (`openjdk 25.0.4`). Versões
explícitas fora do BOM ficam em `<properties>`, como hoje no `Directory.Packages.props`.

### D5. Estrutura e regra da dependência

| Opção | Prós | Contras |
|---|---|---|
| **A. Um módulo Maven, pacote por módulo de negócio (`identity`, `sites`, …) com `domain`/`application`/`infrastructure`/`api` dentro; ArchUnit + Spring Modulith `verify()` (recomendo)** | Simples; é o padrão do `duora-api`; Spring Modulith barra acesso a internos de outro módulo; `package-private` esconde implementações | A regra da dependência vira teste (fitness function), não erro de compilação |
| B. Multi-módulo Maven (`traceon-domain`, `-application`, `-infrastructure`, `-api`), espelho dos projetos .NET | A regra da dependência é garantida pelo compilador, como os `<ProjectReference>` de hoje | Mais POMs e configuração; o módulo de negócio vira transversal aos quatro artefatos |

Bottom line: A. O ADR 0001 ganha uma nota: o compilador cede lugar ao ArchUnit, com `package-private` como primeira
barreira.

### D6. Identidade (revisa o ADR 0008)

| Opção | Prós | Contras |
|---|---|---|
| **A. Spring Security 7 + Spring Session JDBC + fluxos de conta próprios (recomendo)** | Mantém as decisões do ADR 0008 e o contrato do PR #3; hash (`DelegatingPasswordEncoder`, bcrypt), sessão, fixação de sessão, CSRF SPA e headers vêm do framework; logout invalida a sessão no banco | Cadastro, confirmação, redefinição e lockout são nossos: tokens aleatórios guardados **com hash**, validade e uso único; lockout por contador na conta ou balde do Bucket4j por conta. Mais testes |
| B. Keycloak (OIDC) + Spring como BFF (`oauth2-client` com PKCE) | Cadastro, confirmação, redefinição, lockout, MFA e passkeys prontos | Mais um serviço para operar, hospedar e manter atualizado; testes precisam de Keycloak no Testcontainers; refaz o ADR 0008 e o contrato |
| C. Entra External ID | Gerenciado, grátis até 50 mil MAU | Exige criar tenant no Azure (proibido sem autorização) |

Bottom line: A. As ameaças T20–T37 continuam valendo; as mitigações mudam de nome (`SecurityFilterChain`,
`SessionRegistry`, Bucket4j) e o modelo de ameaças é atualizado no mesmo PR.

### D7. Persistência e migrations

**Recomendo** Spring Data JPA (Hibernate 7) para os aggregates, `JdbcClient` para leituras sob medida e **Flyway com
SQL versionado** (`db/migration/V1__...sql`), em linha com a regra de SQL revisável. O ADR 0008 proíbe migrations
na partida: a aplicação roda com `spring.flyway.enabled=false` e as migrations são aplicadas por comando explícito
(`./mvnw flyway:migrate` em dev, job próprio no deploy). Os testes ligam o Flyway no contexto de teste.
`spring.jpa.open-in-view=false` e `ddl-auto=validate`.

Alternativa: jOOQ ou só `JdbcClient`, com mais SQL à mão e sem dirty checking.

### D8. Contrato do health

**Recomendo** manter o JSON atual com controllers próprios em `/health/live` e `/health/ready`. Assim o frontend não
muda, e o Actuator fica só para métricas, numa porta de gerenciamento não exposta. A alternativa (formato do
Actuator, `{"status":"UP"}`) obriga a mudar o parse e os testes do frontend.

### D9. Estratégia de corte

| Opção | Prós | Contras |
|---|---|---|
| **A. Substituição completa numa branch, com paridade provada por testes antes do merge (recomendo)** | O backend tem ~330 linhas; um corte só, sem duas stacks no `main` | PR grande (mitigado por commits pequenos, um comportamento por commit) |
| B. Strangler: Java e .NET lado a lado no `main` | Corte gradual | Duas CIs, dois builds, duas specs para nada: não há tráfego real |

### D10. Registro

ADR 0009 "Migração do backend para Java 25 e Spring Boot 4", com Status, Contexto (D2), Decisão e Consequências.
O ADR 0003 (stack) fica **Superseded by 0009**; os ADRs 0001, 0005, 0007 e 0008 recebem nota de revisão, sem
apagar o texto original.

## 4. Stack alvo

Versões conferidas no Maven Central em 2026-10-09, com a regra de idade mínima de 2 semanas do ADR 0003.

| Componente | Versão | Publicada em | Observação |
|---|---|---|---|
| JDK | Temurin 25 (LTS) | — | Local: `openjdk 25.0.4` |
| Spring Boot (parent) | **4.1.1** (escolha do responsável) | 2026-08-20 | GA mais recente; suporte open source da linha 4.1 até 2027-07-31 (a 4.0 acaba em 2026-12-31; a 4.2 está em milestone). BOM: Framework 7.0.9, Security 7.1.1, Hibernate 7.4.5, Spring Session 4.1.1, Jackson 3.1.5, JUnit 6.0.3, Testcontainers 2.0.5, Flyway 12.4.0, driver PostgreSQL 42.7.13 |
| springdoc-openapi | 3.1.1 | 2026-09-06 | A 3.1.x é compilada contra o Boot 4.1; mesma versão do `duora-api`. Só `-api` (sem Swagger UI), habilitado só no profile de dev |
| Spring Modulith | 2.1.1 | 2026-08-25 | Linha que acompanha o Boot 4.1; confirmar na matriz de compatibilidade do projeto antes de fixar |
| Bucket4j (`bucket4j_jdk17-postgresql`) | **8.20.0** | 2026-09-17 | A 8.21.0 saiu em 2026-10-02, abaixo das 2 semanas; reavaliar depois de 2026-10-16 |
| ArchUnit | 1.5.1 | 2026-09-25 | No limite das 2 semanas |
| Flyway, PostgreSQL JDBC, Testcontainers | BOM do Boot 4.1.1 | — | `spring-boot-starter-flyway` + `flyway-database-postgresql` (Flyway 12.4.0); `testcontainers-postgresql` 2.0.5 |
| jqwik, PIT | 1.10.1 / 1.30.0 + plugin 1.2.3 | — | Validados no `duora-api` com JUnit 6 |
| PostgreSQL | 18.6 (imagem atual do Compose) | — | Sem mudança |

Configuração base: `spring.threads.virtual.enabled=true`; `spring.mvc.problemdetails.enabled=true`; logs
estruturados no console; Actuator com `exposure.include=health` em porta de gerenciamento; graceful shutdown
(padrão no Boot 4).

## 5. Mapa de equivalências

| .NET | Java/Spring |
|---|---|
| Minimal APIs | `@RestController` (Spring MVC) com virtual threads |
| `IOptions` + `ValidateOnStart` | `@ConfigurationProperties` em `record` com `@Validated` (falha na partida) |
| `UseDefaultServiceProvider(ValidateOnBuild)` | Injeção por construtor; o contexto falha na partida se faltar bean |
| `AddProblemDetails` + `UseExceptionHandler` | `ProblemDetail` + um único `@RestControllerAdvice`; 500 genérico com `traceId` |
| 405 com `Allow` | Comportamento padrão do MVC (`HttpRequestMethodNotSupportedException`), a confirmar por teste |
| `SecurityHeaders` (middleware) | `http.headers(...)` do Spring Security: CSP, frame options, referrer policy, content type options |
| `MapOpenApi` + `ApiDescription.Server` no build | springdoc; um teste de integração grava `target/openapi.json` e compara com `docs/api/openapi.json` (padrão `OpenApiContractIT` do `duora-api`); a CI segue com drift e Spectral |
| EF Core + Npgsql | Spring Data JPA (Hibernate 7) + driver PostgreSQL; `JdbcClient` para consultas |
| EF Migrations | Flyway com SQL versionado, aplicado por comando |
| ASP.NET Core Identity | Spring Security 7 + Spring Session JDBC + fluxos de conta próprios (D6) |
| Rate limiter do ASP.NET Core (memória) | Bucket4j com PostgreSQL (compartilhado entre réplicas) |
| `TimeProvider` / `FakeTimeProvider` | `java.time.Clock` injetado / `Clock.fixed` ou um `MutableClock` de teste |
| `CancellationToken` | Timeout por chamada (`JdbcClient`/driver, `HttpClient`), interrupção de virtual thread |
| xUnit v3 + `WebApplicationFactory` | JUnit 6 + `@SpringBootTest` + `MockMvc`/`RestTestClient` |
| Testcontainers .NET | Testcontainers 2 com `@ServiceConnection` |
| `DependencyRuleTests` (project references) | ArchUnit + `ApplicationModules.verify()` |
| `dotnet format` + analyzers + `TreatWarningsAsErrors` | `-Werror` no `maven-compiler-plugin` com `-Xlint:all`; formatador (Spotless com Palantir ou Google Java Format, a escolher na fase 1) com `check` na CI |
| Consulta DNS TXT (etapa 2) | Provedor DNS do JDK via JNDI (sem dependência) ou dnsjava, atrás de um adapter; decidir ao implementar |

## 6. Fases

Cada fase termina com CI verde e commits pequenos. Os nomes de teste em Java seguem o comportamento dos atuais.

### Fase 0: decisões e documentos (sem código)

1. Suas escolhas em D1–D9.
2. Emenda do prompt mestre e ADR 0009; ADR 0003 marcado Superseded; notas nos ADRs 0001, 0005, 0007 e 0008.
3. Mover a pasta para `~/Documents/Projetos/Java/traceon` (D3) e copiar as memórias do Claude Code.

### Fase 1: esqueleto

1. Branch `feat/backend-java`. Projeto Maven em `backend/` (substitui a solução .NET no fim da fase 3), parent Boot
   4.1.1, `java.version` 25, wrapper.
2. Compilador com `-Xlint:all -Werror`, formatador com `check` na CI, `maven-dependency-plugin` `analyze` (padrão
   `duora-api`).
3. Pacotes `bootstrap`, `health`, `shared` (configuração e HTTP); ArchUnit e Spring Modulith verificando a estrutura
   desde o primeiro commit.
4. Job `backend` da CI trocado: `actions/setup-java` (Temurin 25, por SHA), `./mvnw verify`, drift da spec.

**Execução (2026-10-09), branch `feat/backend-java`:**

- `backend/pom.xml` (parent 4.1.1, Java 25), wrapper Maven 3.9.16 com `distributionSha256Sum` (só o script
  `mvnw`; o `mvnw.cmd` saiu, o projeto não roda em Windows). Pacote raiz `bipo.tech.traceon`.
- Formatador: **Spotless + Palantir Java Format** (2.99.0), 120 colunas, como os docs; `spotless:check` no `verify`
  e `./mvnw spotless:apply` para corrigir.
- `dependency:analyze-only` com `failOnWarning` no `verify`: o código declara o que usa (`spring-boot`,
  `spring-boot-autoconfigure`, `spring-context`); só starters e a engine do ArchUnit ficam na lista de ignorados.
- `ArchitectureTest` (4 regras: pacote de módulo planejado, `domain` → nada, `application` → só `domain`,
  `infrastructure` sem `api` nem web/servlet) e `ModularityTest` (`ApplicationModules.verify()`). Cada regra foi
  vista falhando com uma violação temporária, depois removida.
- **Desvios:** os pacotes `health` e `shared` não foram criados ainda, porque o prompt mestre proíbe pacote vazio;
  entram na fase 2 com o comportamento. O job .NET `backend` **não** foi trocado: o job novo `backend-java` roda ao
  lado dele até o corte (fase 3), para a paridade ser provada na CI com as duas suítes. O drift da spec continua no
  job .NET até o Java gerar a spec (fase 2).

### Fase 2: paridade da Foundation

Cada item porta o comportamento **e** o teste correspondente. O teste vem primeiro e é visto falhando.

| Comportamento | Testes atuais → equivalentes |
|---|---|
| Partida falha sem connection string ou com valor malformado, sem ecoar o valor | `StartupConfigurationTests` (5 casos) → `ApplicationContextRunner` com as propriedades + um teste do `main` real |
| `/health/live` e `/health/ready`, JSON fixo, 503, timeout de 3 s, só GET, `no-store`, sem diagnóstico nem senha em log | `HealthEndpointTests` (16 casos) → `HealthEndpointIT` com Testcontainers; banco recusando e mudo com o mesmo harness (`UnreachableDatabase`) |
| 404, 405 e 500 genéricos em Problem Details com `traceId`, log com o mesmo id | `HttpPipelineTests` (3 casos), `UnhandledExceptionTests` (3 casos) → `ProblemDetailsIT` com `OutputCaptureExtension` para o log |
| Headers de segurança, inclusive no 500 | `SecurityHeadersTests` (4 casos) → `SecurityHeadersIT` |
| Inventário de rotas (deny by default), OpenAPI só em dev | `RouteInventoryTests` (3 casos) → teste sobre `RequestMappingHandlerMapping`; `HttpPipelineTests.OpenApi_*` → teste por profile |
| Spec com as duas operações, `operationId` e respostas | `OpenApiDocumentTests` (3 casos) → `OpenApiContractIT` (gera, compara com `docs/api/openapi.json`, verifica operações) |
| Regra da dependência | `DependencyRuleTests` (8 casos) → `ArchitectureTest` (ArchUnit) + `ModularityTest` (Modulith) |

Ao fim da fase: a spec gerada pelo Java substitui `docs/api/openapi.json`. O diff tem de ficar restrito a nomes de
schema e detalhes do gerador; a forma das respostas não muda (o frontend é a prova). O `.spectral.yaml` ajusta os
`overrides` para os nomes de schema do springdoc.

### Fase 3: corte

1. Apaga `backend/` .NET, `global.json` e `Directory.Packages.props`; Dependabot de `nuget` para `maven`.
2. Porta da API mantida em 5120 (`server.port`), para o proxy do Vite e o README não mudarem; ou atualiza
   `TRACEON_API_URL` se preferir a 8080.
3. README (execução, User Secrets → variável de ambiente ou `application-local.properties` fora do Git), CLAUDE.md
   do projeto (comandos, convenções Java no lugar das C#), `architecture.md`, `acceptance.md`, `progress.md`.
4. Modelo de ameaças: troca dos nomes de teste da Foundation e das mitigações da etapa 2 (D6, Bucket4j, Spring
   Session); R7 e R8 reavaliados.
5. Contrato da etapa 2 (PR #3): troca `UserManager`/`SignInManager` por Spring Security e o header antiforgery passa
   a ser o padrão `csrf.spa()`.

### Fase 4: verificação

1. CI verde nos 4 jobs; `./mvnw verify` limpo localmente.
2. Validação ponta a ponta como na Foundation: `docker compose up`, API, `npm run dev`, navegador em 1200 px e 400 px
   com banco ligado e desligado.
3. `acceptance.md` atualizado com evidências; PR único `feat/backend-java` com a paridade descrita.

Depois disso a etapa 2 recomeça em Java pelo tracer bullet já planejado (cadastro → confirmação → login →
organização → site não verificado).

## 7. Riscos da migração

| Risco | Mitigação |
|---|---|
| Comportamentos padrão do Spring Security abrem superfície (form login, HTTP Basic, senha gerada no log, página de login) | `SecurityFilterChain` explícita com tudo desligado; teste de inventário exige 401 fora da allowlist; teste que confere a ausência da senha gerada no log |
| springdoc expõe `/v3/api-docs` e Swagger UI em produção | Só o artefato `-api`; `springdoc.api-docs.enabled=false` por padrão e `true` só no profile de dev; teste por profile |
| Actuator expõe endpoints sensíveis | `exposure.include=health`, porta de gerenciamento separada, fora do proxy |
| Diferença sutil no JSON (Jackson 3, enums, datas) quebra o frontend | O contrato do health é testado por corpo exato; o parse do frontend rejeita o inesperado e os 83 testes dele continuam iguais |
| Flyway rodando na partida por padrão | `spring.flyway.enabled=false` na aplicação; teste que sobe o contexto sem tabela de histórico do Flyway |
| `open-in-view` e lazy loading geram N+1 escondido | `spring.jpa.open-in-view=false`; leituras por projeção |
| Memória da JVM no Azure (etapa 6) | Medir com o container real antes da etapa 6; ajustar `-XX:MaxRAMPercentage` |
| Perda de contexto na mudança de pasta | Copiar as memórias e revisar o CLAUDE.md no mesmo passo |

## 8. Fora deste plano

- Worker e coletor (etapa 3): Playwright Java substitui o Playwright .NET quando a etapa começar.
- Azure (etapa 6): nada muda agora; o alvo continua sendo Container Apps.
- Frontend: sem mudança, salvo a porta da API (fase 3, item 2).

## 9. Próximo passo

Fase 0 num PR próprio, só de documentos: emenda ao prompt mestre, ADR 0009, ADR 0003 marcado Superseded e notas
nos ADRs 0001, 0005, 0007 e 0008. Depois, a mudança de pasta (D3) e a fase 1 na branch `feat/backend-java`.

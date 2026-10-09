# ADR 0009 — Migração do backend para Java 25 e Spring Boot 4.1.1

- **Status:** Aceito (2026-10-09), decidido pelo responsável do projeto. Substitui o [ADR 0003](0003-stack-e-politica-de-versoes.md)
  na parte de stack do backend; a política de versões do 0003 continua valendo.
- **Relacionados:** [planejamento da migração](../planejamento-migracao-java.md) (opções D1 a D9, mapa de
  equivalências, fases e riscos), [prompt mestre](../prompt-mestre.md) (emendado nesta data),
  [ADR 0008](0008-identidade-organizacoes-e-comprovacao-de-sites.md) (identidade).

## Contexto

A Foundation foi entregue em C# 14, ASP.NET Core 10 e EF Core 10, como o prompt mestre fixava. O responsável decidiu
trocar o backend por Java e Spring, para concentrar o portfólio nessa stack. Ele já mantém um projeto Java 25 +
Spring Boot 4.1.1 (`duora-api`) com os mesmos padrões de qualidade.

O momento é o mais barato possível: o backend tem cerca de 330 linhas de produção e 45 casos de teste, e a etapa 2 tem
decisões, modelo de ameaças e contrato prontos, mas nenhum código.

| Opção | Prós | Contras |
|---|---|---|
| **A. Migrar agora, antes do código da etapa 2** | Custo mínimo; uma stack só no portfólio; Spring Session JDBC fecha o R8 e Bucket4j no Postgres fecha o R7 do modelo de ameaças | Perde o ASP.NET Core Identity: cadastro, confirmação, redefinição e lockout viram código próprio; rodada sem feature nova; JVM mais pesada no Azure |
| B. Migrar depois da etapa 2 | Identity entregaria a etapa 2 mais rápido | Reescreveria conta, organizações e sites; o custo cresce a cada etapa |
| C. Ficar em .NET | Nada a refazer | Contraria a decisão de portfólio do responsável |

## Decisão

Migrar agora (opção A), com as escolhas do planejamento:

1. **Stack:** Java 25 (Temurin, LTS) e Spring Boot 4.1.1, a GA mais recente, com suporte open source até
   2027-07-31. A linha 4.0 foi descartada porque o suporte dela termina em 2026-12-31. Maven com wrapper (D4).
2. **Prompt mestre emendado** (D1): backend, testes (JUnit 6), worker (Playwright Java), estrutura de pastas e o
   livro 15, que passa a ser *Effective Java*, 3e.
3. **Mesmo repositório**, com a pasta local movida para `~/Documents/Projetos/Java/traceon` (D3).
4. **Estrutura** (D5): um módulo Maven, pacote por módulo de negócio, camadas como subpacotes, `package-private` como
   primeira barreira e ArchUnit + Spring Modulith (`ApplicationModules.verify()`) na CI.
5. **Identidade** (D6): Spring Security 7 + Spring Session JDBC + fluxos de conta próprios. Tokens de confirmação e
   de redefinição aleatórios, guardados com hash, com validade e uso único. As decisões de produto do ADR 0008
   continuam.
6. **Persistência** (D7): Spring Data JPA (Hibernate 7) para aggregates, `JdbcClient` para leituras; Flyway com SQL
   versionado, desligado na partida (`spring.flyway.enabled=false`) e aplicado por comando, como o ADR 0008 exige.
7. **Health** (D8): o JSON atual de `/health/live` e `/health/ready` não muda; o Actuator fica numa porta de
   gerenciamento não exposta.
8. **Corte** (D9): substituição completa na branch `feat/backend-java`, com cada teste da Foundation portado e visto
   falhar antes da implementação. O backend .NET só sai quando a paridade estiver provada na CI.

## Consequências

- A regra da dependência deixa de ser garantida pelo compilador (`<ProjectReference>`) e passa a ser garantida por
  `package-private` + fitness functions (ArchUnit e Spring Modulith).
- Os fluxos de conta são código nosso: mais testes de segurança na etapa 2 (T20–T23, T35 e T37 do modelo de ameaças),
  todos já listados.
- Rate limit e sessão passam a valer entre réplicas (Bucket4j e Spring Session no Postgres): R7 e R8 mudam de
  "aceito" para "mitigado" quando implementados.
- CI, Dependabot, README, CLAUDE.md e os ADRs 0001, 0002, 0004, 0005, 0006, 0007 e 0008 recebem revisão; os textos
  originais não são apagados.
- O PR #2 do Dependabot (NuGet) perde o sentido e pode ser fechado; o ecossistema `nuget` sai do `dependabot.yml`
  na fase 3.
- Upgrade de minor (4.1 → 4.2) segue a política do ADR 0003: só com 2 semanas de publicação e a suíte verde.
- Memória da JVM no Azure Container Apps precisa ser medida antes da etapa 6.

## Compliance

- `./mvnw verify` na CI com `-Xlint:all -Werror`, formatador em modo `check` e `dependency:analyze`.
- `ArchitectureTest` (ArchUnit) e `ModularityTest` (Spring Modulith) na suíte.
- Paridade da Foundation: os 45 casos atuais têm equivalente nomeado no planejamento (seção 6, fase 2), e o PR de
  corte só entra com todos verdes.
- Drift da spec (`docs/api/openapi.json`) e Spectral OWASP continuam bloqueando o merge.

## Referências

- Planejamento da migração, seções 3 a 7.
- Spring Boot, ciclos de suporte (endoflife.date) e Maven Central, consultados em 2026-10-09.
- *Effective Java*, 3e; *Spring Security in Action*, 2e; *Cloud Native Spring in Action*, pela base de conhecimento
  do responsável (`~/.claude/knowledge/java-spring.md`).

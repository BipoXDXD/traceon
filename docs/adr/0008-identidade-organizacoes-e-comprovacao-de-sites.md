# ADR 0008 — Identidade, organizações e comprovação de controle de sites

- **Status:** Aceito (2026-10-09). Decisões de produto preservadas; implementação .NET substituída pelo
  [ADR 0009](0009-migracao-do-backend-para-java-e-spring-boot.md), D6 e D7, em 2026-10-09.
- **Consolidação documental (2026-10-11):** a seção "Decisão vigente" rege a implementação em Java.
  O texto original em .NET está preservado ao final como histórico, sem efeito sobre a stack atual.
- **Relacionados:** [ADR 0001](0001-clean-architecture-monolito-modular.md) (um `DataSource`, schema por módulo),
  [ADR 0002](0002-catalogo-de-design-patterns.md) (Strategy só com a segunda variante),
  [ADR 0006](0006-integracao-frontend-proxy-e-configuracao.md) (mesma origem),
  [ADR 0009](0009-migracao-do-backend-para-java-e-spring-boot.md),
  [contrato da etapa 2](../api/contrato-etapa-2.md) (rascunho para revisão) e
  [modelo de ameaças](../security/threat-model.md#etapa-2-identity--sites).

## Contexto vigente

A etapa 2 (Identity & Sites) foi autorizada em 2026-10-09 e ainda não foi implementada. A migração da
Foundation para **Java 25 + Spring Boot 4.1.1** foi concluída nessa data (PRs #4, #5 e #6; registro no
PR #8). Esta consolidação remove a divergência entre a antiga decisão técnica e o contrato Java;
não aprova novos comportamentos nem retira o contrato do estado de rascunho.

## Decisão vigente

1. **Spring Security 7 + Spring Session JDBC + fluxos de conta próprios**, na stack Java 25 + Spring Boot
   4.1.1 (ADR 0009, D6). Controllers de conta usam `AuthenticationManager` e `DelegatingPasswordEncoder`
   com bcrypt; sem form login nem HTTP Basic. Cadastro, confirmação de e-mail, redefinição e lockout são
   fluxos nossos, apoiados nos mecanismos do framework. Tokens de confirmação e redefinição são aleatórios
   (128 bits), guardados somente com hash, com validade e uso único.
   - Sessão persistida no PostgreSQL; no navegador, somente o id opaco em cookie `HttpOnly`, `Secure`,
     `SameSite=Strict`, sem persistência além da sessão do navegador. Login cria uma sessão nova; logout
     apaga a sessão no banco; redefinição de senha invalida todas as sessões da conta.
   - `SecurityFilterChain` termina em `anyRequest().authenticated()`, com allowlist pública explícita
     conferida pelo `RouteInventoryIT`. Sem sessão: 401, sem redirecionamento.
   - CSRF por `csrf.spa()` em todo método não seguro, inclusive os endpoints anônimos de conta:
     `GET /api/csrf` entrega o cookie `XSRF-TOKEN`, legível pelo frontend, devolvido no header
     `X-XSRF-TOKEN`. O frontend relê o cookie após login e logout.
   - Login falho responde 401 genérico para conta inexistente, senha errada, e-mail não confirmado ou
     conta bloqueada. Recuperação e cadastro respondem 202 sem corpo, exista ou não a conta. Lockout e
     rate limit seguem o contrato e T20–T23; Bucket4j no PostgreSQL compartilha a contagem entre réplicas.
2. **Organização com `Membership(user, organization, role)`**, `role ∈ {Owner, Member}` com CHECK no banco. Um
   usuário pode pertencer a várias organizações. Convite por e-mail fica fora da primeira fatia.
3. **Persistência:** PostgreSQL, um `DataSource` compartilhado e schemas `identity` (usuários,
   organizações, membros) e `sites`, respeitando as fronteiras dos módulos (ADR 0001). Spring Data JPA
   (Hibernate 7) para aggregates e `JdbcClient` para leituras (ADR 0009, D7). PKs de domínio no tipo
   PostgreSQL `uuid`, geradas pelo servidor como **UUIDv7** e representadas por `java.util.UUID`.
   A biblioteca ou implementação do gerador será escolhida e verificada na implementação; não há gerador
   escolhido neste ADR. `UUID.randomUUID()` (v4) não cumpre esta decisão; `Guid.CreateVersion7()` é somente
   a API .NET histórica. UUID não substitui autorização nem é token secreto de conta ou de comprovação.
   **Flyway com SQL versionado**, aplicado por comando explícito após revisão; `spring.flyway.enabled=false`
   na aplicação. Hibernate não cria nem atualiza o schema (`spring.jpa.hibernate.ddl-auto=none`). A suíte
   de integração aplica as migrations num PostgreSQL vazio. As tabelas do Spring Session JDBC também
   devem ser versionadas pelo Flyway, sem inicialização automática na partida
   (`spring.session.jdbc.initialize-schema=never`, a configurar ao implementar a sessão).
4. **Primeiro método de comprovação: DNS TXT** (opção A). A Strategy de métodos só entra com o segundo método
   (ADR 0002).
5. **Dados pessoais:** só e-mail e hash de senha, removidos com a exclusão da conta. O STRIDE do cadastro e da
   comprovação está no [modelo de ameaças](../security/threat-model.md#etapa-2-identity--sites), ameaças 20 a 37.
6. **Escolhas complementares do responsável (2026-10-09):**
   - **Confirmação de e-mail obrigatória** antes do login. O cadastro responde sempre 202 com o mesmo corpo, o que
     fecha a enumeração de contas (a alternativa era 201 + 409 para e-mail existente, freada só por rate limit). O
     custo é uma porta de envio de e-mail, com adaptador falso em dev e testes.
   - **`Member` lê, cadastra e verifica sites; só `Owner` remove site e exclui a organização** (a alternativa era
     `Member` só leitura).
   - **O mesmo domínio pode existir em várias organizações** (agência e cliente), cada uma com token e comprovação
     próprios: `UNIQUE (organização, domínio)`. Um dono global bloquearia esse caso e abriria squatting antes da
     verificação.
   - **A comprovação não expira nesta etapa;** a etapa 3 revalida o TXT antes de cada coleta (risco R9 do modelo).
     Expiração por prazo exigiria um job agendado, que só existe com o worker.


Por quê: preserva as decisões de produto com pouca infraestrutura (sem Azure nem serviço de identidade novo),
mantém sessão na mesma origem e adia HTTP a URLs de usuário para a etapa 3, junto do coletor isolado.

## Consequências vigentes

- Confirmação, redefinição e lockout exigem implementação e testes próprios; Spring Security não é um
  substituto completo do ASP.NET Core Identity. Envio de e-mail usa adaptador falso em dev e testes;
  o real depende de decisão própria.
- Em produção, o reverse proxy precisa servir frontend e API na mesma origem (etapa 6).
- DNS consulta somente o TXT do nome de desafio, por resolvedor configurado, com timeout e rate limit;
  nenhuma requisição HTTP à URL do usuário nesta etapa.
- Uma futura adoção de OIDC exige nova decisão e migração de contas e sessões.
- R7 e R8 estão tratados pelo desenho, não mitigados por código ainda; os testes da etapa 2 continuam
  planejados no modelo de ameaças.

## Compliance vigente (ao implementar)

- `RouteInventoryIT`: rota fora da allowlist retorna 401 sem sessão; sem redirecionamento.
- Testes de sessão (T23): cookie seguro, sessão nova no login, logout e reset invalidam sessões no banco.
- CSRF (T24): método não seguro sem token é rejeitado e não altera o banco.
- Isolamento (T26): recurso alheio retorna 404, sem dado alheio e sem alteração; papel insuficiente na
  própria organização retorna 403 (T27).
- Enumeração e confirmação (T21–T22): cadastro e recuperação retornam o mesmo 202; falhas de login têm
  o mesmo 401; conta não confirmada não entra; tokens expiram e só podem ser usados uma vez.
- PostgreSQL real vazio recebe as migrations Flyway, inclusive as de sessão; a aplicação não cria schema
  na partida. PKs geradas têm versão 7; nenhum id de criação vem do cliente.
- Endpoints, DTOs, status e testes de contrato seguem o [contrato da etapa 2](../api/contrato-etapa-2.md),
  ainda em rascunho; este ajuste documental não implementa a etapa.

## Referências vigentes

- ADR 0009, decisões D6 e D7; planejamento da migração, mesmas decisões.
- Contrato da etapa 2, convenções de autenticação, CSRF, ids e persistência.
- Modelo de ameaças, T20–T37 e riscos R7–R10.

## Histórico — decisão original em .NET (2026-10-09, substituída tecnicamente)

O registro abaixo preserva contexto, alternativas, decisão e consequências originais. APIs .NET,
EF migrations, nomes de testes e referências desta seção são históricos; para implementar, use as
seções vigentes acima. As decisões de produto foram mantidas.

### Contexto

A etapa 2 (Identity & Sites) exige identidade com mecanismo consolidado, autorização por organização e recurso,
isolamento testado e comprovação de controle do site antes de qualquer coleta. É também a primeira migration do
projeto. Cada escolha abaixo é difícil de desfazer depois que houver usuários e dados.

**Identidade e sessão**

| Opção | Prós | Contras |
|---|---|---|
| **A. ASP.NET Core Identity com cookie** | Mecanismo do framework (não é autenticação improvisada); cookie `HttpOnly`/`Secure`/`SameSite` na mesma origem graças ao proxy do Vite; passkeys nativas no .NET 10; testável com Postgres real; sem custo nem Azure | Nós operamos hash de senha, lockout, reset e confirmação de e-mail (exige um envio de e-mail, falso em dev); MFA é nossa responsabilidade |
| **B. Microsoft Entra External ID (OIDC)** | Gerenciado, grátis até 50 mil MAU, MFA e reset prontos | Exige criar tenant no Azure (proibido neste projeto sem autorização); IdP falso nos testes; dev depende de internet; vínculo com o fornecedor |
| **C. Keycloak self-hosted** | OIDC padrão, sem fornecedor | Mais um serviço para operar e hospedar, sem problema medido que o justifique |

Token no navegador foi descartado: a documentação do ASP.NET Core recomenda cookie para apps de browser, porque o
navegador o guarda fora do alcance do JavaScript.

**Organizações e papéis:** papéis fixos `Owner` e `Member` × permissões granulares (Speculative Generality sem um
terceiro papel pedido).

**Migrations:** aplicadas por comando explícito × `Database.Migrate()` na partida (arriscado com várias réplicas e
aplica SQL sem revisão).

**Comprovação de controle do site**

| Opção | Prós | Contras |
|---|---|---|
| **A. Registro DNS TXT** | Prova controle do domínio; nenhuma requisição HTTP à URL do usuário, então o SSRF continua fora da etapa 2 | Exige pacote de DNS (o .NET não consulta TXT); propagação lenta; menos amigável para quem não é técnico |
| **B. Arquivo `.well-known`** | Familiar e rápido para o usuário | Requisição HTTP a URL do usuário: exige já agora os controles de SSRF (DNS, redirecionamentos, IP interno) |
| **C. Meta tag** | Idem B | Idem B, mais parse de HTML não confiável |

### Decisão

1. **ASP.NET Core Identity com cookie de sessão** (opção A). Cookie `HttpOnly`, `Secure`, `SameSite=Strict`;
   antiforgery em toda mutação; *fallback policy* que exige usuário autenticado, com a allowlist pública explícita no
   `RouteInventoryTests`. Login e recuperação respondem igual para conta existente e inexistente; lockout e rate limit
   no login.
2. **Organização com `Membership(user, organization, role)`**, `role ∈ {Owner, Member}` com CHECK no banco. Um
   usuário pode pertencer a várias organizações. Convite por e-mail fica fora da primeira fatia.
3. **Persistência:** o mesmo `TraceonDbContext`, com schema `identity` (usuários, organizações, membros) e `sites`.
   PK `uuid` gerada na aplicação com `Guid.CreateVersion7()`. Migrations EF versionadas e aplicadas por comando
   explícito (script ou bundle revisado); a aplicação nunca chama `Migrate()` na partida. Os testes aplicam as
   migrations no fixture.
4. **Primeiro método de comprovação: DNS TXT** (opção A). A Strategy de métodos só entra com o segundo método
   (ADR 0002).
5. **Dados pessoais:** só e-mail e hash de senha, removidos com a exclusão da conta. O STRIDE do cadastro e da
   comprovação está no [modelo de ameaças](../security/threat-model.md#etapa-2-identity--sites), ameaças 20 a 37.
6. **Escolhas complementares do responsável (2026-10-09):**
   - **Confirmação de e-mail obrigatória** antes do login. O cadastro responde sempre 202 com o mesmo corpo, o que
     fecha a enumeração de contas (a alternativa era 201 + 409 para e-mail existente, freada só por rate limit). O
     custo é uma porta de envio de e-mail, com adaptador falso em dev e testes.
   - **`Member` lê, cadastra e verifica sites; só `Owner` remove site e exclui a organização** (a alternativa era
     `Member` só leitura).
   - **O mesmo domínio pode existir em várias organizações** (agência e cliente), cada uma com token e comprovação
     próprios: `UNIQUE (organização, domínio)`. Um dono global bloquearia esse caso e abriria squatting antes da
     verificação.
   - **A comprovação não expira nesta etapa;** a etapa 3 revalida o TXT antes de cada coleta (risco R9 do modelo).
     Expiração por prazo exigiria um job agendado, que só existe com o worker.

Por quê: é o caminho com menos infraestrutura (nada de Azure nem serviço novo), com a sessão mais segura para o
frontend atual, e que adia toda requisição a URL de usuário para a etapa 3, junto do coletor isolado.

### Consequências

- Nós carregamos a operação de senha: política pela NIST, lockout, reset por token de uso único e envio de e-mail
  (adaptador falso em dev e testes, real só com decisão própria).
- O cookie depende da mesma origem: em produção o reverse proxy precisa servir frontend e API juntos (etapa 6).
- A consulta DNS usa um resolvedor configurado e só lê o TXT do nome de desafio; mesmo assim tem limite de taxa e
  timeout.
- Trocar para OIDC depois é possível (login externo no Identity), mas migra contas e sessões.

### Compliance

- `RouteInventoryTests`: toda rota fora da allowlist responde 401 sem credencial.
- Testes de isolamento: usuário de outra organização recebe 404, sem dado alheio no corpo e com o recurso intacto.
- Teste de antiforgery: POST sem token → 400/403, banco inalterado.
- Teste de enumeração: login e recuperação com e-mail existente × inexistente → mesmo status e corpo.
- Teste de migration: a suíte de integração parte de um banco vazio e aplica todas as migrations.

### Referências

- ASP.NET Core Identity para SPAs e passkeys, Microsoft Learn (versão 10.0).
- Microsoft Entra External ID, preços e faixa gratuita, Microsoft Learn.

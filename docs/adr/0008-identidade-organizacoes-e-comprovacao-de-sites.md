# ADR 0008 — Identidade, organizações e comprovação de controle de sites

- **Status:** Aceito (2026-10-09), escolhido pelo responsável do projeto entre as opções abaixo.
- **Relacionados:** [ADR 0001](0001-clean-architecture-monolito-modular.md) (um `DbContext`, schema por módulo),
  [ADR 0002](0002-catalogo-de-design-patterns.md) (Strategy só com a segunda variante),
  [ADR 0006](0006-integracao-frontend-proxy-e-configuracao.md) (proxy do Vite, sem CORS),
  [modelo de ameaças](../security/threat-model.md).

## Contexto

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

## Decisão

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

## Consequências

- Nós carregamos a operação de senha: política pela NIST, lockout, reset por token de uso único e envio de e-mail
  (adaptador falso em dev e testes, real só com decisão própria).
- O cookie depende da mesma origem: em produção o reverse proxy precisa servir frontend e API juntos (etapa 6).
- A consulta DNS usa um resolvedor configurado e só lê o TXT do nome de desafio; mesmo assim tem limite de taxa e
  timeout.
- Trocar para OIDC depois é possível (login externo no Identity), mas migra contas e sessões.

## Compliance

- `RouteInventoryTests`: toda rota fora da allowlist responde 401 sem credencial.
- Testes de isolamento: usuário de outra organização recebe 404, sem dado alheio no corpo e com o recurso intacto.
- Teste de antiforgery: POST sem token → 400/403, banco inalterado.
- Teste de enumeração: login e recuperação com e-mail existente × inexistente → mesmo status e corpo.
- Teste de migration: a suíte de integração parte de um banco vazio e aplica todas as migrations.

## Referências

- ASP.NET Core Identity para SPAs e passkeys, Microsoft Learn (versão 10.0).
- Microsoft Entra External ID, preços e faixa gratuita, Microsoft Learn.

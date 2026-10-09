# Progresso

Última atualização: 2026-10-09. Roteiro: `docs/prompt-mestre.md`, seção 4. Cada etapa só começa com
autorização explícita; o roteiro não autoriza implementar etapas futuras.

## Etapas

| Etapa | Status | Observações |
|---|---|---|
| 1. Foundation | Concluída em 2026-10-09, com pendências | API, PostgreSQL local, frontend integrado, testes e CI configurada. Pendentes: execução real da CI, teste de exceção 500 e validação do Dependabot (veja [acceptance.md](acceptance.md)). Revisão das ADRs pelo responsável também pendente |
| 2. Identity & Sites | Não iniciada | Próximo incremento sugerido (abaixo) |
| 3. Monitoring | Não iniciada | Depende da etapa 2 (sites com controle comprovado) |
| 4. Integrity | Não iniciada | Depende da etapa 3 |
| 5. Findings & Notifications | Não iniciada | Depende da etapa 4 |
| 6. Cloud | Não iniciada | Nada provisionado no Azure; exige confirmar saldo, região autorizada, cotas e preços antes |

## O que a Foundation entregou

- Solução .NET com `Domain`, `Application`, `Infrastructure` e `Api` (os dois primeiros vazios de propósito) e dois
  projetos de teste; regra da dependência verificada por testes.
- `/health/live` e `/health/ready` com contrato fixo, sonda de banco com timeout, Problem Details e OpenAPI só em
  Development.
- PostgreSQL 18 no Docker Compose (loopback), configuração por User Secrets/variável de ambiente com falha na partida.
- Frontend React com o painel "Estado do sistema" (carregando, operacional, banco indisponível, API sem resposta,
  resposta inesperada), acessível por teclado, validado em 1200 px e 400 px.
- CI (backend, frontend, gitleaks) e Dependabot configurados, **ainda não executados**.
- ADRs 0001 a 0006.

## Próximo incremento sugerido: etapa 2, Identity & Sites

Escopo (do roteiro): identidade, organizações, permissões, cadastro de sites e comprovação de controle, com
isolamento testado. Fica para o responsável aprovar o escopo antes de começar. Não está implementado nada disso.

### Decisões que exigirão o responsável

| Decisão | Opções a comparar (a apresentar com prós e contras) | Por que importa |
|---|---|---|
| Provedor de identidade | Serviço gerenciado/OIDC (candidatos a levantar na documentação oficial) × ASP.NET Core Identity próprio | O prompt exige mecanismos consolidados e proíbe autenticação improvisada; custo, vínculo ao Azure e esforço de operação diferem muito |
| Modelo de sessão do frontend | Cookie de sessão com backend como client OAuth (BFF) × token no navegador | Define CSRF, armazenamento de credencial e o que o proxy do Vite precisa encaminhar |
| Modelo de organização e papéis | Papéis mínimos (dono, membro) × permissões granulares | Base da autorização por organização e recurso e dos testes de isolamento |
| Esquema do banco por módulo | Um schema por módulo no mesmo `DbContext` (ADR 0001) e política de migrations | Primeira migration da história do projeto; difícil de desfazer |
| Comprovação de controle do site | Meta tag, arquivo `.well-known`, registro DNS TXT: qual o primeiro método | O prompt exige autorização do site antes de qualquer coleta; a Strategy só se justifica com o segundo método |
| Modelo de ameaças e dados pessoais | STRIDE do cadastro e da comprovação; quais dados pessoais são guardados e por quanto tempo | Feature com permissão e URL fornecida por usuário (risco de SSRF já na validação de URL) |
| Validação da CI | Publicar o repositório (privado ou público) para executar a CI e o Dependabot | Pendência herdada da Foundation; publicar exige autorização do responsável |

### Primeiro passo proposto (tracer bullet)

Antes de qualquer tela, definir o contrato OpenAPI e os testes de isolamento entre organizações (usuário de uma
organização recebe 404 ao consultar recurso de outra, sem dado alheio no corpo), depois uma fatia fina ponta a
ponta: criar organização → cadastrar site em estado "não verificado". Entidades ricas e Value Objects entram
aqui, conforme o ADR 0002.

## Dívidas e riscos em aberto

- CI e Dependabot nunca executados (sem remote).
- Teste de exceção 500 sem stack trace pendente.
- Pasta `infrastructure/` ainda inexistente; prevista no prompt mestre e sem conteúdo até haver infraestrutura como código.

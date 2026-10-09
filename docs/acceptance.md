# Critérios de aceite da Foundation

Fonte: `docs/prompt-mestre.md`, seções 4 (escopo da Foundation) e 6. Verificações em 2026-10-09, na máquina de
desenvolvimento (macOS Apple Silicon, .NET SDK 10.0.400, Node 24, Docker Desktop). Regra do prompt: verificação
não executada fica **Pendente**, explicitamente.

Legenda: **Atendido** = executado e observado; **Pendente** = não executado ou não coberto.

## Critério de aceite geral

| Critério | Status | Evidência |
|---|---|---|
| Backend compila | Atendido | `dotnet build backend/Traceon.slnx`: 0 avisos, 0 erros (avisos são erros por `TreatWarningsAsErrors`) |
| Formatação do backend | Atendido | `dotnet format backend/Traceon.slnx --verify-no-changes`: sem alterações |
| Frontend compila | Atendido | `npm run build` (`tsc -b` + `vite build`) passou |
| TypeScript é verificado | Atendido | `npm run typecheck` passou; o build também roda `tsc -b`; ESLint strict type-checked com zero avisos (`npm run lint`) |
| Testes executados passam | Atendido | Backend: `dotnet test --solution backend/Traceon.slnx`, 25 testes (8 unidade/arquitetura, 17 integração). Frontend: `npm test`, 83 testes |
| Conectividade real funciona | Atendido | Ponta a ponta: `docker compose up` + API + Vite; navegador mostrou "API e banco respondendo" |
| Falhas previstas são representadas corretamente | Atendido | Banco parado: "A API responde, mas o banco de dados está indisponível" (`ready` 503 via proxy). API parada: "A API não está respondendo" (proxy 502), banco "Desconhecido". Cobertos também por testes de integração (recusa e silêncio do banco) |
| O README permite reproduzir o resultado | Atendido | Fluxo equivalente executado ponta a ponta (`compose up`, API com `dotnet run --launch-profile http`, `npm run dev`) e comandos do README conferidos contra os reais. Limitação: não seguido do zero por uma segunda pessoa |

## Escopo executável (itens 1 a 6)

| # | Critério | Status | Evidência |
|---|---|---|---|
| 1 | Solução, frontend e configurações criados, preservando o que existia | Atendido | `backend/Traceon.slnx` (4 projetos + 2 de teste), `frontend/`, `global.json`, `.editorconfig`, `.gitignore`. Estrutura conforme a seção 2 do prompt (sem a pasta `infrastructure/`, que não tem conteúdo ainda) |
| 2 | PostgreSQL local, EF Core/Npgsql, variáveis externas, `.env.example` sem segredos | Atendido | `compose.yaml` (`postgres:18.6-alpine3.24`, porta publicada só em `127.0.0.1`, senha obrigatória sem padrão), `.env.example` com valor de exemplo, `TraceonDbContext` registrado. Sem entidades nem migrations (proibido pelo prompt) |
| 2a | Connection string obrigatória e sem vazamento | Atendido | `StartupConfigurationTests`: ausente, vazia ou só espaços derruba o app; malformada derruba sem repetir o valor (canário) |
| 3 | `/health/live` independe do banco | Atendido | `Live_returns_healthy_when_database_is_unreachable` (banco recusando e banco mudo) → 200 |
| 3 | `/health/ready` verifica conectividade real e diferencia indisponibilidade | Atendido | `Ready_returns_healthy_database_check_when_database_is_available` → 200; `Ready_returns_503_unhealthy_promptly_when_database_is_unreachable` → 503 dentro de 10 s; o caso de banco mudo mediu 3,07 s depois da correção (15 s antes dela) |
| 3 | Nenhum diagnóstico sensível exposto | Atendido | Corpo exato conferido; canário da senha, porta, `Host=`, `Exception`, `Npgsql` e ` at ` ausentes; `Cache-Control: no-store` |
| 3 | OpenAPI apenas em desenvolvimento | Atendido | `OpenApi_document_is_exposed_only_in_development`: 200 em Development, 404 em Production |
| 3 | Tratamento consistente de erros (Problem Details) | Atendido | `Unknown_route_returns_problem_details_without_internal_details`: 404 `application/problem+json` sem detalhe interno |
| 3a | Exceção não tratada (500) responde Problem Details sem stack trace | **Pendente** | Pipeline configurado (`AddProblemDetails` + `UseExceptionHandler`), mas não existe endpoint que lance exceção e não há teste dedicado. Será coberto quando houver endpoint de negócio ou um teste com endpoint de teste isolado |
| 4 | Tela inicial consulta o estado real da API por proxy do Vite, sem CORS aberto | Atendido | `frontend/vite.config.ts` encaminha só `/health`; a API não configura CORS; validado no navegador em 1200 px e 400 px |
| 4 | Sem autenticação improvisada nem endpoints de negócio | Atendido | Únicos endpoints: `/health/live`, `/health/ready` e, em Development, `/openapi/v1.json` |
| 5 | Build, análise estática e testes configurados na CI | Atendido (configuração) | `.github/workflows/ci.yml` com jobs backend, frontend e secrets (gitleaks). Ver pendências abaixo sobre a execução |
| 5 | Testes de integração HTTP com PostgreSQL real | Atendido | Testcontainers `postgres:18.6-alpine3.24`, mesma imagem do Compose; liveness com banco indisponível e readiness com banco disponível e indisponível |
| 5 | Sem testes triviais para preencher o projeto de unit tests | Atendido | `Traceon.UnitTests` contém só os 8 testes de arquitetura (regra da dependência); `Domain` e `Application` seguem sem código e sem teste |
| 6 | Interface validada no navegador | Atendido | Playwright em 1200 px e 400 px: estados operacional, banco indisponível e API parada; navegação por teclado (Tab + Enter no botão) e foco visível; console sem erros além do 503/502 esperados |
| 6 | Inicialização, testes, variáveis e solução de problemas documentados | Atendido | `README.md`, `frontend/README.md` |
| 6 | Decisões relevantes em ADRs curtos | Atendido | ADRs 0001 a 0006 |

## Verificações pendentes (não executadas)

| Item | Status | Motivo e próximo passo |
|---|---|---|
| Execução real da CI no GitHub | **Pendente** | O repositório não tem remote no GitHub; o workflow nunca rodou. Os mesmos comandos passam localmente, mas isso não prova o comportamento do runner (Docker para o Testcontainers, cache do NuGet, checksum do gitleaks). Validar no primeiro push autorizado |
| Teste de exceção 500 sem stack trace | **Pendente** | Ver item 3a |
| Validação do Dependabot | **Pendente** | `.github/dependabot.yml` escrito, mas não validado pelo GitHub; confirmar após publicar o repositório. Não há `cooldown`: a idade mínima de 2 semanas das dependências é aplicada à mão ([ADR 0003](adr/0003-stack-e-politica-de-versoes.md)) |
| Revisão das ADRs pelo responsável | **Pendente** | ADRs 0001 a 0006 estão "Aceito" com revisão pendente do responsável do projeto |
| Cobertura de código e mutation testing | Não configurados | Nenhuma ferramenta nem limiar definidos na CI |

## Limitações conhecidas

- O primeiro `docker compose up` baixa a imagem do PostgreSQL; sem rede, ele falha.
- O PostgreSQL nativo na porta 5432 conflita com o Compose (ver solução de problemas no README).
- A Foundation não prova nada sobre segurança da coleta, isolamento entre organizações ou carga: nada disso existe ainda.
- Não há afirmação de prontidão para produção.

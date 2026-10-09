# Traceon

SaaS de monitoramento de integridade de aplicações web, pensado para agências e e-commerces. A visão do
produto: cadastrar sites autorizados, executar verificações, comparar evidências com referências aprovadas e
investigar alterações com histórico e responsáveis.

**Status: etapa 1 (Foundation) implementada. O produto em si ainda não existe.** Hoje há uma API com health
checks, um PostgreSQL local, uma tela que mostra o estado real da API e do banco, testes e CI configurada.
Não há autenticação, sites, verificações nem endpoints de negócio. Detalhes em [docs/progress.md](docs/progress.md).

## Limites do produto

- Alteração observada **não significa** invasão confirmada.
- Falha de coleta **não significa** ausência de problemas.
- A primeira coleta **não é** automaticamente confiável.
- O Traceon não promete proteção integral, conformidade automática nem bloqueio de ataques. A primeira versão
  será de observação e investigação.

## Stack (versões fixadas)

| Área | Tecnologia | Versão |
|---|---|---|
| Backend | .NET SDK / C# | 10.0.400 (`global.json`, `rollForward: latestPatch`) / C# 14 |
| | ASP.NET Core, EF Core | 10.0.12 |
| | Npgsql.EntityFrameworkCore.PostgreSQL | 10.0.3 |
| Banco | PostgreSQL (imagem Docker) | `postgres:18.6-alpine3.24` |
| Frontend | Node / npm | 24 (`frontend/.nvmrc`) / 11 |
| | React, Vite, Tailwind CSS, TypeScript | 19.3.0, 8.3.1, 4.3.3, 6.0.3 |
| Testes backend | xunit.v3 (Microsoft.Testing.Platform), Testcontainers.PostgreSql | 4.0.1, 4.15.0 |
| Testes frontend | Vitest, Testing Library, jsdom | 5.0.1, 16.3.3, 30.1.1 |
| CI | GitHub Actions (`ubuntu-24.04`), gitleaks | 8.30.1 |
| Contrato | OpenAPI gerado no build (`Microsoft.Extensions.ApiDescription.Server`); Spectral + ruleset OWASP | 10.0.12; 6.16.3 + 2.0.1 |

Política de versões e motivos: [ADR 0003](docs/adr/0003-stack-e-politica-de-versoes.md). Azure é etapa
posterior e **nada é provisionado** nesta fase.

## Estrutura

```text
.
├── backend/
│   ├── src/Traceon.{Domain,Application,Infrastructure,Api}/
│   ├── tests/Traceon.{UnitTests,IntegrationTests}/
│   ├── Directory.Build.props        # TFM, analyzers, TreatWarningsAsErrors
│   ├── Directory.Packages.props     # versões centralizadas dos pacotes NuGet
│   └── Traceon.slnx
├── frontend/                        # React + TypeScript + Vite (veja frontend/README.md)
├── docs/                            # arquitetura, aceite, progresso, referências, ADRs e modelo de ameaças
│   └── api/openapi.json             # spec OpenAPI gerada pelo build e versionada
├── .spectral.yaml                   # lint da spec (Spectral + OWASP)
├── .github/                         # workflow de CI e Dependabot
├── compose.yaml                     # PostgreSQL local
├── .env.example                     # variáveis do Docker Compose
├── global.json                      # SDK .NET fixado e runner de testes
└── CLAUDE.md                        # instruções para o assistente de código
```

`Domain` e `Application` estão vazios de propósito: a Foundation não tem regra de negócio
([ADR 0001](docs/adr/0001-clean-architecture-monolito-modular.md)). Visão completa em
[docs/architecture.md](docs/architecture.md).

## Pré-requisitos

- .NET SDK **10.0.400** (o `global.json` rejeita versões de feature band diferentes).
- Node **24** e npm 11.
- Docker Desktop em execução (PostgreSQL local e testes de integração).

## Executar localmente

Na raiz do repositório:

```bash
# 1. Variáveis do Docker Compose. Edite POSTGRES_PASSWORD no .env (o compose recusa subir sem ela).
cp .env.example .env

# 2. PostgreSQL (publicado só em 127.0.0.1)
docker compose up -d --wait

# 3. Connection string da API em User Secrets (use a mesma senha e porta do .env)
dotnet user-secrets set "ConnectionStrings:Traceon" \
  "Host=localhost;Port=5432;Database=traceon;Username=traceon;Password=<POSTGRES_PASSWORD>" \
  --project backend/src/Traceon.Api

# 4. API em http://localhost:5120 (terminal 1)
dotnet run --project backend/src/Traceon.Api --launch-profile http

# 5. Frontend em http://localhost:5173 (terminal 2)
cd frontend && npm ci && npm run dev
```

Abra http://localhost:5173: o painel "Estado do sistema" deve mostrar "API e banco de dados respondendo".
Para ver os outros estados, rode `docker compose stop postgres` (a API responde, banco indisponível) ou
pare a API (API não respondendo) e clique em "Verificar novamente".

A API **não sobe** sem `ConnectionStrings:Traceon`; a mensagem de erro nomeia a chave, nunca o valor.

## Endpoints de health

Os únicos endpoints desta etapa. Sem autenticação, para que um orquestrador possa consultá-los.

| Endpoint | Verifica | Resposta |
|---|---|---|
| `GET /health/live` | Só que o processo atende requisições; **não** consulta o banco | `200` `{"status":"Healthy"}` |
| `GET /health/ready` | Abre uma conexão real com o PostgreSQL (timeout de 3 s) | `200` `{"status":"Healthy","checks":[{"name":"database","status":"Healthy"}]}` ou `503` `{"status":"Unhealthy","checks":[{"name":"database","status":"Unhealthy"}]}` |

- Os corpos só trazem status e o nome do check; nunca mensagem, exceção, duração, host ou porta.
- As duas respostas são `application/json` com `Cache-Control: no-store`.
- Só `GET`: `POST`, `PUT` e `DELETE` respondem `405` em `application/problem+json` com `Allow: GET`.
- Rota inexistente responde `404` em `application/problem+json`, sem detalhe interno. Exceção não tratada responde
  `500` genérico, com `traceId` (o mesmo id vai para o log) e sem stack trace.
- Toda resposta, inclusive erro, leva `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`,
  `X-Frame-Options: DENY` e `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`.
- O documento OpenAPI servido em `/openapi/v1.json` só existe em `Development`.

Racional: [ADR 0004](docs/adr/0004-health-checks-liveness-e-readiness.md) e
[ADR 0007](docs/adr/0007-convencoes-de-api-e-seguranca-da-foundation.md).

## OpenAPI

A spec da API fica em [`docs/api/openapi.json`](docs/api/openapi.json). O build da API (`dotnet build`) a **regrava**
a partir do código; ela é versionada, então **quem muda o contrato commita a spec junto**. A CI falha se a spec
commitada divergir da gerada. O build não precisa de banco nem de connection string.

Lint local, com o mesmo comando da CI (Node 24; baixa as duas versões exatas via `npx`):

```bash
npx --yes \
  -p "@stoplight/spectral-cli@6.16.3" \
  -p "@stoplight/spectral-owasp-ruleset@2.0.1" \
  -c 'NODE_PATH="${PATH%%/.bin:*}" spectral lint docs/api/openapi.json --fail-severity=warn'
```

As regras vêm de [`.spectral.yaml`](.spectral.yaml); cada regra desligada tem o motivo e a etapa em que volta. As
versões do Spectral ficam no workflow e **não** são atualizadas pelo Dependabot.

## Variáveis de ambiente e configuração

| Nome | Onde | Padrão | Uso |
|---|---|---|---|
| `POSTGRES_PASSWORD` | `.env` (Compose) | nenhum, **obrigatória** | Senha do usuário do PostgreSQL local |
| `POSTGRES_DB` | `.env` | `traceon` | Nome do banco |
| `POSTGRES_USER` | `.env` | `traceon` | Usuário do banco |
| `POSTGRES_PORT` | `.env` | `5432` | Porta no loopback do host |
| `ConnectionStrings:Traceon` | User Secrets (dev) | nenhum, **obrigatória** | Connection string Npgsql da API |
| `ConnectionStrings__Traceon` | Variável de ambiente | nenhum | Equivalente ao anterior fora do desenvolvimento |
| `TRACEON_API_URL` | Ambiente ou `frontend/.env` | `http://localhost:5120` | Destino do proxy do Vite; lida só pelo `vite.config.ts`, não entra no bundle |

Segredos ficam fora do Git (`.env` e User Secrets). Nada de credencial no código, nos logs ou no bundle do
frontend ([ADR 0006](docs/adr/0006-integracao-frontend-proxy-e-configuracao.md)).

## Testes, lint e formatação

```bash
# Backend (44 testes: 8 de unidade/arquitetura, 36 de integração com PostgreSQL real via Testcontainers)
dotnet build backend/Traceon.slnx
dotnet test --solution backend/Traceon.slnx
dotnet format backend/Traceon.slnx --verify-no-changes

# Frontend (83 testes)
cd frontend
npm run lint
npm run typecheck
npm test
npm run build
```

Os testes de integração sobem um container `postgres:18.6-alpine3.24` e exigem o Docker em execução; eles
usam o ambiente `Testing` e não leem User Secrets. Estratégia: [ADR 0005](docs/adr/0005-estrategia-de-testes.md).
Os critérios de aceite e o que ainda está pendente estão em [docs/acceptance.md](docs/acceptance.md).

## CI

`.github/workflows/ci.yml` define quatro jobs: **backend** (restore, `dotnet format --verify-no-changes`, build
Release, verificação de drift da spec OpenAPI, testes), **frontend** (lint, typecheck, testes, build), **api-spec**
(Spectral + ruleset OWASP sobre `docs/api/openapi.json`) e **secrets** (gitleaks 8.30.1 com checksum
sobre o histórico completo). As actions estão fixadas por SHA. O Dependabot (`.github/dependabot.yml`) cobre
NuGet, npm, GitHub Actions e imagens do Compose, sem atualizar versões principais.

**A CI nunca foi executada**: o repositório ainda não tem remote no GitHub, então os workflows e o Dependabot
estão configurados, mas não validados.

## Solução de problemas

| Sintoma | Causa provável e ação |
|---|---|
| `docker compose up` falha pedindo `POSTGRES_PASSWORD` | Falta o `.env`; rode `cp .env.example .env` e defina a senha |
| Porta 5432 ocupada (ex.: PostgreSQL nativo) | Troque `POSTGRES_PORT` no `.env` (ex.: `5433`) e use a mesma porta na connection string |
| A API aborta ao iniciar com `ConnectionStrings:Traceon is required` | Configure o User Secret (passo 3) ou `ConnectionStrings__Traceon` |
| Testes de integração falham ao iniciar o container | Docker Desktop precisa estar rodando |
| Painel mostra "A API não está respondendo" | API parada ou `TRACEON_API_URL` incorreta; teste `curl -s localhost:5120/health/live` |
| Painel mostra "banco de dados está indisponível" | PostgreSQL parado ou senha/porta divergentes; `docker compose up -d` e confira a connection string |
| Quero recomeçar com o banco vazio | `docker compose down -v` apaga o volume `postgres-data` (todos os dados locais) |
| Volume do PostgreSQL 18 | A imagem grava em `/var/lib/postgresql`; o `compose.yaml` já monta o volume nesse caminho |
| `npm ci` avisa sobre install script do `fsevents` (npm 11) | Aviso conhecido, não bloqueia a instalação |

## Documentação

- [docs/architecture.md](docs/architecture.md): camadas, módulos, fluxo de health e decisões de configuração
- [docs/acceptance.md](docs/acceptance.md): critérios de aceite da Foundation com evidências e pendências
- [docs/progress.md](docs/progress.md): etapas e próximo incremento
- [docs/security/threat-model.md](docs/security/threat-model.md): modelo de ameaças (ameaça, mitigação, teste)
- [docs/api/openapi.json](docs/api/openapi.json): spec OpenAPI gerada pelo build
- [docs/references.md](docs/references.md): bibliografia (20 livros) e seu papel
- [docs/prompt-mestre.md](docs/prompt-mestre.md): escopo e regras do projeto
- ADRs: [0001](docs/adr/0001-clean-architecture-monolito-modular.md) arquitetura,
  [0002](docs/adr/0002-catalogo-de-design-patterns.md) padrões,
  [0003](docs/adr/0003-stack-e-politica-de-versoes.md) stack e versões,
  [0004](docs/adr/0004-health-checks-liveness-e-readiness.md) health checks,
  [0005](docs/adr/0005-estrategia-de-testes.md) testes,
  [0006](docs/adr/0006-integracao-frontend-proxy-e-configuracao.md) frontend e configuração,
  [0007](docs/adr/0007-convencoes-de-api-e-seguranca-da-foundation.md) convenções de API e segurança

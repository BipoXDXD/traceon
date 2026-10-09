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
| Backend | Java (Temurin) / Maven (wrapper) | 25 (LTS) / 3.9.16 |
| | Spring Boot (Spring MVC, Spring Data JPA, Hibernate) | 4.1.1 (Framework 7.0.9, Hibernate 7.4.5) |
| | Flyway, driver PostgreSQL | 12.4.0, 42.7.13 |
| Banco | PostgreSQL (imagem Docker) | `postgres:18.6-alpine3.24` |
| Frontend | Node / npm | 24 (`frontend/.nvmrc`) / 11 |
| | React, Vite, Tailwind CSS, TypeScript | 19.3.0, 8.3.1, 4.3.3, 6.0.3 |
| Testes backend | JUnit, Testcontainers, ArchUnit, Spring Modulith | 6.0.3, 2.0.5, 1.5.1, 2.1.1 |
| Testes frontend | Vitest, Testing Library, jsdom | 5.0.1, 16.3.3, 30.1.1 |
| CI | GitHub Actions (`ubuntu-24.04`), gitleaks | 8.30.1 |
| Contrato | OpenAPI gerado pela API (springdoc); Spectral + ruleset OWASP | 3.1.1; 6.16.3 + 2.0.1 |

Política de versões: [ADR 0003](docs/adr/0003-stack-e-politica-de-versoes.md); stack do backend:
[ADR 0009](docs/adr/0009-migracao-do-backend-para-java-e-spring-boot.md), que trocou o .NET da Foundation por Java. Azure é etapa
posterior e **nada é provisionado** nesta fase.

## Estrutura

```text
.
├── backend/                         # um módulo Maven
│   ├── src/main/java/bipo/tech/traceon/{health,shared}/
│   ├── src/main/resources/          # application.properties e profiles
│   ├── src/test/java/               # *Test (rápidos) e *IT (integração com PostgreSQL)
│   ├── pom.xml                      # versões fora do BOM do Boot em <properties>
│   └── mvnw                         # Maven fixado, com checksum
├── frontend/                        # React + TypeScript + Vite (veja frontend/README.md)
├── docs/                            # arquitetura, aceite, progresso, referências, ADRs e modelo de ameaças
│   └── api/openapi.json             # spec OpenAPI gerada pela API e versionada
├── .spectral.yaml                   # lint da spec (Spectral + OWASP)
├── .github/                         # workflow de CI e Dependabot
├── compose.yaml                     # PostgreSQL local
├── .env.example                     # variáveis do Docker Compose e da API
└── CLAUDE.md                        # instruções para o assistente de código
```

Os módulos de negócio ainda não existem: a Foundation não tem regra de negócio
([ADR 0001](docs/adr/0001-clean-architecture-monolito-modular.md)). `health` e `shared` são técnicos. Visão completa em
[docs/architecture.md](docs/architecture.md).

## Pré-requisitos

- JDK **25** (Temurin). O Maven vem pelo wrapper (`backend/mvnw`), não precisa estar instalado.
- Node **24** e npm 11.
- Docker Desktop em execução (PostgreSQL local e testes de integração).

## Executar localmente

Na raiz do repositório:

```bash
# 1. Variáveis do Compose e da API. Edite POSTGRES_PASSWORD no .env (o compose recusa subir sem ela).
cp .env.example .env

# 2. PostgreSQL (publicado só em 127.0.0.1)
docker compose up -d --wait

# 3. API em http://localhost:5120 (terminal 1). O .env carregado no shell passa SPRING_DATASOURCE_* à API.
#    plain-logs: log em texto em vez de JSON; api-docs: spec em http://localhost:5120/openapi/v1.json
set -a && . ./.env && set +a
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=plain-logs,api-docs

# 4. Frontend em http://localhost:5173 (terminal 2)
cd frontend && npm ci && npm run dev
```

Abra http://localhost:5173: o painel "Estado do sistema" deve mostrar "API e banco de dados respondendo".
Para ver os outros estados, rode `docker compose stop postgres` (a API responde, banco indisponível) ou
pare a API (API não respondendo) e clique em "Verificar novamente".

A API **não sobe** sem `SPRING_DATASOURCE_URL` válida; a mensagem de erro nomeia a chave, nunca o valor. Ela sobe
com o banco fora do ar (a liveness continua respondendo), e as migrations nunca rodam na partida.

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
- O documento OpenAPI servido em `/openapi/v1.json` só existe com o profile `api-docs`.

Racional: [ADR 0004](docs/adr/0004-health-checks-liveness-e-readiness.md) e
[ADR 0007](docs/adr/0007-convencoes-de-api-e-seguranca-da-foundation.md).

## OpenAPI

A spec da API fica em [`docs/api/openapi.json`](docs/api/openapi.json). Ela é gerada a partir do código pelo
`OpenApiDocumentIT`, que grava `backend/target/openapi.json` e **falha se a versionada divergir**: quem muda o
contrato roda `./mvnw verify`, copia a gerada para `docs/api/openapi.json`, revisa o diff e commita junto.

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
| `SPRING_DATASOURCE_URL` | Ambiente (em dev, o `.env` carregado no shell) | nenhum, **obrigatória** | URL JDBC do PostgreSQL (`jdbc:postgresql://host:porta/banco`), sem senha |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | Idem | nenhum | Credenciais do banco, separadas da URL |
| `SPRING_PROFILES_ACTIVE` | Ambiente | nenhum | `plain-logs` (log em texto), `api-docs` (spec em `/openapi/v1.json`); nunca `api-docs` em produção |
| `TRACEON_API_URL` | Ambiente ou `frontend/.env` | `http://localhost:5120` | Destino do proxy do Vite; lida só pelo `vite.config.ts`, não entra no bundle |

Segredos ficam fora do Git (`.env` local e variáveis de ambiente). Nada de credencial no código, nos logs ou no bundle do
frontend ([ADR 0006](docs/adr/0006-integracao-frontend-proxy-e-configuracao.md)).

## Testes, lint e formatação

```bash
# Backend, em backend/: compila com -Xlint:all -Werror, roda 15 testes rápidos (*Test) e 32 de integração
# (*IT, PostgreSQL real via Testcontainers), confere formatação, dependências declaradas e drift da spec
./mvnw verify
./mvnw test                # só os rápidos, sem Docker
./mvnw spotless:apply      # corrige a formatação (Palantir Java Format)

# Frontend (83 testes)
cd frontend
npm run lint
npm run typecheck
npm test
npm run build
```

Os testes de integração sobem um container `postgres:18.6-alpine3.24` e exigem o Docker em execução; cada
teste define o próprio banco (`@DynamicPropertySource`), então nada do `.env` ou do shell entra neles. Estratégia: [ADR 0005](docs/adr/0005-estrategia-de-testes.md).
Os critérios de aceite e o que ainda está pendente estão em [docs/acceptance.md](docs/acceptance.md).

## CI

`.github/workflows/ci.yml` define quatro jobs: **backend** (`./mvnw verify`: formatação, build sem avisos,
análise de dependências, testes rápidos e de integração, drift da spec OpenAPI), **frontend** (lint, typecheck, testes, build), **api-spec**
(Spectral + ruleset OWASP sobre `docs/api/openapi.json`) e **secrets** (gitleaks 8.30.1 com checksum
sobre o histórico completo). As actions estão fixadas por SHA. O Dependabot (`.github/dependabot.yml`) cobre
Maven, npm, GitHub Actions e imagens do Compose, sem atualizar versões principais.

## Solução de problemas

| Sintoma | Causa provável e ação |
|---|---|
| `docker compose up` falha pedindo `POSTGRES_PASSWORD` | Falta o `.env`; rode `cp .env.example .env` e defina a senha |
| Porta 5432 ocupada (ex.: PostgreSQL nativo) | Troque `POSTGRES_PORT` no `.env` (ex.: `5433`); a `SPRING_DATASOURCE_URL` do `.env.example` acompanha |
| A API aborta ao iniciar com `spring.datasource.url is required` | Carregue o `.env` no shell (passo 3) ou defina `SPRING_DATASOURCE_URL`; um `.env` antigo, sem as variáveis `SPRING_DATASOURCE_*`, precisa delas (veja o `.env.example`) |
| Testes de integração falham ao iniciar o container | Docker Desktop precisa estar rodando |
| Painel mostra "A API não está respondendo" | API parada ou `TRACEON_API_URL` incorreta; teste `curl -s localhost:5120/health/live` |
| Painel mostra "banco de dados está indisponível" | PostgreSQL parado ou senha/porta divergentes; `docker compose up -d` e confira as `SPRING_DATASOURCE_*` |
| Log em JSON difícil de ler no terminal | Suba com o profile `plain-logs` |
| Quero recomeçar com o banco vazio | `docker compose down -v` apaga o volume `postgres-data` (todos os dados locais) |
| Volume do PostgreSQL 18 | A imagem grava em `/var/lib/postgresql`; o `compose.yaml` já monta o volume nesse caminho |
| `npm ci` avisa sobre install script do `fsevents` (npm 11) | Aviso conhecido, não bloqueia a instalação |

## Documentação

- [docs/architecture.md](docs/architecture.md): camadas, módulos, fluxo de health e decisões de configuração
- [docs/acceptance.md](docs/acceptance.md): critérios de aceite da Foundation com evidências e pendências
- [docs/progress.md](docs/progress.md): etapas e próximo incremento
- [docs/security/threat-model.md](docs/security/threat-model.md): modelo de ameaças (ameaça, mitigação, teste)
- [docs/api/openapi.json](docs/api/openapi.json): spec OpenAPI gerada pela API
- [docs/references.md](docs/references.md): bibliografia (20 livros) e seu papel
- [docs/prompt-mestre.md](docs/prompt-mestre.md): escopo e regras do projeto
- ADRs: [0001](docs/adr/0001-clean-architecture-monolito-modular.md) arquitetura,
  [0002](docs/adr/0002-catalogo-de-design-patterns.md) padrões,
  [0003](docs/adr/0003-stack-e-politica-de-versoes.md) stack e versões,
  [0004](docs/adr/0004-health-checks-liveness-e-readiness.md) health checks,
  [0005](docs/adr/0005-estrategia-de-testes.md) testes,
  [0006](docs/adr/0006-integracao-frontend-proxy-e-configuracao.md) frontend e configuração,
  [0007](docs/adr/0007-convencoes-de-api-e-seguranca-da-foundation.md) convenções de API e segurança,
  [0008](docs/adr/0008-identidade-organizacoes-e-comprovacao-de-sites.md) identidade, organizações e sites,
  [0009](docs/adr/0009-migracao-do-backend-para-java-e-spring-boot.md) migração do backend para Java

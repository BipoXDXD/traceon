# Traceon — frontend

React + TypeScript + Vite + Tailwind CSS. Nesta etapa (Foundation) há uma tela só: o painel
"Estado do sistema", que consulta `/health/live` e `/health/ready` da API.

## Requisitos

Node 24 LTS (`.nvmrc`) e npm 11. Instale com `npm ci`.

## Scripts

| Comando | O que faz |
|---|---|
| `npm run dev` | Servidor de desenvolvimento em http://localhost:5173 |
| `npm run build` | Checagem de tipos (`tsc -b`) e build de produção em `dist/` |
| `npm run lint` | ESLint (typescript-eslint strict type-checked + react-hooks), zero avisos |
| `npm run typecheck` | `tsc -b` sobre o app e os arquivos de configuração |
| `npm test` | Vitest + Testing Library (jsdom), modo run |
| `npm run preview` | Serve o `dist/` com o mesmo proxy do `dev` |

## Integração com a API

O navegador fala só com o Vite. O servidor de desenvolvimento (e o `preview`) encaminha apenas
`/health/*` para a API, então o backend não precisa de CORS.

| Variável | Padrão | Uso |
|---|---|---|
| `TRACEON_API_URL` | `http://localhost:5120` | Destino do proxy. Lida só pelo `vite.config.ts`; não tem prefixo `VITE_` e não entra no bundle. |

Pode ser definida no ambiente ou num `frontend/.env` (ignorado pelo Git).

## Estados do painel

| Situação | API | Banco de dados |
|---|---|---|
| `live` 200 e `ready` 200 com `database` Healthy | Respondendo | Disponível |
| `live` 200 e `ready` 503 com `database` Unhealthy | Respondendo | Indisponível |
| Falha de rede, timeout (5 s) ou 5xx em `live` (o proxy responde 502 sem a API) | Não respondendo | Desconhecido |
| Corpo fora do contrato, status inesperado ou `ready` sem resposta | Resposta inesperada | Desconhecido |

## Solução de problemas

- **Painel mostra "A API não está respondendo"**: confira se a API está no ar
  (`curl -s localhost:5120/health/live`) e se `TRACEON_API_URL` aponta para ela.
- **"Banco de dados indisponível"**: a API responde, mas não alcança o PostgreSQL; suba o banco
  com `docker compose up -d` na raiz do repositório.

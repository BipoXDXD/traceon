# ADR 0006 — Integração do frontend por proxy e configuração sem segredos no bundle

- **Status:** Aceito (2026-10-09). Revisão pendente pelo responsável do projeto.

## Contexto

O prompt mestre exige que a tela inicial consulte o estado real da API por proxy local do Vite, sem liberar CORS
indiscriminadamente, e que nenhuma credencial vá para código, logs ou bundle. Há dois tipos de configuração: a do
frontend (para onde o proxy aponta) e a do backend (connection string com senha).

Alternativas para o navegador chegar à API:

| Opção | Prós | Contras |
|---|---|---|
| **A. CORS na API (origem `http://localhost:5173`)** | O navegador chama a API direto | Superfície nova na API; política que precisa ser lembrada e restringida; URL da API acaba no bundle |
| **B. Proxy do Vite só para `/health`** | Mesma origem para o navegador; a API não precisa de CORS; a URL do destino não entra no bundle | Só serve em desenvolvimento e `preview`; em produção será preciso outro mecanismo (reverse proxy ou Static Web Apps), decisão da etapa 6 |
| **C. Variável `VITE_API_URL` apontando a API** | Simples | Variáveis `VITE_*` são embutidas no bundle público; convida a colocar valores sensíveis ali |

Alternativas para a connection string: `appsettings.json`, variável de ambiente, User Secrets, cofre. O cofre
(Azure Key Vault) é da etapa 6.

## Decisão

- **Proxy (opção B):** `vite.config.ts` encaminha apenas `/health` (no `server` e no `preview`) para a API. O
  frontend usa caminhos relativos (`/health/live`, `/health/ready`). **A API não configura CORS.** Cada rota nova
  exposta ao navegador exige entrada explícita no proxy.
- **Destino do proxy:** `TRACEON_API_URL` (padrão `http://localhost:5120`), lida só pelo `vite.config.ts` com
  `loadEnv`, **sem prefixo `VITE_`**, portanto nunca no bundle; aceita só `http` ou `https`. Pode vir do ambiente ou
  de `frontend/.env` (ignorado pelo Git).
- **Frontend sem segredos:** nenhum valor sensível entra no frontend. O bundle contém só código; o `fetch` passa por um
  wrapper único que trata a resposta como `unknown`, confere `response.ok`/status esperado e valida o contrato antes de
  virar tipo da tela.
- **Backend:** a connection string `ConnectionStrings:Traceon` é obrigatória, validada com `ValidateOnStart` e nunca
  ecoada em mensagens de erro. Fontes: **User Secrets** em desenvolvimento (`UserSecretsId` no projeto da API) e
  **variável de ambiente** `ConnectionStrings__Traceon` nos demais ambientes; nunca em `appsettings*.json`.
- **Compose:** `.env` (ignorado pelo Git) alimenta o `compose.yaml`; `POSTGRES_PASSWORD` é obrigatória e sem valor
  padrão; `.env.example` traz só valores de exemplo. O banco é publicado só em `127.0.0.1`.
- **Scanner de segredos:** gitleaks no histórico completo, na CI.

## Consequências

- Em desenvolvimento não há CORS a manter nem origem a restringir; o navegador bloqueia a leitura de respostas da API
  por outras origens (sem CORS).
- A senha existe em dois lugares na máquina local (`.env` do Compose e User Secrets da API) e precisa ser a mesma; um
  desencontro aparece como "banco indisponível" no painel (documentado no README).
- Em produção o proxy do Vite deixa de existir: a etapa 6 precisa definir o equivalente (reverse proxy na mesma
  origem ou CORS restrito, com ADR próprio).
- Os endpoints de health ficam acessíveis sem autenticação pelo proxy; sensibilidade baixa por causa do corpo mínimo
  (ADR 0004).

## Compliance

- Revisão de diff: nenhuma variável `VITE_*` com valor sensível; nenhum segredo em `appsettings*.json`, `.env.example`
  ou `compose.yaml`.
- `StartupConfigurationTests`: sem connection string, a API não sobe; connection string malformada não é repetida na
  exceção (canário).
- Job `secrets` (gitleaks) na CI, ainda não executado no GitHub.
- A API não registra `AddCors`/`UseCors`; a adição exigirá ADR novo.

## Referências

*Secure APIs* (Haro Peralta): configuração segura e gestão de segredos, conforme as notas do projeto;
*Fundamentals of DevOps and Software Delivery* (Brikman): configuração e segredos fora do código, notas em
`~/.claude/knowledge/devops.md`; documentação oficial do Vite (`server.proxy`, `loadEnv`, prefixo `VITE_`) e do
ASP.NET Core (User Secrets, configuração por variáveis de ambiente).

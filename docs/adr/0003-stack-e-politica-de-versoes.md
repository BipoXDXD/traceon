# ADR 0003 — Stack e política de versões

- **Status:** Aceito (2026-10-09). Revisão pendente pelo responsável do projeto.

## Contexto

O prompt mestre fixa a stack (C# 14, ASP.NET Core 10, EF Core 10, Npgsql e PostgreSQL; React, TypeScript, Vite e
Tailwind; xUnit) e exige versões estáveis e compatíveis, fixadas em arquivos versionados, sem `latest` e sem
atualização de versão principal sem justificativa. Falta decidir **como** fixar e quando aceitar uma versão nova.
O projeto é de um desenvolvedor, com CI ainda não executada e dependências de supply chain (NuGet, npm, Actions)
que mudam toda semana.

Alternativas para fixar versões:

| Opção | Prós | Contras |
|---|---|---|
| **A. Ranges (`^`, `*`, versões flutuantes)** | Recebe correções sem esforço | Build não reproduzível; uma versão recém-publicada e comprometida entra sem revisão |
| **B. Versões exatas, centralizadas, com idade mínima e atualização por PR** | Build reproduzível; toda mudança passa por diff e CI; superfície menor contra versões recém-publicadas | Exige manter as atualizações (Dependabot) e revisar |
| **C. B + lockfile NuGet (`packages.lock.json`) com restore travado** | Fixa também as dependências transitivas por hash | Mais arquivos e ruído nos PRs; o `CentralPackageTransitivePinningEnabled` já cobre o essencial hoje |

## Decisão

Adotamos a **opção B**, com estas regras:

- **.NET:** `global.json` fixa o SDK em `10.0.400` com `rollForward: latestPatch` e o runner de testes
  `Microsoft.Testing.Platform` (comando `dotnet test --solution`). **Central Package Management**
  (`backend/Directory.Packages.props`) com `CentralPackageTransitivePinningEnabled`; todo pacote com **versão exata**,
  nunca range. Os pacotes de runtime do .NET ficam na mesma versão (hoje `10.0.12`); Npgsql EF Core na `10.0.3`.
- **Testes backend:** `xunit.v3` `4.0.1` sobre Microsoft.Testing.Platform (projetos de teste são `Exe`);
  `Microsoft.AspNetCore.Mvc.Testing` e `Testcontainers.PostgreSql` `4.15.0`.
- **Frontend:** `package.json` com versões exatas (`.npmrc`: `save-exact=true` e `engine-strict=true`), Node 24 em
  `.nvmrc` e `engines`, `package-lock.json` versionado e `npm ci` na CI. **TypeScript fica em `6.0.3`**: o peer
  dependency do `typescript-eslint` `8.70.1` é `>=4.8.4 <6.1.0`, então uma versão 6.1 ou 7 quebraria a análise
  estática. Subir o TypeScript depende de o `typescript-eslint` ampliar o intervalo.
- **Imagens e actions:** `postgres:18.6-alpine3.24` (a mesma no Compose e no Testcontainers; sem `latest`); GitHub
  Actions fixadas por SHA de commit com o número da versão em comentário; runner `ubuntu-24.04`.
- **Idade mínima:** só se adota versão publicada há **pelo menos 2 semanas**, para dar tempo de a comunidade
  detectar versão defeituosa ou comprometida. A regra é aplicada na escolha, manualmente; o Dependabot não tem
  `cooldown` configurado.
- **Atualizações:** Dependabot semanal para NuGet, npm, GitHub Actions e imagens do Compose, agrupando minor e patch
  e **ignorando major**, que exige decisão e PR manual com justificativa (ADR novo, se mudar a stack).
- Estável e compatível: nenhuma versão prerelease; compatibilidade confirmada pelo build, pelos testes e pela CI.

## Consequências

- Builds reproduzíveis; toda atualização é um diff revisável que passa pela CI.
- Custo: trabalho recorrente de revisar PRs do Dependabot e a regra manual de idade mínima, que pode ser esquecida.
- Dependências transitivas do NuGet ficam travadas só pelo pinning central, sem hash; adotar lockfile é uma
  evolução possível (opção C) se o risco de supply chain justificar.
- O TypeScript atrás da última versão disponível é uma dívida consciente e visível aqui.
- O Dependabot e a CI ainda não foram executados no GitHub (veja `docs/acceptance.md`).

## Compliance

- `ManagePackageVersionsCentrally` ligado: um `<PackageReference>` com `Version` quebra o restore.
- O `dotnet` falha se nenhum SDK instalado casa com `global.json`.
- `npm ci` falha se o lockfile divergir do `package.json`; `engine-strict` barra Node fora de 24.
- Revisão de PR: nenhuma versão com `^`, `~`, `*` ou `latest`; major só com justificativa.

## Referências

*Fundamentals of DevOps and Software Delivery* (Brikman), destilação em `~/.claude/knowledge/devops.md`: pipeline
com build reproduzível e infraestrutura versionada. Documentação oficial do .NET (Central Package Management,
`global.json`) e do npm (`save-exact`, `engine-strict`).

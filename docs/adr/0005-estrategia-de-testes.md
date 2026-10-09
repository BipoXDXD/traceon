# ADR 0005 — Estratégia de testes

- **Status:** Aceito (2026-10-09). Revisão pendente pelo responsável do projeto.

## Contexto

O prompt mestre pede testes de integração HTTP com PostgreSQL real, verificação de liveness com banco indisponível e
readiness com banco disponível e indisponível, e proíbe regras artificiais ou testes triviais para "preencher" o
projeto de unit tests. Na Foundation não há regra de negócio: `Domain` e `Application` estão vazios (ADR 0001). A
referência central é *Unit Testing Principles, Practices, and Patterns* (Khorikov).

Alternativas para o banco nos testes de integração:

| Opção | Prós | Contras |
|---|---|---|
| **A. `UseInMemoryDatabase` ou SQLite** | Rápido; sem Docker | Não executa Npgsql, constraints nem SQL do PostgreSQL; o health check testado seria outro código |
| **B. Mock da conexão/repositório** | Rápido | Testa o mock; não prova conectividade real, timeout nem vazamento de diagnóstico |
| **C. PostgreSQL real em container (Testcontainers), mesma imagem do Compose** | Exercita o código de produção; falhas reais (recusa, silêncio) | Exige Docker; mais lento (um container por execução da suíte) |

Alternativas para verificar a regra da dependência: biblioteca de arquitetura sobre assemblies (por exemplo
NetArchTest) ou leitura dos `.csproj`.

## Decisão

- **Backend, integração (opção C):** `Traceon.IntegrationTests` hospeda a API real em memória com
  `WebApplicationFactory<Program>` e um único container `postgres:18.6-alpine3.24` por execução (`PostgresFixture`, via
  `AssemblyFixture`). O ambiente é `Testing`, que não lê User Secrets. Sem `UseInMemoryDatabase` nem mock do banco.
- **Falhas simuladas sem mock:** `UnreachableDatabase` cria um endpoint local que recusa conexões (porta liberada) ou
  aceita TCP e nunca responde (`TcpListener` sem `Accept`), reproduzindo recusa e silêncio. O banco indisponível é
  testado com a sonda real.
- **Asserções fortes:** corpo JSON exato, status, `Content-Type`, `Cache-Control`, duração máxima do 503 (margem de
  10 s para um limite de 3 s) e canários (senha, porta, `Host=`, `Exception`, `Npgsql`) que não podem aparecer. Um
  comportamento por teste; casos de borda via `[Theory]`. Cobertura da configuração: ausente, vazia, só espaços e
  malformada derrubam a partida sem repetir o valor.
- **Backend, arquitetura (leitura dos `.csproj`):** `Traceon.UnitTests` contém apenas os testes da regra da dependência
  (ADR 0001), lendo os `.csproj` (`ProjectFile` + `DependencyRuleTests`). Escolhida em vez de análise de assemblies
  porque as regras de hoje são sobre referências de projeto e pacote, sem dependência extra e sem carregar assemblies. Um teste
  guarda a lista de projetos de `src/` para que um projeto novo não escape da regra.
- **Sem testes triviais:** nenhum teste para `Domain` e `Application` enquanto não houver comportamento. A primeira
  regra de negócio chega com seus testes de unidade (funções puras, `now` injetado).
- **Frontend:** Vitest + Testing Library (jsdom). O `fetch` é a única borda: `vi.stubGlobal('fetch', ...)` devolve
  respostas controladas; o resto roda de verdade (parse do contrato, regras de estado, hook, componente). Cobre o
  contrato (corpos inválidos, status inesperados), timeout, cancelamento, os estados do painel (carregando, operacional, banco indisponível, resposta inesperada),
  resposta tardia descartada, acionamento por teclado e o contraste das cores do tema (WCAG AA). Sem mock de módulo interno.
- **O que não se testa:** detalhes de implementação, métodos privados, textos irrelevantes à regra. Mock apenas em
  dependência não controlada; o banco próprio é real.
- **CI:** os mesmos comandos locais (`dotnet test --solution`, `npm test`); resultados do backend em TRX como artefato.

## Consequências

- A suíte de integração depende do Docker (local e na CI) e leva alguns segundos a mais; falha sem Docker.
- Os testes de timeout medem tempo real e podem ser sensíveis a CI lenta; a margem de 10 s para um limite de 3 s é
  deliberada.
- A regra da dependência cobre referências de projeto e pacote, não `using` entre namespaces dentro de um projeto; essa
  lacuna é aceita até haver módulos (a fronteira entre módulos precisará de outro teste, no ADR da etapa 2).
- Não há medição de cobertura nem mutation testing; avaliar quando houver regra de negócio.
- A exceção não tratada (500) é testada com um `HealthCheckService` que lança, sem rota de teste na API
  (`UnhandledExceptionTests`, ADR 0007).

## Compliance

- A CI executa as duas suítes; `TreatWarningsAsErrors` e `dotnet format --verify-no-changes` valem também para os testes.
- Revisão de PR: teste novo precisa de um comportamento observável; teste que quebra ao refatorar sem mudar
  comportamento é defeito do teste.
- Nova dependência de produção sem teste de integração real é recusada.

## Referências

*Unit Testing Principles, Practices, and Patterns* (Khorikov): dependências gerenciadas reais e não gerenciadas
mockadas na borda, testes de integração, cap. 8 e caps. 7-8 (separar decisão de efeito), conforme as notas e o ADR 0002;
*Clean Architecture with .NET*, notas do projeto: testes de banco com o mesmo SGBD, sem provider in-memory;
*Release It!*, 2ª ed., cap. 5 (simular lentidão e falha de integração).

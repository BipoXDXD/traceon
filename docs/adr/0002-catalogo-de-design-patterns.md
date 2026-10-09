# ADR 0002 — Catálogo de design patterns do Traceon

- **Status:** Aceito (2026-10-09). Revisão pendente pelo responsável do projeto.

## Contexto

O prompt mestre pede DDD e design patterns "onde resolverem problemas concretos" e proíbe repositórios
genéricos, classes-base, interfaces sem necessidade, MediatR, CQRS completo, Event Sourcing e Redis por
antecipação. Este ADR define **quais padrões entram, em que etapa e por qual problema**, para que a escolha
não seja feita feature a feature nem por reflexo.

Regra de entrada: um padrão só é implementado quando o problema que ele resolve **existe no código** ou a
segunda variante já foi pedida. Nas etapas futuras, este catálogo é um plano, não autorização para criar
classes antes da hora.

## Decisão

### Estruturais da arquitetura (valem desde a Foundation)

| Padrão | Problema que resolve no Traceon | Forma em .NET |
|---|---|---|
| **Dependency Rule / Clean Architecture** | Domínio protegido de EF, ASP.NET Core e Playwright | 4 projetos, referências só para dentro (ADR 0001) |
| **Composition Root + injeção por construtor** | Um único lugar conhece as implementações | `Program.cs` + `AddInfrastructure(...)`; primary constructors; `ValidateScopes` e `ValidateOnBuild` ligados |
| **Options tipadas com validação no boot** | Config ausente derruba o app na partida, não na primeira requisição | `AddOptions<T>().Bind(...).ValidateOnStart()` |
| **Unit of Work** | Uma transação por caso de uso | O próprio `DbContext` (`SaveChangesAsync` uma vez); **sem** UoW nem repositório genérico próprio |
| **Health Check (liveness × readiness)** | Distinguir processo vivo de app pronta (banco alcançável) | `MapGet` (só GET) sobre `HealthCheckService`, readiness filtrada pela tag `ready` e com timeout; DTOs de resposta por allowlist (ADR 0004, ADR 0007) |
| **Problem Details** | Erro consistente, sem detalhe interno | `AddProblemDetails` + `UseExceptionHandler` + `UseStatusCodePages` (404, 405 e 500 genéricos, com `traceId` no 500); `IExceptionHandler` só quando houver exceção de domínio a mapear |

### Domínio (a partir da etapa 2)

| Padrão | Problema | Quando |
|---|---|---|
| **Aggregate + Entity rica** | Invariantes de `Organization`, `Site`, `Baseline`, `Finding` | Etapa 2; construtor privado, factory estática nomeada, setters privados, alteração só pela raiz |
| **Value Object** | URL de site normalizada, domínio, hash de evidência, ids tipados | Etapa 2; `record` imutável, "parse, don't validate" na fronteira |
| **Repository por aggregate** | Persistir aggregates ricos sem vazar EF | Só para aggregate com regra; interface no `Application`, implementação `internal` na `Infrastructure`; nunca `IQueryable` nem `IRepository<T>` |
| **Application Service (caso de uso)** | Orquestrar validação → autorização → carregar → domínio → salvar | Classe por feature com um método por operação, **sem** interface quando há uma implementação e **sem** MediatR |
| **Result tipado** | Falha esperada (limite, estado inválido) sem exceção | `record`/união pequena; violação de invariante continua sendo exceção de domínio |
| **State (como enum + tabela de transições)** | Ciclo de vida de verificação (`Pending → Running → Succeeded/Failed/Cancelled`) e de achado (`Open → InReview → Resolved/Dismissed`) | Etapas 3 e 5; métodos de transição no aggregate (`StartReview()`), `CHECK` no banco; classes de estado só se cada estado ganhar dados diferentes |
| **Policy / Specification nomeada** | Regras com nome do negócio (ex.: "referência aprovada vigente", "destino de coleta permitido") | Quando a regra sair de um `if` repetido; classe pequena e pura, sem framework genérico de Specification |

### Integração, processamento e estabilidade (etapas 3 a 5)

| Padrão | Problema | Quando |
|---|---|---|
| **Adapter (port no Application, adapter na Infrastructure)** | Isolar Playwright, e-mail/webhook e Blob Storage, que são voláteis e não confiáveis | Etapa 3 (coletor) e 5 (notificações); interface na linguagem do negócio (`IPageCollector.CollectAsync`), sem expor tipo do SDK |
| **Strategy (como função ou classe pequena)** | Métodos de comprovação de controle do site (DNS TXT, meta tag, arquivo) e comparadores por tipo de evidência (scripts, DOM, headers) | Etapa 2 (2º método) e 4; registro por chave com falha no boot se houver duplicata |
| **Job table + Competing Consumers** | Processamento persistente, retomável e sem duplicar trabalho entre réplicas | Etapa 3; tabela de jobs com estado, `FOR UPDATE SKIP LOCKED`, lease com expiração |
| **Producer/Consumer com fila limitada** | Concorrência limitada dentro do worker | Etapa 3; `Channel<T>` bounded como transporte interno, nunca como fonte da verdade |
| **Idempotency key / Idempotent Consumer** | Reentrega, retry e duplo clique sem efeito duplicado | Etapa 3 (agendamento) e 5 (notificação); chave única gravada na mesma transação do efeito |
| **Transactional Outbox + Domain Events** | Notificar sem dual write | Etapa 5; evento no passado, imutável, gravado na mesma transação; relay com retentativas limitadas |
| **Timeout, Circuit Breaker, Bulkhead** (Release It!) | Coletor e notificações não podem travar o sistema | Etapa 3 em diante; `Microsoft.Extensions.Http.Resilience` / `Microsoft.Extensions.Resilience`, limites por site e por organização |
| **Audit log append-only** | Histórico e responsáveis por decisão | Etapa 5; tabela só de inserção, **não** Event Sourcing |

### Frontend

| Padrão | Problema | Forma |
|---|---|---|
| **Estado como união discriminada** | Carregando, vazio, erro e sucesso sem flags combinadas | `type State = { kind: 'loading' } \| { kind: 'ready', ... } \| ...` com `switch` exaustivo |
| **Custom hook** | Isolar `fetch` e ciclo de vida da tela | `useSystemStatus()`; cancelamento com `AbortController` |
| **Wrapper único de HTTP com parse na fronteira** | Resposta da API é dado não confiável | Uma função que confere `response.ok` e valida o formato antes de virar tipo da tela |

### Rejeitados por padrão (precisam de ADR próprio para entrar)

MediatR e pipeline behaviors; CQRS com modelos/bancos separados; Event Sourcing; repositório genérico e
classe-base de entidade; AutoMapper; Singleton manual e `static` com estado; Service Locator ou
`IServiceProvider` fora do composition root; Mediator/Facade genéricos ("God service"); Redis/cache
distribuído; Redux/Zustand e bibliotecas de componentes no frontend.

## Consequências

- Cada padrão tem gatilho explícito; revisões de código citam este ADR para aceitar ou recusar classes novas.
- A Foundation usa só a primeira tabela. O resto é aplicado, ajustado ou removido quando a etapa chegar,
  com nota no ADR da etapa se divergir daqui.

## Compliance

- Revisão de diff: classe nova de padrão precisa apontar a linha deste ADR ou um ADR novo.
- Testes de arquitetura (ADR 0001) cobrem a regra da dependência; os demais itens são verificados em revisão.

## Referências

*Dive Into Design Patterns* (Shvets): Adapter, Strategy, State e prós e contras de cada um;
*A Philosophy of Software Design*, 2ª ed., caps. 4–7 (módulos profundos, pass-through);
*Learning Domain-Driven Design*, caps. 5–6 e 10; *Release It!*, 2ª ed., cap. 5 (padrões de estabilidade);
*Designing Data-Intensive Applications*, 2ª ed., caps. 8 e 12 (transações, outbox, idempotência);
*Unit Testing Principles, Practices, and Patterns*, caps. 7–8 (separar decisão de efeito; o que mockar).

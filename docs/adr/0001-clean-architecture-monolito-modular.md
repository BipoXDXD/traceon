# ADR 0001 — Clean Architecture pragmática em monólito modular

- **Status:** Aceito (2026-10-09). Revisão pendente pelo responsável do projeto.
- **Decisores:** responsável do projeto; implementação conduzida pelo assistente.

## Contexto

O Traceon é um SaaS de monitoramento de integridade de aplicações web, com sete módulos planejados
(Identity & Organizations, Sites, Monitoring, Integrity, Findings, Notifications, Audit). Hoje existe um
desenvolvedor, um banco PostgreSQL e um orçamento de cloud pequeno (US$100). As regras de negócio vão
crescer (comparação de evidências, referências aprovadas, ciclo de vida de achados), e o coletor externo
exige isolamento forte de segurança.

Alternativas consideradas:

| Opção | Prós | Contras |
|---|---|---|
| **A. Projeto único (API + EF) com pastas por feature** | Menos cerimônia; rápido no início | Regra da dependência só por disciplina; nada impede o domínio de importar EF ou ASP.NET Core |
| **B. Clean Architecture com 4 projetos (Domain, Application, Infrastructure, Api) e módulos como pastas** | Regra da dependência imposta pelo compilador; domínio testável sem banco; custo baixo (4 projetos, não 4 × 7) | Um pouco mais de cerimônia; risco de camadas vazias e pass-through se aplicada de forma literal |
| **C. Projetos por módulo × camada (28 projetos)** ou microservices | Fronteiras físicas fortes entre módulos | Cerimônia desproporcional para dev solo; distribuição sem driver medido |

## Decisão

Adotamos a **opção B**: monólito modular com Clean Architecture **pragmática**.

- Projetos: `Traceon.Domain` → nada; `Traceon.Application` → `Domain`; `Traceon.Infrastructure` →
  `Application` e `Domain`; `Traceon.Api` → `Application`, e referencia `Infrastructure` **só para composição**
  (`Program.cs` chama `AddInfrastructure`). Os tipos da `Infrastructure` são `internal`, expostos por
  métodos de extensão.
- Os módulos são **pastas/namespaces** dentro de cada projeto (`Traceon.Domain.Sites`,
  `Traceon.Application.Sites`, ...). Nenhum módulo usa entidades nem tabelas de outro; a comunicação
  entre eles passa pelo serviço de aplicação do dono.
- Um `DbContext` (`TraceonDbContext`) e um **schema PostgreSQL por módulo**, quando houver tabelas.
- O worker (etapa 3) será um **host separado** (`Traceon.Worker`) que reaproveita `Application` e
  `Infrastructure`. É um processo a mais, não um serviço com banco próprio.
- **Pragmática** quer dizer: sem input/output ports, presenters e modelos por camada enquanto não pagarem
  o custo. Leitura simples pode projetar direto do `DbContext` para DTO dentro do serviço de aplicação.

## Consequências

- Domínio e casos de uso testáveis sem banco, HTTP ou framework.
- Na Foundation, `Domain` e `Application` ficam **sem código**: não há regra de negócio e o prompt proíbe
  inventar entidades. Eles existem para que a primeira feature real já nasça na fronteira certa.
- Custo: 4 projetos e o cuidado de não criar interfaces 1:1 nem serviços pass-through (ver ADR 0002).

## Compliance

- `<ProjectReference>` só aponta para dentro (o compilador barra ciclos).
- Testes de arquitetura em `Traceon.UnitTests` (lendo os `.csproj`) falham se `Domain` referenciar qualquer
  projeto ou pacote, se `Application` referenciar algo além de `Domain` ou pacotes de infraestrutura, se
  `Infrastructure` depender de ASP.NET Core ou da `Api`, ou se algum projeto referenciar a `Api`.
- O uso de tipos da `Infrastructure` pela `Api` é barrado pelo compilador: esses tipos são `internal`.

## Referências

*Clean Architecture* (Martin; fora dos 20 livros do prompt, mas na biblioteca do responsável), caps. 22 (regra da dependência) e 24–25 (boundaries parciais);
*Fundamentals of Software Architecture*, 2ª ed., caps. 9–11 (monólito modular, particionamento por domínio);
*Learning Domain-Driven Design*, cap. 10 (escolha do padrão por complexidade).

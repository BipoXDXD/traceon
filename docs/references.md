# Referências

Bibliografia do projeto: exatamente 20 livros (`docs/prompt-mestre.md`, seção 7). Os livros orientam decisões e
revisão; não são checklist nem leitura obrigatória. Em conflito entre livros, valem requisitos, segurança,
simplicidade e evidências, com o trade-off registrado. Práticas e APIs atuais são confirmadas na documentação oficial.

## Como ler a coluna "Acesso"

Situação verificada em 2026-10-09 na máquina do responsável:

- **PDF**: existe em `~/Downloads/Books/Dev/`.
- **Notas**: existe destilação (paráfrase) em `~/.claude/knowledge/notes/` (um arquivo por livro) ou, quando
  indicado, apenas a destilação temática em `~/.claude/knowledge/<tema>.md`.

"Disponível" significa que o material pode ser consultado; **não** afirma leitura integral. Nenhum capítulo ou página
é citado neste repositório além dos que constam nas ADRs 0001 e 0002 e nas próprias notas. Nada aqui é citação literal.

## Os 20 livros

| Nº | Livro | Acesso | Uso no Traceon |
|---|---|---|---|
| 1 | *Code Complete*, 2e (McConnell) | PDF; destilado em `knowledge/code-quality.md` (sem arquivo próprio em `notes/`) | Consulta seletiva na revisão da construção do código |
| 2 | *Refactoring*, 2e (Fowler) | PDF; notas `refactoring.md` | Refatorações que preservam comportamento, separadas de mudanças funcionais |
| 3 | *The Pragmatic Programmer*, 2e (Thomas, Hunt) | PDF; notas `pragmatic-programmer.md` | Incrementos pequenos, feedback rápido, decisões reversíveis |
| 4 | *A Philosophy of Software Design*, 2e (Ousterhout) | PDF; notas `philosophy-software-design.md` | Módulos profundos, combate a pass-through (ADR 0002) |
| 5 | *Dive Into Design Patterns* (Shvets) | PDF (inglês) e PDF em português (*Mergulho nos Padrões de Projeto*); destilado em `knowledge/design-patterns.md` | Padrões só quando justificados: Adapter, Strategy e State (ADR 0002) |
| 6 | *Fundamentals of Software Architecture*, 2e (Richards, Ford) | PDF; notas `fundamentals-software-architecture.md` | Monólito modular e fronteiras (ADR 0001, caps. 9–11) |
| 7 | *Learning Domain-Driven Design* (Khononov) | PDF; notas `learning-ddd.md` | Linguagem, invariantes e escolha do padrão por complexidade (ADR 0001 cap. 10; ADR 0002 caps. 5–6 e 10) |
| 8 | *Unit Testing Principles, Practices, and Patterns* (Khorikov) | PDF; notas `unit-testing-khorikov.md` | **Referência central** da estratégia de testes (ADR 0005; ADR 0002 caps. 7–8) |
| 9 | *The Design of Web APIs*, 2e (Lauret) | PDF; sem arquivo próprio em `notes/`, destilado em `knowledge/api-security.md` | Contratos e evolução da API, a partir da etapa 2 |
| 10 | *API Design Patterns* (Geewax) | PDF; notas `api-design-patterns.md` | Operações assíncronas e contratos (etapa 3 em diante) |
| 11 | *Secure APIs* (Haro Peralta) | PDF; notas `secure-apis.md` | Controles e testes de segurança; SSRF antes de qualquer coleta externa |
| 12 | *Designing Data-Intensive Applications*, 2e (Kleppmann, Riccomini) | PDF; notas `ddia.md` | Consistência, idempotência, outbox (ADR 0002 caps. 8 e 12) |
| 13 | *The Art of PostgreSQL*, 2e, atualização de 2026 (Fontaine) | PDF; notas `art-of-postgresql.md` (edição 2ª, atualização de 2026, conforme as notas) | SQL, modelagem e transações, quando houver tabelas |
| 14 | *PostgreSQL Mistakes and How to Avoid Them* (Angelakos) | PDF; notas `postgresql-mistakes.md` | Revisão preventiva de banco e operação |
| 15 | *Effective Java*, 3e (Bloch) | PDF; notas `effective-java.md`; destilado em `knowledge/java-spring.md` | Java idiomático e concorrência (itens 78 a 84): worker, cancelamento e sincronização na etapa 3. Substituiu o livro de C# em 2026-10-09 (ver abaixo) |
| 16 | *Kubernetes in Action*, 2e (Lukša, Conner) | PDF; notas `kubernetes-in-action.md` | Consulta futura; Kubernetes **não** é requisito do MVP. Usado só como base conceitual de liveness × readiness (ADR 0004) |
| 17 | *Release It!*, 2e (Nygard) | PDF; notas `release-it.md` | Timeouts, estabilidade sob falha (ADR 0002 cap. 5; ADR 0004) |
| 18 | *Fundamentals of DevOps and Software Delivery* (Brikman) | PDF; sem arquivo próprio em `notes/`, destilado em `knowledge/devops.md` | CI/CD, infraestrutura e operação (ADR 0003, CI) |
| 19 | *Refactoring UI* (Wathan, Schoger) | **Sem PDF** em `Dev/`; notas `refactoring-ui.md` | Referência única de acabamento visual do frontend |
| 20 | *Code That Fits in Your Head* (Seemann) | PDF; notas `code-that-fits.md` | Revisão transversal e desenvolvimento incremental |

### Troca do nº 15

Até 2026-10-09 o nº 15 era *C# Concurrency: Asynchronous and Multithreaded Programming* (Dobovizki), que nunca esteve
disponível (nem PDF nem notas); o substituto de consulta era *Concurrency in C# Cookbook*, 2ª ed. (Cleary). Com a
migração do backend para Java ([ADR 0009](adr/0009-migracao-do-backend-para-java-e-spring-boot.md)), o prompt mestre
passou a listar *Effective Java*, 3e, que está disponível. Nenhuma passagem foi atribuída ao livro de Dobovizki.

Consulta complementar para Spring, fora dos 20 e sem leitura obrigatória: *Spring Security in Action*, 2e (Spilcă) e
*Cloud Native Spring in Action* (Vitale), ambos com PDF e notas (`spring-security-in-action.md`,
`cloud-native-spring.md`).

### Fonte que não faz parte dos 20

O ADR 0001 cita *Clean Architecture* (Martin), caps. 22 e 24–25, que existe em `~/Downloads/Books/Dev/` mas **não** está
na lista de 20 do prompt mestre. A citação vem da base de conhecimento do responsável. Fica registrado para decisão do
responsável: manter, ou trocar por referência da lista (por exemplo *Fundamentals of Software Architecture*).

## Complementos (sem leitura obrigatória)

| Fonte | Uso |
|---|---|
| WCAG | Contraste: `frontend/src/theme.test.ts` verifica pares de cor contra limiares de contraste WCAG AA |
| OWASP ASVS | Previsto para a etapa 2 (identidade e autorização); ainda não aplicado |
| Documentação oficial (Java, Spring Boot, Spring Framework, Spring Security, Hibernate, springdoc, Testcontainers, Vite, Vitest, PostgreSQL) | Confirmação de versões, APIs e práticas atuais |

## Como citar nas ADRs

Autor ou título curto, edição e capítulo **somente** quando constarem nas notas ou na documentação; sem número de
página. Livro indisponível não é citado.

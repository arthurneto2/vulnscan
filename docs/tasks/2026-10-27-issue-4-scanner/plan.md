# Plano — domain/Scanner (issue #4)

Spec: `spec.md` nesta pasta. Branch: `issue-4-scanner`.

- Produção: `src/main/java/br/ufv/vulnscan/domain/Scanner.java`.
- Teste: `src/test/java/br/ufv/vulnscan/domain/ScannerTest.java`, com um
  `FakeModule implements VulnModule` (classe aninhada no teste) que devolve findings
  fixos, registra o `Target` e o `Instant` em que rodou, e opcionalmente lança exceção.

Cada passo: teste primeiro (red por asserção), mínimo para passar (green), refactor.
Verificação de cada passo: `mvn test -Dtest=ScannerTest`.

## Passos

- [x] **1. Esqueleto + sem módulos.**
  - Teste: `#semModulosDevolveRelatorioVazio` — `target` igual e `findings` vazio.
  - Red por asserção: `scan` devolvendo `new ScanReport(null, null, null, null)`.
  - Green: `new ScanReport(target, now, now, List.of())`.

- [x] **2. Junta findings na ordem.**
  - Testes: `#juntaFindingsDosModulosNaOrdem` (módulo A com 1 finding, B com 2 → 3, na
    ordem A, B) e `#cadaModuloRecebeOAlvo`.
  - Green: laço sobre os módulos, `addAll`.

- [x] **3. Início e fim.**
  - Teste: `#inicioEFimEnvolvemAExecucao` — `antes ≤ startedAt ≤ módulo.rodouEm ≤
    finishedAt ≤ depois`.
  - Green: `Instant.now()` antes e depois do laço.

- [x] **4. Imutabilidade e cópia defensiva.**
  - Testes: `#findingsDoRelatorioImutaveis` e `#listaDeModulosCopiadaNoConstrutor`
    (`ArrayList` com A; constrói; adiciona B; só A roda).
  - Green: `List.copyOf` no construtor e no resultado.

- [x] **5. Exceção propaga.**
  - Teste: `#excecaoDeModuloPropaga` — A lança `IllegalStateException`, B não roda;
    `assertThrows` com a mesma instância.
  - Deve passar de cara (sem `try`); confirmar mordida envolvendo temporariamente o
    módulo num `try/catch` que engole.

- [x] **6. Nulos.**
  - Testes: `#modulosNulosLancaNpe`, `#alvoNuloLancaNpe`.
  - Green: `Objects.requireNonNull` com mensagem.
  - *Desvio:* `#modulosNulosLancaNpe` passou de cara, porque o `List.copyOf` do passo 4
    já lança NPE com `null`. O `requireNonNull` explícito ficou só para dar nome ao
    argumento na mensagem. `#alvoNuloLancaNpe` teve red normal.

- [x] **7. Refactor e Javadoc** (camada: domínio, orquestração).

- [x] **8. Checkpoint.** `mvn clean verify` verde (inclui `DependencyRuleTest`). Marcar
  passos; commits; PR fechando #4.

# Plano — CLI: --detector heuristic|llm|both (issue #19)

Spec: `spec.md` nesta pasta. Branch: `issue-19-cli-detector`.

Cada passo: teste → mínimo → refactor.

## Passos

- [x] **1. `ScanRequest` com detector.** `ScanRequestTest` (ou no `ScanServiceTest`):
  construtor de 4/5 args → `"heuristic"`; nulo/vazio → `"heuristic"`. Atualizar os usos.

- [x] **2. `ScanService` com fábricas + detectores.** Reescrever o construtor e o `scan`.
  `ScanServiceTest`: `#heuristicUsaSoHeuristico`, `#llmUsaSoLlm`,
  `#bothRodaOsDoisPorCaso`, `#detectorDesconhecidoLancaInvalid`,
  `#ordemModuleMajor`. Adaptar os testes existentes às fábricas.

- [x] **3. `Container` com detectores e fábricas.** `ContainerTest#detectoresHeuristicELlm`
  (ex.: `--detector llm` roda contra o servidor em processo e grava relatório com veredito
  `llm`). Ajustar o que montava `ScanService(modules, writers)`.

- [x] **4. CLI `--detector`.** `MainTest#detectorChegaAoScanRequest`,
  `#detectorInvalidoSai1`. `ScanCommand` com a opção.

- [x] **5. Contratos + checkpoint.** `docs/contracts.md`. `mvn clean verify` verde. Lab
  parcial: `scan --module sqli --detector both` no DVWA (dois vereditos por caso). Marcar a
  Fase 5 no `build-order` (checkpoint). Marcar passos, commits, push.
  - Feito: `mvn clean verify` com 233 testes verdes. Lab (sem Ollama): `scan --module sqli
    --detector both` no DVWA → scan completa, lado LLM "indisponível" (negativo, não vira
    achado), relatório com o achado heurístico. A ligação dos dois detectores por caso está
    coberta pelos testes do `ScanService`/`MainTest`.

## Desvios do plano

- `ScanService` virou final com construtor novo; `ScanServiceTest` e `MainTest` passaram a
  montar fábricas (`Detector -> VulnModule`) e um mapa de detectores fake.

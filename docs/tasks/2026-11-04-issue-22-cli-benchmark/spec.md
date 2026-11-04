# Spec — Subcomando benchmark na CLI (issue #22)

Referência: issue #22 (Fase 6 de `docs/build-order.md`), `docs/cli.md` e `docs/experiment.md`.
Depende de #21 (`BenchmarkRunner`/`LabCaseRunner`). Fecha a Fase 6.

## Objetivo

Expor o experimento na CLI: `vulnscan benchmark --ground-truth <arquivo> [--runs <n>]
[--out <dir>]`. Roda os dois detectores (heurística e LLM) sobre o gabarito, N vezes, e grava
os CSVs. A CLI só traduz argumentos e delega — a lógica fica no `app`.

## Escopo

### `app/BenchmarkRequest`

`record BenchmarkRequest(Path groundTruth, int runs, Path outDir)` — `runs` nulo/ausente vira
3; `outDir` nulo vira `experiments/results`.

### `app/BenchmarkService`

- `BenchmarkService(CaseRunner runner, List<String> detectors)` (os dois detectores, na ordem
  `heuristic`, `llm`).
- `Path run(BenchmarkRequest request)`:
  - valida: `groundTruth` existe e `runs >= 1`; senão `InvalidScanRequestException` (CLI: 1);
  - carrega o gabarito (`GroundTruth.load`), roda `new BenchmarkRunner(gt, runner, detectors,
    runs).run(outDir)`, devolve o caminho do `summary.csv`.
- Erro de execução (ex.: alvo fora do ar num caso) não aborta — vira linha `ERRO` no runner
  (já tratado na #21). Falha ao ler o gabarito propaga (CLI: 2).

### `app/Container`

- `benchmarkService()`: monta o `LabCaseRunner` (detectores `heuristic` = `HeuristicDetector`,
  `llm` = `LlmDetector` sobre `OllamaClient`; `httpClient`, `browser`, `apiSession`, payloads
  de SQLi/XSS, gerador de marcador) e devolve `new BenchmarkService(labRunner, ["heuristic",
  "llm"])`. Reaproveita os campos já existentes (detectores e payloads saem de métodos comuns).

### CLI `cli/BenchmarkCommand` + registro no `Main`

- `@Command(name = "benchmark")` com `--ground-truth` (obrigatório, `Path`), `--runs`
  (default 3), `--out` (default `experiments/results`), `-h/--help`.
- `call()`: monta `BenchmarkRequest`, chama `main.benchmarkService().run(...)`, imprime
  `Benchmark concluído: <N> execução(ões). Resumo: <summary.csv>`; devolve 0.
  `InvalidScanRequestException` → 1; demais `RuntimeException` → 2 (sem stack trace), como no
  `scan`.
- `Main`: registra `BenchmarkCommand` em `subcommands`; ganha `Supplier<BenchmarkService>`
  (construtor de teste), com o padrão vindo do `Container`.

## Fora do escopo

- `JsonReportWriter`/`HtmlReportWriter` (#23/#24). Subir Ollama (o lado LLM exige; sem ele,
  "indisponível" = não vulnerável). Medir/validar os números (é execução do experimento).

## Contratos tocados (`docs/contracts.md`)

- Novos: `BenchmarkRequest`, `BenchmarkService`, `Container.benchmarkService()`. CLI:
  subcomando `benchmark` (já em `docs/cli.md`).

## Critérios de aceite

1. `BenchmarkServiceTest` (fake `CaseRunner`, `@TempDir`): `run` grava `summary.csv` e os
   `run-<n>.csv`; `runs` default 3; `outDir` default; gabarito inexistente → erro de uso.
2. `BenchmarkCommandTest` (via `CommandLine.execute`, `BenchmarkService` injetado):
   `benchmark --ground-truth <tmp> --runs 2 --out <tmp>` → saída 0 e a mensagem; sem
   `--ground-truth` → saída 1; gabarito inexistente → saída 1 sem stack trace.
3. `mvn clean verify` verde, incluindo `DependencyRuleTest`.
4. Checkpoint (lab, heurístico): `vulnscan benchmark --ground-truth experiments/ground_truth.yaml
   --runs 1` gera `summary.csv` com os três tipos. O lado LLM exige Ollama.

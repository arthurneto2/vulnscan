# Plano — Subcomando benchmark na CLI (issue #22)

Spec: `spec.md` nesta pasta. Branch: `issue-22-cli-benchmark`.
Cada passo: teste → mínimo → refactor.

## Passos

- [x] **1. `BenchmarkRequest` + `BenchmarkService`.** `BenchmarkServiceTest` com `CaseRunner`
  fake e `@TempDir`: grava `summary.csv`/`run-<n>.csv`; defaults de `runs`/`outDir`; gabarito
  inexistente → `InvalidScanRequestException`. `mvn test -Dtest=BenchmarkServiceTest`.

- [x] **2. `Container.benchmarkService()`.** Extrai `detectors()`/payloads reaproveitáveis e
  monta o `LabCaseRunner`. Sem teste de lab aqui (montagem; checkpoint no passo 4).

- [x] **3. `BenchmarkCommand` + `Main`.** `BenchmarkCommandTest`: saída 0 + mensagem; sem
  `--ground-truth` → 1; gabarito inexistente → 1 sem stack trace. Registrar no `Main`.
  Atualizar `docs/contracts.md`.

- [x] **4. Checkpoint.** `mvn clean verify` verde. Lab (heurístico): `benchmark
  --ground-truth experiments/ground_truth.yaml --runs 1` → `summary.csv` dos três tipos.
  Marcar passos, commits, push.
  - Feito: `mvn clean verify` com 256 testes verdes. Checkpoint (lab): `java ...cli.Main
    benchmark --ground-truth experiments/ground_truth.yaml --runs 1` gerou o `summary.csv`
    com os cinco VulnType + TOTAL para os dois detectores. Heurística 8 VP / 6 VN
    (precisão/recall/F1 = 1,0); LLM sem Ollama = "indisponível" (0 VP, VN nos negativos, FN
    nos positivos). O lado LLM real precisa de Ollama no ar.

## Desvios do plano

- O `BenchmarkCommand` (glue de CLI) e seu teste foram escritos juntos (verde direto); o
  TDD red→green está no `BenchmarkService` (passo 1).

# Plano — Subcomando benchmark na CLI (issue #22)

Spec: `spec.md` nesta pasta. Branch: `issue-22-cli-benchmark`.
Cada passo: teste → mínimo → refactor.

## Passos

- [x] **1. `BenchmarkRequest` + `BenchmarkService`.** `BenchmarkServiceTest` com `CaseRunner`
  fake e `@TempDir`: grava `summary.csv`/`run-<n>.csv`; defaults de `runs`/`outDir`; gabarito
  inexistente → `InvalidScanRequestException`. `mvn test -Dtest=BenchmarkServiceTest`.

- [ ] **2. `Container.benchmarkService()`.** Extrai `detectors()`/payloads reaproveitáveis e
  monta o `LabCaseRunner`. Sem teste de lab aqui (montagem; checkpoint no passo 4).

- [ ] **3. `BenchmarkCommand` + `Main`.** `BenchmarkCommandTest`: saída 0 + mensagem; sem
  `--ground-truth` → 1; gabarito inexistente → 1 sem stack trace. Registrar no `Main`.
  Atualizar `docs/contracts.md`.

- [ ] **4. Checkpoint.** `mvn clean verify` verde. Lab (heurístico): `benchmark
  --ground-truth experiments/ground_truth.yaml --runs 1` → `summary.csv` dos três tipos.
  Marcar passos, commits, push.

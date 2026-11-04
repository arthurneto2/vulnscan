# Plano — BenchmarkRunner (issue #21)

Spec: `spec.md` nesta pasta. Branch: `issue-21-benchmark-runner`.
Pacote: `br.ufv.vulnscan.app.benchmark`. Cada passo: teste → mínimo → refactor.

## Passos

- [x] **1. Métricas (puro).** `MetricsTest`: `Outcome.of`, precisão/recall/F1/FPR (inclui
  denominador 0), agregação por tipo/total. Green: `Outcome`, `ConfusionMatrix`, `Metrics`.

- [x] **2. Gabarito em records.** `GroundTruthLoaderTest` lê `experiments/ground_truth.yaml`.
  Green: records + `load(Path)` (Jackson YAML). Adicionar `auth_mode` ao YAML e ao doc.

- [x] **3. BenchmarkRunner + CSVs.** `BenchmarkRunnerTest` com `CaseRunner` fake e `@TempDir`:
  `run-<n>.csv` e `summary.csv`, contagens/métricas/linhas; erro vira `outcome=ERRO`.
  Green: `CaseRunner`/`CaseResult`, `BenchmarkRunner`, escrita de CSV.

- [ ] **4. LabCaseRunner.** `LabCaseRunnerTest` (offline): derivação do ponto a partir do
  `location`; escolha do módulo por tipo (sem rede). Green: `LabCaseRunner` + `DvwaSession`.

- [ ] **5. Contratos + checkpoint.** `docs/contracts.md`. `mvn clean verify`. Checkpoint de
  lab (heurístico): gerar `summary.csv` dos três tipos. Marcar passos, commits, push.

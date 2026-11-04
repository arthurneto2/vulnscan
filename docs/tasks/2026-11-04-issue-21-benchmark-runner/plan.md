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

- [x] **4. LabCaseRunner.** `LabCaseRunnerTest` (offline): derivação do ponto a partir do
  `location`; escolha do módulo por tipo (sem rede). Green: `LabCaseRunner` + `DvwaSession`.

- [x] **5. Contratos + checkpoint.** `docs/contracts.md`. `mvn clean verify`. Checkpoint de
  lab (heurístico): gerar `summary.csv` dos três tipos. Marcar passos, commits, push.
  - Feito: `mvn clean verify` com 250 testes verdes. Checkpoint de lab (heurístico, via
    runner de scratch, sem CLI ainda): `summary.csv` com os cinco VulnType + TOTAL; a
    heurística fez 8 VP / 6 VN / 0 FP / 0 FN (precisão/recall/F1 = 1,0). CSVs em
    `experiments/results/` (gitignored). O lado LLM precisa de Ollama.

## Desvios do plano

- `predictedVulnerable` = módulo reportou achado (aprovado): a matriz só precisa do booleano;
  a confiança no CSV vem do primeiro achado (0 quando negativo).

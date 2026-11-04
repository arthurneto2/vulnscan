# Spec — BenchmarkRunner: métricas e CSVs (issue #21)

Referência: issue #21 (Fase 6 de `docs/build-order.md`) e `docs/experiment.md`. Depende de
#19 (detectores) e #20 (gabarito). O subcomando `benchmark` da CLI é a #22.

## Objetivo

Rodar cada caso do gabarito com cada detector, N vezes, comparar com a verdade, acumular
VP/FP/VN/FN por tipo e no total, calcular as métricas e gravar os CSVs. O **cálculo de
métricas** é puro e testado com veredictos fixos; a execução real dos casos fica atrás de uma
costura (`CaseRunner`), com uma implementação de laboratório.

## Pacote

`br.ufv.vulnscan.app.benchmark` (camada de aplicação; pode usar Jackson/Selenium como os
demais adaptadores via os módulos).

## Escopo

### 1. Modelo do gabarito + carregador (`GroundTruth`)

- `record GroundTruth(List<TargetCases> targets)`, `record TargetCases(String name, URI
  baseUrl, List<Case> cases)`,
  `record Case(String id, VulnType type, String location, String security, String authMode,
  boolean vulnerable, String notes)` (campos opcionais nulos).
- `static GroundTruth load(Path yaml)` com Jackson YAML. Campo novo opcional `auth_mode`
  (NONE | USER_TOKEN | USER_TOKEN_OTHER_RESOURCE) para os casos `BROKEN_*` — necessário para
  distinguir os dois `/api/Users` (sem token × usuário comum). Acrescentado ao gabarito e ao
  `docs/ground-truth.md`.

### 2. Métricas (puro, testável) — `Outcome`, `ConfusionMatrix`, `Metrics`

- `enum Outcome { TP, FP, TN, FN }`; `Outcome.of(boolean esperado, boolean previsto)`.
- `ConfusionMatrix` acumula contagens; `precision`, `recall`, `f1`, `falsePositiveRate`
  (denominador 0 → 0.0). `Metrics` agrega por `VulnType` e total, por detector.

### 3. Costura de execução — `CaseRunner`

```java
interface CaseRunner {
    // roda o caso com o detector e devolve o veredito previsto (positivo ou negativo) e o tempo
    CaseResult run(GroundTruth.Case c, URI baseUrl, String detectorName);
    record CaseResult(boolean predictedVulnerable, double confidence, long elapsedMillis) {}
}
```

- O previsto é "o módulo do tipo, com aquele detector e um ponto derivado do `location`,
  reportou achado?" — assim reaproveita os módulos (que já encapsulam Evidence + judge) e
  captura também os negativos (lista vazia = não vulnerável).

### 4. `BenchmarkRunner`

- `BenchmarkRunner(GroundTruth gt, CaseRunner runner, List<String> detectors, int runs, Clock?)`.
- `run(Path outDir)`: para `n` em 1..runs, para cada caso, para cada detector: chama o
  runner, classifica (`Outcome.of(caso.vulnerable, resultado.predicted)`), grava a linha;
  escreve `run-<n>.csv`. Acumula no `Metrics`; ao fim escreve `summary.csv`. Devolve o
  `Metrics` (para teste). Erros de um caso não derrubam o benchmark: viram linha com
  `outcome=ERRO` e seguem (registrado).
- CSVs:
  - `run-<n>.csv`: `run,caso,tipo,detector,esperado,previsto,confianca,outcome,tempo_ms`.
  - `summary.csv`: por `(tipo|TOTAL) × detector`: `tp,fp,tn,fn,precisao,recall,f1,fpr,
    tempo_medio_ms`.

### 5. `LabCaseRunner implements CaseRunner` (execução real)

- Mapa `detectorName -> Detector` e os colaboradores (HttpClient, XssBrowser, ApiSession,
  payloads). Por caso, monta um módulo de um ponto só a partir do `location`/`type`:
  - `SQL_INJECTION` → `SqlInjectionModule` com o ponto derivado;
  - `XSS_REFLECTED` com `#` no location → `DomXssModule`; senão `XssModule`;
  - `XSS_STORED` → `StoredXssModule` (guestbook: campos fixos; submit=view=location);
  - `BROKEN_*` → `ApiAuthModule` com um `AuthProbe` (modo = `authMode`).
- DVWA: loga uma vez por nível (`security`) e usa `Cookie: PHPSESSID=…; security=<nível>`
  (`DvwaSession`, regex do `user_token`, como o `JuiceShopSession` usa Jackson). Juice Shop:
  o próprio `ApiSession`/sem auth. `predictedVulnerable` = módulo reportou achado.

## Fora do escopo

- Subcomando `benchmark` na CLI (#22). Subir Ollama (o lado LLM exige; sem ele, veredito
  "indisponível" = não vulnerável). `JsonReportWriter`/`HtmlReportWriter` (Fase 7).

## Contratos tocados

- `docs/ground-truth.md`: campo opcional `auth_mode`. `docs/contracts.md`: seção nova do
  benchmark (`GroundTruth`, `CaseRunner`, `BenchmarkRunner`). Sem mudança nos contratos do
  domínio.

## Critérios de aceite

1. `MetricsTest` (puro): `Outcome.of`; precisão/recall/F1/FPR com casos fixos, inclusive
   denominador 0 → 0.0; agregação por tipo e total.
2. `BenchmarkRunnerTest` com `CaseRunner` **fake** (veredictos fixos) e `@TempDir`: grava
   `run-1.csv`..`run-N.csv` e `summary.csv`; conteúdo confere (contagens, métricas, linhas
   por caso×detector×run); erro de um caso vira `outcome=ERRO` sem abortar.
3. `GroundTruthLoaderTest`: carrega o `ground_truth.yaml` real em records; `auth_mode` lido.
4. `LabCaseRunner`: testado no que dá sem rede (derivação de ponto a partir do `location`;
   escolha do módulo por tipo). A execução contra o lab é manual (checkpoint).
5. `mvn clean verify` verde, incluindo `DependencyRuleTest`.
6. Checkpoint (lab, lado heurístico): `summary.csv` com matriz e métricas dos três tipos.
   O lado LLM exige Ollama (execução manual).

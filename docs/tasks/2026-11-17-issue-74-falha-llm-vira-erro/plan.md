# Plano — Falha da chamada ao LLM vira ERRO no benchmark (issue #74)

Spec: `spec.md` nesta pasta. Branch: `issue-74-falha-llm-vira-erro` (a partir de `main` em
`33bde8b`). Cada passo: teste primeiro (red pelo motivo esperado), código mínimo (green),
refactor. Mudou contrato → `docs/contracts.md` no mesmo passo.

## Passos

- [x] **1. `LlmDetector.unavailable(Verdict)` (red → green).**
  Testes em `LlmDetectorTest`: `falhaDoClienteEhIndisponivel`,
  `respostaNaoParseavelNaoEhIndisponivel`, `vereditoNormalNaoEhIndisponivel`,
  `outroDetectorNaoEhIndisponivel`.
  Red: método stub devolvendo `false` → o 1º falha na asserção.
  Green: constante `UNAVAILABLE`, usada no `judge`; `unavailable` checa detector `llm` e prefixo.
  Contrato em `docs/contracts.md`. Verificar: `mvn test -Dtest=LlmDetectorTest`.

- [x] **2. `RecordingDetector` e a regra no `LabCaseRunner` (red → green).**
  Testes em `LabCaseRunnerTest`: `llmIndisponivelSemAchadoViraErro`,
  `umaChamadaIndisponivelSemAchadoTambemViraErro`, `llmIndisponivelComAchadoVale`, `negativoNormalContinuaNegativo`. Caso de SQLi do Juice Shop
  (sem login), `HttpClient` falso, dois payloads, detector `llm` falso com vereditos roteirizados.
  Red: hoje o 1º devolve `CaseResult(false, …)` em vez de lançar.
  Green: decorador `RecordingDetector` (package-private) que guarda os vereditos; o `run`
  confere: sem achado e `k > 0` indisponíveis → `IllegalStateException("LLM indisponível em k
  de n chamadas: <justificativa da 1ª>")`.
  Verificar: `mvn test -Dtest=LabCaseRunnerTest`.

- [x] **3. Coluna `erro` no `run-<n>.csv` (red → green).**
  Teste: `BenchmarkRunnerTest#erroGravaOMotivoSemVirgula`.
  Green: cabeçalho `…,tempo_ms,erro`; linha normal com `erro` vazio; linha `ERRO` com a
  mensagem saneada (`,` → `;`, `\r`/`\n` → espaço, máx. 200 caracteres; mensagem nula → nome
  da exceção).

- [x] **4. Coluna `erros` no `summary.csv` (red → green).**
  Teste: `BenchmarkRunnerTest#resumoContaErrosPorTipoETotal`.
  Green: contagem detector → (tipo|TOTAL) → erros; a linha `ERRO` também cria a matriz do
  tipo (zerada) para ele aparecer no resumo.
  Contrato (formato dos CSVs) em `docs/contracts.md`.
  Verificar: `mvn test -Dtest=BenchmarkRunnerTest`.

- [x] **5. Docs.** `docs/experiment.md` ("Saída": colunas `erro` e `erros`, o que é `ERRO`;
  limitação: rodadas antes da #74 contavam indisponibilidade como FN/VN — inclusive os 2 FN
  rápidos do checkpoint da #60), `docs/llm-contract.md` (linha "Erro do provedor…": no
  benchmark vira `ERRO`), README se citar as colunas.

- [ ] **6. `mvn clean verify`.**

- [ ] **7. Checkpoint empírico** (lab real, Ollama **parado**, sem tráfego externo):
  `java -jar target/vulnscan.jar benchmark --ground-truth experiments/ground_truth.yaml
  --runs 1 --reset --llm ollama --out experiments/results/2026-11-17-issue-74-ollama-parado`.
  Conferir: linhas `llm` sem achado → `ERRO` com `LLM indisponível` na coluna `erro`;
  `heuristic` com F1 1.000; `erros` no `TOTAL` do `llm` igual ao número de linhas `ERRO`.

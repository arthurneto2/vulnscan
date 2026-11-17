# Plano — Negativo de XSS de DOM e codificação de navegador no DomXssModule (issue #77)

Spec: `spec.md` nesta pasta. Branch: `issue-77-xss-dom-negativo` (a partir de `main` em
`905d7b2`). Cada passo: teste primeiro (red pelo motivo esperado), código mínimo (green),
refactor. Mudou contrato → `docs/contracts.md` no mesmo passo.

## Passos

- [x] **1. `InjectionPoint.browserUrl` (red → green).**
  Testes em `InjectionPointTest`: `browserUrlCodificaComoONavegador`,
  `browserUrlCodificaEspacoAspasENaoAscii`, `urlContinuaComoFormulario`.
  Red: stub que delega ao `url` → falha na asserção (`%2F`, `+`).
  Green: codificador próprio (bytes UTF-8; percent-encode só o conjunto da spec, hex
  maiúsculo). Contrato em `docs/contracts.md`.
  Verificar: `mvn test -Dtest=InjectionPointTest`.

- [x] **2. `DomXssModule` usa `browserUrl` (red → green).**
  Ajustar `DomXssModuleTest#abreUrlComFragmentoEMarcador` para a URL nova (red: o módulo
  ainda usa `url`). Green: trocar a chamada. Javadoc do módulo cita a codificação.
  Verificar: `mvn test -Dtest=DomXssModuleTest`.

- [x] **3. Gabarito com o par de DOM do DVWA (red → green).**
  Teste: `GroundTruthFileTest#xssDeDomTemPositivoENegativo` (red: só há o positivo do Juice
  Shop). Green: os dois casos da spec no `experiments/ground_truth.yaml`.
  Verificar: `mvn test -Dtest=GroundTruthFileTest`.

- [x] **4. Docs.** `docs/experiment.md` (limitação do DOM: o que mudou e desde quando;
  rodadas anteriores com 14 casos e `URLEncoder` no DOM), `docs/ground-truth.md` (exemplo de
  DOM com fragmento), `docs/contracts.md` (já no passo 1).

- [x] **5. `mvn clean verify`.** 375 testes, 0 falhas, BUILD SUCCESS.

- [x] **6. Checkpoint empírico** (lab real, na tomada, `systemd-inhibit`):
  1. `benchmark --ground-truth <rascunho com os 2 casos do DVWA> --runs 1 --reset` (Ollama
     parado: `llm` sai `ERRO`, já esperado) → heurística: low **TP**, impossible **TN**.
  2. `benchmark --ground-truth experiments/ground_truth.yaml --runs 1 --reset --llm api
     --out experiments/results/2026-11-17-issue-77-dom` → heurística F1 1.000 (16 casos),
     `js-xss-dom-search` TP nos dois detectores, `erros` = 0; anotar o resultado do LLM nos
     dois casos novos.

  **Feito (2026-11-17, commit `182b413`):**
  1. Rascunho com os 2 casos do DVWA, `--reset`: heurística **TP** no low (antes da correção,
     FN) e **TN** no impossible.
  2. Gabarito completo via API (`experiments/results/2026-11-17-issue-77-dom/`): heurística
     F1 1.000 nos 16 casos; `js-xss-dom-search` TP nos dois detectores. Mas **10 de 16 casos
     do `llm` saíram `ERRO` por `429 Too Many Requests`** (a coluna `erro` da #74 mostrou o
     motivo), inclusive os dois casos novos. A rodada não serve como medida do LLM e confirma
     a necessidade de retry em 429 (fora do escopo da #74).
  3. Desvio: para medir o LLM nos casos de DOM sem depender da API, os 3 casos de DOM com o
     Qwen 14B local (`--runs 2 --reset`, `experiments/results/2026-11-17-issue-77-dom-ollama/`,
     20/20 chamadas com `200`, `erros` = 0):
     - `dvwa-xss-dom-impossible`: heurística TN 2/2, **Qwen TN 2/2 (nenhum FP de DOM)**;
     - `js-xss-dom-search`: TP 2/2 nos dois;
     - `dvwa-xss-dom-low`: heurística TP 2/2, **Qwen FN 2/2**. A heurística detecta pelo
       diálogo capturado (pseudo-header `x-vulnscan-dialogs`); o header vai no prompt, mas o
       prompt não diz o que ele significa, e o trecho do corpo começa no `value` codificado
       (`%3Cscript%3E…`). Achado do experimento, não defeito desta task; mexer no prompt seria
       ajuste informado pelo gabarito (decisão do usuário).


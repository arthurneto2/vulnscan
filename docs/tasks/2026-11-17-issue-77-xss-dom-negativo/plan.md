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

- [ ] **5. `mvn clean verify`.**

- [ ] **6. Checkpoint empírico** (lab real, na tomada, `systemd-inhibit`):
  1. `benchmark --ground-truth <rascunho com os 2 casos do DVWA> --runs 1 --reset` (Ollama
     parado: `llm` sai `ERRO`, já esperado) → heurística: low **TP**, impossible **TN**.
  2. `benchmark --ground-truth experiments/ground_truth.yaml --runs 1 --reset --llm api
     --out experiments/results/2026-11-17-issue-77-dom` → heurística F1 1.000 (16 casos),
     `js-xss-dom-search` TP nos dois detectores, `erros` = 0; anotar o resultado do LLM nos
     dois casos novos.

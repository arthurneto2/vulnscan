# Plano — Diálogos do navegador explicados no prompt e aceitos no grounding (issue #81)

Spec: `spec.md` nesta pasta. Branch: `issue-81-dialogos-no-prompt` (a partir de `main` em
`b97d202`; o PR #80, da #79, ainda não estava mergeado e não toca no `LlmDetector`). Cada
passo: teste primeiro (red pelo motivo esperado), código mínimo (green), refactor.

## Passos

- [ ] **1. Linha `Browser dialogs` no prompt (red → green).**
  Testes: `promptMostraDialogosQuandoHouve`, `promptSemDialogosNaoTemALinha`.
  Red: a linha não existe (asserção). Green: a linha só quando há
  `HttpResponse.DIALOGS_HEADER`, com a explicação.
  Verificar: `mvn test -Dtest=LlmDetectorTest`.

- [ ] **2. Critério de XSS e instrução do `evidence` (red → green).**
  Teste: `criterioDeXssFalaDoDialogoComMarcador`.
  Green: frase do diálogo com marcador no critério de XSS; `evidence` "from the Response body
  or the Browser dialogs line".

- [ ] **3. Grounding aceita os diálogos (red → green).**
  Testes: `evidenciaCitadaDoDialogoPassaNoGrounding` (red: hoje rebaixa),
  `evidenciaForaDoCorpoEDosDialogosContinuaRebaixada` (regressão).
  Green: a citação vale se estiver no corpo normalizado ou nos diálogos normalizados.
  Refactor: Javadoc da classe (grounding).

- [ ] **4. Docs.** `docs/llm-contract.md` (prompt, `evidence` e grounding),
  `docs/experiment.md` (limitação com o antes e o depois), `docs/contracts.md` se preciso.

- [ ] **5. `mvn clean verify`.**

- [ ] **6. Checkpoint empírico** (lab real, `systemd-inhibit`): gabarito de subconjunto com os
  3 casos de DOM e os 2 de XSS armazenado do DVWA, `--runs 2 --reset`, com o Qwen 14B
  (Ollama com `LLAMA_ARG_CACHE_RAM=0`) e com o Ultra (`--llm api`). Conferir:
  `dvwa-xss-dom-low` TP; negativos de navegador TN; `erros` = 0 ou anotado.

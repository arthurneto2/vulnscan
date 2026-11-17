# Plano — Diálogos do navegador explicados no prompt e aceitos no grounding (issue #81)

Spec: `spec.md` nesta pasta. Branch: `issue-81-dialogos-no-prompt` (rebaseada no `main` em
`350455e`, com o PR #80 da #79). Cada
passo: teste primeiro (red pelo motivo esperado), código mínimo (green), refactor.

## Passos

- [x] **1. Linha `Browser dialogs` no prompt (red → green).**
  Testes: `promptMostraDialogosQuandoHouve`, `promptSemDialogosNaoTemALinha`.
  Red: a linha não existe (asserção). Green: a linha só quando há
  `HttpResponse.DIALOGS_HEADER`, com a explicação.
  Verificar: `mvn test -Dtest=LlmDetectorTest`.

- [x] **2. Critério de XSS e instrução do `evidence` (red → green).**
  Teste: `criterioDeXssFalaDoDialogoComMarcador`.
  Green: frase do diálogo com marcador no critério de XSS; `evidence` "from the Response body
  or the Browser dialogs line".

- [x] **3. Grounding aceita os diálogos (red → green).**
  Testes: `evidenciaCitadaDoDialogoPassaNoGrounding` (red: hoje rebaixa),
  `evidenciaForaDoCorpoEDosDialogosContinuaRebaixada` (regressão).
  Green: a citação vale se estiver no corpo normalizado ou nos diálogos normalizados.
  Refactor: Javadoc da classe (grounding).

- [x] **4. Docs.** `docs/llm-contract.md` (prompt, `evidence` e grounding),
  `docs/experiment.md` (limitação com o antes e o depois), `docs/contracts.md` se preciso.

- [x] **5. `mvn clean verify`.** 387 testes, 0 falhas, BUILD SUCCESS.

- [x] **6. Checkpoint empírico** (lab real, `systemd-inhibit`): gabarito de subconjunto com os
  3 casos de DOM e os 2 de XSS armazenado do DVWA, `--runs 2 --reset`, com o Qwen 14B
  (Ollama com `LLAMA_ARG_CACHE_RAM=0`) e com o Ultra (`--llm api`). Conferir:
  `dvwa-xss-dom-low` TP; negativos de navegador TN; `erros` = 0 ou anotado.

  **Feito (2026-11-17, commit `33666ed`):** `experiments/results/2026-11-17-issue-81-qwen/`
  (8,5 min) e `…-81-ultra/` (7 min), `erros` = 0 nos dois.

  | Caso | Antes (Qwen / Ultra) | Depois: Qwen 2 exec. | Depois: Ultra 2 exec. |
  |---|---|---|---|
  | `dvwa-xss-dom-low` | FN 2/2 / FN 1/1 | **TP 2/2** | **TP 2/2** |
  | `dvwa-xss-dom-impossible` (neg.) | TN 2/2 / TN 1/1 | TN 2/2 | TN 2/2 |
  | `dvwa-xss-stored-low` | — | TP 2/2 | TP 2/2 |
  | `dvwa-xss-stored-impossible` (neg.) | — | TN 2/2 | TN 2/2 |
  | `js-xss-dom-search` | TP / TP | TP 2/2 | TP 2/2 |

  O LLM empata com a heurística nos 5 casos de navegador (F1 1.000, 0 FP), sem FP novo nos
  negativos de navegador (controle).


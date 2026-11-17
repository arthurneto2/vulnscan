# Plano — Instrução do `evidence` sem diálogos volta ao texto antigo (issue #83)

- Spec: `spec.md` nesta pasta.
- Branch: `issue-83-evidence-sem-dialogos`, a partir do `main` em `5578922` (com o PR #82).
- Cada passo segue o ciclo: red pelo motivo esperado → green → refactor.

## Passos

- [x] **1. Texto antigo sem diálogos (red → green).**
  - Teste: `promptSemDialogosUsaInstrucaoDeEvidenciaAntiga`.
  - Red: falha na asserção, porque o prompt atual tem o texto da #81.
  - Green: o texto do `evidence` passa a ser escolhido pela presença do header de diálogos,
    com o texto antigo exato quando o header não existe.
  - Verificar: `mvn test -Dtest=LlmDetectorTest`.

- [x] **2. Texto com diálogos (regressão).**
  - Teste: `promptComDialogosUsaInstrucaoDeEvidenciaComDialogos`.
  - Deve passar de primeira.
  - Refactor: constantes nomeadas para os dois textos, e Javadoc do motivo (#83).

- [x] **3. Docs.**
  - `docs/llm-contract.md`: seção da saída (`evidence`).
  - `docs/experiment.md`: limitação "Ajuste informado pelo gabarito", com a regressão e o A/B.

- [x] **4. `mvn clean verify`.** 389 testes, 0 falhas, BUILD SUCCESS.

- [ ] **5. Checkpoint empírico** (lab real, `systemd-inhibit`, Ollama com
  `LLAMA_ARG_CACHE_RAM=0`):
  - 5 casos de API do Juice Shop, `--runs 3 --reset`;
  - 5 casos de navegador da #81, `--runs 2 --reset`;
  - comparar com o A/B (pré-#81 9/9 TP; pós-#81 2/9 TP) e com o checkpoint da #81.

## Desvios

- **Passo 2.** A asserção `or from the Browser dialogs line` em
  `criterioDeXssFalaDoDialogoComMarcador` (#81) saiu. Ela exigia o texto novo do `evidence`
  num prompt sem diálogos, que é justamente o comportamento que a #83 desfaz. O texto
  continua coberto, agora com diálogos, por `promptComDialogosUsaInstrucaoDeEvidenciaComDialogos`.

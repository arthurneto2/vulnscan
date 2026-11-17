# Spec — Instrução do `evidence` volta ao texto antigo quando não há diálogos (issue #83)

## Objetivo

Desfazer a regressão que a #81 causou no Qwen 14B nos casos de API. A instrução do `evidence`
passou a citar a linha `Browser dialogs` e a dizer "if nothing supports a vulnerability" em
**todo** prompt, inclusive nos casos sem navegador, que nunca têm essa linha. Com isso, o Qwen
passou a responder `vulnerable: false` (confiança 0.0) nos positivos de autenticação e
autorização.

A/B nos 5 casos de API do Juice Shop, com o Qwen, `--runs 3 --reset`:

| | positivos | F1 do LLM |
|---|---|---|
| antes da #81 | 9/9 TP | 1.000 |
| depois da #81 | 2/9 TP | 0.364 |

Nos dois casos, os negativos ficaram em 6/6 TN. Os dados estão em
`experiments/results/2026-11-17-ab-81-api/` (pasta local, fora do git).

## Escopo

1. **Sem a linha `Browser dialogs`**: a instrução do `evidence` volta ao texto de antes da #81,
   idêntico byte a byte:
   `copied character-for-character from the Response body above, or an empty string if nothing
   in the body supports a vulnerability`.
2. **Com diálogos**: o texto da #81 continua:
   `... from the Response body above or from the Browser dialogs line, or an empty string if
   nothing supports a vulnerability`.
3. Docs:
   - `docs/llm-contract.md`: o texto do `evidence` depende da presença de diálogos;
   - `docs/experiment.md`: na limitação "Ajuste informado pelo gabarito", a regressão da #81 e
     a correção, com o A/B.

### Fora do escopo

- O grounding e a linha `Browser dialogs` da #81 não mudam.
- A frase do diálogo no critério de XSS também fica. Ela aparece em todo prompt de XSS, e o
  XSS seguiu 100% no Qwen e no Ultra na rodada pós-#81. Mexer nela agora acrescentaria uma
  variável sem evidência de problema.
- A rodada de referência completa do Qwen vem depois do merge e é registrada à parte.

## Contratos tocados

Nenhuma assinatura muda. O que muda é o comportamento do prompt do `LlmDetector`, descrito em
`docs/llm-contract.md`.

## Critérios de aceite

1. `LlmDetectorTest#promptSemDialogosUsaInstrucaoDeEvidenciaAntiga`: sem o header de diálogos,
   o prompt contém o texto antigo exato e não contém `from the Browser dialogs line`.
2. `LlmDetectorTest#promptComDialogosUsaInstrucaoDeEvidenciaComDialogos`: com o header, o
   prompt contém `from the Browser dialogs line`.
3. Os testes atuais seguem verdes, incluindo os da #81. `mvn clean verify` fica verde.
4. **Checkpoint empírico** (lab real, Qwen 14B, `--runs 3 --reset`):
   - os 5 casos de API do Juice Shop: volta a 9/9 TP e 6/6 TN, ou próximo da linha de base
     pré-#81;
   - o subconjunto de navegador da #81 (5 casos, `--runs 2`): `dvwa-xss-dom-low` continua TP e
     os negativos continuam TN.

## Referências

- Issue #83; PR #82 (#81); rodada `experiments/results/2026-11-17-ref-ollama-qwen14b/`.

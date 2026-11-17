# Spec — Diálogos do navegador explicados no prompt e aceitos no grounding (issue #81)

## Objetivo

Corrigir uma assimetria de informação entre os detectores. Nos casos de XSS abertos no
navegador, a prova de execução é o diálogo JavaScript capturado (pseudo-header
`x-vulnscan-dialogs`). A heurística conhece o significado desse sinal; o LLM recebe o header
cru, sem explicação, com um critério de XSS que manda olhar só `<`/`>` no corpo, e um grounding
que só aceita citação do corpo. Resultado: `dvwa-xss-dom-low` deu FN nos dois modelos (Qwen
2/2, Ultra 1/1), sem erro de API, com a heurística em TP.

## Escopo

1. **Linha própria no prompt**, só quando a resposta tem o pseudo-header de diálogos:
   `Browser dialogs: <textos>` — "JavaScript alert/confirm/prompt dialogs that actually fired
   while a real browser rendered this page (captured by the scanner)". Sem diálogo, a linha não
   aparece. Nas respostas HTTP comuns não houve navegador, então dizer "nenhum diálogo" seria
   enganoso.
2. **Critério de XSS** (`XSS_REFLECTED`, `XSS_STORED`) ganha uma frase: um diálogo cujo texto
   contém o marcador único do payload prova que o payload executou no navegador → vulnerável,
   mesmo que outras cópias no corpo pareçam codificadas ou escapadas. O resto do critério (a
   regra do escape `&lt;`/`&gt;`) fica igual.
3. **Instrução do `evidence`**: o trecho pode ser copiado do corpo **ou** da linha
   `Browser dialogs`.
4. **Grounding**: a citação vale se estiver no corpo normalizado **ou** no texto dos diálogos
   (mesma normalização). Regra genérica, igual para todo tipo; em tipo sem navegador não há
   diálogo e nada muda.
5. Docs: `docs/llm-contract.md` (prompt, saída e grounding), `docs/experiment.md` (limitação:
   ajuste motivado por um caso do gabarito, com o antes e o depois, como na #72),
   `docs/contracts.md` se citar o grounding.

### Fora do escopo

- Mudar o `HeuristicDetector`, o `RenderedPage` ou o nome do pseudo-header.
- Tirar o header da linha `Response headers` (fica duplicado, mas inofensivo).
- Rodadas de referência completas: são a próxima task.

## Contratos tocados

- Nenhuma assinatura muda. Comportamento do `LlmDetector` (prompt e grounding) descrito em
  `docs/llm-contract.md`.

## Critérios de aceite (viram testes em `LlmDetectorTest`)

1. `promptMostraDialogosQuandoHouve`: resposta com `x-vulnscan-dialogs: vsx1a2b3c4d` → o
   prompt tem a linha `Browser dialogs: vsx1a2b3c4d` e a explicação ("actually fired").
2. `promptSemDialogosNaoTemALinha`: resposta sem o header → o prompt não tem
   `Browser dialogs:`.
3. `criterioDeXssFalaDoDialogoComMarcador`: o prompt de XSS cita o diálogo com o marcador; o
   de SQLi não.
4. `evidenciaCitadaDoDialogoPassaNoGrounding`: corpo sem o marcador, diálogo `vsx1a2b3c4d`, o
   modelo responde `vulnerable: true` com `evidence: "vsx1a2b3c4d"` → veredito mantido.
5. `evidenciaForaDoCorpoEDosDialogosContinuaRebaixada`: com diálogo `vsx1`, evidência
   `"outra coisa"` → rebaixado (`sem evidência`, 0.2).
6. Testes atuais do grounding e do prompt seguem verdes.
7. `mvn clean verify` verde.
8. **Checkpoint empírico** (lab real, `--reset`): subconjunto de XSS no navegador (3 casos de
   DOM e os 2 de XSS armazenado do DVWA), com o Qwen 14B (`--runs 2`) e o Ultra via API
   (`--runs 2`). Esperado: `dvwa-xss-dom-low` passa a TP; os negativos de navegador
   (`dvwa-xss-dom-impossible`, `dvwa-xss-stored-impossible`) continuam TN; `erros` = 0 (ou
   anotado). Registrar o antes e o depois.

## Referências

- Issue #81; checkpoints da #77 (Qwen) e da #79 (Ultra); limitação "Ajuste informado pelo
  gabarito" em `docs/experiment.md`.

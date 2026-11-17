# Plano — Janela do corpo centrada no payload no prompt do LLM (issue #72)

Spec: `spec.md` nesta pasta. Branch: `issue-72-janela-do-payload` (a partir de `main`).
O PR #71 (resultados da #68) mexe em `docs/experiment.md` e ainda não foi mergeado; o
passo 8 só começa depois dele, para não dar conflito.

## Passos

- [x] **1. Payload literal depois dos 4 KB (red → green).**
  Teste primeiro: `LlmDetectorTest#corpoGrandeMostraJanelaDoPayload`. Corpo = 10.000 `A` +
  `PERTO<img src=x onerror=alert('vsx1a2b3c4d')>` + 10.000 `B`. Espera: o prompt contém
  `PERTO<img src=x onerror=alert('vsx1a2b3c4d')>` e `caracteres omitidos`; tem menos de
  10.000 caracteres.
  Red: hoje o prompt só tem `A` (falha na asserção).
  Green: âncora pela ocorrência literal de `payload.value()` e janela de 4.096 que começa
  2.048 antes, com as marcas.
  Verificar: `mvn test -Dtest=LlmDetectorTest`. (O helper do teste se chama
  `promptWithBody`, porque `promptFor(VulnType)` já existia.)

- [x] **2. Payload escapado: âncora pelo token mais longo (red → green).**
  Teste: `corpoGrandeComPayloadEscapadoAchaPeloMarcador`. Payload
  `<script>alert('vsx9f8e7d6c')</script>`; corpo = 10.000 `A` +
  `&lt;script&gt;alert('vsx9f8e7d6c')&lt;/script&gt;` + 10.000 `B`. Espera: o prompt traz o
  trecho escapado.
  Green: tokens alfanuméricos com 6 ou mais caracteres, do mais longo para o mais curto.
  O refactor do passo 5 (`bodyExcerpt` e `anchor`) foi feito junto com este green.

- [x] **3. Sem âncora: começo do corpo, como hoje (teste de regressão).**
  Acrescentar ao `promptCitaTipoPayloadRequisicaoETruncaCorpo` a asserção de que não há
  `caracteres omitidos`. Passou de primeira, como esperado (regressão).

- [x] **4. Payload perto do fim (red → green).**
  Teste: `payloadPertoDoFimUsaJanelaInteiraAteOFim`. Corpo = 10.000 `A` + payload + `FIM`.
  Espera: `[… (tamanho − 4096) caracteres omitidos …]` (janela inteira de 4 KB terminando no
  fim) e nada de `[truncado]` depois do `FIM`. Red na primeira asserção.
  Green: ajuste do início da janela para `min(âncora − 2048, tamanho − 4096)`.

- [x] **5. Refactor.** Extrair a seleção para um método privado estático (ex.:
  `bodyExcerpt(String body, String payload)`), com Javadoc. `mvn test` verde.

- [x] **6. Docs.** `docs/llm-contract.md` (item 3 e a regra "Não mande a resposta HTTP
  inteira…") e a linha do CLAUDE.md sobre o corte de ~4 KB passam a descrever a janela.

- [x] **7. `mvn clean verify`.** 342 testes, 0 falhas, BUILD SUCCESS.

- [x] **7a. Regressão achada no checkpoint (red → green).** Rodada 1 da #72: Ultra FN 3/3
  em `js-auth-admin-config` (antes TP 3/3). Causa: o token `configuration` do path ancorou
  numa descrição de produto (posição 8.354 de 36 mil); o começo do JSON saiu do prompt.
  Teste: `LlmDetectorTest#palavraComumDoPayloadNaoDeslocaAJanela`. Payload
  `/rest/admin/application-configuration`; corpo = `{"config":` + 8.000 `X` +
  `SSL configurations` + 8.000 `Y`. Espera: o prompt traz `{"config":` e não traz
  `caracteres omitidos`. Green: o token-âncora precisa de letra e dígito. Depois, refazer
  as rodadas (passo 8.2); a rodada 1 fica registrada como descartada.
  Feito: red na asserção do começo do corpo; green com o `TOKEN` exigindo letra e dígito;
  `mvn clean verify` com 343 testes, 0 falhas. `docs/llm-contract.md` atualizado.

- [x] **8. Checkpoint empírico** (depois do merge do #71, feito).
  1. Harness da issue (`jshell` com o jar novo, **sem** reescrever o prompt): XSS de DOM
     com `vulnerable: true` em pelo menos um payload.
     - **Feito (2026-11-16):** `true` em 4 de 5 payloads (antes, 0 de 5), confiança 1.0,
       evidência passando no grounding. O `false` é `xss-attr-break-svg`: o DOM serializado
       mostra o `">` inicial como `"&gt;`, e o modelo leu isso como escape.
  2. Rodadas: Qwen 14B (Ollama com `LLAMA_ARG_CACHE_RAM=0`) e Ultra (API), 3 execuções de
     `--runs 1` cada, reset do lab antes de cada uma, tudo dentro de
     `systemd-inhibit --what=sleep:idle:handle-lid-switch`, na tomada. Conferir que não
     houve timeout (Ollama sem `500`; API sem `429`).
  3. Registrar em `docs/experiment.md`: tabela nova, comparação com a rodada de referência
     (#68), efeito em `js-xss-dom-search` e nos casos de `xss_s`, FP do LLM como sair, e a
     limitação "sem negativo de XSS de DOM no gabarito".
     - **Rodada 1 (descartada):** DOM TP 6/6, mas Ultra FN 3/3 em `js-auth-admin-config`
       (regressão; ver passo 7a). Guardada só localmente em
       `experiments/results/2026-11-16-72-*-DESCARTADA-token/`.
     - **Rodada 2 (2026-11-16, diff sha1 `f53c643ef8`):** nenhum timeout (81/81 `200` no
       Ollama; API sem erro). Heurística 24/0/18/0; Qwen 22/0/18/2, F1 0.957; Ultra
       24/0/18/0, F1 1.000. DOM e `js-auth-admin-config` TP em 6/6; FP do LLM 0%.
       Registrado em `docs/experiment.md` ("Rodada após a #72", e duas limitações novas).

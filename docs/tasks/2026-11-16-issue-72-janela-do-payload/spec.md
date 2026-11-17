# Spec — Janela do corpo centrada no payload no prompt do LLM (issue #72)

Issue: #72. Fase relacionada: camada de IA (`docs/llm-contract.md`, `docs/build-order.md`).

## Objetivo

O `LlmDetector` põe no prompt só os primeiros 4.096 caracteres do corpo da resposta. Quando
o corpo é maior e o payload aparece depois desse ponto, o modelo julga sem ver a evidência.
O caso `js-xss-dom-search` é FN em 6 de 6 execuções por isso: o DOM renderizado do Juice Shop
tem ~206 mil caracteres, o payload está por volta da posição 195 mil, e o modelo responde
"foi escapado" com confiança 1.0. O guestbook do DVWA (`xss_s`) fica no limite: a mensagem da
requisição atual cai entre as posições 3.369 e 4.817, dependendo do estado do banco.

O prompt deve mostrar o pedaço do corpo onde o payload está, sem passar do tamanho atual.
O LLM continua julgando a evidência: a regra é sobre **qual trecho mostrar**, igual para
todo tipo de falha, e não uma regra de vulnerabilidade.

## Evidência (2026-11-16)

- Prompt atual, 5 payloads do XSS de DOM: `vulnerable: false` em 5 de 5, confiança 1.0.
- Mesmo prompt, mas com uma janela de ~4 KB centrada no marcador (teste com `LlmClient`
  que reescrevia o prompt): `true` em 4 de 5, todos passando no grounding.

## Escopo

1. **Seleção do trecho** no `LlmDetector`, ao montar o prompt:
   - corpo com até `MAX_BODY_CHARS` (4.096) caracteres: vai inteiro, como hoje;
   - corpo maior: procurar uma **âncora**:
     1. a primeira ocorrência de `payload.value()` no corpo;
     2. se não houver, a primeira ocorrência de um **token** do payload (sequência de
        letras e dígitos com 6 caracteres ou mais, **com pelo menos uma letra e um
        dígito**), tentando os mais longos primeiro. Isso acha o marcador único (`vsx…`)
        mesmo quando o payload volta escapado (`&lt;…`) ou reescrito pelo navegador
        (`onload="…"`), sem ancorar em palavras comuns. *Desvio (checkpoint, 2026-11-16):* a
        1ª versão aceitava qualquer token; no `js-auth-admin-config` o payload é o path
        `/rest/admin/application-configuration`, e o token `configuration` ancorou numa
        descrição de produto na posição 8.354 de um JSON de 36 mil caracteres. O começo do
        JSON (a evidência) saiu do prompt e o Ultra deu FN em 3/3. Um marcador sem nenhum
        dígito (chance ≈ 0,04%) só volta ao comportamento antigo (começo do corpo);
     3. sem âncora: os primeiros 4.096 caracteres, como hoje.
   - Com âncora: uma janela de 4.096 caracteres que começa 2.048 antes dela, ajustada para
     não sair do corpo. Antes da janela vai `[… N caracteres omitidos …]`, se houver corte
     no começo; depois, `… [truncado]`, se houver corte no fim.
2. **Grounding sem mudança:** continua checando a evidência citada contra o corpo inteiro.
3. **Docs:** `docs/llm-contract.md` (item "Evidência completa" e a regra de truncar) e a
   linha do CLAUDE.md sobre o corte passam a descrever a janela.
4. **Rodadas novas** (checkpoint): Qwen 14B e Ultra, 3 execuções cada, mesmo protocolo da
   rodada de referência, registradas em `docs/experiment.md`.

## Fora do escopo

- Usar o pseudo-header `x-vulnscan-dialogs` no critério ou no grounding. É o sinal da
  heurística; o objetivo aqui é o LLM ver a evidência do corpo, não copiar a regra dela.
  Pode virar outra issue se o FN não sumir.
- Mudar o tamanho da janela (continua ~4 KB).
- Criar um negativo de XSS de DOM no gabarito (o FP do LLM nesse caminho segue sem medida;
  fica como limitação em `docs/experiment.md`).

## Contratos tocados

- `docs/llm-contract.md`: como o corpo entra no prompt.
- `docs/contracts.md`: nenhum. A assinatura do `LlmDetector` não muda.

## Critérios de aceite

1. Corpo grande com o payload literal depois dos 4 KB: o prompt traz o trecho ao redor do
   payload e a marca `[… N caracteres omitidos …]`. A parte do corpo continua limitada a
   ~4 KB.
2. Corpo grande com o payload escapado (`&lt;script&gt;alert('vsx…')`): a janela é achada
   pelo marcador e o prompt traz o trecho escapado.
3. Corpo grande sem nada do payload: os primeiros 4 KB e `[truncado]`, como hoje, sem a
   marca de omitidos.
4. Payload perto do fim do corpo: a janela termina no fim do corpo, sem `[truncado]` no fim.
5. Corpo pequeno: inalterado (testes atuais continuam verdes).
5a. Corpo grande em que só uma palavra comum do payload aparece (sem dígito, ex.:
    `configuration`): começo do corpo, sem janela deslocada.
6. `mvn clean verify` verde.
7. Checkpoint empírico:
   - o harness da issue, agora **sem** reescrever o prompt, dá `vulnerable: true` no XSS de
     DOM com pelo menos um payload;
   - nas rodadas novas, `js-xss-dom-search` deixa de ser FN sistemático, e a taxa de FP do
     LLM é registrada como sair (se subir, isso é resultado do experimento e vai para o
     relatório).

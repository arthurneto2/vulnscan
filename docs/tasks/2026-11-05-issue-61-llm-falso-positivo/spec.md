# Spec — LlmDetector com 100% de falso positivo (issue #61)

Issue: #61. Fase relacionada: camada de IA (`docs/build-order.md`, `docs/llm-contract.md`).

## Objetivo

Fazer o `LlmDetector` deixar de responder `vulnerable: true` para toda evidência, sem
transformá-lo numa cópia da heurística. O detector "llm" medido no experimento tem de
continuar sendo o julgamento do modelo; o código só pode recusar vereditos **sem lastro
na resposta** (regra genérica, igual para todos os tipos), nunca aplicar regras de
vulnerabilidade por tipo (ex.: "401 → seguro"), que são da heurística.

## Diagnóstico (reproduzido em 2026-11-05)

Linha de base (`benchmark --runs 1`, lab com banco do DVWA criado à mão — ver #59):
heurística VP 8 / FP 0 / VN 6 / FN 0; LLM VP 8 / **FP 6 / VN 0** / FN 0 (taxa FP 100%).
Igual à issue.

Experimento direto no Ollama (`mistral:7b-instruct-v0.3-q4_0`, temperatura 0.1) com
evidências reais do Juice Shop e três variantes de prompt:

| Caso | A: prompt atual | B: + contexto que falta | C: + critérios, "seguro é comum", raciocínio antes do veredito |
|---|---|---|---|
| NEG auth 401 (`/api/Users` sem token) | true 2/2 | true 2/2 | **false 2/2** |
| NEG XSS escapado (`/redirect`) | false 2/2* | false 2/2 | true 4/6 (instável) |
| POS auth 200 (`/rest/admin/application-configuration`) | true 2/2 | true 2/2 | true; erro do Ollama com evidência longa |
| POS SQLi (`/rest/products/search?q=1'`) | true 2/2 | true 2/2 | true 6/6 |

\* isolado, com um payload; no benchmark o caso é FP porque o módulo testa vários payloads
e para no primeiro positivo (ver "Amplificação").

Causas, em ordem de peso:

1. **Veredito antes do raciocínio.** O formato `{"vulnerable", "confidence", "rationale"}`
   faz o modelo se comprometer no primeiro token. Prova: rationale "401 Unauthorized…" com
   `vulnerable: true`. Só acrescentar contexto (B) não muda nada; a reordenação com
   critérios (C) corrige o 401.
2. **Viés de confirmação.** O prompt anuncia o tipo e o payload, não dá critério de
   evidência nem diz que "não vulnerável" é uma resposta normal.
3. **Contexto faltando.** Não vão para o prompt: `payload.description()` (no módulo de API o
   "payload" é só o caminho; o modelo não sabe que a requisição foi **sem token**), os
   headers da requisição, o status fora do corpo e `elapsedMillis` (SQLi temporal
   impossível de julgar).
4. **Alucinação de evidência.** No XSS escapado, o modelo justificou `true` citando o
   payload cru, que não está no corpo (o corpo tem `&lt;script&gt;`). Um campo `evidence`
   verificado no código barra esse caso (3/3 na rodada em que ocorreu).
5. **Limite do modelo.** Mesmo citando corretamente `&lt;script&gt;…`, o Mistral 7B às vezes
   conclui `true`: não entende escape de HTML com segurança. Não se corrige no código sem
   copiar a regra da heurística. Fica registrado como limitação e motiva comparar outro
   modelo (fora do escopo).
6. **Evidência longa trava o modelo.** Pedir trecho literal de um JSON grande gerou
   `500 {"error":"prediction aborted, token repeat limit reached"}`, que hoje vira
   "LLM indisponível" (falso negativo). Mitigação: trecho curto (≤ 120 caracteres) e teto
   de tokens gerados (`num_predict`).

**Amplificação:** os módulos param no primeiro positivo de cada ponto. Com k payloads e
taxa de FP p por payload, a taxa por caso é 1−(1−p)^k. Fica fora do escopo (é desenho dos
módulos, vale para os dois detectores), mas entra no relatório como observação.

## Escopo

1. **Prompt novo** em `LlmDetector` (em inglês, idioma em que o Mistral 7B é mais estável):
   - papel + aviso explícito de que "não vulnerável" é comum e correto sem evidência, e de
     não concluir pela simples presença do payload ou pelo nome do teste;
   - um critério de evidência **por tipo** (`SQL_INJECTION`, `XSS_REFLECTED`/`XSS_STORED`,
     `BROKEN_AUTH`, `BROKEN_ACCESS_CONTROL`), em texto; é contexto para o modelo julgar, não
     regra aplicada pelo código;
   - contexto completo: tipo, payload, descrição do payload, método + URL, headers da
     requisição, status, tempo de resposta (ms), headers da resposta, corpo truncado (4 KB,
     como hoje);
   - saída JSON com os campos **nesta ordem**: `evidence` (trecho literal curto do corpo, ou
     `""`), `rationale`, `vulnerable`, `confidence`.
2. **Grounding no parse** (regra genérica, igual para todos os tipos): se
   `vulnerable == true` e `evidence` estiver vazio, ausente ou não aparecer no corpo da
   resposta, o veredito vira `Verdict(false, 0.2, "veredito do LLM sem evidência na
   resposta: <rationale original>", "llm")`. A comparação ignora diferença de espaços em
   branco e de maiúsculas e **não** desfaz escape de HTML (senão o caso 4 passa).
   Vereditos `false` não são alterados.
   **Revisão depois do checkpoint (aprovada pelo usuário em 2026-11-05):** a regra literal
   rejeitava vereditos corretos em respostas JSON, porque o modelo resume em vez de copiar
   (recall do LLM caiu para 0.625). Se o trecho não estiver literalmente no corpo mas for JSON
   válido, conta como presente quando é uma string cujo texto desembrulhado está no corpo, ou
   quando é objeto/array cujos pares `"chave":valor` escalares citados estão **todos** no corpo,
   com ao menos um. Continua genérica, sem regra por tipo. **Ameaça à validade:** a mudança foi
   informada pelo resultado no próprio gabarito; isso está registrado em `docs/experiment.md`.
3. **`OllamaClient`** passa `options.num_predict` (teto de tokens gerados, 256) junto com a
   temperatura.
4. **Docs:** `docs/llm-contract.md` (prompt, formato de saída, regra de grounding,
   `num_predict`) e `docs/experiment.md` (limitações: gabarito pequeno validado pela mesma
   equipe que escreveu as regras; amplificação pelo "para no primeiro positivo"; limite
   do modelo com escape de HTML).

## Fora do escopo

- Guarda determinística por status ou por tipo depois do parse (decisão do usuário: o
  "llm" medido tem de ser só o LLM).
- Trocar ou comparar modelo (sugestão da issue; vira issue nova se a meta não for
  atingida).
- Mudar a política "para no primeiro positivo" dos módulos.
- Criar o banco do DVWA no benchmark (#59) e resetar o lab entre runs (#60): para validar,
  faço os dois à mão.

## Contratos tocados

- `Verdict`, `Evidence`, `LlmClient`, `Detector`: **inalterados**.
- `docs/llm-contract.md`: formato da saída (`evidence` novo, ordem dos campos), prompt e
  regra de grounding. `docs/contracts.md` não muda.

## Critérios de aceite

Unidade (`LlmDetectorTest`, fake de `LlmClient`):

1. O prompt contém tipo, payload, descrição do payload, método, URL, headers da
   requisição, status, tempo em ms, headers da resposta e corpo truncado com marca.
2. O prompt contém o critério do tipo em teste (um teste por família: SQLi, XSS, auth) e
   não contém o critério de outra família.
3. O prompt diz que "não vulnerável" é uma resposta comum e pede `evidence` antes de
   `vulnerable`.
4. `vulnerable: true` com `evidence` presente no corpo → veredito mantido.
5. Idem com diferença só de espaços e maiúsculas → mantido.
6. `vulnerable: true` com `evidence` vazio ou ausente → `false`, confiança 0.2, rationale
   começa com "veredito do LLM sem evidência na resposta".
7. `vulnerable: true` citando o payload cru quando o corpo tem a versão escapada → rebaixado.
8. `vulnerable: false` → inalterado, com ou sem `evidence`.
9. Testes existentes de robustez (não parseável, cliente que lança, clamp) continuam verdes.

Adaptador (`OllamaClientTest`, `HttpServer` local): o corpo enviado tem
`options.num_predict == 256` e `options.temperature == 0.1`.

Empírico (lab real, 3 execuções, lab resetado e banco do DVWA criado entre elas):

10. Meta: taxa de FP do LLM ≤ 50% com recall ≥ 0.75. O resultado é registrado no
    `plan.md` **atingindo ou não a meta**; se não atingir, abro issue de comparação de
    modelo com os números. Nenhum ajuste fino de prompt contra o gabarito depois da
    medição (seria sobreajuste ao conjunto de teste).

`mvn clean verify` verde.

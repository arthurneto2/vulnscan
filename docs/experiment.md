# Protocolo do experimento

A avaliação empírica é o produto central do trabalho. Rodamos os dois `Detector`
(heurístico e LLM) sobre os mesmos casos do gabarito e medimos cada um contra a verdade
conhecida.

## Procedimento

1. Subir o laboratório no estado de fábrica (`docker compose up`). Com `benchmark --reset`
   (issue #60), o próprio benchmark faz isso antes de cada execução.
2. Para cada caso do `ground_truth.yaml`: o módulo correspondente monta e envia a
   requisição, captura a `Evidence`, e cada `Detector` emite um `Verdict`.
3. Comparar `Verdict.vulnerable` com o campo `vulnerable` do gabarito. Cada par
   (caso × detector) cai em uma célula:

| Gabarito \ Detector | diz vulnerável | diz seguro |
| ------------------- | -------------- | ---------- |
| é vulnerável        | VP             | FN         |
| é seguro            | FP             | VN         |

4. Repetir N vezes (default 3) para capturar a variância do LLM. A heurística é
   determinística; o LLM não.

## Métricas (por tipo de falha e no total)

- Precisão = VP / (VP + FP) — dos que acusou, quantos eram de fato.
- Recall = VP / (VP + FN) — dos vulneráveis, quantos pegou.
- F1 = 2 · (precisão · recall) / (precisão + recall).
- Taxa de falso positivo = FP / (FP + VN).
- Tempo médio por detecção (a heurística deve ser ordens de grandeza mais rápida).

## Reprodutibilidade

- Modelo e versão fixados (`app/Config.java`), temperatura baixa.
- Mesma versão das imagens Docker do laboratório — fixadas por digest (`@sha256:`) em
  `lab/docker-compose.yml`, não por `:latest`.
- Alvo resetado entre execuções (o XSS armazenado suja o banco). Desde a #60, use
  `benchmark --runs N --reset`: o benchmark faz `docker compose down`/`up -d` e espera os
  alvos antes de cada execução, e as N saem num único `summary.csv`. As rodadas anteriores
  (até a #72) fizeram N × `--runs 1` com o reset à mão entre elas. Depois do reset, o banco
  do DVWA não existe; desde a #59 o próprio benchmark o cria (`setup.php`) antes do login e
  aborta com erro se não conseguir. As rodadas antes disso criaram o banco à mão.
- Ollama com o cache de prompts em RAM desligado (`LLAMA_ARG_CACHE_RAM=0 ollama serve`). Na
  máquina do experimento (14 GB de RAM, RTX 4050 de 6 GB, 23 de 49 camadas do 14B na GPU), o
  cache cresceu até 8 GB, a RAM e o swap lotaram, e a geração caiu de 5,4 para 0,7 tokens/s:
  todas as chamadas seguintes passaram dos 120 s e viraram veredito de timeout. Essa rodada
  (2026-11-16) foi descartada. O cache só reaproveita o começo idêntico dos prompts (~10–12%
  aqui), não muda as respostas e economiza menos de 0,5 s por chamada.
- Máquina sem suspender durante as rodadas (na tomada e com
  `systemd-inhibit --what=sleep:idle:handle-lid-switch`). Uma suspensão no meio deixa a
  execução parada por horas e o tempo medido sem sentido.
- Semente/ordem dos casos estável.
- O relatório cita a variância entre as N execuções como limitação honesta.

## Limitações (citar no relatório)

- **Gabarito pequeno e validado pela mesma equipe** que escreveu as regras da heurística.
  F1 = 1.0 da heurística pode refletir isso, não generalização.
- **Amplificação por "para no primeiro positivo".** Os módulos testam vários payloads por
  ponto e param no primeiro positivo. Com k payloads e taxa de falso positivo p por payload,
  a taxa por caso é 1 − (1 − p)^k. Afeta os dois detectores, mas pesa no LLM, que não é
  determinístico.
- **Limite dos modelos de 7B com escape de HTML.** Na investigação da issue #61, o Mistral 7B às vezes
  citou corretamente `&lt;script&gt;…` e ainda concluiu "vulnerável". O grounding do
  `LlmDetector` só barra evidência que não existe na resposta, não raciocínio errado sobre
  evidência real.
- **Grounding no `LlmDetector`.** O detector "llm" rebaixa um `vulnerable: true` sem trecho de
  evidência presente na resposta (`docs/llm-contract.md`). É uma regra genérica, igual para
  todo tipo; nenhuma regra de vulnerabilidade por tipo é aplicada ao veredito do LLM.
- **Ajuste informado pelo gabarito.** A aceitação de evidência JSON resumida no grounding foi
  acrescentada depois de ver o resultado do benchmark (issue #61), nos mesmos 14 casos. Não
  há conjunto de teste separado, então os números do LLM depois dessa mudança podem estar
  otimistas.
  O mesmo vale para a janela do corpo (issue #72): a exigência de letra e dígito no token
  que ancora a janela foi acrescentada depois de uma rodada em que o Ultra errou
  `js-auth-admin-config`, também nesses 14 casos.
  E vale para os diálogos no prompt (issue #81): a linha `Browser dialogs`, a frase do
  critério de XSS sobre o diálogo com o marcador e o grounding que aceita citar o diálogo
  foram acrescentados depois que `dvwa-xss-dom-low` deu FN nos dois modelos (Qwen 2/2 na
  #77, Ultra 1/1 na #79) com a heurística em TP. A justificativa é uma assimetria de
  informação: a heurística já usava o diálogo com o significado embutido, e o LLM recebia o
  pseudo-header cru, sem explicação. A regra é genérica (todo XSS no navegador), mas foi
  motivada por um caso do gabarito.
  A #81 também trocou, em **todo** prompt, a instrução do `evidence`, que passou a citar a
  linha de diálogos e a dizer "if nothing supports". Isso derrubou o Qwen 14B nos casos de
  API, onde a linha nunca aparece. A rodada de referência pós-#81 deu F1 0.851, com 7 FN em
  autenticação e autorização. A causa foi confirmada num A/B com os 5 casos de API do Juice
  Shop (Qwen, `--runs 3 --reset`, jars antes e depois da #81, um em seguida do outro):
  | | positivos (TP) | negativos (TN) |
  |---|---|---|
  | antes da #81 | 9/9 | 6/6 |
  | depois da #81 | 2/9 | 6/6 |
  A #83 volta ao texto antigo quando não há diálogos: o prompt de um caso sem navegador
  fica igual ao de antes da #81, byte a byte. A rodada de referência do Qwen feita entre
  a #81 e a #83 não vale como referência. A do Ultra, que não foi afetado, vale.
- **Negativo de XSS de DOM só desde a #77.** Até a #77, `js-xss-dom-search` era o único caso
  de DOM e era positivo: o FP do LLM nesse caminho (DOM renderizado, janela ao redor do
  payload) não era medido, e as rodadas tinham 14 casos. A #77 acrescentou o par do DVWA
  (`dvwa-xss-dom-low`, positivo, e `dvwa-xss-dom-impossible`, negativo: o payload aparece no
  DOM codificado e não executa) e corrigiu a codificação do `DomXssModule`, que passava o
  payload pelo `URLEncoder` (`/` virava `%2F`, que o `decodeURI` da página não desfaz) e por
  isso não detectava o low. Agora o payload vai com a codificação de um navegador
  (`InjectionPoint.browserUrl`). As rodadas anteriores usaram a codificação antiga no
  `js-xss-dom-search`.

- **Negativos do DVWA antes da #68.** No nível `impossible`, o DVWA exige um token
  anti-CSRF novo a cada submissão e, sem ele, responde `302` antes de executar a consulta.
  Até a #68, o benchmark não mandava o token. Então os três negativos do DVWA
  (`dvwa-sqli-impossible`, `dvwa-xss-reflected-impossible`, `dvwa-xss-stored-impossible`)
  foram julgados pelo redirect, não pela página segura, nas rodadas das seções da #61 e da
  #65. Desde a #68, o benchmark busca o token antes de cada submissão. O GET extra entra no
  tempo médio dos casos `impossible`, igual para os dois detectores. As rodadas foram
  refeitas com a página real (seção "Rodada após as #59 e #68"): o FP do LLM continuou 0%,
  então o resultado antigo se confirmou.
- **Indisponibilidade do LLM contada como previsão antes da #74.** Até a #74, uma chamada ao
  LLM que falhava (erro da API, timeout, Ollama fora do ar) virava "não vulnerável": FN num
  caso positivo e VN num negativo, sem rastro no CSV. As rodadas de referência não registraram
  nenhum timeout nem `429` pelos logs, mas não dá para garantir caso a caso. No checkpoint da
  #60, dois FN do Ultra levaram 175 e 456 ms (uma chamada normal leva de 7 a 44 s),
  provavelmente erro da API. Desde a #74, esses casos saem como `ERRO`, com o motivo, e são
  contados na coluna `erros` do resumo.
- **Retry da API só desde a #79.** O plano gratuito da API devolve `429` sob carga: no
  checkpoint da #77, 10 de 16 casos saíram `ERRO`. Desde a #79, o cliente da API tenta de
  novo em `429`/`5xx` (até 4 chamadas, com espera). **A espera entra no tempo medido do
  caso**, então o tempo médio do LLM via API inclui tempo de fila do provedor, não só de
  inferência; comparar tempos entre Ollama e API com essa ressalva. O Ollama não tem retry.

## Escolha do modelo (issue #61)

O modelo do experimento passou de `mistral:7b-instruct-v0.3-q4_0` para `qwen2.5:14b-instruct`.
Medição de 2026-11-05: mesmo prompt e mesmo grounding, 14 casos × 3 execuções, lab resetado
e banco do DVWA criado antes de cada execução, RTX 4050 (6 GB), Ollama local.

| Detector | VP | FP | VN | FN | Precisão | Recall | F1 | Taxa FP | Mediana | Máx. |
|---|---|---|---|---|---|---|---|---|---|---|
| heuristic | 24 | 0 | 18 | 0 | 1.000 | 1.000 | 1.000 | 0% | ~20 ms | — |
| llm — Mistral 7B | 22 | 6 | 12 | 2 | 0.786 | 0.917 | 0.846 | 33% | 3.4 s | 22 s |
| llm — Qwen2.5 14B | 19 | 0 | 18 | 5 | 1.000 | 0.792 | 0.884 | 0% | 20 s | 78 s |

Uma medição anterior com `qwen2.5:7b-instruct` deu F1 0.750 (6 FP, 6 FN).

**Por que trocar:**

- Os dois modelos de 7B erram de forma **sistemática** em XSS com a saída escapada
  (`dvwa-xss-stored-impossible` e `js-xss-redirect-escaped`, FP em 3/3 execuções). O Mistral
  chega a citar `&lt;script&gt;` como evidência e concluir "não escapado". O prompt já explica
  as entidades HTML e o grounding não pega raciocínio errado sobre evidência real, então
  não havia correção possível no código sem copiar a regra da heurística para o detector "llm".
- Com o mesmo prompt, o 14B acerta esses casos: precisão 1.0 e nenhum FP. Isso separa o efeito
  do prompt do efeito do tamanho do modelo e é o resultado que testa a hipótese do trabalho
  (a IA ajudar a reduzir falso positivo).
- Custo: o 14B é ~6× mais lento (parte das camadas fica na CPU com 6 GB de VRAM) e mais
  conservador. Os FN são `js-xss-dom-search` (3/3) e `js-idor-basket` (2/3). Por isso o
  Ollama ganhou timeout próprio de 120 s (`Config.llmTimeout()`, antes `ollamaTimeout`); com os 30 s do alvo,
  parte dos vereditos viraria "LLM indisponível".

**Regra a partir daqui:** testes e experimentos com modelo local usam **no mínimo 14B
parâmetros**. Modelos de 7B não são recomendados. Para máquinas que não rodam o 14B, o caminho
é a opção de IA via API (`--llm api`, abaixo), não um modelo menor.

## IA via API: escolha do modelo (issue #65)

Mesmo protocolo (14 casos × 3 execuções, lab resetado e banco do DVWA criado antes de cada
uma), com `--llm api` na NVIDIA NIM, plano gratuito, em 2026-11-05. O tempo é por caso, que
pode ter várias chamadas ao modelo.

| Detector | VP | FP | VN | FN | Precisão | Recall | F1 | Taxa FP | Mediana | Máx. |
|---|---|---|---|---|---|---|---|---|---|---|
| llm — Qwen2.5 14B (Ollama, tabela acima) | 19 | 0 | 18 | 5 | 1.000 | 0.792 | 0.884 | 0% | 20 s | 78 s |
| llm — `nemotron-3.5-lightning-30b-a3b` | 15 | 0 | 18 | 9 | 1.000 | 0.625 | 0.769 | 0% | 60 s | 240 s |
| llm — `nemotron-3-ultra-550b-a55b` | 21 | 0 | 18 | 3 | 1.000 | 0.875 | **0.933** | 0% | 6.5 s | 76 s |

A heurística deu 24/0/18/0 em todas as execuções, como antes.

- **O padrão é o Ultra:** F1 0.933 nas 3 execuções, sem variação, nenhum FP e o caso mais
  rápido. O único FN é `js-xss-dom-search` (3/3), o mesmo caso que o Qwen 14B erra.
- O Lightning variou entre execuções (F1 0.67 a 0.86). Os FN vieram com confiança 0, isto
  é, resposta não parseável ou indisponível. O CSV não guarda a justificativa, então não dá
  para separar timeout (120 s), `429` e JSON inválido. Ele raciocina muito mais: até ~3.300
  tokens por chamada contra ~350 do Ultra, o que deixa cada caso ~4× mais lento.
- **Teto de tokens:** a primeira rodada, com `max_tokens: 256` (igual ao `num_predict` do
  Ollama), deu F1 0 no Lightning e 0.40–0.77 no Ultra. Os dois expõem o raciocínio em
  `reasoning_content`, que conta no teto: a resposta vinha `null` com
  `finish_reason: length`. O `OpenAiCompatibleClient` passou a mandar `max_tokens: 8192`.
- O modelo hospedado não tem digest fixo como o do Ollama: o provedor pode atualizar o modelo
  sob o mesmo id. Para o relatório, a referência continua sendo o Qwen 14B local, e a API é
  a alternativa para quem não roda o 14B.

## Rodada após as #59 e #68

Medição de 2026-11-16, commit `73febb7`: 14 casos × 3 execuções por modelo, lab resetado
antes de cada execução (à mão, enquanto a #60 está aberta), banco do DVWA criado pelo próprio
benchmark (#59) e negativos `impossible` do DVWA julgados pela página real, com o token
anti-CSRF (#68). Ollama com `LLAMA_ARG_CACHE_RAM=0`, na tomada, sem suspensão; nenhum
timeout (90/90 chamadas ao Ollama com `200`; nenhum `429` ou erro na API). Tempo por caso.

| Detector | VP | FP | VN | FN | Precisão | Recall | F1 | Taxa FP | Mediana | Máx. | F1 por execução |
|---|---|---|---|---|---|---|---|---|---|---|---|
| heuristic | 24 | 0 | 18 | 0 | 1.000 | 1.000 | 1.000 | 0% | < 0,1 s | 8 s | 1.000 / 1.000 / 1.000 |
| llm — `qwen2.5:14b-instruct` (Ollama) | 20 | 0 | 18 | 4 | 1.000 | 0.833 | 0.909 | 0% | 19,7 s | 87 s | 0.857 / 0.933 / 0.933 |
| llm — `nemotron-3-ultra-550b-a55b` (API) | 21 | 0 | 18 | 3 | 1.000 | 0.875 | **0.933** | 0% | 9,4 s | 111 s | 0.933 / 0.933 / 0.933 |

A heurística deu o mesmo resultado nas duas rodadas (Ollama e API).

- **O FP do LLM continua 0%** com a página real. Os 3 negativos `impossible` do DVWA
  (consulta parametrizada; saída escapada `&lt;script&gt;`) deram VN nas 6 execuções, nos
  dois modelos. O 0% das rodadas da #61 e da #65, quando esses casos eram julgados pelo
  redirect, se confirmou.
- Comparado com essas rodadas: o Qwen foi de F1 0.884 (19/0/18/5) para 0.909 (20/0/18/4),
  um FN a menos, dentro da variação entre execuções; o Ultra repetiu 0.933 (21/0/18/3).
- **Todos os erros do LLM são FN.** `js-xss-dom-search` é FN nas 6 execuções, nos dois
  modelos: é o caso julgado sobre o DOM renderizado pelo navegador. `js-idor-basket` é FN
  em 1 de 3 no Qwen, a única variação entre execuções.
- Esta tabela substituiu as das seções da #61 e da #65. Hoje o resultado de referência é o
  da seção "Rodada após as #60–#83"; esta fica como histórico.

## Rodada após a #72 (histórico)

> Esta seção é histórica. O resultado de referência agora está na seção "Rodada após as
> #60–#83".

A #72 mudou o trecho do corpo que vai para o prompt: em corpo maior que 4 KB, a janela onde
o payload aparece, não o começo (`docs/llm-contract.md`, "Trecho do corpo"). Medição de
2026-11-16, commit base `98efca0` + o diff da #72 (sha1 do diff `f53c643ef8`), mesmo protocolo
da seção anterior: 14 casos × 3 execuções por modelo, reset do lab antes de cada uma, Ollama
com `LLAMA_ARG_CACHE_RAM=0`, na tomada e sem suspensão. Nenhum timeout (81/81 chamadas ao
Ollama com `200`; nenhum `429` ou erro na API).

| Detector | VP | FP | VN | FN | Precisão | Recall | F1 | Taxa FP | Mediana | Máx. | F1 por execução |
|---|---|---|---|---|---|---|---|---|---|---|---|
| heuristic | 24 | 0 | 18 | 0 | 1.000 | 1.000 | 1.000 | 0% | < 0,1 s | 8 s | 1.000 / 1.000 / 1.000 |
| llm — `qwen2.5:14b-instruct` (Ollama) | 22 | 0 | 18 | 2 | 1.000 | 0.917 | 0.957 | 0% | 18,3 s | 99 s | 1.000 / 0.933 / 0.933 |
| llm — `nemotron-3-ultra-550b-a55b` (API) | 24 | 0 | 18 | 0 | 1.000 | 1.000 | **1.000** | 0% | 10,8 s | 68 s | 1.000 / 1.000 / 1.000 |

- **`js-xss-dom-search` passou de FN em 6/6 para TP em 6/6.** O DOM renderizado do Juice
  Shop tem ~206 mil caracteres e o payload fica por volta da posição 195 mil; antes, o
  prompt só levava os primeiros 4 KB e o modelo respondia "foi escapado" com confiança 1.0.
- **Ultra: F1 1.000**, igual à heurística, nas 3 execuções. **Qwen: F1 0.957**; o único
  erro é `js-idor-basket` (FN em 2/3), o mesmo caso instável das rodadas anteriores.
- **FP do LLM continua 0%** nos dois modelos, com os negativos `impossible` do DVWA
  julgados pela página real (#68) e agora com o payload visível no prompt.
- **Rodada descartada:** a 1ª versão da janela ancorava em qualquer token do payload. No
  `js-auth-admin-config`, o payload é o path `/rest/admin/application-configuration`, e o
  token `configuration` ancorou numa descrição de produto (posição 8.354 de um JSON de 36
  mil caracteres): o começo do JSON, que era a evidência, saiu do prompt, e o Ultra deu FN
  em 3/3. A âncora passou a exigir letra e dígito (o marcador `vsx…`), e a rodada foi
  refeita. Ver a limitação "Ajuste informado pelo gabarito".

## Rodada após as #60–#83 (resultado de referência)

Medição de 2026-11-17 com o gabarito de **16 casos**. A #77 acrescentou o par de XSS de DOM
do DVWA (`dvwa-xss-dom-low`, positivo; `dvwa-xss-dom-impossible`, negativo).

- **Protocolo:** 16 casos × 3 execuções por modelo, com `benchmark --runs 3 --reset`: o
  laboratório é resetado automaticamente antes de cada execução (#60).
- **Ambiente:** Ollama com `LLAMA_ARG_CACHE_RAM=0`, notebook na tomada, sem suspensão
  (`systemd-inhibit`).
- **O que mudou desde a #72:**
  - falha do LLM vira `ERRO`, fora do FN/VN (#74);
  - retry em 429/5xx na API (#79);
  - diálogos do navegador no prompt e no grounding (#81), com a correção da instrução do
    `evidence` (#83).

| Detector | VP | FP | VN | FN | ERRO | Precisão | Recall | F1 | Taxa FP | Mediana | Máx. | F1 por execução |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| heuristic | 27 | 0 | 21 | 0 | 0 | 1.000 | 1.000 | 1.000 | 0% | < 0,1 s | 8 s | 1.000 / 1.000 / 1.000 |
| llm — `qwen2.5:14b-instruct` (Ollama) | 25 | 0 | 21 | 2 | 0 | 1.000 | 0.926 | 0.962 | 0% | 18,7 s | 107 s | 1.000 / 0.941 / 0.941 |
| llm — `nemotron-3-ultra-550b-a55b` (API) | 26 | 0 | 15 | 0 | **7** | 1.000 | 1.000 | **1.000**¹ | 0% | 17,1 s | 97 s | 1.000 / 1.000 / 1.000 |

¹ **Sobre 41 de 48 julgamentos.** Os 7 `ERRO` são `429 após 4 tentativas` (limite de
requisições do plano gratuito, mesmo com o retry da #79) e ficam fora da matriz. Não leia
como 48/48. Os 7 caíram em casos negativos (6) e em 1 positivo (`js-bac-users-role`, na
execução 2).

A heurística deu o mesmo resultado nas duas rodadas (Ollama e API).

**Rodadas e commits** (pastas locais em `experiments/results/`):

| Rodada | Commit | Pasta | Duração | Chamadas |
|---|---|---|---|---|
| Qwen | `dc7a6ec` (`main` com a #83) | `2026-11-17-ref83-ollama-qwen14b` | 32 min | 96/96 ao Ollama com `200`, nenhum timeout |
| Ultra | `cbc1612` (antes da #83) | `2026-11-17-ref-api-ultra` | 25 min | — |

A rodada do Ultra é de antes da #83, mas vale como referência:
- nos casos sem navegador, a #83 só trocou o texto da instrução do `evidence`, e o Ultra
  acertou com os dois textos (rodada pós-#81 e A/B da #83);
- nos casos com diálogos, o prompt é idêntico.

A rodada do Qwen feita entre a #81 e a #83 foi descartada (ver a limitação "Ajuste informado
pelo gabarito").

**Achados**
- **FP do LLM continua 0%** nos dois modelos, agora com 7 negativos por execução, incluindo
  os negativos de navegador (`dvwa-xss-dom-impossible`, `dvwa-xss-stored-impossible`).
- **XSS de DOM do DVWA:** `dvwa-xss-dom-low` dá TP 3/3 no Qwen e no Ultra. Ele virou
  detectável com a codificação de navegador do `DomXssModule` (#77); o LLM passou a acertar
  quando o diálogo entrou no prompt (#81).
- **Qwen: F1 0.962**, igual à referência da #72 (0.957) dentro da variação. O único erro é
  `js-idor-basket` (FN em 2/3), o mesmo caso instável de todas as rodadas.
- **Ultra:** acertou todos os 41 casos que chegou a julgar. O custo da API agora é a cota: com
  o retry, o tempo mediano por caso foi de 10,8 s (#72) para 17,1 s, e ainda sobraram 7 `ERRO`.

## Saída

- `experiments/results/run-<n>.csv`: uma linha por (caso, detector) com veredito,
  confiança, acerto (VP/FP/VN/FN) e tempo. Um caso sem previsão válida sai como `ERRO`, fora
  da matriz, com o motivo na coluna `erro`: exceção na execução (ex.: login do DVWA) ou, desde
  a #74, caso sem achado em que o LLM **não respondeu** a alguma chamada (erro da API, como
  429/5xx, timeout, Ollama fora do ar).
- `experiments/results/summary.csv`: matriz de confusão e métricas, heurística vs LLM, por
  tipo de falha, e a coluna `erros` (quantos `ERRO` por tipo e no total). Rodada com `erros`
  > 0 deve ser refeita ou relatada com essa ressalva.

Essas tabelas vão diretas para o relatório técnico e para a apresentação.

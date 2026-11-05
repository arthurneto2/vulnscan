# Protocolo do experimento

A avaliação empírica é o produto central do trabalho. Rodamos os dois `Detector`
(heurístico e LLM) sobre os mesmos casos do gabarito e medimos cada um contra a verdade
conhecida.

## Procedimento

1. Subir o laboratório no estado de fábrica (`docker compose up`).
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
- Alvo resetado entre execuções (o XSS armazenado suja o banco).
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

## Saída

- `experiments/results/run-<n>.csv`: uma linha por (caso, detector) com veredito,
  confiança, acerto (VP/FP/VN/FN) e tempo.
- `experiments/results/summary.csv`: matriz de confusão e métricas, heurística vs LLM, por
  tipo de falha.

Essas tabelas vão diretas para o relatório técnico e para a apresentação.

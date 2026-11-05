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
- **Limite do modelo com escape de HTML.** Na investigação da issue #61, o Mistral 7B às vezes
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

## Saída

- `experiments/results/run-<n>.csv`: uma linha por (caso, detector) com veredito,
  confiança, acerto (VP/FP/VN/FN) e tempo.
- `experiments/results/summary.csv`: matriz de confusão e métricas, heurística vs LLM, por
  tipo de falha.

Essas tabelas vão diretas para o relatório técnico e para a apresentação.

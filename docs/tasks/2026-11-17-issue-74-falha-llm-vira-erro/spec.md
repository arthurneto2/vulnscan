# Spec — Falha da chamada ao LLM vira ERRO no benchmark, não FN/VN (issue #74)

## Objetivo

Separar, no experimento, "o LLM respondeu que não é vulnerável" de "o LLM não respondeu". Hoje
uma falha do cliente (erro HTTP da API, como o 429 do plano gratuito de ~40 req/min, um 5xx,
timeout, Ollama fora do ar) vira `Verdict(false, 0.0, "LLM indisponível: …")`, o que é correto
pelo contrato, mas o benchmark conta esse veredito como previsão: num caso positivo vira **FN**,
num negativo vira **VN de graça**, e o CSV não guarda nada que permita separar. No checkpoint
da #60, dois FN do Ultra levaram 175 e 456 ms (uma chamada normal leva de 7 a 44 s).

## Escopo

1. **Caso sem resposta do LLM vira `ERRO`.** No `LabCaseRunner`, se o módulo **não reportou
   achado** e **pelo menos um** veredito do caso foi de indisponibilidade, o caso não tem
   previsão válida: lança `IllegalStateException("LLM indisponível em k de n chamadas: <msg da
   1ª>")`. O `BenchmarkRunner` já transforma exceção de um caso em linha `outcome=ERRO`, fora
   da matriz de confusão.
   - Se o módulo reportou achado, o resultado vale (positivo de um payload que foi julgado),
     mesmo que outra chamada do caso tenha falhado.
   - Resposta **não parseável** e veredito **sem evidência** (grounding) continuam contando:
     o modelo respondeu, e o erro é dele.
2. **Motivo do `ERRO` no CSV.** `run-<n>.csv` ganha a coluna final `erro`: vazia nas linhas
   normais; nas linhas `ERRO`, a mensagem da exceção, em uma linha, sem vírgula (vírgula vira
   `;`, quebra de linha vira espaço), cortada em 200 caracteres. Vale para qualquer `ERRO`
   (ex.: login do DVWA que falhou), não só para o LLM.
3. **Contagem de `ERRO` no resumo.** `summary.csv` ganha a coluna final `erros` (por detector,
   por tipo e no `TOTAL`). Um tipo cujos casos foram todos `ERRO` aparece com a matriz zerada
   e `erros > 0`, em vez de sumir. Assim uma rodada com falhas fica visível e pode ser
   invalidada.
4. **Identificar a indisponibilidade num lugar só.** `LlmDetector` expõe
   `public static boolean unavailable(Verdict v)` (o detector é `llm` e a justificativa começa
   com o prefixo `"LLM indisponível"`, que vira constante pública `UNAVAILABLE`), em vez de o
   `app/` comparar texto por conta própria.
5. Docs: `docs/contracts.md`, `docs/experiment.md` (seção "Saída" e uma limitação: as rodadas
   anteriores à #74 contavam indisponibilidade como FN/VN), `docs/llm-contract.md` (o
   benchmark trata o veredito de indisponibilidade como `ERRO`).

### Fora do escopo

- **Retry com espera em 429/5xx** no `OpenAiCompatibleClient` (opção 2 da issue). Reduz a
  ocorrência, mas muda o comportamento do cliente e o tempo medido; fica para outra issue, se
  as rodadas mostrarem `ERRO` frequente.
- Mudar o `Verdict` (novo campo) ou o contrato do `LlmDetector` (continua nunca lançando).
- Aviso na saída da CLI: a coluna `erros` do `summary.csv` já mostra o problema.
- `scan`: lá o veredito de indisponibilidade continua aparecendo como hoje (sem achado).

## Contratos tocados (`docs/contracts.md`)

- `LlmDetector`: `public static final String UNAVAILABLE = "LLM indisponível"` e
  `public static boolean unavailable(Verdict v)`.
- `LabCaseRunner`: novo comportamento (caso sem achado com indisponibilidade → exceção).
  Assinaturas iguais; o registro dos vereditos fica num decorador `Detector` privado do pacote
  `app/benchmark` (`RecordingDetector`), sem nova interface.
- `BenchmarkRunner`: formato dos CSVs (`erro` em `run-<n>.csv`, `erros` em `summary.csv`).
  Assinaturas iguais.

## Critérios de aceite (viram testes)

1. `LlmDetectorTest#falhaDoClienteEhIndisponivel`: cliente que lança → `unavailable(v)` é
   verdadeiro. `#respostaNaoParseavelNaoEhIndisponivel` e um veredito normal → falso; um
   veredito do detector `heuristic` com a mesma justificativa → falso.
2. `LabCaseRunnerTest#llmIndisponivelSemAchadoViraErro`: detector `llm` falso que devolve
   indisponível em todas as chamadas → `run` lança `IllegalStateException` com
   `LLM indisponível` e a contagem `k de n`.
   `#umaChamadaIndisponivelSemAchadoTambemViraErro`: uma chamada "não" e outra indisponível →
   exceção com `1 de 2`.
3. `LabCaseRunnerTest#llmIndisponivelComAchadoVale`: uma chamada indisponível e outra
   positiva → `CaseResult(true, …)`, sem exceção.
4. `LabCaseRunnerTest#negativoNormalContinuaNegativo`: todas as chamadas respondem "não" →
   `CaseResult(false, …)`.
5. `BenchmarkRunnerTest#erroGravaOMotivoSemVirgula`: exceção `"falhou, de novo\nlinha 2"` →
   linha `ERRO` com `erro` = `falhou; de novo linha 2`; linha normal termina com `,` vazio; o
   cabeçalho termina em `,erro`.
6. `BenchmarkRunnerTest#resumoContaErrosPorTipoETotal`: 1 caso com exceção → `erros` = 1 no
   tipo e no `TOTAL` daquele detector, 0 no outro detector; tipo só com `ERRO` aparece no
   resumo.
7. Testes atuais do `BenchmarkRunnerTest` (incluindo `erroDeUmCasoViraLinhaErroSemAbortar`)
   seguem verdes.
8. `mvn clean verify` verde.
9. **Checkpoint empírico** (lab real, sem chamar provedor externo): `benchmark --runs 1
   --reset --llm ollama` com o **Ollama parado**. Esperado: todas as linhas `llm` sem achado
   viram `ERRO` com `LLM indisponível` na coluna `erro`; as linhas `heuristic` iguais à
   referência (F1 1.000); `summary.csv` com `erros` = 14 no `TOTAL` do `llm` (ou o número de
   casos do gabarito).

## Referências

- Issue #74; checkpoint da #60 (`docs/tasks/2026-11-17-issue-60-reset-entre-execucoes/plan.md`,
  passo 11); `docs/llm-contract.md` ("Erro do provedor … vira Verdict(false, 0.0, …)").

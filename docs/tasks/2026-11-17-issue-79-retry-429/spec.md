# Spec — Retry com espera em 429/5xx no OpenAiCompatibleClient (issue #79)

## Objetivo

Tornar viável uma rodada do benchmark via API (`--llm api`). No checkpoint da #77, o plano
gratuito da NVIDIA devolveu `429 Too Many Requests` em 10 de 16 casos. Desde a #74 isso vira
`ERRO`, o que é honesto, mas a rodada fica inútil como medida do LLM. Um erro temporário do
provedor deve ser tentado de novo, com espera, antes de virar falha.

## Escopo

1. **`OpenAiCompatibleClient` tenta de novo** quando a resposta é `429` ou `5xx` (500 a 599):
   até **3 novas tentativas** (4 chamadas no máximo).
2. **Espera antes de cada nova tentativa:**
   - o header `Retry-After` da resposta, se for um número inteiro de segundos (a forma
     data-HTTP é ignorada e cai na espera padrão), com **teto de 60 s**;
   - sem `Retry-After` válido: **2 s, 4 s, 8 s** (dobra a cada tentativa).
3. **Sem retry** em `4xx` que não seja `429` (400, 401, 403, 404…), em resposta `2xx` sem
   `choices` ou que não é JSON, e em falha de transporte ou timeout (`UncheckedIOException`):
   cada tentativa já tem 120 s de prazo, e repetir um timeout triplicaria o tempo.
4. **Esgotou as tentativas:** `IllegalStateException("API de IA respondeu <status> após 4
   tentativas: <trecho>")`, com a chave mascarada como hoje. No benchmark, continua virando
   `ERRO` (#74), e a coluna `erro` mostra o número de tentativas.
5. **Testável sem esperar de verdade:** a espera é injetada. Novo construtor
   `OpenAiCompatibleClient(HttpClient, URI, String, String, Consumer<Duration> sleeper)`; o
   construtor atual de 4 argumentos usa `Thread.sleep`. O número de tentativas e as esperas
   ficam fixos em constantes (não em `Config`), como a temperatura e o `max_tokens`.
6. Docs: `docs/llm-contract.md` (troca "Não há retry em 429." pela regra nova),
   `docs/contracts.md` (construtor e comportamento), `docs/experiment.md` (a espera do retry
   entra no tempo medido do caso via API; rodadas anteriores à #79 não tinham retry).

### Fora do escopo

- Retry no `OllamaClient`: é local e não tem limite de taxa.
- Retry em timeout ou erro de conexão.
- Limitar a taxa de chamadas no cliente (throttling preventivo).
- Registrar quantas tentativas uma chamada **bem-sucedida** levou: só o tempo do caso
  aumenta. Se isso pesar na análise, fica para outra issue.

## Contratos tocados (`docs/contracts.md`)

- `OpenAiCompatibleClient`: novo construtor de 5 argumentos com `Consumer<Duration> sleeper`;
  comportamento de retry em 429/5xx. A interface `LlmClient` não muda.

## Critérios de aceite (viram testes, com `HttpServer` local e `sleeper` que só registra)

1. `#erro429DepoisSucessoDevolveOConteudo`: 429 e depois 200 → devolve o conteúdo; 2 chamadas;
   esperas `[2 s]`.
2. `#respeitaRetryAfterEmSegundos`: 429 com `Retry-After: 7` e depois 200 → esperas `[7 s]`.
3. `#retryAfterAcimaDoTetoEhLimitado`: `Retry-After: 3600` → espera `[60 s]`.
4. `#retryAfterEmDataUsaEsperaPadrao`: `Retry-After: Wed, 21 Oct 2026 07:28:00 GMT` →
   `[2 s]`.
5. `#erro5xxEsgotaAsTentativasELancaComAContagem`: 503 sempre → 4 chamadas; esperas
   `[2 s, 4 s, 8 s]`; exceção com `503` e `4 tentativas`, sem a chave.
6. `#erro4xxNaoRepete`: 401 → 1 chamada, nenhuma espera, exceção com `401`.
7. `#respostaSemChoicesNaoRepete`: 200 sem `choices` → 1 chamada.
8. Testes atuais: `statusDeErroLancaComStatusESemVazarChave` (401, 429, 500) continua
   valendo com o `sleeper` que não espera; a chave nunca aparece, nem após as tentativas.
9. `mvn clean verify` verde.
10. **Checkpoint empírico** (lab real, provedor real):
    `benchmark --runs 1 --reset --llm api` com o gabarito completo (16 casos) → `erros` = 0,
    ou, se ainda houver `ERRO`, a coluna `erro` mostra `após 4 tentativas`. Anotar o tempo
    total contra o checkpoint da #77 (5 min).

## Referências

- Issue #79; checkpoint da #77 (`docs/tasks/2026-11-17-issue-77-xss-dom-negativo/plan.md`,
  passo 6); #74 (falha do LLM como `ERRO`); `docs/llm-contract.md`, seção da API.

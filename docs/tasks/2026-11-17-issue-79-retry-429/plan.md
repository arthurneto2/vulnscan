# Plano — Retry com espera em 429/5xx no OpenAiCompatibleClient (issue #79)

Spec: `spec.md` nesta pasta. Branch: `issue-79-retry-429` (a partir de `main` em
`b97d202`). Cada passo: teste primeiro (red pelo motivo esperado), código mínimo (green),
refactor. Mudou contrato → `docs/contracts.md` no mesmo passo.

## Passos

- [x] **1. Infra de teste e construtor com `sleeper`.**
  No `OpenAiCompatibleClientTest`: o servidor passa a responder um roteiro de status (fila;
  repete o último), conta as chamadas e pode mandar `Retry-After`; o helper `client()` usa um
  `sleeper` que só registra as esperas. Construtor de 5 argumentos que ainda ignora o
  `sleeper` (o de 4 delega com `Thread.sleep`). Os testes atuais seguem verdes.
  Contrato em `docs/contracts.md`.

- [x] **2. Retry em 429 com espera padrão (red → green).**
  Testes: `erro429DepoisSucessoDevolveOConteudo`, `erro5xxEsgotaAsTentativasELancaComAContagem`.
  Red: hoje lança no 1º 429 (asserção do conteúdo / da contagem).
  Green: laço de até 4 chamadas, repetindo em 429/5xx com esperas 2, 4, 8 s; mensagem final
  `após 4 tentativas`.

- [x] **3. Sem retry fora de 429/5xx (teste de regressão).**
  Testes: `erro4xxNaoRepete`, `respostaSemChoicesNaoRepete`. Devem passar de primeira; se não
  passarem, corrigir.

- [x] **4. `Retry-After` (red → green).**
  Testes: `respeitaRetryAfterEmSegundos`, `retryAfterAcimaDoTetoEhLimitado`,
  `retryAfterEmDataUsaEsperaPadrao`.
  Green: lê `retry-after` (o `JdkHttpClient` devolve os nomes em minúsculas), inteiro ≥ 0,
  limitado a 60 s; senão, espera padrão.
  Refactor: Javadoc da classe (regra de retry), constantes nomeadas.
  Verificar: `mvn test -Dtest=OpenAiCompatibleClientTest`.

- [x] **5. Docs.** `docs/llm-contract.md` (regra de retry no lugar de "Não há retry em
  429."), `docs/experiment.md` (a espera entra no tempo do caso via API; rodadas antes da
  #79 sem retry), `docs/contracts.md` (já no passo 1, mais o comportamento).

- [ ] **6. `mvn clean verify`.**

- [ ] **7. Checkpoint empírico** (provedor real, lab real, `systemd-inhibit`):
  `benchmark --ground-truth experiments/ground_truth.yaml --runs 1 --reset --llm api --out
  experiments/results/2026-11-17-issue-79-retry`. Conferir `erros` no `summary.csv` e o tempo
  total, contra o checkpoint da #77 (10 `ERRO` por 429 em 16 casos, 5 min).

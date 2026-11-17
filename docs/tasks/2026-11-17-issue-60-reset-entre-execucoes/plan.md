# Plano — Reset do laboratório entre as execuções do benchmark (issue #60)

Spec: `spec.md` nesta pasta. Branch: `issue-60-reset-entre-execucoes` (a partir de `main`
em `a7a69ec`). Cada passo: teste primeiro (red pelo motivo esperado), código mínimo (green),
refactor. Mudou contrato → `docs/contracts.md` no mesmo passo.

## Passos

- [x] **1. `LabReset` no `BenchmarkRunner` (red → green).**
  Testes: `BenchmarkRunnerTest#resetaOLabAntesDeCadaExecucao` e
  `#falhaNoResetAbortaOBenchmark`, com um `LabReset` falso que registra as chamadas num log
  compartilhado com o `CaseRunner` falso (para checar a ordem reset → casos).
  Red: não compila sem a interface → criar só `LabReset` (com `NONE`) e o construtor de 5
  argumentos que ignora o reset; aí o red é a asserção (0 resets).
  Green: chamar `reset.reset(baseUrls)` no começo de cada execução.
  Contratos: `LabReset` e o construtor novo em `docs/contracts.md`.
  Verificar: `mvn test -Dtest=BenchmarkRunnerTest`.

- [x] **2. `forgetSessions()` depois do reset (red → green).**
  Teste: `BenchmarkRunnerTest#esqueceAsSessoesDepoisDoReset`.
  Green: `default void forgetSessions() { }` em `CaseRunner`; o `BenchmarkRunner` chama
  depois de cada reset, só quando o reset não é `NONE` (sem reset, o comportamento atual,
  com um login por nível no benchmark inteiro, fica igual). O teste também cobre que, com
  `NONE`, `forgetSessions()` não é chamado.
  Contrato: `CaseRunner` em `docs/contracts.md`.

- [x] **3. `LabCaseRunner` esquece o cookie do DVWA (red → green).**
  Teste: `LabCaseRunnerTest#forgetSessionsFazNovoLoginNoDvwa` (fake de `HttpClient` contando
  os POST em `/login.php`).
  Green: `forgetSessions()` limpa `dvwaCookies`.
  Verificar: `mvn test -Dtest=LabCaseRunnerTest`.

- [ ] **4. `DockerComposeLabReset` — comandos (red → green).**
  Testes: `DockerComposeLabResetTest#rodaOsComandosEmOrdemEEsperaOAlvo` (dois `sh -c` que
  anotam num arquivo do `@TempDir`; `HttpClient` falso respondendo 302) e
  `#comandoComFalhaAbortaCitandoOComandoEOCodigo` (comando `false`).
  Green: `ProcessBuilder(cmd).inheritIO()`, `waitFor()`, código ≠ 0 →
  `IllegalStateException("reset do lab: '<cmd>' saiu com <código>")`.

- [ ] **5. `DockerComposeLabReset` — espera dos alvos (red → green).**
  Testes: `#alvoQueNaoRespondeNoPrazoAborta` (`HttpClient` falso que sempre lança, prazo de ~300 ms,
  intervalo de ~50 ms) e `#alvoQueSobeDepoisDeAlgumasTentativasPassa` (`HttpClient` falso
  que lança 2 vezes e depois responde).
  Green: laço `GET baseUrl` até responder ou estourar o prazo; prazo e intervalo injetáveis
  no construtor (os de produção vêm do `Config`).
  Refactor: Javadoc da classe; mensagem de prazo cita a URL e o tempo.
  Verificar: `mvn test -Dtest=DockerComposeLabResetTest`.

- [ ] **6. `BenchmarkRequest.reset` e `BenchmarkService` (red → green).**
  Testes: `BenchmarkServiceTest#resetLigadoChamaOLabReset` e `#resetDesligadoNaoChama`.
  Green: componente `boolean reset` no record; `BenchmarkService(CaseRunner, List<String>,
  LabReset)` passa o reset ao runner só se `request.reset()`. Ajustar chamadas existentes do
  construtor do record (testes e CLI).
  Contratos: `BenchmarkRequest` e `BenchmarkService` em `docs/contracts.md`.

- [ ] **7. Ligação no `Container` e no `Config`.**
  `Config.labResetCommands` = `[docker compose -f lab/docker-compose.yml down]`,
  `[... up -d]`; `Config.labReadyTimeout` = 180 s. `Container.benchmarkService(llm)` cria o
  `DockerComposeLabReset` com o `httpClient`. Coberto pelos testes existentes do `Container`
  (sem Docker: o reset só roda com `--reset`).

- [ ] **8. CLI: `--reset`, aviso e erro (red → green).**
  Testes em `BenchmarkCommandTest`: `resetRepassaParaOPedido`,
  `variasExecucoesSemResetAvisaNoStderr`, `umaExecucaoSemResetNaoAvisa`,
  `falhaNoResetSai2SemStackTrace`.
  Green: `@Option(names = "--reset")`; aviso no stderr quando `runs > 1 && !reset`; a
  `IllegalStateException` do reset já cai no `catch (RuntimeException)` → saída 2.
  Verificar: `mvn test -Dtest=BenchmarkCommandTest`.

- [ ] **9. Docs.** `docs/cli.md` (linha da opção e aviso), `docs/experiment.md`
  (procedimento passo 1 e "Alvo resetado entre execuções" citam `--reset`; rodadas antigas
  foram com reset manual), README (comando do experimento com `--reset`), memória
  `ambiente-rodadas-llm` (o reset deixa de ser manual).

- [ ] **10. `mvn clean verify`.** Todos os testes e a regra de dependência verdes.

- [ ] **11. Checkpoint empírico** (lab real, `systemd-inhibit`, na tomada):
  `java -jar target/vulnscan.jar benchmark --ground-truth experiments/ground_truth.yaml
  --runs 2 --reset --llm api --out experiments/results/<data>-issue-60-reset`. Conferir:
  contêineres recriados 2 vezes, nenhum `ERRO` nos `run-*.csv`, guestbook do DVWA com os
  payloads de uma só execução ao final, métricas coerentes com a rodada de referência da #72.

# Spec — Reset do laboratório entre as execuções do benchmark (issue #60)

## Objetivo

Fazer o `benchmark --runs N` seguir o protocolo (`docs/experiment.md`, "Alvo resetado entre
execuções") sem trabalho manual: com `--reset`, o próprio benchmark volta o laboratório ao
estado de fábrica **antes de cada execução**, espera os alvos responderem e só então roda os
casos. O resultado das N execuções sai num único `summary.csv` consolidado, em vez de N
`--runs 1` separados.

Hoje as N execuções rodam com o banco já sujo pela anterior (XSS armazenado no guestbook do
DVWA), o que pode contaminar o resultado a partir da segunda execução.

## Escopo

1. **Opção `--reset`** no `benchmark` (booleana, desligada por padrão). Ligada: antes de cada
   execução (inclusive a 1ª), o benchmark
   1. roda `docker compose -f lab/docker-compose.yml down` e depois `... up -d` (comandos
      fixados em `app/Config.java`, sem shell, via `ProcessBuilder`; caminho relativo ao
      diretório atual, como o resto da documentação);
   2. espera cada `baseUrl` do gabarito responder a um `GET` (qualquer status HTTP serve:
      o DVWA responde 302 para o login), com intervalo de 2 s e prazo de 180 s
      (`Config.labReadyTimeout`);
   3. esquece as sessões guardadas pelo runner (o cookie do DVWA por nível de `security`),
      que deixam de valer depois do reset. O banco do DVWA é criado de novo pelo login,
      como já acontece desde a #59.
2. **Falha no reset aborta o benchmark** (saída `2`, mensagem sem stack trace): comando com
   código de saída ≠ 0 (a mensagem cita o comando e o código) ou alvo que não responde no
   prazo (a mensagem cita a URL). Rodar sem o lab só produziria linhas `ERRO`.
3. **Aviso sem `--reset`**: com `--runs > 1` e sem `--reset`, a CLI escreve no stderr que o
   laboratório não será resetado entre as execuções (o protocolo exige o reset) e segue.
   Com `--runs 1` não avisa.
4. Docs: `docs/cli.md` (opção nova), `docs/experiment.md` (procedimento e reprodutibilidade
   passam a citar `--reset`), `docs/contracts.md`, README (comando do experimento) e a
   memória de ambiente das rodadas.

### Fora do escopo

- Comando `benchmark merge` para consolidar CSVs (alternativa 2 da issue): com o reset
  embutido, deixa de ser necessário.
- `--reset-cmd` com comando arbitrário: o lab do projeto é um só; um comando livre seria
  mais um jeito de errar o protocolo.
- Reset entre **casos** (só entre execuções, como diz o protocolo).
- Mudar o default de `--runs` ou ligar `--reset` por padrão (exigiria Docker em todo uso do
  `benchmark`).

## Contratos tocados (`docs/contracts.md`)

- **Novo** `app/benchmark/LabReset` — costura do experimento (não é porta: fica em `app/`,
  como o `CaseRunner`; justifica-se pelo dublê nos testes do `BenchmarkRunner`):
  ```java
  public interface LabReset {
      /** Volta o lab ao estado de fábrica e espera os alvos responderem; falha → IllegalStateException. */
      void reset(java.util.List<java.net.URI> baseUrls);
      LabReset NONE = baseUrls -> { };
  }
  ```
  Implementação real: `app/benchmark/DockerComposeLabReset` (roda os comandos e espera os
  alvos via a porta `HttpClient`).
- `CaseRunner`: novo `default void forgetSessions() { }`; o `LabCaseRunner` limpa o cache de
  cookies do DVWA.
- `BenchmarkRunner`: novo construtor
  `BenchmarkRunner(GroundTruth, CaseRunner, List<String> detectors, int runs, LabReset reset)`;
  o atual delega com `LabReset.NONE`.
- `BenchmarkRequest`: novo componente `boolean reset`
  (`BenchmarkRequest(Path groundTruth, int runs, Path outDir, boolean reset)`).
- `BenchmarkService`: recebe o `LabReset` real no construtor e o usa só se `request.reset()`.
- `Config`: `labResetCommands` (lista de comandos) e `labReadyTimeout` (180 s).

## Critérios de aceite (viram testes)

1. `BenchmarkRunnerTest#resetaOLabAntesDeCadaExecucao`: com um `LabReset` falso e
   `runs = 3`, o reset é chamado 3 vezes, cada vez **antes** dos casos daquela execução, com
   as `baseUrl` do gabarito.
2. `BenchmarkRunnerTest#esqueceAsSessoesDepoisDoReset`: `forgetSessions()` do runner é
   chamado depois de cada reset.
3. `BenchmarkRunnerTest#falhaNoResetAbortaOBenchmark`: reset que lança
   `IllegalStateException` propaga a exceção e nenhum caso roda.
4. `BenchmarkRunnerTest` (testes atuais): sem `LabReset`, o comportamento não muda.
5. `LabCaseRunnerTest#forgetSessionsFazNovoLoginNoDvwa`: depois de `forgetSessions()`, o
   próximo caso do DVWA loga de novo (2 logins no total, em vez de 1).
6. `DockerComposeLabResetTest` (comandos reais e inofensivos via `sh -c`; `HttpClient` falso,
   como nos demais testes de `app/`):
   - `rodaOsComandosEmOrdemEEsperaOAlvo`: dois comandos que anotam num arquivo temporário e
     alvo respondendo → ordem certa, cada `baseUrl` consultada, sem exceção;
   - `comandoComFalhaAbortaCitandoOComandoEOCodigo`: comando `false` → exceção com
     `false` e `1` na mensagem; o alvo não é consultado;
   - `alvoQueNaoRespondeNoPrazoAborta`: cliente que sempre falha e prazo curto → exceção com a URL;
   - `alvoQueSobeDepoisDeAlgumasTentativasPassa`: cliente HTTP falso que falha 2 vezes e
     depois responde → sem exceção.
7. `BenchmarkServiceTest`: `reset = true` chama o `LabReset` injetado; `reset = false` não.
8. `BenchmarkCommandTest`:
   - `resetRepassaParaOPedido`: `--reset` vira `request.reset() == true`;
   - `variasExecucoesSemResetAvisaNoStderr`: `--runs 2` sem `--reset` → aviso no stderr e
     saída `0`;
   - `umaExecucaoSemResetNaoAvisa`;
   - `falhaNoResetSai2SemStackTrace`.
9. `mvn clean verify` verde (inclui a regra de dependência).
10. **Checkpoint empírico** (manual, com o lab real): `benchmark --runs 2 --reset --llm api`
    (a API é mais rápida que o Ollama; o benchmark sempre roda os dois detectores). Conferir
    que o lab foi derrubado e recriado duas vezes (`docker ps` / horário de criação dos
    contêineres), que nenhuma linha saiu `ERRO` e que, ao final, o guestbook do DVWA só tem
    os payloads de uma execução.

## Referências

- Issue #60; protocolo em `docs/experiment.md`; criação do banco do DVWA na #59.

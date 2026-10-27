# Plano — cli/Main com o subcomando scan (issue #7)

Spec: `spec.md` nesta pasta. Branch: `issue-7-cli-scan`.

- Produção: `src/main/java/br/ufv/vulnscan/cli/Main.java` e `ScanCommand.java`.
- Teste: `src/test/java/br/ufv/vulnscan/cli/MainTest.java`. Helper `run(Main, String...)`
  que cria `CommandLine`, liga `setOut`/`setErr` a `StringWriter`s e devolve código +
  saídas. Fakes aninhados `FakeModule` (anota nome, pode lançar) e `FakeWriter` (guarda
  o `Path`), usados para montar um `ScanService` real injetado em `Main(Supplier)`.

Cada passo: teste primeiro (red por asserção), mínimo para passar (green), refactor.
Verificação: `mvn test -Dtest=MainTest`.

## Passos

- [x] **1. Esqueleto + caminho feliz.**
  - `Main` e `ScanCommand` com `call()` devolvendo `-1` (compila, red por asserção).
  - Teste: `#scanGravaRelatorioESai0` (Container real, `--out` em `@TempDir`).
  - Green: opções, `ScanRequest`, `ScanService.scan`, resumo em stdout, `return 0`.

- [x] **2. Opções.**
  - Testes: `#semOutUsaReportPontoFormato`, `#moduloRepetivelPassaTodos`.
  - Green: resolver `out`; `--module` como lista.
  - *Desvio:* `#moduloRepetivelPassaTodos` passou de cara (o `List<String>` do passo 1
    já acumula repetições no picocli); só `#semOutUsaReportPontoFormato` teve red.

- [x] **3. Erros de uso do picocli.**
  - Testes: `#semTargetSai1`, `#opcaoDesconhecidaSai1`, `#targetMalformadoSai1`,
    `#semSubcomandoSai1`.
  - Green: `exitCodeOnInvalidInput = 1` nas anotações; `Main.call()` imprime uso e
    devolve `1`.

- [x] **4. Erros do scan.**
  - Testes: `#pedidoInvalidoSai1SemStackTrace`, `#erroDeExecucaoSai2SemStackTrace`.
  - Green: `catch InvalidScanRequestException` → `1`; `catch RuntimeException` → `2`;
    `erro: <mensagem>` em stderr. `exitCodeOnExecutionException = 2` como rede de
    segurança.

- [x] **5. Aviso de localhost.**
  - Testes: `#alvoForaDeLocalhostImprimeAviso`, `#alvoLocalNaoImprimeAviso`
    (parametrizado).
  - Green: `isLocal(host)` (localhost, 127.0.0.0/8, `[::1]`), sem DNS.

- [x] **6. Ajuda.**
  - Testes: `#helpSai0`, `#scanHelpSai0`. Devem passar com `usageHelp`; mordida trocando
    temporariamente `exitCodeOnUsageHelp`.
  - *Desvio:* teve red de verdade (ainda não havia `--help`, saída `1`), então a mordida
    não foi necessária. As opções ganharam `description`/`paramLabel` para a ajuda ser útil.

- [x] **7. Refactor e Javadoc** (camada: CLI, adaptador de entrada).

- [x] **8. Checkpoint.** `mvn clean verify` verde; execução manual do jar
  (`java -jar target/vulnscan.jar scan --target http://localhost:3000 --out <tmp>`),
  saída `0` e relatório gravado. Marcar passos; commits; PR fechando #7.
  - Feito: saída `0` e `report.md` gravado; `--module sqli` → `erro: módulo desconhecido:
    sqli (disponíveis: )`, saída `1` (registro vazio até a #8); sem `--target` → `1`;
    `http://example.com` → aviso e `0`; `scan --help` → `0`.

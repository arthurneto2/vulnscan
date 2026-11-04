# Plano — SqlInjectionModule e arquivo de payloads (issue #10)

Spec: `spec.md` nesta pasta. Branch: `claude/analise-proxima-issue-2y3kiw`.

Cada passo: teste primeiro (red por asserção), mínimo para passar (green), refactor.

## Passos

- [x] **1. Headers no `Target`.** Teste `TargetTest#construtorSemHeadersDaMapaVazio`,
  `#headersSaoCopiadosEImutaveis`, `#headersNulosViramVazio`. Green: componente
  `headers`, construtor compacto e secundário. Atualizar `docs/contracts.md`.
  Verificar: `mvn test -Dtest=TargetTest`.

- [x] **2. `PayloadFile.parse`.** `PayloadFileTest`: `#leLinhasValidas`,
  `#ignoraVaziasEComentarios`, `#linhaMalformadaCitaNumeroDaLinha`,
  `#idRepetidoFalha`, `#campoVazioFalha`. Verificar: `mvn test -Dtest=PayloadFileTest`.

- [x] **3. `sqli.txt`.** `PayloadFileTest#arquivoSqliDoClasspathTemAspasBooleanoETemporal`
  (lê `/payloads/sqli.txt` via `getResourceAsStream`). Green: criar o arquivo.

- [x] **4. `InjectionPoint` + envio de requisições.** `SqlInjectionModuleTest` com
  `HttpClient` fake que grava e `Detector` fake configurável:
  `#enviaGetComPayloadCodificadoNoParametro`, `#respeitaQueryFixaDoPath`,
  `#enviaHeadersDoAlvo`, `#typeESqlInjection`.

- [x] **5. Evidência e vereditos.** `#evidenciaPassadaAoDetector`,
  `#semPositivoNaoReportaETentaTudo`, `#positivoViraFindingHighEParaNoPonto`,
  `#positivoEmUmPontoNaoImpedeOutro`, `#comHeuristicDetectorRealDetectaErroMysql`.

- [x] **6. Erros e validações.** `#erroDoHttpClientPropaga`, `#construtorRejeitaNulos`,
  `#construtorRejeitaListasVazias`, `#construtorRejeitaPayloadDeOutroTipo`. Javadoc.
  Atualizar `docs/contracts.md` (InjectionPoint, construtor, PayloadFile).
  Verificar: `mvn test -Dtest=SqlInjectionModuleTest`.

- [x] **7. Headers no `ScanRequest`/`ScanService`.** `ScanServiceTest#passaHeadersAoTarget`
  (módulo fake grava o `Target`). Green: componente + construtor de 4 argumentos.

- [x] **8. `--header` na CLI.** `MainTest#headerRepetivelChegaAoScanRequest`,
  `#headerSemDoisPontosSai1`, `#headerRepetidoJuntaPorVirgula`. Atualizar `docs/cli.md`.
  Verificar: `mvn test -Dtest=MainTest`.

- [x] **9. `Container` com `sqli`; remove `smoke`.** `ContainerTest`:
  `#moduloSqliDetectaErroMysqlNoPontoDoDvwa` (servidor em processo devolve erro de MySQL
  em `/vulnerabilities/sqli/`), `#semModulosRodaSqli`, `#moduloSmokeNaoExisteMais`.
  Ajustar `MainTest#scanGravaRelatorioESai0`. Apagar `SmokeModule` e `SmokeModuleTest`.

- [x] **10. Checkpoint.** `mvn clean verify` verde. Scan real no DVWA e no Juice Shop do lab. Marcar na Fase 2 do `build-order`
  os itens do módulo, do arquivo de payloads e o checkpoint
  da fase. Marcar passos aqui, commits, push.
  - Feito: `mvn clean verify` com 125 testes verdes (inclui `DependencyRuleTest`).
  - Lab (Docker local, 2026-11-04), `java -jar target/vulnscan.jar scan --module sqli`:
    DVWA `security=low` (com `--header "Cookie: ..."`) → 1 achado, `sqli-quote-single`,
    erro de MySQL; DVWA `security=impossible` → 0 achados; Juice Shop → 1 achado, erro de
    SQLite.

## Desvios do plano

- Passo 6 também cobre `#listasSaoCopiadas` (o construtor copia as listas).
- `MainTest#headerInvalidoSai1SemRodar` é parametrizado (sem `:`, nome vazio, nome em branco).

# Plano — SqlInjectionModule e arquivo de payloads (issue #10)

Spec: `spec.md` nesta pasta. Branch: `claude/analise-proxima-issue-2y3kiw`.

Cada passo: teste primeiro (red por asserção), mínimo para passar (green), refactor.

## Passos

- [ ] **1. Headers no `Target`.** Teste `TargetTest#construtorSemHeadersDaMapaVazio`,
  `#headersSaoCopiadosEImutaveis`, `#headersNulosViramVazio`. Green: componente
  `headers`, construtor compacto e secundário. Atualizar `docs/contracts.md`.
  Verificar: `mvn test -Dtest=TargetTest`.

- [ ] **2. `PayloadFile.parse`.** `PayloadFileTest`: `#leLinhasValidas`,
  `#ignoraVaziasEComentarios`, `#linhaMalformadaCitaNumeroDaLinha`,
  `#idRepetidoFalha`, `#campoVazioFalha`. Verificar: `mvn test -Dtest=PayloadFileTest`.

- [ ] **3. `sqli.txt`.** `PayloadFileTest#arquivoSqliDoClasspathTemAspasBooleanoETemporal`
  (lê `/payloads/sqli.txt` via `getResourceAsStream`). Green: criar o arquivo.

- [ ] **4. `InjectionPoint` + envio de requisições.** `SqlInjectionModuleTest` com
  `HttpClient` fake que grava e `Detector` fake configurável:
  `#enviaGetComPayloadCodificadoNoParametro`, `#respeitaQueryFixaDoPath`,
  `#enviaHeadersDoAlvo`, `#typeESqlInjection`.

- [ ] **5. Evidência e vereditos.** `#evidenciaPassadaAoDetector`,
  `#semPositivoNaoReportaETentaTudo`, `#positivoViraFindingHighEParaNoPonto`,
  `#positivoEmUmPontoNaoImpedeOutro`, `#comHeuristicDetectorRealDetectaErroMysql`.

- [ ] **6. Erros e validações.** `#erroDoHttpClientPropaga`, `#construtorRejeitaNulos`,
  `#construtorRejeitaListasVazias`, `#construtorRejeitaPayloadDeOutroTipo`. Javadoc.
  Atualizar `docs/contracts.md` (InjectionPoint, construtor, PayloadFile).
  Verificar: `mvn test -Dtest=SqlInjectionModuleTest`.

- [ ] **7. Headers no `ScanRequest`/`ScanService`.** `ScanServiceTest#passaHeadersAoTarget`
  (módulo fake grava o `Target`). Green: componente + construtor de 4 argumentos.

- [ ] **8. `--header` na CLI.** `MainTest#headerRepetivelChegaAoScanRequest`,
  `#headerSemDoisPontosSai1`, `#headerRepetidoJuntaPorVirgula`. Atualizar `docs/cli.md`.
  Verificar: `mvn test -Dtest=MainTest`.

- [ ] **9. `Container` com `sqli`; remove `smoke`.** `ContainerTest`:
  `#moduloSqliDetectaErroMysqlNoPontoDoDvwa` (servidor em processo devolve erro de MySQL
  em `/vulnerabilities/sqli/`), `#semModulosRodaSqli`, `#moduloSmokeNaoExisteMais`.
  Ajustar `MainTest#scanGravaRelatorioESai0`. Apagar `SmokeModule` e `SmokeModuleTest`.

- [ ] **10. Checkpoint.** `mvn clean verify` verde. Marcar na Fase 2 do `build-order`
  os itens do módulo e do arquivo de payloads; o checkpoint de laboratório (DVWA) fica
  descrito no PR para execução manual. Marcar passos aqui, commits, push.

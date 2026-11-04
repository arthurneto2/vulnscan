# Plano — XssModule: XSS refletido (issue #11)

Spec: `spec.md` nesta pasta. Branch: `issue-11-xss-refletido`.

Cada passo: teste primeiro (red por asserção), mínimo para passar (green), refactor.

## Passos

- [x] **1. `InjectionPoint.url` (refactor guiado por teste).** `InjectionPointTest`:
  `#pathSemQueryUsaInterrogacao`, `#pathComQueryFixaUsaEComercial`,
  `#valorEhCodificadoEmUtf8`. Green: método no record; `SqlInjectionModule` passa a usá-lo
  e perde o `url` privado. Atualizar `docs/contracts.md`.
  Verificar: `mvn test -Dtest='InjectionPointTest,SqlInjectionModuleTest'`.

- [x] **2. `xss.txt`.** `PayloadFileTest#arquivoXssDoClasspathTemMarcadorEmTodos`. Green:
  criar o arquivo.

- [x] **3. `XssModule`: requisições e marcador.** `XssModuleTest` (fakes de
  `HttpClient`, `Detector` e `Supplier<String>` sequencial `m1`, `m2`, ...):
  `#typeEXssRefletido`, `#enviaGetComMarcadorSubstituidoECodificado`,
  `#enviaHeadersDoAlvo`, `#cadaRequisicaoUsaMarcadorNovo`,
  `#evidenciaTrazPayloadConcreto`.

- [x] **4. Vereditos.** `#semPositivoNaoReportaETentaTudo`,
  `#positivoViraFindingMediumEParaNoPonto`, `#positivoEmUmPontoNaoImpedeOutro`.

- [x] **5. Erros e validações.** `#erroDoHttpClientPropaga`, `#construtorRejeitaNulos`,
  `#construtorRejeitaListasVazias`, `#construtorRejeitaPayloadDeOutroTipo`,
  `#construtorRejeitaPayloadSemMarcador`, `#listasSaoCopiadas`. Javadoc. Atualizar
  `docs/contracts.md`. Verificar: `mvn test -Dtest=XssModuleTest`.

- [x] **6. `Container` registra `xss`.** `ContainerTest#moduloXssAtacaDvwaEJuiceShopComMarcadoresDistintos`
  (servidor em processo grava as queries), `#semModulosRodaSqliEXss`. Green: registro,
  pontos e marcador `vsx` + 8 hex.

- [x] **7. Checkpoint.** `mvn clean verify` verde. Lab: `scan --module xss` no DVWA e no
  Juice Shop (saída 0, 0 achados até a #13) e `curl` confirmando a reflexão crua no DVWA
  `low`. Marcar na Fase 3 do `build-order` o item do `XssModule` (refletido). Marcar
  passos aqui, commits, push.
  - Feito: `mvn clean verify` com 144 testes verdes (inclui `DependencyRuleTest`).
  - Lab (2026-11-04): `scan --module xss` no DVWA `low` (com `--header "Cookie: ..."`) e no
    Juice Shop → saída 0, 0 achados (sem regras de XSS até a #13); `scan --module sqli` no
    DVWA continua com 1 achado. `curl` com marcador `vsx…`: DVWA `low` devolve
    `<svg onload=alert('vsx…')>` cru; Juice Shop `/redirect` devolve escapado
    (`&lt;svg ...&gt;`).

## Desvios do plano

- Nenhum no código. Decisão registrada na spec: XSS de DOM do Juice Shop vai para a #12.

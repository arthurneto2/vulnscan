# Plano — XSS armazenado e de DOM via Selenium (issue #12)

Spec: `spec.md` nesta pasta. Branch: `issue-12-xss-armazenado-selenium`.

Cada passo: teste primeiro (red por asserção), mínimo para passar (green), refactor.

## Passos

- [x] **1. Regra 0 do detector.** `HttpResponse.DIALOGS_HEADER`. `HeuristicDetectorTest`:
  `#dialogoComMarcadorEVulneravel` (parametrizado refletido/armazenado),
  `#dialogoSemMarcadorSegueRegrasDoCorpo`. Atualizar `docs/contracts.md`.
  Verificar: `mvn test -Dtest=HeuristicDetectorTest`.

- [x] **2. `RenderedPage` e `XssBrowser`.** `RenderedPageTest#dialogosCopiadosEImutaveis`,
  `#dialogosNulosViramVazio`.

- [ ] **3. `DomXssModule`.** `DomXssModuleTest`: `#typeEXssRefletido`,
  `#abreUrlComFragmentoEMarcador`, `#passaHeadersDoAlvoAoNavegador`,
  `#respostaSinteticaComDomEDialogos`, `#semDialogoNaoPoeHeader`,
  `#cadaAberturaUsaMarcadorNovo`, `#positivoViraFindingMediumEParaNoPonto`,
  `#erroDoNavegadorPropaga`, validações do construtor.

- [ ] **4. `StoredXssPoint` e `StoredXssModule`.** `StoredXssModuleTest`:
  `#typeEXssArmazenado`, `#postaFormularioComPayloadEHeaders`,
  `#abreAPaginaDeVisualizacaoDepoisDoPost`, `#evidenciaTemOPostEAPaginaRenderizada`,
  `#positivoViraFindingHighEParaNoPonto`, `#erroDoHttpPropaga`,
  `#erroDoNavegadorPropaga`, validações. Atualizar `docs/contracts.md`.

- [ ] **5. `SeleniumBrowser`.** `SeleniumBrowserTest` (servidor do JDK em porta efêmera;
  `assumeTrue` se o Chrome não subir): `#devolveDomDepoisDoJavascript`,
  `#capturaTextoDoAlert`, `#enviaCookieDoHeader`.

- [ ] **6. `Container`.** `ContainerTest#modulosDisponiveisIncluemXssDomEXssStored`.
  Atualizar `docs/cli.md`.

- [ ] **7. Checkpoint.** `mvn clean verify` verde. Lab: `xss-dom` no Juice Shop (1),
  `xss-stored` no DVWA `low` (1) e `medium` (0). Marcar a Fase 3 no `build-order`.
  Marcar passos aqui, commits, push.

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

- [x] **3. `DomXssModule`.** `DomXssModuleTest`: `#typeEXssRefletido`,
  `#abreUrlComFragmentoEMarcador`, `#passaHeadersDoAlvoAoNavegador`,
  `#respostaSinteticaComDomEDialogos`, `#semDialogoNaoPoeHeader`,
  `#cadaAberturaUsaMarcadorNovo`, `#positivoViraFindingMediumEParaNoPonto`,
  `#erroDoNavegadorPropaga`, validações do construtor.
  - *Refactor:* `MarkedPayloads` (validação e troca do `{marker}`, do pacote) passa a ser
    usado pelo `XssModule` também; resposta sintética em `RenderedPage.toResponse()`.

- [x] **4. `StoredXssPoint` e `StoredXssModule`.** `StoredXssModuleTest`:
  `#typeEXssArmazenado`, `#postaFormularioComPayloadEHeaders`,
  `#abreAPaginaDeVisualizacaoDepoisDoPost`, `#evidenciaTemOPostEAPaginaRenderizada`,
  `#positivoViraFindingHighEParaNoPonto`, `#erroDoHttpPropaga`,
  `#erroDoNavegadorPropaga`, validações. Atualizar `docs/contracts.md`.

- [x] **5. `SeleniumBrowser`.** `SeleniumBrowserTest` (servidor do JDK em porta efêmera;
  `assumeTrue` se o Chrome não subir): `#devolveDomDepoisDoJavascript`,
  `#capturaTextoDoAlert`, `#enviaCookieDoHeader`.

- [x] **6. `Container`.** `ContainerTest#modulosDisponiveisIncluemXssDomEXssStored`.
  Atualizar `docs/cli.md`.
  - *Desvio:* `#semModulosRodaSqliEXss` virou `#sqliEXssRodamJuntos` (módulos explícitos),
    porque "todos" agora abre o navegador e o `ContainerTest` não deve depender de Chrome.

- [x] **7. Checkpoint.** `mvn clean verify` verde. Lab: `xss-dom` no Juice Shop (1),
  `xss-stored` no DVWA `low` (1) e `medium` (0). Marcar a Fase 3 no `build-order`.
  Marcar passos aqui, commits, push.
  - Feito: `mvn clean verify` com 179 testes verdes — no ambiente padrão os 3 do
    `SeleniumBrowserTest` são pulados (o `chromedriver` 147 do PATH não serve para o
    Chrome 153); sem ele no PATH, rodam e passam (0 pulados).
  - Lab (2026-11-04), com o driver certo: `xss-dom` no Juice Shop → 1 achado (`xss-script`,
    regra "cru em HTML" sobre o DOM renderizado); `xss-stored` no DVWA `low` → 1 achado
    ("o navegador executou o payload: diálogo com o marcador"); `medium` → 0; `xss-dom` no
    DVWA (sem a rota) → 0.
  - Observação para o experimento: no Juice Shop o primeiro positivo veio do `<script>`
    inserido via `innerHTML` (está no DOM, mas não executa); o `<img onerror>` executaria.
    O alvo é vulnerável, mas a evidência desse payload é mais fraca que a de um diálogo.

## Desvios do plano

- `MarkedPayloads` e `RenderedPage.toResponse()` (refactor do passo 3).
- `ContainerTest#sqliEXssRodamJuntos` e `MainTest#scanGravaRelatorioESai0` passam a pedir
  módulos explícitos: "todos" agora abre o navegador.

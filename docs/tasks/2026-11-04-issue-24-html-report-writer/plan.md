# Plano — HtmlReportWriter (issue #24)

Spec: `spec.md` nesta pasta. Branch: `issue-24-html-report-writer`.
Cada passo: teste → mínimo → refactor.

## Passos

- [x] **1. format() + esqueleto HTML.** `HtmlReportWriterTest#formatEhHtml`,
  `#semAchadosTemCabecalhoEDoctype`. Green: classe, `format()`="html", HTML base
  (doctype, charset, alvo, duração, "Nenhum achado.").

- [x] **2. Achado + escape.** `#comAchadoTrazVeredito`, `#escapaHtmlDoPayloadENaoInjeta`.
  Green: render do achado com escape de `& < > " '` em todo texto do alvo/payload.

- [x] **3. Truncamento + I/O.** `#corpoGrandeTruncado`, `#criaDiretorioPaiEUtf8`,
  `#erroDeIoViraUnchecked`. Green: truncar corpos; `Files.createDirectories`; UTF-8.

- [ ] **4. Container + checkpoint.** Registrar `html`; `ContainerTest#formatoHtmlGravaHtml`.
  `docs/contracts.md`. `mvn clean verify` verde. Marcar passos, commits, push.

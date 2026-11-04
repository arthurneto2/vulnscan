# Plano — JsonReportWriter (issue #23)

Spec: `spec.md` nesta pasta. Branch: `issue-23-json-report-writer`.
Cada passo: teste → mínimo → refactor.

## Passos

- [x] **1. format() + esqueleto.** `JsonReportWriterTest#formatEhJson`. Green: classe com
  `format()` = "json" e `write` mínimo.

- [x] **2. Sem achados.** `#semAchadosGravaJsonValido` (parseia; `findingCount` 0, `findings`
  []; target e datas). Green: monta a árvore base com Jackson + JavaTimeModule (ISO-8601).

- [x] **3. Com achado.** `#comAchadoTrazTudo` (tipo, severidade, veredito+detector, payload,
  request/response; `durationMillis`). Green: serializa cada finding.

- [x] **4. Truncamento + I/O.** `#corpoGrandeTruncado`, `#criaDiretorioPaiEUtf8`,
  `#erroDeIoViraUnchecked`. Green: truncar corpos; `Files.createDirectories`; UTF-8.

- [x] **5. Container + checkpoint.** Registrar `json` no `Container`;
  `ContainerTest#formatoJsonGravaJson`. `docs/contracts.md`. `mvn clean verify` verde.
  Marcar passos, commits, push.
  - Feito: `mvn clean verify` com 263 testes verdes; `JsonReportWriterTest` (6, @TempDir) e
    `ContainerTest#formatoJsonGravaJson`. `--format json` registrado no Container.

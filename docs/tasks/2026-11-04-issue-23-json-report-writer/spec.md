# Spec — JsonReportWriter (issue #23)

Referência: issue #23 (Fase 7 de `docs/build-order.md`). Depende de #5 (porta `ReportWriter`
e o `MarkdownReportWriter`). Adaptador de `ReportWriter` com formato `json`.

## Objetivo

Gravar o `ScanReport` em JSON (Jackson), para consumo por máquina. Mesmo contrato do
Markdown: cria diretório pai, sobrescreve, UTF-8; erro de I/O → `UncheckedIOException`.

## Escopo — `adapter/report/JsonReportWriter implements ReportWriter`

- `format()` → `"json"`.
- `write(report, output)`: serializa o `ScanReport` com Jackson (`ObjectMapper` +
  `JavaTimeModule` para os `Instant`, gravados em ISO-8601 — `WRITE_DATES_AS_TIMESTAMPS`
  desligado), indentado. Estrutura:
  - `target`: `{ name, baseUrl, headers }`;
  - `startedAt`, `finishedAt` (ISO-8601 UTC), `durationMillis`, `findingCount`;
  - `findings`: lista de `{ type, severity, verdict{vulnerable,confidence,rationale,detector},
    payload{id,type,value,description}, request{method,url,headers,body},
    response{status,headers,body,elapsedMillis} }`.
  - Corpos de requisição/resposta **truncados** ao mesmo limite do Markdown
    (`MAX_BODY_CHARS = 2000`), com marca de truncamento, para o JSON não explodir com corpos
    grandes. Headers como objeto (mapa).
- Monta a árvore à mão (não serializa os records direto), para controlar nomes de campo,
  truncamento e a URI como string. Cria o diretório pai; UTF-8; erro de I/O →
  `UncheckedIOException`.

## Fora do escopo

- `HtmlReportWriter` (#24). Registrar o writer no `Container` (a lista de writers já aceita
  vários; registro de `json`/`html` é decisão de acabamento — incluo `json` no `Container`
  aqui para `--format json` funcionar, com teste). Esquema versionado/estável para terceiros.

## Contratos tocados

- Novo adaptador `JsonReportWriter` (porta `ReportWriter` inalterada); citado em
  `docs/contracts.md` na lista de adaptadores. `Container` passa a registrar `md` e `json`.

## Critérios de aceite (`JsonReportWriterTest`, `@TempDir`)

1. `format()` é `"json"`.
2. Relatório sem achados → JSON válido com `findingCount: 0` e `findings: []`; `target` e
   datas presentes.
3. Relatório com um achado → o JSON parseia e traz tipo, severidade, veredito (com
   `detector`), payload e a troca HTTP; `durationMillis` correto.
4. Corpo de resposta grande → truncado no JSON (marca presente; tamanho ≤ limite + margem).
5. Cria diretório pai; sobrescreve; UTF-8 (caractere acentuado no corpo preservado).
6. Erro de I/O (saída é diretório) → `UncheckedIOException`.
7. `ContainerTest`: `--format json` grava um `.json` (writer registrado).
8. `mvn clean verify` verde, incluindo `DependencyRuleTest`.

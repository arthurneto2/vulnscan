# Spec — HtmlReportWriter (issue #24)

Referência: issue #24 (Fase 7 de `docs/build-order.md`). Depende de #5 (porta `ReportWriter`).
Adaptador de `ReportWriter` com formato `html`.

## Objetivo

Gravar o `ScanReport` como uma página HTML legível (um arquivo, sem dependências externas),
para anexar ao relatório/apresentação. Mesmo contrato de I/O dos outros writers.

## Escopo — `adapter/report/HtmlReportWriter implements ReportWriter`

- `format()` → `"html"`.
- `write(report, output)`: monta um HTML5 autossuficiente (UTF-8, `<style>` embutido):
  - cabeçalho: alvo (nome + baseUrl), início/fim (ISO-8601), duração em ms, total de achados;
  - sem achados → uma linha "Nenhum achado.";
  - cada achado: tipo + severidade, veredito (vulnerável/não, confiança com 2 casas,
    detector), justificativa, payload (id + descrição + valor), e a troca HTTP (requisição e
    resposta) em blocos `<pre>`.
- **Escape de HTML obrigatório.** Todo texto vindo do alvo/payload (corpos, headers, valor do
  payload, justificativa, URL) passa por escape (`& < > " '`), senão um payload de XSS
  refletido no corpo quebra ou injeta a própria página do relatório. Esta é a regra central
  do writer.
- Corpos truncados ao mesmo limite dos outros (`MAX_BODY_CHARS = 2000`), com marca.
- Cria o diretório pai, sobrescreve, UTF-8 (com `<meta charset>`); erro de I/O →
  `UncheckedIOException`.

## Fora do escopo

- Gráficos/JS; CSS externo; relatório do benchmark (isto é do scan). Registrar `html` no
  `Container` entra aqui (para `--format html`), com teste.

## Contratos tocados

- Novo adaptador `HtmlReportWriter` (porta inalterada); citado em `docs/contracts.md`.
  `Container` passa a registrar `md`, `json` e `html`. Fecha a lista de writers da Fase 7.

## Critérios de aceite (`HtmlReportWriterTest`, `@TempDir`)

1. `format()` é `"html"`.
2. Sem achados → HTML com `<!DOCTYPE html>`, `<meta charset="utf-8">`, o alvo, a duração e
   "Nenhum achado.".
3. Com achado → contém tipo, severidade, veredito (confiança 2 casas, detector),
   justificativa, id do payload e o status da resposta.
4. **Escape:** payload/corpo com `<script>alert('x')</script>` aparece escapado
   (`&lt;script&gt;`), e **não** como tag `<script>` crua no HTML.
5. Corpo grande → truncado (marca presente).
6. Cria diretório pai; sobrescreve; UTF-8 (acento preservado).
7. Erro de I/O (saída é diretório) → `UncheckedIOException`.
8. `ContainerTest`: `--format html` grava um `.html`.
9. `mvn clean verify` verde, incluindo `DependencyRuleTest`.

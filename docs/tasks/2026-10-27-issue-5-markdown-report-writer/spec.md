# Spec — MarkdownReportWriter (issue #5)

Referência: issue #5 (milestone "Fase 1 — HTTP ponta a ponta"), item
"`adapter/report/MarkdownReportWriter`" da Fase 1 em `docs/build-order.md`. Saída usada
pelo checkpoint da fase (`scan` grava um `report.md`).

## Objetivo

Adaptador da porta `ReportWriter` que grava um `ScanReport` em Markdown legível: alvo,
período e cada achado com veredito e evidência. É o relatório padrão da CLI
(`--format md`, `docs/cli.md`) e o que vai como evidência de execução no trabalho.

## Escopo

`br.ufv.vulnscan.adapter.report.MarkdownReportWriter implements ReportWriter`:

- `format()` → `"md"`.
- `write(report, output)`: `output` é o **arquivo** de saída (a CLI passa `--out`, padrão
  `report.md`). Cria os diretórios pais se faltarem; sobrescreve se existir; UTF-8.
- Erro de I/O → `UncheckedIOException` (mesma convenção do `JdkHttpClient`).

### Formato

````markdown
# Relatório de scan — juice-shop

- **Alvo:** juice-shop (http://localhost:3000)
- **Início:** 2026-10-27T12:00:00Z
- **Fim:** 2026-10-27T12:00:03.500Z
- **Duração:** 3500 ms
- **Achados:** 1

## Achados

### 1. SQL_INJECTION — HIGH

- **Veredito:** vulnerável (confiança 0.92, detector `heuristic`)
- **Justificativa:** erro de sintaxe SQL do MySQL no corpo da resposta
- **Payload:** `sqli-quote` — aspa simples para quebrar a query

```text
'
```

**Requisição**

```http
POST http://localhost:3000/rest/user/login
content-type: application/json

{"email":"'","password":"x"}
```

**Resposta** (500, 42 ms)

```http
content-type: text/html

<html>... You have an error in your SQL syntax ...
```
````

Regras:

- Instantes em ISO-8601 UTC (`Instant.toString()`); duração em ms entre início e fim.
- Achados numerados na ordem do `ScanReport`. Sem achados → seção traz `Nenhum achado.`
- Veredito: `vulnerável`/`não vulnerável`, confiança com 2 casas (`Locale.ROOT`).
- Justificativa em uma linha (quebras de linha viram espaço, para não quebrar a lista).
- Headers de requisição e resposta **ordenados por nome** (o `Map` não tem ordem;
  saída determinística facilita comparar relatórios).
- Requisição sem corpo/headers (`null`) → só a linha de método e URL.
- **Corpos truncados em 2000 caracteres**, com a linha
  `… (truncado: N caracteres no total)`. Páginas HTML inteiras tornariam o relatório
  ilegível; o corpo completo é assunto do experimento (CSVs), não do relatório.
- **Cerca de código maior que qualquer sequência de crases do conteúdo** (mínimo 3).
  Payloads de XSS e corpos HTML podem conter ```` ``` ````; sem isso o Markdown quebra.

## Fora do escopo

- JSON e HTML (issues #23 e #24).
- Escapar Markdown em nome do alvo/justificativa além da troca de quebras de linha.
- `Finding` com `evidence` ou `verdict` nulos: o contrato os trata como obrigatórios;
  nulos lançam `NullPointerException`.
- Resumo agregado por tipo/gravidade (pode entrar no acabamento, Fase 7).

## Contratos tocados

Nenhum. Implementa `ReportWriter` como está.

## Critérios de aceite (viram testes; gravam em `@TempDir`)

1. `format()` devolve `"md"`.
2. Cabeçalho: nome e URL do alvo, início e fim ISO, duração em ms, total de achados.
3. Relatório sem achados traz `Nenhum achado.`
4. Achado traz tipo, gravidade, veredito (`vulnerável`, confiança `0.92`, detector) e
   justificativa.
5. Evidência traz id/descrição/valor do payload, método+URL+header+corpo da requisição e
   status+tempo+header+corpo da resposta.
6. Headers saem ordenados por nome.
7. Requisição com corpo e headers nulos sai só com método e URL.
8. Corpo maior que 2000 caracteres é truncado com a nota do total.
9. Conteúdo com ```` ``` ```` fica dentro de uma cerca de 4 crases.
10. Vários achados numerados na ordem.
11. Justificativa com quebra de linha sai em uma linha.
12. Cria diretório pai inexistente e sobrescreve arquivo existente; texto em UTF-8.
13. `output` apontando para um diretório → `UncheckedIOException`.
14. `DependencyRuleTest` verde; `mvn clean verify` verde.

# Plano — MarkdownReportWriter (issue #5)

Spec: `spec.md` nesta pasta. Branch: `issue-5-markdown-report-writer`.

- Produção: `src/main/java/br/ufv/vulnscan/adapter/report/MarkdownReportWriter.java`.
- Teste: `src/test/java/br/ufv/vulnscan/adapter/report/MarkdownReportWriterTest.java`,
  gravando em `@TempDir` e lendo o arquivo com `Files.readString`. Fábricas de teste
  para `ScanReport`/`Finding` com valores fixos (instantes fixos, sem `Instant.now()`).

Cada passo: teste primeiro (red por asserção), mínimo para passar (green), refactor.
Verificação de cada passo: `mvn test -Dtest=MarkdownReportWriterTest`.

## Passos

- [x] **1. Esqueleto + `format()`.**
  - Teste: `#formatoEMd`. Red: stub com `format()` devolvendo `""` e `write` vazio.
  - Green: `"md"`.
  - *Desvio:* no green do passo 1 o stub de `write` passou a gravar um arquivo vazio,
    para que o red do passo 2 fosse por asserção e não `NoSuchFileException`.

- [x] **2. Cabeçalho e relatório vazio.**
  - Testes: `#gravaCabecalhoComAlvoPeriodoEDuracao`, `#semAchadosDizNenhumAchado`.
  - Green: `StringBuilder` + `Files.writeString(output, md, UTF_8)`.

- [x] **3. Achado com veredito.**
  - Testes: `#gravaAchadoComVeredito`, `#variosAchadosNumeradosNaOrdem`,
    `#justificativaEmUmaLinha`.
  - Green: seção por achado; `String.format(Locale.ROOT, "%.2f", ...)`;
    `rationale.replaceAll("\\R", " ")`.

- [x] **4. Evidência.**
  - Testes: `#gravaPayloadRequisicaoEResposta`, `#headersOrdenadosPorNome`,
    `#requisicaoSemCorpoNemHeaders`.
  - Green: blocos `text`/`http`; headers via `TreeMap`; nulos pulados.

- [x] **5. Truncamento e cerca.**
  - Testes: `#corpoGrandeTruncado`, `#cercaMaiorQueCrasesDoConteudo`.
  - Green: `truncate(String)` com limite 2000; `fence(String)` = maior sequência de
    crases do conteúdo + 1, mínimo 3.

- [x] **6. Arquivo.**
  - Testes: `#criaDiretorioPaiESobrescreve` (inclui texto com acento lido em UTF-8),
    `#saidaQueEDiretorioLancaUncheckedIOException`.
  - Green: `Files.createDirectories(parent)` quando `parent != null`; `catch IOException`
    → `UncheckedIOException`.
  - *Desvio:* o red de `#criaDiretorioPaiESobrescreve` foi a própria falha que o passo
    corrige (`NoSuchFileException` da pasta inexistente). `#saidaQueEDiretorio...` passou
    de cara porque o `catch` existe desde o passo 1; a mordida foi confirmada trocando o
    `throw` por `return` (falhou com "nothing was thrown").

- [x] **7. Refactor e Javadoc** (camada: adaptador de relatório).

- [x] **8. Checkpoint.** `mvn clean verify` verde (inclui `DependencyRuleTest`). Marcar
  passos; commits; PR fechando #5.

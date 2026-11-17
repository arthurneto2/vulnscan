# Plano — Falha de um módulo não derruba o scan (issue #58)

Spec: `spec.md` nesta pasta. Branch: `issue-58-falha-de-modulo-isolada`, a partir do `main` em
`fbab721`. Cada passo segue red pelo motivo esperado, green e refactor. Quando um passo muda
um contrato, `docs/contracts.md` é atualizado no mesmo passo.

## Passos

- [ ] **1. `ModuleError` e `ScanReport.errors`** (infra, contrato).
  - Criar o record `ModuleError`.
  - Pôr o novo componente `errors` no `ScanReport`, com o construtor de 4 argumentos
    (`List.of()`) e a cópia imutável.
  - Teste: `ScanReportTest#construtorSemErrosTemListaVazia` e `#errosImutaveis`.
  - Red: falha de compilação inevitável, porque o tipo ainda não existe. Primeiro crio o
    esqueleto, depois confirmo o red na asserção da imutabilidade.
  - Atualizar `docs/contracts.md`.

- [ ] **2. `Scanner` isola a falha (red → green).**
  - Testes: `falhaDeModuloViraErroEOsOutrosSeguem`, `semFalhaNaoTemErros`,
    `errorDaJvmPropaga`. O teste `excecaoDeModuloPropaga` sai.
  - Red: a exceção propaga, o que faz o teste falhar.
  - Green: try/catch de `RuntimeException` por módulo. A mensagem nula vira o nome da classe
    da exceção.
  - Refactor: Javadoc do `Scanner`.
  - Verificar: `mvn test -Dtest=ScannerTest`.

- [ ] **3. Relatórios mostram os erros (red → green).**
  - JSON: `errorCount` e `errors`.
  - MD e HTML: linha de resumo e a seção "Erros" só quando há erro, com escape no HTML.
  - Testes nos três `*ReportWriterTest`.

- [ ] **4. `ScanService` grava com erros** (teste de regressão).
  - Teste: `relatorioEhGravadoMesmoComModuloEmErro`, com fakes. Deve passar depois do passo 2.

- [ ] **5. CLI sai com 2 e lista os erros (red → green).**
  - Teste: `MainTest#scanComModuloEmErroSaiCom2EGravaORelatorio`.
  - Green: `ScanCommand` confere `report.errors()`.
  - Atualizar `docs/cli.md`.

- [ ] **6. `mvn clean verify`.**

- [ ] **7. Checkpoint manual** (lab real): `scan` contra o DVWA sem `--module`, com
  relatório parcial, o erro do `ApiAuthModule` e saída `2`.

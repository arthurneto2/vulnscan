# Plano — Falha de um módulo não derruba o scan (issue #58)

Spec: `spec.md` nesta pasta. Branch: `issue-58-falha-de-modulo-isolada`, a partir do `main` em
`fbab721`. Cada passo segue red pelo motivo esperado, green e refactor. Quando um passo muda
um contrato, `docs/contracts.md` é atualizado no mesmo passo.

## Passos

- [x] **1. `ModuleError` e `ScanReport.errors`** (infra, contrato).
  - Criar o record `ModuleError`.
  - Pôr o novo componente `errors` no `ScanReport`, com o construtor de 4 argumentos
    (`List.of()`) e a cópia imutável.
  - Teste: `ScanReportTest#construtorSemErrosTemListaVazia` e `#errosImutaveis`.
  - Red: falha de compilação inevitável, porque o tipo ainda não existe. Primeiro crio o
    esqueleto, depois confirmo o red na asserção da imutabilidade.
  - Atualizar `docs/contracts.md`.

- [x] **2. `Scanner` isola a falha (red → green).**
  - Testes: `falhaDeModuloViraErroEOsOutrosSeguem`, `semFalhaNaoTemErros`,
    `errorDaJvmPropaga`. O teste `excecaoDeModuloPropaga` sai.
  - Red: a exceção propaga, o que faz o teste falhar.
  - Green: try/catch de `RuntimeException` por módulo. A mensagem nula vira o nome da classe
    da exceção.
  - Refactor: Javadoc do `Scanner`.
  - Verificar: `mvn test -Dtest=ScannerTest`.

- [x] **3. Relatórios mostram os erros (red → green).**
  - JSON: `errorCount` e `errors`.
  - MD e HTML: linha de resumo e a seção "Erros" só quando há erro, com escape no HTML.
  - Testes nos três `*ReportWriterTest`.

- [x] **4. `ScanService` grava com erros** (teste de regressão).
  - Teste: `relatorioEhGravadoMesmoComModuloEmErro`, com fakes. Deve passar depois do passo 2.

- [x] **5. CLI sai com 2 e lista os erros (red → green).**
  - Teste: `MainTest#scanComModuloEmErroSaiCom2EGravaORelatorio`.
  - Green: `ScanCommand` confere `report.errors()`.
  - Atualizar `docs/cli.md`.

- [x] **6. `mvn clean verify`.**

- [x] **7. Checkpoint manual** (lab real): `scan` contra o DVWA sem `--module`, com
  relatório parcial, o erro do `ApiAuthModule` e saída `2`.

## Resultado

- **Passo 6.** `mvn clean verify`: 400 testes, 0 falhas, BUILD SUCCESS.
- **Passo 7. Checkpoint** em 2026-11-17, lab real: `scan --target http://localhost:8080`
  sem `--module`, com a sessão do DVWA e `security=low`. Resultado: saída **2**. O relatório
  foi gravado em MD e em JSON, com 3 achados (`SQL_INJECTION`, `XSS_REFLECTED`, `XSS_STORED`)
  e 1 erro (`ApiAuthModule: resposta de login não é JSON: <… 404 Not Found …>`). O stderr
  traz `erro no módulo …` numa linha e `Scan incompleto: 3 achado(s), 1 módulo(s) com erro`.
  Antes da #58, o mesmo comando saía sem relatório nenhum.

## Desvios

- **Ordem.** Os passos 4 e 5 vieram antes do 3. Os testes antigos
  `ScanServiceTest#excecaoDeModuloPropagaSemGravar` e `MainTest#erroDeExecucaoSai2SemStackTrace`
  exigiam a propagação e quebraram no passo 2. Eles viraram os testes novos desses passos:
  `relatorioEhGravadoMesmoComModuloEmErro` e `scanComModuloEmErroSaiCom2EGravaORelatorio`.
- **Acréscimo.** Teste `MainTest#erroDeModuloComVariasLinhasSaiNumaLinha`. O checkpoint
  mostrou o HTML do 404 em várias linhas no stderr, o mesmo sintoma da issue. Agora a mensagem
  sai numa linha só, como já acontecia no Markdown.

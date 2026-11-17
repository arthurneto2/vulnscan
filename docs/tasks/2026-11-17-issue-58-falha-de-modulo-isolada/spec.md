# Spec — Falha de um módulo não derruba o scan (issue #58)

## Objetivo

Sem `--module`, o `scan` roda todos os módulos. Contra o DVWA, o `api` falha porque
`/rest/user/login` dá 404, e essa exceção derruba o scan inteiro. Nenhum relatório sai, nem
com os achados dos módulos que funcionaram.

A propagação era proposital. O `Scanner` não tinha onde registrar o erro, e engolir a falha
produziria um relatório "limpo" falso. A solução é dar ao relatório um lugar para o erro:

- o `Scanner` isola a falha de cada módulo;
- o `ScanReport` carrega os erros;
- o relatório sai parcial, com os erros visíveis;
- a CLI sai com `2`.

Decisões do usuário: isolar no `Scanner`, e não só no `ApiAuthModule`. Código de saída `2`,
com o relatório gravado.

## Escopo

1. **Novo record de domínio `ModuleError(String module, String message)`**, em
   `domain/model`. `module` é o nome simples da classe do módulo, por exemplo
   `ApiAuthModule`; `message` é a mensagem da exceção. Os dois não podem ser nulos: uma
   mensagem nula vira o nome da classe da exceção.
2. **`ScanReport` ganha `List<ModuleError> errors`**, como último componente, copiado e
   imutável. O construtor de 4 argumentos continua existindo e usa `errors = List.of()`, para
   não mexer nos 13 pontos que criam relatório sem erro.
3. **`Scanner`** captura `RuntimeException` em cada `module.scan(target)`:
   - registra um `ModuleError`;
   - descarta os achados daquele módulo;
   - segue para os próximos módulos.

   Os achados dos outros módulos ficam, na ordem. `Error`, como `OutOfMemoryError`, continua
   propagando.
4. **Relatórios:**
   - **JSON:** `errorCount` e o array `errors` (`module`, `message`), sempre presentes, mesmo
     vazios;
   - **Markdown e HTML:** uma linha de resumo "Módulos com erro: N" e a seção "Erros", só
     quando há erro.
5. **`ScanService`** grava o relatório mesmo com erros. Ele só monta e grava; não decide o
   código de saída.
6. **CLI (`ScanCommand`):** com `report.errors()` não vazio, imprime no stderr uma linha
   `erro no módulo <module>: <message>` por erro. Também imprime o resumo `Scan incompleto: N
   achado(s), M módulo(s) com erro. Relatório: <arquivo>` e sai com `2`. Sem erros, nada muda
   (`0`).
7. Docs:
   - `docs/contracts.md`: o `ModuleError`, o `ScanReport` e o comportamento do `Scanner`;
   - `docs/cli.md`: código `2` com relatório parcial;
   - o Javadoc do `Scanner`.

### Fora do escopo

- Fazer o `ApiAuthModule` se declarar "não aplicável" a alvos que não são o Juice Shop. No
  DVWA, o erro do `api` vai aparecer no relatório, o que é honesto.
- O benchmark: o `LabCaseRunner` chama os módulos direto, sem o `Scanner`, e trata a falha
  por caso (`ERRO`, #74).
- Mudar a mensagem das exceções dos adaptadores.

## Contratos tocados (`docs/contracts.md`)

- Novo `record ModuleError(String module, String message)`.
- `record ScanReport(Target, Instant, Instant, List<Finding> findings, List<ModuleError>
  errors)`, mais o construtor de 4 argumentos.
- `Scanner#scan`: isola a falha de cada módulo, em vez de propagar.

## Critérios de aceite (viram testes)

1. `ScannerTest#falhaDeModuloViraErroEOsOutrosSeguem`: os módulos A (1 achado), B (lança
   `IllegalStateException("404")`) e C (1 achado) dão 2 achados na ordem A, C e
   `errors = [ModuleError("<classe de B>", "404")]`. Substitui `excecaoDeModuloPropaga`.
2. `ScannerTest#semFalhaNaoTemErros`: `errors` vazio.
3. `ScannerTest#errorDaJvmPropaga`: um `Error` lançado pelo módulo propaga.
4. `ScanReportTest` (ou nos testes do Scanner): o construtor de 4 argumentos dá `errors`
   vazio; `errors` é imutável; `ModuleError` com mensagem nula usa o nome da exceção (no
   Scanner).
5. `JsonReportWriterTest`: `errorCount` e `errors` com `module` e `message`; vazio quando não
   há erro.
6. `MarkdownReportWriterTest` e `HtmlReportWriterTest`: a seção "Erros" com o módulo e a
   mensagem (escapada no HTML), e nenhuma seção sem erro.
7. `ScanServiceTest#relatorioEhGravadoMesmoComModuloEmErro`.
8. `MainTest#scanComModuloEmErroSaiCom2EGravaORelatorio`: saída `2`, stderr com `erro no
   módulo` e o relatório gravado.
9. `mvn clean verify` verde.
10. **Checkpoint manual** (lab real): `scan --target http://localhost:8080` sem `--module`,
    com o cookie do DVWA:
    - grava o relatório com os achados de SQLi e XSS;
    - lista o erro do `ApiAuthModule`;
    - sai com `2`.

## Referências

- Issue #58; `docs/cli.md` (códigos de saída); `docs/contracts.md` (`ScanReport`, `Scanner`).

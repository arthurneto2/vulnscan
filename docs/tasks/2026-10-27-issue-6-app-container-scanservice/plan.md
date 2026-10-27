# Plano — app/Config, app/Container e app/ScanService (issue #6)

Spec: `spec.md` nesta pasta. Branch: `issue-6-app-container-scanservice`.

- Produção em `src/main/java/br/ufv/vulnscan/app/`: `ScanRequest`, `ScanService`,
  `InvalidScanRequestException`, `Config`, `Container`.
- Testes em `src/test/java/br/ufv/vulnscan/app/`: `ScanServiceTest` (fakes aninhados
  `FakeModule` — registra se rodou e o `Target`, pode lançar — e `FakeWriter` — registra
  `ScanReport` e `Path`) e `ContainerTest` (`@TempDir`).

Cada passo: teste primeiro (red por asserção), mínimo para passar (green), refactor.
Verificação: `mvn test -Dtest=ScanServiceTest` / `-Dtest=ContainerTest`.

## Passos

- [x] **1. Esqueleto + caminho feliz.**
  - Records/classes com corpo vazio (`scan` devolvendo `null`) para compilar.
  - Teste: `#gravaComOWriterDoFormatoEDevolveORelatorio` — 1 módulo, `format = "md"`,
    `out` em `@TempDir`; writer recebeu o relatório e o caminho; retorno é o mesmo
    relatório. Red por asserção.
  - Green: `Scanner` + writer por formato.
  - *Desvio:* com `scan` devolvendo `null` o red veio por `NullPointerException`; o stub
    passou a devolver um `ScanReport` vazio para o red ser por asserção.

- [x] **2. Target.**
  - Teste: `#montaTargetComAuthorityEUrl`.
  - Green: `new Target(uri.getAuthority(), uri)`.

- [x] **3. Seleção de módulos.**
  - Testes: `#semModulosPedidosRodaTodosNaOrdem`,
    `#rodaSoOsPedidosNaOrdemDoRegistroSemRepetir` (pede `b, a, b` com registro `a, b, c`
    → roda `a, b`).
  - *Desvio:* `#semModulosPedidosRodaTodosNaOrdem` passou de cara (o mínimo do passo 1
    já rodava todos); só o teste de seleção teve red.
  - Green: filtrar `modules.entrySet()` pelo conjunto pedido.

- [x] **4. Erros de uso.**
  - Testes: `#moduloDesconhecidoLancaInvalidScanRequest`,
    `#formatoDesconhecidoLancaInvalidScanRequest` (ambos: mensagem cita os disponíveis e
    nenhum módulo rodou), `#alvoInvalidoLancaInvalidScanRequest` (parametrizado:
    relativo, `ftp://`, sem host; e `null` à parte).
  - Green: validação no início de `scan`.

- [x] **5. Saída padrão.**
  - Teste: `#saidaNulaViraReportPontoFormato` — `Path.of("report.md")`.

- [x] **6. Exceção de módulo.**
  - Teste: `#excecaoDeModuloPropagaSemGravar`. Deve passar de cara; mordida:
    gravar relatório vazio num `catch` temporário.

- [x] **7. Config e Container.**
  - Testes: `ContainerTest#configPadraoTemTimeoutDe30s`,
    `ContainerTest#scanServiceGravaRelatorioMarkdown`.
  - Green: `Config.defaults()`; `Container` com `JdkHttpClient`, `MarkdownReportWriter`
    e registro vazio.
  - *Nota:* o red de `#scanServiceGravaRelatorioMarkdown` foi a própria
    `InvalidScanRequestException` ("formato desconhecido: md"), já que o stub não tinha
    writers — é o comportamento que o passo corrige.

- [x] **8. Contrato e Javadoc.** Assinaturas em `docs/contracts.md` → "Orquestração e
  composição"; Javadoc curto (camada: aplicação).

- [x] **9. Checkpoint.** `mvn clean verify` verde (inclui `DependencyRuleTest`). Marcar
  passos; commits; PR fechando #6.

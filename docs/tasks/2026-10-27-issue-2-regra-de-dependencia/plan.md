# Plano — Teste da regra de dependência (issue #2)

Spec: `spec.md` nesta pasta. Branch: `issue-2-regra-de-dependencia`. Todo o código fica
em `src/test/java/br/ufv/vulnscan/architecture/`.

Cada passo: teste primeiro (red, falhando por asserção), código mínimo (green), refactor.

## Passos

- [x] **1. Esqueleto + primeiro red (domain → adapter).**
  - Teste: `DependencyRuleTest#domainImportandoAdapterViola` — escreve em `@TempDir`
    `br/ufv/vulnscan/domain/Foo.java` com `import br.ufv.vulnscan.adapter.http.X;` e
    espera 1 violação contendo `Foo.java:3` e `br.ufv.vulnscan.adapter`.
  - Para o red ser por asserção: criar `DependencyRuleChecker.violations(Path)` devolvendo
    `List.of()`. Rodar e ver falhar por asserção.
  - Green: varrer `domain/` com `Files.walk`, ler linhas, casar
    `br\.ufv\.vulnscan\.(adapter|app|cli)\b`.
  - Verificar: `mvn test -Dtest=DependencyRuleTest`.

- [x] **2. domain → app e domain → cli.**
  - Teste parametrizado `DependencyRuleTest#domainImportandoCamadaExternaViola`
    (`@ValueSource(strings = {"adapter", "app", "cli"})`), substituindo o teste do passo 1.
  - Deve passar já com o código do passo 1; se passar de primeira, confirmar o red
    trocando temporariamente a regex (garantia de que o teste morde).

- [x] **3. `import static` e nome totalmente qualificado no corpo.**
  - Testes: `#importStaticDeAppEmDomainViola`, `#nomeQualificadoDeAdapterNoCorpoViola`.
  - A busca por linha inteira (não só por `import`) já deve cobrir; mesma verificação de
    mordida do passo 2.

- [x] **4. Regra de `port/`.**
  - Red: `#portImportandoDomainDetectionViola` — `port/Bar.java` importando
    `br.ufv.vulnscan.domain.detection.Detector`.
  - Green: em `port/`, toda ocorrência `br\.ufv\.vulnscan\.[\w.]+` que não comece com
    `br.ufv.vulnscan.domain.model` nem `br.ufv.vulnscan.port` é violação (cobre curinga
    `br.ufv.vulnscan.domain.*`).
  - Teste: `#portImportandoDomainModelEJdkNaoViola` — sem violações.
  - Teste: `#portComImportCuringaDeDomainViola`.

- [x] **5. Código real.**
  - Teste: `#codigoDeProducaoRespeitaARegra` — `violations(Path.of("src/main/java"))`
    vazio; mensagem de falha junta as violações uma por linha.
  - Verificar mordida: inserir à mão uma referência a `br.ufv.vulnscan.adapter...` num
    arquivo de `domain/`, rodar, ver falhar com arquivo:linha, desfazer.
    *Desvio:* um `import` de classe inexistente nem compila; a mordida foi feita com
    `// usa br.ufv.vulnscan.adapter.http.JdkHttpClient` em `Detector.java` (falhou com
    `domain/detection/Detector.java:3 -> ...`).

- [x] **6. Refactor e Javadoc.** Javadoc curto nas duas classes (camada: teste de
  arquitetura). Rodar `mvn test -Dtest=DependencyRuleTest`.

- [x] **7. Checkpoint.** `mvn clean verify` verde. Marcar passos neste arquivo; commit;
  PR fechando #2.

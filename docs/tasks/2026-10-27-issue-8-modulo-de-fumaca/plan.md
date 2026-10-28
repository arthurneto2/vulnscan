# Plano — Módulo de fumaça fechando o circuito (issue #8)

Spec: `spec.md` nesta pasta. Branch: `issue-8-modulo-de-fumaca`.

- Produção: `src/main/java/br/ufv/vulnscan/domain/module/SmokeModule.java`;
  `app/Container.java` (registro).
- Testes: `src/test/java/br/ufv/vulnscan/domain/module/SmokeModuleTest.java` (fake
  `RecordingHttpClient` aninhado: guarda as requisições, devolve resposta fixa ou lança);
  ajustes em `ContainerTest` e `MainTest` (HttpServer do JDK em porta efêmera, contando
  as requisições recebidas).

Cada passo: teste primeiro (red por asserção), mínimo para passar (green), refactor.

## Passos

- [ ] **1. Esqueleto + uma requisição GET.**
  - `SmokeModule` com construtor e `scan` devolvendo `List.of()` sem enviar nada;
    `type()` devolvendo `null` (compila, red por asserção).
  - Teste: `SmokeModuleTest#enviaUmGetParaABaseUrl`.
  - Green: `http.send(new HttpRequest("GET", target.baseUrl(), Map.of(), null))`.
  - Verificar: `mvn test -Dtest=SmokeModuleTest`.

- [ ] **2. Não acha nada / erro propaga.**
  - Testes: `#naoReportaNadaMesmoCom500`, `#erroDoHttpClientPropaga`.
  - Devem passar com o passo 1; mordida: trocar temporariamente por `try/catch` que
    engole (o segundo fica vermelho) e desfazer. Registrar no plano se não houver red.

- [ ] **3. `type()` e construtor.**
  - Testes: `#typeLancaUnsupportedOperation`, `#construtorRejeitaNull`.
  - Green: `throw new UnsupportedOperationException(...)`; `Objects.requireNonNull`.

- [ ] **4. Registro no Container.**
  - Ajustar `ContainerTest#scanServiceGravaRelatorioMarkdown` para o HttpServer local
    (continua verde: hoje sem módulos).
  - Testes novos: `ContainerTest#moduloSmokeFazUmGetNoAlvo`,
    `#semModulosRodaAFumaca` → red (registro vazio: módulo desconhecido / 0 requisições).
  - Green: `modules.put("smoke", new SmokeModule(httpClient))`; Javadoc do `Container`
    sem o "vazio até a #8", com a nota de remoção na #10.
  - Verificar: `mvn test -Dtest=ContainerTest`.

- [ ] **5. MainTest com Container real.**
  - `MainTest#scanGravaRelatorioESai0` passa a usar o HttpServer local e a checar que
    recebeu o GET.
  - Verificar: `mvn test -Dtest=MainTest`.

- [ ] **6. Refactor e Javadoc** (camada: domínio, estratégia `VulnModule`; temporário até
  a #10). `DependencyRuleTest` verde.

- [ ] **7. Checkpoint da Fase 1.** `mvn clean verify` verde;
  `mvn package -DskipTests`; com o lab no ar,
  `java -jar target/vulnscan.jar scan --target http://localhost:3000` → `0` e
  `report.md`; sem lab → `2` com `erro: ...`. Marcar o item da Fase 1 em
  `docs/build-order.md`, marcar passos aqui, commits, PR com "Closes #8".

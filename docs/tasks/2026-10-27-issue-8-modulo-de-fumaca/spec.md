# Spec — Módulo de fumaça fechando o circuito (issue #8)

Referência: issue #8 (milestone "Fase 1 — HTTP ponta a ponta"), último item da Fase 1 em
`docs/build-order.md`: "Um `VulnModule` trivial de fumaça (faz uma requisição e não acha
nada) só para fechar o circuito."

## Objetivo

Validar o caminho ponta a ponta CLI → `ScanService` → `Scanner` → `VulnModule` →
`HttpClient` (rede real) → `ReportWriter`, com um módulo que faz **uma** requisição ao alvo
e não reporta nada. Fecha o checkpoint da Fase 1.

## Escopo

### `domain/module/SmokeModule`

- `public final class SmokeModule implements VulnModule`.
- Construtor `SmokeModule(HttpClient http)` (colaborador por construtor, como pede
  `docs/contracts.md`; `null` → `NullPointerException`). Sem `Detector`: não julga nada.
- `scan(Target target)`: envia **um** `GET` para `target.baseUrl()`, sem cabeçalhos
  (`Map.of()`) e sem corpo (`null`); ignora a resposta e devolve `List.of()`.
- Exceção do `HttpClient` (alvo fora do ar etc.) **propaga**: o `Scanner` já interrompe e
  a CLI devolve `2`. Fumaça que engole erro não prova nada.
- `type()`: não há `VulnType` de fumaça. Lança
  `UnsupportedOperationException("módulo de fumaça não tem VulnType")`. Hoje ninguém chama
  `VulnModule.type()` (o relatório usa `Finding.type()`); o enum do escopo não muda.
  *(Decisão do usuário.)*

### `app/Container`

- Registra `"smoke" → new SmokeModule(httpClient)` no mapa de módulos. Com isso
  `--module smoke` funciona e `scan` sem `--module` (= todos) roda a fumaça.
- **Ciclo de vida:** temporário. A issue #10 (`SqlInjectionModule`, primeiro módulo real)
  remove o registro e a classe. Anotar isso no Javadoc do `SmokeModule` e do `Container`.
  *(Decisão do usuário.)*

### Testes existentes afetados

Com a fumaça no registro, os testes que usam o `Container` real passariam a fazer um GET
de verdade em `localhost:3000` (lab, que não pode ser requisito de teste):

- `ContainerTest#scanServiceGravaRelatorioMarkdown`
- `MainTest#scanGravaRelatorioESai0`

Ambos passam a apontar para um `com.sun.net.httpserver.HttpServer` em processo, em
`localhost` porta efêmera (padrão já usado no `JdkHttpClientTest`). Os demais testes do
`MainTest` usam registro fake e não mudam.

## Fora do escopo

- `Detector`, payloads, qualquer achado.
- Novo valor em `VulnType`; mudança em `VulnModule` ou em `docs/contracts.md`.
- Regra de "módulo fora do todos" no `ScanService`.

## Contratos tocados

Nenhum em `docs/contracts.md` (o `SmokeModule` é implementação, não contrato; é
temporário). `docs/cli.md` não muda: `smoke` não é anunciado como módulo do usuário.

## Critérios de aceite

`SmokeModuleTest` (fake de `HttpClient` escrito à mão, sem rede):

1. `scan` envia exatamente uma requisição: `GET`, URL = `baseUrl` do alvo, sem
   cabeçalhos, corpo `null`.
2. `scan` devolve lista vazia (inclusive com resposta 500 do fake).
3. Exceção do `HttpClient` propaga (`UncheckedIOException` sai de `scan`).
4. `type()` lança `UnsupportedOperationException`.
5. Construtor com `null` → `NullPointerException`.

`ContainerTest` (HttpServer local):

6. `ScanRequest(..., List.of("smoke"), "md", out)` → servidor recebe um `GET /` e o
   relatório tem `Nenhum achado.`
7. `ScanRequest` sem módulos (= todos) também faz o GET (fumaça está no "todos").

`MainTest`:

8. `scanGravaRelatorioESai0` contra o HttpServer local → saída `0`, relatório gravado,
   servidor recebeu o GET.

Gerais:

9. `DependencyRuleTest` verde (`SmokeModule` só importa `domain` e `port`).
10. `mvn clean verify` verde.
11. **Checkpoint da Fase 1 (manual, com lab):**
    `docker compose -f lab/docker-compose.yml up -d` e
    `java -jar target/vulnscan.jar scan --target http://localhost:3000` → saída `0` e
    `report.md` gravado. Sem lab: mesma linha → saída `2` com `erro: ...` (sem stack
    trace).

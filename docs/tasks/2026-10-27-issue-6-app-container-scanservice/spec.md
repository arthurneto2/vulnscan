# Spec — app/Config, app/Container e app/ScanService (issue #6)

Referência: issue #6 (milestone "Fase 1 — HTTP ponta a ponta"), item "`app/Container` e
`app/ScanService`" da Fase 1 em `docs/build-order.md`. Superfície que a CLI vai usar:
`docs/cli.md` (issue #7).

## Objetivo

Ter a camada de aplicação: o caso de uso `ScanService` (o que a CLI chama) e a raiz de
composição `Container`, que instancia os adaptadores e monta o grafo à mão, sem framework
de DI. Com isso a CLI da #7 fica só traduzindo argumentos.

## Escopo

### `app/ScanRequest` (record)

```java
public record ScanRequest(URI target, List<String> modules, String format, Path out) {}
```

- `target`: URL base vinda de `--target`.
- `modules`: nomes de `--module` (`sqli`, `xss`, `api`…); **vazio = todos**.
- `format`: `--format` (`md` hoje).
- `out`: `--out`; **`null` = `report.<format>`** no diretório atual (default de
  `docs/cli.md`, que depende de outra opção — mais simples resolver aqui que na CLI).

### `app/ScanService`

```java
public ScanService(Map<String, VulnModule> modules, List<ReportWriter> writers)
public ScanReport scan(ScanRequest request)
```

- `modules`: módulos disponíveis por nome, **na ordem de execução** (o `Container` passa
  um `LinkedHashMap`). Copiado no construtor.
- `writers`: um por formato, indexados por `ReportWriter.format()`.
- `scan`:
  1. valida o pedido (abaixo);
  2. monta `Target(name = authority da URL, ex. "localhost:3000", baseUrl = target)`;
  3. escolhe os módulos: todos, ou só os pedidos — sempre **na ordem do registro**,
     sem repetir;
  4. `new Scanner(módulos).scan(target)`;
  5. grava com o `ReportWriter` do formato em `out`;
  6. devolve o `ScanReport` (a CLI pode imprimir um resumo).
- **Erro de uso** → `InvalidScanRequestException` (nova, `RuntimeException` em `app/`),
  com mensagem para o usuário. A CLI mapeia para código de saída `1`:
  - `target` nulo, não absoluto, esquema diferente de `http`/`https` ou sem host;
  - módulo desconhecido (mensagem lista os disponíveis);
  - formato sem `ReportWriter` (mensagem lista os disponíveis).
- **Erro de execução** (módulo lança, falha de I/O ao gravar) propaga como está; a CLI
  mapeia para `2`. Se o scan falha, nada é gravado.
- Validação acontece **antes** de rodar qualquer módulo.

Uma exceção própria, em vez de `IllegalArgumentException`, porque um módulo pode lançar
`IllegalArgumentException` em tempo de execução e a CLI devolveria `1` (uso) em vez de `2`.

### `app/Config` (record)

```java
public record Config(Duration httpTimeout) {
    public static Config defaults()   // httpTimeout = 30 s
}
```

Só o que já tem consumidor. Modelo/URL do Ollama entram com as issues #16/#17.

### `app/Container`

```java
public Container(Config config)
public ScanService scanService()
```

- Instancia `JdkHttpClient(config.httpTimeout())` e `MarkdownReportWriter`.
- Registro de módulos: `LinkedHashMap` **vazio por enquanto** — ainda não há módulo
  (o de fumaça é a #8, que só o registra aqui). O `HttpClient` fica pronto para eles.

## Fora do escopo

- `--detector` (issue #19): sem `Detector` implementado ainda; o `Container` passará o
  detector aos módulos quando eles existirem.
- Aviso de alvo fora de `localhost` (issue #7, CLI) e parsing de argumentos.
- Subcomando `benchmark`.
- Restringir `scan` a `localhost` (o `docs/cli.md` pede só aviso).

## Contratos tocados

`docs/contracts.md` → "Orquestração e composição": registrar as assinaturas de
`ScanRequest`, `ScanService`, `InvalidScanRequestException`, `Config` e `Container`.
Nada no domínio nem nas portas muda.

## Critérios de aceite (viram testes)

`ScanService`, com `VulnModule` e `ReportWriter` fakes escritos à mão:

1. Sem módulos pedidos → roda todos, na ordem do registro.
2. Com módulos pedidos → roda só esses, na ordem do registro, sem repetir.
3. Módulo desconhecido → `InvalidScanRequestException` citando o nome e os disponíveis;
   nenhum módulo roda.
4. Formato desconhecido → `InvalidScanRequestException` citando os disponíveis; nenhum
   módulo roda.
5. `target` inválido (`null`, relativo, `ftp://`, sem host) → `InvalidScanRequestException`.
6. `Target` montado com `name` = authority e `baseUrl` = URL.
7. Grava com o writer do formato no `out` pedido e devolve o mesmo `ScanReport`.
8. `out == null` → grava em `report.<format>`.
9. Exceção de módulo propaga e nada é gravado.

`Container`:

10. `Config.defaults()` tem timeout de 30 s.
11. `new Container(Config.defaults()).scanService()` grava `report.md` em `@TempDir`
    para um alvo `http://localhost:3000` (sem módulos → sem rede) com `Nenhum achado.`
12. `DependencyRuleTest` verde; `mvn clean verify` verde.

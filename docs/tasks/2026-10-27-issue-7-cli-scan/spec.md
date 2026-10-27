# Spec — cli/Main com o subcomando scan (issue #7)

Referência: issue #7 (milestone "Fase 1 — HTTP ponta a ponta"), item "`cli/Main` com o
subcomando `scan`" da Fase 1 em `docs/build-order.md`. Superfície em `docs/cli.md`.

## Objetivo

Ter o executável: `java -jar target/vulnscan.jar scan --target <url> ...` traduz os
argumentos num `ScanRequest`, chama o `ScanService` e devolve o código de saída certo.
Nenhuma lógica de scan na CLI.

## Escopo

### Comandos (picocli)

- `cli/Main` — comando raiz `vulnscan`; `main(String[])` faz
  `System.exit(new CommandLine(new Main()).execute(args))`. Sem subcomando → imprime o uso
  em stderr e sai com `1`. `-h/--help` → uso, saída `0`.
- `cli/ScanCommand` — subcomando `scan`, opções de `docs/cli.md` (menos `--detector`):

| Opção      | Tipo           | Default                | Observação                    |
| ---------- | -------------- | ---------------------- | ----------------------------- |
| `--target` | `URI`          | obrigatório            | conversão de URI do picocli   |
| `--module` | `List<String>` | vazio (= todos)        | repetível                     |
| `--format` | `String`       | `md`                   |                               |
| `--out`    | `Path`         | `report.<format>`      | resolvido na CLI (ver abaixo) |

- `ScanCommand` obtém o `ScanService` do `Main` via `@ParentCommand`. O `Main` padrão usa
  `new Container(Config.defaults()).scanService()`; um construtor **package-private**
  `Main(Supplier<ScanService>)` permite aos testes injetar um `ScanService` montado com
  módulos/writers fake (o `ScanService` é `final`; não é preciso interface nova).

### Códigos de saída (`docs/cli.md`)

| Situação                                                             | Saída |
| -------------------------------------------------------------------- | ----- |
| Scan concluído (com ou sem achados)                                  | `0`   |
| Erro de uso: opção faltando/desconhecida, URI malformada, sem subcomando, `InvalidScanRequestException` (alvo/módulo/formato inválido) | `1` |
| Erro de execução: qualquer outra exceção durante o scan              | `2`   |

- O padrão do picocli é o inverso (uso = 2, execução = 1); configura-se nas anotações
  (`exitCodeOnInvalidInput = 1`, `exitCodeOnExecutionException = 2`) para valer também
  com `new CommandLine(new Main())` direto nos testes.
- `ScanCommand.call()` captura `InvalidScanRequestException` (→ `1`) e as demais
  `RuntimeException` (→ `2`) e imprime **uma linha** em stderr (`erro: <mensagem>`), sem
  stack trace.

### Saída

- Sucesso, em stdout: `Scan concluído: N achado(s). Relatório: <caminho>`.
- Para imprimir o caminho, a CLI resolve o default `report.<format>` e sempre passa `out`
  preenchido ao `ScanService` (a regra de `out == null` do `ScanService` continua para
  outros chamadores).

### Aviso de alvo fora de localhost

- Antes do scan, se o host do alvo não for local, imprime em stderr:
  `aviso: o alvo <host> não é localhost; o vulnscan é para o laboratório local.`
  e **segue** (o `docs/cli.md` pede aviso, não bloqueio).
- Local = `localhost` (sem diferenciar maiúsculas), IPv4 `127.0.0.0/8` e `[::1]`.
  Sem resolução DNS (não faz rede para decidir).

### Dependência de camadas

A CLI importa `app` e lê o `ScanReport` (de `domain.model`) que o `ScanService` devolve,
só para contar os achados. Nenhuma lógica de domínio na CLI.

## Fora do escopo

- `--detector` (issue #19) e `benchmark` (issue #22).
- `--version`.
- Módulos reais: o registro do `Container` segue vazio até a #8; portanto
  `scan` hoje roda sem requisições e grava um relatório sem achados.

## Contratos tocados

Nenhum em `docs/contracts.md`. `docs/cli.md` não muda (já descreve isto).

## Critérios de aceite (viram testes, via `new CommandLine(new Main(...)).execute(...)`, com
stdout/stderr capturados em `StringWriter`)

1. `scan --target http://localhost:3000 --out <tmp>/r.md` (Container real) → saída `0`,
   arquivo com `Nenhum achado.`, stdout com `0 achado(s)` e o caminho.
2. Sem `--out` → `ScanService` recebe `report.md` (writer fake registra o caminho).
3. `--module a --module c` com registro `a, b, c` → rodam `a` e `c`.
4. Sem `--target` → `1`, stderr cita `--target`.
5. Opção desconhecida → `1`.
6. `--target "http://exa mple"` (URI malformada) → `1`.
7. Módulo desconhecido (`InvalidScanRequestException`) → `1`, stderr com a mensagem, sem
   stack trace.
8. Módulo que lança `IllegalStateException("alvo fora do ar")` → `2`, stderr com
   `erro: alvo fora do ar`, sem stack trace.
9. Alvo `http://example.com` → stderr com `aviso:`, saída `0` (sem módulos, sem rede).
10. Alvos locais (`localhost`, `LOCALHOST`, `127.0.0.1`, `127.1.2.3`, `[::1]`) → sem aviso.
11. Sem subcomando → `1`, uso em stderr.
12. `--help` e `scan --help` → `0`.
13. `DependencyRuleTest` verde; `mvn clean verify` verde; `java -jar target/vulnscan.jar
    scan --target http://localhost:3000 --out <tmp>/report.md` roda manualmente e grava o
    relatório (sem lab: não há módulos ainda).

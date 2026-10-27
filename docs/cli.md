# Superfície da CLI

Implementada com picocli em `cli/Main.java`. Dois subcomandos: `scan` e `benchmark`.

## `scan` — roda um ou mais módulos contra um alvo

```
vulnscan scan --target <url> [--module <nome>]... [--detector heuristic|llm|both]
              [--format md|json|html] [--out <arquivo>]
```

| Opção        | Default        | Descrição                                            |
| ------------ | -------------- | ---------------------------------------------------- |
| `--target`   | (obrigatório)  | URL base do alvo, ex.: `http://localhost:3000`       |
| `--module`   | todos          | `sqli`, `xss`, `api` — repetível                     |
| `--detector` | `heuristic`    | qual estratégia usar no veredito                     |
| `--format`   | `md`           | formato do relatório de saída                        |
| `--out`      | `report.<fmt>` | caminho do arquivo de saída                          |

Exemplo:
```
vulnscan scan --target http://localhost:3000 --module xss --detector both --format html
```

## `benchmark` — roda o experimento (heurística vs LLM)

```
vulnscan benchmark --ground-truth <arquivo> [--runs <n>] [--out <dir>]
```

| Opção            | Default            | Descrição                                         |
| ---------------- | ------------------ | ------------------------------------------------- |
| `--ground-truth` | (obrigatório)      | caminho do `ground_truth.yaml`                    |
| `--runs`         | `3`                | nº de execuções (para medir variância do LLM)     |
| `--out`          | `experiments/results` | diretório dos CSVs de resultado                |

Saída: um CSV por execução com uma linha por (caso × detector), e um resumo com a matriz
de confusão e as métricas por tipo de falha. Ver `docs/experiment.md`.

## Convenções

- A CLI só traduz argumentos e delega para `app/ScanService`. Nenhuma lógica de scan vive
  aqui.
- Código de saída: `0` se rodou, `1` em erro de uso, `2` em erro de execução.
- Alvo fora de `localhost` imprime aviso (o escopo é laboratório).

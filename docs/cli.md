# Superfície da CLI

Implementada com picocli em `cli/Main.java`. Dois subcomandos: `scan` e `benchmark`.

## `scan` — roda um ou mais módulos contra um alvo

```
vulnscan scan --target <url> [--module <nome>]... [--header "<nome>: <valor>"]...
              [--detector heuristic|llm|both] [--llm ollama|api] [--format md|json|html]
              [--out <arquivo>]
```

| Opção        | Default        | Descrição                                            |
| ------------ | -------------- | ---------------------------------------------------- |
| `--target`   | (obrigatório)  | URL base do alvo, ex.: `http://localhost:3000`       |
| `--module`   | todos          | `sqli`, `xss`, `xss-dom`, `xss-stored`, `api` — repetível. `xss-dom`/`xss-stored` abrem o Chrome headless; `api` faz login real; "todos" faz as duas coisas |
| `--header`   | nenhum         | header enviado em toda requisição ao alvo; repetível. Nome repetido junta valores por `, `. Sem `:` ou nome vazio → saída 1 |
| `--detector` | `heuristic`    | qual estratégia usar no veredito                     |
| `--llm`      | `ollama`       | backend do detector `llm`: `ollama` (local) ou `api` (compatível com OpenAI; ver abaixo). Inválido ou `api` sem chave → saída 1 |
| `--format`   | `md`           | formato do relatório de saída                        |
| `--out`      | `report.<fmt>` | caminho do arquivo de saída                          |

Exemplos:
```
vulnscan scan --target http://localhost:8080 --module sqli \
              --header "Cookie: PHPSESSID=<sessão>; security=low"
vulnscan scan --target http://localhost:3000 --module xss --detector both --format html
```

## `benchmark` — roda o experimento (heurística vs LLM)

```
vulnscan benchmark --ground-truth <arquivo> [--runs <n>] [--llm ollama|api] [--out <dir>]
```

| Opção            | Default            | Descrição                                         |
| ---------------- | ------------------ | ------------------------------------------------- |
| `--ground-truth` | (obrigatório)      | caminho do `ground_truth.yaml`                    |
| `--runs`         | `3`                | nº de execuções (para medir variância do LLM)     |
| `--llm`          | `ollama`           | backend do detector `llm` (igual ao do `scan`)    |
| `--out`          | `experiments/results` | diretório dos CSVs de resultado                |

Saída: um CSV por execução com uma linha por (caso × detector), e um resumo com a matriz
de confusão e as métricas por tipo de falha. Ver `docs/experiment.md`.

## IA via API (`--llm api`, issue #65)

Para quem não roda o modelo local mínimo (14B). A configuração vem **só de variáveis de
ambiente** (nunca de argumento, que fica no histórico do shell, nem de arquivo do
repositório):

| Variável                 | Default                                  | Descrição                    |
| ------------------------ | ---------------------------------------- | ---------------------------- |
| `VULNSCAN_LLM_API_KEY`   | (obrigatória)                            | chave do provedor            |
| `VULNSCAN_LLM_API_URL`   | `https://integrate.api.nvidia.com/v1`    | URL base (até o `/v1`)       |
| `VULNSCAN_LLM_API_MODEL` | `nvidia/nemotron-3-ultra-550b-a55b`  | id do modelo no provedor     |

```
export VULNSCAN_LLM_API_KEY=...   # build.nvidia.com → "Get API Key"
vulnscan benchmark --ground-truth experiments/ground_truth.yaml --llm api --out experiments/results-api
```

Qualquer provedor com `POST /chat/completions` compatível com OpenAI serve (Groq, Gemini,
OpenRouter…): troque `VULNSCAN_LLM_API_URL` e `VULNSCAN_LLM_API_MODEL`.

## Convenções

- A CLI só traduz argumentos e delega para `app/ScanService`. Nenhuma lógica de scan vive
  aqui.
- Código de saída: `0` se rodou, `1` em erro de uso, `2` em erro de execução.
- Alvo fora de `localhost` imprime aviso (o escopo é laboratório).

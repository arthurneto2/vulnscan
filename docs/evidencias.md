# Evidências de execução

Saídas reais do `vulnscan` 0.2.0 no laboratório local: DVWA em `:8080` e Juice Shop em
`:3000`, recém-resetados com `lab/docker-compose.yml`. Capturadas em 2026-11-17, para o
relatório técnico. A sessão do DVWA aparece como `<sessão>`, nunca com o valor real.

- **Seções 1, 2 e 4:** capturadas com o JAR do branch `release-v0.2.0`.
- **Seção 3:** vem da rodada de referência pós-#83, descrita em `docs/experiment.md`
  ("Rodada após as #60–#83").

## 1. Scan — SQL injection no DVWA

Comando (sessão do DVWA passada por `--header`; nível `low`):

```bash
java -jar target/vulnscan.jar scan --target http://localhost:8080 --module sqli \
     --header "Cookie: PHPSESSID=<sessão>; security=low" --out report.md
```

Saída: `Scan concluído: 1 achado(s). Relatório: report.md` (código `0`).

Trecho do `report.md`:

````
- **Duração:** 54 ms
- **Achados:** 1

## Achados

### 1. SQL_INJECTION — HIGH

- **Veredito:** vulnerável (confiança 0.90, detector `heuristic`)
- **Justificativa:** assinatura de erro de SQL na resposta: MySQL
- **Payload:** `sqli-quote-single` — aspas simples após valor válido: quebra a string e provoca erro de sintaxe

**Requisição**

```http
GET http://localhost:8080/vulnerabilities/sqli/?Submit=Submit&id=1%27
Cookie: PHPSESSID=<sessão>; security=low
```

**Resposta** (200, 43 ms)

```http
content-type: text/html; charset=UTF-8
server: Apache/2.4.25 (Debian)

<pre>You have an error in your SQL syntax; check the manual that corresponds to your MariaDB server version for the right syntax to use near ''1''' at line 1</pre>
```
````

## 2. Scan com todos os módulos — relatório parcial (#58)

Sem `--module`, o scan roda todos os módulos. O DVWA não tem a API REST do Juice Shop, e o
`ApiAuthModule` falha no login. Desde a #58, essa falha não derruba o scan: os outros
módulos seguem, o relatório é gravado com uma seção `Erros` e a saída é `2`.

```bash
java -jar target/vulnscan.jar scan --target http://localhost:8080 \
     --header "Cookie: PHPSESSID=<sessão>; security=low" --out report.md
```

stderr (código de saída `2`; a mensagem do módulo traz o começo da página 404):

```
erro no módulo ApiAuthModule: resposta de login não é JSON: <!DOCTYPE HTML PUBLIC "-//IETF//DTD HTML 2.0//EN"> <html><head> <title>404 Not Found</title> </head><body> <h1>Not Found</h1> <p>The requested URL /rest/user/login was not found on this server.</p> <h
Scan incompleto: 3 achado(s), 1 módulo(s) com erro. Relatório: report.md
```

Início do `report.md`:

```
- **Duração:** 9712 ms
- **Achados:** 3
- **Módulos com erro:** 1

## Erros

Estes módulos falharam; os achados deles não estão no relatório (scan incompleto).

- **ApiAuthModule**: resposta de login não é JSON: <!DOCTYPE HTML PUBLIC ... 404 Not Found ...

## Achados

### 1. SQL_INJECTION — HIGH
### 2. XSS_REFLECTED — MEDIUM
### 3. XSS_STORED — HIGH
```

O achado de XSS armazenado vem do navegador headless. A justificativa é "o navegador
executou o payload: diálogo com o marcador", e a resposta traz `x-vulnscan-dialogs` com o
marcador do payload.

## 3. Benchmark — heurística vs LLM sobre o gabarito

Rodada de referência: commit `dc7a6ec` (`main` com a #83), 2026-11-17, 12:22–12:54 (-03:00).
Ollama no ar com o `qwen2.5:14b-instruct`.

```bash
java -jar target/vulnscan.jar benchmark --ground-truth experiments/ground_truth.yaml \
     --runs 3 --reset --llm ollama
```

`summary.csv`: 16 casos × 3 execuções = 48 vereditos por detector, 27 positivos e 21
negativos. Pasta local: `experiments/results/2026-11-17-ref83-ollama-qwen14b/`.

| detector  | tipo                  | tp | fp | tn | fn | precisão | recall | f1     | fpr  | tempo_médio_ms | erros |
| --------- | --------------------- | -- | -- | -- | -- | -------- | ------ | ------ | ---- | -------------- | ----- |
| heuristic | SQL_INJECTION         | 6  | 0  | 3  | 0  | 1.0000   | 1.0000 | 1.0000 | 0.00 | 1402           | 0     |
| heuristic | XSS_REFLECTED         | 9  | 0  | 9  | 0  | 1.0000   | 1.0000 | 1.0000 | 0.00 | 1402           | 0     |
| heuristic | XSS_STORED            | 3  | 0  | 3  | 0  | 1.0000   | 1.0000 | 1.0000 | 0.00 | 1402           | 0     |
| heuristic | BROKEN_AUTH           | 3  | 0  | 3  | 0  | 1.0000   | 1.0000 | 1.0000 | 0.00 | 1402           | 0     |
| heuristic | BROKEN_ACCESS_CONTROL | 6  | 0  | 3  | 0  | 1.0000   | 1.0000 | 1.0000 | 0.00 | 1402           | 0     |
| heuristic | **TOTAL**             | 27 | 0  | 21 | 0  | 1.0000   | 1.0000 | 1.0000 | 0.00 | 1402           | 0     |
| llm       | SQL_INJECTION         | 6  | 0  | 3  | 0  | 1.0000   | 1.0000 | 1.0000 | 0.00 | 38069          | 0     |
| llm       | XSS_REFLECTED         | 9  | 0  | 9  | 0  | 1.0000   | 1.0000 | 1.0000 | 0.00 | 38069          | 0     |
| llm       | XSS_STORED            | 3  | 0  | 3  | 0  | 1.0000   | 1.0000 | 1.0000 | 0.00 | 38069          | 0     |
| llm       | BROKEN_AUTH           | 3  | 0  | 3  | 0  | 1.0000   | 1.0000 | 1.0000 | 0.00 | 38069          | 0     |
| llm       | BROKEN_ACCESS_CONTROL | 4  | 0  | 3  | 2  | 1.0000   | 0.6667 | 0.8000 | 0.00 | 38069          | 0     |
| llm       | **TOTAL**             | 25 | 0  | 21 | 2  | 1.0000   | 0.9259 | 0.9615 | 0.00 | 38069          | 0     |

Leitura:

- **Heurística:** acerta os 48 vereditos.
- **LLM:** nenhum falso positivo e nenhum `ERRO`. Os 2 falsos negativos são o mesmo caso,
  `js-idor-basket`, nas execuções 2 e 3.
- **Tempo:** o tempo médio do LLM (~38 s) inclui a latência de cada chamada ao Ollama. A
  mediana por veredito (18,7 s) e a análise completa estão em `docs/experiment.md`.

## 4. LLM fora do ar vira `ERRO` (#74)

Quando o LLM não responde (Ollama parado, timeout ou erro da API), o caso sai como `ERRO`.
Ele não conta como "não vulnerável" e fica fora das métricas; o motivo vai na coluna `erro`.
A captura abaixo usa um gabarito de 1 caso (`dvwa-sqli-low`) com o Ollama parado:

```bash
java -jar target/vulnscan.jar benchmark --ground-truth gt-1caso.yaml --runs 1 --out ev-erro
```

`run-1.csv`:

```
run,caso,tipo,detector,esperado,previsto,confianca,outcome,tempo_ms,erro
1,dvwa-sqli-low,SQL_INJECTION,heuristic,true,true,0.9000,TP,4,
1,dvwa-sqli-low,SQL_INJECTION,llm,true,,,ERRO,,LLM indisponível em 5 de 5 chamadas: java.net.ConnectException
```

Linhas `TOTAL` do `summary.csv`: o `ERRO` aparece só na coluna `erros`.

```
detector,tipo,tp,fp,tn,fn,precisao,recall,f1,fpr,tempo_medio_ms,erros
heuristic,TOTAL,1,0,0,0,1.0000,1.0000,1.0000,0.0000,4.0000,0
llm,TOTAL,0,0,0,0,0.0000,0.0000,0.0000,0.0000,0.0000,1
```

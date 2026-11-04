# Evidências de execução

Saídas reais capturadas no laboratório local (DVWA em `:8080`, Juice Shop em `:3000`,
imagens recém-subidas via `lab/docker-compose.yml`), para o relatório técnico. Os números
abaixo são de uma execução nesta máquina; o relatório final usa a execução dos autores.
O lado **LLM** exige Ollama no ar (`ollama serve` + `ollama pull mistral:7b-instruct-v0.3-q4_0`);
sem ele, o `LlmDetector` devolve "LLM indisponível" (veredito negativo), como aparece abaixo.

## 1. Scan — SQL injection no DVWA

Comando (sessão do DVWA passada por `--header`; nível `low`):

```bash
java -jar target/vulnscan.jar scan --target http://localhost:8080 --module sqli \
     --header "Cookie: PHPSESSID=<sessão>; security=low" --out report.md
```

Trecho do `report.md` (achado 1):

```
### 1. SQL_INJECTION — HIGH

- **Veredito:** vulnerável (confiança 0.90, detector `heuristic`)
- **Justificativa:** assinatura de erro de SQL na resposta: MySQL
- **Payload:** `sqli-quote-single` — aspas simples após valor válido: quebra a string e provoca erro de sintaxe

**Requisição**

    GET http://localhost:8080/vulnerabilities/sqli/?Submit=Submit&id=1%27
    Cookie: PHPSESSID=<sessão>; security=low

**Resposta** (200, ~80 ms)

    <pre>You have an error in your SQL syntax; check the manual that corresponds to
    your MariaDB server version for the right syntax to use near ''1''' at line 1</pre>
```

## 2. Benchmark — heurística vs LLM sobre o gabarito

Comando:

```bash
java -jar target/vulnscan.jar benchmark --ground-truth experiments/ground_truth.yaml --runs 1
```

`experiments/results/summary.csv` (14 casos: 8 positivos, 6 negativos):

| detector  | tipo                   | tp | fp | tn | fn | precisão | recall | f1   | fpr  | tempo_médio_ms |
| --------- | ---------------------- | -- | -- | -- | -- | -------- | ------ | ---- | ---- | -------------- |
| heuristic | SQL_INJECTION          | 2  | 0  | 1  | 0  | 1.00     | 1.00   | 1.00 | 0.00 | ~1064          |
| heuristic | XSS_REFLECTED          | 2  | 0  | 2  | 0  | 1.00     | 1.00   | 1.00 | 0.00 | ~1064          |
| heuristic | XSS_STORED             | 1  | 0  | 1  | 0  | 1.00     | 1.00   | 1.00 | 0.00 | ~1064          |
| heuristic | BROKEN_AUTH            | 1  | 0  | 1  | 0  | 1.00     | 1.00   | 1.00 | 0.00 | ~1064          |
| heuristic | BROKEN_ACCESS_CONTROL  | 2  | 0  | 1  | 0  | 1.00     | 1.00   | 1.00 | 0.00 | ~1064          |
| heuristic | **TOTAL**              | 8  | 0  | 6  | 0  | 1.00     | 1.00   | 1.00 | 0.00 | ~1064          |
| llm       | **TOTAL** (sem Ollama) | 0  | 0  | 6  | 8  | 0.00     | 0.00   | 0.00 | 0.00 | ~2468          |

Leitura: no gabarito atual a heurística acerta os 14 casos. A linha `llm` acima é com o
Ollama **fora do ar** (todos os vereditos viram "indisponível" = não vulnerável), só para
mostrar a comparação lado a lado; os números reais do LLM saem com o Ollama rodando.

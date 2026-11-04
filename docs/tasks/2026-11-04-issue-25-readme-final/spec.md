# Spec — README final e evidências de execução (issue #25)

Referência: issue #25 (Fase 7 de `docs/build-order.md`). Depende de #22 (benchmark na CLI).
Fecha a documentação para o relatório técnico.

## Objetivo

Deixar o `README.md` completo (instalação, laboratório, scan, benchmark, módulos, formatos)
e registrar evidências de execução reais, citáveis no relatório.

## Natureza da issue

É documentação — não há código de produção, então não há ciclo red→green de teste. O
critério é o conteúdo do README e das evidências; o `mvn clean verify` segue verde (nada de
código muda).

## Escopo

### `README.md` (expandir)

- **Visão geral** e os três tipos de falha (já há; manter enxuto).
- **Pré-requisitos** (Java 21, Maven, Docker, Ollama + `ollama pull` com a tag/digest fixos).
- **Laboratório:** subir/derrubar; **resetar ao estado de fábrica entre execuções do
  benchmark** (o XSS armazenado suja o banco); como o DVWA exige **login + nível `security`**
  e, para scan manual no DVWA, passar `--header "Cookie: PHPSESSID=...; security=low"`.
- **Build:** `mvn clean verify` (testes) e `mvn package -DskipTests` (só o jar
  `target/vulnscan.jar`). Nota de ambiente: o `SeleniumBrowserTest` é pulado se o Chrome não
  subir.
- **Scan:** sintaxe, **módulos** (`sqli`, `xss`, `xss-dom`, `xss-stored`, `api`),
  `--detector heuristic|llm|both`, `--format md|json|html`, `--out`; aponta para `docs/cli.md`.
- **Benchmark:** `--ground-truth`, `--runs`, `--out`; o que sai (`run-<n>.csv`,
  `summary.csv`); aponta para `docs/experiment.md`.
- **Estrutura**, **Documentação** (índice de `docs/`), **Licença** (já há).
- **Evidências:** uma linha apontando para `docs/evidencias.md`.

### `docs/evidencias.md` (novo)

Evidências de execução capturadas no laboratório local (DVWA + Juice Shop), para o relatório:
- um trecho de relatório de **scan** (SQLi no DVWA: veredito, payload, evidência);
- a **matriz do benchmark** (`summary.csv`) do lado heurístico sobre os 14 casos do gabarito;
- nota de que o lado LLM exige Ollama no ar (não roda no CI nem neste ambiente).
Os números são de uma execução real aqui; o relatório final usa a execução dos autores.

## Fora do escopo

- Fixar versões das imagens Docker (#26), CI (#27), revisão/extensão da regra de dependência
  (#29). Gerar os PDFs/figuras do relatório.

## Contratos tocados

Nenhum (documentação).

## Critérios de aceite

1. `README.md` cobre instalação, laboratório (com reset e o detalhe do DVWA), build, scan
   (módulos/detector/formatos) e benchmark.
2. `docs/evidencias.md` existe, com o trecho de scan e a matriz do benchmark reais.
3. `mvn clean verify` verde (nada de código muda).

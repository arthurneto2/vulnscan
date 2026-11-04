# Spec — CLI: --detector heuristic|llm|both (issue #19)

Referência: issue #19 (Fase 5 de `docs/build-order.md`) e `docs/cli.md`. Depende de #18
(`LlmDetector`). Liga os detectores no `Container` e expõe a escolha na CLI. Fecha o grosso
da Fase 5: o mesmo caso julgado por heurística e por LLM, com justificativa.

## Objetivo

Tornar o detector **escolhido por scan** (não fixo na composição). `--detector heuristic`
(padrão) usa só a heurística; `llm` usa só o modelo; `both` roda os dois sobre cada caso e
reporta os dois vereditos (o `Verdict.detector()` os distingue; o `MarkdownReportWriter` já
imprime o detector).

## Problema atual

O `Container` injeta um `Detector` fixo (`HeuristicDetector`) em cada módulo na construção.
Para escolher por scan, a construção do módulo passa a ser parametrizada pelo detector.

## Escopo

### `app/ScanRequest`

Ganha `String detector` (de `--detector`). Construtor de 5 argumentos (sem headers) e o de 4
continuam, com `detector` = `"heuristic"`. Nulo/vazio → tratado como `"heuristic"`.

### `app/ScanService` (refatoração do construtor)

```java
public ScanService(Map<String, Function<Detector, VulnModule>> moduleFactories,
                   Map<String, Detector> detectors,
                   List<ReportWriter> writers) { ... }
```

- `moduleFactories`: por nome de `--module`, na ordem de execução; cada fábrica cria o módulo
  com o `Detector` escolhido.
- `detectors`: por nome (`"heuristic"`, `"llm"`).
- `scan(request)`:
  1. valida alvo, formato e módulos (como hoje);
  2. resolve os detectores do pedido: `heuristic`→[heuristic], `llm`→[llm], `both`→[heuristic,
     llm]; desconhecido → `InvalidScanRequestException` (saída 1);
  3. para cada módulo selecionado (ordem do registro), para cada detector escolhido, cria o
     módulo com aquele detector; roda tudo num `Scanner` só e junta os `Finding`
     (module-major: os dois vereditos do mesmo módulo ficam adjacentes no relatório).
- Erro de uso continua antes de qualquer requisição.

### `app/Container`

- `detectors()`: `{"heuristic": new HeuristicDetector(), "llm": new LlmDetector(new
  OllamaClient(httpClient, config.ollamaBaseUrl(), config.ollamaModel()))}`.
- `moduleFactories()`: as mesmas cinco entradas, agora `Detector -> VulnModule` (closures que
  capturam `httpClient`/`browser`/`apiSession`/payloads/pontos).

### CLI `cli/ScanCommand`

- `--detector` (padrão `heuristic`), repassado ao `ScanRequest`. `docs/cli.md` já documenta.

## Fora do escopo

- `BenchmarkRunner` (#21) e os CSVs do experimento. Subir Ollama (não há no ambiente).
- Mudar o formato do relatório (o `detector` já aparece por achado).

## Contratos tocados (`docs/contracts.md`)

- `ScanRequest` (+ `detector`) e `ScanService` (construtor com fábricas + detectores).

## Critérios de aceite

1. `ScanServiceTest` (fábricas e detectores fakes): `heuristic` usa só o heurístico; `llm` só
   o llm; `both` roda os dois (dois `Finding` por caso vulnerável, um de cada
   `detector()`); detector desconhecido → `InvalidScanRequestException`; construtor de 4/5
   args do `ScanRequest` usa `heuristic`.
2. `MainTest`: `--detector both` chega ao `ScanRequest`; `--detector xpto` → saída 1.
3. `ContainerTest`: há detectores `heuristic` e `llm`; `--detector llm` roda (sem Ollama no
   alvo de teste → vereditos "LLM indisponível", sem derrubar o scan).
4. `mvn clean verify` verde, incluindo `DependencyRuleTest`.
5. Lab (parcial, sem Ollama): `scan --module sqli --detector both` no DVWA → cada caso com
   dois vereditos no relatório (heurístico real + `llm` "indisponível"). Veredito de LLM real
   exige `ollama serve` + `ollama pull mistral:7b-instruct-v0.3-q4_0` (execução manual).

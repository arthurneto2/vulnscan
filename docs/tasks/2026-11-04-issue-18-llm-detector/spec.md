# Spec — LlmDetector: prompt, truncamento e parse defensivo (issue #18)

Referência: issue #18 (Fase 5 de `docs/build-order.md`) e `docs/llm-contract.md`. Depende de
#17 (`OllamaClient`/porta `LlmClient`). A ligação na CLI (`--detector`) e no `Container` é a
#19.

## Objetivo

Implementação de IA da estratégia `Detector`: monta o prompt a partir da `Evidence`, chama a
porta `LlmClient`, e faz **parse defensivo** do texto para um `Verdict`. Linha que o
experimento compara com o `HeuristicDetector`.

## Escopo — `domain/detection/LlmDetector implements Detector`

- `LlmDetector(LlmClient llm)`; `llm` nulo → NPE. `name()` → `"llm"`.
- `judge(evidence)` **nunca lança exceção** (regra dura de `docs/llm-contract.md`):
  1. monta o prompt (abaixo) e chama `llm.complete(prompt)`;
  2. extrai o primeiro bloco `{...}` do texto (varredura por profundidade de chaves) e faz
     parse com Jackson;
  3. mapeia para `Verdict(vulnerable, confidence, rationale, "llm")`:
     `vulnerable` (boolean, obrigatório), `confidence` (double, ausente → 0.0, grampeado a
     `[0,1]`), `rationale` (texto, ausente → "");
  4. qualquer falha de parse (sem bloco `{...}`, JSON inválido, sem `vulnerable`) →
     `Verdict(false, 0.0, "resposta do LLM não parseável", "llm")`;
  5. exceção do `LlmClient` (timeout/transporte: `UncheckedIOException`; indisponível:
     `IllegalStateException`) → capturada → `Verdict(false, 0.0, "LLM indisponível: <msg>",
     "llm")` (baixa confiança, não erro fatal).
- `evidence` nulo → NPE (como o `HeuristicDetector`; é erro de programação, não do modelo).

### Prompt

Três partes (texto em português, determinístico), conforme `docs/llm-contract.md`:

1. Papel + formato: "Você é um analisador de segurança. Decida se a resposta HTTP indica
   uma vulnerabilidade do tipo informado. Responda só com JSON:
   `{"vulnerable": boolean, "confidence": number, "rationale": string}`."
2. Tipo da falha (`evidence.payload().type()`) e o payload aplicado (`value`).
3. Evidência: método + URL da requisição; os headers da resposta; e o corpo da resposta
   **truncado** a `MAX_BODY_CHARS = 4096` (com marca `… [truncado]` quando corta). Não manda
   a resposta inteira (janela pequena do modelo).

## Fora do escopo

- `--detector` na CLI e ligação no `Container` (#19) — aqui o `LlmDetector` não é instanciado
  pela composição ainda.
- Subir Ollama. Formato do prompt "ótimo" (só o contrato mínimo). Variância entre execuções
  (é do experimento).

## Contratos tocados

Nenhum. `Detector`, `LlmClient`, `Evidence`, `Verdict` ficam como estão. O `LlmDetector` é a
segunda implementação de `Detector`, já prevista em `docs/contracts.md`.

## Critérios de aceite (`LlmDetectorTest`, `LlmClient` fake, sem rede/Ollama)

1. `name()` é `"llm"`; `judge(null)` → NPE.
2. JSON válido (`{"vulnerable":true,"confidence":0.82,"rationale":"..."}`) → `Verdict`
   correspondente, `detector == "llm"`.
3. JSON cercado de texto (ex.: "Claro! {json} espero ter ajudado") → extrai o bloco e
   mapeia certo.
4. Lixo sem JSON → `Verdict(false, 0.0, "...não parseável", "llm")`, sem exceção.
5. JSON sem `vulnerable` → não parseável.
6. `confidence` fora de `[0,1]` → grampeado; `confidence`/`rationale` ausentes → 0.0 / "".
7. `LlmClient` que lança (timeout/indisponível) → `Verdict(false, 0.0, "LLM indisponível:
   ...", "llm")`, sem propagar.
8. O prompt passado ao `LlmClient` cita o tipo, o payload, o método+URL e **trunca** um corpo
   grande (marca de truncamento presente; tamanho ≤ limite + margem da marca).
9. `mvn clean verify` verde, incluindo `DependencyRuleTest` (domínio só usa Jackson? — ver
   nota).

## Nota de arquitetura (parse no domínio)

O `HeuristicDetector` é puro (regex do JDK). O `LlmDetector` precisa parsear JSON. Para não
pôr Jackson no domínio, o parse usa **`java.util` + varredura manual**? Não: Jackson já é
dependência e o `DependencyRuleChecker` restringe só imports de `adapter`/`app`/`cli` dentro
de `domain`/`port`, não bibliotecas externas. Confirmar no checker; se ele barrar Jackson no
domínio, cair para parse manual mínimo (extrair os três campos). **Decisão:** usar Jackson no
`LlmDetector` se o `DependencyRuleTest` permitir (ele checa só pacotes do projeto).

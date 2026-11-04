# Spec — OllamaClient implementando LlmClient (issue #17)

Referência: issue #17 (Fase 5 de `docs/build-order.md`) e `docs/llm-contract.md`. Depende de
#16 (modelo e URL fixados em `Config`).

## Objetivo

Adaptador da porta `LlmClient`: recebe um prompt pronto e devolve o **texto cru** do modelo.
Só fala HTTP com o Ollama (`POST /api/generate`); não conhece o formato do veredito — o
parse para `Verdict` é do `LlmDetector` (#18).

## Escopo

### `adapter/llm/OllamaClient implements LlmClient`

- Construtor `OllamaClient(HttpClient http, URI baseUrl, String model)` — reusa a porta
  `HttpClient` (transporte e timeout do `JdkHttpClient`); nulos → NPE.
- `complete(prompt)`:
  1. monta o corpo JSON (com Jackson) de `/api/generate`:
     `{"model":<model>,"prompt":<prompt>,"stream":false,"format":"json","options":{"temperature":0.1}}`
     (`stream:false` e `format:"json"` do contrato; temperatura baixa = constante
     `TEMPERATURE = 0.1`);
  2. `POST baseUrl.resolve("/api/generate")` com `Content-Type: application/json`;
  3. status fora de 2xx → `IllegalStateException` (status + corpo truncado);
  4. faz parse do envelope do Ollama e devolve o campo `response` (o texto do modelo, cru).
     `response` ausente/não textual → `IllegalStateException`.
- O adaptador trunca o corpo truncado só para a mensagem de erro (não o `response`).
- Transporte/timeout: o `JdkHttpClient` lança `UncheckedIOException` (timeout vira
  `HttpTimeoutException` dentro). O `OllamaClient` **não** captura — propaga; tratar como
  veredito de baixa confiança é do `LlmDetector` (#18), que faz o `Verdict`.

### Observações

- O `response` do modelo é, com `format:"json"`, ele mesmo um JSON com
  `{vulnerable,confidence,rationale}`; mas isso é problema do `LlmDetector`. Aqui devolve-se
  o texto como veio.
- Temperatura fica como constante do adaptador (0.1), documentada; se virar configurável,
  entra em `Config` numa issue própria.

## Fora do escopo

- `LlmDetector` (#18): montar o prompt, parse para `Verdict`, tratamento de timeout como
  baixa confiança. Registro do módulo no `Container` e `--detector` na CLI (#18).
- Subir Ollama (teste usa servidor HTTP local em processo).

## Contratos tocados

- Novo adaptador `OllamaClient` (a porta `LlmClient` não muda). Registrado em
  `docs/contracts.md` na lista de adaptadores.

## Critérios de aceite (`OllamaClientTest`, `HttpServer` do JDK, sem Ollama)

1. `complete(prompt)` faz `POST /api/generate` e devolve o campo `response` do JSON do Ollama.
2. O corpo enviado tem `model` (o fixado), `prompt` (o recebido), `stream:false`,
   `format:"json"` e `options.temperature` baixo; `Content-Type: application/json`.
3. Status 500 → `IllegalStateException` (com o status).
4. JSON sem `response` → `IllegalStateException`.
5. Construtor rejeita nulos.
6. `mvn clean verify` verde, incluindo `DependencyRuleTest` (o adaptador pode usar Jackson).

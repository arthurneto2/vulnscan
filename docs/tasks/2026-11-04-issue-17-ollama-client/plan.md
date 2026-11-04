# Plano — OllamaClient (issue #17)

Spec: `spec.md` nesta pasta. Branch: `issue-17-ollama-client`.

Teste contra `com.sun.net.httpserver.HttpServer` em porta efêmera; `HttpClient` real
(`JdkHttpClient`) apontando para ele. Sem Ollama.

## Passos

- [x] **1. Esqueleto + POST e `response`.** `OllamaClientTest#devolveOCampoResponse`,
  `#fazPostEmApiGenerate`. Green: construtor, monta corpo mínimo, POST, extrai `response`.

- [x] **2. Corpo da chamada.** `#corpoTemModeloPromptStreamFalseEFormatJson`,
  `#enviaContentTypeJson`. Green: Jackson monta o corpo com os campos e headers.

- [x] **3. Erros.** `#statusNao2xxLancaIllegalState`, `#semCampoResponseLancaIllegalState`,
  `#construtorRejeitaNulos`. Javadoc. Atualizar `docs/contracts.md`.

- [x] **4. Checkpoint.** `mvn clean verify` verde. Marcar passos, commits, push.
  (Sem checkpoint de lab com Ollama real: é execução manual / #18+.)
  - Feito: `mvn clean verify` com 215 testes verdes; `OllamaClientTest` (7) contra
    `HttpServer` do JDK, sem Ollama. Não registrado no `Container` (consumidor só na #18).

# Plano — JdkHttpClient (issue #3)

Spec: `spec.md` nesta pasta. Branch: `issue-3-jdk-http-client`.

- Produção: `src/main/java/br/ufv/vulnscan/adapter/http/JdkHttpClient.java`.
- Teste: `src/test/java/br/ufv/vulnscan/adapter/http/JdkHttpClientTest.java`, com um
  `HttpServer` do JDK em porta `0` criado no `@BeforeEach` e parado no `@AfterEach`.
  Cada teste registra seu próprio handler (`server.createContext`).

Cada passo: teste primeiro (red por asserção), mínimo para passar (green), refactor.
Verificação de cada passo: `mvn test -Dtest=JdkHttpClientTest`.

## Passos

- [x] **1. Esqueleto + status e corpo.**
  - Teste: `#devolveStatusECorpo` — handler responde `201` com `"olá"` (UTF-8,
    `Content-Type: text/plain; charset=utf-8`); espera `status == 201` e `body == "olá"`.
  - Para o red ser por asserção: `JdkHttpClient.send` devolvendo
    `new HttpResponse(0, Map.of(), "", 0)`.
  - Green: `java.net.http.HttpClient` com `Version.HTTP_1_1`, `Redirect.NEVER`;
    `BodyHandlers.ofString()`.

- [x] **2. Requisição completa.**
  - Teste: `#enviaMetodoUrlHeadersECorpo` — `POST /login?x=1` com header
    `X-Test: abc` e corpo `user=admin`; handler guarda método, URI, header e corpo;
    teste confere os quatro.
  - Green: `HttpRequest.newBuilder(url).method(m, BodyPublishers.ofString(body))`,
    copiando headers; timeout do construtor.

- [x] **3. Corpo e headers nulos.**
  - Teste: `#getSemCorpoESemHeaders` — `HttpRequest("GET", url, null, null)` → `200`.
  - Green: `noBody()` e laço de headers protegido contra `null`.

- [x] **4. 500 não é exceção.**
  - Teste: `#status500DevolvidoComoResposta`. Deve passar de cara; confirmar mordida
    lançando exceção temporariamente para `status >= 500`.

- [x] **5. Headers da resposta.**
  - Testes: `#nomesDeHeaderEmMinusculas`, `#headerRepetidoJuntadoPorVirgula`,
    `#headersDaRespostaImutaveis`.
  - Green: percorrer `response.headers().map()`, `toLowerCase(Locale.ROOT)` no nome,
    `String.join(", ", valores)`, `Map.copyOf`.

- [x] **6. Redirect não seguido.**
  - Teste: `#naoSegueRedirect` — handler `/a` responde `302` com `Location: /b`; `/b`
    responde `200`. Espera `302`. (Já deve passar por `Redirect.NEVER`; confirmar mordida
    trocando para `Redirect.NORMAL` temporariamente.)

- [x] **7. Tempo decorrido.**
  - Teste: `#medeTempoDecorrido` — handler dorme 200 ms; `elapsedMillis >= 200`.
  - Green: `System.nanoTime()` antes/depois do `send`.

- [x] **8. Falhas de transporte.**
  - Testes: `#timeoutLancaUncheckedIOException` (cliente `Duration.ofMillis(100)`,
    handler dorme 1 s) e `#conexaoRecusadaLancaUncheckedIOException` (porta de um
    servidor já parado), com `assertThrows`.
  - Green: `catch (IOException e)` → `UncheckedIOException`; `catch (InterruptedException e)`
    → `Thread.currentThread().interrupt()` + `UncheckedIOException`.
  - *Desvio:* o `catch` já existia desde o passo 1 (`HttpClient.send` do JDK lança
    exceção checada, então sem ele não compila). Os dois testes passaram de cara; a
    mordida foi confirmada trocando o `throw` por um `return` de resposta vazia (ambos
    falharam com "nothing was thrown").

- [x] **9. Refactor, Javadoc e contrato.** Javadoc curto da classe (camada: adaptador
  HTTP). Linha nova em `docs/contracts.md` → "Decisões fixadas" (minúsculas, vírgula,
  redirect não seguido).

- [x] **10. Checkpoint.** `mvn clean verify` verde (inclui `DependencyRuleTest`). Marcar
  passos; commits; PR fechando #3.

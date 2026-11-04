# Contratos (já decididos)

Estas são as assinaturas públicas do domínio e das portas. **Não mude as assinaturas sem
atualizar este arquivo.** Os stubs em `src/main/java/.../` já contêm estas definições; o
trabalho é preencher os corpos das implementações.

Pacote raiz: `br.ufv.vulnscan`.

## Modelos de valor (`domain/model/`) — todos `record`, imutáveis

```java
enum VulnType { SQL_INJECTION, XSS_REFLECTED, XSS_STORED, BROKEN_AUTH, BROKEN_ACCESS_CONTROL }

enum Severity { INFO, LOW, MEDIUM, HIGH, CRITICAL }

// Alvo sob teste (um app do laboratório). headers vão em toda requisição ao alvo
// (ex.: cookie de sessão do DVWA, vindo de --header); nulo → vazio; cópia imutável.
record Target(String name, java.net.URI baseUrl, java.util.Map<String, String> headers) {
    Target(String name, java.net.URI baseUrl) { ... }   // headers vazios
}

// Um payload de ataque, carregado de arquivo ou gerado por um módulo.
record Payload(String id, VulnType type, String value, String description) {}

// Requisição e resposta HTTP (usadas pela porta HttpClient e pela evidência).
record HttpRequest(String method, java.net.URI url,
                   java.util.Map<String, String> headers, String body) {}

record HttpResponse(int status, java.util.Map<String, String> headers,
                    String body, long elapsedMillis) {
    // Resposta sintética de módulo com navegador: status 200, corpo = DOM renderizado,
    // content-type text/html e, se houve alert/confirm/prompt, este pseudo-header com os
    // textos juntados por ", ". O HeuristicDetector trata diálogo com o marcador como XSS.
    static final String DIALOGS_HEADER = "x-vulnscan-dialogs";
}

// O que um Detector recebe para julgar: o payload aplicado e a troca HTTP resultante.
record Evidence(Payload payload, HttpRequest request, HttpResponse response) {}

// O veredito de um Detector sobre uma Evidence.
//   detector = "heuristic" | "llm" (quem emitiu)
//   confidence em [0.0, 1.0]
record Verdict(boolean vulnerable, double confidence, String rationale, String detector) {}

// Uma falha encontrada: evidência + veredito + classificação.
record Finding(VulnType type, Severity severity, Target target,
               Evidence evidence, Verdict verdict) {}

// Resultado de um scan completo de um alvo.
record ScanReport(Target target, java.time.Instant startedAt,
                  java.time.Instant finishedAt, java.util.List<Finding> findings) {}
```

## Estratégias internas do domínio (não são portas)

```java
// domain/detection/Detector.java
// Julga se uma evidência indica vulnerabilidade. Duas implementações:
//   HeuristicDetector (regex/regras, puro)  e  LlmDetector (usa a porta LlmClient).
public interface Detector {
    Verdict judge(Evidence evidence);
    String name();   // "heuristic" ou "llm"
}

// domain/module/VulnModule.java
// Um tipo de ataque de ponta a ponta. Recebe HttpClient e Detector por CONSTRUTOR
// (injeção de dependência); o método scan só recebe o alvo.
public interface VulnModule {
    VulnType type();
    java.util.List<Finding> scan(Target target);
}
```

## Módulos concretos (`domain/module/`)

```java
// Ponto de injeção: GET em path (relativo à URL base; pode ter query fixa) com o payload
// no parâmetro de query `parameter`.
record InjectionPoint(String path, String parameter) {
    // baseUrl.resolve(path) + ?/& + parameter=<value URL-encoded UTF-8>
    public java.net.URI url(java.net.URI baseUrl, String value) { ... }
}

// Parser puro do arquivo de payloads (id<TAB>valor<TAB>descrição; # e vazias ignoradas).
// Linha malformada ou id repetido → IllegalArgumentException citando a linha.
public final class PayloadFile {
    public static java.util.List<Payload> parse(String content, VulnType type) { ... }
}

// SQLi: um GET por (ponto × payload), na ordem; positivo → Finding HIGH e encerra o ponto.
// Nulos → NPE; listas vazias ou payload de outro tipo → IllegalArgumentException.
public final class SqlInjectionModule implements VulnModule {
    public SqlInjectionModule(HttpClient http, Detector detector,
                              java.util.List<Payload> payloads,
                              java.util.List<InjectionPoint> points) { ... }
}

// XSS refletido: igual ao SQLi, mas cada payload traz {marker}, trocado por markers.get() a
// cada requisição; o payload concreto vai na Evidence. Positivo → Finding MEDIUM e encerra o
// ponto. Payload de outro tipo ou sem {marker} → IllegalArgumentException.
public final class XssModule implements VulnModule {
    public XssModule(HttpClient http, Detector detector,
                     java.util.List<Payload> payloads,
                     java.util.List<InjectionPoint> points,
                     java.util.function.Supplier<String> markers) { ... }
}

// --- XSS via navegador (#12). Interface do PRÓPRIO módulo, não é porta: só para o domínio
// não depender de Selenium (adaptador: adapter/browser/SeleniumBrowser).
record RenderedPage(String html, java.util.List<String> dialogs, long elapsedMillis) {
    // dialogs: textos de alert/confirm/prompt, na ordem; nulo → vazio; cópia imutável.
    public HttpResponse toResponse() { ... }   // 200, text/html, corpo = DOM, DIALOGS_HEADER se houver
}

public interface XssBrowser {
    // De headers, só "Cookie" é aplicado (WebDriver não manda header arbitrário).
    RenderedPage open(java.net.URI url, java.util.Map<String, String> headers);
}

// XSS de DOM (tipo XSS_REFLECTED): como o XssModule, mas abre a URL no navegador.
// Positivo → Finding MEDIUM e encerra o ponto.
public final class DomXssModule implements VulnModule {
    public DomXssModule(XssBrowser browser, Detector detector,
                        java.util.List<Payload> payloads,
                        java.util.List<InjectionPoint> points,
                        java.util.function.Supplier<String> markers) { ... }
}

// Ponto de XSS armazenado: POST de formulário em submitPath (fixedFields na ordem + payload
// em payloadField) e a página viewPath onde o conteúdo aparece.
record StoredXssPoint(String submitPath, java.util.Map<String, String> fixedFields,
                      String payloadField, String viewPath) {}

// XSS armazenado (tipo XSS_STORED): grava pelo HttpClient, vê pelo XssBrowser. Evidence =
// (payload, o POST, página renderizada). Positivo → Finding HIGH e encerra o ponto.
public final class StoredXssModule implements VulnModule {
    public StoredXssModule(HttpClient http, XssBrowser browser, Detector detector,
                           java.util.List<Payload> payloads,
                           java.util.List<StoredXssPoint> points,
                           java.util.function.Supplier<String> markers) { ... }
}

// --- API REST auth/autorização (#14). ApiSession é interface do PRÓPRIO módulo (não é
// porta): isola o login do alvo (parse de JSON) do domínio. Adaptador: adapter/http/JuiceShopSession.
public interface ApiSession {
    AuthSession authenticate(Target target);
    record AuthSession(String token, String ownResourceId) {}   // não nulos
}

enum AuthMode { NONE, USER_TOKEN, USER_TOKEN_OTHER_RESOURCE }
record AuthProbe(VulnType type, String name, AuthMode mode, String pathTemplate) {}

// Cada probe é um GET indevido; positivo → Finding HIGH do tipo do probe (não encerra os
// outros). Login uma vez, só se algum probe pede token. type() = BROKEN_AUTH (interface).
public final class ApiAuthModule implements VulnModule {
    public ApiAuthModule(HttpClient http, Detector detector, ApiSession session,
                         java.util.List<AuthProbe> probes) { ... }
}
```

## Portas (`port/`) — a fronteira com o mundo externo

```java
// port/HttpClient.java  → adapter: JdkHttpClient (java.net.http)
public interface HttpClient {
    HttpResponse send(HttpRequest request);
}

// port/LlmClient.java  → adapter: OllamaClient (POST localhost:11434)
// Recebe um prompt pronto e devolve o texto cru do modelo (espera-se JSON; ver
// docs/llm-contract.md). Quem monta o prompt e faz o parse é o LlmDetector.
public interface LlmClient {
    String complete(String prompt);
}

// port/ReportWriter.java  → adapters: Markdown / Json / Html
public interface ReportWriter {
    void write(ScanReport report, java.nio.file.Path output);
    String format();   // "md" | "json" | "html"
}
```

## Orquestração e composição

```java
// domain/Scanner.java — orquestra os módulos pedidos sobre um alvo.
public final class Scanner {
    public Scanner(java.util.List<VulnModule> modules) { ... }
    public ScanReport scan(Target target) { ... }
}

// app/ScanRequest.java — pedido vindo da CLI. modules vazio = todos; out nulo = report.<format>;
// headers (de --header) vão para Target.headers; nulo → vazio.
public record ScanRequest(java.net.URI target, java.util.List<String> modules,
                          String format, java.nio.file.Path out,
                          java.util.Map<String, String> headers) {
    public ScanRequest(java.net.URI target, java.util.List<String> modules,
                       String format, java.nio.file.Path out) { ... }   // headers vazios
}

// app/ScanService.java — caso de uso chamado pela CLI.
//   modules: por nome de --module, na ordem de execução; writers: um por format().
//   Erro de uso → InvalidScanRequestException (CLI: saída 1). Erro de execução propaga (saída 2).
public final class ScanService {
    public ScanService(java.util.Map<String, VulnModule> modules,
                       java.util.List<ReportWriter> writers) { ... }
    public ScanReport scan(ScanRequest request) { ... }
}

// app/InvalidScanRequestException.java — alvo, módulo ou formato inválido.
public final class InvalidScanRequestException extends RuntimeException { ... }

// app/Config.java — configuração fixa. Modelo e endpoint do Ollama fixados (#16).
public record Config(java.time.Duration httpTimeout, java.net.URI ollamaBaseUrl, String ollamaModel) {
    public static Config defaults() { ... }   // 30 s, http://localhost:11434, mistral:7b-instruct-v0.3-q4_0
}

// app/Container.java — raiz de composição: instancia adaptadores e injeta nos módulos.
public final class Container {
    public Container(Config config) { ... }
    public ScanService scanService() { ... }
}
```

## Decisões fixadas (não reabrir sem motivo)

- `Detector` devolve sempre um `Verdict` com confiança e justificativa — nunca só um
  booleano. Isso alimenta tanto o relatório quanto o experimento.
- `VulnModule` recebe colaboradores por construtor, não por parâmetro de método. Mantém a
  injeção de dependência explícita e os módulos fáceis de compor.
- `LlmClient.complete` devolve texto cru; o parse de JSON fica no `LlmDetector`, para o
  adaptador não conhecer o formato do veredito.
- Mapas de header são `Map<String,String>` simples (não multimap). Suficiente para o
  escopo; se um header repetir, documente e junte por vírgula.
- `JdkHttpClient`: nomes de header da **resposta** em minúsculas; valores repetidos
  juntados por `", "` na ordem recebida; redirecionamentos **não** são seguidos (o 3xx
  volta como resposta); 4xx/5xx são resposta normal; falha de transporte (conexão,
  timeout) lança `UncheckedIOException`.

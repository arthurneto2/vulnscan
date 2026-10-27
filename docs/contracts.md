# Contratos (já decididos)

Estas são as assinaturas públicas do domínio e das portas. **Não mude as assinaturas sem
atualizar este arquivo.** Os stubs em `src/main/java/.../` já contêm estas definições; o
trabalho é preencher os corpos das implementações.

Pacote raiz: `br.ufv.vulnscan`.

## Modelos de valor (`domain/model/`) — todos `record`, imutáveis

```java
enum VulnType { SQL_INJECTION, XSS_REFLECTED, XSS_STORED, BROKEN_AUTH, BROKEN_ACCESS_CONTROL }

enum Severity { INFO, LOW, MEDIUM, HIGH, CRITICAL }

// Alvo sob teste (um app do laboratório).
record Target(String name, java.net.URI baseUrl) {}

// Um payload de ataque, carregado de arquivo ou gerado por um módulo.
record Payload(String id, VulnType type, String value, String description) {}

// Requisição e resposta HTTP (usadas pela porta HttpClient e pela evidência).
record HttpRequest(String method, java.net.URI url,
                   java.util.Map<String, String> headers, String body) {}

record HttpResponse(int status, java.util.Map<String, String> headers,
                    String body, long elapsedMillis) {}

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

// app/ScanRequest.java — pedido vindo da CLI. modules vazio = todos; out nulo = report.<format>.
public record ScanRequest(java.net.URI target, java.util.List<String> modules,
                          String format, java.nio.file.Path out) {}

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

// app/Config.java — configuração fixa. Modelo/URL do Ollama entram com #16/#17.
public record Config(java.time.Duration httpTimeout) {
    public static Config defaults() { ... }   // httpTimeout = 30 s
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

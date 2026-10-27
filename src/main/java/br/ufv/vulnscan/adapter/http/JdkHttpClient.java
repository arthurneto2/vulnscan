package br.ufv.vulnscan.adapter.http;

import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.port.HttpClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Adaptador HTTP: implementa a porta HttpClient com java.net.http (HTTP/1.1).
 * Não segue redirecionamentos e devolve 4xx/5xx como resposta normal, porque ambos são
 * evidência para o scanner. Falhas de transporte (conexão, timeout) viram
 * {@link UncheckedIOException}.
 */
public final class JdkHttpClient implements HttpClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_1_1)
            .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
            .build();

    private final Duration timeout;

    public JdkHttpClient() {
        this(DEFAULT_TIMEOUT);
    }

    /** @param timeout tempo máximo de cada requisição (padrão: 30 s). */
    public JdkHttpClient(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public HttpResponse send(HttpRequest request) {
        java.net.http.HttpRequest jdkRequest = toJdkRequest(request);
        try {
            long start = System.nanoTime();
            java.net.http.HttpResponse<String> response = client.send(jdkRequest, BodyHandlers.ofString());
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
            return new HttpResponse(response.statusCode(), flatten(response.headers()),
                    response.body(), elapsedMillis);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("requisição interrompida", e));
        }
    }

    private java.net.http.HttpRequest toJdkRequest(HttpRequest request) {
        BodyPublisher body = request.body() == null
                ? BodyPublishers.noBody()
                : BodyPublishers.ofString(request.body());
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(request.url())
                .timeout(timeout)
                .method(request.method(), body);
        if (request.headers() != null) {
            request.headers().forEach(builder::header);
        }
        return builder.build();
    }

    /** Nomes em minúsculas e valores repetidos juntados por ", " (contrato: Map simples). */
    private static Map<String, String> flatten(HttpHeaders headers) {
        Map<String, String> flat = new HashMap<>();
        headers.map().forEach((name, values) ->
                flat.merge(name.toLowerCase(Locale.ROOT), String.join(", ", values),
                        (a, b) -> a + ", " + b));
        return Map.copyOf(flat);
    }
}

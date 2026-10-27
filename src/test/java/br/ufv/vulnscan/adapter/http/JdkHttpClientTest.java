package br.ufv.vulnscan.adapter.http;

import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes do adaptador HTTP contra um servidor do JDK em processo, sem rede externa. */
class JdkHttpClientTest {

    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void devolveStatusECorpo() {
        handle("/", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            respond(exchange, 201, "olá");
        });

        HttpResponse response = new JdkHttpClient().send(get("/"));

        assertEquals(201, response.status());
        assertEquals("olá", response.body());
    }

    @Test
    void enviaMetodoUrlHeadersECorpo() {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<URI> uri = new AtomicReference<>();
        AtomicReference<String> header = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        handle("/login", exchange -> {
            method.set(exchange.getRequestMethod());
            uri.set(exchange.getRequestURI());
            header.set(exchange.getRequestHeaders().getFirst("X-Test"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "");
        });

        new JdkHttpClient().send(new HttpRequest("POST", url("/login?x=1"),
                Map.of("X-Test", "abc"), "user=admin"));

        assertEquals("POST", method.get());
        assertEquals("/login?x=1", uri.get().toString());
        assertEquals("abc", header.get());
        assertEquals("user=admin", body.get());
    }

    @Test
    void getSemCorpoESemHeaders() {
        AtomicReference<String> method = new AtomicReference<>();
        handle("/", exchange -> {
            method.set(exchange.getRequestMethod());
            respond(exchange, 200, "ok");
        });

        HttpResponse response = new JdkHttpClient().send(new HttpRequest("GET", url("/"), null, null));

        assertEquals("GET", method.get());
        assertEquals(200, response.status());
    }

    @Test
    void status500DevolvidoComoResposta() {
        handle("/", exchange -> respond(exchange, 500, "You have an error in your SQL syntax"));

        HttpResponse response = new JdkHttpClient().send(get("/"));

        assertEquals(500, response.status());
        assertEquals("You have an error in your SQL syntax", response.body());
    }

    @Test
    void nomesDeHeaderEmMinusculas() {
        handle("/", exchange -> {
            exchange.getResponseHeaders().add("X-Custom", "valor");
            respond(exchange, 200, "");
        });

        HttpResponse response = new JdkHttpClient().send(get("/"));

        assertEquals("valor", response.headers().get("x-custom"));
    }

    @Test
    void headerRepetidoJuntadoPorVirgula() {
        handle("/", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "a=1");
            exchange.getResponseHeaders().add("Set-Cookie", "b=2");
            respond(exchange, 200, "");
        });

        HttpResponse response = new JdkHttpClient().send(get("/"));

        assertEquals("a=1, b=2", response.headers().get("set-cookie"));
    }

    @Test
    void headersDaRespostaImutaveis() {
        handle("/", exchange -> respond(exchange, 200, ""));

        HttpResponse response = new JdkHttpClient().send(get("/"));

        assertFalse(response.headers().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> response.headers().put("x", "y"));
    }

    @Test
    void naoSegueRedirect() {
        handle("/a", exchange -> {
            exchange.getResponseHeaders().add("Location", "/b");
            respond(exchange, 302, "");
        });
        handle("/b", exchange -> respond(exchange, 200, "destino"));

        HttpResponse response = new JdkHttpClient().send(get("/a"));

        assertEquals(302, response.status());
        assertEquals("/b", response.headers().get("location"));
    }

    @Test
    void medeTempoDecorrido() {
        handle("/", exchange -> {
            sleep(200);
            respond(exchange, 200, "");
        });

        HttpResponse response = new JdkHttpClient().send(get("/"));

        assertTrue(response.elapsedMillis() >= 200, () -> "elapsedMillis = " + response.elapsedMillis());
    }

    @Test
    void timeoutLancaUncheckedIOException() {
        handle("/", exchange -> {
            sleep(1_000);
            respond(exchange, 200, "");
        });
        JdkHttpClient client = new JdkHttpClient(Duration.ofMillis(100));

        UncheckedIOException e = assertThrows(UncheckedIOException.class, () -> client.send(get("/")));
        assertInstanceOf(HttpTimeoutException.class, e.getCause());
    }

    @Test
    void conexaoRecusadaLancaUncheckedIOException() {
        HttpRequest request = get("/");
        server.stop(0);

        assertThrows(UncheckedIOException.class, () -> new JdkHttpClient().send(request));
    }

    private void handle(String path, HttpHandler handler) {
        server.createContext(path, handler);
    }

    private URI url(String pathAndQuery) {
        return URI.create("http://" + server.getAddress().getHostString() + ":"
                + server.getAddress().getPort() + pathAndQuery);
    }

    private HttpRequest get(String pathAndQuery) {
        return new HttpRequest("GET", url(pathAndQuery), null, null);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}

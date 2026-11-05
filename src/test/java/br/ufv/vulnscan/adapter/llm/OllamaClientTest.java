package br.ufv.vulnscan.adapter.llm;

import br.ufv.vulnscan.adapter.http.JdkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes do OllamaClient contra um servidor do JDK que imita /api/generate, sem Ollama. */
class OllamaClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private volatile String requestPath;
    private volatile String requestBody;
    private volatile String requestContentType;
    private volatile int status = 200;
    private volatile String responseJson = "{\"response\":\"{\\\"vulnerable\\\":true}\"}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void devolveOCampoResponse() {
        assertEquals("{\"vulnerable\":true}", client().complete("prompt-x"));
    }

    @Test
    void fazPostEmApiGenerate() {
        client().complete("prompt-x");

        assertEquals("/api/generate", requestPath);
    }

    @Test
    void corpoTemModeloPromptStreamFalseEFormatJson() throws IOException {
        client().complete("meu prompt");

        JsonNode body = MAPPER.readTree(requestBody);
        assertEquals("mistral:7b-instruct-v0.3-q4_0", body.path("model").asText());
        assertEquals("meu prompt", body.path("prompt").asText());
        assertFalse(body.path("stream").asBoolean(true), "stream deve ser false");
        assertEquals("json", body.path("format").asText());
        assertTrue(body.path("options").path("temperature").asDouble(1.0) <= 0.2,
                "temperatura baixa: " + body);
    }

    @Test
    void corpoLevaTetoDeTokens() throws IOException {
        client().complete("meu prompt");

        JsonNode options = MAPPER.readTree(requestBody).path("options");
        assertEquals(256, options.path("num_predict").asInt(-1), "teto de tokens gerados: " + options);
        assertEquals(0.1, options.path("temperature").asDouble(-1));
    }

    @Test
    void enviaContentTypeJson() {
        client().complete("x");

        assertTrue(requestContentType != null && requestContentType.contains("application/json"),
                "content-type: " + requestContentType);
    }

    @Test
    void statusNao2xxLancaIllegalState() {
        status = 500;
        responseJson = "erro interno";

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> client().complete("x"));
        assertTrue(e.getMessage().contains("500"), e.getMessage());
    }

    @Test
    void semCampoResponseLancaIllegalState() {
        responseJson = "{\"done\":true}";

        assertThrows(IllegalStateException.class, () -> client().complete("x"));
    }

    @Test
    void construtorRejeitaNulos() {
        JdkHttpClient http = new JdkHttpClient();
        assertThrows(NullPointerException.class, () -> new OllamaClient(null, baseUrl(), "m"));
        assertThrows(NullPointerException.class, () -> new OllamaClient(http, null, "m"));
        assertThrows(NullPointerException.class, () -> new OllamaClient(http, baseUrl(), null));
    }

    private OllamaClient client() {
        return new OllamaClient(new JdkHttpClient(), baseUrl(), "mistral:7b-instruct-v0.3-q4_0");
    }

    private URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestPath = exchange.getRequestURI().getPath();
        requestContentType = exchange.getRequestHeaders().getFirst("Content-Type");
        requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}

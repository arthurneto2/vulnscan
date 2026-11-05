package br.ufv.vulnscan.adapter.llm;

import br.ufv.vulnscan.adapter.http.JdkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

/**
 * Testes do OpenAiCompatibleClient contra um servidor do JDK que imita
 * {@code /v1/chat/completions}, sem provedor real nem internet.
 */
class OpenAiCompatibleClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String KEY = "nvapi-chave-de-teste-123";
    private static final String MODEL = "nvidia/modelo-de-teste";

    private HttpServer server;
    private volatile String requestMethod;
    private volatile String requestPath;
    private volatile String requestBody;
    private volatile String requestAuthorization;
    private volatile String requestContentType;
    private volatile int status = 200;
    private volatile String responseJson =
            "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"vulnerable\\\":false}\"}}]}";

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
    void enviaChatCompletionsComBearerEParametros() throws IOException {
        client().complete("meu prompt");

        assertEquals("POST", requestMethod);
        assertEquals("/v1/chat/completions", requestPath, "mantém o /v1 da URL base");
        assertEquals("Bearer " + KEY, requestAuthorization);
        assertTrue(requestContentType != null && requestContentType.contains("application/json"),
                "content-type: " + requestContentType);
        JsonNode body = MAPPER.readTree(requestBody);
        assertEquals(MODEL, body.path("model").asText());
        assertEquals(1, body.path("messages").size(), "uma mensagem: " + body);
        assertEquals("user", body.path("messages").path(0).path("role").asText());
        assertEquals("meu prompt", body.path("messages").path(0).path("content").asText());
        assertEquals(0.1, body.path("temperature").asDouble(-1));
        assertEquals(256, body.path("max_tokens").asInt(-1));
        assertFalse(body.path("stream").asBoolean(true), "stream deve ser false");
    }

    @Test
    void devolveConteudoDaPrimeiraEscolha() {
        assertEquals("{\"vulnerable\":false}", client().complete("x"));
    }

    /** O corpo de erro de alguns provedores ecoa a chave ("Invalid API key: nvapi-…"). */
    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void statusDeErroLancaComStatusESemVazarChave(int code) {
        status = code;
        responseJson = "{\"error\":{\"message\":\"Invalid API key: " + KEY + "\"}}";

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> client().complete("x"));

        assertTrue(e.getMessage().contains(String.valueOf(code)), e.getMessage());
        assertFalse(e.getMessage().contains(KEY), "a chave vazou: " + e.getMessage());
    }

    @Test
    void respostaSemChoicesLanca() {
        responseJson = "{\"object\":\"chat.completion\",\"choices\":[]}";

        assertThrows(IllegalStateException.class, () -> client().complete("x"));
    }

    @Test
    void respostaQueNaoEJsonLanca() {
        responseJson = "<html>gateway</html>";

        assertThrows(IllegalStateException.class, () -> client().complete("x"));
    }

    @Test
    void construtorRejeitaNulos() {
        JdkHttpClient http = new JdkHttpClient();
        assertThrows(NullPointerException.class, () -> new OpenAiCompatibleClient(null, baseUrl(), MODEL, KEY));
        assertThrows(NullPointerException.class, () -> new OpenAiCompatibleClient(http, null, MODEL, KEY));
        assertThrows(NullPointerException.class, () -> new OpenAiCompatibleClient(http, baseUrl(), null, KEY));
        assertThrows(NullPointerException.class, () -> new OpenAiCompatibleClient(http, baseUrl(), MODEL, null));
    }

    private OpenAiCompatibleClient client() {
        return new OpenAiCompatibleClient(new JdkHttpClient(), baseUrl(), MODEL, KEY);
    }

    /** Como a da NVIDIA ({@code https://integrate.api.nvidia.com/v1}): a URL base termina em /v1. */
    private URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestMethod = exchange.getRequestMethod();
        requestPath = exchange.getRequestURI().getPath();
        requestAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
        requestContentType = exchange.getRequestHeaders().getFirst("Content-Type");
        requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}

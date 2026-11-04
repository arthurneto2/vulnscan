package br.ufv.vulnscan.adapter.llm;

import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.port.HttpClient;
import br.ufv.vulnscan.port.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;

/**
 * Adaptador: porta {@link LlmClient} via Ollama ({@code POST /api/generate}). Só fala HTTP —
 * monta o corpo da chamada e lê o envelope do Ollama, devolvendo o campo {@code response}
 * (o texto cru do modelo). O parse desse texto para {@code Verdict} é do {@code LlmDetector}.
 *
 * <p>Reusa a porta {@link HttpClient} (transporte e timeout). Falha de transporte/timeout
 * propaga como {@link UncheckedIOException}; status fora de 2xx ou {@code response} ausente →
 * {@link IllegalStateException}. Temperatura baixa fixa ({@value #TEMPERATURE}) para reduzir
 * a variação do modelo.
 */
public final class OllamaClient implements LlmClient {

    private static final String GENERATE_PATH = "/api/generate";
    private static final double TEMPERATURE = 0.1;
    private static final Map<String, String> JSON = Map.of("Content-Type", "application/json");

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    private final URI baseUrl;
    private final String model;

    public OllamaClient(HttpClient http, URI baseUrl, String model) {
        this.http = Objects.requireNonNull(http, "http");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.model = Objects.requireNonNull(model, "model");
    }

    @Override
    public String complete(String prompt) {
        HttpResponse response = http.send(new HttpRequest("POST", baseUrl.resolve(GENERATE_PATH), JSON,
                requestBody(prompt)));
        if (response.status() < 200 || response.status() >= 300) {
            throw new IllegalStateException("Ollama respondeu " + response.status() + ": "
                    + truncate(response.body()));
        }
        JsonNode node = parse(response.body()).path("response");
        if (!node.isTextual()) {
            throw new IllegalStateException("resposta do Ollama sem campo 'response': "
                    + truncate(response.body()));
        }
        return node.asText();
    }

    private String requestBody(String prompt) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("stream", false);
        body.put("format", "json");
        body.putObject("options").put("temperature", TEMPERATURE);
        try {
            return mapper.writeValueAsString(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("não montou o corpo da chamada ao Ollama", e);
        }
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body == null ? "" : body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("resposta do Ollama não é JSON: " + truncate(body), e);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String truncate(String body) {
        String s = body == null ? "" : body;
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}

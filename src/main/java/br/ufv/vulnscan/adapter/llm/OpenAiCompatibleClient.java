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
 * Adaptador: porta {@link LlmClient} via API compatível com OpenAI
 * ({@code POST <baseUrl>/chat/completions}), para usar um modelo hospedado (NVIDIA NIM, Groq,
 * Gemini etc.) no lugar do Ollama local (issue #65). Só fala HTTP e devolve
 * {@code choices[0].message.content}; o parse do veredito é do {@code LlmDetector}. Mesma
 * temperatura do {@code OllamaClient} ({@value #TEMPERATURE}); o teto de tokens gerados
 * ({@value #MAX_TOKENS}) é maior porque inclui o raciocínio dos modelos que o expõem.
 *
 * <p>Falha de transporte/timeout propaga como {@link UncheckedIOException}; status fora de 2xx,
 * corpo que não é JSON ou sem conteúdo → {@link IllegalStateException}. <b>A chave nunca aparece
 * em mensagem de erro</b>, nem quando o provedor a ecoa no corpo.
 */
public final class OpenAiCompatibleClient implements LlmClient {

    private static final String COMPLETIONS_PATH = "/chat/completions";
    private static final double TEMPERATURE = 0.1;
    /** Alto de propósito: modelos com raciocínio (Nemotron) gastam milhares de tokens no
     *  {@code reasoning_content} antes do {@code content}; com 256 a resposta vinha {@code null}. */
    private static final int MAX_TOKENS = 8192;
    private static final int MAX_ERROR_CHARS = 200;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    private final URI baseUrl;
    private final String model;
    private final String apiKey;

    public OpenAiCompatibleClient(HttpClient http, URI baseUrl, String model, String apiKey) {
        this.http = Objects.requireNonNull(http, "http");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.model = Objects.requireNonNull(model, "model");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
    }

    @Override
    public String complete(String prompt) {
        HttpResponse response = http.send(new HttpRequest("POST", completionsUri(),
                Map.of("Content-Type", "application/json", "Authorization", "Bearer " + apiKey),
                requestBody(prompt)));
        if (response.status() < 200 || response.status() >= 300) {
            throw new IllegalStateException("API de IA respondeu " + response.status() + ": "
                    + snippet(response.body()));
        }
        JsonNode content = parse(response.body()).path("choices").path(0).path("message").path("content");
        if (!content.isTextual()) {
            throw new IllegalStateException("resposta da API de IA sem choices[0].message.content: "
                    + snippet(response.body()));
        }
        return content.asText();
    }

    /** Acrescenta o path à URL base sem perder o {@code /v1} ({@code URI.resolve} o descartaria). */
    private URI completionsUri() {
        String base = baseUrl.toString();
        return URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + COMPLETIONS_PATH);
    }

    private String requestBody(String prompt) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.putArray("messages").addObject().put("role", "user").put("content", prompt);
        body.put("temperature", TEMPERATURE);
        body.put("max_tokens", MAX_TOKENS);
        body.put("stream", false);
        try {
            return mapper.writeValueAsString(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("não montou o corpo da chamada à API de IA", e);
        }
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body == null ? "" : body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("resposta da API de IA não é JSON: " + snippet(body));
        }
    }

    /** Trecho do corpo para mensagem de erro, com a chave mascarada caso o provedor a ecoe. */
    private String snippet(String body) {
        String s = Objects.requireNonNullElse(body, "").replace(apiKey, "***");
        return s.length() > MAX_ERROR_CHARS ? s.substring(0, MAX_ERROR_CHARS) : s;
    }
}

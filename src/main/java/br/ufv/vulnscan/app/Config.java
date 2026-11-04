package br.ufv.vulnscan.app;

import java.net.URI;
import java.time.Duration;

/**
 * Aplicação: configuração fixa do scanner, lida pela raiz de composição (e, na camada de IA,
 * pelo {@code OllamaClient} e pelo {@code LlmDetector}). Fixar modelo e endpoint do Ollama é
 * o que torna o experimento reproduzível.
 */
public record Config(Duration httpTimeout, URI ollamaBaseUrl, String ollamaModel) {

    private static final Duration DEFAULT_HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final URI DEFAULT_OLLAMA_BASE_URL = URI.create("http://localhost:11434");

    /**
     * Modelo LLM fixado (issue #16): Mistral 7B Instruct v0.3, quantização Q4_0, via Ollama.
     * O digest {@code sha256} correspondente está no README e em {@code docs/llm-contract.md}.
     */
    private static final String DEFAULT_OLLAMA_MODEL = "mistral:7b-instruct-v0.3-q4_0";

    /** Timeout HTTP de 30 s (comporta o SQLi temporal); Ollama em localhost com o modelo fixado. */
    public static Config defaults() {
        return new Config(DEFAULT_HTTP_TIMEOUT, DEFAULT_OLLAMA_BASE_URL, DEFAULT_OLLAMA_MODEL);
    }
}

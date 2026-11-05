package br.ufv.vulnscan.app;

import java.net.URI;
import java.time.Duration;

/**
 * Aplicação: configuração fixa do scanner, lida pela raiz de composição (e, na camada de IA,
 * pelo {@code OllamaClient} e pelo {@code LlmDetector}). Fixar modelo e endpoint do Ollama é
 * o que torna o experimento reproduzível. {@code httpTimeout} vale para o alvo;
 * {@code llmTimeout}, para cada chamada ao modelo (Ollama ou API).
 */
public record Config(Duration httpTimeout, URI ollamaBaseUrl, String ollamaModel, Duration llmTimeout) {

    private static final Duration DEFAULT_HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final URI DEFAULT_OLLAMA_BASE_URL = URI.create("http://localhost:11434");

    /**
     * Modelo LLM fixado: Qwen2.5 14B Instruct (Q4_K_M, Apache-2.0), via Ollama. Substituiu o Mistral
     * 7B na issue #61: os modelos de 7B não entendem saída com escape de HTML (FP fixo em XSS
     * escapado); o de 14B zerou os FP no gabarito. Para modelo local, 14B é o mínimo recomendado.
     * O digest {@code sha256} está no README e em {@code docs/llm-contract.md}.
     */
    private static final String DEFAULT_OLLAMA_MODEL = "qwen2.5:14b-instruct";

    /** O 14B leva ~20 s por veredito (máx. ~80 s numa GPU de 6 GB): 30 s cortaria parte deles. */
    private static final Duration DEFAULT_LLM_TIMEOUT = Duration.ofSeconds(120);

    /** Alvo com timeout de 30 s (comporta o SQLi temporal); Ollama local com 120 s e o modelo fixado. */
    public static Config defaults() {
        return new Config(DEFAULT_HTTP_TIMEOUT, DEFAULT_OLLAMA_BASE_URL, DEFAULT_OLLAMA_MODEL,
                DEFAULT_LLM_TIMEOUT);
    }
}

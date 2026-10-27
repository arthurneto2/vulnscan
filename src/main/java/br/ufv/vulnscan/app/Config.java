package br.ufv.vulnscan.app;

import java.time.Duration;

/**
 * Aplicação: configuração fixa do scanner, lida pela raiz de composição.
 * Modelo e URL do Ollama entram aqui com a camada de IA (issues #16/#17).
 */
public record Config(Duration httpTimeout) {

    /** Timeout HTTP de 30 s: comporta os payloads de SQLi temporal. */
    public static Config defaults() {
        return new Config(Duration.ofSeconds(30));
    }
}

package br.ufv.vulnscan.app;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Testes da configuração fixa do scanner. */
class ConfigTest {

    @Test
    void defaultsFixaTimeoutModeloEUrlDoOllama() {
        Config config = Config.defaults();

        assertEquals(Duration.ofSeconds(30), config.httpTimeout());
        assertEquals(URI.create("http://localhost:11434"), config.ollamaBaseUrl());
        assertEquals("qwen2.5:14b-instruct", config.ollamaModel());
        assertEquals(Duration.ofSeconds(120), config.ollamaTimeout());
    }
}

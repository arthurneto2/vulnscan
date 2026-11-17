package br.ufv.vulnscan.app;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Testes da configuração fixa do scanner. */
class ConfigTest {

    @Test
    void defaultsFixaTimeoutModeloEUrlDoOllama() {
        Config config = Config.defaults();

        assertEquals(Duration.ofSeconds(30), config.httpTimeout());
        assertEquals(URI.create("http://localhost:11434"), config.ollamaBaseUrl());
        assertEquals("qwen2.5:14b-instruct", config.ollamaModel());
        assertEquals(Duration.ofSeconds(120), config.llmTimeout());
    }

    @Test
    void resetDoLabDerrubaESobeOComposeDoLaboratorio() {
        assertEquals(List.of(
                List.of("docker", "compose", "-f", "lab/docker-compose.yml", "down"),
                List.of("docker", "compose", "-f", "lab/docker-compose.yml", "up", "-d")),
                Config.LAB_RESET_COMMANDS);
        assertEquals(Duration.ofSeconds(180), Config.LAB_READY_TIMEOUT);
        assertEquals(Duration.ofSeconds(2), Config.LAB_POLL_INTERVAL);
    }
}

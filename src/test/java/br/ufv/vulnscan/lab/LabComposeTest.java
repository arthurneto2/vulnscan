package br.ufv.vulnscan.lab;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Garante que as imagens do laboratório estão fixadas por digest (reprodutibilidade), sem rede. */
class LabComposeTest {

    private static final Path COMPOSE = Path.of("lab", "docker-compose.yml");

    @Test
    void imagensFixadasPorDigestSemLatest() throws IOException {
        List<String> images = Files.readAllLines(COMPOSE).stream()
                .map(String::strip)
                .filter(line -> line.startsWith("image:"))
                .toList();

        assertFalse(images.isEmpty(), "nenhuma linha image: em " + COMPOSE);
        for (String image : images) {
            assertTrue(image.contains("@sha256:"), "imagem sem digest: " + image);
            assertFalse(image.contains(":latest"), "imagem com :latest: " + image);
        }
    }
}

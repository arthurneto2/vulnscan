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

    /** #63: alvos deliberadamente vulneráveis só escutam em localhost, nunca em 0.0.0.0. */
    @Test
    void portasSoEmLocalhost() throws IOException {
        List<String> lines = Files.readAllLines(COMPOSE).stream().map(String::strip).toList();
        List<String> ports = new java.util.ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals("ports:")) {
                for (int j = i + 1; j < lines.size() && lines.get(j).startsWith("- "); j++) {
                    ports.add(lines.get(j).substring(2).replace("\"", "").strip());
                }
            }
        }

        assertFalse(ports.isEmpty(), "nenhuma porta publicada em " + COMPOSE);
        for (String port : ports) {
            assertTrue(port.startsWith("127.0.0.1:"), "porta exposta fora de localhost: " + port);
        }
    }
}

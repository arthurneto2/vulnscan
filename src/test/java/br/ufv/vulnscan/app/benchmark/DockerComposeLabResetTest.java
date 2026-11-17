package br.ufv.vulnscan.app.benchmark;

import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.port.HttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do DockerComposeLabReset com comandos inofensivos ({@code sh -c}) no lugar do docker e
 * um HttpClient falso no lugar dos alvos — sem Docker nem rede.
 */
class DockerComposeLabResetTest {

    private static final URI DVWA = URI.create("http://localhost:8080");
    private static final URI JUICE = URI.create("http://localhost:3000");
    private static final Duration PRAZO = Duration.ofSeconds(5);
    private static final Duration INTERVALO = Duration.ofMillis(10);

    @TempDir
    Path dir;

    @Test
    void rodaOsComandosEmOrdemEEsperaOAlvo() throws IOException {
        Path log = dir.resolve("log.txt");
        List<URI> consultadas = new ArrayList<>();
        HttpClient alvos = request -> {
            consultadas.add(request.url());
            return new HttpResponse(302, Map.of("location", "login.php"), "", 1);
        };
        LabReset reset = new DockerComposeLabReset(List.of(
                List.of("sh", "-c", "echo down >> '" + log + "'"),
                List.of("sh", "-c", "echo up >> '" + log + "'")), alvos, PRAZO, INTERVALO);

        reset.reset(List.of(DVWA, JUICE));

        assertEquals(List.of("down", "up"), Files.readAllLines(log));
        assertEquals(List.of(DVWA, JUICE), consultadas);
    }

    @Test
    void comandoComFalhaAbortaCitandoOComandoEOCodigo() {
        List<URI> consultadas = new ArrayList<>();
        HttpClient alvos = request -> {
            consultadas.add(request.url());
            return new HttpResponse(200, Map.of(), "", 1);
        };
        LabReset reset = new DockerComposeLabReset(List.of(List.of("sh", "-c", "exit 3")),
                alvos, PRAZO, INTERVALO);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> reset.reset(List.of(DVWA)));

        assertTrue(e.getMessage().contains("sh -c exit 3"), e.getMessage());
        assertTrue(e.getMessage().contains("3"), e.getMessage());
        assertTrue(consultadas.isEmpty(), "o alvo não deve ser consultado se o comando falhou");
    }

    @Test
    void alvoQueNaoRespondeNoPrazoAborta() {
        HttpClient fora = request -> {
            throw new java.io.UncheckedIOException(new java.net.ConnectException("Connection refused"));
        };
        LabReset reset = new DockerComposeLabReset(List.of(), fora, Duration.ofMillis(300), Duration.ofMillis(20));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> reset.reset(List.of(JUICE)));

        assertTrue(e.getMessage().contains(JUICE.toString()), e.getMessage());
    }

    @Test
    void alvoQueSobeDepoisDeAlgumasTentativasPassa() {
        int[] tentativas = {0};
        HttpClient subindo = request -> {
            if (++tentativas[0] <= 2) {
                throw new java.io.UncheckedIOException(new java.net.ConnectException("Connection refused"));
            }
            return new HttpResponse(200, Map.of(), "ok", 1);
        };
        LabReset reset = new DockerComposeLabReset(List.of(), subindo, PRAZO, INTERVALO);

        reset.reset(List.of(JUICE));

        assertEquals(3, tentativas[0]);
    }
}

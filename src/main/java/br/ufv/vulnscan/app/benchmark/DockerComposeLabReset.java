package br.ufv.vulnscan.app.benchmark;

import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.port.HttpClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Aplicação (experimento): {@link LabReset} real. Roda os comandos de reset do lab em ordem
 * ({@code docker compose ... down} e {@code ... up -d}, fixados no {@code Config}), sem shell, e
 * espera cada alvo responder a um {@code GET} pela porta {@link HttpClient} — qualquer status
 * HTTP serve (o DVWA responde 302 para o login); falha de transporte é "ainda subindo". Comando
 * com código ≠ 0 ou alvo mudo além do prazo → {@link IllegalStateException}.
 */
public final class DockerComposeLabReset implements LabReset {

    private final List<List<String>> commands;
    private final HttpClient http;
    private final Duration readyTimeout;
    private final Duration pollInterval;

    /**
     * @param commands     comandos rodados em ordem (sem shell)
     * @param readyTimeout prazo para cada alvo responder depois dos comandos
     * @param pollInterval espera entre tentativas
     */
    public DockerComposeLabReset(List<List<String>> commands, HttpClient http, Duration readyTimeout,
                                 Duration pollInterval) {
        this.commands = commands.stream().map(List::copyOf).toList();
        this.http = http;
        this.readyTimeout = readyTimeout;
        this.pollInterval = pollInterval;
    }

    @Override
    public void reset(List<URI> baseUrls) {
        for (List<String> command : commands) {
            run(command);
        }
        for (URI baseUrl : baseUrls) {
            awaitReady(baseUrl);
        }
    }

    private void awaitReady(URI baseUrl) {
        long deadline = System.nanoTime() + readyTimeout.toNanos();
        while (true) {
            try {
                http.send(new HttpRequest("GET", baseUrl, Map.of(), null));
                return;
            } catch (UncheckedIOException e) {
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException("reset do lab: " + baseUrl + " não respondeu em "
                            + readyTimeout.toSeconds() + " s (" + e.getMessage() + ")", e);
                }
            }
            sleep(pollInterval);
        }
    }

    private static void sleep(Duration interval) {
        try {
            Thread.sleep(interval);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("reset do lab interrompido esperando os alvos", e);
        }
    }

    private static void run(List<String> command) {
        String shown = String.join(" ", command);
        int exit;
        try {
            exit = new ProcessBuilder(command).inheritIO().start().waitFor();
        } catch (IOException e) {
            throw new IllegalStateException("reset do lab: não foi possível rodar '" + shown + "': "
                    + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("reset do lab interrompido em '" + shown + "'", e);
        }
        if (exit != 0) {
            throw new IllegalStateException("reset do lab: '" + shown + "' saiu com código " + exit);
        }
    }
}

package br.ufv.vulnscan.app;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes da raiz de composição: grafo montado à mão, sem framework de DI. O alvo é um
 * servidor do JDK em processo (porta efêmera), nunca o laboratório.
 */
class ContainerTest {

    @TempDir
    Path dir;

    private HttpServer server;
    private final List<String> received = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void configPadraoTemTimeoutDe30s() {
        assertEquals(Duration.ofSeconds(30), Config.defaults().httpTimeout());
    }

    @Test
    void scanServiceGravaRelatorioMarkdown() throws IOException {
        Path out = dir.resolve("report.md");
        ScanService service = new Container(Config.defaults()).scanService();

        service.scan(new ScanRequest(target(), List.of(), "md", out));

        String md = Files.readString(out);
        assertTrue(md.contains("# Relatório de scan — 127.0.0.1:" + port()), md);
        assertTrue(md.contains("Nenhum achado."), md);
    }

    @Test
    void moduloSmokeFazUmGetNoAlvo() throws IOException {
        Path out = dir.resolve("report.md");
        ScanService service = new Container(Config.defaults()).scanService();

        service.scan(new ScanRequest(target(), List.of("smoke"), "md", out));

        assertEquals(List.of("GET /"), received);
        assertTrue(Files.readString(out).contains("Nenhum achado."));
    }

    @Test
    void semModulosRodaAFumaca() {
        ScanService service = new Container(Config.defaults()).scanService();

        service.scan(new ScanRequest(target(), List.of(), "md", dir.resolve("report.md")));

        assertEquals(List.of("GET /"), received);
    }

    private int port() {
        return server.getAddress().getPort();
    }

    private URI target() {
        return URI.create("http://127.0.0.1:" + port() + "/");
    }
}

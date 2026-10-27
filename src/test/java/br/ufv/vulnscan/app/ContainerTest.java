package br.ufv.vulnscan.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes da raiz de composição: grafo montado à mão, sem framework de DI. */
class ContainerTest {

    @TempDir
    Path dir;

    @Test
    void configPadraoTemTimeoutDe30s() {
        assertEquals(Duration.ofSeconds(30), Config.defaults().httpTimeout());
    }

    @Test
    void scanServiceGravaRelatorioMarkdown() throws IOException {
        Path out = dir.resolve("report.md");
        ScanService service = new Container(Config.defaults()).scanService();

        // Ainda não há módulos registrados: o scan não faz requisição nenhuma.
        service.scan(new ScanRequest(URI.create("http://localhost:3000"), List.of(), "md", out));

        String md = Files.readString(out);
        assertTrue(md.contains("# Relatório de scan — localhost:3000"), md);
        assertTrue(md.contains("Nenhum achado."), md);
    }
}

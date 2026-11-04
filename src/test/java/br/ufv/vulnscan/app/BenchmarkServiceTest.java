package br.ufv.vulnscan.app;

import br.ufv.vulnscan.app.benchmark.CaseRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes do caso de uso de benchmark com CaseRunner fake e saída em @TempDir. */
class BenchmarkServiceTest {

    @TempDir
    Path dir;

    private static final String GT = """
            targets:
              - name: dvwa
                base_url: http://localhost:8080
                cases:
                  - id: sqli-pos
                    type: SQL_INJECTION
                    location: "/a?id="
                    vulnerable: true
                  - id: sqli-neg
                    type: SQL_INJECTION
                    location: "/b?id="
                    vulnerable: false
            """;

    // runner perfeito: prevê exatamente o gabarito
    private final CaseRunner perfect = (c, base, det) ->
            new CaseRunner.CaseResult(c.vulnerable(), 0.9, 1);

    @Test
    void gravaSummaryERunsNoOutDir() throws IOException {
        Path gt = write(GT);
        Path out = dir.resolve("res");

        Path summary = new BenchmarkService(perfect, List.of("heuristic")).run(
                new BenchmarkRequest(gt, 2, out));

        assertEquals(out.resolve("summary.csv"), summary);
        assertTrue(Files.exists(out.resolve("summary.csv")));
        assertTrue(Files.exists(out.resolve("run-1.csv")));
        assertTrue(Files.exists(out.resolve("run-2.csv")));
        assertTrue(Files.readString(summary).contains("SQL_INJECTION"));
    }

    @Test
    void runsDefault3QuandoNaoInformado() throws IOException {
        Path gt = write(GT);
        Path out = dir.resolve("res2");

        new BenchmarkService(perfect, List.of("heuristic")).run(new BenchmarkRequest(gt, 0, out));

        assertTrue(Files.exists(out.resolve("run-3.csv")));
    }

    @Test
    void gabaritoInexistenteLancaInvalidScanRequest() {
        assertThrows(InvalidScanRequestException.class,
                () -> new BenchmarkService(perfect, List.of("heuristic"))
                        .run(new BenchmarkRequest(dir.resolve("nao-existe.yaml"), 1, dir.resolve("o"))));
    }

    private Path write(String content) throws IOException {
        Path gt = dir.resolve("gt.yaml");
        Files.writeString(gt, content);
        return gt;
    }
}

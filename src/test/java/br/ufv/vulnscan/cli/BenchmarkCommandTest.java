package br.ufv.vulnscan.cli;

import br.ufv.vulnscan.app.BenchmarkRequest;
import br.ufv.vulnscan.app.BenchmarkService;
import br.ufv.vulnscan.app.InvalidScanRequestException;
import br.ufv.vulnscan.app.benchmark.CaseRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes da CLI benchmark via CommandLine.execute, com BenchmarkService injetado. */
class BenchmarkCommandTest {

    @TempDir
    Path dir;

    private final CaseRunner runner = (c, base, det) -> new CaseRunner.CaseResult(c.vulnerable(), 0.9, 1);

    @Test
    void benchmarkGravaESai0() throws IOException {
        Path gt = gabarito();
        Path out = dir.resolve("res");

        Result r = run("benchmark", "--ground-truth", gt.toString(), "--runs", "2", "--out", out.toString());

        assertEquals(0, r.code(), r.err());
        assertTrue(Files.exists(out.resolve("summary.csv")));
        assertTrue(r.out().contains("Benchmark concluído: 2 execução(ões)."), r.out());
    }

    @Test
    void semGroundTruthSai1() {
        Result r = run("benchmark");

        assertEquals(1, r.code());
        assertTrue(r.err().contains("--ground-truth"), r.err());
    }

    @Test
    void gabaritoInexistenteSai1SemStackTrace() {
        Result r = run("benchmark", "--ground-truth", dir.resolve("nao.yaml").toString());

        assertEquals(1, r.code());
        assertTrue(r.err().contains("erro: gabarito não encontrado"), r.err());
        assertFalse(r.err().contains("\tat "), r.err());
    }

    private Path gabarito() throws IOException {
        Path gt = dir.resolve("gt.yaml");
        Files.writeString(gt, """
                targets:
                  - name: dvwa
                    base_url: http://localhost:8080
                    cases:
                      - id: sqli-pos
                        type: SQL_INJECTION
                        location: "/a?id="
                        vulnerable: true
                """);
        return gt;
    }

    private Result run(String... args) {
        Main main = new Main(() -> {
            throw new UnsupportedOperationException("scan não usado");
        }, () -> new BenchmarkService(runner, List.of("heuristic")));
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cli = new CommandLine(main);
        cli.setOut(new PrintWriter(out));
        cli.setErr(new PrintWriter(err));
        int code = cli.execute(args);
        return new Result(code, out.toString(), err.toString());
    }

    private record Result(int code, String out, String err) {
    }
}

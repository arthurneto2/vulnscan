package br.ufv.vulnscan.cli;

import br.ufv.vulnscan.app.BenchmarkRequest;
import br.ufv.vulnscan.app.BenchmarkService;
import br.ufv.vulnscan.app.InvalidScanRequestException;
import br.ufv.vulnscan.app.benchmark.CaseRunner;
import br.ufv.vulnscan.app.benchmark.LabReset;
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

    @Test
    void benchmarkRepassaOLlmEscolhido() throws IOException {
        List<String> llms = new java.util.ArrayList<>();
        Main main = new Main(llm -> {
            throw new UnsupportedOperationException("scan não usado");
        }, llm -> {
            llms.add(llm);
            return new BenchmarkService(runner, List.of("heuristic"));
        });

        Result r = run(main, "benchmark", "--ground-truth", gabarito().toString(), "--runs", "1",
                "--out", dir.resolve("res").toString(), "--llm", "api");

        assertEquals(0, r.code(), r.err());
        assertEquals(List.of("api"), llms);
    }

    @Test
    void benchmarkLlmApiSemChaveSai1() throws IOException {
        br.ufv.vulnscan.app.Container container =
                new br.ufv.vulnscan.app.Container(br.ufv.vulnscan.app.Config.defaults(), java.util.Map.of());
        Main main = new Main(llm -> container.scanService(llm), llm -> container.benchmarkService(llm));

        Result r = run(main, "benchmark", "--ground-truth", gabarito().toString(), "--llm", "api");

        assertEquals(1, r.code(), r.err());
        assertTrue(r.err().contains("VULNSCAN_LLM_API_KEY"), r.err());
    }

    @Test
    void resetRepassaParaOPedido() throws IOException {
        List<String> resets = new java.util.ArrayList<>();

        Result r = run(withReset(urls -> resets.add("reset")), "benchmark", "--ground-truth",
                gabarito().toString(), "--runs", "2", "--out", dir.resolve("res").toString(), "--reset");

        assertEquals(0, r.code(), r.err());
        assertEquals(List.of("reset", "reset"), resets);
        assertFalse(r.err().contains("não será resetado"), r.err());
    }

    @Test
    void variasExecucoesSemResetAvisaNoStderr() throws IOException {
        Result r = run("benchmark", "--ground-truth", gabarito().toString(), "--runs", "2",
                "--out", dir.resolve("res").toString());

        assertEquals(0, r.code(), r.err());
        assertTrue(r.err().contains("aviso: o laboratório não será resetado entre as execuções"), r.err());
        assertTrue(r.err().contains("--reset"), r.err());
    }

    @Test
    void umaExecucaoSemResetNaoAvisa() throws IOException {
        Result r = run("benchmark", "--ground-truth", gabarito().toString(), "--runs", "1",
                "--out", dir.resolve("res").toString());

        assertEquals(0, r.code(), r.err());
        assertEquals("", r.err());
    }

    @Test
    void falhaNoResetSai2SemStackTrace() throws IOException {
        LabReset falha = urls -> {
            throw new IllegalStateException("reset do lab: 'docker compose down' saiu com código 1");
        };

        Result r = run(withReset(falha), "benchmark", "--ground-truth", gabarito().toString(),
                "--runs", "1", "--out", dir.resolve("res").toString(), "--reset");

        assertEquals(2, r.code(), r.err());
        assertTrue(r.err().contains("erro: reset do lab: 'docker compose down' saiu com código 1"), r.err());
        assertFalse(r.err().contains("\tat "), r.err());
    }

    private Main withReset(LabReset reset) {
        return new Main(() -> {
            throw new UnsupportedOperationException("scan não usado");
        }, () -> new BenchmarkService(runner, List.of("heuristic"), reset));
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
        return run(main, args);
    }

    private Result run(Main main, String... args) {
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

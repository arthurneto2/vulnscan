package br.ufv.vulnscan.cli;

import br.ufv.vulnscan.app.ScanService;
import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.ScanReport;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.domain.module.VulnModule;
import br.ufv.vulnscan.port.ReportWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes da CLI via CommandLine.execute, com stdout/stderr capturados. */
class MainTest {

    @TempDir
    Path dir;

    private final List<String> ran = new ArrayList<>();
    private final FakeWriter md = new FakeWriter();

    @Test
    void scanGravaRelatorioESai0() throws IOException {
        Path out = dir.resolve("r.md");

        Result result = run(new Main(), "scan", "--target", "http://localhost:3000", "--out", out.toString());

        assertEquals(0, result.code(), result.err());
        assertTrue(Files.readString(out).contains("Nenhum achado."));
        assertTrue(result.out().contains("Scan concluído: 0 achado(s). Relatório: " + out), result.out());
    }

    @Test
    void semOutUsaReportPontoFormato() {
        Result result = run(mainWith(registry("a")), "scan", "--target", "http://localhost:3000");

        assertEquals(0, result.code(), result.err());
        assertEquals(Path.of("report.md"), md.path);
        assertTrue(result.out().contains("Relatório: report.md"), result.out());
    }

    @Test
    void moduloRepetivelPassaTodos() {
        Result result = run(mainWith(registry("a", "b", "c")),
                "scan", "--target", "http://localhost:3000", "--module", "a", "--module", "c");

        assertEquals(0, result.code(), result.err());
        assertEquals(List.of("a", "c"), ran);
    }

    @Test
    void semTargetSai1() {
        Result result = run(mainWith(registry("a")), "scan");

        assertEquals(1, result.code());
        assertTrue(result.err().contains("--target"), result.err());
    }

    @Test
    void opcaoDesconhecidaSai1() {
        Result result = run(mainWith(registry("a")), "scan", "--target", "http://localhost:3000", "--xpto");

        assertEquals(1, result.code());
    }

    @Test
    void targetMalformadoSai1() {
        Result result = run(mainWith(registry("a")), "scan", "--target", "http://exa mple");

        assertEquals(1, result.code());
        assertEquals(List.of(), ran);
    }

    @Test
    void semSubcomandoSai1() {
        Result result = run(mainWith(registry("a")));

        assertEquals(1, result.code());
        assertTrue(result.err().contains("Usage: vulnscan"), result.err());
    }

    @Test
    void pedidoInvalidoSai1SemStackTrace() {
        Result result = run(mainWith(registry("sqli")),
                "scan", "--target", "http://localhost:3000", "--module", "csrf");

        assertEquals(1, result.code());
        assertTrue(result.err().contains("erro: módulo desconhecido: csrf (disponíveis: sqli)"), result.err());
        assertFalse(result.err().contains("\tat "), "stack trace no stderr:\n" + result.err());
    }

    @Test
    void erroDeExecucaoSai2SemStackTrace() {
        Map<String, VulnModule> modules = Map.of("sqli",
                new FakeModule("sqli", new IllegalStateException("alvo fora do ar")));

        Result result = run(mainWith(modules), "scan", "--target", "http://localhost:3000");

        assertEquals(2, result.code());
        assertTrue(result.err().contains("erro: alvo fora do ar"), result.err());
        assertFalse(result.err().contains("\tat "), "stack trace no stderr:\n" + result.err());
    }

    @Test
    void alvoForaDeLocalhostImprimeAviso() {
        Result result = run(mainWith(registry()), "scan", "--target", "http://example.com");

        assertEquals(0, result.code(), result.err());
        assertTrue(result.err().contains(
                "aviso: o alvo example.com não é localhost; o vulnscan é para o laboratório local."), result.err());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:3000", "http://LOCALHOST", "http://127.0.0.1:8080",
            "http://127.1.2.3", "http://[::1]:3000"})
    void alvoLocalNaoImprimeAviso(String target) {
        Result result = run(mainWith(registry()), "scan", "--target", target);

        assertEquals(0, result.code(), result.err());
        assertFalse(result.err().contains("aviso:"), result.err());
    }

    @Test
    void helpSai0() {
        Result result = run(mainWith(registry()), "--help");

        assertEquals(0, result.code());
        assertTrue(result.out().contains("Usage: vulnscan"), result.out());
    }

    @Test
    void scanHelpSai0() {
        Result result = run(mainWith(registry()), "scan", "--help");

        assertEquals(0, result.code());
        assertTrue(result.out().contains("--target"), result.out());
    }

    /** Main com um ScanService real montado sobre módulos e writer fake. */
    private Main mainWith(Map<String, VulnModule> modules) {
        return new Main(() -> new ScanService(modules, List.of(md)));
    }

    private Map<String, VulnModule> registry(String... names) {
        Map<String, VulnModule> modules = new LinkedHashMap<>();
        for (String name : names) {
            modules.put(name, new FakeModule(name, null));
        }
        return modules;
    }

    private static Result run(Main main, String... args) {
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

    /** VulnModule fake: anota o nome em {@code ran}; lança {@code failure} se houver. */
    private final class FakeModule implements VulnModule {
        private final String name;
        private final RuntimeException failure;

        FakeModule(String name, RuntimeException failure) {
            this.name = name;
            this.failure = failure;
        }

        @Override
        public VulnType type() {
            return VulnType.SQL_INJECTION;
        }

        @Override
        public List<Finding> scan(Target target) {
            ran.add(name);
            if (failure != null) {
                throw failure;
            }
            return List.of();
        }
    }

    /** ReportWriter fake (formato md): guarda o caminho, não grava nada. */
    private static final class FakeWriter implements ReportWriter {
        Path path;

        @Override
        public void write(ScanReport report, Path output) {
            path = output;
        }

        @Override
        public String format() {
            return "md";
        }
    }
}

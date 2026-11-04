package br.ufv.vulnscan.cli;

import br.ufv.vulnscan.app.ScanRequest;
import br.ufv.vulnscan.app.ScanService;
import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.Severity;
import br.ufv.vulnscan.domain.model.Verdict;
import br.ufv.vulnscan.domain.model.ScanReport;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.domain.detection.Detector;
import br.ufv.vulnscan.domain.module.VulnModule;
import br.ufv.vulnscan.port.ReportWriter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes da CLI via CommandLine.execute, com stdout/stderr capturados. */
class MainTest {

    @TempDir
    Path dir;

    private final List<String> ran = new ArrayList<>();
    private final List<Target> targets = new ArrayList<>();
    private final FakeWriter md = new FakeWriter();

    @Test
    void scanGravaRelatorioESai0() throws IOException {
        Path out = dir.resolve("r.md");
        // Container real: o módulo sqli ataca um servidor em processo (sem falha), não o lab.
        // Módulo explícito: "todos" abriria o navegador (xss-dom, xss-stored).
        List<String> received = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            String target = "http://127.0.0.1:" + server.getAddress().getPort() + "/";

            Result result = run(new Main(), "scan", "--target", target, "--module", "sqli", "--out", out.toString());

            assertEquals(0, result.code(), result.err());
            assertTrue(received.contains("GET /vulnerabilities/sqli/"), "recebidas: " + received);
            assertTrue(Files.readString(out).contains("Nenhum achado."));
            assertTrue(result.out().contains("Scan concluído: 0 achado(s). Relatório: " + out), result.out());
        } finally {
            server.stop(0);
        }
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
    void headerRepetivelChegaAoAlvo() {
        Result result = run(mainWith(registry("a")), "scan", "--target", "http://localhost:8080",
                "--header", "Cookie: PHPSESSID=abc; security=low", "--header", "X-Lab:  dvwa ");

        assertEquals(0, result.code(), result.err());
        assertEquals(Map.of("Cookie", "PHPSESSID=abc; security=low", "X-Lab", "dvwa"), targets.get(0).headers());
    }

    @Test
    void headerRepetidoJuntaPorVirgula() {
        Result result = run(mainWith(registry("a")), "scan", "--target", "http://localhost:8080",
                "--header", "Accept: text/html", "--header", "Accept: application/json");

        assertEquals(0, result.code(), result.err());
        assertEquals(Map.of("Accept", "text/html, application/json"), targets.get(0).headers());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sem-dois-pontos", ": valor-sem-nome", "  : x"})
    void headerInvalidoSai1SemRodar(String header) {
        Result result = run(mainWith(registry("a")), "scan", "--target", "http://localhost:8080",
                "--header", header);

        assertEquals(1, result.code());
        assertTrue(result.err().contains("erro: header inválido"), result.err());
        assertEquals(List.of(), ran);
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
        Map<String, Function<Detector, VulnModule>> factories = new LinkedHashMap<>();
        modules.forEach((name, module) -> factories.put(name, detector -> module));
        Map<String, Detector> detectors = new LinkedHashMap<>();
        detectors.put("heuristic", named("heuristic"));
        detectors.put("llm", named("llm"));
        return new Main(() -> new ScanService(factories, detectors, List.of(md)));
    }

    private static Detector named(String name) {
        return new Detector() {
            @Override
            public Verdict judge(Evidence e) {
                return new Verdict(false, 0.0, "", name);
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    private Map<String, VulnModule> registry(String... names) {
        Map<String, VulnModule> modules = new LinkedHashMap<>();
        for (String name : names) {
            modules.put(name, new FakeModule(name, null));
        }
        return modules;
    }

    /** Main com fábricas que marcam no Finding o nome do detector com que o módulo foi criado. */
    private Main mainMarking() {
        Map<String, Function<Detector, VulnModule>> factories = new LinkedHashMap<>();
        factories.put("sqli", detector -> new MarkingModule(detector));
        Map<String, Detector> detectors = new LinkedHashMap<>();
        detectors.put("heuristic", named("heuristic"));
        detectors.put("llm", named("llm"));
        return new Main(() -> new ScanService(factories, detectors, List.of(md)));
    }

    @Test
    void detectorBothRodaOsDoisPorCaso() {
        Result result = run(mainMarking(), "scan", "--target", "http://localhost:3000",
                "--module", "sqli", "--detector", "both");

        assertEquals(0, result.code(), result.err());
        assertEquals(List.of("heuristic", "llm"),
                md.written.findings().stream().map(f -> f.verdict().detector()).toList());
    }

    @Test
    void detectorPadraoEHeuristic() {
        Result result = run(mainMarking(), "scan", "--target", "http://localhost:3000", "--module", "sqli");

        assertEquals(0, result.code(), result.err());
        assertEquals(List.of("heuristic"),
                md.written.findings().stream().map(f -> f.verdict().detector()).toList());
    }

    @Test
    void detectorInvalidoSai1() {
        Result result = run(mainMarking(), "scan", "--target", "http://localhost:3000", "--detector", "xpto");

        assertEquals(1, result.code());
        assertTrue(result.err().contains("detector desconhecido"), result.err());
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

    /** Módulo que reporta um Finding cujo Verdict.detector() é o nome do detector recebido. */
    private final class MarkingModule implements VulnModule {
        private final Detector detector;

        MarkingModule(Detector detector) {
            this.detector = detector;
        }

        @Override
        public VulnType type() {
            return VulnType.SQL_INJECTION;
        }

        @Override
        public List<Finding> scan(Target target) {
            Payload payload = new Payload("caso", VulnType.SQL_INJECTION, "v", "d");
            Evidence evidence = new Evidence(payload,
                    new HttpRequest("GET", target.baseUrl(), Map.of(), null),
                    new HttpResponse(200, Map.of(), "", 1));
            return List.of(new Finding(VulnType.SQL_INJECTION, Severity.HIGH, target, evidence,
                    new Verdict(true, 0.9, "", detector.name())));
        }
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
            targets.add(target);
            if (failure != null) {
                throw failure;
            }
            return List.of();
        }
    }

    /** ReportWriter fake (formato md): guarda o caminho, não grava nada. */
    private static final class FakeWriter implements ReportWriter {
        Path path;
        ScanReport written;

        @Override
        public void write(ScanReport report, Path output) {
            written = report;
            path = output;
        }

        @Override
        public String format() {
            return "md";
        }
    }
}

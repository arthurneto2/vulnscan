package br.ufv.vulnscan.app;

import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.ScanReport;
import br.ufv.vulnscan.domain.model.Severity;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.domain.module.VulnModule;
import br.ufv.vulnscan.port.ReportWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes do caso de uso de scan com módulos e writer fake, sem rede nem disco. */
class ScanServiceTest {

    private static final URI LAB = URI.create("http://localhost:3000");

    @TempDir
    Path dir;

    private final List<String> ran = new ArrayList<>();
    private final FakeWriter md = new FakeWriter("md");

    @Test
    void gravaComOWriterDoFormatoEDevolveORelatorio() {
        Finding finding = new Finding(VulnType.SQL_INJECTION, Severity.HIGH, null, null, null);
        ScanService service = service(Map.of("sqli", new FakeModule("sqli", List.of(finding))));
        Path out = dir.resolve("saida.md");

        ScanReport report = service.scan(new ScanRequest(LAB, List.of(), "md", out));

        assertEquals(List.of(finding), report.findings());
        assertSame(report, md.written);
        assertEquals(out, md.path);
    }

    @Test
    void montaTargetComAuthorityEUrl() {
        FakeModule module = new FakeModule("sqli", List.of());

        service(Map.of("sqli", module)).scan(request(List.of()));

        assertEquals(new Target("localhost:3000", LAB), module.receivedTarget);
    }

    @Test
    void semModulosPedidosRodaTodosNaOrdem() {
        service(registry("a", "b", "c")).scan(request(List.of()));

        assertEquals(List.of("a", "b", "c"), ran);
    }

    @Test
    void rodaSoOsPedidosNaOrdemDoRegistroSemRepetir() {
        service(registry("a", "b", "c")).scan(request(List.of("b", "a", "b")));

        assertEquals(List.of("a", "b"), ran);
    }

    @Test
    void moduloDesconhecidoLancaInvalidScanRequest() {
        ScanService service = service(registry("sqli", "xss"));

        InvalidScanRequestException e = assertThrows(InvalidScanRequestException.class,
                () -> service.scan(request(List.of("sqli", "csrf"))));

        assertTrue(e.getMessage().contains("csrf"), e.getMessage());
        assertTrue(e.getMessage().contains("sqli, xss"), e.getMessage());
        assertEquals(List.of(), ran, "nenhum módulo deveria rodar");
    }

    @Test
    void formatoDesconhecidoLancaInvalidScanRequest() {
        ScanService service = service(registry("sqli"));

        InvalidScanRequestException e = assertThrows(InvalidScanRequestException.class,
                () -> service.scan(new ScanRequest(LAB, List.of(), "pdf", null)));

        assertTrue(e.getMessage().contains("pdf"), e.getMessage());
        assertTrue(e.getMessage().contains("md"), e.getMessage());
        assertEquals(List.of(), ran, "nenhum módulo deveria rodar");
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost:3000/x", "/relativo", "ftp://localhost/", "http:///sem-host"})
    void alvoInvalidoLancaInvalidScanRequest(String target) {
        ScanService service = service(registry("sqli"));

        assertThrows(InvalidScanRequestException.class,
                () -> service.scan(new ScanRequest(URI.create(target), List.of(), "md", null)));
        assertEquals(List.of(), ran, "nenhum módulo deveria rodar");
    }

    @Test
    void alvoNuloLancaInvalidScanRequest() {
        ScanService service = service(registry("sqli"));

        assertThrows(InvalidScanRequestException.class,
                () -> service.scan(new ScanRequest(null, List.of(), "md", null)));
    }

    @Test
    void saidaNulaViraReportPontoFormato() {
        service(registry("sqli")).scan(new ScanRequest(LAB, List.of(), "md", null));

        assertEquals(Path.of("report.md"), md.path);
    }

    @Test
    void excecaoDeModuloPropagaSemGravar() {
        IllegalStateException failure = new IllegalStateException("alvo fora do ar");
        ScanService service = service(Map.of("sqli", new FakeModule("sqli", List.of(), failure)));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.scan(request(List.of())));

        assertSame(failure, thrown);
        assertNull(md.written, "nada deveria ser gravado");
    }

    private ScanRequest request(List<String> modules) {
        return new ScanRequest(LAB, modules, "md", dir.resolve("report.md"));
    }

    private ScanService service(Map<String, VulnModule> modules) {
        return new ScanService(modules, List.of(md));
    }

    /** Registro ordenado a, b, c — a ordem em que o Container entregaria. */
    private Map<String, VulnModule> registry(String... names) {
        Map<String, VulnModule> modules = new LinkedHashMap<>();
        for (String name : names) {
            modules.put(name, new FakeModule(name, List.of()));
        }
        return modules;
    }

    /** VulnModule fake: anota o nome em {@code ran}, guarda o alvo e devolve findings fixos. */
    private final class FakeModule implements VulnModule {
        private final String name;
        private final List<Finding> findings;
        private final RuntimeException failure;
        Target receivedTarget;

        FakeModule(String name, List<Finding> findings) {
            this(name, findings, null);
        }

        FakeModule(String name, List<Finding> findings, RuntimeException failure) {
            this.name = name;
            this.findings = findings;
            this.failure = failure;
        }

        @Override
        public VulnType type() {
            return VulnType.SQL_INJECTION;
        }

        @Override
        public List<Finding> scan(Target target) {
            ran.add(name);
            receivedTarget = target;
            if (failure != null) {
                throw failure;
            }
            return findings;
        }
    }

    /** ReportWriter fake: guarda o último relatório e caminho recebidos. */
    private static final class FakeWriter implements ReportWriter {
        private final String format;
        ScanReport written;
        Path path;

        FakeWriter(String format) {
            this.format = format;
        }

        @Override
        public void write(ScanReport report, Path output) {
            written = report;
            path = output;
        }

        @Override
        public String format() {
            return format;
        }
    }
}

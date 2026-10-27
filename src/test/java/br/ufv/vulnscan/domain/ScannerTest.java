package br.ufv.vulnscan.domain;

import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.ScanReport;
import br.ufv.vulnscan.domain.model.Severity;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.domain.module.VulnModule;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes do orquestrador do domínio com módulos fake, sem rede. */
class ScannerTest {

    private static final Target TARGET = new Target("juice-shop", URI.create("http://localhost:3000"));

    @Test
    void semModulosDevolveRelatorioVazio() {
        ScanReport report = new Scanner(List.of()).scan(TARGET);

        assertEquals(TARGET, report.target());
        assertTrue(report.findings().isEmpty());
    }

    @Test
    void juntaFindingsDosModulosNaOrdem() {
        Finding a1 = finding(VulnType.SQL_INJECTION);
        Finding b1 = finding(VulnType.XSS_REFLECTED);
        Finding b2 = finding(VulnType.XSS_STORED);
        Scanner scanner = new Scanner(List.of(new FakeModule(List.of(a1)), new FakeModule(List.of(b1, b2))));

        ScanReport report = scanner.scan(TARGET);

        assertEquals(List.of(a1, b1, b2), report.findings());
    }

    @Test
    void cadaModuloRecebeOAlvo() {
        FakeModule a = new FakeModule(List.of());
        FakeModule b = new FakeModule(List.of());

        new Scanner(List.of(a, b)).scan(TARGET);

        assertEquals(TARGET, a.receivedTarget);
        assertEquals(TARGET, b.receivedTarget);
    }

    @Test
    void inicioEFimEnvolvemAExecucao() {
        FakeModule module = new FakeModule(List.of());
        Instant before = Instant.now();

        ScanReport report = new Scanner(List.of(module)).scan(TARGET);

        Instant after = Instant.now();
        assertFalse(report.startedAt().isBefore(before), "startedAt antes do scan");
        assertFalse(module.ranAt.isBefore(report.startedAt()), "módulo rodou antes de startedAt");
        assertFalse(report.finishedAt().isBefore(module.ranAt), "finishedAt antes do módulo terminar");
        assertFalse(after.isBefore(report.finishedAt()), "finishedAt depois do scan");
    }

    @Test
    void findingsDoRelatorioImutaveis() {
        ScanReport report = new Scanner(List.of(new FakeModule(List.of(finding(VulnType.SQL_INJECTION)))))
                .scan(TARGET);

        assertThrows(UnsupportedOperationException.class,
                () -> report.findings().add(finding(VulnType.XSS_REFLECTED)));
    }

    @Test
    void listaDeModulosCopiadaNoConstrutor() {
        FakeModule a = new FakeModule(List.of());
        FakeModule b = new FakeModule(List.of());
        List<VulnModule> modules = new ArrayList<>(List.of(a));
        Scanner scanner = new Scanner(modules);

        modules.add(b);
        scanner.scan(TARGET);

        assertEquals(TARGET, a.receivedTarget);
        assertNull(b.receivedTarget, "módulo adicionado depois da construção não deveria rodar");
    }

    @Test
    void excecaoDeModuloPropaga() {
        IllegalStateException failure = new IllegalStateException("alvo fora do ar");
        FakeModule b = new FakeModule(List.of());
        Scanner scanner = new Scanner(List.of(new FakeModule(List.of(), failure), b));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> scanner.scan(TARGET));

        assertSame(failure, thrown);
        assertNull(b.receivedTarget, "módulo seguinte não deveria rodar");
    }

    @Test
    void modulosNulosLancaNpe() {
        assertThrows(NullPointerException.class, () -> new Scanner(null));
    }

    @Test
    void alvoNuloLancaNpe() {
        Scanner scanner = new Scanner(List.of(new FakeModule(List.of())));

        assertThrows(NullPointerException.class, () -> scanner.scan(null));
    }

    private static Finding finding(VulnType type) {
        return new Finding(type, Severity.HIGH, TARGET, null, null);
    }

    /** VulnModule fake: devolve findings fixos e registra alvo e instante de execução. */
    private static final class FakeModule implements VulnModule {
        private final List<Finding> findings;
        private final RuntimeException failure;
        Target receivedTarget;
        Instant ranAt;

        FakeModule(List<Finding> findings) {
            this(findings, null);
        }

        FakeModule(List<Finding> findings, RuntimeException failure) {
            this.findings = findings;
            this.failure = failure;
        }

        @Override
        public VulnType type() {
            return VulnType.SQL_INJECTION;
        }

        @Override
        public List<Finding> scan(Target target) {
            receivedTarget = target;
            ranAt = Instant.now();
            if (failure != null) {
                throw failure;
            }
            return findings;
        }
    }
}

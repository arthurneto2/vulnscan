package br.ufv.vulnscan.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes de arquitetura: garante a regra de dependência entre camadas. */
class DependencyRuleTest {

    @TempDir
    Path root;

    @ParameterizedTest
    @ValueSource(strings = {"adapter", "app", "cli"})
    void domainImportandoCamadaExternaViola(String layer) throws IOException {
        write("br/ufv/vulnscan/domain/Foo.java", """
                package br.ufv.vulnscan.domain;

                import br.ufv.vulnscan.%s.X;

                class Foo {}
                """.formatted(layer));

        assertSingleViolation("Foo.java:3", "br.ufv.vulnscan." + layer);
    }

    @Test
    void importStaticDeAppEmDomainViola() throws IOException {
        write("br/ufv/vulnscan/domain/module/Foo.java", """
                package br.ufv.vulnscan.domain.module;

                import static br.ufv.vulnscan.app.Config.MODEL;

                class Foo {}
                """);

        assertSingleViolation("Foo.java:3", "br.ufv.vulnscan.app");
    }

    @Test
    void nomeQualificadoDeAdapterNoCorpoViola() throws IOException {
        write("br/ufv/vulnscan/domain/Foo.java", """
                package br.ufv.vulnscan.domain;

                class Foo {
                    br.ufv.vulnscan.adapter.http.JdkHttpClient client;
                }
                """);

        assertSingleViolation("Foo.java:4", "br.ufv.vulnscan.adapter");
    }

    @Test
    void portImportandoDomainDetectionViola() throws IOException {
        write("br/ufv/vulnscan/port/Bar.java", """
                package br.ufv.vulnscan.port;

                import br.ufv.vulnscan.domain.detection.Detector;

                interface Bar {}
                """);

        assertSingleViolation("Bar.java:3", "br.ufv.vulnscan.domain.detection");
    }

    @Test
    void portComImportCuringaDeDomainViola() throws IOException {
        write("br/ufv/vulnscan/port/Bar.java", """
                package br.ufv.vulnscan.port;

                import br.ufv.vulnscan.domain.*;

                interface Bar {}
                """);

        assertSingleViolation("Bar.java:3", "br.ufv.vulnscan.domain.*");
    }

    @Test
    void portImportandoDomainModelEJdkNaoViola() throws IOException {
        write("br/ufv/vulnscan/port/Bar.java", """
                package br.ufv.vulnscan.port;

                import br.ufv.vulnscan.domain.model.ScanReport;
                import br.ufv.vulnscan.port.HttpClient;

                import java.nio.file.Path;

                interface Bar {}
                """);

        assertEquals(List.of(), DependencyRuleChecker.violations(root));
    }

    @Test
    void codigoDeProducaoRespeitaARegra() throws IOException {
        List<String> violations = DependencyRuleChecker.violations(Path.of("src/main/java"));

        assertTrue(violations.isEmpty(),
                () -> "Violações da regra de dependência:\n" + String.join("\n", violations));
    }

    private void assertSingleViolation(String location, String referenced) throws IOException {
        List<String> violations = DependencyRuleChecker.violations(root);

        assertEquals(1, violations.size(), violations::toString);
        assertTrue(violations.get(0).contains(location), violations::toString);
        assertTrue(violations.get(0).contains(referenced), violations::toString);
    }

    private void write(String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}

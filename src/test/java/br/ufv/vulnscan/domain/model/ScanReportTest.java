package br.ufv.vulnscan.domain.model;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes do ScanReport e do ModuleError (issue #58). */
class ScanReportTest {

    private static final Target TARGET = new Target("dvwa", URI.create("http://localhost:8080"));
    private static final Instant NOW = Instant.now();

    @Test
    void construtorSemErrosTemListaVazia() {
        assertTrue(new ScanReport(TARGET, NOW, NOW, List.of()).errors().isEmpty());
    }

    @Test
    void errosImutaveisECopiados() {
        List<ModuleError> errors = new ArrayList<>(List.of(new ModuleError("ApiAuthModule", "404")));
        ScanReport report = new ScanReport(TARGET, NOW, NOW, List.of(), errors);

        errors.clear();

        assertEquals(List.of(new ModuleError("ApiAuthModule", "404")), report.errors());
        assertThrows(UnsupportedOperationException.class, () -> report.errors().add(new ModuleError("x", "y")));
    }

    @Test
    void moduleErrorNaoAceitaNulos() {
        assertThrows(NullPointerException.class, () -> new ModuleError(null, "m"));
        assertThrows(NullPointerException.class, () -> new ModuleError("m", null));
    }
}

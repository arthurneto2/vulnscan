package br.ufv.vulnscan.app.benchmark;

import br.ufv.vulnscan.app.benchmark.GroundTruth.Case;
import br.ufv.vulnscan.app.benchmark.GroundTruth.TargetCases;
import br.ufv.vulnscan.domain.model.VulnType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Carrega o gabarito real em records. */
class GroundTruthLoaderTest {

    private static final Path FILE = Path.of("experiments", "ground_truth.yaml");

    @Test
    void carregaAlvosECasos() throws IOException {
        GroundTruth gt = GroundTruth.load(FILE);

        assertEquals(List.of("dvwa", "juice-shop"), gt.targets().stream().map(TargetCases::name).toList());
        TargetCases dvwa = gt.targets().get(0);
        assertEquals(URI.create("http://localhost:8080"), dvwa.baseUrl());
        assertFalse(dvwa.cases().isEmpty());
    }

    @Test
    void leCamposDoCasoIncluindoSecurityEAuthMode() throws IOException {
        List<Case> cases = GroundTruth.load(FILE).targets().stream()
                .flatMap(t -> t.cases().stream()).toList();

        Case sqliLow = find(cases, "dvwa-sqli-low");
        assertEquals(VulnType.SQL_INJECTION, sqliLow.type());
        assertEquals("low", sqliLow.security());
        assertTrue(sqliLow.vulnerable());

        Case idor = find(cases, "js-idor-basket");
        assertEquals("USER_TOKEN_OTHER_RESOURCE", idor.authMode());

        Case search = find(cases, "js-sqli-search");
        assertEquals(Optional.empty(), Optional.ofNullable(search.security()));
    }

    private static Case find(List<Case> cases, String id) {
        return cases.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }
}

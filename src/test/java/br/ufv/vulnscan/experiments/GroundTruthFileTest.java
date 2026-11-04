package br.ufv.vulnscan.experiments;

import br.ufv.vulnscan.domain.model.VulnType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida o gabarito do experimento (estrutura e balanço), sem rede: lê
 * {@code experiments/ground_truth.yaml} e confere as invariantes de {@code docs/ground-truth.md}.
 */
class GroundTruthFileTest {

    private static final Path FILE = Path.of("experiments", "ground_truth.yaml");

    private static List<JsonNode> cases;

    @BeforeAll
    static void load() throws IOException {
        assertTrue(Files.exists(FILE), "gabarito ausente: " + FILE.toAbsolutePath());
        JsonNode root = new YAMLMapper().readTree(Files.readString(FILE));
        cases = new ArrayList<>();
        for (JsonNode target : root.path("targets")) {
            assertFalse(target.path("base_url").asText("").isBlank(), "alvo sem base_url: " + target);
            target.path("cases").forEach(cases::add);
        }
        assertFalse(cases.isEmpty(), "gabarito sem casos");
    }

    @Test
    void todoCasoTemCamposValidosEIdUnico() {
        Set<String> ids = new HashSet<>();
        for (JsonNode c : cases) {
            String id = c.path("id").asText("");
            assertFalse(id.isBlank(), "caso sem id: " + c);
            assertTrue(ids.add(id), "id repetido: " + id);
            assertTrue(isVulnType(c.path("type").asText("")), "type inválido em " + id + ": " + c.path("type"));
            assertFalse(c.path("location").asText("").isBlank(), "location vazio em " + id);
            assertTrue(c.path("vulnerable").isBoolean(), "vulnerable não booleano em " + id);
        }
    }

    @Test
    void osTresTiposDeFalhaEstaoPresentes() {
        assertTrue(temTipo("SQL_INJECTION"), "falta SQL_INJECTION");
        assertTrue(cases.stream().anyMatch(c -> c.path("type").asText().startsWith("XSS_")), "falta XSS");
        assertTrue(cases.stream().anyMatch(c -> c.path("type").asText().startsWith("BROKEN_")), "falta auth/API");
    }

    @Test
    void cadaTipoPresenteTemPositivoENegativo() {
        for (VulnType type : VulnType.values()) {
            List<JsonNode> doTipo = cases.stream()
                    .filter(c -> c.path("type").asText().equals(type.name())).toList();
            if (doTipo.isEmpty()) {
                continue;
            }
            assertTrue(doTipo.stream().anyMatch(c -> c.path("vulnerable").asBoolean()),
                    "sem positivo para " + type);
            assertTrue(doTipo.stream().anyMatch(c -> !c.path("vulnerable").asBoolean()),
                    "sem negativo para " + type);
        }
    }

    private static boolean temTipo(String type) {
        return cases.stream().anyMatch(c -> c.path("type").asText().equals(type));
    }

    private static boolean isVulnType(String value) {
        for (VulnType t : VulnType.values()) {
            if (t.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}

package br.ufv.vulnscan.adapter.report;

import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.ScanReport;
import br.ufv.vulnscan.domain.model.Severity;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.Verdict;
import br.ufv.vulnscan.domain.model.VulnType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes do adaptador de relatório JSON, gravando em diretório temporário. */
class JsonReportWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Target TARGET = new Target("juice-shop", URI.create("http://localhost:3000"));
    private static final Instant STARTED = Instant.parse("2026-10-27T12:00:00Z");
    private static final Instant FINISHED = Instant.parse("2026-10-27T12:00:03.500Z");

    private final JsonReportWriter writer = new JsonReportWriter();

    @TempDir
    Path dir;

    @Test
    void formatEhJson() {
        assertEquals("json", writer.format());
    }

    @Test
    void semAchadosGravaJsonValido() throws IOException {
        JsonNode json = write(new ScanReport(TARGET, STARTED, FINISHED, List.of()));

        assertEquals("juice-shop", json.path("target").path("name").asText());
        assertEquals("http://localhost:3000", json.path("target").path("baseUrl").asText());
        assertEquals("2026-10-27T12:00:00Z", json.path("startedAt").asText());
        assertEquals(3500, json.path("durationMillis").asLong());
        assertEquals(0, json.path("findingCount").asInt());
        assertTrue(json.path("findings").isArray());
        assertEquals(0, json.path("findings").size());
    }

    @Test
    void comAchadoTrazTudo() throws IOException {
        JsonNode json = write(new ScanReport(TARGET, STARTED, FINISHED, List.of(finding("<pre>erro SQL</pre>"))));

        assertEquals(1, json.path("findingCount").asInt());
        JsonNode f = json.path("findings").get(0);
        assertEquals("SQL_INJECTION", f.path("type").asText());
        assertEquals("HIGH", f.path("severity").asText());
        assertEquals(true, f.path("verdict").path("vulnerable").asBoolean());
        assertEquals("heuristic", f.path("verdict").path("detector").asText());
        assertEquals(0.9, f.path("verdict").path("confidence").asDouble(), 1e-9);
        assertEquals("sqli-quote", f.path("payload").path("id").asText());
        assertEquals("GET", f.path("request").path("method").asText());
        assertEquals(200, f.path("response").path("status").asInt());
        assertTrue(f.path("response").path("body").asText().contains("erro SQL"));
    }

    @Test
    void corpoGrandeTruncado() throws IOException {
        String big = "A".repeat(5000);
        JsonNode json = write(new ScanReport(TARGET, STARTED, FINISHED, List.of(finding(big))));

        String body = json.path("findings").get(0).path("response").path("body").asText();
        assertTrue(body.contains("[truncado]"), "marca de truncamento");
        assertTrue(body.length() < big.length(), "corpo truncado");
    }

    @Test
    void criaDiretorioPaiEUtf8() throws IOException {
        Path out = dir.resolve("sub/report.json");
        writer.write(new ScanReport(TARGET, STARTED, FINISHED, List.of(finding("acentuação é ótimo"))), out);

        String content = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(content.contains("acentuação é ótimo"), content);
    }

    @Test
    void erroDeIoViraUnchecked() throws IOException {
        Path asDir = dir.resolve("saida");
        Files.createDirectories(asDir); // destino é diretório → escrita falha

        assertThrows(UncheckedIOException.class,
                () -> writer.write(new ScanReport(TARGET, STARTED, FINISHED, List.of()), asDir));
    }

    private JsonNode write(ScanReport report) throws IOException {
        Path out = dir.resolve("report.json");
        writer.write(report, out);
        return MAPPER.readTree(Files.readString(out, StandardCharsets.UTF_8));
    }

    private static Finding finding(String body) {
        Payload payload = new Payload("sqli-quote", VulnType.SQL_INJECTION, "1'", "aspas");
        HttpRequest request = new HttpRequest("GET",
                URI.create("http://localhost:3000/rest/products/search?q=1%27"), Map.of(), null);
        HttpResponse response = new HttpResponse(200, Map.of("content-type", "text/html"), body, 12);
        Evidence evidence = new Evidence(payload, request, response);
        Verdict verdict = new Verdict(true, 0.9, "assinatura de erro de SQL", "heuristic");
        return new Finding(VulnType.SQL_INJECTION, Severity.HIGH, TARGET, evidence, verdict);
    }
}

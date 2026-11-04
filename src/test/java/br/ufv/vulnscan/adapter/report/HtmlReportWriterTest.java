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

/** Testes do adaptador de relatório HTML, gravando em diretório temporário. */
class HtmlReportWriterTest {

    private static final Target TARGET = new Target("juice-shop", URI.create("http://localhost:3000"));
    private static final Instant STARTED = Instant.parse("2026-10-27T12:00:00Z");
    private static final Instant FINISHED = Instant.parse("2026-10-27T12:00:03.500Z");

    private final HtmlReportWriter writer = new HtmlReportWriter();

    @TempDir
    Path dir;

    @Test
    void formatEhHtml() {
        assertEquals("html", writer.format());
    }

    @Test
    void semAchadosTemCabecalhoEDoctype() throws IOException {
        String html = write(new ScanReport(TARGET, STARTED, FINISHED, List.of()));

        assertTrue(html.contains("<!DOCTYPE html>"), html);
        assertTrue(html.toLowerCase().contains("<meta charset=\"utf-8\">"), html);
        assertTrue(html.contains("juice-shop"), html);
        assertTrue(html.contains("3500"), "duração em ms");
        assertTrue(html.contains("Nenhum achado."), html);
    }

    @Test
    void comAchadoTrazVeredito() throws IOException {
        String html = write(new ScanReport(TARGET, STARTED, FINISHED, List.of(finding("<pre>erro</pre>", "1'"))));

        assertTrue(html.contains("SQL_INJECTION"), html);
        assertTrue(html.contains("HIGH"), html);
        assertTrue(html.contains("heuristic"), html);
        assertTrue(html.contains("0.90"), "confiança com 2 casas");
        assertTrue(html.contains("sqli-quote"), html);
        assertTrue(html.contains("200"), "status");
    }

    @Test
    void escapaHtmlDoPayloadENaoInjeta() throws IOException {
        String xss = "<script>alert('x')</script>";
        String html = write(new ScanReport(TARGET, STARTED, FINISHED, List.of(finding(xss, xss))));

        assertTrue(html.contains("&lt;script&gt;"), "payload escapado");
        assertFalse(html.contains("<script>alert('x')</script>"), "não pode haver script cru");
    }

    @Test
    void corpoGrandeTruncado() throws IOException {
        String big = "A".repeat(5000);
        String html = write(new ScanReport(TARGET, STARTED, FINISHED, List.of(finding(big, "1'"))));

        assertTrue(html.contains("truncado"), "marca de truncamento");
        assertFalse(html.contains("A".repeat(5000)), "corpo não vai inteiro");
    }

    @Test
    void criaDiretorioPaiEUtf8() throws IOException {
        Path out = dir.resolve("sub/report.html");
        writer.write(new ScanReport(TARGET, STARTED, FINISHED, List.of(finding("acentuação é ótimo", "1'"))), out);

        assertTrue(Files.readString(out, StandardCharsets.UTF_8).contains("acentuação é ótimo"));
    }

    @Test
    void erroDeIoViraUnchecked() throws IOException {
        Path asDir = dir.resolve("saida");
        Files.createDirectories(asDir);

        assertThrows(UncheckedIOException.class,
                () -> writer.write(new ScanReport(TARGET, STARTED, FINISHED, List.of()), asDir));
    }

    private String write(ScanReport report) throws IOException {
        Path out = dir.resolve("report.html");
        writer.write(report, out);
        return Files.readString(out, StandardCharsets.UTF_8);
    }

    private static Finding finding(String body, String payloadValue) {
        Payload payload = new Payload("sqli-quote", VulnType.SQL_INJECTION, payloadValue, "aspas");
        HttpRequest request = new HttpRequest("GET",
                URI.create("http://localhost:3000/rest/products/search?q=x"), Map.of(), null);
        HttpResponse response = new HttpResponse(200, Map.of("content-type", "text/html"), body, 12);
        Evidence evidence = new Evidence(payload, request, response);
        Verdict verdict = new Verdict(true, 0.9, "assinatura de erro de SQL", "heuristic");
        return new Finding(VulnType.SQL_INJECTION, Severity.HIGH, TARGET, evidence, verdict);
    }
}

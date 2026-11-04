package br.ufv.vulnscan.domain.detection;

import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.Verdict;
import br.ufv.vulnscan.domain.model.VulnType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes do detector heurístico com Evidence montada à mão, sem rede. */
class HeuristicDetectorTest {

    private static final String SVG = "<svg onload=alert('vsx1a2b3c4d')>";

    private final HeuristicDetector detector = new HeuristicDetector();

    @Test
    void nomeEHeuristic() {
        assertEquals("heuristic", detector.name());
    }

    @Test
    void corpoLimpoNaoEVulneravel() {
        Verdict verdict = detector.judge(sqli("<p>ID: 1<br>First name: admin</p>", 40));

        assertFalse(verdict.vulnerable());
        assertEquals(0.6, verdict.confidence());
        assertEquals("heuristic", verdict.detector());
        assertFalse(verdict.rationale().isBlank());
    }

    @Test
    void corpoNuloNaoLanca() {
        Verdict verdict = detector.judge(sqli(null, 40));

        assertFalse(verdict.vulnerable());
        assertEquals("heuristic", verdict.detector());
    }

    @Test
    void evidenciaNulaLancaNpe() {
        assertThrows(NullPointerException.class, () -> detector.judge(null));
    }

    @Test
    void erroMysqlEVulneravel() {
        String body = "<pre>You have an error in your SQL syntax; check the manual that corresponds to "
                + "your MariaDB server version for the right syntax to use near ''1''' at line 1</pre>";

        Verdict verdict = detector.judge(sqli(body, 40));

        assertTrue(verdict.vulnerable());
        assertEquals(0.9, verdict.confidence());
        assertTrue(verdict.rationale().contains("MySQL"), verdict.rationale());
        assertEquals("heuristic", verdict.detector());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "MySQL      | Warning: mysqli_fetch_array() expects parameter 1 to be mysqli_result",
            "SQLite     | {\"error\":{\"message\":\"SQLITE_ERROR: unrecognized token: \\\"'\\\"\"}}",
            "SQLite     | sqlite3.OperationalError: near \"x\": syntax error",
            "PostgreSQL | ERROR: syntax error at or near \"'\" at character 31",
            "PostgreSQL | org.postgresql.util.PSQLException: ERROR",
            "genérico   | SQLSTATE[42000]: Syntax error or access violation",
            "genérico   | [Microsoft][ODBC SQL Server Driver] Unclosed quotation mark",
    })
    void erroDeOutrosSgbdsEVulneravel(String sgbd, String body) {
        Verdict verdict = detector.judge(sqli(body, 40));

        assertTrue(verdict.vulnerable(), body);
        assertEquals(0.9, verdict.confidence());
        assertTrue(verdict.rationale().contains(sgbd), verdict.rationale());
    }

    @Test
    void assinaturaIgnoraMaiusculas() {
        assertTrue(detector.judge(sqli("YOU HAVE AN ERROR IN YOUR SQL SYNTAX", 40)).vulnerable());
    }

    @Test
    void payloadTemporalLentoEVulneravel() {
        Verdict verdict = detector.judge(evidence(VulnType.SQL_INJECTION, "sqli-time-sleep-5", "<p>ok</p>", 5000));

        assertTrue(verdict.vulnerable());
        assertEquals(0.8, verdict.confidence());
        assertTrue(verdict.rationale().contains("5000 ms"), verdict.rationale());
    }

    @Test
    void payloadTemporalAbaixoDoLimiarNaoEVulneravel() {
        Verdict verdict = detector.judge(evidence(VulnType.SQL_INJECTION, "sqli-time-sleep-5", "<p>ok</p>", 4999));

        assertFalse(verdict.vulnerable());
    }

    @Test
    void lentidaoSemPayloadTemporalNaoConta() {
        assertFalse(detector.judge(sqli("<p>ok</p>", 8000)).vulnerable());
    }

    @ParameterizedTest
    @EnumSource(value = VulnType.class, names = {"BROKEN_AUTH", "BROKEN_ACCESS_CONTROL"})
    void tipoSemRegrasNaoEVulneravel(VulnType type) {
        Verdict verdict = detector.judge(evidence(type, "outro", "You have an error in your SQL syntax", 40));

        assertFalse(verdict.vulnerable());
        assertEquals(0.0, verdict.confidence());
        assertTrue(verdict.rationale().contains(type.name()), verdict.rationale());
        assertEquals("heuristic", verdict.detector());
    }

    @ParameterizedTest
    @EnumSource(value = VulnType.class, names = {"XSS_REFLECTED", "XSS_STORED"})
    void xssCruEmHtmlEVulneravel(VulnType type) {
        Verdict verdict = detector.judge(xss(type, SVG, "<pre>Hello " + SVG + "</pre>", "text/html;charset=utf-8"));

        assertTrue(verdict.vulnerable(), verdict.rationale());
        assertEquals(0.9, verdict.confidence());
        assertTrue(verdict.rationale().contains("sem escape"), verdict.rationale());
        assertEquals("heuristic", verdict.detector());
    }

    @Test
    void xssCruSemContentTypeEVulneravel() {
        Verdict verdict = detector.judge(xss(VulnType.XSS_REFLECTED, SVG, "Hello " + SVG, null));

        assertTrue(verdict.vulnerable(), verdict.rationale());
    }

    @Test
    void xssScriptRemovidoNaoEVulneravel() {
        String script = "<script>alert('vsx1a2b3c4d')</script>";
        Verdict verdict = detector.judge(xss(VulnType.XSS_REFLECTED, script,
                "<pre>Hello alert('vsx1a2b3c4d')</script></pre>", "text/html;charset=utf-8"));

        assertFalse(verdict.vulnerable());
        assertEquals(0.6, verdict.confidence());
        assertTrue(verdict.rationale().contains("não refletido"), verdict.rationale());
    }

    @Test
    void xssCorpoNuloNaoLanca() {
        assertFalse(detector.judge(xss(VulnType.XSS_REFLECTED, SVG, null, "text/html")).vulnerable());
    }

    @Test
    void xssEmMaiusculasNaoContaComoCru() {
        Verdict verdict = detector.judge(xss(VulnType.XSS_REFLECTED, SVG,
                "Hello <SVG ONLOAD=ALERT('VSX1A2B3C4D')>", "text/html"));

        assertFalse(verdict.vulnerable());
    }

    @Test
    void xssCruEmJsonNaoEVulneravel() {
        Verdict verdict = detector.judge(xss(VulnType.XSS_REFLECTED, SVG,
                "{\"orderId\":\"" + SVG + "\"}", "application/json; charset=utf-8"));

        assertFalse(verdict.vulnerable());
        assertEquals(0.5, verdict.confidence());
        assertTrue(verdict.rationale().contains("application/json"), verdict.rationale());
    }

    @Test
    void xssEscapadoNaoEVulneravel() {
        String juiceShop = "<title>Error: Unrecognized target URL for redirect: "
                + "&lt;svg onload=alert(&#39;vsx1a2b3c4d&#39;)&gt;</title>";

        Verdict verdict = detector.judge(xss(VulnType.XSS_REFLECTED, SVG, juiceShop, "text/html; charset=utf-8"));

        assertFalse(verdict.vulnerable());
        assertEquals(0.8, verdict.confidence());
        assertTrue(verdict.rationale().contains("escape"), verdict.rationale());
    }

    @ParameterizedTest
    @EnumSource(value = VulnType.class, names = {"XSS_REFLECTED", "XSS_STORED"})
    void dialogoComMarcadorEVulneravel(VulnType type) {
        String normalizado = "<img src=\"x\" onerror=\"alert('vsx1a2b3c4d')\">";
        Evidence evidence = xssWithHeaders(type, "<img src=x onerror=alert('vsx1a2b3c4d')>", normalizado,
                Map.of("content-type", "text/html", HttpResponse.DIALOGS_HEADER, "outro, vsx1a2b3c4d"));

        Verdict verdict = detector.judge(evidence);

        assertTrue(verdict.vulnerable(), verdict.rationale());
        assertEquals(0.95, verdict.confidence());
        assertTrue(verdict.rationale().contains("navegador executou"), verdict.rationale());
    }

    @Test
    void dialogoSemMarcadorSegueRegrasDoCorpo() {
        Evidence evidence = xssWithHeaders(VulnType.XSS_STORED, SVG, "<p>nada</p>",
                Map.of("content-type", "text/html", HttpResponse.DIALOGS_HEADER, "Tem certeza?"));

        Verdict verdict = detector.judge(evidence);

        assertFalse(verdict.vulnerable());
        assertEquals(0.6, verdict.confidence());
    }

    private static Evidence xss(VulnType type, String value, String body, String contentType) {
        return xssWithHeaders(type, value, body, contentType == null ? Map.of() : Map.of("content-type", contentType));
    }

    private static Evidence xssWithHeaders(VulnType type, String value, String body, Map<String, String> headers) {
        Payload payload = new Payload("xss-svg-onload", type, value, "payload de teste");
        HttpRequest request = new HttpRequest("GET",
                URI.create("http://localhost:8080/vulnerabilities/xss_r/?name=x"), Map.of(), null);
        return new Evidence(payload, request, new HttpResponse(200, headers, body, 5));
    }

    private static Evidence sqli(String body, long elapsedMillis) {
        return evidence(VulnType.SQL_INJECTION, "sqli-quote", body, elapsedMillis);
    }

    private static Evidence evidence(VulnType type, String payloadId, String body, long elapsedMillis) {
        Payload payload = new Payload(payloadId, type, "valor", "payload de teste");
        HttpRequest request = new HttpRequest("GET",
                URI.create("http://localhost:8080/vulnerabilities/sqli/?id=1"), Map.of(), null);
        HttpResponse response = new HttpResponse(200, Map.of(), body, elapsedMillis);
        return new Evidence(payload, request, response);
    }
}

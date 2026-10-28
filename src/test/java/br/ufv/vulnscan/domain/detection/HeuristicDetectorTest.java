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
    @EnumSource(value = VulnType.class, mode = EnumSource.Mode.EXCLUDE, names = "SQL_INJECTION")
    void tipoSemRegrasNaoEVulneravel(VulnType type) {
        Verdict verdict = detector.judge(evidence(type, "outro", "You have an error in your SQL syntax", 40));

        assertFalse(verdict.vulnerable());
        assertEquals(0.0, verdict.confidence());
        assertTrue(verdict.rationale().contains(type.name()), verdict.rationale());
        assertEquals("heuristic", verdict.detector());
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

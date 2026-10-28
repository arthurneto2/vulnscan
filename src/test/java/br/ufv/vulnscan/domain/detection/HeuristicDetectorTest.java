package br.ufv.vulnscan.domain.detection;

import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.Verdict;
import br.ufv.vulnscan.domain.model.VulnType;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

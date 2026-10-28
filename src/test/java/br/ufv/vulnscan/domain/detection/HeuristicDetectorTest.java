package br.ufv.vulnscan.domain.detection;

import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.VulnType;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Testes do detector heurístico com Evidence montada à mão, sem rede. */
class HeuristicDetectorTest {

    private final HeuristicDetector detector = new HeuristicDetector();

    @Test
    void nomeEHeuristic() {
        assertEquals("heuristic", detector.name());
    }

    private static Evidence evidence(VulnType type, String payloadId, String body, long elapsedMillis) {
        Payload payload = new Payload(payloadId, type, "valor", "payload de teste");
        HttpRequest request = new HttpRequest("GET",
                URI.create("http://localhost:8080/vulnerabilities/sqli/?id=1"), Map.of(), null);
        HttpResponse response = new HttpResponse(200, Map.of(), body, elapsedMillis);
        return new Evidence(payload, request, response);
    }
}

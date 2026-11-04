package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.detection.Detector;
import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.Verdict;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.port.HttpClient;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Testes do módulo de SQLi com HttpClient e Detector fakes, sem rede. */
class SqlInjectionModuleTest {

    private static final Target TARGET = new Target("dvwa", URI.create("http://localhost:8080"));
    private static final Payload QUOTE = payload("sqli-quote-single", "1'");
    private static final Payload BOOL = payload("sqli-bool-or-true", "' OR '1'='1");
    private static final InjectionPoint SEARCH = new InjectionPoint("/rest/products/search", "q");
    private static final InjectionPoint DVWA = new InjectionPoint("/vulnerabilities/sqli/?Submit=Submit", "id");

    private final RecordingHttpClient http = new RecordingHttpClient();
    private final FakeDetector detector = new FakeDetector(evidence -> false);

    @Test
    void typeESqlInjection() {
        assertEquals(VulnType.SQL_INJECTION, module(List.of(QUOTE), List.of(SEARCH)).type());
    }

    @Test
    void enviaGetComPayloadCodificadoNoParametro() {
        module(List.of(BOOL), List.of(SEARCH)).scan(TARGET);

        HttpRequest request = onlyRequest();
        assertEquals("GET", request.method());
        assertEquals(URI.create("http://localhost:8080/rest/products/search?q=%27+OR+%271%27%3D%271"),
                request.url());
        assertNull(request.body());
    }

    @Test
    void respeitaQueryFixaDoPath() {
        module(List.of(QUOTE), List.of(DVWA)).scan(TARGET);

        assertEquals(URI.create("http://localhost:8080/vulnerabilities/sqli/?Submit=Submit&id=1%27"),
                onlyRequest().url());
    }

    @Test
    void enviaHeadersDoAlvo() {
        Target logged = new Target("dvwa", TARGET.baseUrl(), Map.of("Cookie", "PHPSESSID=abc; security=low"));

        module(List.of(QUOTE), List.of(DVWA)).scan(logged);

        assertEquals(Map.of("Cookie", "PHPSESSID=abc; security=low"), onlyRequest().headers());
    }

    private HttpRequest onlyRequest() {
        assertEquals(1, http.requests.size(), "requisições");
        return http.requests.get(0);
    }

    private SqlInjectionModule module(List<Payload> payloads, List<InjectionPoint> points) {
        return new SqlInjectionModule(http, detector, payloads, points);
    }

    private static Payload payload(String id, String value) {
        return new Payload(id, VulnType.SQL_INJECTION, value, "teste");
    }

    private static HttpResponse response(String body) {
        return new HttpResponse(200, Map.of(), body, 3);
    }

    /** Grava as requisições e devolve uma resposta fixa (ou lança, se configurado). */
    private static final class RecordingHttpClient implements HttpClient {
        final List<HttpRequest> requests = new ArrayList<>();
        HttpResponse response = response("ok");
        RuntimeException error;

        @Override
        public HttpResponse send(HttpRequest request) {
            requests.add(request);
            if (error != null) {
                throw error;
            }
            return response;
        }
    }

    /** Detector fake: vulnerável quando o predicado casa; grava as evidências recebidas. */
    private static final class FakeDetector implements Detector {
        final List<Evidence> judged = new ArrayList<>();
        Predicate<Evidence> vulnerable;

        FakeDetector(Predicate<Evidence> vulnerable) {
            this.vulnerable = vulnerable;
        }

        @Override
        public Verdict judge(Evidence evidence) {
            judged.add(evidence);
            boolean v = vulnerable.test(evidence);
            return new Verdict(v, v ? 0.9 : 0.6, v ? "positivo fake" : "negativo fake", "fake");
        }

        @Override
        public String name() {
            return "fake";
        }
    }
}

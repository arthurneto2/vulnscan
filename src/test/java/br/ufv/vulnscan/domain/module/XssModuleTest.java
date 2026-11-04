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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Testes do módulo de XSS refletido com HttpClient, Detector e marcadores fakes, sem rede. */
class XssModuleTest {

    private static final Target TARGET = new Target("dvwa", URI.create("http://localhost:8080"));
    private static final Payload SCRIPT = payload("xss-script", "<script>alert('{marker}')</script>");
    private static final Payload SVG = payload("xss-svg-onload", "<svg onload=alert('{marker}')>");
    private static final InjectionPoint DVWA = new InjectionPoint("/vulnerabilities/xss_r/", "name");
    private static final InjectionPoint REDIRECT = new InjectionPoint("/redirect", "to");

    private final RecordingHttpClient http = new RecordingHttpClient();
    private final FakeDetector detector = new FakeDetector();
    private final SequentialMarkers markers = new SequentialMarkers();

    @Test
    void typeEXssRefletido() {
        assertEquals(VulnType.XSS_REFLECTED, module(List.of(SCRIPT), List.of(DVWA)).type());
    }

    @Test
    void enviaGetComMarcadorSubstituidoECodificado() {
        module(List.of(SVG), List.of(DVWA)).scan(TARGET);

        HttpRequest request = onlyRequest();
        assertEquals("GET", request.method());
        assertEquals(URI.create("http://localhost:8080/vulnerabilities/xss_r/?name="
                + "%3Csvg+onload%3Dalert%28%27m1%27%29%3E"), request.url());
        assertNull(request.body());
    }

    @Test
    void enviaHeadersDoAlvo() {
        Target logged = new Target("dvwa", TARGET.baseUrl(), Map.of("Cookie", "PHPSESSID=abc; security=low"));

        module(List.of(SCRIPT), List.of(DVWA)).scan(logged);

        assertEquals(Map.of("Cookie", "PHPSESSID=abc; security=low"), onlyRequest().headers());
    }

    @Test
    void cadaRequisicaoUsaMarcadorNovo() {
        module(List.of(SCRIPT, SVG), List.of(DVWA, REDIRECT)).scan(TARGET);

        assertEquals(List.of("m1", "m2", "m3", "m4"), http.requests.stream()
                .map(r -> r.url().getQuery().replaceAll(".*alert\\('(m\\d+)'\\).*", "$1"))
                .toList());
    }

    @Test
    void evidenciaTrazPayloadConcreto() {
        HttpResponse resposta = response("<pre>Hello x</pre>");
        http.response = resposta;

        module(List.of(SCRIPT), List.of(DVWA)).scan(TARGET);

        Payload concrete = new Payload("xss-script", VulnType.XSS_REFLECTED,
                "<script>alert('m1')</script>", "teste");
        assertEquals(List.of(new Evidence(concrete, onlyRequest(), resposta)), detector.judged);
    }

    private HttpRequest onlyRequest() {
        assertEquals(1, http.requests.size(), "requisições");
        return http.requests.get(0);
    }

    private XssModule module(List<Payload> payloads, List<InjectionPoint> points) {
        return new XssModule(http, detector, payloads, points, markers);
    }

    private static Payload payload(String id, String value) {
        return new Payload(id, VulnType.XSS_REFLECTED, value, "teste");
    }

    private static HttpResponse response(String body) {
        return new HttpResponse(200, Map.of(), body, 3);
    }

    /** Marcadores previsíveis: m1, m2, m3, ... */
    private static final class SequentialMarkers implements Supplier<String> {
        private int next = 1;

        @Override
        public String get() {
            return "m" + next++;
        }
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
        Predicate<Evidence> vulnerable = evidence -> false;

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

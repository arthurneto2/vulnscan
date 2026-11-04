package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.Severity;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.domain.module.DomXssModuleTest.FakeBrowser;
import br.ufv.vulnscan.domain.module.DomXssModuleTest.FakeDetector;
import br.ufv.vulnscan.domain.module.DomXssModuleTest.SequentialMarkers;
import br.ufv.vulnscan.port.HttpClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes do módulo de XSS armazenado com HttpClient, navegador, detector e marcadores fakes. */
class StoredXssModuleTest {

    private static final Target TARGET = new Target("dvwa", URI.create("http://localhost:8080"),
            Map.of("Cookie", "PHPSESSID=abc; security=low"));
    private static final Payload IMG = payload("xss-img-onerror", "<img src=x onerror=alert('{marker}')>");
    private static final Payload SVG = payload("xss-svg-onload", "<svg onload=alert('{marker}')>");
    private static final StoredXssPoint GUESTBOOK = new StoredXssPoint("/vulnerabilities/xss_s/",
            fields("txtName", "vulnscan", "btnSign", "Sign Guestbook"), "mtxMessage", "/vulnerabilities/xss_s/");
    private static final StoredXssPoint OTHER = new StoredXssPoint("/salvar", Map.of(), "texto", "/ver");

    private final RecordingHttpClient http = new RecordingHttpClient();
    private final FakeBrowser browser = new FakeBrowser();
    private final FakeDetector detector = new FakeDetector();
    private final SequentialMarkers markers = new SequentialMarkers();

    @Test
    void typeEXssArmazenado() {
        assertEquals(VulnType.XSS_STORED, module(List.of(IMG), List.of(GUESTBOOK)).type());
    }

    @Test
    void postaFormularioComPayloadEHeaders() {
        module(List.of(IMG), List.of(GUESTBOOK)).scan(TARGET);

        assertEquals(1, http.requests.size(), "requisições");
        HttpRequest post = http.requests.get(0);
        assertEquals("POST", post.method());
        assertEquals(URI.create("http://localhost:8080/vulnerabilities/xss_s/"), post.url());
        assertEquals(Map.of("Cookie", "PHPSESSID=abc; security=low",
                "Content-Type", "application/x-www-form-urlencoded"), post.headers());
        assertEquals("txtName=vulnscan&btnSign=Sign+Guestbook&mtxMessage="
                + "%3Cimg+src%3Dx+onerror%3Dalert%28%27m1%27%29%3E", post.body());
    }

    @Test
    void abreAPaginaDeVisualizacaoDepoisDoPost() {
        module(List.of(IMG), List.of(OTHER)).scan(TARGET);

        assertEquals(List.of(URI.create("http://localhost:8080/ver")), browser.opened);
        assertEquals(List.of(TARGET.headers()), browser.headers);
    }

    @Test
    void evidenciaTemOPostEAPaginaRenderizada() {
        browser.page = new RenderedPage("<html>guestbook</html>", List.of("m1"), 1700);

        module(List.of(IMG), List.of(GUESTBOOK)).scan(TARGET);

        assertEquals(1, detector.judged.size(), "evidências julgadas");
        Evidence evidence = detector.judged.get(0);
        assertEquals(new Payload("xss-img-onerror", VulnType.XSS_STORED,
                "<img src=x onerror=alert('m1')>", "teste"), evidence.payload());
        assertSame(http.requests.get(0), evidence.request());
        assertEquals(browser.page.toResponse(), evidence.response());
    }

    @Test
    void positivoViraFindingHighEParaNoPonto() {
        detector.vulnerable = e -> e.payload().id().equals("xss-img-onerror");

        List<Finding> findings = module(List.of(IMG, SVG), List.of(GUESTBOOK, OTHER)).scan(TARGET);

        assertEquals(2, http.requests.size(), "um POST por ponto");
        assertEquals(2, findings.size());
        Finding finding = findings.get(0);
        assertEquals(VulnType.XSS_STORED, finding.type());
        assertEquals(Severity.HIGH, finding.severity());
        assertSame(TARGET, finding.target());
        assertSame(detector.judged.get(0), finding.evidence());
    }

    @Test
    void semPositivoNaoReportaETentaTudo() {
        assertTrue(module(List.of(IMG, SVG), List.of(GUESTBOOK, OTHER)).scan(TARGET).isEmpty());
        assertEquals(4, http.requests.size());
        assertEquals(4, browser.opened.size());
    }

    @Test
    void erroDoHttpPropagaSemAbrirNavegador() {
        UncheckedIOException error = new UncheckedIOException(new IOException("alvo fora do ar"));
        http.error = error;

        assertSame(error, assertThrows(UncheckedIOException.class,
                () -> module(List.of(IMG), List.of(GUESTBOOK)).scan(TARGET)));
        assertTrue(browser.opened.isEmpty());
    }

    @Test
    void erroDoNavegadorPropaga() {
        IllegalStateException error = new IllegalStateException("chrome não subiu");
        browser.error = error;

        assertSame(error, assertThrows(IllegalStateException.class,
                () -> module(List.of(IMG), List.of(GUESTBOOK)).scan(TARGET)));
    }

    @Test
    void construtorValidaArgumentos() {
        List<Payload> payloads = List.of(IMG);
        List<StoredXssPoint> points = List.of(GUESTBOOK);
        assertThrows(NullPointerException.class,
                () -> new StoredXssModule(null, browser, detector, payloads, points, markers));
        assertThrows(NullPointerException.class,
                () -> new StoredXssModule(http, null, detector, payloads, points, markers));
        assertThrows(NullPointerException.class,
                () -> new StoredXssModule(http, browser, null, payloads, points, markers));
        assertThrows(NullPointerException.class,
                () -> new StoredXssModule(http, browser, detector, null, points, markers));
        assertThrows(NullPointerException.class,
                () -> new StoredXssModule(http, browser, detector, payloads, null, markers));
        assertThrows(NullPointerException.class,
                () -> new StoredXssModule(http, browser, detector, payloads, points, null));
        assertThrows(IllegalArgumentException.class,
                () -> new StoredXssModule(http, browser, detector, payloads, List.of(), markers));
        Payload refletido = new Payload("xss-ref", VulnType.XSS_REFLECTED, "{marker}", "outro tipo");
        assertThrows(IllegalArgumentException.class,
                () -> new StoredXssModule(http, browser, detector, List.of(refletido), points, markers));
    }

    @Test
    void campoFixosMantemOrdemESaoImutaveis() {
        assertEquals(List.of("txtName", "btnSign"), List.copyOf(GUESTBOOK.fixedFields().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> GUESTBOOK.fixedFields().put("x", "y"));
    }

    private StoredXssModule module(List<Payload> payloads, List<StoredXssPoint> points) {
        return new StoredXssModule(http, browser, detector, payloads, points, markers);
    }

    private static Payload payload(String id, String value) {
        return new Payload(id, VulnType.XSS_STORED, value, "teste");
    }

    private static Map<String, String> fields(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    /** Grava as requisições e devolve uma resposta fixa (ou lança, se configurado). */
    private static final class RecordingHttpClient implements HttpClient {
        final List<HttpRequest> requests = new ArrayList<>();
        RuntimeException error;

        @Override
        public HttpResponse send(HttpRequest request) {
            requests.add(request);
            if (error != null) {
                throw error;
            }
            return new HttpResponse(200, Map.of(), "ok", 3);
        }
    }
}

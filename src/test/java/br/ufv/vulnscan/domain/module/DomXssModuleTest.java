package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.detection.Detector;
import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.Severity;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.Verdict;
import br.ufv.vulnscan.domain.model.VulnType;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes do módulo de XSS de DOM com navegador, detector e marcadores fakes. */
class DomXssModuleTest {

    private static final Target TARGET = new Target("juice", URI.create("http://localhost:3000"));
    private static final Payload IMG = payload("xss-img-onerror", "<img src=x onerror=alert('{marker}')>");
    private static final Payload SVG = payload("xss-svg-onload", "<svg onload=alert('{marker}')>");
    private static final InjectionPoint SEARCH = new InjectionPoint("/#/search", "q");
    private static final InjectionPoint OTHER = new InjectionPoint("/#/outra", "x");

    private final FakeBrowser browser = new FakeBrowser();
    private final FakeDetector detector = new FakeDetector();
    private final SequentialMarkers markers = new SequentialMarkers();

    @Test
    void typeEXssRefletido() {
        assertEquals(VulnType.XSS_REFLECTED, module(List.of(IMG), List.of(SEARCH)).type());
    }

    @Test
    void abreUrlComFragmentoEMarcador() {
        module(List.of(IMG), List.of(SEARCH)).scan(TARGET);

        assertEquals(List.of(URI.create("http://localhost:3000/#/search?q="
                + "%3Cimg+src%3Dx+onerror%3Dalert%28%27m1%27%29%3E")), browser.opened);
    }

    @Test
    void passaHeadersDoAlvoAoNavegador() {
        Target logged = new Target("juice", TARGET.baseUrl(), Map.of("Cookie", "token=abc"));

        module(List.of(IMG), List.of(SEARCH)).scan(logged);

        assertEquals(List.of(Map.of("Cookie", "token=abc")), browser.headers);
    }

    @Test
    void respostaSinteticaComDomEDialogos() {
        browser.page = new RenderedPage("<html>dom</html>", List.of("m1", "outro"), 1500);

        module(List.of(IMG), List.of(SEARCH)).scan(TARGET);

        Evidence evidence = onlyJudged();
        assertEquals(new Payload("xss-img-onerror", VulnType.XSS_REFLECTED,
                "<img src=x onerror=alert('m1')>", "teste"), evidence.payload());
        HttpRequest request = evidence.request();
        assertEquals("GET", request.method());
        assertEquals(browser.opened.get(0), request.url());
        assertNull(request.body());
        assertEquals(new HttpResponse(200, Map.of("content-type", "text/html",
                HttpResponse.DIALOGS_HEADER, "m1, outro"), "<html>dom</html>", 1500), evidence.response());
    }

    @Test
    void semDialogoNaoPoeHeader() {
        module(List.of(IMG), List.of(SEARCH)).scan(TARGET);

        assertFalse(onlyJudged().response().headers().containsKey(HttpResponse.DIALOGS_HEADER));
    }

    @Test
    void cadaAberturaUsaMarcadorNovo() {
        module(List.of(IMG, SVG), List.of(SEARCH, OTHER)).scan(TARGET);

        assertEquals(List.of("m1", "m2", "m3", "m4"), detector.judged.stream()
                .map(e -> e.payload().value().replaceAll(".*alert\\('(m\\d+)'\\).*", "$1")).toList());
    }

    @Test
    void positivoViraFindingMediumEParaNoPonto() {
        detector.vulnerable = e -> e.payload().id().equals("xss-img-onerror");

        List<Finding> findings = module(List.of(IMG, SVG), List.of(SEARCH, OTHER)).scan(TARGET);

        assertEquals(2, browser.opened.size(), "um por ponto");
        assertEquals(2, findings.size());
        Finding finding = findings.get(0);
        assertEquals(VulnType.XSS_REFLECTED, finding.type());
        assertEquals(Severity.MEDIUM, finding.severity());
        assertSame(TARGET, finding.target());
        assertSame(detector.judged.get(0), finding.evidence());
    }

    @Test
    void semPositivoNaoReportaETentaTudo() {
        assertTrue(module(List.of(IMG, SVG), List.of(SEARCH, OTHER)).scan(TARGET).isEmpty());
        assertEquals(4, browser.opened.size());
    }

    @Test
    void erroDoNavegadorPropaga() {
        IllegalStateException error = new IllegalStateException("chrome não subiu");
        browser.error = error;

        assertSame(error, assertThrows(IllegalStateException.class,
                () -> module(List.of(IMG), List.of(SEARCH)).scan(TARGET)));
    }

    @Test
    void construtorValidaArgumentos() {
        List<Payload> payloads = List.of(IMG);
        List<InjectionPoint> points = List.of(SEARCH);
        assertThrows(NullPointerException.class, () -> new DomXssModule(null, detector, payloads, points, markers));
        assertThrows(NullPointerException.class, () -> new DomXssModule(browser, null, payloads, points, markers));
        assertThrows(NullPointerException.class, () -> new DomXssModule(browser, detector, null, points, markers));
        assertThrows(NullPointerException.class, () -> new DomXssModule(browser, detector, payloads, null, markers));
        assertThrows(NullPointerException.class, () -> new DomXssModule(browser, detector, payloads, points, null));
        assertThrows(IllegalArgumentException.class,
                () -> new DomXssModule(browser, detector, List.of(), points, markers));
        assertThrows(IllegalArgumentException.class,
                () -> new DomXssModule(browser, detector, payloads, List.of(), markers));
        Payload stored = new Payload("xss-stored", VulnType.XSS_STORED, "{marker}", "outro tipo");
        assertThrows(IllegalArgumentException.class,
                () -> new DomXssModule(browser, detector, List.of(stored), points, markers));
        Payload semMarcador = payload("xss-fixo", "<img src=x onerror=alert(1)>");
        assertThrows(IllegalArgumentException.class,
                () -> new DomXssModule(browser, detector, List.of(semMarcador), points, markers));
    }

    private Evidence onlyJudged() {
        assertEquals(1, detector.judged.size(), "evidências julgadas");
        return detector.judged.get(0);
    }

    private DomXssModule module(List<Payload> payloads, List<InjectionPoint> points) {
        return new DomXssModule(browser, detector, payloads, points, markers);
    }

    private static Payload payload(String id, String value) {
        return new Payload(id, VulnType.XSS_REFLECTED, value, "teste");
    }

    /** Navegador fake: grava URLs e headers e devolve uma página fixa (ou lança). */
    static final class FakeBrowser implements XssBrowser {
        final List<URI> opened = new ArrayList<>();
        final List<Map<String, String>> headers = new ArrayList<>();
        RenderedPage page = new RenderedPage("<html></html>", List.of(), 1000);
        RuntimeException error;

        @Override
        public RenderedPage open(URI url, Map<String, String> requestHeaders) {
            opened.add(url);
            headers.add(requestHeaders);
            if (error != null) {
                throw error;
            }
            return page;
        }
    }

    /** Marcadores previsíveis: m1, m2, m3, ... */
    static final class SequentialMarkers implements Supplier<String> {
        private int next = 1;

        @Override
        public String get() {
            return "m" + next++;
        }
    }

    /** Detector fake: vulnerável quando o predicado casa; grava as evidências recebidas. */
    static final class FakeDetector implements Detector {
        final List<Evidence> judged = new ArrayList<>();
        Predicate<Evidence> vulnerable = evidence -> false;

        @Override
        public Verdict judge(Evidence evidence) {
            judged.add(evidence);
            boolean v = vulnerable.test(evidence);
            return new Verdict(v, v ? 0.95 : 0.6, v ? "positivo fake" : "negativo fake", "fake");
        }

        @Override
        public String name() {
            return "fake";
        }
    }
}

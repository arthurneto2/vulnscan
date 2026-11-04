package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.detection.Detector;
import br.ufv.vulnscan.domain.detection.HeuristicDetector;
import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.Severity;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.Verdict;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.port.HttpClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void evidenciaPassadaAoDetector() {
        HttpResponse resposta = response("corpo da resposta");
        http.response = resposta;

        module(List.of(QUOTE), List.of(DVWA)).scan(TARGET);

        assertEquals(List.of(new Evidence(QUOTE, onlyRequest(), resposta)), detector.judged);
    }

    @Test
    void semPositivoNaoReportaETentaTudo() {
        List<Finding> findings = module(List.of(QUOTE, BOOL), List.of(SEARCH, DVWA)).scan(TARGET);

        assertTrue(findings.isEmpty(), "findings: " + findings);
        assertEquals(4, http.requests.size(), "2 pontos x 2 payloads");
        assertEquals(List.of(QUOTE, BOOL, QUOTE, BOOL),
                detector.judged.stream().map(Evidence::payload).toList());
    }

    @Test
    void positivoViraFindingHighEParaNoPonto() {
        detector.vulnerable = evidence -> evidence.payload().equals(QUOTE);
        Payload time = payload("sqli-time-mysql-sleep", "1' AND SLEEP(5)-- -");

        List<Finding> findings = module(List.of(QUOTE, BOOL, time), List.of(DVWA)).scan(TARGET);

        assertEquals(1, http.requests.size(), "não envia os payloads restantes do ponto");
        assertEquals(1, findings.size());
        Finding finding = findings.get(0);
        assertEquals(VulnType.SQL_INJECTION, finding.type());
        assertEquals(Severity.HIGH, finding.severity());
        assertSame(TARGET, finding.target());
        assertSame(detector.judged.get(0), finding.evidence());
        assertEquals(new Verdict(true, 0.9, "positivo fake", "fake"), finding.verdict());
    }

    @Test
    void positivoEmUmPontoNaoImpedeOutro() {
        detector.vulnerable = evidence -> evidence.payload().equals(BOOL);

        List<Finding> findings = module(List.of(QUOTE, BOOL), List.of(SEARCH, DVWA)).scan(TARGET);

        assertEquals(4, http.requests.size());
        assertEquals(List.of(
                        URI.create("http://localhost:8080/rest/products/search?q=%27+OR+%271%27%3D%271"),
                        URI.create("http://localhost:8080/vulnerabilities/sqli/?Submit=Submit&id=%27+OR+%271%27%3D%271")),
                findings.stream().map(f -> f.evidence().request().url()).toList());
    }

    @Test
    void comHeuristicDetectorRealDetectaErroMysql() {
        http.response = response("<pre>You have an error in your SQL syntax; check the manual that "
                + "corresponds to your MariaDB server version</pre>");

        List<Finding> findings = new SqlInjectionModule(http, new HeuristicDetector(), List.of(QUOTE, BOOL),
                List.of(DVWA)).scan(TARGET);

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).verdict().vulnerable());
        assertEquals("heuristic", findings.get(0).verdict().detector());
    }

    @Test
    void erroDoHttpClientPropaga() {
        UncheckedIOException error = new UncheckedIOException(new IOException("alvo fora do ar"));
        http.error = error;

        UncheckedIOException thrown = assertThrows(UncheckedIOException.class,
                () -> module(List.of(QUOTE), List.of(DVWA)).scan(TARGET));
        assertSame(error, thrown);
    }

    @Test
    void construtorRejeitaNulos() {
        List<Payload> payloads = List.of(QUOTE);
        List<InjectionPoint> points = List.of(DVWA);
        assertThrows(NullPointerException.class, () -> new SqlInjectionModule(null, detector, payloads, points));
        assertThrows(NullPointerException.class, () -> new SqlInjectionModule(http, null, payloads, points));
        assertThrows(NullPointerException.class, () -> new SqlInjectionModule(http, detector, null, points));
        assertThrows(NullPointerException.class, () -> new SqlInjectionModule(http, detector, payloads, null));
    }

    @Test
    void construtorRejeitaListasVazias() {
        assertThrows(IllegalArgumentException.class,
                () -> new SqlInjectionModule(http, detector, List.of(), List.of(DVWA)));
        assertThrows(IllegalArgumentException.class,
                () -> new SqlInjectionModule(http, detector, List.of(QUOTE), List.of()));
    }

    @Test
    void construtorRejeitaPayloadDeOutroTipo() {
        Payload xss = new Payload("xss-script", VulnType.XSS_REFLECTED, "<script>", "xss");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new SqlInjectionModule(http, detector, List.of(QUOTE, xss), List.of(DVWA)));
        assertTrue(e.getMessage().contains("xss-script"), e.getMessage());
    }

    @Test
    void listasSaoCopiadas() {
        List<Payload> payloads = new ArrayList<>(List.of(QUOTE));
        SqlInjectionModule module = new SqlInjectionModule(http, detector, payloads, List.of(DVWA));
        payloads.add(BOOL);

        module.scan(TARGET);

        assertEquals(1, http.requests.size());
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

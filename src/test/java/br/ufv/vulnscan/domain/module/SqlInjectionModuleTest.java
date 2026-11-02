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
import br.ufv.vulnscan.port.HttpClient;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do SqlInjectionModule com fakes à mão de HttpClient e Detector. Sem rede, sem
 * Docker: o login do DVWA é roteirizado no fake.
 */
class SqlInjectionModuleTest {

    private static final Target DVWA = new Target("localhost:8080", URI.create("http://localhost:8080"));

    private static Payload payload(String id, String value) {
        return new Payload(id, VulnType.SQL_INJECTION, value, id);
    }

    private static Payload quote() {
        return payload("sqli-quote-1", "'");
    }

    /** Fake de login: responde login.php com user_token e Set-Cookie; 200 vazio no resto. */
    private static HttpResponse dvwaLoginResponder(HttpRequest request) {
        String url = request.url().toString();
        if (url.contains("/login.php") && "GET".equals(request.method())) {
            return new HttpResponse(200,
                    Map.of("set-cookie", "PHPSESSID=SID123; path=/"),
                    "<input type='hidden' name='user_token' value='TOKEN42' />", 1);
        }
        if (url.contains("/login.php") && "POST".equals(request.method())) {
            return new HttpResponse(302, Map.of("location", "index.php"), "", 1);
        }
        return new HttpResponse(200, Map.of(), "", 1);
    }

    @Test
    void typeEhSqlInjection() {
        SqlInjectionModule module = new SqlInjectionModule(
                req -> new HttpResponse(200, Map.of(), "", 1), negativeDetector(), List.of());
        assertEquals(VulnType.SQL_INJECTION, module.type());
    }

    @Test
    void construtorRejeitaNulos() {
        HttpClient http = req -> new HttpResponse(200, Map.of(), "", 1);
        Detector det = negativeDetector();
        List<Payload> ps = List.of();
        assertThrows(NullPointerException.class, () -> new SqlInjectionModule(null, det, ps));
        assertThrows(NullPointerException.class, () -> new SqlInjectionModule(http, null, ps));
        assertThrows(NullPointerException.class, () -> new SqlInjectionModule(http, det, null));
    }

    @Test
    void scanRejeitaTargetNulo() {
        SqlInjectionModule module = new SqlInjectionModule(
                req -> new HttpResponse(200, Map.of(), "", 1), negativeDetector(), List.of(quote()));
        assertThrows(NullPointerException.class, () -> module.scan(null));
    }

    @Test
    void payloadVulneravelViraFinding() {
        RecordingDetector det = new RecordingDetector(ev -> new Verdict(true, 0.9, "erro de SQL", "heuristic"));
        RecordingHttpClient http = new RecordingHttpClient(SqlInjectionModuleTest::dvwaLoginResponder);
        Payload p = quote();

        List<Finding> findings = new SqlInjectionModule(http, det, List.of(p)).scan(DVWA);

        assertEquals(1, findings.size());
        Finding f = findings.get(0);
        assertEquals(VulnType.SQL_INJECTION, f.type());
        assertEquals(Severity.CRITICAL, f.severity());
        assertEquals(DVWA, f.target());
        assertSame(p, f.evidence().payload());
        assertEquals("erro de SQL", f.verdict().rationale());
    }

    @Test
    void payloadNegativoNaoViraFinding() {
        SqlInjectionModule module = new SqlInjectionModule(
                new RecordingHttpClient(SqlInjectionModuleTest::dvwaLoginResponder),
                negativeDetector(), List.of(quote()));
        assertTrue(module.scan(DVWA).isEmpty());
    }

    @Test
    void cadaPayloadGeraUmaRequisicaoDeAtaqueNaOrdem() {
        RecordingHttpClient http = new RecordingHttpClient(SqlInjectionModuleTest::dvwaLoginResponder);
        List<Payload> ps = List.of(payload("sqli-quote-1", "'"), payload("sqli-error-1", "x' OR '1'='1"));

        new SqlInjectionModule(http, negativeDetector(), ps).scan(DVWA);

        List<HttpRequest> attacks = http.attacks();
        assertEquals(2, attacks.size());
        assertTrue(attacks.get(0).url().toString().contains("/vulnerabilities/sqli/"), attacks.get(0).url().toString());
        assertTrue(attacks.get(0).url().toString().contains("id=%27"), attacks.get(0).url().toString());
        assertTrue(attacks.get(0).url().toString().contains("Submit=Submit"), attacks.get(0).url().toString());
        assertTrue(attacks.get(1).url().toString().contains("id=x%27+OR+%271%27%3D%271"), attacks.get(1).url().toString());
    }

    @Test
    void detectorChamadoComAEvidenciaDeCadaPayload() {
        RecordingDetector det = new RecordingDetector(ev -> new Verdict(false, 0.6, "nada", "heuristic"));
        List<Payload> ps = List.of(payload("sqli-quote-1", "'"), payload("sqli-error-1", "\""));

        new SqlInjectionModule(new RecordingHttpClient(SqlInjectionModuleTest::dvwaLoginResponder), det, ps).scan(DVWA);

        assertEquals(2, det.judged.size());
        assertSame(ps.get(0), det.judged.get(0).payload());
        assertSame(ps.get(1), det.judged.get(1).payload());
    }

    @Test
    void misturaDeVereditosSoPositivosViramFinding() {
        List<Payload> ps = List.of(payload("sqli-quote-1", "a"), payload("sqli-error-1", "b"), payload("sqli-time-5s", "c"));
        RecordingDetector det = new RecordingDetector(ev ->
                ev.payload().id().equals("sqli-error-1")
                        ? new Verdict(true, 0.9, "erro", "heuristic")
                        : new Verdict(false, 0.6, "nada", "heuristic"));

        List<Finding> findings = new SqlInjectionModule(
                new RecordingHttpClient(SqlInjectionModuleTest::dvwaLoginResponder), det, ps).scan(DVWA);

        assertEquals(1, findings.size());
        assertEquals("sqli-error-1", findings.get(0).evidence().payload().id());
    }

    @Test
    void fazLoginEMandaCookieDeSessao() {
        RecordingHttpClient http = new RecordingHttpClient(SqlInjectionModuleTest::dvwaLoginResponder);

        new SqlInjectionModule(http, negativeDetector(), List.of(quote())).scan(DVWA);

        HttpRequest firstLoginGet = http.all.get(0);
        assertEquals("GET", firstLoginGet.method());
        assertTrue(firstLoginGet.url().toString().endsWith("/login.php"), firstLoginGet.url().toString());

        HttpRequest post = http.all.stream()
                .filter(r -> "POST".equals(r.method()) && r.url().toString().endsWith("/login.php"))
                .findFirst().orElseThrow();
        assertTrue(post.body().contains("username=admin"), post.body());
        assertTrue(post.body().contains("password=password"), post.body());
        assertTrue(post.body().contains("user_token=TOKEN42"), post.body());
        assertTrue(post.body().contains("Login=Login"), post.body());

        HttpRequest attack = http.attacks().get(0);
        String cookie = attack.headers().get("Cookie");
        assertTrue(cookie != null && cookie.contains("PHPSESSID=SID123"), String.valueOf(cookie));
        assertTrue(cookie.contains("security=low"), cookie);
    }

    @Test
    void listaDePayloadsVaziaNaoAtaca() {
        RecordingHttpClient http = new RecordingHttpClient(SqlInjectionModuleTest::dvwaLoginResponder);

        List<Finding> findings = new SqlInjectionModule(http, negativeDetector(), List.of()).scan(DVWA);

        assertTrue(findings.isEmpty());
        assertTrue(http.attacks().isEmpty());
    }

    private static Detector negativeDetector() {
        return new RecordingDetector(ev -> new Verdict(false, 0.6, "nada", "heuristic"));
    }

    /** HttpClient fake: responde por função e grava todas as requisições. */
    private static final class RecordingHttpClient implements HttpClient {
        private final Function<HttpRequest, HttpResponse> responder;
        final List<HttpRequest> all = new ArrayList<>();

        RecordingHttpClient(Function<HttpRequest, HttpResponse> responder) {
            this.responder = responder;
        }

        @Override
        public HttpResponse send(HttpRequest request) {
            all.add(request);
            return responder.apply(request);
        }

        /** Só as requisições de ataque (as de login.php ficam de fora). */
        List<HttpRequest> attacks() {
            return all.stream().filter(r -> !r.url().toString().contains("/login.php")).toList();
        }
    }

    /** Detector fake: julga por função e grava as evidências recebidas. */
    private static final class RecordingDetector implements Detector {
        private final Function<Evidence, Verdict> judge;
        final List<Evidence> judged = new ArrayList<>();

        RecordingDetector(Function<Evidence, Verdict> judge) {
            this.judge = judge;
        }

        @Override
        public Verdict judge(Evidence evidence) {
            judged.add(evidence);
            return judge.apply(evidence);
        }

        @Override
        public String name() {
            return "heuristic";
        }
    }
}

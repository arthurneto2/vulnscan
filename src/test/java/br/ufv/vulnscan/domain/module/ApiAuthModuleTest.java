package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.detection.Detector;
import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Severity;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.Verdict;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.domain.module.ApiSession.AuthSession;
import br.ufv.vulnscan.port.HttpClient;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes do módulo de auth/API com HttpClient, Detector e ApiSession fakes, sem rede. */
class ApiAuthModuleTest {

    private static final Target TARGET = new Target("juice", URI.create("http://localhost:3000"));
    private static final AuthProbe NO_TOKEN = new AuthProbe(VulnType.BROKEN_AUTH, "admin-config",
            AuthMode.NONE, "/rest/admin/application-configuration");
    private static final AuthProbe ROLE = new AuthProbe(VulnType.BROKEN_ACCESS_CONTROL, "lista-usuarios",
            AuthMode.USER_TOKEN, "/api/Users");
    private static final AuthProbe IDOR = new AuthProbe(VulnType.BROKEN_ACCESS_CONTROL, "idor-cesta",
            AuthMode.USER_TOKEN_OTHER_RESOURCE, "/rest/basket/{otherId}");

    private final RecordingHttpClient http = new RecordingHttpClient();
    private final FakeDetector detector = new FakeDetector();
    private final FakeSession session = new FakeSession("tok-abc", "7");

    @Test
    void typeEBrokenAuth() {
        assertEquals(VulnType.BROKEN_AUTH, module(List.of(NO_TOKEN)).type());
    }

    @Test
    void noneSemAuthorization() {
        module(List.of(NO_TOKEN)).scan(TARGET);

        HttpRequest request = http.requests.get(0);
        assertEquals("GET", request.method());
        assertEquals(URI.create("http://localhost:3000/rest/admin/application-configuration"), request.url());
        assertFalse(request.headers().containsKey("Authorization"), request.headers().toString());
        assertNull(request.body());
    }

    @Test
    void userTokenComBearer() {
        module(List.of(ROLE)).scan(TARGET);

        assertEquals("Bearer tok-abc", http.requests.get(0).headers().get("Authorization"));
        assertEquals(URI.create("http://localhost:3000/api/Users"), http.requests.get(0).url());
    }

    @Test
    void otherResourceTrocaIdEUsaBearer() {
        module(List.of(IDOR)).scan(TARGET);

        HttpRequest request = http.requests.get(0);
        assertEquals("Bearer tok-abc", request.headers().get("Authorization"));
        assertEquals(URI.create("http://localhost:3000/rest/basket/6"), request.url(), "7 → vizinho 6");
    }

    @Test
    void autenticaUmaVezMesmoComVariosProbesComToken() {
        module(List.of(ROLE, IDOR)).scan(TARGET);

        assertEquals(1, session.calls, "login uma vez só");
    }

    @Test
    void semProbeComTokenNaoChamaSessao() {
        module(List.of(NO_TOKEN)).scan(TARGET);

        assertEquals(0, session.calls);
    }

    @Test
    void evidenciaPorProbe() {
        http.response = new HttpResponse(200, Map.of(), "{\"config\":1}", 4);

        module(List.of(NO_TOKEN)).scan(TARGET);

        assertEquals(1, detector.judged.size());
        Evidence evidence = detector.judged.get(0);
        assertEquals("admin-config", evidence.payload().id());
        assertEquals(VulnType.BROKEN_AUTH, evidence.payload().type());
        assertSame(http.requests.get(0), evidence.request());
        assertEquals(http.response, evidence.response());
    }

    @Test
    void positivoViraFindingDoTipoDoProbeHigh() {
        detector.vulnerable = e -> e.payload().id().equals("idor-cesta");

        List<Finding> findings = module(List.of(IDOR)).scan(TARGET);

        assertEquals(1, findings.size());
        Finding finding = findings.get(0);
        assertEquals(VulnType.BROKEN_ACCESS_CONTROL, finding.type());
        assertEquals(Severity.HIGH, finding.severity());
        assertSame(TARGET, finding.target());
    }

    @Test
    void cadaProbeEIndependenteNaoEncerra() {
        detector.vulnerable = e -> true;

        List<Finding> findings = module(List.of(NO_TOKEN, ROLE, IDOR)).scan(TARGET);

        assertEquals(3, http.requests.size(), "todos os probes rodam");
        assertEquals(3, findings.size());
        assertEquals(List.of(VulnType.BROKEN_AUTH, VulnType.BROKEN_ACCESS_CONTROL,
                VulnType.BROKEN_ACCESS_CONTROL), findings.stream().map(Finding::type).toList());
    }

    @Test
    void erroDoHttpPropaga() {
        http.error = new RuntimeException("alvo fora do ar");
        assertThrows(RuntimeException.class, () -> module(List.of(NO_TOKEN)).scan(TARGET));
    }

    @Test
    void erroDaSessaoPropaga() {
        session.error = new IllegalStateException("login falhou");
        assertSame(session.error, assertThrows(IllegalStateException.class,
                () -> module(List.of(ROLE)).scan(TARGET)));
    }

    @Test
    void construtorValidaArgumentos() {
        List<AuthProbe> probes = List.of(NO_TOKEN);
        assertThrows(NullPointerException.class, () -> new ApiAuthModule(null, detector, session, probes));
        assertThrows(NullPointerException.class, () -> new ApiAuthModule(http, null, session, probes));
        assertThrows(NullPointerException.class, () -> new ApiAuthModule(http, detector, null, probes));
        assertThrows(NullPointerException.class, () -> new ApiAuthModule(http, detector, session, null));
        assertThrows(IllegalArgumentException.class, () -> new ApiAuthModule(http, detector, session, List.of()));
    }

    private ApiAuthModule module(List<AuthProbe> probes) {
        return new ApiAuthModule(http, detector, session, probes);
    }

    private static final class RecordingHttpClient implements HttpClient {
        final List<HttpRequest> requests = new ArrayList<>();
        HttpResponse response = new HttpResponse(200, Map.of(), "ok", 3);
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

    private static final class FakeSession implements ApiSession {
        final String token;
        final String ownId;
        int calls;
        RuntimeException error;

        FakeSession(String token, String ownId) {
            this.token = token;
            this.ownId = ownId;
        }

        @Override
        public AuthSession authenticate(Target target) {
            calls++;
            if (error != null) {
                throw error;
            }
            return new AuthSession(token, ownId);
        }
    }

    private static final class FakeDetector implements Detector {
        final List<Evidence> judged = new ArrayList<>();
        Predicate<Evidence> vulnerable = e -> false;

        @Override
        public Verdict judge(Evidence evidence) {
            judged.add(evidence);
            boolean v = vulnerable.test(evidence);
            return new Verdict(v, v ? 0.7 : 0.6, v ? "pos" : "neg", "fake");
        }

        @Override
        public String name() {
            return "fake";
        }
    }
}

package br.ufv.vulnscan.app.benchmark;

import br.ufv.vulnscan.app.benchmark.GroundTruth.Case;
import br.ufv.vulnscan.domain.detection.Detector;
import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.Verdict;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.domain.module.InjectionPoint;
import br.ufv.vulnscan.port.HttpClient;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Testa, offline, a derivação do ponto de injeção, a escolha do HttpClient por caso e o cache
 * da sessão do DVWA.
 */
class LabCaseRunnerTest {

    @Test
    void pointComQueryFixa() {
        InjectionPoint p = LabCaseRunner.pointFromLocation("/vulnerabilities/sqli/?Submit=Submit&id=");

        assertEquals("/vulnerabilities/sqli/?Submit=Submit", p.path());
        assertEquals("id", p.parameter());
    }

    @Test
    void pointSemQueryFixa() {
        InjectionPoint p = LabCaseRunner.pointFromLocation("/rest/products/search?q=");

        assertEquals("/rest/products/search", p.path());
        assertEquals("q", p.parameter());
    }

    @Test
    void pointComFragmento() {
        InjectionPoint p = LabCaseRunner.pointFromLocation("/#/search?q=");

        assertEquals("/#/search", p.path());
        assertEquals("q", p.parameter());
    }

    @Test
    void domQuandoLocationTemFragmento() {
        assertEquals(true, LabCaseRunner.isDom("/#/search?q="));
        assertEquals(false, LabCaseRunner.isDom("/vulnerabilities/xss_r/?name="));
    }

    private static final HttpClient PLAIN = request -> new HttpResponse(200, Map.of(), "", 1);

    @Test
    void impossibleDoDvwaUsaClienteComToken() {
        Case c = new Case("dvwa-sqli-impossible", VulnType.SQL_INJECTION,
                "/vulnerabilities/sqli/?Submit=Submit&id=", "impossible", null, false, null);
        assertInstanceOf(DvwaCsrfHttpClient.class, LabCaseRunner.httpFor(c, PLAIN));
    }

    @Test
    void lowEJuiceShopUsamClientePuro() {
        Case low = new Case("dvwa-sqli-low", VulnType.SQL_INJECTION,
                "/vulnerabilities/sqli/?Submit=Submit&id=", "low", null, true, null);
        Case semNivel = new Case("dvwa-xss", VulnType.XSS_REFLECTED,
                "/vulnerabilities/xss_r/?name=", null, null, true, null);
        Case juice = new Case("juice-search", VulnType.SQL_INJECTION,
                "/rest/products/search?q=", null, null, true, null);
        assertSame(PLAIN, LabCaseRunner.httpFor(low, PLAIN));
        assertSame(PLAIN, LabCaseRunner.httpFor(semNivel, PLAIN));
        assertSame(PLAIN, LabCaseRunner.httpFor(juice, PLAIN));
    }

    @Test
    void forgetSessionsFazNovoLoginNoDvwa() {
        List<String> logins = new ArrayList<>();
        HttpClient http = request -> {
            String path = request.url().getPath();
            if (path.equals("/login.php") && request.method().equals("GET")) {
                return new HttpResponse(200, Map.of("set-cookie", "PHPSESSID=abc; path=/"),
                        "<input type='hidden' name='user_token' value='aaa' />", 1);
            }
            if (path.equals("/login.php")) {
                logins.add("login");
                return new HttpResponse(302, Map.of("location", "index.php"), "", 1);
            }
            return new HttpResponse(200, Map.of(), "ok", 1);
        };
        Detector nunca = new Detector() {
            @Override
            public Verdict judge(Evidence evidence) {
                return new Verdict(false, 0.0, "fake", "heuristic");
            }

            @Override
            public String name() {
                return "heuristic";
            }
        };
        LabCaseRunner runner = new LabCaseRunner(Map.of("heuristic", nunca), http, null, null,
                List.of(new Payload("p1", VulnType.SQL_INJECTION, "'", "aspa")), List.of(), List.of(),
                () -> "vsx");
        Case dvwa = new Case("dvwa-sqli", VulnType.SQL_INJECTION, "/vulnerabilities/sqli/?Submit=Submit&id=",
                "low", null, true, null);
        URI base = URI.create("http://localhost:8080");

        runner.run(dvwa, base, "heuristic");
        runner.run(dvwa, base, "heuristic");
        assertEquals(1, logins.size(), "sem forgetSessions, o cookie é reaproveitado");

        runner.forgetSessions();
        runner.run(dvwa, base, "heuristic");
        assertEquals(2, logins.size(), "depois do forgetSessions, loga de novo");
    }
}

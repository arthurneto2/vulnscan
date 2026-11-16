package br.ufv.vulnscan.app.benchmark;

import br.ufv.vulnscan.app.benchmark.GroundTruth.Case;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.domain.module.InjectionPoint;
import br.ufv.vulnscan.port.HttpClient;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Testa, offline, a derivação do ponto de injeção e a escolha do HttpClient por caso. */
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
}

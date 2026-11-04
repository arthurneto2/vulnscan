package br.ufv.vulnscan.app.benchmark;

import br.ufv.vulnscan.domain.module.InjectionPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Testa, offline, a derivação do ponto de injeção a partir do location do gabarito. */
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
}

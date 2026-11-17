package br.ufv.vulnscan.domain.module;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Testes da montagem da URL de ataque de um ponto de injeção. */
class InjectionPointTest {

    private static final URI BASE = URI.create("http://localhost:8080");

    @Test
    void pathSemQueryUsaInterrogacao() {
        URI url = new InjectionPoint("/vulnerabilities/xss_r/", "name").url(BASE, "abc");

        assertEquals(URI.create("http://localhost:8080/vulnerabilities/xss_r/?name=abc"), url);
    }

    @Test
    void pathComQueryFixaUsaEComercial() {
        URI url = new InjectionPoint("/vulnerabilities/sqli/?Submit=Submit", "id").url(BASE, "1");

        assertEquals(URI.create("http://localhost:8080/vulnerabilities/sqli/?Submit=Submit&id=1"), url);
    }

    @Test
    void valorEhCodificadoEmUtf8() {
        URI url = new InjectionPoint("/redirect", "to").url(BASE, "<svg onload=alert('é')>");

        assertEquals(URI.create("http://localhost:8080/redirect?to=%3Csvg+onload%3Dalert%28%27%C3%A9%27%29%3E"), url);
    }

    @Test
    void urlContinuaComoFormulario() {
        URI url = new InjectionPoint("/vulnerabilities/xss_r/", "name").url(BASE, "</b>");

        assertEquals(URI.create("http://localhost:8080/vulnerabilities/xss_r/?name=%3C%2Fb%3E"), url);
    }

    @Test
    void browserUrlCodificaComoONavegador() {
        URI url = new InjectionPoint("/vulnerabilities/xss_d/#", "default")
                .browserUrl(BASE, "<script>alert('vsx1')</script>");

        assertEquals("http://localhost:8080/vulnerabilities/xss_d/#?default="
                + "%3Cscript%3Ealert('vsx1')%3C/script%3E", url.toString());
    }

    @Test
    void browserUrlCodificaEspacoAspasENaoAscii() {
        InjectionPoint point = new InjectionPoint("/#/search", "q");

        assertEquals("http://localhost:8080/#/search?q=%3Cimg%20src=%22x%22%20%C3%A9%3E",
                point.browserUrl(BASE, "<img src=\"x\" é>").toString());
        assertEquals("http://localhost:8080/#/search?q=%25%23%7B%7D%7C%5C%5E%60%5B%5D;:&+",
                point.browserUrl(BASE, "%#{}|\\^`[];:&+").toString());
    }
}

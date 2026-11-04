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
}

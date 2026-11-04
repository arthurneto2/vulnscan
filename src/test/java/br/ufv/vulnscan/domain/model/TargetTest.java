package br.ufv.vulnscan.domain.model;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Testes do record Target: headers enviados em toda requisição ao alvo. */
class TargetTest {

    private static final URI URL = URI.create("http://localhost:8080");

    @Test
    void construtorSemHeadersDaMapaVazio() {
        assertEquals(Map.of(), new Target("dvwa", URL).headers());
    }

    @Test
    void headersNulosViramVazio() {
        assertEquals(Map.of(), new Target("dvwa", URL, null).headers());
    }

    @Test
    void headersSaoCopiadosEImutaveis() {
        Map<String, String> original = new HashMap<>(Map.of("Cookie", "security=low"));
        Target target = new Target("dvwa", URL, original);

        original.put("X-Outro", "1");

        assertEquals(Map.of("Cookie", "security=low"), target.headers());
        assertThrows(UnsupportedOperationException.class, () -> target.headers().put("X", "y"));
    }
}

package br.ufv.vulnscan.domain.model;

import java.net.URI;
import java.util.Map;

/**
 * Alvo sob teste: um app do laboratório. {@code headers} vão em toda requisição ao alvo
 * (ex.: o cookie de sessão do DVWA); nulo vira vazio e o mapa é copiado e imutável.
 */
public record Target(String name, URI baseUrl, Map<String, String> headers) {

    public Target {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    /** Alvo sem headers extras. */
    public Target(String name, URI baseUrl) {
        this(name, baseUrl, Map.of());
    }
}

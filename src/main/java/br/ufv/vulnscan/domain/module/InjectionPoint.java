package br.ufv.vulnscan.domain.module;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Domínio: ponto de injeção de um módulo — requisição {@code GET} em {@code path} (relativo
 * à URL base do alvo; pode trazer query fixa, ex.: {@code /vulnerabilities/sqli/?Submit=Submit})
 * com o payload no parâmetro de query {@code parameter}.
 */
public record InjectionPoint(String path, String parameter) {

    /** URL base + {@code path} + {@code parameter=<value URL-encoded UTF-8>}, com ? ou &. */
    public URI url(URI baseUrl, String value) {
        String base = baseUrl.resolve(path).toString();
        String separator = base.contains("?") ? "&" : "?";
        return URI.create(base + separator + parameter + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
    }
}

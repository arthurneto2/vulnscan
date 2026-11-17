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

    /**
     * Caracteres ASCII que {@link #browserUrl} codifica: o conjunto de fragmento do WHATWG
     * (espaço, {@code "}, {@code <}, {@code >}, {@code `}) mais o que o {@link URI} recusa.
     */
    private static final String BROWSER_ENCODED = " \"#%<>[\\]^`{|}";

    /**
     * Como {@link #url}, mas codifica o valor como um navegador codifica o fragmento: só
     * controles, não-ASCII (UTF-8) e {@code  "#%<>[\\]^`{|}}. {@code /}, {@code '}, {@code ()} e
     * {@code =} ficam crus. Para o XSS de DOM, que a página lê de {@code location.href}
     * (ex.: com {@code decodeURI}, que não decodifica {@code %2F}) — issue #77.
     */
    public URI browserUrl(URI baseUrl, String value) {
        String base = baseUrl.resolve(path).toString();
        String separator = base.contains("?") ? "&" : "?";
        return URI.create(base + separator + parameter + "=" + browserEncode(value));
    }

    private static String browserEncode(String value) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if (c <= 0x20 || c >= 0x7F || BROWSER_ENCODED.indexOf(c) >= 0) {
                out.append('%').append(String.format("%02X", c));
            } else {
                out.append((char) c);
            }
        }
        return out.toString();
    }
}

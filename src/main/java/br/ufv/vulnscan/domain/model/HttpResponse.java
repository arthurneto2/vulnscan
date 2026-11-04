package br.ufv.vulnscan.domain.model;

import java.util.Map;

/**
 * Resposta HTTP devolvida pela porta HttpClient e guardada na evidência. Módulos que usam
 * navegador montam uma resposta sintética: corpo = DOM renderizado e, se houve diálogos
 * ({@code alert/confirm/prompt}), o header {@link #DIALOGS_HEADER} com os textos juntados
 * por {@code ", "}.
 */
public record HttpResponse(int status, Map<String, String> headers, String body, long elapsedMillis) {

    /** Pseudo-header com os textos dos diálogos que o navegador disparou. */
    public static final String DIALOGS_HEADER = "x-vulnscan-dialogs";
}

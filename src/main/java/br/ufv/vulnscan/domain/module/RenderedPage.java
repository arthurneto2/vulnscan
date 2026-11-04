package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.model.HttpResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Domínio: página aberta num navegador real por um {@link XssBrowser} — DOM depois do
 * JavaScript ({@code outerHTML}), textos dos diálogos disparados ({@code alert/confirm/prompt},
 * na ordem; nulo vira vazio, lista copiada e imutável) e tempo até assentar.
 */
public record RenderedPage(String html, List<String> dialogs, long elapsedMillis) {

    public RenderedPage {
        dialogs = dialogs == null ? List.of() : List.copyOf(dialogs);
    }

    /**
     * Resposta sintética para a {@code Evidence}: status 200, {@code content-type: text/html},
     * corpo = DOM e, se houve diálogos, {@link HttpResponse#DIALOGS_HEADER} com os textos
     * juntados por {@code ", "}.
     */
    public HttpResponse toResponse() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("content-type", "text/html");
        if (!dialogs.isEmpty()) {
            headers.put(HttpResponse.DIALOGS_HEADER, String.join(", ", dialogs));
        }
        return new HttpResponse(200, Map.copyOf(headers), html, elapsedMillis);
    }
}

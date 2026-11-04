package br.ufv.vulnscan.domain.module;

import java.util.List;

/**
 * Domínio: página aberta num navegador real por um {@link XssBrowser} — DOM depois do
 * JavaScript ({@code outerHTML}), textos dos diálogos disparados ({@code alert/confirm/prompt},
 * na ordem; nulo vira vazio, lista copiada e imutável) e tempo até assentar.
 */
public record RenderedPage(String html, List<String> dialogs, long elapsedMillis) {

    public RenderedPage {
        dialogs = dialogs == null ? List.of() : List.copyOf(dialogs);
    }
}

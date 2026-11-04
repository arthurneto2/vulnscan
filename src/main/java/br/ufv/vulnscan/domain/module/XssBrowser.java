package br.ufv.vulnscan.domain.module;

import java.net.URI;
import java.util.Map;

/**
 * Domínio: interface pequena dos módulos de XSS que precisam de navegador (DOM e armazenado).
 * Não é porta: existe só para o domínio não depender de Selenium (o adaptador é
 * {@code adapter/browser/SeleniumBrowser}). De {@code headers}, só {@code Cookie} é aplicado
 * — WebDriver não envia header arbitrário.
 */
public interface XssBrowser {

    /** Abre {@code url}, espera a página assentar e devolve o DOM, os diálogos e o tempo. */
    RenderedPage open(URI url, Map<String, String> headers);
}

package br.ufv.vulnscan.adapter.browser;

import br.ufv.vulnscan.domain.module.RenderedPage;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.SessionNotCreatedException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do adaptador Selenium contra um servidor do JDK em processo (porta efêmera), nunca o
 * laboratório. Pulados se o Chrome não puder ser iniciado neste ambiente.
 */
class SeleniumBrowserTest {

    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/js", exchange -> respond(exchange.getResponseBody(), exchange,
                "<html><body><div id='alvo'></div><script>"
                        + "document.getElementById('alvo').textContent='js-rodou';"
                        + "alert('vsx1a2b3c4d');</script></body></html>"));
        server.createContext("/eco", exchange -> respond(exchange.getResponseBody(), exchange,
                "<html><body><pre>cookie=" + Objects.requireNonNullElse(
                        exchange.getRequestHeaders().getFirst("Cookie"), "") + "</pre></body></html>"));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void devolveDomDepoisDoJavascript() {
        RenderedPage page = open("/js", Map.of());

        assertTrue(page.html().contains("<div id=\"alvo\">js-rodou</div>"), page.html());
        assertTrue(page.elapsedMillis() > 0, "tempo: " + page.elapsedMillis());
    }

    @Test
    void capturaTextoDoAlert() {
        assertEquals(List.of("vsx1a2b3c4d"), open("/js", Map.of()).dialogs());
    }

    @Test
    void enviaCookieDoHeader() {
        RenderedPage page = open("/eco", Map.of("Cookie", "PHPSESSID=abc; security=low"));

        assertTrue(page.html().contains("PHPSESSID=abc"), page.html());
        assertTrue(page.html().contains("security=low"), page.html());
        assertEquals(List.of(), page.dialogs());
    }

    private RenderedPage open(String path, Map<String, String> headers) {
        URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
        try {
            return new SeleniumBrowser().open(url, headers);
        } catch (SessionNotCreatedException e) {
            Assumptions.abort("Chrome indisponível neste ambiente: " + e.getRawMessage());
            throw e;
        }
    }

    private static void respond(OutputStream out, com.sun.net.httpserver.HttpExchange exchange, String html)
            throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (out) {
            out.write(body);
        }
    }
}

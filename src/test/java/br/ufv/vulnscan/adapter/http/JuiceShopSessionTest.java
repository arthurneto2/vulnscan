package br.ufv.vulnscan.adapter.http;

import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.module.ApiSession.AuthSession;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes do adaptador de sessão contra um servidor do JDK que imita o Juice Shop. */
class JuiceShopSessionTest {

    private HttpServer server;
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private volatile String loginBody = "{\"authentication\":{\"token\":\"tok-xyz\",\"bid\":7}}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/Users", exchange -> reply(exchange, 201, "{\"status\":\"success\"}"));
        server.createContext("/rest/user/login", exchange -> reply(exchange, 200, loginBody));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void registraELogaDevolvendoTokenEBid() {
        AuthSession session = new JuiceShopSession().authenticate(target());

        assertEquals("tok-xyz", session.token());
        assertEquals("7", session.ownResourceId());
        assertTrue(paths.contains("/api/Users"), "registrou: " + paths);
        assertTrue(paths.contains("/rest/user/login"), "logou: " + paths);
    }

    @Test
    void loginSemTokenLancaIllegalState() {
        loginBody = "{\"authentication\":{}}";

        assertThrows(IllegalStateException.class, () -> new JuiceShopSession().authenticate(target()));
    }

    private Target target() {
        return new Target("juice", URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    }

    private void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        paths.add(exchange.getRequestURI().getPath());
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}

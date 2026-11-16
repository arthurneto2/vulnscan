package br.ufv.vulnscan.app.benchmark;

import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.port.HttpClient;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes do login do DVWA (com criação do banco) usando um HttpClient fake. */
class DvwaSessionTest {

    private static final URI BASE = URI.create("http://localhost:8080");

    @Test
    void loginComBancoProntoNaoChamaSetup() {
        ScriptedHttp http = new ScriptedHttp()
                .on("GET /login.php", page("abc", "aaa"))
                .on("POST /login.php", redirect("index.php"));

        String cookie = new DvwaSession(http).login(BASE, "low");

        assertEquals("PHPSESSID=abc; security=low", cookie);
        assertTrue(http.paths().stream().noneMatch(p -> p.contains("setup.php")), http.paths().toString());
    }

    @Test
    void criaBancoQuandoLoginRedirecionaParaSetup() {
        ScriptedHttp http = new ScriptedHttp()
                .on("GET /login.php", page("abc", "aaa"), page("def", "bbb"))
                .on("POST /login.php", redirect("setup.php"), redirect("index.php"))
                .on("GET /setup.php", page(null, "5e7"))
                .on("POST /setup.php", new HttpResponse(200, Map.of(), "Setup successful!", 1));

        String cookie = new DvwaSession(http).login(BASE, "low");

        HttpRequest setup = http.requests.stream()
                .filter(r -> r.method().equals("POST") && r.url().getPath().equals("/setup.php"))
                .findFirst().orElseThrow(() -> new AssertionError("sem POST /setup.php: " + http.paths()));
        assertTrue(setup.body().contains("create_db="), setup.body());
        assertTrue(setup.body().contains("user_token=5e7"), setup.body());
        assertEquals("PHPSESSID=abc; security=low", setup.headers().get("Cookie"));
        assertEquals(List.of("GET /login.php", "POST /login.php", "GET /setup.php", "POST /setup.php",
                "GET /login.php", "POST /login.php"), http.paths());
        assertEquals("PHPSESSID=def; security=low", cookie);
    }

    @Test
    void falhaSeSetupNaoCriaOBanco() {
        ScriptedHttp http = new ScriptedHttp()
                .on("GET /login.php", page("abc", "aaa"))
                .on("POST /login.php", redirect("setup.php"))
                .on("GET /setup.php", page(null, "5e7"))
                .on("POST /setup.php", new HttpResponse(200, Map.of(), "Could not connect", 1));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new DvwaSession(http).login(BASE, "low"));

        assertTrue(e.getMessage().contains("setup.php"), e.getMessage());
    }

    // --- fakes e respostas ---

    private static HttpResponse page(String sid, String token) {
        Map<String, String> headers = sid == null ? Map.of() : Map.of("set-cookie", "PHPSESSID=" + sid + "; path=/");
        return new HttpResponse(200, headers,
                "<form><input type='hidden' name='user_token' value='" + token + "' /></form>", 1);
    }

    private static HttpResponse redirect(String location) {
        return new HttpResponse(302, Map.of("location", location), "", 1);
    }

    /** Responde por "MÉTODO /path", em fila (a última resposta se repete); grava as requisições. */
    private static final class ScriptedHttp implements HttpClient {
        final List<HttpRequest> requests = new ArrayList<>();
        private final Map<String, Deque<HttpResponse>> script = new HashMap<>();

        ScriptedHttp on(String key, HttpResponse... responses) {
            script.computeIfAbsent(key, k -> new ArrayDeque<>()).addAll(List.of(responses));
            return this;
        }

        @Override
        public HttpResponse send(HttpRequest request) {
            requests.add(request);
            Deque<HttpResponse> queue = script.get(request.method() + " " + request.url().getPath());
            if (queue == null || queue.isEmpty()) {
                return new HttpResponse(404, Map.of(), "", 1);
            }
            return queue.size() > 1 ? queue.poll() : queue.peek();
        }

        List<String> paths() {
            return requests.stream().map(r -> r.method() + " " + r.url().getPath()).toList();
        }
    }
}

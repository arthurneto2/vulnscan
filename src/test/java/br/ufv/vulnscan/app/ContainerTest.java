package br.ufv.vulnscan.app;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes da raiz de composição: grafo montado à mão, sem framework de DI. O alvo é um
 * servidor do JDK em processo (porta efêmera) que imita o DVWA, nunca o laboratório.
 */
class ContainerTest {

    private static final String MYSQL_ERROR = "<pre>You have an error in your SQL syntax; check the manual "
            + "that corresponds to your MariaDB server version for the right syntax</pre>";

    @TempDir
    Path dir;

    private HttpServer server;
    private final List<String> received = new CopyOnWriteArrayList<>();
    private final List<String> cookies = new CopyOnWriteArrayList<>();
    private final List<String> queries = new CopyOnWriteArrayList<>();

    /** Imita o DVWA: em /vulnerabilities/sqli/, id com aspas simples devolve erro de MySQL. */
    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            URI uri = exchange.getRequestURI();
            received.add(exchange.getRequestMethod() + " " + uri.getPath());
            queries.add(uri.getPath() + "?" + uri.getQuery());
            String cookie = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookie != null) {
                cookies.add(cookie);
            }
            boolean injectable = uri.getPath().equals("/vulnerabilities/sqli/")
                    && uri.getQuery() != null && uri.getQuery().contains("id=1'");
            byte[] body = (injectable ? MYSQL_ERROR : "<p>ok</p>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void configPadraoTemTimeoutDe30s() {
        assertEquals(Duration.ofSeconds(30), Config.defaults().httpTimeout());
    }

    @Test
    void moduloSqliDetectaErroMysqlNoPontoDoDvwa() throws IOException {
        Path out = dir.resolve("report.md");
        ScanService service = new Container(Config.defaults()).scanService();

        service.scan(new ScanRequest(target(), List.of("sqli"), "md", out,
                Map.of("Cookie", "PHPSESSID=abc; security=low")));

        String md = Files.readString(out);
        assertTrue(md.contains("- **Achados:** 1"), md);
        assertTrue(md.contains("SQL_INJECTION — HIGH"), md);
        assertTrue(md.contains("You have an error in your SQL syntax"), md);
        assertTrue(!cookies.isEmpty() && cookies.stream().allMatch("PHPSESSID=abc; security=low"::equals),
                "cookies: " + cookies);
    }

    /**
     * O modelo de 14B leva ~20 s (máx. ~80 s) por veredito: o Ollama tem timeout próprio, maior que o
     * do alvo. Aqui, em escala: o Ollama falso demora 600 ms; o alvo tem 300 ms e o Ollama 5 s.
     */
    @Test
    void llmUsaTimeoutProprioMaiorQueODoAlvo() throws IOException {
        server.createContext("/api/generate", exchange -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String verdict = "{\\\"evidence\\\":\\\"You have an error in your SQL syntax\\\","
                    + "\\\"rationale\\\":\\\"erro de SQL\\\",\\\"vulnerable\\\":true,\\\"confidence\\\":0.9}";
            byte[] body = ("{\"response\":\"" + verdict + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        Path out = dir.resolve("report.md");
        Config config = new Config(Duration.ofMillis(300), target(), "modelo", Duration.ofSeconds(5));

        new Container(config).scanService().scan(new ScanRequest(target(), List.of("sqli"), "md", out,
                Map.of(), "llm"));

        String md = Files.readString(out);
        assertTrue(md.contains("- **Achados:** 1"), md);
    }

    /** Veredito "vulnerável" com evidência presente no corpo do DVWA falso (passa no grounding). */
    private static final String SQLI_VERDICT = "{\\\"evidence\\\":\\\"You have an error in your SQL syntax\\\","
            + "\\\"rationale\\\":\\\"erro de SQL\\\",\\\"vulnerable\\\":true,\\\"confidence\\\":0.9}";

    /** Provedor compatível com OpenAI falso no mesmo servidor; guarda o header Authorization. */
    private final List<String> authorizations = new CopyOnWriteArrayList<>();

    private void fakeOpenAiProvider() {
        server.createContext("/v1/chat/completions", exchange -> {
            received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = ("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + SQLI_VERDICT
                    + "\"}}]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }

    private void fakeOllama() {
        server.createContext("/api/generate", exchange -> {
            received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            byte[] body = ("{\"response\":\"" + SQLI_VERDICT + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }

    private Config localConfig() {
        return new Config(Duration.ofSeconds(5), target(), "modelo-local", Duration.ofSeconds(5));
    }

    @Test
    void llmApiUsaProvedorCompativelComOpenAi() throws IOException {
        fakeOpenAiProvider();
        Map<String, String> env = Map.of("VULNSCAN_LLM_API_KEY", "chave-falsa",
                "VULNSCAN_LLM_API_URL", target() + "v1", "VULNSCAN_LLM_API_MODEL", "modelo-api");
        Path out = dir.resolve("report.md");

        new Container(localConfig(), env).scanService("api").scan(new ScanRequest(target(), List.of("sqli"),
                "md", out, Map.of(), "llm"));

        assertTrue(Files.readString(out).contains("- **Achados:** 1"), Files.readString(out));
        assertTrue(received.contains("POST /v1/chat/completions"), "recebidas: " + received);
        assertTrue(!received.contains("POST /api/generate"), "não chama o Ollama: " + received);
        assertTrue(authorizations.stream().allMatch("Bearer chave-falsa"::equals), "auth: " + authorizations);
    }

    @Test
    void llmOllamaUsaOOllamaEIgnoraAApi() throws IOException {
        fakeOllama();
        fakeOpenAiProvider();
        Map<String, String> env = Map.of("VULNSCAN_LLM_API_KEY", "chave-falsa",
                "VULNSCAN_LLM_API_URL", target() + "v1");
        Path out = dir.resolve("report.md");

        new Container(localConfig(), env).scanService("ollama").scan(new ScanRequest(target(), List.of("sqli"),
                "md", out, Map.of(), "llm"));

        assertTrue(Files.readString(out).contains("- **Achados:** 1"), Files.readString(out));
        assertTrue(received.contains("POST /api/generate"), "recebidas: " + received);
        assertTrue(!received.contains("POST /v1/chat/completions"), "não chama a API: " + received);
    }

    @Test
    void llmApiSemChaveEErroDeUsoCitandoAVariavel() {
        Container container = new Container(localConfig(), Map.of());

        InvalidScanRequestException e = assertThrows(InvalidScanRequestException.class,
                () -> container.scanService("api"));

        assertTrue(e.getMessage().contains("VULNSCAN_LLM_API_KEY"), e.getMessage());
    }

    @Test
    void llmDesconhecidoEErroDeUsoListandoAsOpcoes() {
        Container container = new Container(localConfig(), Map.of());

        InvalidScanRequestException e = assertThrows(InvalidScanRequestException.class,
                () -> container.benchmarkService("gpt"));

        assertTrue(e.getMessage().contains("gpt") && e.getMessage().contains("ollama, api"), e.getMessage());
    }

    @Test
    void sqliEXssRodamJuntos() {
        ScanService service = new Container(Config.defaults()).scanService();

        service.scan(new ScanRequest(target(), List.of("sqli", "xss"), "md", dir.resolve("report.md")));

        assertTrue(received.containsAll(List.of("GET /vulnerabilities/sqli/", "GET /rest/products/search",
                "GET /vulnerabilities/xss_r/", "GET /redirect")), "recebidas: " + received);
    }

    @Test
    void moduloXssAtacaDvwaEJuiceShopComMarcadoresDistintos() {
        ScanService service = new Container(Config.defaults()).scanService();

        service.scan(new ScanRequest(target(), List.of("xss"), "md", dir.resolve("report.md")));

        assertTrue(received.containsAll(List.of("GET /vulnerabilities/xss_r/", "GET /redirect")),
                "recebidas: " + received);
        Pattern marker = Pattern.compile("vsx[0-9a-f]{8}");
        List<String> markers = queries.stream().map(marker::matcher).filter(Matcher::find)
                .map(Matcher::group).toList();
        assertEquals(queries.size(), markers.size(), "toda requisição leva marcador: " + queries);
        assertEquals(markers.size(), markers.stream().distinct().count(), "marcadores repetidos: " + markers);
    }

    @Test
    void modulosDisponiveisIncluemXssDomEXssStored() {
        ScanService service = new Container(Config.defaults()).scanService();

        InvalidScanRequestException e = assertThrows(InvalidScanRequestException.class,
                () -> service.scan(new ScanRequest(target(), List.of("nenhum"), "md", dir.resolve("r.md"))));

        assertTrue(e.getMessage().contains("(disponíveis: sqli, xss, xss-dom, xss-stored, api)"), e.getMessage());
        assertTrue(received.isEmpty(), "nenhuma requisição: " + received);
    }

    @Test
    void formatoJsonGravaJson() throws IOException {
        Path out = dir.resolve("report.json");
        ScanService service = new Container(Config.defaults()).scanService();

        service.scan(new ScanRequest(target(), List.of("sqli"), "json", out,
                Map.of("Cookie", "PHPSESSID=abc; security=low")));

        String json = Files.readString(out);
        assertTrue(json.contains("\"findingCount\""), json);
        assertTrue(json.contains("\"target\""), json);
    }

    @Test
    void formatoHtmlGravaHtml() throws IOException {
        Path out = dir.resolve("report.html");
        ScanService service = new Container(Config.defaults()).scanService();

        service.scan(new ScanRequest(target(), List.of("sqli"), "html", out,
                Map.of("Cookie", "PHPSESSID=abc; security=low")));

        String html = Files.readString(out);
        assertTrue(html.contains("<!DOCTYPE html>"), html);
        assertTrue(html.contains("SQL_INJECTION"), html);
    }

    @Test
    void moduloSmokeNaoExisteMais() {
        ScanService service = new Container(Config.defaults()).scanService();

        assertThrows(InvalidScanRequestException.class,
                () -> service.scan(new ScanRequest(target(), List.of("smoke"), "md", dir.resolve("r.md"))));
    }

    private URI target() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }
}

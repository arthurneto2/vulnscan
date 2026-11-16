package br.ufv.vulnscan.app.benchmark;

import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.port.HttpClient;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Testes do decorador que acrescenta o user_token anti-CSRF do DVWA, com HttpClient fake. */
class DvwaCsrfHttpClientTest {

    private static final String BASE = "http://localhost:8080";
    private static final Map<String, String> COOKIE = Map.of("Cookie", "PHPSESSID=abc; security=impossible");

    @Test
    void getComQueryRecebeTokenDaPagina() {
        RecordingHttp http = new RecordingHttp(tokenPage("a1b2"));

        HttpResponse response = new DvwaCsrfHttpClient(http).send(new HttpRequest("GET",
                URI.create(BASE + "/vulnerabilities/sqli/?Submit=Submit&id=1%27"), COOKIE, null));

        assertEquals(2, http.requests.size(), http.requests.toString());
        HttpRequest tokenFetch = http.requests.get(0);
        assertEquals("GET", tokenFetch.method());
        assertEquals(URI.create(BASE + "/vulnerabilities/sqli/"), tokenFetch.url());
        assertEquals(COOKIE, tokenFetch.headers());
        HttpRequest sent = http.requests.get(1);
        assertEquals(URI.create(BASE + "/vulnerabilities/sqli/?Submit=Submit&id=1%27&user_token=a1b2"), sent.url());
        assertEquals(COOKIE, sent.headers());
        assertEquals("resultado", response.body());
    }

    @Test
    void postRecebeTokenNoCorpo() {
        RecordingHttp http = new RecordingHttp(tokenPage("a1b2"));
        Map<String, String> headers = Map.of("Cookie", COOKIE.get("Cookie"),
                "Content-Type", "application/x-www-form-urlencoded");
        URI url = URI.create(BASE + "/vulnerabilities/xss_s/");

        new DvwaCsrfHttpClient(http).send(new HttpRequest("POST", url, headers,
                "txtName=v&mtxMessage=x&btnSign=Sign+Guestbook"));

        assertEquals(2, http.requests.size(), http.requests.toString());
        HttpRequest tokenFetch = http.requests.get(0);
        assertEquals(new HttpRequest("GET", url, COOKIE, null), tokenFetch);
        HttpRequest sent = http.requests.get(1);
        assertEquals(new HttpRequest("POST", url, headers,
                "txtName=v&mtxMessage=x&btnSign=Sign+Guestbook&user_token=a1b2"), sent);
    }

    @Test
    void paginaSemTokenMandaOriginal() {
        RecordingHttp http = new RecordingHttp(new HttpResponse(200, Map.of(), "<p>sem form</p>", 1));
        HttpRequest original = new HttpRequest("GET",
                URI.create(BASE + "/vulnerabilities/sqli/?Submit=Submit&id=1"), COOKIE, null);

        new DvwaCsrfHttpClient(http).send(original);

        assertEquals(original, http.requests.get(http.requests.size() - 1));
    }

    @Test
    void getSemQueryNaoBuscaToken() {
        RecordingHttp http = new RecordingHttp(tokenPage("a1b2"));
        HttpRequest original = new HttpRequest("GET", URI.create(BASE + "/vulnerabilities/xss_s/"), COOKIE, null);

        new DvwaCsrfHttpClient(http).send(original);

        assertEquals(List.of(original), http.requests);
    }

    // --- fakes e respostas ---

    private static HttpResponse tokenPage(String token) {
        return new HttpResponse(200, Map.of(),
                "<form><input type='hidden' name='user_token' value='" + token + "' /></form>", 1);
    }

    /** Requisições sem query e sem corpo recebem {@code page}; as demais, "resultado". */
    private static final class RecordingHttp implements HttpClient {
        final List<HttpRequest> requests = new ArrayList<>();
        private final HttpResponse page;

        RecordingHttp(HttpResponse page) {
            this.page = page;
        }

        @Override
        public HttpResponse send(HttpRequest request) {
            requests.add(request);
            boolean plainGet = request.url().getQuery() == null && request.body() == null;
            return plainGet ? page : new HttpResponse(200, Map.of(), "resultado", 7);
        }
    }
}

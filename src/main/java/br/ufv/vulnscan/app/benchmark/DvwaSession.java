package br.ufv.vulnscan.app.benchmark;

import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.port.HttpClient;

import java.net.URI;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aplicação (experimento): login no DVWA para o benchmark. Devolve o header {@code Cookie}
 * da sessão autenticada.
 */
final class DvwaSession {

    private static final Pattern USER_TOKEN =
            Pattern.compile("user_token'\\s*value='([a-f0-9]+)'");
    private static final Pattern PHPSESSID = Pattern.compile("PHPSESSID=([^;\\s]+)");

    private final HttpClient http;

    DvwaSession(HttpClient http) {
        this.http = http;
    }

    /**
     * Loga como admin e devolve {@code PHPSESSID=<sid>; security=<nível>}. Se o DVWA pedir o
     * setup (banco ainda não criado, como logo após o reset do lab), cria o banco e loga de novo.
     */
    String login(URI baseUrl, String level) {
        Attempt first = attempt(baseUrl, level);
        if (!first.needsSetup()) {
            return first.cookie();
        }
        createDatabase(baseUrl, first.cookie());
        Attempt second = attempt(baseUrl, level);
        if (second.needsSetup()) {
            throw new IllegalStateException("DVWA: o banco não foi criado; o login ainda redireciona para "
                    + baseUrl.resolve("/setup.php") + " (abra a página e use Create / Reset Database)");
        }
        return second.cookie();
    }

    private Attempt attempt(URI baseUrl, String level) {
        URI loginUrl = baseUrl.resolve("/login.php");
        HttpResponse page = http.send(new HttpRequest("GET", loginUrl, Map.of(), null));
        String sid = firstMatch(PHPSESSID, page.headers().getOrDefault("set-cookie", ""));
        String token = firstMatch(USER_TOKEN, page.body());
        String cookie = "PHPSESSID=" + sid + "; security=" + level;
        String body = "username=admin&password=password&user_token=" + token + "&Login=Login";
        HttpResponse response = post(loginUrl, cookie, body);
        return new Attempt(cookie, redirectsToSetup(response));
    }

    /** "Create / Reset Database" do setup.php, com o token CSRF da própria página. */
    private void createDatabase(URI baseUrl, String cookie) {
        URI setupUrl = baseUrl.resolve("/setup.php");
        HttpResponse page = http.send(new HttpRequest("GET", setupUrl, Map.of("Cookie", cookie), null));
        String token = firstMatch(USER_TOKEN, page.body());
        post(setupUrl, cookie, "create_db=Create+%2F+Reset+Database&user_token=" + token);
    }

    private HttpResponse post(URI url, String cookie, String body) {
        return http.send(new HttpRequest("POST", url,
                Map.of("Cookie", cookie, "Content-Type", "application/x-www-form-urlencoded"), body));
    }

    private static boolean redirectsToSetup(HttpResponse response) {
        return response.status() / 100 == 3
                && response.headers().getOrDefault("location", "").contains("setup.php");
    }

    private record Attempt(String cookie, boolean needsSetup) {
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text == null ? "" : text);
        return m.find() ? m.group(1) : "";
    }
}

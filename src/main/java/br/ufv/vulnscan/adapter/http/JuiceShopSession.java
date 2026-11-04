package br.ufv.vulnscan.adapter.http;

import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.module.ApiSession;
import br.ufv.vulnscan.port.HttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador: {@link ApiSession} para o Juice Shop. Registra um usuário novo (email único) via
 * {@code POST /api/Users} e loga via {@code POST /rest/user/login} pela porta
 * {@link HttpClient}, extraindo {@code authentication.token} e {@code authentication.bid} do
 * JSON com Jackson. Específico do Juice Shop (como o SeleniumBrowser é do Chrome). Login sem
 * token no JSON → {@link IllegalStateException}.
 */
public final class JuiceShopSession implements ApiSession {

    private static final String PASSWORD = "Passw0rd!1";
    private static final Map<String, String> JSON = Map.of("Content-Type", "application/json");

    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public JuiceShopSession() {
        this(new JdkHttpClient());
    }

    JuiceShopSession(HttpClient http) {
        this.http = http;
    }

    @Override
    public AuthSession authenticate(Target target) {
        URI base = target.baseUrl();
        String email = "vulnscan-" + UUID.randomUUID() + "@test.local";
        register(base, email);
        return login(base, email);
    }

    private void register(URI base, String email) {
        String body = "{\"email\":%s,\"password\":%s,\"passwordRepeat\":%s,"
                .formatted(quote(email), quote(PASSWORD), quote(PASSWORD))
                + "\"securityQuestion\":{\"id\":1},\"securityAnswer\":\"vulnscan\"}";
        http.send(new HttpRequest("POST", base.resolve("/api/Users"), JSON, body));
    }

    private AuthSession login(URI base, String email) {
        String body = "{\"email\":%s,\"password\":%s}".formatted(quote(email), quote(PASSWORD));
        HttpResponse response = http.send(new HttpRequest("POST", base.resolve("/rest/user/login"), JSON, body));
        JsonNode auth = parse(response.body()).path("authentication");
        JsonNode token = auth.path("token");
        if (!token.isTextual() || token.asText().isEmpty()) {
            throw new IllegalStateException("login no Juice Shop sem token: " + truncate(response.body()));
        }
        return new AuthSession(token.asText(), auth.path("bid").asText());
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body == null ? "" : body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("resposta de login não é JSON: " + truncate(body), e);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String quote(String value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String body) {
        String s = body == null ? "" : body;
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}

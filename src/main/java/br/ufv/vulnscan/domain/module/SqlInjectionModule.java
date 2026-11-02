package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.detection.Detector;
import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.Severity;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.Verdict;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.port.HttpClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Domínio: módulo de SQL injection (estratégia {@link VulnModule}). Autentica no DVWA do
 * laboratório, aplica cada {@link Payload} no ponto de injeção conhecido
 * ({@code /vulnerabilities/sqli/?id=…}), captura a troca HTTP como {@link Evidence} e pede
 * o veredito ao {@link Detector} injetado. Cada veredito vulnerável vira um {@link Finding}.
 *
 * <p>Não decide se há vulnerabilidade — isso é do {@code Detector}, para a comparação
 * heurística × LLM ficar justa. Os payloads chegam prontos por construtor (lidos de
 * {@code payloads/sqli.txt} na raiz de composição), para o domínio não tocar disco.
 *
 * <p>Sessão do DVWA: GET {@code login.php} (extrai o {@code user_token} do HTML e o
 * {@code PHPSESSID} do {@code Set-Cookie}), POST {@code login.php} como {@code admin},
 * depois manda {@code Cookie: PHPSESSID=…; security=low} em cada ataque. É específico do
 * laboratório; outros alvos/pontos ficam para issues futuras.
 */
public final class SqlInjectionModule implements VulnModule {

    private static final Severity SQLI_SEVERITY = Severity.CRITICAL;

    private static final String LOGIN_PATH = "/login.php";
    private static final String SQLI_PATH = "/vulnerabilities/sqli/";
    private static final String DVWA_USER = "admin";
    private static final String DVWA_PASSWORD = "password";
    private static final String SESSION_SECURITY = "security=low";

    private static final Pattern USER_TOKEN =
            Pattern.compile("name=['\"]user_token['\"]\\s+value=['\"]([^'\"]+)['\"]");
    private static final Pattern PHPSESSID = Pattern.compile("PHPSESSID=([^;\\s]+)");

    private final HttpClient http;
    private final Detector detector;
    private final List<Payload> payloads;

    /**
     * @param http     porta HTTP (laboratório)
     * @param detector estratégia que julga cada evidência ("heuristic" ou "llm")
     * @param payloads payloads de SQLi a aplicar, na ordem (lista copiada)
     */
    public SqlInjectionModule(HttpClient http, Detector detector, List<Payload> payloads) {
        this.http = Objects.requireNonNull(http, "http");
        this.detector = Objects.requireNonNull(detector, "detector");
        this.payloads = List.copyOf(Objects.requireNonNull(payloads, "payloads"));
    }

    @Override
    public VulnType type() {
        return VulnType.SQL_INJECTION;
    }

    @Override
    public List<Finding> scan(Target target) {
        Objects.requireNonNull(target, "target");
        if (payloads.isEmpty()) {
            return List.of();
        }
        String origin = origin(target.baseUrl());
        String cookie = login(origin);

        List<Finding> findings = new ArrayList<>();
        for (Payload payload : payloads) {
            HttpRequest request = new HttpRequest("GET", attackUrl(origin, payload.value()),
                    Map.of("Cookie", cookie), null);
            HttpResponse response = http.send(request);
            Evidence evidence = new Evidence(payload, request, response);
            Verdict verdict = detector.judge(evidence);
            if (verdict.vulnerable()) {
                findings.add(new Finding(VulnType.SQL_INJECTION, SQLI_SEVERITY, target, evidence, verdict));
            }
        }
        return List.copyOf(findings);
    }

    /** Faz o handshake de login do DVWA e devolve o valor do header {@code Cookie}. */
    private String login(String origin) {
        URI loginUrl = URI.create(origin + LOGIN_PATH);
        HttpResponse page = http.send(new HttpRequest("GET", loginUrl, Map.of(), null));
        String sessionId = firstMatch(PHPSESSID, headerValue(page, "set-cookie"));
        String token = firstMatch(USER_TOKEN, page.body());

        String cookie = sessionId.isEmpty() ? SESSION_SECURITY : "PHPSESSID=" + sessionId + "; " + SESSION_SECURITY;
        String body = "username=" + DVWA_USER + "&password=" + DVWA_PASSWORD
                + "&user_token=" + token + "&Login=Login";
        http.send(new HttpRequest("POST", loginUrl,
                Map.of("Cookie", cookie, "Content-Type", "application/x-www-form-urlencoded"), body));
        return cookie;
    }

    private static URI attackUrl(String origin, String payloadValue) {
        return URI.create(origin + SQLI_PATH + "?id=" + encode(payloadValue) + "&Submit=Submit");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Esquema + autoridade, sem o path do alvo: o DVWA mora na raiz do host. */
    private static String origin(URI baseUrl) {
        return baseUrl.getScheme() + "://" + baseUrl.getAuthority();
    }

    private static String headerValue(HttpResponse response, String name) {
        String value = response.headers() == null ? null : response.headers().get(name);
        return value == null ? "" : value;
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text == null ? "" : text);
        return m.find() ? m.group(1) : "";
    }
}

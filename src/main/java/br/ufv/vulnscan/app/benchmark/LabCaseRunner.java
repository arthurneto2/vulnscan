package br.ufv.vulnscan.app.benchmark;

import br.ufv.vulnscan.app.benchmark.GroundTruth.Case;
import br.ufv.vulnscan.domain.detection.Detector;
import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.module.ApiAuthModule;
import br.ufv.vulnscan.domain.module.ApiSession;
import br.ufv.vulnscan.domain.module.AuthMode;
import br.ufv.vulnscan.domain.module.AuthProbe;
import br.ufv.vulnscan.domain.module.DomXssModule;
import br.ufv.vulnscan.domain.module.InjectionPoint;
import br.ufv.vulnscan.domain.module.SqlInjectionModule;
import br.ufv.vulnscan.domain.module.StoredXssModule;
import br.ufv.vulnscan.domain.module.StoredXssPoint;
import br.ufv.vulnscan.domain.module.VulnModule;
import br.ufv.vulnscan.domain.module.XssModule;
import br.ufv.vulnscan.port.HttpClient;
import br.ufv.vulnscan.domain.module.XssBrowser;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aplicação (experimento): {@link CaseRunner} de laboratório. Para cada caso, monta um módulo
 * de um ponto só a partir do {@code location}/tipo e roda com o detector pedido; o previsto é
 * "o módulo reportou achado?". DVWA: loga uma vez por nível {@code security} e manda o cookie.
 * Juice Shop: a autenticação de API fica no {@link ApiSession}.
 */
public final class LabCaseRunner implements CaseRunner {

    private static final Pattern USER_TOKEN =
            Pattern.compile("user_token'\\s*value='([a-f0-9]+)'");
    private static final Pattern PHPSESSID = Pattern.compile("PHPSESSID=([^;\\s]+)");

    private final Map<String, Detector> detectors;
    private final HttpClient http;
    private final XssBrowser browser;
    private final ApiSession apiSession;
    private final List<Payload> sqliPayloads;
    private final List<Payload> xssReflectedPayloads;
    private final List<Payload> xssStoredPayloads;
    private final Supplier<String> markers;
    private final Map<String, String> dvwaCookies = new LinkedHashMap<>();

    public LabCaseRunner(Map<String, Detector> detectors, HttpClient http, XssBrowser browser,
                         ApiSession apiSession, List<Payload> sqliPayloads, List<Payload> xssReflectedPayloads,
                         List<Payload> xssStoredPayloads, Supplier<String> markers) {
        this.detectors = Map.copyOf(detectors);
        this.http = http;
        this.browser = browser;
        this.apiSession = apiSession;
        this.sqliPayloads = List.copyOf(sqliPayloads);
        this.xssReflectedPayloads = List.copyOf(xssReflectedPayloads);
        this.xssStoredPayloads = List.copyOf(xssStoredPayloads);
        this.markers = markers;
    }

    @Override
    public CaseResult run(Case c, URI baseUrl, String detectorName) {
        Detector detector = detectors.get(detectorName);
        if (detector == null) {
            throw new IllegalArgumentException("detector desconhecido: " + detectorName);
        }
        Target target = target(c, baseUrl);
        VulnModule module = moduleFor(c, detector);
        long start = System.nanoTime();
        List<Finding> findings = module.scan(target);
        long elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis();
        double confidence = findings.isEmpty() ? 0.0 : findings.get(0).verdict().confidence();
        return new CaseResult(!findings.isEmpty(), confidence, elapsed);
    }

    private VulnModule moduleFor(Case c, Detector detector) {
        return switch (c.type()) {
            case SQL_INJECTION -> new SqlInjectionModule(http, detector, sqliPayloads,
                    List.of(pointFromLocation(c.location())));
            case XSS_REFLECTED -> isDom(c.location())
                    ? new DomXssModule(browser, detector, xssReflectedPayloads,
                            List.of(pointFromLocation(c.location())), markers)
                    : new XssModule(http, detector, xssReflectedPayloads,
                            List.of(pointFromLocation(c.location())), markers);
            case XSS_STORED -> new StoredXssModule(http, browser, detector, xssStoredPayloads,
                    List.of(guestbook(c.location())), markers);
            case BROKEN_AUTH, BROKEN_ACCESS_CONTROL -> new ApiAuthModule(http, detector, apiSession,
                    List.of(new AuthProbe(c.type(), c.id(), authMode(c.authMode()), c.location())));
        };
    }

    private Target target(Case c, URI baseUrl) {
        boolean dvwa = c.location().startsWith("/vulnerabilities");
        if (!dvwa) {
            return new Target(baseUrl.getAuthority(), baseUrl);
        }
        String level = c.security() == null ? "low" : c.security();
        String cookie = dvwaCookies.computeIfAbsent(baseUrl + "|" + level, k -> login(baseUrl, level));
        return new Target(baseUrl.getAuthority(), baseUrl, Map.of("Cookie", cookie));
    }

    /** Login no DVWA; devolve o header Cookie (PHPSESSID + security=<nível>). */
    private String login(URI baseUrl, String level) {
        URI loginUrl = baseUrl.resolve("/login.php");
        HttpResponse page = http.send(new HttpRequest("GET", loginUrl, Map.of(), null));
        String sid = firstMatch(PHPSESSID, page.headers().getOrDefault("set-cookie", ""));
        String token = firstMatch(USER_TOKEN, page.body());
        String cookie = "PHPSESSID=" + sid + "; security=" + level;
        String body = "username=admin&password=password&user_token=" + token + "&Login=Login";
        http.send(new HttpRequest("POST", loginUrl,
                Map.of("Cookie", cookie, "Content-Type", "application/x-www-form-urlencoded"), body));
        return cookie;
    }

    private static StoredXssPoint guestbook(String location) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("txtName", "vulnscan");
        fields.put("btnSign", "Sign Guestbook");
        return new StoredXssPoint(location, fields, "mtxMessage", location);
    }

    private static AuthMode authMode(String value) {
        return value == null ? AuthMode.NONE : AuthMode.valueOf(value);
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text == null ? "" : text);
        return m.find() ? m.group(1) : "";
    }

    /** location "…/path?fixo=val&param=" → ponto (path + query fixa, param no fim). */
    static InjectionPoint pointFromLocation(String location) {
        int q = location.indexOf('?');
        String path = location.substring(0, q);
        String[] segments = location.substring(q + 1).split("&", -1);
        String last = segments[segments.length - 1];
        String parameter = last.endsWith("=") ? last.substring(0, last.length() - 1) : last;
        if (segments.length > 1) {
            path = path + "?" + String.join("&", Arrays.copyOf(segments, segments.length - 1));
        }
        return new InjectionPoint(path, parameter);
    }

    /** Fragmento no location indica XSS de DOM (precisa de navegador). */
    static boolean isDom(String location) {
        return location.contains("#");
    }
}

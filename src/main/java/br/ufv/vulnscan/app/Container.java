package br.ufv.vulnscan.app;

import br.ufv.vulnscan.adapter.browser.SeleniumBrowser;
import br.ufv.vulnscan.adapter.http.JdkHttpClient;
import br.ufv.vulnscan.adapter.http.JuiceShopSession;
import br.ufv.vulnscan.adapter.llm.OllamaClient;
import br.ufv.vulnscan.adapter.report.HtmlReportWriter;
import br.ufv.vulnscan.adapter.report.JsonReportWriter;
import br.ufv.vulnscan.adapter.report.MarkdownReportWriter;
import br.ufv.vulnscan.domain.detection.Detector;
import br.ufv.vulnscan.domain.detection.HeuristicDetector;
import br.ufv.vulnscan.domain.detection.LlmDetector;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.domain.module.ApiAuthModule;
import br.ufv.vulnscan.domain.module.ApiSession;
import br.ufv.vulnscan.domain.module.AuthMode;
import br.ufv.vulnscan.domain.module.AuthProbe;
import br.ufv.vulnscan.domain.module.DomXssModule;
import br.ufv.vulnscan.domain.module.InjectionPoint;
import br.ufv.vulnscan.domain.module.PayloadFile;
import br.ufv.vulnscan.domain.module.SqlInjectionModule;
import br.ufv.vulnscan.domain.module.StoredXssModule;
import br.ufv.vulnscan.domain.module.StoredXssPoint;
import br.ufv.vulnscan.domain.module.VulnModule;
import br.ufv.vulnscan.app.benchmark.CaseRunner;
import br.ufv.vulnscan.app.benchmark.LabCaseRunner;
import br.ufv.vulnscan.domain.module.XssBrowser;
import br.ufv.vulnscan.domain.module.XssModule;
import br.ufv.vulnscan.port.HttpClient;
import br.ufv.vulnscan.port.ReportWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.UUID;

/**
 * Aplicação: raiz de composição. Instancia os adaptadores e injeta nos módulos à mão,
 * sem framework de DI. Único lugar que conhece as implementações concretas e que lê os
 * arquivos de payloads do classpath.
 */
public final class Container {

    /** Pontos de SQLi conhecidos do laboratório: DVWA (campo id) e Juice Shop (busca). */
    private static final List<InjectionPoint> SQLI_POINTS = List.of(
            new InjectionPoint("/vulnerabilities/sqli/?Submit=Submit", "id"),
            new InjectionPoint("/rest/products/search", "q"));

    /** Pontos de XSS refletido: DVWA (reflete cru) e Juice Shop /redirect (reflete escapado). */
    private static final List<InjectionPoint> XSS_POINTS = List.of(
            new InjectionPoint("/vulnerabilities/xss_r/", "name"),
            new InjectionPoint("/redirect", "to"));

    /** XSS de DOM: busca do Juice Shop (o q vai para o fragmento e o Angular põe no DOM). */
    private static final List<InjectionPoint> DOM_XSS_POINTS = List.of(new InjectionPoint("/#/search", "q"));

    /** XSS armazenado: guestbook do DVWA (mensagem; o nome tem maxlength no formulário). */
    private static final List<StoredXssPoint> STORED_XSS_POINTS = List.of(new StoredXssPoint(
            "/vulnerabilities/xss_s/", guestbookFields(), "mtxMessage", "/vulnerabilities/xss_s/"));

    /** Probes de auth/acesso (Juice Shop): sem token, IDOR e papel indevido. */
    private static final List<AuthProbe> API_AUTH_PROBES = List.of(
            new AuthProbe(VulnType.BROKEN_AUTH, "admin-config-sem-token", AuthMode.NONE,
                    "/rest/admin/application-configuration"),
            new AuthProbe(VulnType.BROKEN_AUTH, "lista-usuarios-sem-token", AuthMode.NONE, "/api/Users"),
            new AuthProbe(VulnType.BROKEN_ACCESS_CONTROL, "idor-cesta", AuthMode.USER_TOKEN_OTHER_RESOURCE,
                    "/rest/basket/{otherId}"),
            new AuthProbe(VulnType.BROKEN_ACCESS_CONTROL, "lista-usuarios-papel", AuthMode.USER_TOKEN,
                    "/api/Users"),
            new AuthProbe(VulnType.BROKEN_ACCESS_CONTROL, "historico-pedidos-papel", AuthMode.USER_TOKEN,
                    "/rest/order-history/orders"));

    private static final String SQLI_PAYLOADS = "/payloads/sqli.txt";
    private static final String XSS_PAYLOADS = "/payloads/xss.txt";

    private final HttpClient httpClient;
    private final XssBrowser browser = new SeleniumBrowser();
    private final ApiSession apiSession = new JuiceShopSession();
    private final Config config;

    public Container(Config config) {
        this.config = config;
        this.httpClient = new JdkHttpClient(config.httpTimeout());
    }

    public ScanService scanService() {
        return new ScanService(moduleFactories(), detectors(), writers());
    }

    /** Caso de uso do experimento: LabCaseRunner com os dois detectores sobre os módulos reais. */
    public BenchmarkService benchmarkService() {
        CaseRunner runner = new LabCaseRunner(detectors(), httpClient, browser, apiSession,
                payloads(SQLI_PAYLOADS, VulnType.SQL_INJECTION),
                payloads(XSS_PAYLOADS, VulnType.XSS_REFLECTED),
                payloads(XSS_PAYLOADS, VulnType.XSS_STORED), Container::newMarker);
        return new BenchmarkService(runner, List.of("heuristic", "llm"));
    }

    /** Detectores por nome: heurístico (puro) e LLM (Ollama com o modelo fixado em Config). */
    private Map<String, Detector> detectors() {
        Map<String, Detector> detectors = new LinkedHashMap<>();
        detectors.put("heuristic", new HeuristicDetector());
        detectors.put("llm", new LlmDetector(
                new OllamaClient(httpClient, config.ollamaBaseUrl(), config.ollamaModel())));
        return detectors;
    }

    /** Fábricas de módulo por nome de {@code --module}, na ordem; cada uma recebe o Detector escolhido. */
    private Map<String, Function<Detector, VulnModule>> moduleFactories() {
        List<Payload> sqli = payloads(SQLI_PAYLOADS, VulnType.SQL_INJECTION);
        List<Payload> xssReflected = payloads(XSS_PAYLOADS, VulnType.XSS_REFLECTED);
        List<Payload> xssStored = payloads(XSS_PAYLOADS, VulnType.XSS_STORED);
        Map<String, Function<Detector, VulnModule>> factories = new LinkedHashMap<>();
        factories.put("sqli", d -> new SqlInjectionModule(httpClient, d, sqli, SQLI_POINTS));
        factories.put("xss", d -> new XssModule(httpClient, d, xssReflected, XSS_POINTS, Container::newMarker));
        factories.put("xss-dom", d -> new DomXssModule(browser, d, xssReflected, DOM_XSS_POINTS, Container::newMarker));
        factories.put("xss-stored", d -> new StoredXssModule(httpClient, browser, d, xssStored,
                STORED_XSS_POINTS, Container::newMarker));
        factories.put("api", d -> new ApiAuthModule(httpClient, d, apiSession, API_AUTH_PROBES));
        return factories;
    }

    private static Map<String, String> guestbookFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("txtName", "vulnscan");
        fields.put("btnSign", "Sign Guestbook");
        return fields;
    }

    /** Marcador único de XSS: "vsx" + 8 hex de um UUID aleatório (só letras e dígitos). */
    private static String newMarker() {
        return "vsx" + UUID.randomUUID().toString().substring(0, 8);
    }

    private List<ReportWriter> writers() {
        return List.of(new MarkdownReportWriter(), new JsonReportWriter(), new HtmlReportWriter());
    }

    /** Lê um arquivo de payloads do classpath; ausente ou ilegível é erro de montagem. */
    private static List<Payload> payloads(String resource, VulnType type) {
        try (InputStream in = Container.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("arquivo de payloads ausente no classpath: " + resource);
            }
            return PayloadFile.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

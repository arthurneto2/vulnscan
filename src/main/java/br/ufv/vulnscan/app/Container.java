package br.ufv.vulnscan.app;

import br.ufv.vulnscan.adapter.browser.SeleniumBrowser;
import br.ufv.vulnscan.adapter.http.JdkHttpClient;
import br.ufv.vulnscan.adapter.report.MarkdownReportWriter;
import br.ufv.vulnscan.domain.detection.Detector;
import br.ufv.vulnscan.domain.detection.HeuristicDetector;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.domain.module.DomXssModule;
import br.ufv.vulnscan.domain.module.InjectionPoint;
import br.ufv.vulnscan.domain.module.PayloadFile;
import br.ufv.vulnscan.domain.module.SqlInjectionModule;
import br.ufv.vulnscan.domain.module.StoredXssModule;
import br.ufv.vulnscan.domain.module.StoredXssPoint;
import br.ufv.vulnscan.domain.module.VulnModule;
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

    private static final String SQLI_PAYLOADS = "/payloads/sqli.txt";
    private static final String XSS_PAYLOADS = "/payloads/xss.txt";

    private final HttpClient httpClient;
    private final Detector detector = new HeuristicDetector();
    private final XssBrowser browser = new SeleniumBrowser();

    public Container(Config config) {
        this.httpClient = new JdkHttpClient(config.httpTimeout());
    }

    public ScanService scanService() {
        return new ScanService(modules(), writers());
    }

    /** Módulos por nome de {@code --module}, na ordem de execução. */
    private Map<String, VulnModule> modules() {
        Map<String, VulnModule> modules = new LinkedHashMap<>();
        modules.put("sqli", new SqlInjectionModule(httpClient, detector,
                payloads(SQLI_PAYLOADS, VulnType.SQL_INJECTION), SQLI_POINTS));
        modules.put("xss", new XssModule(httpClient, detector,
                payloads(XSS_PAYLOADS, VulnType.XSS_REFLECTED), XSS_POINTS, Container::newMarker));
        modules.put("xss-dom", new DomXssModule(browser, detector,
                payloads(XSS_PAYLOADS, VulnType.XSS_REFLECTED), DOM_XSS_POINTS, Container::newMarker));
        modules.put("xss-stored", new StoredXssModule(httpClient, browser, detector,
                payloads(XSS_PAYLOADS, VulnType.XSS_STORED), STORED_XSS_POINTS, Container::newMarker));
        return modules;
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
        return List.of(new MarkdownReportWriter());
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

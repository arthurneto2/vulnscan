package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.detection.Detector;
import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.Severity;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.Verdict;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.port.HttpClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Supplier;

/**
 * Domínio: módulo de XSS armazenado (estratégia {@link VulnModule}, tipo {@code XSS_STORED}).
 * Para cada {@link StoredXssPoint} e payload (com {@code {marker}} trocado por um marcador
 * único): grava via {@code POST} de formulário pela porta {@link HttpClient} (com os headers
 * do alvo) e abre a página de visualização num navegador real ({@link XssBrowser}). A
 * {@link Evidence} leva o {@code POST} (onde o payload entrou) e a resposta sintética da página
 * renderizada ({@link RenderedPage#toResponse()}). Positivo → {@link Finding}
 * {@link Severity#HIGH} e encerra o ponto. Erros do HTTP e do navegador propagam.
 *
 * <p>Suja o alvo (grava no banco); o marcador único evita confundir entradas antigas.
 */
public final class StoredXssModule implements VulnModule {

    private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";

    private final HttpClient http;
    private final XssBrowser browser;
    private final Detector detector;
    private final List<Payload> payloads;
    private final List<StoredXssPoint> points;
    private final Supplier<String> markers;

    /**
     * @param http     porta HTTP, para gravar
     * @param browser  navegador (interface do módulo), para ver
     * @param detector quem julga cada evidência
     * @param payloads payloads de {@code XSS_STORED} com {@code {marker}} (copiada; não vazia)
     * @param points   pontos de gravação e visualização (copiada; não vazia)
     * @param markers  fonte de marcadores únicos, um por gravação
     */
    public StoredXssModule(HttpClient http, XssBrowser browser, Detector detector, List<Payload> payloads,
                           List<StoredXssPoint> points, Supplier<String> markers) {
        this.http = Objects.requireNonNull(http, "http");
        this.browser = Objects.requireNonNull(browser, "browser");
        this.detector = Objects.requireNonNull(detector, "detector");
        this.payloads = MarkedPayloads.validated(payloads, VulnType.XSS_STORED);
        this.points = List.copyOf(Objects.requireNonNull(points, "points"));
        this.markers = Objects.requireNonNull(markers, "markers");
        if (this.points.isEmpty()) {
            throw new IllegalArgumentException("pontos de XSS armazenado não podem ser vazios");
        }
    }

    @Override
    public VulnType type() {
        return VulnType.XSS_STORED;
    }

    @Override
    public List<Finding> scan(Target target) {
        List<Finding> findings = new ArrayList<>();
        for (StoredXssPoint point : points) {
            for (Payload template : payloads) {
                Payload payload = MarkedPayloads.withMarker(template, markers.get());
                HttpRequest post = submit(target, point, payload);
                http.send(post);
                RenderedPage page = browser.open(target.baseUrl().resolve(point.viewPath()), target.headers());
                Evidence evidence = new Evidence(payload, post, page.toResponse());
                Verdict verdict = detector.judge(evidence);
                if (verdict.vulnerable()) {
                    findings.add(new Finding(VulnType.XSS_STORED, Severity.HIGH, target, evidence, verdict));
                    break;
                }
            }
        }
        return List.copyOf(findings);
    }

    /** POST form-urlencoded (UTF-8): campos fixos na ordem e depois o payload. */
    private static HttpRequest submit(Target target, StoredXssPoint point, Payload payload) {
        StringJoiner form = new StringJoiner("&");
        point.fixedFields().forEach((name, value) -> form.add(encode(name) + "=" + encode(value)));
        form.add(encode(point.payloadField()) + "=" + encode(payload.value()));
        Map<String, String> headers = new LinkedHashMap<>(target.headers());
        headers.put("Content-Type", FORM_CONTENT_TYPE);
        return new HttpRequest("POST", target.baseUrl().resolve(point.submitPath()), Map.copyOf(headers),
                form.toString());
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}

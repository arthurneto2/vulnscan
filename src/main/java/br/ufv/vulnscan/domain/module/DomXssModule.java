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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Domínio: módulo de XSS de DOM (estratégia {@link VulnModule}, tipo {@code XSS_REFLECTED}).
 * Igual ao {@link XssModule}, mas abre a URL num navegador real ({@link XssBrowser}) em vez de
 * fazer só a requisição HTTP: o XSS de DOM (ex.: {@code /#/search?q=} do Juice Shop) não
 * aparece em resposta HTTP nenhuma. A {@link Evidence} leva o {@code GET} e a resposta
 * sintética da página renderizada ({@link RenderedPage#toResponse()}, com os diálogos).
 * Positivo → {@link Finding} {@link Severity#MEDIUM} e encerra o ponto. Erro do navegador
 * propaga.
 */
public final class DomXssModule implements VulnModule {

    private final XssBrowser browser;
    private final Detector detector;
    private final List<Payload> payloads;
    private final List<InjectionPoint> points;
    private final Supplier<String> markers;

    /**
     * @param browser  navegador (interface do módulo)
     * @param detector quem julga cada evidência
     * @param payloads payloads de {@code XSS_REFLECTED} com {@code {marker}} (copiada; não vazia)
     * @param points   pontos de injeção, podendo ter fragmento, ex.: {@code /#/search} (copiada; não vazia)
     * @param markers  fonte de marcadores únicos, um por abertura
     */
    public DomXssModule(XssBrowser browser, Detector detector, List<Payload> payloads,
                        List<InjectionPoint> points, Supplier<String> markers) {
        this.browser = Objects.requireNonNull(browser, "browser");
        this.detector = Objects.requireNonNull(detector, "detector");
        this.payloads = MarkedPayloads.validated(payloads, VulnType.XSS_REFLECTED);
        this.points = List.copyOf(Objects.requireNonNull(points, "points"));
        this.markers = Objects.requireNonNull(markers, "markers");
        if (this.points.isEmpty()) {
            throw new IllegalArgumentException("pontos de injeção não podem ser vazios");
        }
    }

    @Override
    public VulnType type() {
        return VulnType.XSS_REFLECTED;
    }

    @Override
    public List<Finding> scan(Target target) {
        List<Finding> findings = new ArrayList<>();
        for (InjectionPoint point : points) {
            for (Payload template : payloads) {
                Payload payload = MarkedPayloads.withMarker(template, markers.get());
                URI url = point.url(target.baseUrl(), payload.value());
                RenderedPage page = browser.open(url, target.headers());
                Evidence evidence = new Evidence(payload,
                        new HttpRequest("GET", url, target.headers(), null), page.toResponse());
                Verdict verdict = detector.judge(evidence);
                if (verdict.vulnerable()) {
                    findings.add(new Finding(VulnType.XSS_REFLECTED, Severity.MEDIUM, target, evidence, verdict));
                    break;
                }
            }
        }
        return List.copyOf(findings);
    }
}

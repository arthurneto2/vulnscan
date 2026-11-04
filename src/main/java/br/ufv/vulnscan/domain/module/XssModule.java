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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Domínio: módulo de XSS refletido (estratégia {@link VulnModule}). Para cada
 * {@link InjectionPoint}, envia um {@code GET} por payload (na ordem, com os headers do alvo).
 * Cada payload traz o marcador {@value #MARKER}, trocado a cada requisição por um valor novo
 * de {@code markers}; o payload concreto vai na {@link Evidence}, para o {@link Detector}
 * saber o que procurar na resposta. Veredito positivo vira {@link Finding}
 * {@link Severity#MEDIUM} e encerra aquele ponto. Erro do {@link HttpClient} propaga.
 *
 * <p>XSS de DOM (só visível no navegador) está fora: fica com o módulo de XSS via Selenium.
 */
public final class XssModule implements VulnModule {

    private static final String MARKER = "{marker}";

    private final HttpClient http;
    private final Detector detector;
    private final List<Payload> payloads;
    private final List<InjectionPoint> points;
    private final Supplier<String> markers;

    /**
     * @param http     porta HTTP
     * @param detector quem julga cada evidência
     * @param payloads payloads de {@code XSS_REFLECTED} com {@value #MARKER}, na ordem de envio
     *                 (copiada; não vazia)
     * @param points   pontos de injeção (copiada; não vazia)
     * @param markers  fonte de marcadores únicos, um por requisição
     */
    public XssModule(HttpClient http, Detector detector, List<Payload> payloads,
                     List<InjectionPoint> points, Supplier<String> markers) {
        this.http = Objects.requireNonNull(http, "http");
        this.detector = Objects.requireNonNull(detector, "detector");
        this.payloads = List.copyOf(Objects.requireNonNull(payloads, "payloads"));
        this.points = List.copyOf(Objects.requireNonNull(points, "points"));
        this.markers = Objects.requireNonNull(markers, "markers");
        if (this.payloads.isEmpty() || this.points.isEmpty()) {
            throw new IllegalArgumentException("payloads e pontos de injeção não podem ser vazios");
        }
        for (Payload payload : this.payloads) {
            if (payload.type() != VulnType.XSS_REFLECTED) {
                throw new IllegalArgumentException("payload " + payload.id() + " é " + payload.type()
                        + ", esperado " + VulnType.XSS_REFLECTED);
            }
            if (!payload.value().contains(MARKER)) {
                throw new IllegalArgumentException("payload " + payload.id() + " não contém " + MARKER);
            }
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
                Evidence evidence = attack(target, point, withMarker(template));
                Verdict verdict = detector.judge(evidence);
                if (verdict.vulnerable()) {
                    findings.add(new Finding(VulnType.XSS_REFLECTED, Severity.MEDIUM, target, evidence, verdict));
                    break;
                }
            }
        }
        return List.copyOf(findings);
    }

    private Payload withMarker(Payload template) {
        return new Payload(template.id(), template.type(),
                template.value().replace(MARKER, markers.get()), template.description());
    }

    private Evidence attack(Target target, InjectionPoint point, Payload payload) {
        HttpRequest request = new HttpRequest("GET", point.url(target.baseUrl(), payload.value()),
                target.headers(), null);
        HttpResponse response = http.send(request);
        return new Evidence(payload, request, response);
    }
}

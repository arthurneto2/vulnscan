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

/**
 * Domínio: módulo de SQL injection (estratégia {@link VulnModule}). Para cada
 * {@link InjectionPoint}, envia um {@code GET} por payload (na ordem, com os headers do alvo),
 * monta a {@link Evidence} e pede o veredito ao {@link Detector}. Veredito positivo vira
 * {@link Finding} {@link Severity#HIGH} e encerra aquele ponto — os payloads restantes (em
 * especial o temporal, de 5 s) não são enviados. Erro do {@link HttpClient} propaga.
 *
 * <p>SQLi booleana diferencial (resposta verdadeira × falsa) está fora: cada payload é
 * julgado sozinho, numa única troca HTTP.
 */
public final class SqlInjectionModule implements VulnModule {

    private final HttpClient http;
    private final Detector detector;
    private final List<Payload> payloads;
    private final List<InjectionPoint> points;

    /**
     * @param http     porta HTTP
     * @param detector quem julga cada evidência
     * @param payloads payloads de {@code SQL_INJECTION}, na ordem de envio (copiada; não vazia)
     * @param points   pontos de injeção (copiada; não vazia)
     */
    public SqlInjectionModule(HttpClient http, Detector detector, List<Payload> payloads,
                              List<InjectionPoint> points) {
        this.http = Objects.requireNonNull(http, "http");
        this.detector = Objects.requireNonNull(detector, "detector");
        this.payloads = List.copyOf(Objects.requireNonNull(payloads, "payloads"));
        this.points = List.copyOf(Objects.requireNonNull(points, "points"));
        if (this.payloads.isEmpty() || this.points.isEmpty()) {
            throw new IllegalArgumentException("payloads e pontos de injeção não podem ser vazios");
        }
        for (Payload payload : this.payloads) {
            if (payload.type() != VulnType.SQL_INJECTION) {
                throw new IllegalArgumentException("payload " + payload.id() + " é " + payload.type()
                        + ", esperado " + VulnType.SQL_INJECTION);
            }
        }
    }

    @Override
    public VulnType type() {
        return VulnType.SQL_INJECTION;
    }

    @Override
    public List<Finding> scan(Target target) {
        List<Finding> findings = new ArrayList<>();
        for (InjectionPoint point : points) {
            for (Payload payload : payloads) {
                Evidence evidence = attack(target, point, payload);
                Verdict verdict = detector.judge(evidence);
                if (verdict.vulnerable()) {
                    findings.add(new Finding(VulnType.SQL_INJECTION, Severity.HIGH, target, evidence, verdict));
                    break;
                }
            }
        }
        return List.copyOf(findings);
    }

    private Evidence attack(Target target, InjectionPoint point, Payload payload) {
        HttpRequest request = new HttpRequest("GET", point.url(target.baseUrl(), payload.value()),
                target.headers(), null);
        HttpResponse response = http.send(request);
        return new Evidence(payload, request, response);
    }
}

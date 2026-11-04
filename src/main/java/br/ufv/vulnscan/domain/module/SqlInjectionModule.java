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

public final class SqlInjectionModule implements VulnModule {

    private final HttpClient http;
    private final Detector detector;
    private final List<Payload> payloads;
    private final List<InjectionPoint> points;

    public SqlInjectionModule(HttpClient http, Detector detector, List<Payload> payloads,
                              List<InjectionPoint> points) {
        this.http = http;
        this.detector = detector;
        this.payloads = payloads;
        this.points = points;
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
                HttpRequest request = new HttpRequest("GET", url(target, point, payload), target.headers(), null);
                HttpResponse response = http.send(request);
                Evidence evidence = new Evidence(payload, request, response);
                Verdict verdict = detector.judge(evidence);
                if (verdict.vulnerable()) {
                    findings.add(new Finding(VulnType.SQL_INJECTION, Severity.HIGH, target, evidence, verdict));
                    break;
                }
            }
        }
        return List.copyOf(findings);
    }

    private static URI url(Target target, InjectionPoint point, Payload payload) {
        String base = target.baseUrl().resolve(point.path()).toString();
        String separator = base.contains("?") ? "&" : "?";
        return URI.create(base + separator + point.parameter() + "="
                + URLEncoder.encode(payload.value(), StandardCharsets.UTF_8));
    }
}

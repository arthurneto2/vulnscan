package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.detection.Detector;
import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.port.HttpClient;

import java.util.List;
import java.util.function.Supplier;

public final class XssModule implements VulnModule {

    private static final String MARKER = "{marker}";

    private final HttpClient http;
    private final Detector detector;
    private final List<Payload> payloads;
    private final List<InjectionPoint> points;
    private final Supplier<String> markers;

    public XssModule(HttpClient http, Detector detector, List<Payload> payloads,
                     List<InjectionPoint> points, Supplier<String> markers) {
        this.http = http;
        this.detector = detector;
        this.payloads = payloads;
        this.points = points;
        this.markers = markers;
    }

    @Override
    public VulnType type() {
        return VulnType.XSS_REFLECTED;
    }

    @Override
    public List<Finding> scan(Target target) {
        for (InjectionPoint point : points) {
            for (Payload template : payloads) {
                Payload payload = new Payload(template.id(), template.type(),
                        template.value().replace(MARKER, markers.get()), template.description());
                HttpRequest request = new HttpRequest("GET", point.url(target.baseUrl(), payload.value()),
                        target.headers(), null);
                HttpResponse response = http.send(request);
                detector.judge(new Evidence(payload, request, response));
            }
        }
        return List.of();
    }
}

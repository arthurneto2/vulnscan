package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.port.HttpClient;

import java.util.List;
import java.util.Map;

public final class SmokeModule implements VulnModule {

    private final HttpClient http;

    public SmokeModule(HttpClient http) {
        this.http = http;
    }

    @Override
    public VulnType type() {
        return null;
    }

    @Override
    public List<Finding> scan(Target target) {
        http.send(new HttpRequest("GET", target.baseUrl(), Map.of(), null));
        return List.of();
    }
}

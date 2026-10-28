package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.port.HttpClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SmokeModule implements VulnModule {

    private final HttpClient http;

    public SmokeModule(HttpClient http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    @Override
    public VulnType type() {
        throw new UnsupportedOperationException("módulo de fumaça não tem VulnType");
    }

    @Override
    public List<Finding> scan(Target target) {
        http.send(new HttpRequest("GET", target.baseUrl(), Map.of(), null));
        return List.of();
    }
}
